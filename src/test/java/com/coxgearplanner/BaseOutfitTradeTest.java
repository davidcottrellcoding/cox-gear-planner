package com.coxgearplanner;

import java.util.EnumMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The base outfit is now allowed to differ from the primary style's own
 * optimum, so that a slot can be traded to remove an expensive switch.
 *
 * That trade is only sound if the primary style is charged for what it gives
 * up. The charge is computed as a delta against the reported room times, and
 * these pin the comparison that decides whether the delta is applied at all —
 * getting it wrong would either bill the primary twice or let base changes
 * look free.
 */
public class BaseOutfitTradeTest
{
	private static SetupBuilder.Pick item(int id)
	{
		return new SetupBuilder.Pick(ItemOption.of("Item " + id, id), ItemSource.BANK, id, 1);
	}

	@Test
	public void anUnchangedLoadoutKeepsTheReportedBaseline()
	{
		Map<GearSlot, SetupBuilder.Pick> a = new EnumMap<>(GearSlot.class);
		a.put(GearSlot.RING, item(1));
		a.put(GearSlot.NECK, item(2));

		Map<GearSlot, SetupBuilder.Pick> b = new EnumMap<>(GearSlot.class);
		b.put(GearSlot.RING, item(1));
		b.put(GearSlot.NECK, item(2));

		assertTrue(SwitchAdvisor.sameLoadout(a, b));
	}

	@Test
	public void aSwappedSlotIsNotTheSameLoadout()
	{
		Map<GearSlot, SetupBuilder.Pick> a = new EnumMap<>(GearSlot.class);
		a.put(GearSlot.NECK, item(2));

		Map<GearSlot, SetupBuilder.Pick> b = new EnumMap<>(GearSlot.class);
		b.put(GearSlot.NECK, item(3));

		assertFalse(SwitchAdvisor.sameLoadout(a, b));
	}

	/**
	 * An empty slot and a filled one are different loadouts. Treating them as
	 * equal would let the planner drop a piece and still bill the primary
	 * style the baseline time, making the loss invisible.
	 */
	@Test
	public void anEmptySlotDiffersFromAFilledOne()
	{
		Map<GearSlot, SetupBuilder.Pick> a = new EnumMap<>(GearSlot.class);
		a.put(GearSlot.RING, item(1));

		Map<GearSlot, SetupBuilder.Pick> b = new EnumMap<>(GearSlot.class);

		assertFalse(SwitchAdvisor.sameLoadout(a, b));
		assertFalse(SwitchAdvisor.sameLoadout(b, a));
	}

	/**
	 * A slot absent from the map and a slot explicitly mapped to null are the
	 * same thing — both mean "wearing nothing there". The resolver produces
	 * both forms, so a false difference here would charge a delta for a change
	 * that never happened.
	 */
	@Test
	public void anAbsentSlotMatchesAnExplicitlyEmptyOne()
	{
		Map<GearSlot, SetupBuilder.Pick> a = new EnumMap<>(GearSlot.class);
		a.put(GearSlot.RING, item(1));
		a.put(GearSlot.NECK, null);

		Map<GearSlot, SetupBuilder.Pick> b = new EnumMap<>(GearSlot.class);
		b.put(GearSlot.RING, item(1));

		assertTrue(SwitchAdvisor.sameLoadout(a, b));
	}
}
