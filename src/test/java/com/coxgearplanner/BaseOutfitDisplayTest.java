package com.coxgearplanner;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * The plan must render the outfit its times were computed against.
 *
 * The switch advisor is allowed to trade a base slot to remove an expensive
 * switch, so the base outfit is no longer always what the resolver would
 * produce for that style. Everything that draws the plan asks the resolver
 * what is being worn, so the traded outfit has to be pinned there — otherwise
 * the item list tells you to wear one thing while every number assumes
 * another, and the traded-in item vanishes from the plan entirely.
 */
public class BaseOutfitDisplayTest
{
	private static Map<ItemSource, Map<Integer, Integer>> bankWith(int... ids)
	{
		Map<Integer, Integer> bank = new HashMap<>();
		for (int id : ids)
		{
			bank.put(id, 1);
		}
		Map<ItemSource, Map<Integer, Integer>> items = new EnumMap<>(ItemSource.class);
		items.put(ItemSource.BANK, bank);
		return items;
	}

	private static SetupBuilder.Pick pick(String name, int id)
	{
		return new SetupBuilder.Pick(ItemOption.of(name, id), ItemSource.BANK, id, 1);
	}

	@Test
	public void aPinnedOutfitIsWhatLaterLookupsReturn()
	{
		Map<ItemSource, Map<Integer, Integer>> items = bankWith(22325, 30750);
		GearResolver resolver = new GearResolver(null);

		Map<GearSlot, SetupBuilder.Pick> traded = new LinkedHashMap<>();
		traded.put(GearSlot.RING, pick("Berserker ring (i)", 11773));

		resolver.pinResolved(GearNeed.MAGIC, traded, items, true);

		Map<GearSlot, SetupBuilder.Pick> shown =
			resolver.resolve(GearNeed.MAGIC, items, true);

		assertNotNull(shown.get(GearSlot.RING));
		assertEquals("Berserker ring (i)", shown.get(GearSlot.RING).getOption().getName());
	}

	/** Pinning one style must not silently rewrite the others. */
	@Test
	public void pinningOneStyleLeavesTheOthersAlone()
	{
		Map<ItemSource, Map<Integer, Integer>> items = bankWith(22325, 30750);
		GearResolver resolver = new GearResolver(null);

		Map<GearSlot, SetupBuilder.Pick> traded = new LinkedHashMap<>();
		traded.put(GearSlot.RING, pick("Berserker ring (i)", 11773));
		resolver.pinResolved(GearNeed.MAGIC, traded, items, true);

		Map<GearSlot, SetupBuilder.Pick> melee =
			resolver.resolve(GearNeed.MELEE, items, true);

		assertEquals("Scythe of vitur", melee.get(GearSlot.WEAPON).getOption().getName());
	}

	/**
	 * A pin belongs to the snapshot it was computed for. When the bank
	 * contents change the plan is recomputed from scratch, and carrying a
	 * stale traded outfit across would reintroduce exactly the disagreement
	 * this is meant to prevent.
	 */
	@Test
	public void aNewSnapshotDropsThePin()
	{
		GearResolver resolver = new GearResolver(null);

		Map<GearSlot, SetupBuilder.Pick> traded = new LinkedHashMap<>();
		traded.put(GearSlot.RING, pick("Berserker ring (i)", 11773));
		resolver.pinResolved(GearNeed.MAGIC, traded, bankWith(22325, 30750), true);

		// A different snapshot object: the bank was re-read.
		Map<ItemSource, Map<Integer, Integer>> fresh = bankWith(22325, 30750);
		Map<GearSlot, SetupBuilder.Pick> shown =
			resolver.resolve(GearNeed.MAGIC, fresh, true);

		SetupBuilder.Pick ring = shown.get(GearSlot.RING);
		if (ring != null)
		{
			assertEquals(false, "Berserker ring (i)".equals(ring.getOption().getName()));
		}
	}
}
