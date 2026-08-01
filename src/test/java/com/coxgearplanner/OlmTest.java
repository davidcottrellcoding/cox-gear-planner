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
	public void everyPartIsDraconicAndUsableByAllStyles()
	{
		for (RoomMonsters.Encounter encounter : RoomMonsters.getAll(CoxRoom.OLM))
		{
			MonsterProfile profile = encounter.getProfile();
			assertTrue(profile.getName() + " is draconic", profile.isDraconic());
			assertEquals(profile.getName() + " allows all styles",
				3, profile.getUsableStyles().size());
		}
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
	public void olmRoomDemandsAllThreeStyles()
	{
		assertTrue(CoxRoom.OLM.getNeeds().contains(GearNeed.MELEE));
		assertTrue(CoxRoom.OLM.getNeeds().contains(GearNeed.RANGED));
		assertTrue(CoxRoom.OLM.getNeeds().contains(GearNeed.MAGIC));
	}
}
