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
	}

	private final RoomTimeEstimator estimator;

	public SwitchAdvisor(RoomTimeEstimator estimator)
	{
		this.estimator = estimator;
	}

	/**
	 * @return advice per secondary-style armour switch, or an empty list when
	 * fewer than two styles are actually used across the selected rooms.
	 * The primary style (worn as the base outfit) is the one with the most
	 * estimated combat time.
	 */
	public List<Advice> advise(
		List<RoomTimeEstimator.RoomTime> times,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage,
		PlayerSnapshot player,
		int partySize,
		boolean elitePrayers,
		double thresholdSeconds,
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
		if (byStyle.size() < 2)
		{
			return Collections.emptyList();
		}

		GearNeed primary = null;
		double primarySeconds = -1;
		for (Map.Entry<GearNeed, List<RoomTimeEstimator.RoomTime>> entry : byStyle.entrySet())
		{
			double total = entry.getValue().stream().mapToDouble(RoomTimeEstimator.RoomTime::getSeconds).sum();
			if (total > primarySeconds)
			{
				primarySeconds = total;
				primary = entry.getKey();
			}
		}

		Map<GearSlot, SetupBuilder.Pick> primaryPicks =
			estimator.getResolver().resolve(primary, items, includeGroupStorage);

		List<Advice> advices = new ArrayList<>();
		for (Map.Entry<GearNeed, List<RoomTimeEstimator.RoomTime>> entry : byStyle.entrySet())
		{
			if (entry.getKey() == primary)
			{
				continue;
			}
			advices.addAll(adviseStyle(entry.getKey(), entry.getValue(), primaryPicks,
				items, includeGroupStorage, player, partySize, elitePrayers,
				thresholdSeconds, explanation));
		}

		// Most valuable switches first; shared/zero-value entries last
		advices.sort((a, b) -> Double.compare(b.getSecondsSaved(), a.getSecondsSaved()));
		return advices;
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
	private List<Advice> adviseStyle(
		GearNeed style,
		List<RoomTimeEstimator.RoomTime> styleTimes,
		Map<GearSlot, SetupBuilder.Pick> primaryPicks,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage,
		PlayerSnapshot player,
		int partySize,
		boolean elitePrayers,
		double thresholdSeconds,
		PlanExplanation explanation)
	{
		Map<GearSlot, SetupBuilder.Pick> picks =
			estimator.getResolver().resolve(style, items, includeGroupStorage);

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

		while (!notCarried.isEmpty())
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

		// Whatever is left didn't clear the threshold
		for (Map.Entry<GearSlot, SetupBuilder.Pick> left : notCarried.entrySet())
		{
			GearSlot slot = left.getKey();
			Map<GearSlot, SetupBuilder.Pick> trial = new LinkedHashMap<>(notCarried);
			trial.remove(slot);
			double gain = currentSeconds - totalSeconds(style, styleTimes, picks, trial,
				items, includeGroupStorage, player, elitePrayers);

			SetupBuilder.Pick instead = left.getValue();
			advices.add(new Advice(style, slot, picks.get(slot).getOption().getName(),
				instead != null ? instead.getOption().getName() : null, gain, false, false));

			if (explanation != null)
			{
				explanation.addSwitchChoice(String.format(
					"SKIP %s %s: %s would only save %.1fs (threshold %.0fs) — keep %s on",
					style.getDisplayName().toLowerCase(), slot.getDisplayName().toLowerCase(),
					picks.get(slot).getOption().getName(), gain, thresholdSeconds,
					instead != null ? instead.getOption().getName() : "nothing"));
			}
		}
		return advices;
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
