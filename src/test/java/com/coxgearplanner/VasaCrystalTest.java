package com.coxgearplanner;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Vasa's room has two targets whose answers are opposites: Vasa himself wants
 * ranged, and the glowing crystal he siphons from wants stab. The crystal is
 * a real target rather than a hardcoded "bring a stab weapon" note, so the
 * DPS maths picks the weapon — and will reuse your melee weapon if it already
 * stabs well enough, instead of adding one for its own sake.
 */
public class VasaCrystalTest
{
	private static MonsterProfile part(String needle)
	{
		for (RoomMonsters.Encounter encounter : RoomMonsters.getAll(CoxRoom.VASA))
		{
			if (encounter.getProfile().getName().toLowerCase().contains(needle))
			{
				return encounter.getProfile();
			}
		}
		throw new AssertionError("no Vasa target matching " + needle);
	}

	@Test
	public void theRoomHasBothVasaAndTheCrystal()
	{
		assertEquals(2, RoomMonsters.getAll(CoxRoom.VASA).size());
	}

	@Test
	public void theCrystalIsAStabTargetByItsRealStats()
	{
		MonsterProfile crystal = part("crystal");
		assertEquals(120, crystal.getHp());
		// -5 stab against +180 slash and crush is what makes stab the answer
		assertEquals(-5, crystal.getDStab());
		assertEquals(180, crystal.getDSlash());
		assertEquals(180, crystal.getDCrush());
		assertTrue("stab is far softer than anything else",
			crystal.getDStab() < crystal.getDSlash() - 100);
	}

	@Test
	public void theCrystalIsLargeSoTheScytheCanTripleHitIt()
	{
		// Stab is far more accurate here, but the crystal is 4x4 — a scythe
		// hits three times, and with oathplate that beats a fang on dps
		// (~10.7 against ~8.5). Scythe is the Challenge Mode meta for this
		// reason, so the flag that enables the triple hit must stay set.
		assertTrue("the scythe's triple hit depends on this", part("crystal").isLarge());
	}

	@Test
	public void theCrystalIsImmuneToRangedAndResistsMagic()
	{
		MonsterProfile crystal = part("crystal");
		assertFalse("immune to ranged entirely",
			crystal.getUsableStyles().contains(GearNeed.RANGED));
		assertTrue(crystal.getUsableStyles().contains(GearNeed.MELEE));
		assertEquals("a third damage from magic", 1.0 / 3.0, crystal.getMagicDamageMult(), 1e-9);
	}

	@Test
	public void vasaHimselfStaysARangedFight()
	{
		MonsterProfile vasa = part("vasa");
		assertTrue(vasa.getPreferredStyles().contains(GearNeed.RANGED));
		// His own defences are the opposite shape to the crystal's
		assertTrue("Vasa resists magic far more than ranged",
			vasa.getDMagic() > vasa.getDRange());
	}

	@Test
	public void noHardcodedStabWeaponListRemains()
	{
		// The weapon is chosen by the dps maths against the crystal's real
		// stats, so a fang is never added just because a list said so.
		for (GearNeed need : GearNeed.values())
		{
			assertFalse("stab should not be a listed utility",
				need.name().contains("STAB"));
		}
	}
}
