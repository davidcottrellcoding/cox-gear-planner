package com.coxgearplanner;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the wiki-sourced monster stats and the room-defining mechanics, so a
 * later "tidy-up" can't quietly reintroduce the guessed values these replaced.
 */
public class MonsterDataTest
{
	private static MonsterProfile only(CoxRoom room)
	{
		return RoomMonsters.get(room).getProfile();
	}

	@Test
	public void tektonHasNoMagicDefenceButResistsMagicDamage()
	{
		MonsterProfile tekton = only(CoxRoom.TEKTON);
		// Previously guessed at 600/600; the wiki has both at 0
		assertEquals(0, tekton.getDMagic());
		assertEquals(0, tekton.getDRange());
		// What actually keeps you off magic here is the damage cap
		assertEquals(0.20, tekton.getMagicDamageMult(), 1e-9);
		// Crush is the soft style
		assertTrue(tekton.getDCrush() < tekton.getDStab());
		assertTrue(tekton.getDCrush() < tekton.getDSlash());
	}

	@Test
	public void iceDemonIsAFireSpellCheckNotAGearCheck()
	{
		MonsterProfile demon = only(CoxRoom.ICE_DEMON);
		assertTrue(demon.isDemon());
		assertEquals("all non-fire damage cut by 67%", 0.33, demon.getNonFireDamageMult(), 1e-9);
		assertEquals("fire spells do 150% extra", 2.50, demon.getFireSpellDamageMult(), 1e-9);
		// Its magic defence rolls off Defence, not Magic — a big difference
		// given its Magic level is 390
		assertEquals(390, demon.getMagicLevel());
		assertEquals(160, demon.getMagicDefenceLevel());
	}

	@Test
	public void eachVanguardIsSoftToADifferentStyle()
	{
		java.util.List<RoomMonsters.Encounter> vanguards = RoomMonsters.getAll(CoxRoom.VANGUARDS);
		assertEquals(3, vanguards.size());

		MonsterProfile melee = vanguards.get(0).getProfile();
		MonsterProfile ranged = vanguards.get(1).getProfile();
		MonsterProfile magic = vanguards.get(2).getProfile();

		// The melee vanguard is the one you kill with magic, and so on
		assertTrue("melee vanguard soft to magic", melee.getDMagic() < melee.getDStab());
		assertTrue("ranged vanguard soft to melee", ranged.getDStab() < ranged.getDMagic());
		assertTrue("magic vanguard soft to ranged", magic.getDRange() < magic.getDCrush());
	}

	@Test
	public void muttadilesAreTwoDifferentMonsters()
	{
		java.util.List<RoomMonsters.Encounter> muttadiles = RoomMonsters.getAll(CoxRoom.MUTTADILES);
		assertEquals(2, muttadiles.size());
		// The large one is far tankier than the small one
		assertTrue(muttadiles.get(1).getProfile().getDefenceLevel()
			> muttadiles.get(0).getProfile().getDefenceLevel());
	}

	@Test
	public void skeletalMysticsAreTheOnlyUndeadAndIceDemonTheOnlyDemon()
	{
		for (CoxRoom room : CoxRoom.values())
		{
			for (RoomMonsters.Encounter encounter : RoomMonsters.getAll(room))
			{
				MonsterProfile profile = encounter.getProfile();
				assertEquals(room + " undead flag", room == CoxRoom.MYSTICS, profile.isUndead());
				assertEquals(room + " demon flag", room == CoxRoom.ICE_DEMON, profile.isDemon());
			}
		}
	}

	@Test
	public void onlyOlmIsDraconic()
	{
		for (CoxRoom room : CoxRoom.values())
		{
			for (RoomMonsters.Encounter encounter : RoomMonsters.getAll(room))
			{
				assertEquals(room + " draconic flag", room == CoxRoom.OLM,
					encounter.getProfile().isDraconic());
			}
		}
	}

	@Test
	public void hpScalesLinearlyWithPartySize()
	{
		// Tekton is 300 solo and 600 in a duo, i.e. the multiplier is the
		// party size, not the 1 + 0.5*(n-1) previously guessed
		assertEquals(1.0, RoomTimeEstimator.hpMultiplier(1), 1e-9);
		assertEquals(2.0, RoomTimeEstimator.hpMultiplier(2), 1e-9);
		assertEquals(3.0, RoomTimeEstimator.hpMultiplier(3), 1e-9);
		assertEquals(1.0, RoomTimeEstimator.hpMultiplier(0), 1e-9);

		assertEquals(300, only(CoxRoom.TEKTON).getHp());
		assertFalse(only(CoxRoom.TEKTON).isUndead());
	}
}
