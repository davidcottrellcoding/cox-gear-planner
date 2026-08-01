package com.coxgearplanner;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Powered staves carry their own cast speeds, which cannot be read from an
 * item's attack speed. The Olm 4-tick filter and the damage maths share this
 * helper so they can never disagree about a weapon's rhythm.
 */
public class MagicSpeedTest
{
	@Test
	public void poweredStaffSpeedsMatchTheWiki()
	{
		assertEquals("Tumeken's shadow", 5, RoomTimeEstimator.magicSpeedTicks(27275));
		assertEquals("Eye of ayak is the fastest magic weapon",
			3, RoomTimeEstimator.magicSpeedTicks(RoomTimeEstimator.EYE_OF_AYAK));
		assertEquals("Sanguinesti staff", 4, RoomTimeEstimator.magicSpeedTicks(22323));
		assertEquals("Trident of the swamp", 4, RoomTimeEstimator.magicSpeedTicks(12899));
		assertEquals("Trident of the seas", 4, RoomTimeEstimator.magicSpeedTicks(11905));
		assertEquals("Harmonised autocasts at 4", 4, RoomTimeEstimator.magicSpeedTicks(24423));
		assertEquals("an ordinary staff casts at 5", 5, RoomTimeEstimator.magicSpeedTicks(21006));
	}

	@Test
	public void theFourTickMagicOptionsAreTheOnesOlmModeWouldKeep()
	{
		// These are exactly the weapons the Olm 4-tick toggle leaves available
		// for magic; the shadow and eye of ayak are deliberately excluded
		// because they break the shared rhythm.
		int[] fourTick = {22323, 25731, 12899, 11905, 24423};
		for (int id : fourTick)
		{
			assertEquals("expected 4 ticks for " + id, 4, RoomTimeEstimator.magicSpeedTicks(id));
		}

		assertTrue("shadow is 5 ticks and would be filtered out",
			RoomTimeEstimator.magicSpeedTicks(27275) != 4);
		assertTrue("eye of ayak is 3 ticks and would be filtered out",
			RoomTimeEstimator.magicSpeedTicks(RoomTimeEstimator.EYE_OF_AYAK) != 4);
	}
}
