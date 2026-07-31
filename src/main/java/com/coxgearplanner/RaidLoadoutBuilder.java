package com.coxgearplanner;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Condenses the per-style loadouts, room times and switch advice into one
 * concrete raid layout: what to wear out of the bank (the primary style's
 * full kit) and exactly what goes in the inventory — secondary weapons,
 * only the worth-it armour switches, and utility items — with a count of
 * how many slots remain free for supplies.
 */
public final class RaidLoadoutBuilder
{
	/** One inventory slot. */
	public static class Entry
	{
		private final String name;
		private final ItemSource source; // null when missing
		private final String note;
		private final boolean missing;

		Entry(String name, ItemSource source, String note, boolean missing)
		{
			this.name = name;
			this.source = source;
			this.note = note;
			this.missing = missing;
		}

		public String getName()
		{
			return name;
		}

		public ItemSource getSource()
		{
			return source;
		}

		public String getNote()
		{
			return note;
		}

		public boolean isMissing()
		{
			return missing;
		}
	}

	public static class RaidLoadout
	{
		private final GearNeed primaryStyle;
		private final List<SetupBuilder.Line> equipped;
		private final List<Entry> inventory;

		RaidLoadout(GearNeed primaryStyle, List<SetupBuilder.Line> equipped, List<Entry> inventory)
		{
			this.primaryStyle = primaryStyle;
			this.equipped = equipped;
			this.inventory = inventory;
		}

		public GearNeed getPrimaryStyle()
		{
			return primaryStyle;
		}

		public List<SetupBuilder.Line> getEquipped()
		{
			return equipped;
		}

		public List<Entry> getInventory()
		{
			return inventory;
		}

		public int getUsedSlots()
		{
			return (int) inventory.stream().filter(e -> !e.isMissing()).count();
		}

		public int getFreeSlots()
		{
			return 28 - getUsedSlots();
		}
	}

	private RaidLoadoutBuilder()
	{
	}

	/**
	 * @return the single-inventory raid layout, or null when no selected room
	 * has a feasible kill (nothing to plan around).
	 */
	public static RaidLoadout build(
		Set<CoxRoom> rooms,
		List<RoomTimeEstimator.RoomTime> times,
		List<SwitchAdvisor.Advice> advice,
		GearNeed primary,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage)
	{
		if (primary == null)
		{
			return null;
		}

		// Group feasible rooms by winning style; remember each style's weapons
		Map<GearNeed, List<RoomTimeEstimator.RoomTime>> byStyle = new EnumMap<>(GearNeed.class);
		for (RoomTimeEstimator.RoomTime time : times)
		{
			if (time.isFeasible() && time.getStyle() != null)
			{
				byStyle.computeIfAbsent(time.getStyle(), k -> new ArrayList<>()).add(time);
			}
		}

		Map<GearSlot, SetupBuilder.Pick> primaryPicks =
			SetupBuilder.resolveLoadout(primary, items, includeGroupStorage);
		SetupBuilder.Pick primaryWeapon = mainWeapon(byStyle.get(primary));

		List<SetupBuilder.Line> equipped =
			equippedLines(primary, primaryWeapon, primaryPicks, items, includeGroupStorage);

		// Inventory, deduped by item id (LinkedHashMap keeps insertion order)
		Map<Integer, Entry> inventory = new LinkedHashMap<>();
		Set<Integer> worn = wornIds(primaryWeapon, primaryPicks);

		// 1. Extra weapons: other rooms' winners, primary style first
		addWeapons(inventory, worn, primary, byStyle.get(primary), primaryWeapon, items, includeGroupStorage);
		for (Map.Entry<GearNeed, List<RoomTimeEstimator.RoomTime>> entry : byStyle.entrySet())
		{
			if (entry.getKey() != primary)
			{
				addWeapons(inventory, worn, entry.getKey(), entry.getValue(), null, items, includeGroupStorage);
			}
		}

		// 2. Only the worth-it armour switches
		for (SwitchAdvisor.Advice a : advice)
		{
			if (!a.isWorthIt() || a.isAlreadyShared())
			{
				continue;
			}
			SetupBuilder.Pick pick = SetupBuilder
				.resolveLoadout(a.getStyle(), items, includeGroupStorage)
				.get(a.getSlot());
			if (pick == null || worn.contains(pick.getItemId()))
			{
				continue;
			}
			inventory.putIfAbsent(pick.getItemId(), new Entry(
				pick.getOption().getName(), pick.getSource(),
				a.getStyle().getDisplayName().toLowerCase() + " "
					+ a.getSlot().getDisplayName().toLowerCase() + " switch",
				false));
		}

		// 3. Utility items the selected rooms demand
		int missingKey = -1;
		for (GearNeed need : GearNeed.values())
		{
			if (need.isCombatStyle())
			{
				continue;
			}
			String forRooms = roomsNeeding(rooms, need);
			if (forRooms == null)
			{
				continue;
			}

			SetupBuilder.Pick pick = SetupBuilder.pickBest(
				GearDatabase.utility(need), items, includeGroupStorage);
			if (pick != null)
			{
				if (!worn.contains(pick.getItemId()))
				{
					inventory.putIfAbsent(pick.getItemId(), new Entry(
						pick.getOption().getName(), pick.getSource(),
						need.getDisplayName().toLowerCase() + " — " + forRooms, false));
				}
			}
			else
			{
				String best = GearDatabase.utility(need).get(0).getName();
				inventory.put(missingKey--, new Entry(
					"none owned (best: " + best + ")", null,
					need.getDisplayName().toLowerCase() + " — " + forRooms, true));
			}
		}

		return new RaidLoadout(primary, equipped, new ArrayList<>(inventory.values()));
	}

	/** The style's weapon in the room where it spends the most time. */
	private static SetupBuilder.Pick mainWeapon(List<RoomTimeEstimator.RoomTime> styleTimes)
	{
		SetupBuilder.Pick best = null;
		double most = -1;
		if (styleTimes != null)
		{
			for (RoomTimeEstimator.RoomTime rt : styleTimes)
			{
				if (rt.getSeconds() > most)
				{
					most = rt.getSeconds();
					best = rt.getWeapon();
				}
			}
		}
		return best;
	}

	private static List<SetupBuilder.Line> equippedLines(
		GearNeed primary,
		SetupBuilder.Pick weapon,
		Map<GearSlot, SetupBuilder.Pick> picks,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage)
	{
		List<SetupBuilder.Line> lines = new ArrayList<>();
		boolean twoHanded = weapon != null && weapon.getOption().isTwoHanded();

		for (GearSlot slot : GearSlot.values())
		{
			if (slot == GearSlot.WEAPON)
			{
				if (weapon != null)
				{
					lines.add(new SetupBuilder.Line(slot.getDisplayName(),
						weapon.getOption().getName(), weapon.getSource(), false, weapon.getQuantity()));
				}
				continue;
			}
			if (slot == GearSlot.SHIELD && twoHanded)
			{
				lines.add(new SetupBuilder.Line(slot.getDisplayName(),
					"— (two-handed weapon)", null, false, 0));
				continue;
			}
			if (slot == GearSlot.AMMO)
			{
				if (weapon != null && primary == GearNeed.RANGED)
				{
					SetupBuilder.Pick ammo =
						RoomTimeEstimator.findAmmo(weapon.getItemId(), items, includeGroupStorage);
					if (ammo != null)
					{
						lines.add(new SetupBuilder.Line(slot.getDisplayName(),
							ammo.getOption().getName(), ammo.getSource(), false, ammo.getQuantity()));
					}
				}
				continue;
			}

			SetupBuilder.Pick pick = picks.get(slot);
			if (pick != null)
			{
				lines.add(new SetupBuilder.Line(slot.getDisplayName(),
					pick.getOption().getName(), pick.getSource(), false, pick.getQuantity()));
			}
			else
			{
				String best = GearDatabase.loadout(primary).get(slot).get(0).getName();
				lines.add(new SetupBuilder.Line(slot.getDisplayName(),
					"none owned (best: " + best + ")", null, true, 0));
			}
		}
		return lines;
	}

	private static Set<Integer> wornIds(SetupBuilder.Pick weapon, Map<GearSlot, SetupBuilder.Pick> picks)
	{
		Set<Integer> ids = new java.util.HashSet<>();
		if (weapon != null)
		{
			ids.add(weapon.getItemId());
		}
		for (SetupBuilder.Pick pick : picks.values())
		{
			if (pick != null)
			{
				ids.add(pick.getItemId());
			}
		}
		return ids;
	}

	private static void addWeapons(
		Map<Integer, Entry> inventory,
		Set<Integer> worn,
		GearNeed style,
		List<RoomTimeEstimator.RoomTime> styleTimes,
		SetupBuilder.Pick alreadyEquipped,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage)
	{
		if (styleTimes == null)
		{
			return;
		}
		for (RoomTimeEstimator.RoomTime rt : styleTimes)
		{
			SetupBuilder.Pick weapon = rt.getWeapon();
			if (weapon == null || worn.contains(weapon.getItemId())
				|| (alreadyEquipped != null && weapon.getItemId() == alreadyEquipped.getItemId()))
			{
				continue;
			}
			inventory.putIfAbsent(weapon.getItemId(), new Entry(
				weapon.getOption().getName(), weapon.getSource(),
				style.getDisplayName().toLowerCase() + " weapon", false));

			if (style == GearNeed.RANGED && RoomTimeEstimator.needsAmmo(weapon.getItemId()))
			{
				SetupBuilder.Pick ammo =
					RoomTimeEstimator.findAmmo(weapon.getItemId(), items, includeGroupStorage);
				if (ammo != null && !worn.contains(ammo.getItemId()))
				{
					inventory.putIfAbsent(ammo.getItemId(), new Entry(
						ammo.getOption().getName(), ammo.getSource(),
						"ammo for " + weapon.getOption().getName(), false));
				}
			}
		}
	}

	/** Comma-joined display names of selected rooms with this need, or null when none. */
	private static String roomsNeeding(Set<CoxRoom> rooms, GearNeed need)
	{
		StringBuilder sb = new StringBuilder();
		for (CoxRoom room : rooms)
		{
			if (room.getNeeds().contains(need))
			{
				if (sb.length() > 0)
				{
					sb.append(", ");
				}
				sb.append(room.getDisplayName());
			}
		}
		return sb.length() == 0 ? null : sb.toString();
	}
}
