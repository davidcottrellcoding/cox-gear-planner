package com.coxgearplanner;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GearDatabaseTest
{
	@Test
	public void noDuplicateIdsWithinAnySlotList()
	{
		for (GearNeed style : GearNeed.values())
		{
			Map<GearSlot, List<ItemOption>> loadout = GearDatabase.loadout(style);
			if (loadout == null)
			{
				continue;
			}
			for (Map.Entry<GearSlot, List<ItemOption>> entry : loadout.entrySet())
			{
				Set<Integer> seen = new HashSet<>();
				for (ItemOption option : entry.getValue())
				{
					for (int id : option.getItemIds())
					{
						assertTrue("id must be positive: " + option.getName(), id > 0);
						assertTrue("duplicate id " + id + " in " + style + " " + entry.getKey(),
							seen.add(id));
					}
				}
			}
		}
	}

	@Test
	public void everyCombatStyleCoversAllArmourSlots()
	{
		for (GearNeed style : GearNeed.values())
		{
			if (!style.isCombatStyle())
			{
				continue;
			}
			Map<GearSlot, List<ItemOption>> loadout = GearDatabase.loadout(style);
			for (GearSlot slot : GearSlot.values())
			{
				if (slot == GearSlot.AMMO && style != GearNeed.RANGED)
				{
					continue;
				}
				assertFalse(style + " has no options for " + slot,
					loadout.getOrDefault(slot, java.util.Collections.emptyList()).isEmpty());
			}
		}
	}

	@Test
	public void fullCrystalSetGivesThirtyPercentAccuracyFifteenDamage()
	{
		EquipmentTotals totals = new EquipmentTotals();
		RoomTimeEstimator.addCrystalSetBonus(totals, 23971); // helm
		RoomTimeEstimator.addCrystalSetBonus(totals, 23975); // body
		RoomTimeEstimator.addCrystalSetBonus(totals, 23979); // legs
		assertEquals(0.30, totals.crystalAcc, 1e-9);
		assertEquals(0.15, totals.crystalDmg, 1e-9);

		// Non-crystal items contribute nothing
		RoomTimeEstimator.addCrystalSetBonus(totals, 11828); // armadyl chestplate
		assertEquals(0.30, totals.crystalAcc, 1e-9);
	}
}
