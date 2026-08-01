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
				partySize, elitePrayers, thresholdSeconds, maxSwitchItems, null).getTotalSeconds();
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
					partySize, elitePrayers, thresholdSeconds, maxSwitchItems, null).getTotalSeconds();
				explanation.addSwitchChoice(String.format("base outfit %s: %.0fs total raid time%s",
					candidate.getDisplayName().toLowerCase(), total,
					candidate == best ? "  <-- chosen" : ""));
			}
		}

		// Re-run the winner so the explanation records its decisions only
		return evaluate(best, byStyle, items, includeGroupStorage, player,
			partySize, elitePrayers, thresholdSeconds, maxSwitchItems, explanation);
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
		PlanExplanation explanation)
	{
		Map<GearSlot, SetupBuilder.Pick> primaryPicks =
			estimator.getResolver().resolve(primary, items, includeGroupStorage);

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
