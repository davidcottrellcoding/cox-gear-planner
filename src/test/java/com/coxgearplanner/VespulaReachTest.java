package com.coxgearplanner;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The abyssal portal sits 8 tiles from the safe standing tiles. Weapons that
 * reach 8+ can stay on rapid; shorter ones must use longrange, which gives up
 * the rapid speed bonus — that is the whole reason a 10-tile bow is worth
 * carrying for this room.
 */
public class VespulaReachTest
{
	private static MonsterProfile portal()
	{
		return RoomMonsters.get(CoxRoom.VESPULA).getProfile();
	}

	@Test
	public void theKillTargetIsThePortalEightTilesOut()
	{
		assertEquals("Abyssal portal", portal().getName());
		assertEquals(250, portal().getHp());
		assertEquals(8, portal().getMinReach());
	}

	@Test
	public void tenTileBowsReachOnRapid()
	{
		for (int bow : new int[]{20997, 25865, 23983, 29591})
		{
			assertEquals("expected 10 tiles for " + bow, 10, RoomTimeEstimator.weaponReach(bow));
			assertFalse("should not need longrange",
				RoomTimeEstimator.needsLongrange(bow, portal()));
			assertFalse(RoomTimeEstimator.cannotReach(bow, portal()));
		}
	}

	@Test
	public void armadylAndZaryteCrossbowsAlsoReachWithoutLongrange()
	{
		// The guides call these out specifically as the crossbows that don't
		// need longrange
		for (int xbow : new int[]{11785, 26374})
		{
			assertEquals(8, RoomTimeEstimator.weaponReach(xbow));
			assertFalse(RoomTimeEstimator.needsLongrange(xbow, portal()));
		}
	}

	@Test
	public void sevenTileWeaponsMustGiveUpRapid()
	{
		// Rune/dragon/dragon hunter crossbows and one-handed powered staves
		for (int weapon : new int[]{9185, 21902, 21012, 22323, 12899})
		{
			assertEquals(7, RoomTimeEstimator.weaponReach(weapon));
			assertTrue("must switch to longrange",
				RoomTimeEstimator.needsLongrange(weapon, portal()));
			assertFalse("longrange still gets there",
				RoomTimeEstimator.cannotReach(weapon, portal()));
		}
	}

	@Test
	public void theBlowpipeCannotReachThePortalAtAll()
	{
		assertEquals(5, RoomTimeEstimator.weaponReach(12926));
		assertTrue("5 + 2 longrange is still short of 8",
			RoomTimeEstimator.cannotReach(12926, portal()));
	}

	@Test
	public void reachIsIrrelevantForOtherRooms()
	{
		MonsterProfile tekton = RoomMonsters.get(CoxRoom.TEKTON).getProfile();
		assertEquals(0, tekton.getMinReach());
		assertFalse(RoomTimeEstimator.needsLongrange(12926, tekton));
		assertFalse(RoomTimeEstimator.cannotReach(12926, tekton));
	}
}
