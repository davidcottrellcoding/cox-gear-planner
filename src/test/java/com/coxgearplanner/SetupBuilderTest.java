package com.coxgearplanner;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SetupBuilderTest
{
	private static Map<ItemSource, Map<Integer, Integer>> items()
	{
		Map<ItemSource, Map<Integer, Integer>> items = new EnumMap<>(ItemSource.class);
		for (ItemSource source : ItemSource.values())
		{
			items.put(source, new HashMap<>());
		}
		return items;
	}

	@Test
	public void picksBankItemAndFlagsSource()
	{
		Map<ItemSource, Map<Integer, Integer>> items = items();
		items.get(ItemSource.BANK).put(20997, 1); // Twisted bow

		List<SetupBuilder.Section> sections = SetupBuilder.build(
			EnumSet.of(CoxRoom.VESPULA), items, true);

		SetupBuilder.Section ranged = sections.get(0);
		SetupBuilder.Line weapon = ranged.getLines().get(0);
		assertEquals("Weapon", weapon.getLabel());
		assertEquals("Twisted bow", weapon.getItemName());
		assertEquals(ItemSource.BANK, weapon.getSource());
	}

	@Test
	public void twoHandedWeaponSuppressesShield()
	{
		Map<ItemSource, Map<Integer, Integer>> items = items();
		items.get(ItemSource.BANK).put(20997, 1); // Twisted bow (2h)
		items.get(ItemSource.BANK).put(21000, 1); // Twisted buckler

		List<SetupBuilder.Section> sections = SetupBuilder.build(
			EnumSet.of(CoxRoom.VESPULA), items, true);

		SetupBuilder.Line shield = sections.get(0).getLines().get(1);
		assertEquals("Shield", shield.getLabel());
		assertTrue(shield.getItemName().contains("two-handed"));
	}

	@Test
	public void groupStorageRespectsToggle()
	{
		Map<ItemSource, Map<Integer, Integer>> items = items();
		items.get(ItemSource.GROUP_STORAGE).put(20997, 1); // Twisted bow only in group storage

		List<SetupBuilder.Section> with = SetupBuilder.build(
			EnumSet.of(CoxRoom.VESPULA), items, true);
		assertEquals(ItemSource.GROUP_STORAGE, with.get(0).getLines().get(0).getSource());

		List<SetupBuilder.Section> without = SetupBuilder.build(
			EnumSet.of(CoxRoom.VESPULA), items, false);
		assertTrue(without.get(0).getLines().get(0).isMissing());
	}

	@Test
	public void onHandGearBeatsBankCopy()
	{
		Map<ItemSource, Map<Integer, Integer>> items = items();
		items.get(ItemSource.BANK).put(22325, 1); // Scythe in bank
		items.get(ItemSource.EQUIPMENT).put(22325, 1); // and equipped

		List<SetupBuilder.Section> sections = SetupBuilder.build(
			EnumSet.of(CoxRoom.TEKTON), items, true);

		assertEquals(ItemSource.EQUIPMENT, sections.get(0).getLines().get(0).getSource());
	}

	@Test
	public void guardiansAddsPickaxeUtility()
	{
		Map<ItemSource, Map<Integer, Integer>> items = items();
		items.get(ItemSource.GROUP_STORAGE).put(11920, 1); // Dragon pickaxe

		List<SetupBuilder.Section> sections = SetupBuilder.build(
			EnumSet.of(CoxRoom.GUARDIANS), items, true);

		SetupBuilder.Section utilities = sections.get(sections.size() - 1);
		assertEquals("Utility items", utilities.getTitle());
		assertFalse(utilities.getLines().isEmpty());
		assertEquals("Dragon pickaxe", utilities.getLines().get(0).getItemName());
		assertEquals(ItemSource.GROUP_STORAGE, utilities.getLines().get(0).getSource());
	}

	@Test
	public void missingGearShowsRecommendation()
	{
		List<SetupBuilder.Section> sections = SetupBuilder.build(
			EnumSet.of(CoxRoom.TEKTON), items(), true);

		SetupBuilder.Line weapon = sections.get(0).getLines().get(0);
		assertTrue(weapon.isMissing());
		assertTrue(weapon.getItemName().contains("Scythe of vitur"));
	}

	@Test
	public void vanguardsNeedsAllThreeStyles()
	{
		List<SetupBuilder.Section> sections = SetupBuilder.build(
			EnumSet.of(CoxRoom.VANGUARDS), items(), true);

		assertEquals(3, sections.size()); // melee, ranged, magic; no utilities
		assertTrue(sections.get(0).getTitle().startsWith("Melee"));
		assertTrue(sections.get(1).getTitle().startsWith("Ranged"));
		assertTrue(sections.get(2).getTitle().startsWith("Magic"));
	}
}
