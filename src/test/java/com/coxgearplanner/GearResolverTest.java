package com.coxgearplanner;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GearResolverTest
{
	@Test
	public void mapsEveryRealEquipmentSlotIndex()
	{
		assertEquals(GearSlot.HEAD, GearResolver.toGearSlot(0));
		assertEquals(GearSlot.CAPE, GearResolver.toGearSlot(1));
		assertEquals(GearSlot.NECK, GearResolver.toGearSlot(2));
		assertEquals(GearSlot.WEAPON, GearResolver.toGearSlot(3));
		assertEquals(GearSlot.BODY, GearResolver.toGearSlot(4));
		assertEquals(GearSlot.SHIELD, GearResolver.toGearSlot(5));
		assertEquals(GearSlot.LEGS, GearResolver.toGearSlot(7));
		assertEquals(GearSlot.GLOVES, GearResolver.toGearSlot(9));
		assertEquals(GearSlot.BOOTS, GearResolver.toGearSlot(10));
		assertEquals(GearSlot.RING, GearResolver.toGearSlot(12));
		assertEquals(GearSlot.AMMO, GearResolver.toGearSlot(13));

		// Cosmetic-only slots are ignored
		assertNull(GearResolver.toGearSlot(6));  // arms
		assertNull(GearResolver.toGearSlot(8));  // hair
		assertNull(GearResolver.toGearSlot(11)); // jaw
	}

	@Test
	public void withoutItemManagerFallsBackToCuratedList()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(22325, 1); // scythe of vitur
		bank.put(30750, 1); // oathplate helm
		Map<ItemSource, Map<Integer, Integer>> items = new EnumMap<>(ItemSource.class);
		items.put(ItemSource.BANK, bank);

		Map<GearSlot, SetupBuilder.Pick> picks =
			new GearResolver(null).resolve(GearNeed.MELEE, items, true);

		assertEquals("Scythe of vitur", picks.get(GearSlot.WEAPON).getOption().getName());
		assertEquals("Oathplate helm", picks.get(GearSlot.HEAD).getOption().getName());
	}
}
