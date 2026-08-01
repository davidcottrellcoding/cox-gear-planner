package com.coxgearplanner;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A base-outfit trade is only worth making if it removes a switch.
 *
 * If the base style has to carry its own item back into the traded slot,
 * nothing was removed — the switch changed owner and an inventory slot was
 * spent moving it. That is worth guarding, because with a per-style cap the
 * base style's switch-backs look free, so the search will strip its gear slot
 * by slot and return a "melee" base outfit wearing a magic hood and a ranged
 * cape, with the melee gear carried.
 */
public class SwitchBackTest
{
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

		assertTrue(SwitchAdvisor.switchesBack(result, GearNeed.MELEE, GearSlot.HEAD));
	}

	@Test
	public void anotherStylesSwitchInThatSlotIsNotASwitchBack()
	{
		// Ranged wanting its own helm is the switch the trade is trying to
		// remove, not evidence the trade failed.
		SwitchAdvisor.Result result =
			resultWith(advice(GearNeed.RANGED, GearSlot.HEAD, true, 6.9));

		assertFalse(SwitchAdvisor.switchesBack(result, GearNeed.MELEE, GearSlot.HEAD));
	}

	@Test
	public void adifferentSlotIsNotASwitchBack()
	{
		SwitchAdvisor.Result result =
			resultWith(advice(GearNeed.MELEE, GearSlot.RING, true, 11.8));

		assertFalse(SwitchAdvisor.switchesBack(result, GearNeed.MELEE, GearSlot.HEAD));
	}

	/**
	 * A switch-back that lost to the budget did NOT consume a slot, so the
	 * trade freed one after all — that is the trade working.
	 *
	 * Treating this as failure vetoed every useful trade: with a low minimum
	 * switch value each switch-back looks worth having, so the guard fired
	 * every time and the base outfit could never give up a slot. That is how a
	 * Mage's book and a seers ring worth 0.7s apiece stayed equipped while a
	 * 5.1s necklace of anguish went uncarried.
	 */
	@Test
	public void aCappedSwitchBackIsNotAFailure()
	{
		SwitchAdvisor.Advice capped =
			new SwitchAdvisor.Advice(GearNeed.MELEE, GearSlot.HEAD, "Oathplate helm",
				null, 10.7, false, false);
		capped.overLimit = true;

		assertFalse(SwitchAdvisor.switchesBack(resultWith(capped), GearNeed.MELEE, GearSlot.HEAD));
	}

	@Test
	public void aSlotNobodyWantsBackIsAGoodTrade()
	{
		SwitchAdvisor.Result result = resultWith();

		assertFalse(SwitchAdvisor.switchesBack(result, GearNeed.MELEE, GearSlot.HEAD));
	}
}
