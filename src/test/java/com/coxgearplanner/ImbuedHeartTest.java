package com.coxgearplanner;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The imbued heart boosts Magic by +1 plus 10% of the level. Boosts do not
 * stack in OSRS — each sets an absolute level and the highest wins — and a
 * CoX overload beats the heart at every level, so the heart only ever matters
 * when you are not overloaded.
 */
public class ImbuedHeartTest
{
	private static int overload(int level)
	{
		return level + 6 + (int) (level * 0.16);
	}

	@Test
	public void theBoostIsOnePlusATenthOfTheLevel()
	{
		assertEquals(109, CoxGearPlannerPlugin.imbuedHeart(99));
		assertEquals(104, CoxGearPlannerPlugin.imbuedHeart(94));
		assertEquals(83, CoxGearPlannerPlugin.imbuedHeart(75));
	}

	@Test
	public void aCoxOverloadBeatsItAtEveryLevel()
	{
		// +6 and 16% always exceeds +1 and 10%, so an overloaded raider gets
		// nothing from the heart — this is why it is off by default and why
		// enabling it alongside overloads changes no numbers.
		for (int level = 1; level <= 99; level++)
		{
			assertTrue("overload should win at magic " + level,
				overload(level) >= CoxGearPlannerPlugin.imbuedHeart(level));
		}
	}

	@Test
	public void itStillBeatsAnUnboostedLevel()
	{
		for (int level = 10; level <= 99; level++)
		{
			assertTrue(CoxGearPlannerPlugin.imbuedHeart(level) > level);
		}
	}
}
