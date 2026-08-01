package com.coxgearplanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlanExportTest
{
	/** Config defaults are enough; the export only reads values. */
	private static CoxGearPlannerConfig config()
	{
		return new CoxGearPlannerConfig()
		{
		};
	}

	private static PlanResult plan()
	{
		Map<Integer, Integer> bank = new HashMap<>();
		bank.put(20997, 1);  // twisted bow
		bank.put(11212, 500); // dragon arrows
		bank.put(22325, 1);  // scythe
		Map<ItemSource, Map<Integer, Integer>> items = new EnumMap<>(ItemSource.class);
		items.put(ItemSource.BANK, bank);

		SetupBuilder.Pick tbow = SetupBuilder.findOwned(
			ItemOption.twoHanded("Twisted bow", 20997), items, true);
		SetupBuilder.Pick scythe = SetupBuilder.findOwned(
			ItemOption.twoHanded("Scythe of vitur", 22325), items, true);

		List<RoomTimeEstimator.RoomTime> times = new ArrayList<>(Arrays.asList(
			new RoomTimeEstimator.RoomTime(CoxRoom.OLM, "tbow", 200, true, GearNeed.RANGED, tbow),
			new RoomTimeEstimator.RoomTime(CoxRoom.TEKTON, "scythe", 100, true, GearNeed.MELEE, scythe)));

		List<SwitchAdvisor.Advice> advice = Arrays.asList(
			new SwitchAdvisor.Advice(GearNeed.MELEE, GearSlot.NECK, "Amulet of rancour", null, 8.0, true, false),
			new SwitchAdvisor.Advice(GearNeed.MELEE, GearSlot.BOOTS, "Primordial boots", "Pegasian boots", 0.5, false, false));

		RaidLoadoutBuilder.RaidLoadout loadout = RaidLoadoutBuilder.build(
			EnumSet.of(CoxRoom.OLM, CoxRoom.TEKTON), times, advice,
			GearNeed.RANGED, items, true, null);

		PlanResult result = new PlanResult(new ArrayList<>(), times, advice,
			GearNeed.RANGED, loadout, null);
		return result;
	}

	@Test
	public void exportContainsEverythingAReviewerNeeds()
	{
		String text = PlanExport.render(
			EnumSet.of(CoxRoom.OLM, CoxRoom.TEKTON),
			new PlayerSnapshot(118, 118, 118, 112, 99),
			config(),
			plan());

		// The settings behind the plan, not just the result
		assertTrue("names the version", text.contains(CoxGearPlannerPlugin.VERSION));
		assertTrue("lists the rooms", text.contains("Great Olm") && text.contains("Tekton"));
		assertTrue("shows the stats used", text.contains("STATS USED"));
		assertTrue("shows mining, which drives Guardians", text.contains("mining 99"));
		assertTrue("shows the settings", text.contains("SETTINGS"));
		assertTrue("shows party size", text.contains("party "));

		// The plan itself
		assertTrue(text.contains("EQUIPPED"));
		assertTrue(text.contains("INVENTORY"));
		assertTrue(text.contains("Twisted bow"));
		assertTrue(text.contains("Scythe of vitur"));
		assertTrue(text.contains("ESTIMATED TIMES"));
		assertTrue(text.contains("TOTAL COMBAT"));

		// Switch reasoning, including why something was dropped
		assertTrue(text.contains("SWITCH DECISIONS"));
		assertTrue(text.contains("CARRY"));
		assertTrue(text.contains("Amulet of rancour"));
		assertTrue("says what to keep on instead", text.contains("keep Pegasian boots"));

		// The caveat travels with the plan
		assertTrue(text.contains("rank"));
	}

	@Test
	public void exportIsPlainTextWithNoMarkup()
	{
		String text = PlanExport.render(
			EnumSet.of(CoxRoom.OLM),
			new PlayerSnapshot(99, 99, 99, 99, 99),
			config(),
			plan());
		assertFalse("no html leaks into the shared text", text.contains("<html>"));
		assertFalse(text.contains("<br>"));
		assertFalse(text.contains("<font"));
	}

	@Test
	public void handlesAnInfeasiblePlanWithoutBlowingUp()
	{
		PlanResult empty = new PlanResult(new ArrayList<>(), new ArrayList<>(),
			new ArrayList<>(), null, null, null);
		String text = PlanExport.render(EnumSet.noneOf(CoxRoom.class),
			new PlayerSnapshot(99, 99, 99, 99, 99), config(), empty);
		assertTrue(text.contains("No feasible plan"));
	}
}
