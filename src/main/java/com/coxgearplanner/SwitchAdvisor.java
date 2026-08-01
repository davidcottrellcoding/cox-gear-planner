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
		private double secondsSaved;
		private final boolean worthIt;
		private final boolean alreadyShared;
		/** Dropped because of the max-items-per-switch cap, not its value. */
		boolean overLimit;

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
		/**
		 * The outfit the advice was actually computed against. Not always the
		 * primary style's own optimum: a slot may have been traded to remove a
		 * switch. Whatever is displayed has to come from here, or the plan
		 * reports times for a loadout it never tells you to wear.
		 */
		private Map<GearSlot, SetupBuilder.Pick> basePicks;

		Result(GearNeed primary, List<Advice> advice, double totalSeconds)
		{
			this.primary = primary;
			this.advice = advice;
			this.totalSeconds = totalSeconds;
		}

		public Map<GearSlot, SetupBuilder.Pick> getBasePicks()
		{
			return basePicks;
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
		Map<GearSlot, SetupBuilder.Pick> base =
			new LinkedHashMap<>(estimator.getResolver().resolve(primary, items, includeGroupStorage));
		emptyUnwearableSlots(base);

		Result best = resultFor(primary, base, byStyle, items, includeGroupStorage, player,
			partySize, elitePrayers, thresholdSeconds, maxSwitchItems, totalSwapItems, null);

		// The base outfit used to be whatever maximised the primary style's own
		// damage, priced as though wearing it were free. It is not: every slot
		// where the base disagrees with another style creates a switch, and a
		// switch costs an inventory slot or a place in the swap budget. A ring
		// worth a fraction of a second to the primary can push out a necklace
		// worth six seconds to a secondary, which is not a trade anyone would
		// make deliberately.
		//
		// So try wearing the secondary's item instead, for the slots where a
		// switch is actually worth something. Anything that lowers the total
		// raid time - the primary's loss included - is kept. Only those slots
		// are tried, because a switch nobody wanted cannot be worth removing.
		List<Advice> worthTrying = new ArrayList<>();
		for (Advice advice : best.getAdvice())
		{
			// Trading a slot to remove the primary's own switch is circular:
			// that switch exists only because the slot was traded.
			if (advice.getStyle() == primary)
			{
				continue;
			}
			if (!advice.isAlreadyShared() && advice.getSecondsSaved() > 0)
			{
				worthTrying.add(advice);
			}
		}
		worthTrying.sort((a, b) -> Double.compare(b.getSecondsSaved(), a.getSecondsSaved()));

		for (Advice advice : worthTrying)
		{
			SetupBuilder.Pick alt = estimator.getResolver()
				.resolve(advice.getStyle(), items, includeGroupStorage).get(advice.getSlot());
			SetupBuilder.Pick current = base.get(advice.getSlot());
			if (alt == null || (current != null && current.getItemId() == alt.getItemId()))
			{
				continue;
			}

			Map<GearSlot, SetupBuilder.Pick> trial = new LinkedHashMap<>(base);
			trial.put(advice.getSlot(), alt);
			Result candidate = resultFor(primary, trial, byStyle, items, includeGroupStorage,
				player, partySize, elitePrayers, thresholdSeconds, maxSwitchItems,
				totalSwapItems, null);

			// A trade is only worth making if it REMOVES a switch. If the base
			// style has to carry its own item back into that slot, nothing was
			// removed - the switch just changed owner, and an inventory slot
			// was spent to move it. Left unchecked this compounds: with a
			// per-style cap the base style's switch-backs look free, so the
			// optimiser strips its gear slot by slot and hands back a "melee"
			// base outfit wearing a magic hood and a ranged cape.
			if (switchesBack(candidate, primary, advice.getSlot()))
			{
				continue;
			}

			if (candidate.getTotalSeconds() < best.getTotalSeconds() - 1e-9)
			{
				base = trial;
				best = candidate;
				if (explanation != null)
				{
					explanation.addSwitchChoice(String.format(
						"BASE %s: wearing %s saves the %s switch and lowers the raid to %.1fs",
						advice.getSlot().getDisplayName().toLowerCase(),
						alt.getOption().getName(),
						advice.getStyle().getDisplayName().toLowerCase(),
						candidate.getTotalSeconds()));
				}
			}
		}

		Result settled = resultFor(primary, base, byStyle, items, includeGroupStorage, player,
			partySize, elitePrayers, thresholdSeconds, maxSwitchItems, totalSwapItems,
			explanation);
		settled.basePicks = base;
		return settled;
	}

	/**
	 * What an already-worn slot is contributing, in seconds.
	 *
	 * A shared slot never appears as a decision, because there is nothing to
	 * carry - the base outfit already has the right item. That makes it the
	 * one part of the advice with no number against it, which is unhelpful
	 * when a plan looks wrong: a slot doing nothing and a slot doing a great
	 * deal read identically. Priced against wearing nothing there, since the
	 * question being answered is what the slot is worth at all, not which of
	 * two items won it.
	 */
	private void priceShared(
		List<Advice> advices,
		GearNeed style,
		List<RoomTimeEstimator.RoomTime> styleTimes,
		Map<GearSlot, SetupBuilder.Pick> picks,
		Map<GearSlot, SetupBuilder.Pick> notCarried,
		double baseline,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage,
		PlayerSnapshot player,
		boolean elitePrayers)
	{
		if (!(baseline > 0))
		{
			return;
		}
		for (Advice advice : advices)
		{
			if (!advice.isAlreadyShared())
			{
				continue;
			}
			advice.secondsSaved = Math.max(0, worstCaseWithout(advice.getSlot(), style,
				styleTimes, picks, notCarried, items, includeGroupStorage, player,
				elitePrayers) - baseline);
		}
	}

	/**
	 * Time this style would take wearing the next best thing it owns in a
	 * slot, or nothing at all if it owns nothing else.
	 *
	 * Pricing against an empty slot was the obvious reading of "what is this
	 * worth", and it produced numbers like an avernic defender saving 3286
	 * seconds. That is not a bug in the arithmetic so much as in the question:
	 * stripping a slot can drop an accuracy roll below the point where hit
	 * chance collapses, and time being health over damage, the answer runs
	 * away. It is also a comparison nobody faces - you do not raid with a bare
	 * slot, you raid with the second best thing in your bank.
	 *
	 * Against a real alternative the number is both finite and directly
	 * comparable to the carried switches listed above it, which are priced the
	 * same way.
	 */
	private double worstCaseWithout(
		GearSlot slot,
		GearNeed style,
		List<RoomTimeEstimator.RoomTime> styleTimes,
		Map<GearSlot, SetupBuilder.Pick> picks,
		Map<GearSlot, SetupBuilder.Pick> notCarried,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage,
		PlayerSnapshot player,
		boolean elitePrayers)
	{
		SetupBuilder.Pick worn = picks.get(slot);
		double best = Double.MAX_VALUE;

		List<SetupBuilder.Pick> owned = estimator.getResolver()
			.scan(items, includeGroupStorage)
			.getOrDefault(slot, java.util.Collections.emptyList());
		for (SetupBuilder.Pick candidate : owned)
		{
			if (worn != null && candidate.getItemId() == worn.getItemId())
			{
				continue;
			}
			Map<GearSlot, SetupBuilder.Pick> trial = new LinkedHashMap<>(notCarried);
			trial.put(slot, candidate);
			double seconds = totalSeconds(style, styleTimes, picks, trial,
				items, includeGroupStorage, player, elitePrayers);
			if (seconds > 0 && seconds < best)
			{
				best = seconds;
			}
		}

		if (best < Double.MAX_VALUE)
		{
			return best;
		}

		// Nothing else owned for this slot, so empty really is the alternative
		Map<GearSlot, SetupBuilder.Pick> bare = new LinkedHashMap<>(notCarried);
		bare.put(slot, null);
		return totalSeconds(style, styleTimes, picks, bare,
			items, includeGroupStorage, player, elitePrayers);
	}

	/** Whether the base style ends up carrying a switch for this slot. */
	static boolean switchesBack(Result result, GearNeed primary, GearSlot slot)
	{
		for (Advice advice : result.getAdvice())
		{
			// Only a switch-back that is actually CARRIED means nothing was
			// freed. If it lost to the budget, the slot really did become
			// available for something better - which is the trade working, not
			// failing. Counting that as failure vetoed every trade whenever the
			// minimum switch value was low enough to make each switch-back look
			// worth having, which is how a Mage's book and a seers ring worth
			// 0.7s each stayed on while a 5.1s anguish went uncarried.
			if (advice.getStyle() == primary && advice.getSlot() == slot
				&& advice.isWorthIt())
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Clears slots the base outfit cannot actually fill.
	 *
	 * A style's resolved picks name an item for every slot, including ones its
	 * own weapon rules out - a shield behind a two-handed weapon, ammunition
	 * for a bow that fires none. The damage calculation already ignores those,
	 * so the numbers were right, but everything that reports what is worn read
	 * them as equipped: the advice called a mage's book "already worn" and
	 * every section claimed dragon arrows were on, while the equipped list
	 * correctly showed an empty shield and ammo slot.
	 *
	 * Emptying them here fixes all of it at once, and lets the pieces compete
	 * honestly - a shield the base outfit cannot wear is a switch like any
	 * other, to be carried or dropped on its merits.
	 */
	static void emptyUnwearableSlots(Map<GearSlot, SetupBuilder.Pick> base)
	{
		SetupBuilder.Pick weapon = base.get(GearSlot.WEAPON);
		if (weapon == null)
		{
			return;
		}
		if (weapon.getOption().isTwoHanded())
		{
			base.put(GearSlot.SHIELD, null);
		}
		if (!RoomTimeEstimator.needsAmmo(weapon.getItemId()))
		{
			base.put(GearSlot.AMMO, null);
		}
	}

	/** Total raid time for one specific base outfit. */
	private Result resultFor(
		GearNeed primary,
		Map<GearSlot, SetupBuilder.Pick> primaryPicks,
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
		// One shared budget across all styles beats a per-style cap, because
		// it can move a slot from a style that barely benefits to one that does
		if (totalSwapItems > 0)
		{
			return adviseWithSharedBudget(primary, byStyle, primaryPicks, items,
				includeGroupStorage, player, partySize, elitePrayers, totalSwapItems, explanation);
		}

		double total = baselineCorrection(primary, byStyle, items,
			includeGroupStorage, player, elitePrayers);

		List<Advice> advices = new ArrayList<>();
		for (Map.Entry<GearNeed, List<RoomTimeEstimator.RoomTime>> entry : byStyle.entrySet())
		{
			StyleResult styleResult = adviseStyle(entry.getKey(), entry.getValue(), primaryPicks,
				items, includeGroupStorage, player, partySize, elitePrayers,
				thresholdSeconds, maxSwitchItems, explanation);
			advices.addAll(styleResult.advice);
			total += styleResult.seconds;
		}

		// Most valuable switches first; shared/zero-value entries last
		// Shared slots last regardless of value. They now carry real numbers,
		// and a worn slot worth 40s would otherwise sort above a decision to
		// carry something worth 10s - burying the actual decisions under
		// entries that are not decisions at all.
		advices.sort((a, b) -> a.isAlreadyShared() != b.isAlreadyShared()
			? Boolean.compare(a.isAlreadyShared(), b.isAlreadyShared())
			: Double.compare(b.getSecondsSaved(), a.getSecondsSaved()));
		return new Result(primary, advices, total);
	}

	/**
	 * The primary style's own rooms, fought in whatever base outfit it is
	 * actually wearing. This used to be the precomputed best-case sum, which
	 * quietly assumed the base outfit was always the primary's own optimum -
	 * true until the base outfit became something worth trading.
	 *
	 * Charged as a delta rather than recomputed outright. The reported room
	 * times already fold in things this method cannot see - the salve amulet
	 * swap the estimator found for undead targets, complete-set effects - so
	 * recomputing from scratch would silently make every primary style look
	 * slower than the times shown beside it, and could flip which style wins
	 * the base slot for no better reason than which effects survived the
	 * round trip. Taking the difference between two override-free numbers
	 * leaves those effects in the baseline where they belong, and cancels
	 * them out of the comparison.
	 */
	private double baselineCorrection(
		GearNeed primary,
		Map<GearNeed, List<RoomTimeEstimator.RoomTime>> byStyle,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage,
		PlayerSnapshot player,
		boolean elitePrayers)
	{
		List<RoomTimeEstimator.RoomTime> times = byStyle.get(primary);
		if (times == null)
		{
			return 0;
		}

		Map<GearSlot, SetupBuilder.Pick> optimal =
			estimator.getResolver().resolve(primary, items, includeGroupStorage);
		return sumSeconds(times) - totalSeconds(primary, times, optimal,
			java.util.Collections.emptyMap(), items, includeGroupStorage, player, elitePrayers);
	}

	static boolean sameLoadout(
		Map<GearSlot, SetupBuilder.Pick> a, Map<GearSlot, SetupBuilder.Pick> b)
	{
		for (GearSlot slot : GearSlot.values())
		{
			SetupBuilder.Pick left = a.get(slot);
			SetupBuilder.Pick right = b.get(slot);
			if ((left == null) != (right == null))
			{
				return false;
			}
			if (left != null && left.getItemId() != right.getItemId())
			{
				return false;
			}
		}
		return true;
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
			priceShared(state.advice, style, state.times, picks, state.notCarried,
				state.seconds, items, includeGroupStorage, player, elitePrayers);
			states.add(state);
		}

		// Every distinct weapon across every style gets carried, except the one
		// actually equipped. Counting one per secondary style undercounted
		// badly: a style can win different rooms with different weapons, and
		// the base style's other weapons are carried too.
		mandatory = carriedWeaponCount(primary, byStyle, primaryPicks, items, includeGroupStorage);

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
		double total = baselineCorrection(primary, byStyle, items,
			includeGroupStorage, player, elitePrayers);
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

		// Shared slots last regardless of value. They now carry real numbers,
		// and a worn slot worth 40s would otherwise sort above a decision to
		// carry something worth 10s - burying the actual decisions under
		// entries that are not decisions at all.
		advices.sort((a, b) -> a.isAlreadyShared() != b.isAlreadyShared()
			? Boolean.compare(a.isAlreadyShared(), b.isAlreadyShared())
			: Double.compare(b.getSecondsSaved(), a.getSecondsSaved()));
		return new Result(primary, advices, total);
	}

	/**
	 * How many inventory slots the weapons take: every distinct weapon the plan
	 * uses, minus the one worn, plus any ammo the base outfit isn't already
	 * wearing. This has to match what RaidLoadoutBuilder actually packs, or the
	 * budget is spent against a weapon count that never existed.
	 */
	private int carriedWeaponCount(
		GearNeed primary,
		Map<GearNeed, List<RoomTimeEstimator.RoomTime>> byStyle,
		Map<GearSlot, SetupBuilder.Pick> primaryPicks,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage)
	{
		SetupBuilder.Pick equipped = busiestWeapon(byStyle.get(primary));
		java.util.Set<Integer> weapons = new java.util.HashSet<>();
		java.util.Set<Integer> ammo = new java.util.HashSet<>();

		for (Map.Entry<GearNeed, List<RoomTimeEstimator.RoomTime>> entry : byStyle.entrySet())
		{
			for (RoomTimeEstimator.RoomTime rt : entry.getValue())
			{
				SetupBuilder.Pick weapon = rt.getWeapon();
				if (weapon == null
					|| (equipped != null && weapon.getItemId() == equipped.getItemId()))
				{
					continue;
				}
				weapons.add(weapon.getItemId());

				if (entry.getKey() == GearNeed.RANGED
					&& RoomTimeEstimator.needsAmmo(weapon.getItemId()))
				{
					SetupBuilder.Pick shot = RoomTimeEstimator.findAmmo(
						weapon.getItemId(), items, includeGroupStorage);
					SetupBuilder.Pick wornAmmo = primaryPicks.get(GearSlot.AMMO);
					if (shot != null
						&& (wornAmmo == null || wornAmmo.getItemId() != shot.getItemId()))
					{
						ammo.add(shot.getItemId());
					}
				}
			}
		}
		return weapons.size() + ammo.size();
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
		priceShared(advices, style, styleTimes, picks, notCarried, currentSeconds,
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
