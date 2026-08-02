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
import static org.junit.Assert.assertTrue;

/**
 * Defence-lowering specials: Tekton is opened with two, and every fresh
 * melee-hand instance at Olm gets its own, paid for from a special-energy
 * ledger with regen. The affected rooms are re-timed against the reduced
 * defence, so packing a dragon warhammer genuinely shortens them.
 */
public class DefenceSpecTest
{
	private static final int FANG = 26219;
	private static final int DWH = 13576;

	private final Map<Integer, ItemStats> statsById = new HashMap<>();
	private ItemManager itemManager;
	private Map<ItemSource, Map<Integer, Integer>> bank;
	private final PlayerSnapshot player = new PlayerSnapshot(99, 99, 99, 99, 99);

	@Before
	public void setUp()
	{
		statsById.put(FANG, new ItemStats(true, 1.0, 0, ItemEquipmentStats.builder()
			.slot(3).isTwoHanded(false).aspeed(5).astab(105).str(103).build()));
		statsById.put(DWH, new ItemStats(true, 1.0, 0, ItemEquipmentStats.builder()
			.slot(3).isTwoHanded(false).aspeed(6).acrush(95).str(85).build()));

		itemManager = Mockito.mock(ItemManager.class);
		Mockito.when(itemManager.getItemStats(Mockito.anyInt()))
			.thenAnswer(inv -> statsById.get((Integer) inv.getArgument(0)));
		ItemComposition composition = Mockito.mock(ItemComposition.class);
		Mockito.when(composition.getName()).thenReturn("Mock item");
		Mockito.when(itemManager.getItemComposition(Mockito.anyInt())).thenReturn(composition);

		Map<Integer, Integer> pool = new HashMap<>();
		pool.put(FANG, 1);
		bank = new EnumMap<>(ItemSource.class);
		bank.put(ItemSource.BANK, pool);
	}

	@Test
	public void theMathOfOneAttempt()
	{
		assertEquals("a guaranteed DWH keeps 70%", 0.70,
			RoomTimeEstimator.expectedSpecFactor(1.0, 0.70), 1e-9);
		assertEquals("a guaranteed miss still shaves 5%", 0.95,
			RoomTimeEstimator.expectedSpecFactor(0.0, 0.70), 1e-9);
		assertEquals(0.65, RoomTimeEstimator.specOnHitFactor(21003), 1e-9);
		assertEquals(0.70, RoomTimeEstimator.specOnHitFactor(DWH), 1e-9);
	}

	private List<RoomTimeEstimator.RoomTime> estimate(Set<CoxRoom> rooms)
	{
		RoomTimeEstimator estimator = new RoomTimeEstimator(itemManager);
		estimator.getResolver().setDpsContext(estimator, player, rooms, true);
		return estimator.estimate(rooms, bank, true, player, 1, true, null);
	}

	@Test
	public void aWarhammerShortensTekton()
	{
		Set<CoxRoom> rooms = EnumSet.of(CoxRoom.TEKTON);
		double without = 0;
		for (RoomTimeEstimator.RoomTime rt : estimate(rooms))
		{
			without += rt.getSeconds();
		}

		bank.get(ItemSource.BANK).put(DWH, 1);
		double with = 0;
		boolean noted = false;
		for (RoomTimeEstimator.RoomTime rt : estimate(rooms))
		{
			with += rt.getSeconds();
			noted |= rt.getDetail().contains("spec");
		}

		assertTrue(String.format("specs must shorten Tekton (%.1fs -> %.1fs)", without, with),
			with < without);
		assertTrue("the room line says the numbers assume the specials", noted);
	}

	@Test
	public void everyFreshMeleeHandGetsItsOwnSpecial()
	{
		bank.get(ItemSource.BANK).put(DWH, 1);
		Set<CoxRoom> rooms = EnumSet.of(CoxRoom.OLM);

		for (RoomTimeEstimator.RoomTime rt : estimate(rooms))
		{
			if (rt.getDisplayName().contains("melee hand"))
			{
				assertTrue("each phase spawns a new hand, each is spec'd again: "
					+ rt.getDetail(), rt.getDetail().contains("3/3 phases"));
			}
			else
			{
				assertTrue("only the melee hand is a spec target: " + rt.getDetail(),
					!rt.getDetail().contains("spec"));
			}
		}
	}
}
