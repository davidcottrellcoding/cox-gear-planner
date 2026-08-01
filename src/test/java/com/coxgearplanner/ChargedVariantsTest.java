package com.coxgearplanner;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChargedVariantsTest
{
	@Test
	public void unchargedItemsResolveToTheirChargedForm()
	{
		assertEquals(31113, ChargedVariants.canonical(31115)); // Eye of ayak
		assertEquals(22325, ChargedVariants.canonical(22486)); // Scythe of vitur
		assertEquals(27275, ChargedVariants.canonical(27277)); // Tumeken's shadow
		assertEquals(22323, ChargedVariants.canonical(22481)); // Sanguinesti staff
		assertEquals(25865, ChargedVariants.canonical(25862)); // Bow of faerdhinen
		assertEquals(23971, ChargedVariants.canonical(23973)); // Crystal helm (inactive)
		assertEquals(28951, ChargedVariants.canonical(28947)); // Dizana's quiver
		assertEquals(28810, ChargedVariants.canonical(28813)); // Zombie axe (broken)
	}

	@Test
	public void alreadyChargedItemsPassThroughUnchanged()
	{
		assertEquals(22325, ChargedVariants.canonical(22325));
		assertEquals(20997, ChargedVariants.canonical(20997)); // Twisted bow
		assertEquals(6585, ChargedVariants.canonical(6585));   // Amulet of fury
		assertFalse(ChargedVariants.needsCharging(22325));
		assertTrue(ChargedVariants.needsCharging(22486));
	}

	@Test
	public void everyMappingResolvesToAFullyChargedItem()
	{
		for (Map.Entry<Integer, Integer> entry : ChargedVariants.all().entrySet())
		{
			assertFalse("id maps to itself: " + entry.getKey(),
				entry.getKey().equals(entry.getValue()));

			// canonical() resolves chains, so its result must be a fixed point
			int resolved = ChargedVariants.canonical(entry.getKey());
			assertEquals("canonical() did not fully resolve " + entry.getKey(),
				resolved, ChargedVariants.canonical(resolved));
		}
	}

	@Test
	public void coversTheChargedWeaponsThatMatterInCox()
	{
		// Named explicitly because these are the expensive ones people bank
		// uncharged between raids.
		assertEquals(12926, ChargedVariants.canonical(12924)); // Toxic blowpipe
		assertEquals(27275, ChargedVariants.canonical(27277)); // Tumeken's shadow
		assertEquals(31113, ChargedVariants.canonical(31115)); // Eye of ayak
		assertEquals(25865, ChargedVariants.canonical(25862)); // Bow of faerdhinen
		assertEquals(23995, ChargedVariants.canonical(23997)); // Blade of saeldor
		assertEquals(22323, ChargedVariants.canonical(22481)); // Sanguinesti staff
		assertEquals(22325, ChargedVariants.canonical(22486)); // Scythe of vitur
		assertEquals(12899, ChargedVariants.canonical(12900)); // Trident of the swamp
		assertEquals(11905, ChargedVariants.canonical(11908)); // Trident of the seas
		assertEquals(27610, ChargedVariants.canonical(27612)); // Venator bow
		assertEquals(28922, ChargedVariants.canonical(28919)); // Tonalztics of ralos

		// Armour and capes that degrade or break rather than discharge
		assertEquals(21295, ChargedVariants.canonical(21287)); // Infernal cape
		assertEquals(22109, ChargedVariants.canonical(21914)); // Ava's assembler
		assertEquals(22322, ChargedVariants.canonical(22441)); // Avernic defender
		assertEquals(4732, ChargedVariants.canonical(4932));   // Karil's coif
	}

	@Test
	public void chargedTargetsAreFindableInTheGearDatabase()
	{
		// Every charged target that the curated lists reference should be
		// reachable, so an uncharged item resolves to a real option.
		Set<Integer> databaseIds = new HashSet<>();
		for (GearNeed style : GearNeed.values())
		{
			Map<GearSlot, java.util.List<ItemOption>> loadout = GearDatabase.loadout(style);
			if (loadout == null)
			{
				continue;
			}
			for (java.util.List<ItemOption> options : loadout.values())
			{
				for (ItemOption option : options)
				{
					for (int id : option.getItemIds())
					{
						databaseIds.add(id);
					}
				}
			}
		}

		// Spot-check the ones the curated lists do carry
		assertTrue(databaseIds.contains(ChargedVariants.canonical(22486))); // scythe
		assertTrue(databaseIds.contains(ChargedVariants.canonical(25862))); // bofa
		assertTrue(databaseIds.contains(ChargedVariants.canonical(27277))); // shadow
		assertTrue(databaseIds.contains(ChargedVariants.canonical(11284))); // dragonfire shield
		assertTrue(databaseIds.contains(ChargedVariants.canonical(22003))); // dragonfire ward
	}
}
