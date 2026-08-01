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
	/** An OSRS inventory is 28 slots; the worn set is the 11 GearSlot values. */
	public static final int INVENTORY_SIZE = 28;

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
		private final List<SetupBuilder.Section> styleSections = new ArrayList<>();

		RaidLoadout(GearNeed primaryStyle, List<SetupBuilder.Line> equipped, List<Entry> inventory)
		{
			this.primaryStyle = primaryStyle;
			this.equipped = equipped;
			this.inventory = inventory;
		}

		/**
		 * What you will actually be wearing for each style once you've made the
		 * swaps — built from the equipped set plus the carried switches, so it
		 * can only ever name items that are in the equipped or inventory lists.
		 */
		public List<SetupBuilder.Section> getStyleSections()
		{
			return styleSections;
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

		/** Inventory slots the gear occupies (missing items take none). */
		public int getUsedSlots()
		{
			return (int) inventory.stream().filter(e -> !e.isMissing()).count();
		}

		/** Slots left for brews, restores and food. */
		public int getFreeSlots()
		{
			return INVENTORY_SIZE - getUsedSlots();
		}

		/** True when the gear alone cannot fit in one inventory. */
		public boolean isOverCapacity()
		{
			return getUsedSlots() > INVENTORY_SIZE;
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
		boolean includeGroupStorage,
		GearResolver resolver)
	{
		return build(rooms, times, advice, primary, items, includeGroupStorage,
			resolver, java.util.Collections.emptySet());
	}

	/**
	 * @param needsCharging item ids owned only in uncharged form — flagged so
	 * you charge them before the raid rather than discovering it inside.
	 */
	public static RaidLoadout build(
		Set<CoxRoom> rooms,
		List<RoomTimeEstimator.RoomTime> times,
		List<SwitchAdvisor.Advice> advice,
		GearNeed primary,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage,
		GearResolver resolver,
		Set<Integer> needsCharging)
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
			picksFor(resolver, primary, items, includeGroupStorage);
		SetupBuilder.Pick primaryWeapon = mainWeapon(byStyle.get(primary));

		List<SetupBuilder.Line> equipped =
			equippedLines(primary, primaryWeapon, primaryPicks, items, includeGroupStorage);

		// Inventory, deduped by item id (LinkedHashMap keeps insertion order)
		Map<Integer, Entry> inventory = new LinkedHashMap<>();
		Set<Integer> worn = wornIds(primaryWeapon, primaryPicks);

		// 1. Extra weapons: other rooms' winners, primary style first
		addWeapons(inventory, worn, primary, byStyle.get(primary), primaryWeapon, items, includeGroupStorage, needsCharging);
		for (Map.Entry<GearNeed, List<RoomTimeEstimator.RoomTime>> entry : byStyle.entrySet())
		{
			if (entry.getKey() != primary)
			{
				addWeapons(inventory, worn, entry.getKey(), entry.getValue(), null, items, includeGroupStorage, needsCharging);
			}
		}

		// 2. Room-specific extras the general loadout can't cover (salve amulet)
		for (RoomTimeEstimator.RoomTime time : times)
		{
			SetupBuilder.Pick extra = time.getExtraSwitch();
			if (extra == null || worn.contains(extra.getItemId()))
			{
				continue;
			}
			inventory.putIfAbsent(extra.getItemId(), new Entry(
				extra.getOption().getName(), extra.getSource(),
				charge(needsCharging, extra.getItemId(), "for " + time.getDisplayName()), false));
		}

		// 3. Only the worth-it armour switches
		for (SwitchAdvisor.Advice a : advice)
		{
			if (!a.isWorthIt() || a.isAlreadyShared())
			{
				continue;
			}
			SetupBuilder.Pick pick =
				picksFor(resolver, a.getStyle(), items, includeGroupStorage).get(a.getSlot());
			if (pick == null || worn.contains(pick.getItemId()))
			{
				continue;
			}
			inventory.putIfAbsent(pick.getItemId(), new Entry(
				pick.getOption().getName(), pick.getSource(),
				charge(needsCharging, pick.getItemId(),
					a.getStyle().getDisplayName().toLowerCase() + " "
						+ a.getSlot().getDisplayName().toLowerCase() + " switch"),
				false));
		}

		// 4. Utility items the selected rooms demand
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
					"nothing you own — BiS to chase: " + best, null,
					need.getDisplayName().toLowerCase() + " — " + forRooms, true));
			}
		}

		RaidLoadout loadout = new RaidLoadout(primary, equipped, new ArrayList<>(inventory.values()));

		// Per-style sections describing the *actual* result of swapping, so
		// they never name gear that isn't in the equipped or inventory lists.
		for (Map.Entry<GearNeed, List<RoomTimeEstimator.RoomTime>> entry : byStyle.entrySet())
		{
			// A style can win different rooms with different weapons — magic
			// might use a 3-tick eye of ayak on the tightrope but a 4-tick
			// staff at Olm. One section per style hid the second weapon, which
			// then appeared in the inventory with nothing explaining it. Group
			// by weapon so every weapon you carry has a section naming it.
			Map<Integer, List<RoomTimeEstimator.RoomTime>> byWeapon = new LinkedHashMap<>();
			for (RoomTimeEstimator.RoomTime rt : entry.getValue())
			{
				if (rt.getWeapon() != null)
				{
					byWeapon.computeIfAbsent(rt.getWeapon().getItemId(), k -> new ArrayList<>()).add(rt);
				}
			}
			for (List<RoomTimeEstimator.RoomTime> group : byWeapon.values())
			{
				loadout.styleSections.add(wornForStyle(entry.getKey(), group, primary,
					primaryPicks, primaryWeapon, advice, resolver, items, includeGroupStorage));
			}
		}
		return loadout;
	}

	/**
	 * The eleven slots as they will look while fighting with one style: the
	 * base outfit, with the switches you are actually carrying swapped in.
	 */
	private static SetupBuilder.Section wornForStyle(
		GearNeed style,
		List<RoomTimeEstimator.RoomTime> styleTimes,
		GearNeed primary,
		Map<GearSlot, SetupBuilder.Pick> primaryPicks,
		SetupBuilder.Pick primaryWeapon,
		List<SwitchAdvisor.Advice> advice,
		GearResolver resolver,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage)
	{
		StringBuilder rooms = new StringBuilder();
		for (RoomTimeEstimator.RoomTime rt : styleTimes)
		{
			if (rooms.length() > 0)
			{
				rooms.append(", ");
			}
			rooms.append(rt.getDisplayName());
		}
		SetupBuilder.Section section = new SetupBuilder.Section(
			style.getDisplayName()
				+ (weaponName(styleTimes) == null ? "" : " with " + weaponName(styleTimes))
				+ " — while fighting " + rooms);

		// Each group is already one weapon, so take it from the rooms rather
		// than assuming the style's busiest weapon covers them all.
		SetupBuilder.Pick weapon = mainWeapon(styleTimes);
		boolean twoHanded = weapon != null && weapon.getOption().isTwoHanded();
		boolean wornWeapon = weapon != null && primaryWeapon != null
			&& weapon.getItemId() == primaryWeapon.getItemId();

		// Only switches that are actually carried change anything
		Map<GearSlot, SetupBuilder.Pick> swapped = new LinkedHashMap<>();
		if (style != primary)
		{
			Map<GearSlot, SetupBuilder.Pick> stylePicks =
				picksFor(resolver, style, items, includeGroupStorage);
			for (SwitchAdvisor.Advice a : advice)
			{
				if (a.getStyle() == style && a.isWorthIt() && !a.isAlreadyShared())
				{
					SetupBuilder.Pick pick = stylePicks.get(a.getSlot());
					if (pick != null)
					{
						swapped.put(a.getSlot(), pick);
					}
				}
			}
		}

		for (GearSlot slot : GearSlot.values())
		{
			if (slot == GearSlot.WEAPON)
			{
				section.getLines().add(line(slot, weapon,
					weapon == null ? "(empty)" : null,
					wornWeapon ? "worn" : "from inventory"));
				continue;
			}
			if (slot == GearSlot.SHIELD && twoHanded)
			{
				section.getLines().add(new SetupBuilder.Line(
					slot.getDisplayName(), "— (two-handed weapon)", null, false, 0));
				continue;
			}
			if (slot == GearSlot.AMMO)
			{
				// A bow that takes no ammo (bofa, crystal bow) doesn't empty the
				// slot — whatever the base outfit wears there stays on.
				SetupBuilder.Pick ammo = null;
				boolean withBow = false;
				if (weapon != null && style == GearNeed.RANGED)
				{
					ammo = RoomTimeEstimator.findAmmo(weapon.getItemId(), items, includeGroupStorage);
					withBow = ammo != null;
				}
				if (ammo == null)
				{
					ammo = primaryPicks.get(GearSlot.AMMO);
				}
				section.getLines().add(line(slot, ammo, "(empty)", withBow ? "with the bow" : "worn"));
				continue;
			}

			SetupBuilder.Pick swap = swapped.get(slot);
			SetupBuilder.Pick worn = primaryPicks.get(slot);
			section.getLines().add(swap != null
				? line(slot, swap, null, "SWAP IN")
				: line(slot, worn, "(empty)", "stays on"));
		}
		return section;
	}

	/** Name of the single weapon a section's rooms are fought with. */
	private static String weaponName(List<RoomTimeEstimator.RoomTime> styleTimes)
	{
		SetupBuilder.Pick weapon = mainWeapon(styleTimes);
		return weapon == null ? null : weapon.getOption().getName();
	}

	private static SetupBuilder.Line line(GearSlot slot, SetupBuilder.Pick pick,
		String emptyText, String note)
	{
		if (pick == null)
		{
			return new SetupBuilder.Line(slot.getDisplayName(),
				emptyText == null ? "(empty)" : emptyText, null, false, 0);
		}
		return new SetupBuilder.Line(slot.getDisplayName(),
			pick.getOption().getName() + " — " + note, pick.getSource(), false, pick.getQuantity());
	}

	/** Appends a charge warning to a note when the item is owned uncharged. */
	private static String charge(Set<Integer> needsCharging, int itemId, String note)
	{
		return needsCharging.contains(itemId) ? note + " — CHARGE IT FIRST" : note;
	}

	/** Stats-scanned picks when a resolver is available, curated list otherwise. */
	private static Map<GearSlot, SetupBuilder.Pick> picksFor(
		GearResolver resolver,
		GearNeed style,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage)
	{
		return resolver != null
			? resolver.resolve(style, items, includeGroupStorage)
			: SetupBuilder.resolveLoadout(style, items, includeGroupStorage);
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
				else
				{
					lines.add(new SetupBuilder.Line(slot.getDisplayName(), "(empty)", null, false, 0));
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
				// A non-ranged base outfit still wears whatever sits in the
				// ammo slot (a blessing, say), and the per-style sections say
				// so — the equipped list has to agree.
				SetupBuilder.Pick ammo = weapon != null && primary == GearNeed.RANGED
					? RoomTimeEstimator.findAmmo(weapon.getItemId(), items, includeGroupStorage)
					: picks.get(GearSlot.AMMO);
				if (ammo != null)
				{
					lines.add(new SetupBuilder.Line(slot.getDisplayName(),
						ammo.getOption().getName(), ammo.getSource(), false, ammo.getQuantity()));
				}
				else
				{
					lines.add(new SetupBuilder.Line(slot.getDisplayName(), "(empty)", null, false, 0));
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
					"nothing you own — BiS to chase: " + best, null, true, 0));
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
		for (Map.Entry<GearSlot, SetupBuilder.Pick> entry : picks.entrySet())
		{
			// The style's ideal WEAPON is not necessarily the one equipped —
			// the equipped one comes from the busiest room and is passed in
			// separately. Treating the ideal as worn made the real weapon look
			// already-carried, so it was dropped from the inventory entirely.
			if (entry.getKey() == GearSlot.WEAPON || entry.getValue() == null)
			{
				continue;
			}
			ids.add(entry.getValue().getItemId());
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
		boolean includeGroupStorage,
		Set<Integer> needsCharging)
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
				charge(needsCharging, weapon.getItemId(),
					style.getDisplayName().toLowerCase() + " weapon"), false));

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
