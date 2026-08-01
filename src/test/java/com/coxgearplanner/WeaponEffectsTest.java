package com.coxgearplanner;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Effects that are not expressed in an item's equipment stats and therefore
 * have to be modelled explicitly, or the stats-based picker gets them wrong.
 */
public class WeaponEffectsTest
{
	@Test
	public void inquisitorPiecesSumToTwoAndAHalfPercent()
	{
		EquipmentTotals full = new EquipmentTotals();
		RoomTimeEstimator.addPieceEffects(full, RoomTimeEstimator.INQUISITOR_HELM);
		RoomTimeEstimator.addPieceEffects(full, RoomTimeEstimator.INQUISITOR_HAUBERK);
		RoomTimeEstimator.addPieceEffects(full, RoomTimeEstimator.INQUISITOR_SKIRT);
		assertEquals(0.025, full.inquisitorCrush, 1e-9);

		// Helm alone is the half-percent piece
		EquipmentTotals helm = new EquipmentTotals();
		RoomTimeEstimator.addPieceEffects(helm, RoomTimeEstimator.INQUISITOR_HELM);
		assertEquals(0.005, helm.inquisitorCrush, 1e-9);
	}

	@Test
	public void tomeOfFireIsTenPercentAgainstMonstersNotFifty()
	{
		// The widely-quoted 50% is player-versus-player only
		EquipmentTotals tome = new EquipmentTotals();
		RoomTimeEstimator.addPieceEffects(tome, RoomTimeEstimator.TOME_OF_FIRE);
		assertEquals(1.10, tome.fireSpellMult, 1e-9);

		EquipmentTotals none = new EquipmentTotals();
		RoomTimeEstimator.addPieceEffects(none, 6585); // amulet of fury
		assertEquals(1.0, none.fireSpellMult, 1e-9);
	}

	@Test
	public void eyeOfAyakIsAThreeTickWeaponInTheDatabase()
	{
		assertEquals(31113, RoomTimeEstimator.EYE_OF_AYAK);
		assertTrue("eye of ayak is a known magic weapon",
			GearDatabase.loadout(GearNeed.MAGIC).get(GearSlot.WEAPON).stream()
				.anyMatch(o -> o.getName().equals("Eye of ayak")));
	}

	@Test
	public void dragonHunterLanceIsKnownAndDraconicOnly()
	{
		assertEquals(22978, RoomTimeEstimator.DRAGON_HUNTER_LANCE);
		assertTrue("lance is a known melee weapon",
			GearDatabase.loadout(GearNeed.MELEE).get(GearSlot.WEAPON).stream()
				.anyMatch(o -> o.getName().equals("Dragon hunter lance")));

		// Olm's parts are draconic, so the lance's bonus applies there
		for (RoomMonsters.Encounter encounter : RoomMonsters.getAll(CoxRoom.OLM))
		{
			assertTrue(encounter.getProfile().isDraconic());
		}
		// Tekton is not draconic — the lance gets no bonus there
		assertTrue(!RoomMonsters.get(CoxRoom.TEKTON).getProfile().isDraconic());
	}
}
