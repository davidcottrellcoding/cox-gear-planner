package com.coxgearplanner;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A base-outfit trade is vetoed only when the base style ends up CARRYING
 * its own item back into the traded slot — then nothing was freed, the
 * switch changed owner and an inventory slot was spent moving it, a cost the
 * time totals cannot see.
 *
 * Everything else is decided by the total. A switch-back that loses to the
 * budget is a real loss the totals count in full (the base style's rooms get
 * slower without its item, and every number is re-priced against the packed
 * kit), so a trade that still wins — wearing the melee berserker ring in a
 * dead magic ring slot, keeping its full value without spending a budget or
 * inventory slot — is the optimiser doing its job. The best TOTAL kit wins,
 * not the purest base outfit.
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
	 * A switch-back the budget dropped is NOT a veto: its loss is real but
	 * fully counted in the totals, so the trade stands or falls on whether
	 * the raid still gets faster overall. Vetoing it killed net-positive
	 * trades like wearing the melee berserker ring in a dead magic ring slot.
	 */
	@Test
	public void aCappedSwitchBackIsDecidedByTheTotalNotTheVeto()
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
