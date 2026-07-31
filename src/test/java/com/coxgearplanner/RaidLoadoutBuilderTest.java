package com.coxgearplanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RaidLoadoutBuilderTest
{
	private static final int TBOW = 20997;
	private static final int DRAGON_ARROW = 11212;
	private static final int SCYTHE = 22325;
	private static final int RANCOUR = 29801;
	private static final int PRIMORDIAL = 13239;
	private static final int DWH = 13576;

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

	private static SetupBuilder.Pick pick(Map<ItemSource, Map<Integer, Integer>> items, ItemOption option)
	{
		return SetupBuilder.findOwned(option, items, true);
	}

	@Test
	public void buildsSingleInventoryLoadout()
	{
		Map<ItemSource, Map<Integer, Integer>> items =
			bankWith(TBOW, DRAGON_ARROW, SCYTHE, RANCOUR, PRIMORDIAL, DWH);

		SetupBuilder.Pick tbow = pick(items, ItemOption.twoHanded("Twisted bow", TBOW));
		SetupBuilder.Pick scythe = pick(items, ItemOption.twoHanded("Scythe of vitur", SCYTHE));

		List<RoomTimeEstimator.RoomTime> times = new ArrayList<>(Arrays.asList(
			new RoomTimeEstimator.RoomTime(CoxRoom.OLM, "tbow", 200, true, GearNeed.RANGED, tbow),
			new RoomTimeEstimator.RoomTime(CoxRoom.TEKTON, "scythe", 100, true, GearNeed.MELEE, scythe)));

		List<SwitchAdvisor.Advice> advice = Arrays.asList(
			new SwitchAdvisor.Advice(GearNeed.MELEE, GearSlot.NECK, "Amulet of rancour", null, 8.0, true, false),
			new SwitchAdvisor.Advice(GearNeed.MELEE, GearSlot.BOOTS, "Primordial boots", null, 0.5, false, false));

		RaidLoadoutBuilder.RaidLoadout loadout = RaidLoadoutBuilder.build(
			EnumSet.of(CoxRoom.OLM, CoxRoom.TEKTON), times, advice, GearNeed.RANGED, items, true, null);

		assertEquals(GearNeed.RANGED, loadout.getPrimaryStyle());

		Map<String, String> equipped = loadout.getEquipped().stream()
			.collect(Collectors.toMap(SetupBuilder.Line::getLabel, SetupBuilder.Line::getItemName));
		assertEquals("Twisted bow", equipped.get("Weapon"));
		assertEquals("Dragon arrow", equipped.get("Ammo"));
		assertEquals("— (two-handed weapon)", equipped.get("Shield"));

		List<String> inventory = loadout.getInventory().stream()
			.map(RaidLoadoutBuilder.Entry::getName)
			.collect(Collectors.toList());
		assertTrue("melee weapon carried", inventory.contains("Scythe of vitur"));
		assertTrue("worth-it switch carried", inventory.contains("Amulet of rancour"));
		assertTrue("utility carried", inventory.contains("Dragon warhammer"));
		assertFalse("skipped switch left behind", inventory.contains("Primordial boots"));

		assertEquals(3, loadout.getUsedSlots());
		assertEquals(25, loadout.getFreeSlots());
	}

	@Test
	public void equippedListAlwaysCoversAllElevenSlots()
	{
		Map<ItemSource, Map<Integer, Integer>> items = bankWith(TBOW, DRAGON_ARROW);
		SetupBuilder.Pick tbow = pick(items, ItemOption.twoHanded("Twisted bow", TBOW));

		List<RoomTimeEstimator.RoomTime> times = new ArrayList<>(Arrays.asList(
			new RoomTimeEstimator.RoomTime(CoxRoom.OLM, "tbow", 200, true, GearNeed.RANGED, tbow)));

		RaidLoadoutBuilder.RaidLoadout loadout = RaidLoadoutBuilder.build(
			EnumSet.of(CoxRoom.OLM), times, new ArrayList<>(), GearNeed.RANGED, items, true, null);

		assertEquals("one line per equipment slot",
			GearSlot.values().length, loadout.getEquipped().size());
		assertEquals(11, GearSlot.values().length);

		// Every slot named exactly once, empty ones included
		Map<String, String> equipped = loadout.getEquipped().stream()
			.collect(Collectors.toMap(SetupBuilder.Line::getLabel, SetupBuilder.Line::getItemName));
		for (GearSlot slot : GearSlot.values())
		{
			assertTrue("missing slot " + slot, equipped.containsKey(slot.getDisplayName()));
		}
	}

	@Test
	public void inventoryCapacityIsTwentyEight()
	{
		assertEquals(28, RaidLoadoutBuilder.INVENTORY_SIZE);

		Map<ItemSource, Map<Integer, Integer>> items = bankWith(TBOW, SCYTHE, DWH);
		SetupBuilder.Pick tbow = pick(items, ItemOption.twoHanded("Twisted bow", TBOW));
		SetupBuilder.Pick scythe = pick(items, ItemOption.twoHanded("Scythe of vitur", SCYTHE));

		List<RoomTimeEstimator.RoomTime> times = new ArrayList<>(Arrays.asList(
			new RoomTimeEstimator.RoomTime(CoxRoom.OLM, "tbow", 200, true, GearNeed.RANGED, tbow),
			new RoomTimeEstimator.RoomTime(CoxRoom.TEKTON, "scythe", 100, true, GearNeed.MELEE, scythe)));

		RaidLoadoutBuilder.RaidLoadout loadout = RaidLoadoutBuilder.build(
			EnumSet.of(CoxRoom.OLM, CoxRoom.TEKTON), times, new ArrayList<>(),
			GearNeed.RANGED, items, true, null);

		assertEquals(RaidLoadoutBuilder.INVENTORY_SIZE,
			loadout.getUsedSlots() + loadout.getFreeSlots());
		assertFalse(loadout.isOverCapacity());
	}

	@Test
	public void missingUtilityIsListedButTakesNoSlot()
	{
		Map<ItemSource, Map<Integer, Integer>> items = bankWith(SCYTHE);
		SetupBuilder.Pick scythe = pick(items, ItemOption.twoHanded("Scythe of vitur", SCYTHE));

		List<RoomTimeEstimator.RoomTime> times = new ArrayList<>(Arrays.asList(
			new RoomTimeEstimator.RoomTime(CoxRoom.GUARDIANS, "scythe", 90, true, GearNeed.MELEE, scythe)));

		RaidLoadoutBuilder.RaidLoadout loadout = RaidLoadoutBuilder.build(
			EnumSet.of(CoxRoom.GUARDIANS), times, new ArrayList<>(), GearNeed.MELEE, items, true, null);

		boolean missingPickaxe = loadout.getInventory().stream()
			.anyMatch(e -> e.isMissing() && e.getNote().contains("ickaxe"));
		assertTrue("missing pickaxe flagged", missingPickaxe);
		assertEquals(0, loadout.getUsedSlots());
	}

	@Test
	public void noFeasibleRoomsMeansNoLoadout()
	{
		assertNull(RaidLoadoutBuilder.build(
			EnumSet.of(CoxRoom.OLM), new ArrayList<>(), new ArrayList<>(),
			null, bankWith(), true, null));
	}
}
