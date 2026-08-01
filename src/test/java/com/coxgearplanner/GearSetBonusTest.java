package com.coxgearplanner;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GearSetBonusTest
{
	private static final int VOID_MELEE_HELM = 11665;
	private static final int VOID_RANGER_HELM = 11664;
	private static final int VOID_MAGE_HELM = 11663;
	private static final int VOID_TOP = 8839;
	private static final int VOID_ROBE = 8840;
	private static final int VOID_GLOVES = 8842;
	private static final int ELITE_TOP = 13072;
	private static final int ELITE_ROBE = 13073;

	private static Set<Integer> worn(int... ids)
	{
		Set<Integer> set = new HashSet<>();
		for (int id : ids)
		{
			set.add(id);
		}
		return set;
	}

	@Test
	public void voidNeedsEveryPieceToPayOut()
	{
		// Three of the four pieces gives nothing
		double[] partial = GearSetBonus.multipliers(
			worn(VOID_MELEE_HELM, VOID_TOP, VOID_ROBE), GearNeed.MELEE, 4151);
		assertEquals(1.0, partial[0], 1e-9);
		assertEquals(1.0, partial[1], 1e-9);

		double[] full = GearSetBonus.multipliers(
			worn(VOID_MELEE_HELM, VOID_TOP, VOID_ROBE, VOID_GLOVES), GearNeed.MELEE, 4151);
		assertEquals(1.10, full[0], 1e-9);
		assertEquals(1.10, full[1], 1e-9);
	}

	@Test
	public void theHelmDecidesWhichStyleVoidBoosts()
	{
		Set<Integer> rangedVoid = worn(VOID_RANGER_HELM, VOID_TOP, VOID_ROBE, VOID_GLOVES);
		assertEquals(1.10, GearSetBonus.multipliers(rangedVoid, GearNeed.RANGED, 20997)[0], 1e-9);
		// Ranger helm does nothing for melee
		assertEquals(1.0, GearSetBonus.multipliers(rangedVoid, GearNeed.MELEE, 4151)[0], 1e-9);
	}

	@Test
	public void magicVoidIsAnAccuracySetAndEliteAddsDamage()
	{
		double[] regular = GearSetBonus.multipliers(
			worn(VOID_MAGE_HELM, VOID_TOP, VOID_ROBE, VOID_GLOVES), GearNeed.MAGIC, 11905);
		assertEquals(1.45, regular[0], 1e-9);
		assertEquals("regular magic void gives no damage bonus", 1.0, regular[1], 1e-9);

		double[] elite = GearSetBonus.multipliers(
			worn(VOID_MAGE_HELM, ELITE_TOP, ELITE_ROBE, VOID_GLOVES), GearNeed.MAGIC, 11905);
		assertEquals(1.45, elite[0], 1e-9);
		assertEquals(1.05, elite[1], 1e-9);
	}

	@Test
	public void eliteVoidAddsRangedDamageOnly()
	{
		double[] elite = GearSetBonus.multipliers(
			worn(VOID_RANGER_HELM, ELITE_TOP, ELITE_ROBE, VOID_GLOVES), GearNeed.RANGED, 20997);
		assertEquals(1.10, elite[0], 1e-9);
		assertEquals(1.125, elite[1], 1e-9);
	}

	@Test
	public void obsidianArmourNeedsAnObsidianWeapon()
	{
		Set<Integer> obsidian = worn(21298, 21301, 21304);
		int obsidianWeapon = GearSetBonus.obsidianWeapons()[0];

		assertEquals(1.10, GearSetBonus.multipliers(obsidian, GearNeed.MELEE, obsidianWeapon)[0], 1e-9);
		// Same armour with a whip gets nothing
		assertEquals(1.0, GearSetBonus.multipliers(obsidian, GearNeed.MELEE, 4151)[0], 1e-9);
	}

	@Test
	public void ownedSetsAreOnlyReportedWhenComplete()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		for (int id : Arrays.asList(VOID_MELEE_HELM, VOID_TOP, VOID_ROBE))
		{
			bank.put(id, 1);
		}
		Map<ItemSource, Map<Integer, Integer>> items = new EnumMap<>(ItemSource.class);
		items.put(ItemSource.BANK, bank);

		assertTrue("incomplete void is not offered",
			GearSetBonus.ownedSets(GearNeed.MELEE, items, true).isEmpty());

		bank.put(VOID_GLOVES, 1);
		List<GearSetBonus.SetOption> sets = GearSetBonus.ownedSets(GearNeed.MELEE, items, true);
		assertEquals(1, sets.size());
		assertEquals(4, sets.get(0).getPieces().size());
	}
}
