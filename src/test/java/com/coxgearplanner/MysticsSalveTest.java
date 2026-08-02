package com.coxgearplanner;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The salve amulet (ei) at the skeletal mystics, through the WHOLE pipeline.
 *
 * The salve has no offensive stats at all — its 20% against the undead is
 * invisible to the stats scan, so the neck slot always resolves to a real
 * amulet and the salve only exists if the estimator tries it explicitly as an
 * override and the loadout packs the winner as a room extra. Every link in
 * that chain has to hold or the item silently vanishes from the plan.
 */
public class MysticsSalveTest
{
	private static final int TBOW = 20997;
	private static final int DRAGON_ARROW = 11212;
	private static final int ANGUISH = 19547;
	private static final int SALVE_EI = 12018;

	private final Map<Integer, ItemStats> statsById = new HashMap<>();
	private ItemManager itemManager;
	private Map<ItemSource, Map<Integer, Integer>> bank;
	private final PlayerSnapshot player = new PlayerSnapshot(99, 99, 99, 99, 99);

	@Before
	public void setUp()
	{
		statsById.put(TBOW, new ItemStats(true, 1.0, 0, ItemEquipmentStats.builder()
			.slot(3).isTwoHanded(true).aspeed(5).arange(70).build()));
		statsById.put(DRAGON_ARROW, new ItemStats(true, 1.0, 0, ItemEquipmentStats.builder()
			.slot(13).arange(0).rstr(60).build()));
		statsById.put(ANGUISH, new ItemStats(true, 1.0, 0, ItemEquipmentStats.builder()
			.slot(2).arange(15).rstr(5).build()));
		// The salve: no stats whatsoever — the scan must NOT be its way in
		statsById.put(SALVE_EI, new ItemStats(true, 1.0, 0, ItemEquipmentStats.builder()
			.slot(2).build()));

		itemManager = Mockito.mock(ItemManager.class);
		Mockito.when(itemManager.getItemStats(Mockito.anyInt()))
			.thenAnswer(inv -> statsById.get((Integer) inv.getArgument(0)));
		ItemComposition composition = Mockito.mock(ItemComposition.class);
		Mockito.when(composition.getName()).thenReturn("Mock item");
		Mockito.when(itemManager.getItemComposition(Mockito.anyInt())).thenReturn(composition);

		Map<Integer, Integer> pool = new HashMap<>();
		pool.put(TBOW, 1);
		pool.put(DRAGON_ARROW, 500);
		pool.put(ANGUISH, 1);
		pool.put(SALVE_EI, 1);
		bank = new EnumMap<>(ItemSource.class);
		bank.put(ItemSource.BANK, pool);
	}

	/**
	 * When the salve genuinely loses, the plan must say so with a number —
	 * "not suggested" and "never considered" are indistinguishable otherwise.
	 */
	@Test
	public void aLosingSalveIsRecordedWithItsCost()
	{
		// A neck so strong the salve trial cannot win
		statsById.put(ANGUISH, new ItemStats(true, 1.0, 0, ItemEquipmentStats.builder()
			.slot(2).arange(150).rstr(80).build()));

		Set<CoxRoom> rooms = EnumSet.of(CoxRoom.MYSTICS);
		RoomTimeEstimator estimator = new RoomTimeEstimator(itemManager);
		estimator.getResolver().setDpsContext(estimator, player, rooms, true);
		RoomTimeEstimator.RoomTime mystics =
			estimator.estimate(rooms, bank, true, player, 1, true, null).get(0);

		assertTrue("no salve extra when it loses", mystics.getExtraSwitches().isEmpty());
		assertNotNull("the verdict is recorded", mystics.getSalveTried());
		assertEquals(SALVE_EI, mystics.getSalveTried().getItemId());
		assertTrue("with the seconds it would cost", mystics.getSalveLostSeconds() > 0);
		assertNotNull("and what it was measured against", mystics.getSalveComparedTo());

		// The verdict must survive the settle: the salve is not packed, but it
		// still auditions against the kit so the panel can say WHY it stays home
		SwitchAdvisor advisor = new SwitchAdvisor(estimator);
		List<RoomTimeEstimator.RoomTime> times = java.util.Collections.singletonList(mystics);
		SwitchAdvisor.Result switches = advisor.advise(
			times, bank, true, player, 1, true, 3, 0, 0, null);
		estimator.getResolver().pinResolved(switches.getPrimary(),
			switches.getBasePicks(), bank, true);
		SwitchAdvisor.SettledPlan settled = advisor.settle(rooms, times,
			switches.getAdvice(), switches.getPrimary(), bank, true, player, 1,
			true, java.util.Collections.emptySet(), 3, 0);

		RoomTimeEstimator.RoomTime settledMystics = settled.getRealTimes().get(0);
		assertNotNull("the losing verdict survives the kit re-time",
			settledMystics.getSalveTried());
		assertTrue("the unpacked salve stays out of the kit",
			!settled.getLoadout().getCarriedIds().contains(SALVE_EI));
	}

	@Test
	public void theSalveWinsTheMysticsAndIsPacked()
	{
		Set<CoxRoom> rooms = EnumSet.of(CoxRoom.MYSTICS);
		RoomTimeEstimator estimator = new RoomTimeEstimator(itemManager);
		estimator.getResolver().setDpsContext(estimator, player, rooms, true);

		List<RoomTimeEstimator.RoomTime> times =
			estimator.estimate(rooms, bank, true, player, 1, true, null);
		RoomTimeEstimator.RoomTime mystics = times.get(0);

		assertTrue(mystics.isFeasible());
		assertTrue("the salve must beat the anguish against the undead",
			!mystics.getExtraSwitches().isEmpty());
		RoomTimeEstimator.RoomTime.ExtraSwitch salve = mystics.getExtraSwitches().get(0);
		assertEquals(SALVE_EI, salve.getPick().getItemId());
		assertTrue("its value is measured for the debug panel",
			salve.getSecondsSaved() > 0);
		assertTrue("the room line must name it: " + mystics.getDetail(),
			mystics.getDetail().contains("Salve amulet (ei)"));
		assertTrue("three kills charge overkill on top of the 480 hp share",
			mystics.getTotalHp() > 480);

		// And the full settle pipeline packs it, even on a 1-item budget —
		// a room-specific requirement rides like a weapon, not like a switch
		SwitchAdvisor advisor = new SwitchAdvisor(estimator);
		SwitchAdvisor.Result switches = advisor.advise(
			times, bank, true, player, 1, true, 3, 0, 1, null);
		estimator.getResolver().pinResolved(switches.getPrimary(),
			switches.getBasePicks(), bank, true);
		SwitchAdvisor.SettledPlan settled = advisor.settle(rooms, times,
			switches.getAdvice(), switches.getPrimary(), bank, true, player, 1,
			true, java.util.Collections.emptySet(), 3, 1);

		assertNotNull(settled.getLoadout());
		assertTrue("the salve is in the kit",
			settled.getLoadout().getCarriedIds().contains(SALVE_EI));
		assertTrue("the re-timed room still uses it",
			settled.getRealTimes().get(0).getDetail().contains("Salve amulet (ei)"));
	}
}
