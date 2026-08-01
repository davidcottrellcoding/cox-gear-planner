package com.coxgearplanner;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Augury grants +4% magic damage on top of its accuracy boost.
 *
 * The damage part was computed but never applied: it was added to the local
 * damage percentage AFTER the max hit had already been derived from it, so the
 * store was dead and every magic loadout with elite prayers assumed (the
 * default) was underrated by 4%. That skews the magic rooms' estimated times,
 * and through them the base-outfit choice and every switch priced against a
 * magic room.
 */
public class AuguryMagicDamageTest
{
	private static final int TRIDENT_SWAMP = 12899;

	/** A target with no meaningful defences, so the numbers stay legible. */
	private static MonsterProfile dummy()
	{
		return new MonsterProfile("Dummy", 100, 1, 1, 0, 0, 0, 0, 0, false, false,
			GearNeed.MAGIC);
	}

	private static final PlayerSnapshot MAXED = new PlayerSnapshot(99, 99, 99, 99, 99);

	@Test
	public void auguryAddsFourPercentMagicDamage()
	{
		double dps = RoomTimeEstimator.magicDps(
			TRIDENT_SWAMP, new EquipmentTotals(), MAXED, dummy(), true);

		// 99 Magic on the swamp trident is a 31 base hit; Augury's +4% pushes
		// it to floor(31 * 1.04) = 32. Accuracy still gets the 25% level boost.
		int effMagic = (int) (99 * 1.25) + 8;
		double expected = CombatFormulas.dps(
			CombatFormulas.accuracy(effMagic * 64.0, CombatFormulas.defenceRoll(1, 0)),
			32, 4);
		assertEquals("elite magic dps must include Augury's +4% damage",
			expected, dps, 1e-9);
	}

	@Test
	public void withoutElitePrayersTheBaseMaxHitStands()
	{
		double dps = RoomTimeEstimator.magicDps(
			TRIDENT_SWAMP, new EquipmentTotals(), MAXED, dummy(), false);

		int effMagic = 99 + 8;
		double expected = CombatFormulas.dps(
			CombatFormulas.accuracy(effMagic * 64.0, CombatFormulas.defenceRoll(1, 0)),
			31, 4);
		assertEquals(expected, dps, 1e-9);
	}
}
