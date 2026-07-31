package com.coxgearplanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
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
		double thresholdSeconds)
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
		double hpMult = RoomTimeEstimator.hpMultiplier(partySize);

		List<Advice> advices = new ArrayList<>();
		for (Map.Entry<GearNeed, List<RoomTimeEstimator.RoomTime>> entry : byStyle.entrySet())
		{
			GearNeed style = entry.getKey();
			if (style == primary)
			{
				continue;
			}

			Map<GearSlot, SetupBuilder.Pick> picks =
				estimator.getResolver().resolve(style, items, includeGroupStorage);

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
					advices.add(new Advice(style, slot, pick.getOption().getName(),
						null, 0, false, true));
					continue;
				}

				double saved = 0;
				boolean applies = false;
				for (RoomTimeEstimator.RoomTime rt : entry.getValue())
				{
					SetupBuilder.Pick weapon = rt.getWeapon();
					if (slot == GearSlot.SHIELD && weapon.getOption().isTwoHanded())
					{
						continue; // no shield in use with this weapon
					}
					applies = true;

					RoomMonsters.Encounter encounter = RoomMonsters.get(rt.getRoom());
					MonsterProfile monster = encounter.getProfile();
					double totalHp = monster.getHp() * hpMult * encounter.getCount();

					double dpsFull = estimator.loadoutDps(style, weapon, picks,
						Collections.emptyMap(), items, includeGroupStorage, player, monster, elitePrayers);

					Map<GearSlot, SetupBuilder.Pick> override = new HashMap<>();
					override.put(slot, primaryPick); // may be null → bare slot
					double dpsWithout = estimator.loadoutDps(style, weapon, picks,
						override, items, includeGroupStorage, player, monster, elitePrayers);

					if (dpsFull > 0 && dpsWithout > 0)
					{
						saved += totalHp / dpsWithout - totalHp / dpsFull;
					}
				}

				if (!applies)
				{
					continue;
				}

				String instead = primaryPick != null ? primaryPick.getOption().getName() : null;
				advices.add(new Advice(style, slot, pick.getOption().getName(),
					instead, saved, saved >= thresholdSeconds, false));
			}
		}

		// Most valuable switches first; shared/zero-value entries last
		advices.sort((a, b) -> Double.compare(b.getSecondsSaved(), a.getSecondsSaved()));
		return advices;
	}
}
