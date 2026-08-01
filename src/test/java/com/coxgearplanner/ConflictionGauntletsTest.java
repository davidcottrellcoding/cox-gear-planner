package com.coxgearplanner;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Confliction gauntlets grant a second accuracy roll.
 *
 * The wiki gives the exact form as Ptwo / (1 + Ptwo - Pone), which needs the
 * raw attack and defence rolls, and alongside it an average over the base hit
 * chance — which is what the DPS path has to hand. These pin the documented
 * shape of that average: large when you are inaccurate, nothing when you are
 * already accurate.
 */
public class ConflictionGauntletsTest
{
	private static EquipmentTotals worn()
	{
		EquipmentTotals eq = new EquipmentTotals();
		eq.confliction = true;
		return eq;
	}

	@Test
	public void doesNothingWhenNotWorn()
	{
		assertEquals(0.5, RoomTimeEstimator.withConfliction(0.5, new EquipmentTotals()), 1e-9);
	}

	/** About +30% relative at 10% base accuracy, per the wiki. */
	@Test
	public void isWorthAboutThirtyPercentWhenInaccurate()
	{
		double improved = RoomTimeEstimator.withConfliction(0.10, worn());
		assertEquals(0.129, improved, 0.002);
		assertTrue(improved / 0.10 > 1.25);
	}

	@Test
	public void isWorthLessAsAccuracyClimbs()
	{
		double low = RoomTimeEstimator.withConfliction(0.20, worn()) / 0.20;
		double mid = RoomTimeEstimator.withConfliction(0.50, worn()) / 0.50;
		double high = RoomTimeEstimator.withConfliction(0.80, worn()) / 0.80;

		assertTrue("the effect must shrink as base accuracy rises", low > mid);
		assertTrue(mid > high);
	}

	/**
	 * The wiki says virtually no effect at 95% and above — not a penalty. The
	 * quadratic average dips below its input up there, an artefact of the fit,
	 * so it is clamped.
	 */
	@Test
	public void neverReducesAccuracy()
	{
		for (int pct = 1; pct <= 100; pct++)
		{
			double base = pct / 100.0;
			double after = RoomTimeEstimator.withConfliction(base, worn());
			assertTrue("must not reduce accuracy at " + pct + "%", after >= base - 1e-9);
			assertTrue("must not exceed certainty at " + pct + "%", after <= 1.0 + 1e-9);
		}
	}

	@Test
	public void barelyMattersWhenAlreadyAccurate()
	{
		double after = RoomTimeEstimator.withConfliction(0.96, worn());
		assertEquals("virtually no effect at 95% and above", 0.96, after, 0.005);
	}
}
