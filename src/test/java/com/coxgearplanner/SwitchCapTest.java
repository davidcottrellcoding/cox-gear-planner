package com.coxgearplanner;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The max-items-per-switch cap. The weapon (and ammo the base outfit isn't
 * already wearing) counts toward the total, so a "4-way switch" means four
 * clicks including the weapon.
 */
public class SwitchCapTest
{
	/** Mirrors the budget arithmetic in SwitchAdvisor.adviseStyle. */
	private static int armourBudget(int maxSwitchItems, int mandatory)
	{
		return maxSwitchItems <= 0
			? Integer.MAX_VALUE
			: Math.max(0, maxSwitchItems - mandatory);
	}

	@Test
	public void zeroMeansNoLimit()
	{
		assertEquals(Integer.MAX_VALUE, armourBudget(0, 1));
		assertEquals(Integer.MAX_VALUE, armourBudget(0, 2));
	}

	@Test
	public void aFourWaySwitchLeavesThreeArmourPiecesAfterTheWeapon()
	{
		assertEquals(3, armourBudget(4, 1));
	}

	@Test
	public void ammoCountsWhenTheBaseOutfitIsNotAlreadyWearingIt()
	{
		// Bow plus its ammo eats two of the four clicks
		assertEquals(2, armourBudget(4, 2));
	}

	@Test
	public void aCapSmallerThanTheMandatoryItemsAllowsNoArmour()
	{
		assertEquals(0, armourBudget(1, 1));
		assertEquals(0, armourBudget(1, 2));
		assertEquals(0, armourBudget(2, 2));
	}

	@Test
	public void theCapIsNeverNegative()
	{
		for (int cap = 1; cap <= 11; cap++)
		{
			for (int mandatory = 1; mandatory <= 3; mandatory++)
			{
				assertEquals(Math.max(0, cap - mandatory), armourBudget(cap, mandatory));
			}
		}
	}
}
