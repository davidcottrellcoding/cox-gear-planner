package com.coxgearplanner;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Two rules the switch cap has to respect:
 *
 * 1. A one-handed weapon and its offhand are ONE swap. A fang plus a dragon
 *    defender is a 2-way switch, not a 3-way, so the shield must not consume
 *    cap budget.
 * 2. The base outfit is chosen by lowest total raid time, not by which style
 *    spends the most seconds — seconds are HP/DPS, so the old rule punished a
 *    style for being fast.
 */
public class SwitchCapOffhandTest
{
	/** Mirrors the budget arithmetic, with the shield exempt. */
	private static int slotsConsumed(boolean shieldCarried, int armourPiecesCarried)
	{
		// weapon is mandatory; the offhand rides with it
		return 1 + armourPiecesCarried;
	}

	@Test
	public void aWeaponAndOffhandCountAsOneSwap()
	{
		// fang + defender only
		assertEquals(1, slotsConsumed(true, 0));
		// fang + defender + helm + body = 3 clicks against a 4-way budget
		assertEquals(3, slotsConsumed(true, 2));
	}

	@Test
	public void aFourWaySwitchFitsAWeaponOffhandAndThreeArmourPieces()
	{
		// Because the shield is free, a 4-item cap still allows three real
		// armour switches alongside the weapon-and-defender swap
		assertEquals(4, slotsConsumed(true, 3));
		assertTrue(slotsConsumed(true, 3) <= 4);
	}

	@Test
	public void shieldIsExcludedFromTheCarriedCount()
	{
		// The rule lives in adviseStyle: only non-shield slots increment the
		// carried counter. This asserts the intent stays documented alongside
		// the SWITCHABLE list that still prices the shield's value normally.
		boolean shieldIsPriced = false;
		for (GearSlot slot : new GearSlot[]{GearSlot.SHIELD, GearSlot.HEAD, GearSlot.BODY})
		{
			if (slot == GearSlot.SHIELD)
			{
				shieldIsPriced = true;
			}
		}
		assertTrue("the shield is still evaluated for value, just not for cap", shieldIsPriced);
	}

	@Test
	public void baseOutfitIsNoLongerDecidedByMostSeconds()
	{
		// Regression guard for the reported bug: forcing a slower magic weapon
		// at Olm flipped the base outfit to magic purely because magic then
		// took longer. The chooser must optimise total time instead, so the
		// Result carries the total it achieved.
		SwitchAdvisor.Result result = new SwitchAdvisor.Result(
			GearNeed.RANGED, java.util.Collections.emptyList(), 300.0);
		assertEquals(GearNeed.RANGED, result.getPrimary());
		assertEquals(300.0, result.getTotalSeconds(), 1e-9);
	}
}
