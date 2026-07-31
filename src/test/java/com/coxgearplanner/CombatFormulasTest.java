package com.coxgearplanner;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CombatFormulasTest
{
	@Test
	public void maxHitMatchesKnownValue()
	{
		// 99 str, piety (1.23), aggressive (+3): floor(99*1.23)=121, +8+3 = 132
		int effStr = CombatFormulas.effectiveLevel(99, 1.23, 3);
		assertEquals(132, effStr);
		// +147 strength bonus: floor(0.5 + 132*211/640) = 44
		assertEquals(44, CombatFormulas.maxHit(effStr, 147));
	}

	@Test
	public void accuracyIsBoundedAndMonotonic()
	{
		double low = CombatFormulas.accuracy(5000, 20000);
		double high = CombatFormulas.accuracy(30000, 20000);
		assertTrue(low > 0 && low < 1);
		assertTrue(high > 0 && high < 1);
		assertTrue(high > low);
	}

	@Test
	public void tbowScalesWithTargetMagic()
	{
		double vsLowMagic = CombatFormulas.tbowDamageMult(50);
		double vsOlm = CombatFormulas.tbowDamageMult(250);
		assertTrue(vsOlm > vsLowMagic);
		// Multipliers never exceed their caps
		assertTrue(CombatFormulas.tbowDamageMult(350) <= 2.5);
		assertTrue(CombatFormulas.tbowAccuracyMult(350) <= 1.4);
	}

	@Test
	public void dpsScalesInverselyWithSpeed()
	{
		double fast = CombatFormulas.dps(0.8, 40, 4);
		double slow = CombatFormulas.dps(0.8, 40, 5);
		assertTrue(fast > slow);
	}

	@Test
	public void everyCombatRoomHasMonsterData()
	{
		for (CoxRoom room : CoxRoom.values())
		{
			boolean needsCombat = room.getNeeds().stream().anyMatch(GearNeed::isCombatStyle);
			if (needsCombat)
			{
				assertNotNull("missing monster data for " + room, RoomMonsters.get(room));
			}
		}
	}
}
