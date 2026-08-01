package com.coxgearplanner;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A weapon is the style, not a swap.
 *
 * You cannot range without a bow, so the bow comes whether you budgeted for it
 * or not — and the plan packs it regardless. Charging the budget for something
 * that is never dropped only shrank the budget behind your back: a "4-way
 * switch" spent one slot on the weapon and another on its ammo and carried two
 * armour pieces, and a total budget of 1 against three weapons resolved to
 * max(0, 1 - 3) = 0, so it carried no armour at all while still packing all
 * three weapons. The number in the setting has to mean armour switches.
 */
public class WeaponsAreNotSwapsTest
{
	private static final int TBOW = 20997;
	private static final int DRAGON_ARROW = 11212;
	private static final int SCYTHE = 22325;
	private static final int RANCOUR = 29801;

	/** The kit still packs every weapon — that part was always right. */
	@Test
	public void everyWeaponIsStillPacked()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(TBOW, 1);
		bank.put(DRAGON_ARROW, 500);
		bank.put(SCYTHE, 1);
		bank.put(RANCOUR, 1);
		Map<ItemSource, Map<Integer, Integer>> items = new java.util.EnumMap<>(ItemSource.class);
		items.put(ItemSource.BANK, bank);

		SetupBuilder.Pick tbow = SetupBuilder.findOwned(
			ItemOption.twoHanded("Twisted bow", TBOW), items, true);
		SetupBuilder.Pick scythe = SetupBuilder.findOwned(
			ItemOption.twoHanded("Scythe of vitur", SCYTHE), items, true);

		List<RoomTimeEstimator.RoomTime> times = Arrays.asList(
			new RoomTimeEstimator.RoomTime(CoxRoom.OLM, "tbow", 200, true, GearNeed.RANGED, tbow),
			new RoomTimeEstimator.RoomTime(CoxRoom.TEKTON, "scythe", 100, true, GearNeed.MELEE, scythe));

		// A budget so tight it could not have afforded the weapons under the
		// old accounting.
		List<SwitchAdvisor.Advice> advice = Arrays.asList(
			new SwitchAdvisor.Advice(GearNeed.MELEE, GearSlot.NECK,
				"Amulet of rancour", null, 8.0, true, false));

		RaidLoadoutBuilder.RaidLoadout loadout = RaidLoadoutBuilder.build(
			EnumSet.of(CoxRoom.OLM, CoxRoom.TEKTON), times, advice,
			GearNeed.RANGED, items, true, null);

		Set<Integer> carried = loadout.getCarriedIds();
		assertTrue("the scythe comes even on a one-item budget", carried.contains(SCYTHE));
		assertTrue("so does the bow", carried.contains(TBOW));
	}

	/**
	 * The kit ids drive the re-timed rooms, so anything left in the bank must
	 * not appear in them — otherwise the times drift back to assuming gear the
	 * plan never told you to bring.
	 */
	@Test
	public void gearLeftBehindIsNotInTheKit()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(TBOW, 1);
		bank.put(RANCOUR, 1);
		Map<ItemSource, Map<Integer, Integer>> snapshot = new java.util.EnumMap<>(ItemSource.class);
		snapshot.put(ItemSource.BANK, bank);

		Map<ItemSource, Map<Integer, Integer>> kit = CoxGearPlannerPlugin.kitContents(
			new java.util.HashSet<>(java.util.Collections.singletonList(TBOW)), snapshot);

		assertEquals(1, kit.get(ItemSource.BANK).size());
		assertTrue(kit.get(ItemSource.BANK).containsKey(TBOW));
	}
}
