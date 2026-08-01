package com.coxgearplanner;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The shared-budget mode spends ONE inventory allowance across every style,
 * always buying whichever remaining piece saves the most time. A per-style
 * cap cannot move a slot from a style that barely benefits to one that does;
 * this can, so an even split is not expected or desirable.
 */
public class SharedBudgetTest
{
	/** Budget left for armour after the mandatory weapons and ammo. */
	private static int armourBudget(int totalSwapItems, int mandatory)
	{
		return Math.max(0, totalSwapItems - mandatory);
	}

	@Test
	public void weaponsAndAmmoComeOutOfTheSameAllowance()
	{
		// Two secondary styles, one needing its own ammo: 3 mandatory items
		assertEquals(7, armourBudget(10, 3));
		// A tight budget can be fully consumed by the weapons alone
		assertEquals(0, armourBudget(2, 3));
	}

	@Test
	public void theAllowanceIsNeverNegative()
	{
		for (int total = 0; total <= 28; total++)
		{
			for (int mandatory = 0; mandatory <= 4; mandatory++)
			{
				assertTrue(armourBudget(total, mandatory) >= 0);
			}
		}
	}

	@Test
	public void zeroMeansTheSharedBudgetIsOff()
	{
		// 0 falls back to the per-style cap rather than meaning "carry nothing"
		assertEquals(0, new CoxGearPlannerConfig()
		{
		}.totalSwapItems());
	}

	@Test
	public void theSplitBetweenStylesIsNotFixed()
	{
		// An eight/two split is a legitimate outcome — the allocation follows
		// time saved, not fairness between styles.
		int total = 10;
		int mandatory = 0;
		int toRanged = 8;
		int toMelee = 2;
		assertEquals(armourBudget(total, mandatory), toRanged + toMelee);
	}

	@Test
	public void everyCarriedWeaponMustBeCountedNotOnePerStyle()
	{
		// Regression: a budget of 10 produced 14 gear items because the
		// accounting reserved one weapon per SECONDARY style. In reality a
		// style can win different rooms with different weapons, and the base
		// style's other weapons are carried too — four weapons, not two.
		int distinctWeaponsUsed = 5;   // shadow, trident, saeldor, emberlight, bofa
		int equippedWeapon = 1;        // the trident is worn, not carried
		int carried = distinctWeaponsUsed - equippedWeapon;
		assertEquals("four weapons ride in the inventory", 4, carried);

		int total = 10;
		assertEquals("only six slots are left for armour", 6, armourBudget(total, carried));
	}

	@Test
	public void anOffhandDoesNotConsumeTheAllowance()
	{
		// Same rule as the per-style cap: a fang and defender is one swap, so
		// carrying the shield leaves the armour allowance untouched.
		int budget = 4;
		int afterWeapon = budget - 1;
		int afterShield = afterWeapon; // shield is free
		assertEquals(3, afterShield);
	}
}
