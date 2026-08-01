package com.coxgearplanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The slot search that replaced the heuristic score.
 *
 * The point of these is that gear is now chosen by the same numbers the room
 * times are reported in, so a slot can no longer win on a scoring rule that
 * has no bearing on damage.
 */
public class GearSearchTest
{
	private static SetupBuilder.Pick item(String name, int id)
	{
		return new SetupBuilder.Pick(ItemOption.of(name, id), ItemSource.BANK, id, 1);
	}

	/** Pretend each item id is worth that many damage points. */
	private static double secondsFrom(Map<GearSlot, SetupBuilder.Pick> picks, Map<Integer, Double> worth)
	{
		double damage = 1;
		for (SetupBuilder.Pick pick : picks.values())
		{
			if (pick != null)
			{
				damage += worth.getOrDefault(pick.getItemId(), 0.0);
			}
		}
		return 1000.0 / damage;
	}

	@Test
	public void picksTheItemThatActuallyLowersTheTime()
	{
		Map<Integer, Double> worth = new LinkedHashMap<>();
		worth.put(1, 1.0);   // the incumbent, barely does anything
		worth.put(2, 40.0);  // the item that actually helps

		Map<GearSlot, SetupBuilder.Pick> start = new EnumMap<>(GearSlot.class);
		start.put(GearSlot.RING, item("Seers ring", 1));

		Map<GearSlot, List<SetupBuilder.Pick>> shortlists = new LinkedHashMap<>();
		shortlists.put(GearSlot.RING, Arrays.asList(item("Seers ring", 1), item("Magus ring", 2)));

		Map<GearSlot, SetupBuilder.Pick> out =
			GearResolver.improve(start, shortlists, p -> secondsFrom(p, worth));

		assertEquals(2, out.get(GearSlot.RING).getItemId());
	}

	@Test
	public void keepsTheIncumbentWhenNothingBeatsIt()
	{
		Map<Integer, Double> worth = new LinkedHashMap<>();
		worth.put(1, 40.0);
		worth.put(2, 1.0);

		Map<GearSlot, SetupBuilder.Pick> start = new EnumMap<>(GearSlot.class);
		start.put(GearSlot.RING, item("Magus ring", 1));

		Map<GearSlot, List<SetupBuilder.Pick>> shortlists = new LinkedHashMap<>();
		shortlists.put(GearSlot.RING, Arrays.asList(item("Magus ring", 1), item("Seers ring", 2)));

		Map<GearSlot, SetupBuilder.Pick> out =
			GearResolver.improve(start, shortlists, p -> secondsFrom(p, worth));

		assertEquals(1, out.get(GearSlot.RING).getItemId());
	}

	/**
	 * A loadout priced so that each crystal piece is individually worse than
	 * what it replaces, and only the pair is better. This is the real shape of
	 * crystal armour with a bow of faerdhinen.
	 */
	private static double crystalPricing(Map<GearSlot, SetupBuilder.Pick> picks)
	{
		List<Integer> ids = new ArrayList<>();
		picks.values().forEach(p -> ids.add(p == null ? 0 : p.getItemId()));
		boolean bothCrystal = ids.contains(20) && ids.contains(21);
		double damage = 10 + (ids.contains(10) ? 3 : 0) + (ids.contains(11) ? 3 : 0)
			+ (ids.contains(20) ? 1 : 0) + (ids.contains(21) ? 1 : 0)
			+ (bothCrystal ? 30 : 0);
		return 1000.0 / damage;
	}

	private static Map<GearSlot, List<SetupBuilder.Pick>> crystalShortlists()
	{
		Map<GearSlot, List<SetupBuilder.Pick>> shortlists = new LinkedHashMap<>();
		shortlists.put(GearSlot.HEAD, Arrays.asList(item("Neitiznot", 10), item("Crystal helm", 20)));
		shortlists.put(GearSlot.BODY, Arrays.asList(item("Karil top", 11), item("Crystal body", 21)));
		return shortlists;
	}

	/**
	 * The limitation, stated as a test so nobody deletes the crystal weighting
	 * in the heuristic believing DPS ranking has made it redundant.
	 *
	 * Climbing one slot at a time cannot find a set whose pieces are each
	 * individually a loss: the first swap is rejected before the second can
	 * pay for it. The heuristic seed is what puts crystal on in the first
	 * place.
	 */
	@Test
	public void cannotDiscoverASetWhosePiecesAreIndividuallyWorse()
	{
		Map<GearSlot, SetupBuilder.Pick> start = new EnumMap<>(GearSlot.class);
		start.put(GearSlot.HEAD, item("Neitiznot", 10));
		start.put(GearSlot.BODY, item("Karil top", 11));

		Map<GearSlot, SetupBuilder.Pick> out =
			GearResolver.improve(start, crystalShortlists(), GearSearchTest::crystalPricing);

		assertEquals(10, out.get(GearSlot.HEAD).getItemId());
		assertEquals(11, out.get(GearSlot.BODY).getItemId());
	}

	/**
	 * The half that actually protects the plan: once seeded with the set, the
	 * search must not dismantle it. Every single-piece removal costs the set
	 * bonus, so every one of them has to be rejected.
	 */
	@Test
	public void doesNotBreakUpASetItStartsWith()
	{
		Map<GearSlot, SetupBuilder.Pick> start = new EnumMap<>(GearSlot.class);
		start.put(GearSlot.HEAD, item("Crystal helm", 20));
		start.put(GearSlot.BODY, item("Crystal body", 21));

		Map<GearSlot, SetupBuilder.Pick> out =
			GearResolver.improve(start, crystalShortlists(), GearSearchTest::crystalPricing);

		assertEquals(20, out.get(GearSlot.HEAD).getItemId());
		assertEquals(21, out.get(GearSlot.BODY).getItemId());
	}

	@Test
	public void leavesEverythingAloneWhenNothingIsPriceable()
	{
		Map<GearSlot, SetupBuilder.Pick> start = new EnumMap<>(GearSlot.class);
		start.put(GearSlot.RING, item("Seers ring", 1));

		Map<GearSlot, List<SetupBuilder.Pick>> shortlists = new LinkedHashMap<>();
		shortlists.put(GearSlot.RING, Arrays.asList(item("Magus ring", 2)));

		// A zero-dps loadout means the weapon cannot hit this target at all;
		// swapping jewellery must not be presented as an improvement.
		Map<GearSlot, SetupBuilder.Pick> out =
			GearResolver.improve(start, shortlists, p -> 0.0);

		assertEquals(1, out.get(GearSlot.RING).getItemId());
	}

	@Test
	public void neverReturnsAWorseLoadoutThanItStarted()
	{
		Map<Integer, Double> worth = new LinkedHashMap<>();
		for (int id = 1; id <= 12; id++)
		{
			worth.put(id, (id * 7 % 13) * 1.0);
		}

		Map<GearSlot, SetupBuilder.Pick> start = new EnumMap<>(GearSlot.class);
		start.put(GearSlot.RING, item("a", 1));
		start.put(GearSlot.HEAD, item("b", 2));

		Map<GearSlot, List<SetupBuilder.Pick>> shortlists = new LinkedHashMap<>();
		shortlists.put(GearSlot.RING, Arrays.asList(item("a", 1), item("c", 5), item("d", 9)));
		shortlists.put(GearSlot.HEAD, Arrays.asList(item("b", 2), item("e", 6), item("f", 12)));

		double before = secondsFrom(start, worth);
		Map<GearSlot, SetupBuilder.Pick> out =
			GearResolver.improve(start, shortlists, p -> secondsFrom(p, worth));

		assertTrue("search must not make the loadout slower",
			secondsFrom(out, worth) <= before + 1e-9);
	}
}
