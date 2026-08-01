package com.coxgearplanner;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * An empty slot must render, not crash.
 *
 * The base outfit legitimately leaves slots empty — a two-handed weapon takes
 * the shield slot, a bow of faerdhinen fires no ammunition. The renderer fell
 * back to naming a best-in-slot to chase, but the curated list has no entry
 * for every slot: melee and magic name no ammunition at all, so the lookup
 * returned null and threw on the client thread, taking the whole plan with it.
 */
public class EmptySlotRenderTest
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

	/** Every slot renders a line even when the curated list names nothing. */
	@Test
	public void aSlotWithNoCuratedSuggestionStillRenders()
	{
		List<SetupBuilder.Section> sections = SetupBuilder.build(
			EnumSet.of(CoxRoom.OLM), bankWith(22325), true);

		assertNotNull(sections);
		assertTrue("expected at least one style section", sections.size() > 0);
		for (SetupBuilder.Section section : sections)
		{
			for (SetupBuilder.Line line : section.getLines())
			{
				assertNotNull("every line needs a label", line.getLabel());
				assertNotNull("every line needs text, empty slots included",
					line.getItemName());
			}
		}
	}

	/** Owning nothing at all is the widest version of the same path. */
	@Test
	public void anEmptyBankDoesNotCrash()
	{
		List<SetupBuilder.Section> sections = SetupBuilder.build(
			EnumSet.allOf(CoxRoom.class), bankWith(), true);

		assertNotNull(sections);
		for (SetupBuilder.Section section : sections)
		{
			for (SetupBuilder.Line line : section.getLines())
			{
				assertNotNull(line.getItemName());
			}
		}
	}
}
