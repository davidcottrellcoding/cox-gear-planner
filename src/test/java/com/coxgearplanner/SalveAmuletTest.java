package com.coxgearplanner;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SalveAmuletTest
{
	@Test
	public void imbuedSalvesBoostAllStylesPlainOnesOnlyMelee()
	{
		EquipmentTotals ei = new EquipmentTotals();
		RoomTimeEstimator.addSalveBonus(ei, RoomTimeEstimator.SALVE_EI);
		assertEquals(1.20, ei.salveMeleeMult, 1e-9);
		assertEquals(1.20, ei.salveRangedMagicMult, 1e-9);
		assertEquals(1.20, ei.salveMagicMult, 1e-9);

		EquipmentTotals i = new EquipmentTotals();
		RoomTimeEstimator.addSalveBonus(i, RoomTimeEstimator.SALVE_I);
		assertEquals(1.1667, i.salveMeleeMult, 1e-4);
		assertEquals(1.1667, i.salveRangedMagicMult, 1e-4);
		assertEquals("the (i) gives magic only 15%", 1.15, i.salveMagicMult, 1e-9);

		// Non-imbued: melee only, ranged/magic untouched
		EquipmentTotals e = new EquipmentTotals();
		RoomTimeEstimator.addSalveBonus(e, RoomTimeEstimator.SALVE_E);
		assertEquals(1.20, e.salveMeleeMult, 1e-9);
		assertEquals(1.0, e.salveRangedMagicMult, 1e-9);

		EquipmentTotals plain = new EquipmentTotals();
		RoomTimeEstimator.addSalveBonus(plain, RoomTimeEstimator.SALVE);
		assertEquals(1.1667, plain.salveMeleeMult, 1e-4);
		assertEquals(1.0, plain.salveRangedMagicMult, 1e-9);

		// An unrelated amulet leaves both multipliers neutral
		EquipmentTotals fury = new EquipmentTotals();
		RoomTimeEstimator.addSalveBonus(fury, 6585);
		assertEquals(1.0, fury.salveMeleeMult, 1e-9);
		assertEquals(1.0, fury.salveRangedMagicMult, 1e-9);
	}

	/**
	 * The Soul Wars imbues are distinct item ids with identical effects. When
	 * they were unknown, an (ei) imbued at Soul Wars was invisible and the
	 * search fell through to an old melee-only (e) — which then "lost 6.8s" on
	 * a magic loadout it never claimed to help.
	 */
	@Test
	public void soulWarsImbuesCountAsTheRealThing()
	{
		EquipmentTotals ei = new EquipmentTotals();
		RoomTimeEstimator.addSalveBonus(ei, RoomTimeEstimator.SALVE_EI_SW);
		assertEquals(1.20, ei.salveMeleeMult, 1e-9);
		assertEquals(1.20, ei.salveRangedMagicMult, 1e-9);
		assertEquals(1.20, ei.salveMagicMult, 1e-9);

		EquipmentTotals i = new EquipmentTotals();
		RoomTimeEstimator.addSalveBonus(i, RoomTimeEstimator.SALVE_I_SW);
		assertEquals(1.1667, i.salveRangedMagicMult, 1e-4);
		assertEquals(1.15, i.salveMagicMult, 1e-9);

		// A Soul Wars (ei) outranks an old (e) sitting in the same bank
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(RoomTimeEstimator.SALVE_E, 1);
		bank.put(RoomTimeEstimator.SALVE_EI_SW, 1);
		Map<ItemSource, Map<Integer, Integer>> items = new EnumMap<>(ItemSource.class);
		items.put(ItemSource.BANK, bank);

		SetupBuilder.Pick pick = RoomTimeEstimator.findSalve(items, true);
		assertEquals(RoomTimeEstimator.SALVE_EI_SW, pick.getItemId());
		assertEquals("Salve amulet (ei)", pick.getOption().getName());
	}

	@Test
	public void findSalvePrefersTheBestOwnedVariant()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(RoomTimeEstimator.SALVE, 1);
		bank.put(RoomTimeEstimator.SALVE_EI, 1);
		Map<ItemSource, Map<Integer, Integer>> items = new EnumMap<>(ItemSource.class);
		items.put(ItemSource.BANK, bank);

		SetupBuilder.Pick pick = RoomTimeEstimator.findSalve(items, true);
		assertNotNull(pick);
		assertEquals(RoomTimeEstimator.SALVE_EI, pick.getItemId());
		assertEquals("Salve amulet (ei)", pick.getOption().getName());

		assertNull("no salve owned", RoomTimeEstimator.findSalve(
			new EnumMap<>(ItemSource.class), true));
	}

	@Test
	public void skeletalMysticsAreTheUndeadRoom()
	{
		RoomMonsters.Encounter mystics = RoomMonsters.get(CoxRoom.MYSTICS);
		assertNotNull(mystics);
		assertTrue("skeletal mystics are undead", mystics.getProfile().isUndead());

		// Nothing else in the raid is flagged undead
		for (CoxRoom room : CoxRoom.values())
		{
			RoomMonsters.Encounter encounter = RoomMonsters.get(room);
			if (encounter != null && room != CoxRoom.MYSTICS)
			{
				assertFalse(room + " should not be undead", encounter.getProfile().isUndead());
			}
		}
	}
}
