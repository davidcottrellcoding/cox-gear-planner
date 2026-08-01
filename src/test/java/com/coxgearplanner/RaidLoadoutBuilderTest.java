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
	public void styleSectionsOnlyNameGearYouAreActuallyBringing()
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

		// Everything the plan says you can wear, anywhere
		java.util.Set<String> available = new java.util.HashSet<>();
		loadout.getEquipped().forEach(l -> available.add(strip(l.getItemName())));
		loadout.getInventory().forEach(e -> available.add(strip(e.getName())));

		assertFalse("style sections are produced", loadout.getStyleSections().isEmpty());
		for (SetupBuilder.Section section : loadout.getStyleSections())
		{
			assertEquals("every section covers all 11 slots",
				GearSlot.values().length, section.getLines().size());

			for (SetupBuilder.Line line : section.getLines())
			{
				String item = strip(line.getItemName());
				if (item.startsWith("(empty)") || item.startsWith("—"))
				{
					continue; // an empty or suppressed slot names no item
				}
				assertTrue("style section names '" + item
						+ "', which is neither equipped nor in the inventory",
					available.contains(item));
			}
		}

		// The skipped switch must not appear as something you wear
		for (SetupBuilder.Section section : loadout.getStyleSections())
		{
			for (SetupBuilder.Line line : section.getLines())
			{
				assertFalse("a skipped switch is being described as worn",
					strip(line.getItemName()).equals("Primordial boots"));
			}
		}
	}

	/** Style-section lines carry a " — stays on"/" — SWAP IN" note. */
	private static String strip(String itemName)
	{
		int dash = itemName.indexOf(" — ");
		return dash < 0 ? itemName : itemName.substring(0, dash);
	}

	@Test
	public void aStyleUsingTwoWeaponsGetsASectionForEach()
	{
		// Magic can win one room with a 3-tick eye of ayak and another with a
		// 4-tick staff (e.g. once Olm is forced to 4-tick). Both weapons land
		// in the inventory, so both need a section explaining where they go.
		int EYE = 31113;
		int SANG = 22323;
		Map<ItemSource, Map<Integer, Integer>> items = bankWith(EYE, SANG, TBOW, DRAGON_ARROW);

		SetupBuilder.Pick eye = pick(items, ItemOption.of("Eye of ayak", EYE));
		SetupBuilder.Pick sang = pick(items, ItemOption.of("Sanguinesti staff", SANG));
		SetupBuilder.Pick tbow = pick(items, ItemOption.twoHanded("Twisted bow", TBOW));

		List<RoomTimeEstimator.RoomTime> times = new ArrayList<>(Arrays.asList(
			new RoomTimeEstimator.RoomTime(CoxRoom.OLM, "tbow", 200, true, GearNeed.RANGED, tbow),
			new RoomTimeEstimator.RoomTime(CoxRoom.TIGHTROPE, "eye", 40, true, GearNeed.MAGIC, eye),
			new RoomTimeEstimator.RoomTime(CoxRoom.OLM, "sang", 90, true, GearNeed.MAGIC, sang)));

		RaidLoadoutBuilder.RaidLoadout loadout = RaidLoadoutBuilder.build(
			EnumSet.of(CoxRoom.OLM, CoxRoom.TIGHTROPE), times, new ArrayList<>(),
			GearNeed.RANGED, items, true, null);

		// Both magic weapons are carried...
		List<String> inventory = loadout.getInventory().stream()
			.map(RaidLoadoutBuilder.Entry::getName)
			.collect(Collectors.toList());
		assertTrue(inventory.contains("Eye of ayak"));
		assertTrue(inventory.contains("Sanguinesti staff"));

		// ...and each one has a section naming it, so nothing in the inventory
		// is left unexplained
		List<String> titles = loadout.getStyleSections().stream()
			.map(SetupBuilder.Section::getTitle)
			.collect(Collectors.toList());
		assertTrue("eye of ayak has its own section",
			titles.stream().anyMatch(t -> t.contains("Eye of ayak")));
		assertTrue("sanguinesti has its own section",
			titles.stream().anyMatch(t -> t.contains("Sanguinesti staff")));

		// Every carried weapon appears as the weapon line of some section
		java.util.Set<String> sectionWeapons = new java.util.HashSet<>();
		for (SetupBuilder.Section section : loadout.getStyleSections())
		{
			for (SetupBuilder.Line line : section.getLines())
			{
				if ("Weapon".equals(line.getLabel()))
				{
					sectionWeapons.add(strip(line.getItemName()));
				}
			}
		}
		assertTrue(sectionWeapons.contains("Eye of ayak"));
		assertTrue(sectionWeapons.contains("Sanguinesti staff"));
		assertTrue(sectionWeapons.contains("Twisted bow"));
	}

	@Test
	public void aSecondWeaponOfTheBaseStyleIsStillCarried()
	{
		// Regression: with a magic base outfit, resolve(MAGIC) names the
		// shadow as the style's ideal weapon while the EQUIPPED weapon comes
		// from the busiest room (a trident). The shadow was then treated as
		// "already worn" and dropped from the inventory, even though three
		// rooms used it and it had its own section.
		int SHADOW = 27275;
		int TRIDENT = 12899;
		Map<ItemSource, Map<Integer, Integer>> items = bankWith(SHADOW, TRIDENT);

		SetupBuilder.Pick shadow = pick(items, ItemOption.twoHanded("Tumeken's shadow", SHADOW));
		SetupBuilder.Pick trident = pick(items, ItemOption.of("Trident of the swamp", TRIDENT));

		List<RoomTimeEstimator.RoomTime> times = new ArrayList<>(Arrays.asList(
			// The trident room is the longest, so it becomes the worn weapon
			new RoomTimeEstimator.RoomTime(CoxRoom.OLM, "trident", 220, true, GearNeed.MAGIC, trident),
			new RoomTimeEstimator.RoomTime(CoxRoom.TIGHTROPE, "shadow", 24, true, GearNeed.MAGIC, shadow)));

		RaidLoadoutBuilder.RaidLoadout loadout = RaidLoadoutBuilder.build(
			EnumSet.of(CoxRoom.OLM, CoxRoom.TIGHTROPE), times, new ArrayList<>(),
			GearNeed.MAGIC, items, true, null);

		java.util.Set<String> available = new java.util.HashSet<>();
		loadout.getEquipped().forEach(l -> available.add(strip(l.getItemName())));
		loadout.getInventory().forEach(e -> available.add(strip(e.getName())));

		assertTrue("the equipped weapon is the busiest room's",
			available.contains("Trident of the swamp"));
		assertTrue("the style's other weapon must still be carried",
			available.contains("Tumeken's shadow"));

		// And the invariant holds for every section, ammo slot included
		for (SetupBuilder.Section section : loadout.getStyleSections())
		{
			for (SetupBuilder.Line line : section.getLines())
			{
				String item = strip(line.getItemName());
				if (item.startsWith("(empty)") || item.startsWith("—"))
				{
					continue;
				}
				assertTrue("section names '" + item + "' which is not equipped or carried",
					available.contains(item));
			}
		}
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
