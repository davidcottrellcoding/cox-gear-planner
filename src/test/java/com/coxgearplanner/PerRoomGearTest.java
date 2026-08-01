package com.coxgearplanner;

import java.util.ArrayList;
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

import static org.junit.Assert.assertTrue;

/**
 * A style is not limited to one item per slot. When different rooms favour
 * different pieces — an accuracy body against a heavily-armoured target, a
 * strength body against one with no defence to speak of — the plan brings
 * both and swaps per room, exactly as it always did for the salve amulet.
 */
public class PerRoomGearTest
{
	private static final int FANG = 26219;
	private static final int BODY_STRENGTH = 90001;
	private static final int BODY_ACCURACY = 90002;

	private final Map<Integer, ItemStats> statsById = new HashMap<>();
	private ItemManager itemManager;
	private Map<ItemSource, Map<Integer, Integer>> bank;
	private final PlayerSnapshot player = new PlayerSnapshot(99, 99, 99, 99, 99);

	@Before
	public void setUp()
	{
		statsById.put(FANG, new ItemStats(true, 1.0, 0, ItemEquipmentStats.builder()
			.slot(3).isTwoHanded(false).aspeed(5).astab(105).str(103).build()));
		// One body is pure strength, the other pure stab accuracy — with a
		// wide enough gap that each dominates a different defensive profile.
		statsById.put(BODY_STRENGTH, new ItemStats(true, 1.0, 0, ItemEquipmentStats.builder()
			.slot(4).str(40).build()));
		statsById.put(BODY_ACCURACY, new ItemStats(true, 1.0, 0, ItemEquipmentStats.builder()
			.slot(4).astab(150).build()));

		itemManager = Mockito.mock(ItemManager.class);
		Mockito.when(itemManager.getItemStats(Mockito.anyInt()))
			.thenAnswer(inv -> statsById.get((Integer) inv.getArgument(0)));
		ItemComposition composition = Mockito.mock(ItemComposition.class);
		Mockito.when(composition.getName()).thenReturn("Mock item");
		Mockito.when(itemManager.getItemComposition(Mockito.anyInt())).thenReturn(composition);

		Map<Integer, Integer> pool = new HashMap<>();
		pool.put(FANG, 1);
		pool.put(BODY_STRENGTH, 1);
		pool.put(BODY_ACCURACY, 1);
		bank = new EnumMap<>(ItemSource.class);
		bank.put(ItemSource.BANK, pool);
	}

	@Test
	public void bothBodiesComeWhenDifferentRoomsWantDifferentOnes()
	{
		// Mystics: defence 187, stab 155 — accuracy is everything.
		// Vasa's crystal: stab defence -5 — accuracy saturates, strength wins.
		Set<CoxRoom> rooms = EnumSet.of(CoxRoom.MYSTICS, CoxRoom.VASA);
		RoomTimeEstimator estimator = new RoomTimeEstimator(itemManager);
		estimator.getResolver().setDpsContext(estimator, player, rooms, true);

		List<RoomTimeEstimator.RoomTime> times =
			estimator.estimate(rooms, bank, true, player, 1, true, null);

		List<RoomTimeEstimator.RoomTime.ExtraSwitch> bodyExtras = new ArrayList<>();
		for (RoomTimeEstimator.RoomTime rt : times)
		{
			for (RoomTimeEstimator.RoomTime.ExtraSwitch extra : rt.getExtraSwitches())
			{
				if (extra.getSlot() == GearSlot.BODY)
				{
					bodyExtras.add(extra);
				}
			}
		}
		assertTrue("some room must prefer the body the style did not settle on",
			!bodyExtras.isEmpty());
		for (RoomTimeEstimator.RoomTime.ExtraSwitch extra : bodyExtras)
		{
			assertTrue("a per-room alternate must clear the threshold",
				extra.getSecondsSaved() >= 3);
		}

		// And the packed kit carries BOTH bodies — one worn, one as an extra
		SwitchAdvisor advisor = new SwitchAdvisor(estimator);
		SwitchAdvisor.Result switches = advisor.advise(
			times, bank, true, player, 1, true, 3, 0, 0, null);
		estimator.getResolver().pinResolved(switches.getPrimary(),
			switches.getBasePicks(), bank, true);
		SwitchAdvisor.SettledPlan settled = advisor.settle(rooms, times,
			switches.getAdvice(), switches.getPrimary(), bank, true, player, 1,
			true, java.util.Collections.emptySet(), 3, 0);

		assertTrue(settled.getLoadout() != null);
		Set<Integer> carried = settled.getLoadout().getCarriedIds();
		assertTrue("the strength body comes", carried.contains(BODY_STRENGTH));
		assertTrue("so does the accuracy body", carried.contains(BODY_ACCURACY));
	}
}
