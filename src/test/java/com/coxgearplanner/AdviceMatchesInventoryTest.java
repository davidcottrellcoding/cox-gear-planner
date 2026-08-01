package com.coxgearplanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The plan must pack what its own advice says to pack.
 *
 * This has broken twice in different ways, both times because something
 * resolved a carried switch through the wrong loadout and then discarded it
 * for looking like an item already worn. The failure is quiet — the switch
 * decisions still promise the item, the inventory simply does not list it —
 * so it survives until someone reads both halves of a plan side by side.
 */
public class AdviceMatchesInventoryTest
{
	private static final int TBOW = 20997;
	private static final int DRAGON_ARROW = 11212;
	private static final int SCYTHE = 22325;
	private static final int RANCOUR = 29801;
	private static final int PRIMORDIAL = 13239;

	private static Map<ItemSource, Map<Integer, Integer>> bankWith(int... ids)
	{
		Map<Integer, Integer> bank = new HashMap<>();
		for (int id : ids)
		{
			bank.put(id, id == DRAGON_ARROW ? 500 : 1);
		}
		Map<ItemSource, Map<Integer, Integer>> items = new EnumMap<>(ItemSource.class);
		items.put(ItemSource.BANK, bank);
		return items;
	}

	private static RaidLoadoutBuilder.RaidLoadout loadoutWith(List<SwitchAdvisor.Advice> advice)
	{
		Map<ItemSource, Map<Integer, Integer>> items =
			bankWith(TBOW, DRAGON_ARROW, SCYTHE, RANCOUR, PRIMORDIAL);

		SetupBuilder.Pick tbow = SetupBuilder.findOwned(
			ItemOption.twoHanded("Twisted bow", TBOW), items, true);
		SetupBuilder.Pick scythe = SetupBuilder.findOwned(
			ItemOption.twoHanded("Scythe of vitur", SCYTHE), items, true);

		List<RoomTimeEstimator.RoomTime> times = new ArrayList<>(Arrays.asList(
			new RoomTimeEstimator.RoomTime(CoxRoom.OLM, "tbow", 200, true, GearNeed.RANGED, tbow),
			new RoomTimeEstimator.RoomTime(CoxRoom.TEKTON, "scythe", 100, true, GearNeed.MELEE, scythe)));

		return RaidLoadoutBuilder.build(EnumSet.of(CoxRoom.OLM, CoxRoom.TEKTON),
			times, advice, GearNeed.RANGED, items, true, null);
	}

	private static Set<String> carriedNames(RaidLoadoutBuilder.RaidLoadout loadout)
	{
		Set<String> names = new HashSet<>();
		for (RaidLoadoutBuilder.Entry entry : loadout.getInventory())
		{
			names.add(entry.getName());
		}
		return names;
	}

	private static Set<String> wornNames(RaidLoadoutBuilder.RaidLoadout loadout)
	{
		Set<String> names = new HashSet<>();
		for (SetupBuilder.Line line : loadout.getEquipped())
		{
			names.add(line.getItemName());
		}
		return names;
	}

	@Test
	public void everySwitchTheAdviceCarriesIsPackedOrAlreadyWorn()
	{
		List<SwitchAdvisor.Advice> advice = Arrays.asList(
			new SwitchAdvisor.Advice(GearNeed.MELEE, GearSlot.NECK,
				"Amulet of rancour", null, 8.0, true, false),
			new SwitchAdvisor.Advice(GearNeed.MELEE, GearSlot.BOOTS,
				"Primordial boots", null, 6.0, true, false));

		RaidLoadoutBuilder.RaidLoadout loadout = loadoutWith(advice);
		Set<String> carried = carriedNames(loadout);
		Set<String> worn = wornNames(loadout);

		for (SwitchAdvisor.Advice a : advice)
		{
			assertTrue(a.getItemName() + " was advised as CARRY but is neither packed nor worn",
				carried.contains(a.getItemName()) || worn.contains(a.getItemName()));
		}
	}

	/**
	 * The converse, so the invariant cannot be satisfied by packing
	 * everything: a switch judged not worth its slot must stay behind.
	 */
	@Test
	public void aSwitchNotWorthCarryingIsNotPacked()
	{
		List<SwitchAdvisor.Advice> advice = Arrays.asList(
			new SwitchAdvisor.Advice(GearNeed.MELEE, GearSlot.BOOTS,
				"Primordial boots", null, 0.5, false, false));

		RaidLoadoutBuilder.RaidLoadout loadout = loadoutWith(advice);

		assertFalse("a switch that lost on value should not be packed",
			carriedNames(loadout).contains("Primordial boots"));
	}
}
