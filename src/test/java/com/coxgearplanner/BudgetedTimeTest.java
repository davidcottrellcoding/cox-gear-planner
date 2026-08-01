package com.coxgearplanner;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The times have to be built from the gear the plan actually brings.
 *
 * The estimator runs before the switch advisor, because the advisor needs each
 * room's cost before it can decide what is worth carrying. That first pass
 * hands every room the best gear you own for it, so its times assume an
 * unlimited budget — and they were the only times anything displayed. A
 * one-item kit and a twelve-item kit therefore produced the same total, which
 * is the one thing a switch budget must never do.
 *
 * Scaling those numbers afterwards was not enough either. The estimator
 * applies effects the advisor does not model — a salve amulet against the
 * undead most of all — so a ratio taken from the advisor's own model could
 * come back below one and make a *tighter* budget look faster. The fix is to
 * stop deriving the number and re-run the estimator against the kit itself.
 */
public class BudgetedTimeTest
{
	private static final int TBOW = 20997;
	private static final int SCYTHE = 22325;
	private static final int DRAGON_ARROW = 11212;

	private static RoomTimeEstimator.RoomTime room(CoxRoom where, double seconds, boolean feasible)
	{
		return new RoomTimeEstimator.RoomTime(where, "detail", seconds, feasible, GearNeed.RANGED,
			new SetupBuilder.Pick(ItemOption.twoHanded("Twisted bow", TBOW),
				ItemSource.BANK, TBOW, 1));
	}

	private static PlanResult planWith(
		List<RoomTimeEstimator.RoomTime> real, List<RoomTimeEstimator.RoomTime> ideal)
	{
		PlanResult result = new PlanResult(Collections.emptyList(), real,
			Collections.emptyList(), GearNeed.RANGED, null, null);
		result.setIdealTimes(ideal);
		return result;
	}

	/** A budget that was never binding costs nothing. */
	@Test
	public void aKitThatCarriesEverythingLosesNoTime()
	{
		List<RoomTimeEstimator.RoomTime> ideal =
			Arrays.asList(room(CoxRoom.OLM, 200, true), room(CoxRoom.TEKTON, 100, true));
		List<RoomTimeEstimator.RoomTime> real =
			Arrays.asList(room(CoxRoom.OLM, 200, true), room(CoxRoom.TEKTON, 100, true));

		assertEquals(0, planWith(real, ideal).secondsLostToBudget(), 1e-9);
	}

	/** A tighter kit is slower, and the gap is what the budget cost. */
	@Test
	public void aTighterKitReportsTheTimeItCost()
	{
		List<RoomTimeEstimator.RoomTime> ideal =
			Arrays.asList(room(CoxRoom.OLM, 200, true), room(CoxRoom.TEKTON, 100, true));
		List<RoomTimeEstimator.RoomTime> real =
			Arrays.asList(room(CoxRoom.OLM, 214, true), room(CoxRoom.TEKTON, 160, true));

		PlanResult result = planWith(real, ideal);

		assertEquals(74.0, result.secondsLostToBudget(), 1e-9);
		assertEquals("the displayed time is the one you will actually get",
			214.0, result.secondsFor(real.get(0)), 1e-9);
	}

	/** Infeasible rooms are excluded from both sides, as the panel excludes them. */
	@Test
	public void anInfeasibleRoomIsNotCountedOnEitherSide()
	{
		List<RoomTimeEstimator.RoomTime> ideal =
			Arrays.asList(room(CoxRoom.OLM, 200, true), room(CoxRoom.TEKTON, 100, false));
		List<RoomTimeEstimator.RoomTime> real =
			Arrays.asList(room(CoxRoom.OLM, 200, true), room(CoxRoom.TEKTON, 999, false));

		assertEquals(0, planWith(real, ideal).secondsLostToBudget(), 1e-9);
	}

	/**
	 * The kit is what the estimator is re-pointed at, so it has to contain
	 * every id the loadout brings and nothing else — an item left in the bank
	 * must not be able to contribute damage.
	 */
	@Test
	public void theKitHoldsExactlyWhatTheLoadoutBrings()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(TBOW, 1);
		bank.put(SCYTHE, 1);
		bank.put(DRAGON_ARROW, 500);
		Map<ItemSource, Map<Integer, Integer>> snapshot = new EnumMap<>(ItemSource.class);
		snapshot.put(ItemSource.BANK, bank);

		Set<Integer> carried = new HashSet<>(Arrays.asList(TBOW, DRAGON_ARROW));
		Map<ItemSource, Map<Integer, Integer>> kit =
			CoxGearPlannerPlugin.kitContents(carried, snapshot);

		Map<Integer, Integer> owned = kit.get(ItemSource.BANK);
		assertTrue("the bow is coming", owned.containsKey(TBOW));
		assertEquals("ammo keeps its real stack size", 500, (int) owned.get(DRAGON_ARROW));
		assertTrue("a scythe left in the bank cannot do damage",
			!owned.containsKey(SCYTHE));
	}

	/** Quantities are summed across sources, so group storage still counts. */
	@Test
	public void theKitSumsQuantitiesAcrossSources()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(DRAGON_ARROW, 200);
		Map<Integer, Integer> group = new HashMap<>();
		group.put(DRAGON_ARROW, 300);
		Map<ItemSource, Map<Integer, Integer>> snapshot = new EnumMap<>(ItemSource.class);
		snapshot.put(ItemSource.BANK, bank);
		snapshot.put(ItemSource.GROUP_STORAGE, group);

		Map<ItemSource, Map<Integer, Integer>> kit = CoxGearPlannerPlugin.kitContents(
			new HashSet<>(Collections.singletonList(DRAGON_ARROW)), snapshot);

		assertEquals(500, (int) kit.get(ItemSource.BANK).get(DRAGON_ARROW));
	}
}
