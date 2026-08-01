package com.coxgearplanner;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A base-outfit trade is only worth making if it removes a switch without
 * creating a real one for the base style.
 *
 * If the base style has to carry its own item back into the traded slot,
 * nothing was removed — the switch changed owner and an inventory slot was
 * spent moving it. And a switch-back that merely LOST to the budget is not
 * free either: every traded slot is a swap the budget never sees, so under a
 * tight budget — where every switch-back loses to the cap — judging only
 * carried switch-backs let the search strip the base outfit slot by slot. A
 * "1-swap" plan wearing another style's neck, body and legs is really a
 * four-swap plan, which is why tightening the budget barely moved the clock.
 *
 * The rule: a trade is vetoed when the base style's item for that slot is
 * worth at least the minimum switch value, carried or not. Slots the base
 * style barely cares about (the 0.7s mage's book that once vetoed a 5.1s
 * anguish) still trade freely.
 */
public class SwitchBackTest
{
	private static final double THRESHOLD = 3.0;

	private static SwitchAdvisor.Result resultWith(SwitchAdvisor.Advice... advice)
	{
		List<SwitchAdvisor.Advice> list = new ArrayList<>();
		for (SwitchAdvisor.Advice a : advice)
		{
			list.add(a);
		}
		return new SwitchAdvisor.Result(GearNeed.MELEE, list, 100);
	}

	private static SwitchAdvisor.Advice advice(
		GearNeed style, GearSlot slot, boolean carried, double saved)
	{
		return new SwitchAdvisor.Advice(style, slot, "Oathplate helm", null, saved, carried, false);
	}

	@Test
	public void aCarriedSwitchBackCountsAsMovingTheSwitch()
	{
		SwitchAdvisor.Result result =
			resultWith(advice(GearNeed.MELEE, GearSlot.HEAD, true, 10.7));

		assertTrue(SwitchAdvisor.switchesBack(result, GearNeed.MELEE, GearSlot.HEAD, THRESHOLD));
	}

	@Test
	public void anotherStylesSwitchInThatSlotIsNotASwitchBack()
	{
		// Ranged wanting its own helm is the switch the trade is trying to
		// remove, not evidence the trade failed.
		SwitchAdvisor.Result result =
			resultWith(advice(GearNeed.RANGED, GearSlot.HEAD, true, 6.9));

		assertFalse(SwitchAdvisor.switchesBack(result, GearNeed.MELEE, GearSlot.HEAD, THRESHOLD));
	}

	@Test
	public void adifferentSlotIsNotASwitchBack()
	{
		SwitchAdvisor.Result result =
			resultWith(advice(GearNeed.MELEE, GearSlot.RING, true, 11.8));

		assertFalse(SwitchAdvisor.switchesBack(result, GearNeed.MELEE, GearSlot.HEAD, THRESHOLD));
	}

	/**
	 * A valuable switch-back vetoes the trade even when the budget dropped it.
	 * The slot did not really become free — the base style still wants its
	 * item back, the budget just could not afford to bring it. Trading anyway
	 * smuggles the swap into the worn set, past the budget the user set.
	 */
	@Test
	public void aCappedButValuableSwitchBackStillVetoesTheTrade()
	{
		SwitchAdvisor.Advice capped =
			new SwitchAdvisor.Advice(GearNeed.MELEE, GearSlot.HEAD, "Oathplate helm",
				null, 10.7, false, false);
		capped.overLimit = true;

		assertTrue(SwitchAdvisor.switchesBack(resultWith(capped), GearNeed.MELEE, GearSlot.HEAD, THRESHOLD));
	}

	/**
	 * A switch-back below the minimum switch value is a fair trade: the base
	 * style would never have carried its item back anyway, so wearing the
	 * other style's piece there costs nothing real. This is the mage's book /
	 * seers ring case that once vetoed every useful trade.
	 */
	@Test
	public void aCheapSwitchBackDoesNotVetoTheTrade()
	{
		SwitchAdvisor.Result result =
			resultWith(advice(GearNeed.MELEE, GearSlot.HEAD, false, 0.7));

		assertFalse(SwitchAdvisor.switchesBack(result, GearNeed.MELEE, GearSlot.HEAD, THRESHOLD));
	}

	@Test
	public void aSlotNobodyWantsBackIsAGoodTrade()
	{
		SwitchAdvisor.Result result = resultWith();

		assertFalse(SwitchAdvisor.switchesBack(result, GearNeed.MELEE, GearSlot.HEAD, THRESHOLD));
	}
}
