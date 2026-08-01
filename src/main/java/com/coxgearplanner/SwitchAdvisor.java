package com.coxgearplanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prices every armour switch for the secondary combat styles: how many
 * seconds does carrying this item actually save across the rooms where that
 * style is used, versus just keeping the primary style's item on? Switches
 * below the configured threshold are flagged as not worth the inventory
 * slot.
 *
 * Weapons are never flagged — the weapon *is* the style switch.
 */
public class SwitchAdvisor
{
	/** Armour slots that are candidates for dropping. Ammo is tied to the weapon. */
	private static final GearSlot[] SWITCHABLE = {
		GearSlot.SHIELD, GearSlot.HEAD, GearSlot.CAPE, GearSlot.NECK,
		GearSlot.BODY, GearSlot.LEGS, GearSlot.GLOVES, GearSlot.BOOTS, GearSlot.RING,
	};

	public static class Advice
	{
		private final GearNeed style;
		private final GearSlot slot;
		private final String itemName;
		private final String wearInstead; // primary item kept on, or null for bare slot
		private final double secondsSaved;
		private final boolean worthIt;
		private final boolean alreadyShared;
		/** Dropped because of the max-items-per-switch cap, not its value. */
		private boolean overLimit;

		Advice(GearNeed style, GearSlot slot, String itemName, String wearInstead,
			double secondsSaved, boolean worthIt, boolean alreadyShared)
		{
			this.style = style;
			this.slot = slot;
			this.itemName = itemName;
			this.wearInstead = wearInstead;
			this.secondsSaved = secondsSaved;
			this.worthIt = worthIt;
			this.alreadyShared = alreadyShared;
		}

		public GearNeed getStyle()
		{
			return style;
		}

		public GearSlot getSlot()
		{
			return slot;
		}

		public String getItemName()
		{
			return itemName;
		}

		public String getWearInstead()
		{
			return wearInstead;
		}

		public double getSecondsSaved()
		{
			return secondsSaved;
		}

		public boolean isWorthIt()
		{
			return worthIt;
		}

		public boolean isAlreadyShared()
		{
			return alreadyShared;
		}

		/** True when this piece was worth carrying but exceeded the switch cap. */
		public boolean isOverLimit()
		{
			return overLimit;
		}
	}

	private final RoomTimeEstimator estimator;

	public SwitchAdvisor(RoomTimeEstimator estimator)
	{
		this.estimator = estimator;
	}

	/** The chosen base outfit, its switches, and the raid time that produces. */
	public static class Result
	{
		private final GearNeed primary;
		private final List<Advice> advice;
		private final double totalSeconds;

		Result(GearNeed primary, List<Advice> advice, double totalSeconds)
		{
			this.primary = primary;
			this.advice = advice;
			this.totalSeconds = totalSeconds;
		}

		public GearNeed getPrimary()
		{
			return primary;
		}

		public List<Advice> getAdvice()
		{
			return advice;
		}

		public double getTotalSeconds()
		{
			return totalSeconds;
		}
	}

	/**
	 * Picks the base outfit by trying every style and keeping whichever gives
	 * the lowest total raid time once its switches are chosen.
	 *
	 * The old rule — wear whichever style has the most combat seconds — was
	 * self-defeating, because seconds are HP/DPS: making a style stronger cut
	 * its share of the clock and so made it LESS likely to be worn. Forcing a
	 * weaker 4-tick staff at Olm could flip the base outfit from melee to
	 * magic purely by making magic slower.
	 */
	public Result advise(
		List<RoomTimeEstimator.RoomTime> times,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage,
		PlayerSnapshot player,
		int partySize,
		boolean elitePrayers,
		double thresholdSeconds,
		int maxSwitchItems,
		int totalSwapItems,
		PlanExplanation explanation)
	{
		Map<GearNeed, List<RoomTimeEstimator.RoomTime>> byStyle = new EnumMap<>(GearNeed.class);
		for (RoomTimeEstimator.RoomTime time : times)
		{
			if (time.isFeasible() && time.getStyle() != null)
			{
				byStyle.computeIfAbsent(time.getStyle(), k -> new ArrayList<>()).add(time);
			}
		}
		if (byStyle.isEmpty())
		{
			return new Result(null, Collections.emptyList(), 0);
		}
		if (byStyle.size() == 1)
		{
			GearNeed only = byStyle.keySet().iterator().next();
			return new Result(only, Collections.emptyList(), sumSeconds(byStyle.get(only)));
		}

		// Try each style as the base outfit and keep the best total
		GearNeed best = null;
		double bestSeconds = Double.MAX_VALUE;
		for (GearNeed candidate : byStyle.keySet())
		{
			double total = evaluate(candidate, byStyle, items, includeGroupStorage, player,
				partySize, elitePrayers, thresholdSeconds, maxSwitchItems, totalSwapItems, null).getTotalSeconds();
			if (total < bestSeconds)
			{
				bestSeconds = total;
				best = candidate;
			}
		}

		if (explanation != null)
		{
			for (GearNeed candidate : byStyle.keySet())
			{
				double total = evaluate(candidate, byStyle, items, includeGroupStorage, player,
					partySize, elitePrayers, thresholdSeconds, maxSwitchItems, totalSwapItems, null).getTotalSeconds();
				explanation.addSwitchChoice(String.format("base outfit %s: %.0fs total raid time%s",
					candidate.getDisplayName().toLowerCase(), total,
					candidate == best ? "  <-- chosen" : ""));
			}
		}

		// Re-run the winner so the explanation records its decisions only
		return evaluate(best, byStyle, items, includeGroupStorage, player,
			partySize, elitePrayers, thresholdSeconds, maxSwitchItems, totalSwapItems, explanation);
	}

	/** Total raid time with one style worn as the base outfit. */
	private Result evaluate(
		GearNeed primary,
		Map<GearNeed, List<RoomTimeEstimator.RoomTime>> byStyle,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage,
		PlayerSnapshot player,
		int partySize,
		boolean elitePrayers,
		double thresholdSeconds,
		int maxSwitchItems,
		int totalSwapItems,
		PlanExplanation explanation)
	{
		Map<GearSlot, SetupBuilder.Pick> primaryPicks =
			estimator.getResolver().resolve(primary, items, includeGroupStorage);

		// One shared budget across all styles beats a per-style cap, because
		// it can move a slot from a style that barely benefits to one that does
		if (totalSwapItems > 0)
		{
			return adviseWithSharedBudget(primary, byStyle, primaryPicks, items,
				includeGroupStorage, player, partySize, elitePrayers, totalSwapItems, explanation);
		}

		// Rooms fought with the base outfit run at full speed already
		double total = sumSeconds(byStyle.get(primary));

		List<Advice> advices = new ArrayList<>();
		for (Map.Entry<GearNeed, List<RoomTimeEstimator.RoomTime>> entry : byStyle.entrySet())
		{
			if (entry.getKey() == primary)
			{
				continue;
			}
			StyleResult styleResult = adviseStyle(entry.getKey(), entry.getValue(), primaryPicks,
				items, includeGroupStorage, player, partySize, elitePrayers,
				thresholdSeconds, maxSwitchItems, explanation);
			advices.addAll(styleResult.advice);
			total += styleResult.seconds;
		}

		// Most valuable switches first; shared/zero-value entries last
		advices.sort((a, b) -> Double.compare(b.getSecondsSaved(), a.getSecondsSaved()));
		return new Result(primary, advices, total);
	}

	private static double sumSeconds(List<RoomTimeEstimator.RoomTime> times)
	{
		return times == null ? 0
			: times.stream().mapToDouble(RoomTimeEstimator.RoomTime::getSeconds).sum();
	}

	/**
	 * Working state for one secondary style while the shared budget is spent.
	 * Adding a piece only affects its own style's time, so the styles can be
	 * advanced independently and compared against each other each round.
	 */
	private static class StyleState
	{
		private final GearNeed style;
		private final List<RoomTimeEstimator.RoomTime> times;
		private final Map<GearSlot, SetupBuilder.Pick> picks;
		private final Map<GearSlot, SetupBuilder.Pick> notCarried = new LinkedHashMap<>();
		private final List<Advice> advice = new ArrayList<>();
		private double seconds;

		StyleState(GearNeed style, List<RoomTimeEstimator.RoomTime> times,
			Map<GearSlot, SetupBuilder.Pick> picks)
		{
			this.style = style;
			this.times = times;
			this.picks = picks;
		}
	}

	/**
	 * Spends ONE budget across every style at once, always buying whichever
	 * remaining piece saves the most time regardless of which style it belongs
	 * to. A per-style cap cannot trade slots between styles; this can, so it
	 * may land on eight items for one style and two for another.
	 *
	 * Weapons and any ammo the base outfit isn't already wearing are mandatory
	 * — you cannot use a style without them — and count against the budget.
	 * An offhand rides free with its weapon.
	 */
	private Result adviseWithSharedBudget(
		GearNeed primary,
		Map<GearNeed, List<RoomTimeEstimator.RoomTime>> byStyle,
		Map<GearSlot, SetupBuilder.Pick> primaryPicks,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage,
		PlayerSnapshot player,
		int partySize,
		boolean elitePrayers,
		int totalSwapItems,
		PlanExplanation explanation)
	{
		double hpMult = RoomTimeEstimator.hpMultiplier(partySize);
		List<StyleState> states = new ArrayList<>();
		int mandatory = 0;

		for (Map.Entry<GearNeed, List<RoomTimeEstimator.RoomTime>> entry : byStyle.entrySet())
		{
			if (entry.getKey() == primary)
			{
				continue;
			}
			GearNeed style = entry.getKey();
			Map<GearSlot, SetupBuilder.Pick> picks =
				estimator.getResolver().resolve(style, items, includeGroupStorage);
			StyleState state = new StyleState(style, entry.getValue(), picks);

			for (GearSlot slot : SWITCHABLE)
			{
				SetupBuilder.Pick pick = picks.get(slot);
				if (pick == null)
				{
					continue;
				}
				SetupBuilder.Pick primaryPick = primaryPicks.get(slot);
				if (primaryPick != null && primaryPick.getItemId() == pick.getItemId())
				{
					state.advice.add(new Advice(style, slot, pick.getOption().getName(),
						null, 0, false, true));
					continue;
				}
				state.notCarried.put(slot, primaryPick);
			}

			state.seconds = totalSeconds(style, state.times, picks, state.notCarried,
				items, includeGroupStorage, player, elitePrayers);
			mandatory += mandatoryItems(style, state.times, primaryPicks, items, includeGroupStorage);
			states.add(state);
		}

		int budget = Math.max(0, totalSwapItems - mandatory);
		if (explanation != null)
		{
			explanation.addSwitchChoice(String.format(
				"shared budget: %d items total, %d taken by weapons/ammo, %d left for armour",
				totalSwapItems, mandatory, budget));
		}

		while (budget > 0)
		{
			StyleState bestState = null;
			GearSlot bestSlot = null;
			double bestGain = 0;

			for (StyleState state : states)
			{
				for (GearSlot slot : state.notCarried.keySet())
				{
					Map<GearSlot, SetupBuilder.Pick> trial = new LinkedHashMap<>(state.notCarried);
					trial.remove(slot);
					double gain = state.seconds - totalSeconds(state.style, state.times,
						state.picks, trial, items, includeGroupStorage, player, elitePrayers);
					if (gain > bestGain)
					{
						bestGain = gain;
						bestSlot = slot;
						bestState = state;
					}
				}
			}

			if (bestState == null)
			{
				break; // nothing left saves any time
			}

			SetupBuilder.Pick instead = bestState.notCarried.remove(bestSlot);
			bestState.seconds -= bestGain;
			bestState.advice.add(new Advice(bestState.style, bestSlot,
				bestState.picks.get(bestSlot).getOption().getName(),
				instead != null ? instead.getOption().getName() : null, bestGain, true, false));

			// The offhand travels with its weapon, so it costs no budget
			if (bestSlot != GearSlot.SHIELD)
			{
				budget--;
			}

			if (explanation != null)
			{
				explanation.addSwitchChoice(String.format("CARRY %s %s: %s saves %.1fs (%d left)",
					bestState.style.getDisplayName().toLowerCase(),
					bestSlot.getDisplayName().toLowerCase(),
					bestState.picks.get(bestSlot).getOption().getName(), bestGain, budget));
			}
		}

		List<Advice> advices = new ArrayList<>();
		double total = sumSeconds(byStyle.get(primary));
		for (StyleState state : states)
		{
			for (Map.Entry<GearSlot, SetupBuilder.Pick> left : state.notCarried.entrySet())
			{
				Map<GearSlot, SetupBuilder.Pick> trial = new LinkedHashMap<>(state.notCarried);
				trial.remove(left.getKey());
				double gain = state.seconds - totalSeconds(state.style, state.times,
					state.picks, trial, items, includeGroupStorage, player, elitePrayers);
				Advice advice = new Advice(state.style, left.getKey(),
					state.picks.get(left.getKey()).getOption().getName(),
					left.getValue() != null ? left.getValue().getOption().getName() : null,
					gain, false, false);
				advice.overLimit = gain > 0; // it was worth something, the budget just ran out
				state.advice.add(advice);
			}
			advices.addAll(state.advice);
			total += state.seconds;
		}

		advices.sort((a, b) -> Double.compare(b.getSecondsSaved(), a.getSecondsSaved()));
		return new Result(primary, advices, total);
	}

	/** Weapon, plus ammo the base outfit isn't already wearing. */
	private int mandatoryItems(
		GearNeed style,
		List<RoomTimeEstimator.RoomTime> styleTimes,
		Map<GearSlot, SetupBuilder.Pick> primaryPicks,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage)
	{
		int count = 1;
		SetupBuilder.Pick weapon = busiestWeapon(styleTimes);
		if (style == GearNeed.RANGED && weapon != null
			&& RoomTimeEstimator.needsAmmo(weapon.getItemId()))
		{
			SetupBuilder.Pick ammo = RoomTimeEstimator.findAmmo(
				weapon.getItemId(), items, includeGroupStorage);
			SetupBuilder.Pick wornAmmo = primaryPicks.get(GearSlot.AMMO);
			if (ammo != null && (wornAmmo == null || wornAmmo.getItemId() != ammo.getItemId()))
			{
				count++;
			}
		}
		return count;
	}

	/** One style's switch decisions and the time they leave it taking. */
	private static class StyleResult
	{
		private final List<Advice> advice;
		private final double seconds;

		StyleResult(List<Advice> advice, double seconds)
		{
			this.advice = advice;
			this.seconds = seconds;
		}
	}

	/**
	 * Chooses one style's switches by greedy forward selection: starting from
	 * "wear the base outfit and just swap the weapon", repeatedly add whichever
	 * remaining piece saves the most time, until the best remaining piece is
	 * worth less than the threshold.
	 *
	 * Pricing each piece independently (the old approach) produced incoherent
	 * sets — several pieces each looked marginal against a *fully* switched
	 * loadout, so all were dropped even though wearing none of them is far
	 * worse than the individual numbers suggest.
	 */
	private StyleResult adviseStyle(
		GearNeed style,
		List<RoomTimeEstimator.RoomTime> styleTimes,
		Map<GearSlot, SetupBuilder.Pick> primaryPicks,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage,
		PlayerSnapshot player,
		int partySize,
		boolean elitePrayers,
		double thresholdSeconds,
		int maxSwitchItems,
		PlanExplanation explanation)
	{
		Map<GearSlot, SetupBuilder.Pick> picks =
			estimator.getResolver().resolve(style, items, includeGroupStorage);

		// The weapon always has to be swapped, and a bow you don't already have
		// ammo for costs a second click — both count against the cap.
		int armourBudget = Integer.MAX_VALUE;
		if (maxSwitchItems > 0)
		{
			int mandatory = 1;
			SetupBuilder.Pick mainWeapon = busiestWeapon(styleTimes);
			if (style == GearNeed.RANGED && mainWeapon != null
				&& RoomTimeEstimator.needsAmmo(mainWeapon.getItemId()))
			{
				SetupBuilder.Pick ammo = RoomTimeEstimator.findAmmo(
					mainWeapon.getItemId(), items, includeGroupStorage);
				SetupBuilder.Pick wornAmmo = primaryPicks.get(GearSlot.AMMO);
				if (ammo != null && (wornAmmo == null || wornAmmo.getItemId() != ammo.getItemId()))
				{
					mandatory++;
				}
			}
			armourBudget = Math.max(0, maxSwitchItems - mandatory);
		}

		List<Advice> advices = new ArrayList<>();
		// Slots still wearing the base outfit's item; removing an entry from
		// this map means "carry the switch and wear it".
		Map<GearSlot, SetupBuilder.Pick> notCarried = new LinkedHashMap<>();

		for (GearSlot slot : SWITCHABLE)
		{
			SetupBuilder.Pick pick = picks.get(slot);
			if (pick == null)
			{
				continue; // nothing owned to carry anyway
			}
			SetupBuilder.Pick primaryPick = primaryPicks.get(slot);
			if (primaryPick != null && primaryPick.getItemId() == pick.getItemId())
			{
				advices.add(new Advice(style, slot, pick.getOption().getName(), null, 0, false, true));
				continue;
			}
			notCarried.put(slot, primaryPick); // may be null → slot left bare
		}

		double currentSeconds = totalSeconds(style, styleTimes, picks, notCarried,
			items, includeGroupStorage, player, elitePrayers);

		int carried = 0;
		while (!notCarried.isEmpty() && carried < armourBudget)
		{
			GearSlot bestSlot = null;
			double bestGain = 0;

			for (GearSlot slot : notCarried.keySet())
			{
				Map<GearSlot, SetupBuilder.Pick> trial = new LinkedHashMap<>(notCarried);
				trial.remove(slot); // wear this style's item in that slot
				double seconds = totalSeconds(style, styleTimes, picks, trial,
					items, includeGroupStorage, player, elitePrayers);
				double gain = currentSeconds - seconds;
				if (gain > bestGain)
				{
					bestGain = gain;
					bestSlot = slot;
				}
			}

			if (bestSlot == null || bestGain < thresholdSeconds)
			{
				break; // nothing left is worth an inventory slot
			}

			SetupBuilder.Pick instead = notCarried.remove(bestSlot);
			currentSeconds -= bestGain;
			// A shield rides along with the weapon swap — a fang and defender
			// is one swap, not two — so the offhand costs no cap budget.
			if (bestSlot != GearSlot.SHIELD)
			{
				carried++;
			}
			advices.add(new Advice(style, bestSlot, picks.get(bestSlot).getOption().getName(),
				instead != null ? instead.getOption().getName() : null, bestGain, true, false));

			if (explanation != null)
			{
				explanation.addSwitchChoice(String.format(
					"CARRY %s %s: %s saves %.1fs when added (threshold %.0fs)",
					style.getDisplayName().toLowerCase(), bestSlot.getDisplayName().toLowerCase(),
					picks.get(bestSlot).getOption().getName(), bestGain, thresholdSeconds));
			}
		}

		// Whatever is left didn't clear the threshold (currentSeconds below is
		// the time this style ends up taking with the switches actually kept)
		for (Map.Entry<GearSlot, SetupBuilder.Pick> left : notCarried.entrySet())
		{
			GearSlot slot = left.getKey();
			Map<GearSlot, SetupBuilder.Pick> trial = new LinkedHashMap<>(notCarried);
			trial.remove(slot);
			double gain = currentSeconds - totalSeconds(style, styleTimes, picks, trial,
				items, includeGroupStorage, player, elitePrayers);

			SetupBuilder.Pick instead = left.getValue();
			Advice advice = new Advice(style, slot, picks.get(slot).getOption().getName(),
				instead != null ? instead.getOption().getName() : null, gain, false, false);
			// Distinguish "not worth it" from "would have been worth it, but
			// the switch cap was already full"
			advice.overLimit = carried >= armourBudget && gain >= thresholdSeconds;
			advices.add(advice);

			if (explanation != null)
			{
				explanation.addSwitchChoice(String.format(
					(advice.overLimit ? "CAPPED" : "SKIP") + " %s %s: %s saves %.1fs (threshold %.0fs) — keep %s on",
					style.getDisplayName().toLowerCase(), slot.getDisplayName().toLowerCase(),
					picks.get(slot).getOption().getName(), gain, thresholdSeconds,
					instead != null ? instead.getOption().getName() : "nothing"));
			}
		}
		return new StyleResult(advices, currentSeconds);
	}

	/** The weapon this style spends the most time using. */
	private static SetupBuilder.Pick busiestWeapon(List<RoomTimeEstimator.RoomTime> styleTimes)
	{
		SetupBuilder.Pick best = null;
		double most = -1;
		for (RoomTimeEstimator.RoomTime rt : styleTimes)
		{
			if (rt.getSeconds() > most && rt.getWeapon() != null)
			{
				most = rt.getSeconds();
				best = rt.getWeapon();
			}
		}
		return best;
	}

	/** Total expected kill time across a style's rooms with the given slots not switched. */
	private double totalSeconds(
		GearNeed style,
		List<RoomTimeEstimator.RoomTime> styleTimes,
		Map<GearSlot, SetupBuilder.Pick> picks,
		Map<GearSlot, SetupBuilder.Pick> notCarried,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage,
		PlayerSnapshot player,
		boolean elitePrayers)
	{
		double total = 0;
		for (RoomTimeEstimator.RoomTime rt : styleTimes)
		{
			// Each entry carries its own target and party-scaled HP — Olm's
			// three parts are separate entries with different monsters.
			MonsterProfile monster = rt.getMonster();
			if (monster == null)
			{
				continue;
			}

			double dps = estimator.loadoutDps(style, rt.getWeapon(), picks, notCarried,
				items, includeGroupStorage, player, monster, elitePrayers);
			if (dps > 0)
			{
				total += rt.getTotalHp() / dps;
			}
		}
		return total;
	}
}
