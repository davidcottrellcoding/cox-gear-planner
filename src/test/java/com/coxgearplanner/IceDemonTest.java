package com.coxgearplanner;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The ice demon reduces all damage by 67% except standard-spellbook fire
 * spells and demonbane weapons. Its fire weakness is additive, not a
 * multiplier, and its demonbane effectiveness is 115%.
 */
public class IceDemonTest
{
	private static MonsterProfile demon()
	{
		return RoomMonsters.get(CoxRoom.ICE_DEMON).getProfile();
	}

	@Test
	public void demonbaneEffectivenessScalesTheWeaponsOwnBonus()
	{
		MonsterProfile ice = demon();
		assertEquals(1.15, ice.getDemonbaneEffectiveness(), 1e-9);

		// Emberlight and Arclight are 70%, so 70% x 115% = 80.5%
		assertEquals(1.805, RoomTimeEstimator.demonbaneMultiplier(29589, ice), 1e-9);
		assertEquals(1.805, RoomTimeEstimator.demonbaneMultiplier(19675, ice), 1e-9);
		// Darklight and Silverlight are 60% -> 69%
		assertEquals(1.69, RoomTimeEstimator.demonbaneMultiplier(6746, ice), 1e-9);
		// Scorching bow 30% -> 34.5%
		assertEquals(1.345, RoomTimeEstimator.demonbaneMultiplier(29591, ice), 1e-9);
	}

	@Test
	public void nonDemonbaneWeaponsGetNoBonus()
	{
		assertEquals(1.0, RoomTimeEstimator.demonbaneMultiplier(22325, demon()), 1e-9); // scythe
		assertEquals(1.0, RoomTimeEstimator.demonbaneMultiplier(4151, demon()), 1e-9);  // whip
	}

	@Test
	public void demonbaneDoesNothingAgainstNonDemons()
	{
		MonsterProfile tekton = RoomMonsters.get(CoxRoom.TEKTON).getProfile();
		assertFalse(tekton.isDemon());
		assertEquals(1.0, RoomTimeEstimator.demonbaneMultiplier(29589, tekton), 1e-9);
	}

	@Test
	public void fireWeaknessIsAdditiveNotAMultiplier()
	{
		// 150% is an additive elemental weakness: it adds 1.5x the spell's BASE
		// max on top of the gear-boosted hit, so "250% damage" only holds at
		// zero magic damage bonus.
		assertEquals(1.50, demon().getElementalWeakness(), 1e-9);
		assertEquals("all other damage is cut by 67%", 0.33, demon().getNonFireDamageMult(), 1e-9);
	}

	@Test
	public void theRoomProvidesItsOwnTinderboxAndAxe()
	{
		// A tinderbox and bronze axe spawn in the room; the ice is cleared by
		// burning kindling, so no fire spell is needed to progress.
		assertFalse("fire spells are a damage choice, not a room requirement",
			CoxRoom.ICE_DEMON.getNeeds().contains(GearNeed.FIRE_SPELLS));
		// No axe either: the room spawns a bronze one, and kindling per chop
		// scales with Woodcutting level rather than axe tier, so a better axe
		// only buys chopping speed — not worth an inventory slot.
		assertFalse("the room provides an axe",
			CoxRoom.ICE_DEMON.getNeeds().contains(GearNeed.AXE));
		// Melee is now a real option there thanks to demonbane
		assertTrue(CoxRoom.ICE_DEMON.getNeeds().contains(GearNeed.MELEE));
	}

	@Test
	public void magicDefenceComesFromTheDefenceLevel()
	{
		// 390 magic but only 160 defence — this is why magic is accurate here
		assertEquals(390, demon().getMagicLevel());
		assertEquals(160, demon().getMagicDefenceLevel());
	}
}
