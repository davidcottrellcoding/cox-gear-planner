package com.coxgearplanner;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Same item, different id: ornament kits, alternate imbue sources, and the
 * upgraded blowpipe. Every one of these once made a best-in-slot item
 * invisible to the planner — the salve (ei) saga, generalised.
 */
public class VariantItemsTest
{
	@Test
	public void ornamentKitsCollapseOntoTheBaseItem()
	{
		assertEquals("occult (or)", 12002, ChargedVariants.sameStats(19720));
		assertEquals("torture (or)", 19553, ChargedVariants.sameStats(20366));
		assertEquals("anguish (or)", 19547, ChargedVariants.sameStats(22249));
		assertEquals("tormented (or)", 19544, ChargedVariants.sameStats(23444));
		assertEquals("fury (or)", 6585, ChargedVariants.sameStats(12436));
		assertEquals("dragon defender (t)", 12954, ChargedVariants.sameStats(19722));
		assertEquals("blowpipe ornament stays loaded", 12926, ChargedVariants.sameStats(28688));

		// The empty ornament blowpipe chains: ornament -> empty -> loaded,
		// which is exactly the CHARGE IT FIRST path
		assertEquals(12924, ChargedVariants.sameStats(28687));
		assertEquals(12926, ChargedVariants.canonical(12924));
	}

	@Test
	public void everyImbueSourceCountsAsTheSameRing()
	{
		// Soul Wars and PvP Arena imbues are identical to the NMZ imbue
		for (int id : new int[]{25264, 26770})
		{
			assertEquals("berserker ring (i)", 11773, ChargedVariants.sameStats(id));
		}
		for (int id : new int[]{25258, 26767})
		{
			assertEquals("seers ring (i)", 11770, ChargedVariants.sameStats(id));
		}
		for (int id : new int[]{25260, 26768})
		{
			assertEquals("archers ring (i)", 11771, ChargedVariants.sameStats(id));
		}
		for (int id : new int[]{25252, 26764})
		{
			assertEquals("ring of the gods (i)", 13202, ChargedVariants.sameStats(id));
		}

		// An id with no variant passes through untouched
		assertEquals(11773, ChargedVariants.sameStats(11773));
		assertEquals(4151, ChargedVariants.sameStats(4151));
	}

	@Test
	public void theBlazingBlowpipeBehavesLikeABlowpipe()
	{
		int blazing = 30374;
		assertEquals("empty form counts as owning it, charge first",
			blazing, ChargedVariants.canonical(30373));
		assertEquals("5 tiles — cannot reach the abyssal portal",
			5, RoomTimeEstimator.weaponReach(blazing));
		assertTrue("dart-fed, no quiver ammo", !RoomTimeEstimator.needsAmmo(blazing));
		assertTrue("it is a known ranged weapon",
			GearDatabase.loadout(GearNeed.RANGED).get(GearSlot.WEAPON).stream()
				.anyMatch(o -> o.getName().equals("Blazing blowpipe")));
	}

	@Test
	public void seekingArrowsFeedTheArrowWeapons()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(20997, 1);   // twisted bow
		bank.put(33595, 300); // seeking dragon arrows
		Map<ItemSource, Map<Integer, Integer>> items = new EnumMap<>(ItemSource.class);
		items.put(ItemSource.BANK, bank);

		SetupBuilder.Pick ammo = RoomTimeEstimator.findAmmo(20997, items, true);
		assertNotNull("the tbow must find the seeking arrows", ammo);
		assertEquals(33595, ammo.getItemId());
		assertEquals("Seeking dragon arrow", ammo.getOption().getName());

		// Best-first: seeking dragon outranks plain dragon when both are owned
		bank.put(11212, 300);
		assertEquals(33595, RoomTimeEstimator.findAmmo(20997, items, true).getItemId());
	}
}
