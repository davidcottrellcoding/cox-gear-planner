package com.coxgearplanner;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The scythe's hit chain, checked against a GearScape reading of max 81 for a
 * 47 first hit: 47 + 23 + 11. Each hit is half the previous one ROUNDED DOWN,
 * and the number of hits depends on the target's tile size — so a flat 1.75x
 * multiplier is wrong twice over.
 */
public class ScytheHitsTest
{
	private static int chain(int max, int hits)
	{
		int hit = max;
		int total = hit;
		for (int h = 1; h < hits; h++)
		{
			hit = hit / 2;
			total += hit;
		}
		return total;
	}

	@Test
	public void theChainMatchesGearscape()
	{
		// GearScape showed "Max Hit: 81 (47+23+11)"
		assertEquals(81, chain(47, 3));
		// A flat 1.75x would have claimed 82.25
		assertEquals(82.25, 47 * 1.75, 1e-9);
	}

	@Test
	public void flooringCompoundsThroughTheChain()
	{
		// 47 -> 23 -> 11, not 47 -> 23.5 -> 11.75
		assertEquals(23, 47 / 2);
		assertEquals(11, 23 / 2);
		// An odd max loses a little at every step
		assertEquals(99 + 49 + 24, chain(99, 3));
	}

	@Test
	public void hitCountFollowsTileSize()
	{
		// 3x3 and larger take three hits
		assertEquals(3, RoomMonsters.get(CoxRoom.TEKTON).getProfile().scytheHits());
		// Skeletal mystics are 2x2, so only two
		assertEquals(2, RoomMonsters.get(CoxRoom.MYSTICS).getProfile().scytheHits());
		// The tightrope pair are 1x1, so one
		assertEquals(1, RoomMonsters.getAll(CoxRoom.TIGHTROPE).get(0).getProfile().scytheHits());
	}

	@Test
	public void theVasaCrystalTakesThreeHitsBecauseItIsFourByFour()
	{
		MonsterProfile crystal = null;
		for (RoomMonsters.Encounter e : RoomMonsters.getAll(CoxRoom.VASA))
		{
			if (e.getProfile().getName().contains("crystal"))
			{
				crystal = e.getProfile();
			}
		}
		assertEquals(3, crystal.scytheHits());
	}
}
