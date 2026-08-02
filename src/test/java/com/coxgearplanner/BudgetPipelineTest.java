package com.coxgearplanner;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertTrue;

/**
 * End-to-end check of the budgeted-time pipeline, with a mocked item cache so
 * the real DPS path runs: estimate against the bank, advise switches under a
 * budget, pack the kit, and re-estimate against exactly that kit.
 *
 * The one thing a swap budget must do is show up in the clock: a 1-item kit
 * has to be slower than a 12-item kit whenever the switches are worth real
 * time. This is the invariant the user can see break in the panel, and the
 * unit tests around each stage cannot, because only the full pipeline decides
 * what the kit contains.
 */
public class BudgetPipelineTest
{
	// Melee kit
	private static final int FANG = 26219;
	private static final int TORVA_HELM = 26382;
	private static final int TORVA_BODY = 26384;
	private static final int TORVA_LEGS = 26386;
	private static final int PRIMORDIAL_BOOTS = 13239;
	private static final int FEROCIOUS_GLOVES = 22981;
	private static final int TORTURE = 19553;
	private static final int INFERNAL_CAPE = 21295;
	private static final int BERSERKER_RING = 11773;
	private static final int AVERNIC_DEFENDER = 22322;

	// Magic kit
	private static final int SHADOW = 27275;
	private static final int ANCESTRAL_HAT = 21018;
	private static final int ANCESTRAL_TOP = 21021;
	private static final int ANCESTRAL_BOTTOM = 21024;
	private static final int OCCULT = 12002;
	private static final int TORMENTED_BRACELET = 19544;
	private static final int IMBUED_CAPE = 21791;
	private static final int ETERNAL_BOOTS = 13235;
	private static final int MAGES_BOOK = 6889;
	private static final int SEERS_RING = 11771;

	private static final int SLOT_HEAD = 0;
	private static final int SLOT_CAPE = 1;
	private static final int SLOT_AMULET = 2;
	private static final int SLOT_WEAPON = 3;
	private static final int SLOT_BODY = 4;
	private static final int SLOT_SHIELD = 5;
	private static final int SLOT_LEGS = 7;
	private static final int SLOT_GLOVES = 9;
	private static final int SLOT_BOOTS = 10;
	private static final int SLOT_RING = 12;

	private final Map<Integer, ItemStats> statsById = new HashMap<>();
	private ItemManager itemManager;
	private Map<ItemSource, Map<Integer, Integer>> bank;
	private final PlayerSnapshot player = new PlayerSnapshot(99, 99, 99, 99, 99);

	private void item(int id, int slot, boolean twoHanded, int aspeed,
		int astab, int amagic, int str, float mdmg)
	{
		ItemEquipmentStats eq = ItemEquipmentStats.builder()
			.slot(slot)
			.isTwoHanded(twoHanded)
			.aspeed(aspeed)
			.astab(astab)
			.amagic(amagic)
			.str(str)
			.mdmg(mdmg)
			.build();
		statsById.put(id, new ItemStats(true, 1.0, 0, eq));
	}

	@Before
	public void setUp()
	{
		// Weapons
		item(FANG, SLOT_WEAPON, false, 5, 105, 0, 103, 0);
		item(SHADOW, SLOT_WEAPON, true, 5, 0, 35, 0, 0);

		// Melee armour: strength, no magic bonuses
		item(TORVA_HELM, SLOT_HEAD, false, 0, 0, 0, 8, 0);
		item(TORVA_BODY, SLOT_BODY, false, 0, 0, 0, 12, 0);
		item(TORVA_LEGS, SLOT_LEGS, false, 0, 0, 0, 10, 0);
		item(PRIMORDIAL_BOOTS, SLOT_BOOTS, false, 0, 0, 0, 5, 0);
		item(FEROCIOUS_GLOVES, SLOT_GLOVES, false, 0, 0, 0, 14, 0);
		item(TORTURE, SLOT_AMULET, false, 0, 0, 0, 10, 0);
		item(INFERNAL_CAPE, SLOT_CAPE, false, 0, 0, 0, 8, 0);
		item(BERSERKER_RING, SLOT_RING, false, 0, 0, 0, 8, 0);
		item(AVERNIC_DEFENDER, SLOT_SHIELD, false, 0, 30, 0, 8, 0);

		// Magic armour: accuracy and damage percent, no strength
		item(ANCESTRAL_HAT, SLOT_HEAD, false, 0, 0, 8, 0, 2);
		item(ANCESTRAL_TOP, SLOT_BODY, false, 0, 0, 35, 0, 2);
		item(ANCESTRAL_BOTTOM, SLOT_LEGS, false, 0, 0, 26, 0, 2);
		item(OCCULT, SLOT_AMULET, false, 0, 0, 12, 0, 10);
		item(TORMENTED_BRACELET, SLOT_GLOVES, false, 0, 0, 10, 0, 5);
		item(IMBUED_CAPE, SLOT_CAPE, false, 0, 0, 15, 0, 2);
		item(ETERNAL_BOOTS, SLOT_BOOTS, false, 0, 0, 8, 0, 0);
		item(MAGES_BOOK, SLOT_SHIELD, false, 0, 0, 15, 0, 0);
		item(SEERS_RING, SLOT_RING, false, 0, 0, 8, 0, 0);

		itemManager = Mockito.mock(ItemManager.class);
		Mockito.when(itemManager.getItemStats(Mockito.anyInt()))
			.thenAnswer(inv -> statsById.get((Integer) inv.getArgument(0)));
		ItemComposition composition = Mockito.mock(ItemComposition.class);
		Mockito.when(composition.getName()).thenReturn("Mock item");
		Mockito.when(itemManager.getItemComposition(Mockito.anyInt())).thenReturn(composition);

		Map<Integer, Integer> pool = new HashMap<>();
		for (int id : statsById.keySet())
		{
			pool.put(id, 1);
		}
		bank = new EnumMap<>(ItemSource.class);
		bank.put(ItemSource.BANK, pool);
	}

	/** Everything one full plan run produces that the assertions care about. */
	private static class Run
	{
		final List<SwitchAdvisor.Advice> advice;
		final double idealTotal;
		final double realTotal;

		Run(List<SwitchAdvisor.Advice> advice, double idealTotal, double realTotal)
		{
			this.advice = advice;
			this.idealTotal = idealTotal;
			this.realTotal = realTotal;
		}
	}

	/** Runs the exact pipeline computePlan runs, including the re-pricing. */
	private Run runPipeline(int totalSwapItems)
	{
		Set<CoxRoom> rooms = EnumSet.of(CoxRoom.TEKTON, CoxRoom.VESPULA);

		RoomTimeEstimator estimator = new RoomTimeEstimator(itemManager);
		estimator.getResolver().setDpsContext(estimator, player, rooms, true);

		List<RoomTimeEstimator.RoomTime> times =
			estimator.estimate(rooms, bank, true, player, 1, true, null);
		SwitchAdvisor advisor = new SwitchAdvisor(estimator);
		SwitchAdvisor.Result switches = advisor.advise(
			times, bank, true, player, 1, true, 3, 0, totalSwapItems, null);

		estimator.getResolver().pinResolved(switches.getPrimary(),
			switches.getBasePicks(), bank, true);
		SwitchAdvisor.SettledPlan settled = advisor.settle(rooms, times,
			switches.getAdvice(), switches.getPrimary(), switches.getBasePicks(),
			bank, true, player, 1, true, java.util.Collections.emptySet(), 3, totalSwapItems);
		assertTrue("both rooms must be feasible", settled.getLoadout() != null);
		List<RoomTimeEstimator.RoomTime> real = settled.getRealTimes();

		// The traded base outfit must survive the settle: kit estimates clear
		// the resolver's caches, and without re-pinning the equipped list
		// drifts back to the un-traded optimum while the advice keeps
		// referencing the base the times were computed against.
		if (switches.getBasePicks() != null)
		{
			assertTrue("the pinned base outfit survives the settle",
				SwitchAdvisor.sameLoadout(
					estimator.getResolver().resolve(switches.getPrimary(), bank, true),
					switches.getBasePicks()));
		}

		// A room extra the verdicts rejected must not be packed anyway — the
		// loadout was once built a pass before the final budget veto landed,
		// leaving "over limit" advice beside an inventory that carried it.
		for (RoomTimeEstimator.RoomTime rt : real)
		{
			for (RoomTimeEstimator.RoomTime.ExtraSwitch extra : rt.getExtraSwitches())
			{
				if (!extra.isBrought())
				{
					assertTrue(extra.getPick().getOption().getName()
						+ " is rejected but packed",
						!settled.getLoadout().getCarriedIds()
							.contains(extra.getPick().getItemId()));
				}
			}
		}

		double idealTotal = 0;
		for (RoomTimeEstimator.RoomTime rt : times)
		{
			idealTotal += rt.getSeconds();
		}
		double total = 0;
		for (RoomTimeEstimator.RoomTime rt : real)
		{
			assertTrue("room must stay feasible with the packed kit: " + rt.getDisplayName(),
				rt.isFeasible());
			total += rt.getSeconds();
		}
		return new Run(switches.getAdvice(), idealTotal, total);
	}

	private double totalWithBudget(int totalSwapItems)
	{
		return runPipeline(totalSwapItems).realTotal;
	}

	@Test
	public void aTighterBudgetIsSlower()
	{
		double oneSwap = totalWithBudget(1);
		double twelveSwaps = totalWithBudget(12);

		assertTrue(String.format(
			"a 1-swap kit (%.1fs) must be meaningfully slower than a 12-swap kit (%.1fs)",
			oneSwap, twelveSwaps),
			oneSwap > twelveSwaps * 1.10);
	}

	/**
	 * The advice numbers must live in the same world as the totals beside
	 * them. An uncarried switch cannot claim to be worth more than the entire
	 * gap between this kit and the everything-carried ideal — with the old
	 * per-style, fixed-weapon pricing it routinely did ("saves 101s" next to a
	 * total only 24s off the ideal), because that model has no idea a room
	 * missing its armour just falls back to another carried style.
	 */
	@Test
	public void noSwitchClaimsMoreThanTheWholeBudgetGap()
	{
		Run run = runPipeline(1);
		double gap = run.realTotal - run.idealTotal;
		assertTrue("the tight budget must actually cost something", gap > 1);

		for (SwitchAdvisor.Advice a : run.advice)
		{
			if (a.isAlreadyShared() || a.isWorthIt())
			{
				continue;
			}
			assertTrue(String.format(
				"%s claims %.1fs but the whole budget only costs %.1fs",
				a.getItemName(), a.getSecondsSaved(), gap),
				a.getSecondsSaved() <= gap + 1e-6);
		}
	}

	/**
	 * The budget has to land on the most valuable pieces by the kit-priced
	 * numbers. After settling, no uncarried armour switch may be worth
	 * meaningfully more than a carried one — the visible symptom was a 0.9s
	 * body carried while 3.8s legs sat "over limit".
	 */
	@Test
	public void theBudgetIsSpentOnTheMostValuablePieces()
	{
		for (int budget : new int[]{1, 2})
		{
			Run run = runPipeline(budget);
			double cheapestCarried = Double.MAX_VALUE;
			double dearestLeft = 0;
			for (SwitchAdvisor.Advice a : run.advice)
			{
				if (a.isAlreadyShared() || a.getSlot() == GearSlot.SHIELD)
				{
					continue;
				}
				if (a.isWorthIt())
				{
					cheapestCarried = Math.min(cheapestCarried, a.getSecondsSaved());
				}
				else
				{
					dearestLeft = Math.max(dearestLeft, a.getSecondsSaved());
				}
			}
			if (cheapestCarried < Double.MAX_VALUE)
			{
				assertTrue(String.format(
					"budget %d: carries a %.1fs piece while a %.1fs piece is left behind",
					budget, cheapestCarried, dearestLeft),
					dearestLeft <= cheapestCarried + 1.0);
			}
		}
	}

	/**
	 * A shield rides free of the BUDGET (it comes in the same motion as its
	 * weapon) but not of the swap effort: below the minimum switch value it
	 * stays home, no matter that packing it costs no budget on paper. The
	 * visible symptom was a mage's book carried to save 0.1s.
	 */
	@Test
	public void aShieldStillHasToClearTheMinimumSwitchValue()
	{
		// Swap the two-handed shadow for a one-handed trident so the magic
		// shield slot is live at all.
		int trident = 12899;
		bank.get(ItemSource.BANK).remove(SHADOW);
		item(trident, SLOT_WEAPON, false, 4, 0, 25, 0, 0);
		bank.get(ItemSource.BANK).put(trident, 1);

		// A book worth many seconds is carried even with the budget spent
		item(MAGES_BOOK, SLOT_SHIELD, false, 0, 0, 60, 0, 0);
		assertTrue("a strong offhand rides free with its weapon",
			shieldCarried(runPipeline(1)));

		// The same slot with a token bonus is not worth the motion
		item(MAGES_BOOK, SLOT_SHIELD, false, 0, 0, 2, 0, 0);
		assertTrue("a junk offhand stays home even though it is budget-free",
			!shieldCarried(runPipeline(1)));
	}

	private static boolean shieldCarried(Run run)
	{
		for (SwitchAdvisor.Advice a : run.advice)
		{
			if (a.getStyle() == GearNeed.MAGIC && a.getSlot() == GearSlot.SHIELD
				&& !a.isAlreadyShared())
			{
				return a.isWorthIt();
			}
		}
		return false;
	}

	/**
	 * Skip-vs-over-limit must follow the printed number, not the internal
	 * gains the spend was decided on — a 2.1s "skip" beside a 2.0s "over
	 * limit" reads as arbitrary because it is. In shared-budget mode a
	 * leftover is over the limit exactly when its kit-priced value clears
	 * the minimum switch value.
	 */
	@Test
	public void verdictLabelsFollowTheRepricedValues()
	{
		Run run = runPipeline(1);
		for (SwitchAdvisor.Advice a : run.advice)
		{
			if (a.isAlreadyShared() || a.isWorthIt())
			{
				continue;
			}
			assertTrue(String.format(
				"%s: %.1fs labelled %s", a.getItemName(), a.getSecondsSaved(),
				a.isOverLimit() ? "over limit" : "skip"),
				a.isOverLimit() == (a.getSecondsSaved() >= 3));
		}
	}

	/**
	 * The budget must be fully spent while qualifying items remain. The
	 * initial spend uses the internal model's gains, which can score a piece
	 * at nothing that the kit-priced numbers value at 8s — leaving slots
	 * empty while an "over limit" label blames a budget that was never full.
	 */
	@Test
	public void theBudgetIsFullySpentWhileCandidatesRemain()
	{
		Run run = runPipeline(2);
		int spent = 0;
		boolean qualifyingLeftBehind = false;
		for (SwitchAdvisor.Advice a : run.advice)
		{
			if (a.isAlreadyShared() || a.getSlot() == GearSlot.SHIELD)
			{
				continue;
			}
			if (a.isWorthIt())
			{
				spent++;
			}
			else if (a.getSecondsSaved() >= 3)
			{
				qualifyingLeftBehind = true;
			}
		}
		if (qualifyingLeftBehind)
		{
			assertTrue(String.format(
				"%d of 2 budget slots spent while a 3s+ switch sits over limit", spent),
				spent >= 2);
		}
	}

	/** More budget can never cost time — the knob must be monotonic. */
	@Test
	public void theBudgetIsMonotonic()
	{
		double none = totalWithBudget(1);
		double some = totalWithBudget(4);
		double plenty = totalWithBudget(12);

		assertTrue("4 swaps must not be slower than 1", some <= none + 1e-6);
		assertTrue("12 swaps must not be slower than 4", plenty <= some + 1e-6);
	}
}
