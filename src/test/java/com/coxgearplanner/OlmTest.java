package com.coxgearplanner;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Olm is three targets with deliberately opposite defensive profiles — the
 * reason the fight demands all three combat styles. These assertions pin the
 * wiki stats that make each style the right answer for its part.
 */
public class OlmTest
{
	private static MonsterProfile part(String needle)
	{
		for (RoomMonsters.Encounter encounter : RoomMonsters.getAll(CoxRoom.OLM))
		{
			if (encounter.getProfile().getName().toLowerCase().contains(needle))
			{
				return encounter.getProfile();
			}
		}
		throw new AssertionError("no Olm part matching " + needle);
	}

	@Test
	public void olmHasThreeSeparateTargets()
	{
		List<RoomMonsters.Encounter> parts = RoomMonsters.getAll(CoxRoom.OLM);
		assertEquals(3, parts.size());
	}

	@Test
	public void eachPartIsWeakestToADifferentStyle()
	{
		MonsterProfile left = part("left claw");
		MonsterProfile right = part("right claw");
		MonsterProfile head = part("head");

		// Left claw: melee defence far below magic/ranged → melee hand
		assertTrue("left claw weak to melee", left.getDStab() < left.getDMagic());
		assertTrue("left claw weak to melee", left.getDStab() < left.getDRange());

		// Right claw: magic defence and magic level both low → mage hand
		assertTrue("right claw weak to magic", right.getDMagic() < right.getDStab());
		assertTrue("right claw weak to magic", right.getDMagic() < right.getDRange());
		assertEquals(87, right.getMagicLevel());

		// Head: ranged defence far below everything else → tbow
		assertTrue("head weak to ranged", head.getDRange() < head.getDStab());
		assertTrue("head weak to ranged", head.getDRange() < head.getDMagic());
		assertEquals(250, head.getMagicLevel()); // what the twisted bow scales from
	}

	@Test
	public void everyPartIsDraconic()
	{
		for (RoomMonsters.Encounter encounter : RoomMonsters.getAll(CoxRoom.OLM))
		{
			assertTrue(encounter.getProfile().getName() + " is draconic",
				encounter.getProfile().isDraconic());
		}
	}

	/**
	 * No Olm part takes damage from every style. The claws are locked to their
	 * own style, and the head cannot be maged at all. With no melee armour
	 * carried the estimator once "fell back" to a shadow on the melee hand, a
	 * plan the real fight does not allow.
	 */
	@Test
	public void eachPartOnlyTakesDamageFromItsLegalStyles()
	{
		MonsterProfile left = part("left claw");
		assertEquals(1, left.getUsableStyles().size());
		assertTrue("melee hand is melee only",
			left.getUsableStyles().contains(GearNeed.MELEE));

		MonsterProfile right = part("right claw");
		assertEquals(1, right.getUsableStyles().size());
		assertTrue("mage hand is magic only",
			right.getUsableStyles().contains(GearNeed.MAGIC));

		MonsterProfile head = part("head");
		assertEquals(2, head.getUsableStyles().size());
		assertTrue(head.getUsableStyles().contains(GearNeed.MELEE));
		assertTrue(head.getUsableStyles().contains(GearNeed.RANGED));
		assertTrue("the head cannot be maged",
			!head.getUsableStyles().contains(GearNeed.MAGIC));
	}

	@Test
	public void phaseCountIsFourPlusOnePerEightPlayers()
	{
		assertEquals(4, RoomMonsters.olmPhases(1));
		assertEquals(4, RoomMonsters.olmPhases(3));
		assertEquals(4, RoomMonsters.olmPhases(7));
		assertEquals(5, RoomMonsters.olmPhases(8));
		assertEquals(6, RoomMonsters.olmPhases(16));
	}

	@Test
	public void eachClawIsFoughtThreeTimesInAStandardRaid()
	{
		// Every phase except the final head phase
		assertEquals(3, RoomMonsters.olmClawPhases(1));
		assertEquals(3, RoomMonsters.olmClawPhases(3));
		assertEquals(3, RoomMonsters.olmClawPhases(7));
		assertEquals(4, RoomMonsters.olmClawPhases(8));
		assertEquals(RoomMonsters.olmPhases(5) - 1, RoomMonsters.olmClawPhases(5));
	}

	@Test
	public void olmRoomDemandsAllThreeStyles()
	{
		assertTrue(CoxRoom.OLM.getNeeds().contains(GearNeed.MELEE));
		assertTrue(CoxRoom.OLM.getNeeds().contains(GearNeed.RANGED));
		assertTrue(CoxRoom.OLM.getNeeds().contains(GearNeed.MAGIC));
	}
}
