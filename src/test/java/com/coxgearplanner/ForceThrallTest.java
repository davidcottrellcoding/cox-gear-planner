package com.coxgearplanner;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Solo raids need thralls, and thralls keep you on the Arceuus spellbook —
 * which makes standard-spellbook casting unavailable. Fire spells and
 * autocast staves (harmonised, staff of the dead, kodai) are therefore not an
 * option; only powered staves with built-in spells can fight.
 */
public class ForceThrallTest
{
	private static final int SHADOW = 27275;
	private static final int EYE_OF_AYAK = 31113;
	private static final int SANGUINESTI = 22323;
	private static final int TRIDENT_SWAMP = 12899;
	private static final int TRIDENT_SEAS = 11905;
	private static final int HARMONISED = 24423;
	private static final int STAFF_OF_THE_DEAD = 11791;
	private static final int KODAI = 21006;

	@Test
	public void onlyPoweredStavesAreUsableOnArceuus()
	{
		assertTrue(RoomTimeEstimator.usableOnArceuus(SHADOW));
		assertTrue(RoomTimeEstimator.usableOnArceuus(EYE_OF_AYAK));
		assertTrue(RoomTimeEstimator.usableOnArceuus(SANGUINESTI));
		assertTrue(RoomTimeEstimator.usableOnArceuus(TRIDENT_SWAMP));
		assertTrue(RoomTimeEstimator.usableOnArceuus(TRIDENT_SEAS));

		assertFalse("harmonised autocasts standard spells",
			RoomTimeEstimator.usableOnArceuus(HARMONISED));
		assertFalse("staff of the dead autocasts standard spells",
			RoomTimeEstimator.usableOnArceuus(STAFF_OF_THE_DEAD));
		assertFalse("kodai autocasts standard spells",
			RoomTimeEstimator.usableOnArceuus(KODAI));
	}

	// --- End-to-end: an autocast-only mage loses magic entirely ---

	private final Map<Integer, ItemStats> statsById = new HashMap<>();
	private ItemManager itemManager;
	private Map<ItemSource, Map<Integer, Integer>> bank;
	private final PlayerSnapshot player = new PlayerSnapshot(99, 99, 99, 99, 99);

	@Before
	public void setUp()
	{
		ItemEquipmentStats harmonised = ItemEquipmentStats.builder()
			.slot(3).isTwoHanded(true).aspeed(5).amagic(15).build();
		statsById.put(HARMONISED, new ItemStats(true, 1.0, 0, harmonised));
		ItemEquipmentStats trident = ItemEquipmentStats.builder()
			.slot(3).isTwoHanded(false).aspeed(4).amagic(25).build();
		statsById.put(TRIDENT_SWAMP, new ItemStats(true, 1.0, 0, trident));

		itemManager = Mockito.mock(ItemManager.class);
		Mockito.when(itemManager.getItemStats(Mockito.anyInt()))
			.thenAnswer(inv -> statsById.get((Integer) inv.getArgument(0)));
		ItemComposition composition = Mockito.mock(ItemComposition.class);
		Mockito.when(composition.getName()).thenReturn("Mock item");
		Mockito.when(itemManager.getItemComposition(Mockito.anyInt())).thenReturn(composition);

		Map<Integer, Integer> pool = new HashMap<>();
		pool.put(HARMONISED, 1);
		bank = new EnumMap<>(ItemSource.class);
		bank.put(ItemSource.BANK, pool);
	}

	private List<RoomTimeEstimator.RoomTime> vespula(boolean forceThrall)
	{
		RoomTimeEstimator estimator = new RoomTimeEstimator(itemManager);
		estimator.setForceThrall(forceThrall);
		return estimator.estimate(EnumSet.of(CoxRoom.VESPULA), bank, true,
			player, 1, true, null);
	}

	@Test
	public void anAutocastOnlyMageCannotFightOnArceuus()
	{
		// Off: the harmonised staff carries the room
		assertTrue("harmonised works when spellbooks are free",
			vespula(false).get(0).isFeasible());

		// On: no powered staff owned, so magic is honestly unavailable —
		// the portal takes ranged or magic, and this bank has neither left
		assertFalse("no powered staff means no magic on Arceuus",
			vespula(true).get(0).isFeasible());
	}

	@Test
	public void aPoweredStaffStillFightsOnArceuus()
	{
		bank.get(ItemSource.BANK).put(TRIDENT_SWAMP, 1);

		List<RoomTimeEstimator.RoomTime> times = vespula(true);
		assertTrue("the trident casts its own spell from any book",
			times.get(0).isFeasible());
		assertTrue("the plan must name the powered staff",
			times.get(0).getDetail().contains("Trident"));
	}
}
