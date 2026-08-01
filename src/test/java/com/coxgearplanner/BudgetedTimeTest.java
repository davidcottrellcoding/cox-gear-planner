package com.coxgearplanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The headline time has to answer the question the settings ask.
 *
 * Room times are computed before any switch decision exists, so every room
 * gets the best gear you own for it whether or not the plan tells you to
 * carry it. That is the unlimited-switches time, and it read the same at a
 * budget of one item as at twelve — the one number a player tunes the budget
 * to watch was the one number the budget could not move.
 *
 * The advisor knew the real figure all along; RoomTime.seconds is final, so
 * it had nowhere to put it.
 */
public class BudgetedTimeTest
{
	private static final int TBOW = 20997;

	private static RoomTimeEstimator.RoomTime room(CoxRoom room, GearNeed style, double seconds)
	{
		return new RoomTimeEstimator.RoomTime(room, "detail", seconds, true, style,
			new SetupBuilder.Pick(ItemOption.twoHanded("Twisted bow", TBOW),
				ItemSource.BANK, TBOW, 1));
	}

	private static PlanResult planWith(
		List<RoomTimeEstimator.RoomTime> times,
		java.util.Map<RoomTimeEstimator.RoomTime, Double> budgeted)
	{
		PlanResult result = new PlanResult(Collections.emptyList(), times,
			Collections.emptyList(), GearNeed.RANGED, null, null);
		result.setBudgetedSeconds(budgeted);
		return result;
	}

	/** A room with every switch carried keeps the estimator's own number. */
	@Test
	public void aRoomThatGivesUpNothingIsUnchanged()
	{
		RoomTimeEstimator.RoomTime olm = room(CoxRoom.OLM, GearNeed.RANGED, 200);
		PlanResult result = planWith(
			new ArrayList<>(Arrays.asList(olm)), Collections.emptyMap());

		assertEquals(200, result.secondsFor(olm), 1e-9);
		assertEquals("nothing was given up", 0, result.secondsLostToBudget(), 1e-9);
	}

	/** A room that lost a switch reports the slower time, not the ideal one. */
	@Test
	public void aRoomThatLostASwitchReportsTheSlowerTime()
	{
		RoomTimeEstimator.RoomTime tekton = room(CoxRoom.TEKTON, GearNeed.MELEE, 100);
		java.util.Map<RoomTimeEstimator.RoomTime, Double> budgeted =
			new java.util.IdentityHashMap<>();
		budgeted.put(tekton, 118.0);

		PlanResult result = planWith(new ArrayList<>(Arrays.asList(tekton)), budgeted);

		assertEquals(118.0, result.secondsFor(tekton), 1e-9);
		assertEquals(18.0, result.secondsLostToBudget(), 1e-9);
	}

	/**
	 * The loss is what the budget costs you, so it can only ever be positive.
	 * A carried switch is one the plan chose because it saved time.
	 */
	@Test
	public void theLossIsSummedAcrossEveryRoom()
	{
		RoomTimeEstimator.RoomTime olm = room(CoxRoom.OLM, GearNeed.RANGED, 200);
		RoomTimeEstimator.RoomTime tekton = room(CoxRoom.TEKTON, GearNeed.MELEE, 100);
		java.util.Map<RoomTimeEstimator.RoomTime, Double> budgeted =
			new java.util.IdentityHashMap<>();
		budgeted.put(olm, 214.0);
		budgeted.put(tekton, 111.0);

		PlanResult result = planWith(
			new ArrayList<>(Arrays.asList(olm, tekton)), budgeted);

		assertEquals(25.0, result.secondsLostToBudget(), 1e-9);
		assertTrue("a tighter budget can only make the raid longer",
			result.secondsFor(olm) > olm.getSeconds());
	}

	/**
	 * Two rooms of the same style are keyed separately. A map keyed on value
	 * rather than identity would collapse rooms that happen to tie on time,
	 * and Olm arrives as three entries that share a style.
	 */
	@Test
	public void roomsThatTieOnTimeAreStillDistinct()
	{
		RoomTimeEstimator.RoomTime first = room(CoxRoom.OLM, GearNeed.RANGED, 150);
		RoomTimeEstimator.RoomTime second = room(CoxRoom.OLM, GearNeed.RANGED, 150);
		java.util.Map<RoomTimeEstimator.RoomTime, Double> budgeted =
			new java.util.IdentityHashMap<>();
		budgeted.put(first, 170.0);

		PlanResult result = planWith(
			new ArrayList<>(Arrays.asList(first, second)), budgeted);

		assertEquals(170.0, result.secondsFor(first), 1e-9);
		assertEquals("the second room kept its own time", 150.0,
			result.secondsFor(second), 1e-9);
	}
}
