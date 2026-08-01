package com.coxgearplanner;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Prayer bonus decides what the clock cannot.
 *
 * A fraction of a second across a whole raid is inside the error of
 * everything here, so giving up prayer bonus to chase it is a bad trade. The
 * case that exposed this was the ammo slot: next to a bow of faerdhinen,
 * which fires no ammo, nothing in that slot changes damage at all, so every
 * candidate tied and the winner was whichever the bank scan happened to reach
 * first.
 */
public class PrayerTiebreakTest
{
	private static SetupBuilder.Pick item(String name, int id)
	{
		return new SetupBuilder.Pick(ItemOption.of(name, id), ItemSource.BANK, id, 1);
	}

	private static double prayerOf(Map<GearSlot, SetupBuilder.Pick> picks, Map<Integer, Double> table)
	{
		double total = 0;
		for (SetupBuilder.Pick pick : picks.values())
		{
			if (pick != null)
			{
				total += table.getOrDefault(pick.getItemId(), 0.0);
			}
		}
		return total;
	}

	/** Broad arrows (no prayer) against a Rada's blessing (prayer), tied on damage. */
	@Test
	public void aSlotThatChangesNothingGoesToPrayerBonus()
	{
		Map<Integer, Double> prayer = new LinkedHashMap<>();
		prayer.put(1, 0.0);  // broad arrows
		prayer.put(2, 8.0);  // rada's blessing

		Map<GearSlot, SetupBuilder.Pick> start = new EnumMap<>(GearSlot.class);
		start.put(GearSlot.AMMO, item("Broad arrows", 1));

		Map<GearSlot, List<SetupBuilder.Pick>> shortlists = new LinkedHashMap<>();
		shortlists.put(GearSlot.AMMO,
			Arrays.asList(item("Broad arrows", 1), item("Rada's blessing 4", 2)));

		Map<GearSlot, SetupBuilder.Pick> out = GearResolver.improve(
			start, shortlists, picks -> 100.0, picks -> prayerOf(picks, prayer));

		assertEquals(2, out.get(GearSlot.AMMO).getItemId());
	}

	/** A gain inside the noise floor is not worth losing prayer bonus over. */
	@Test
	public void aSubSecondGainDoesNotBeatPrayerBonus()
	{
		Map<Integer, Double> prayer = new LinkedHashMap<>();
		prayer.put(1, 8.0);
		prayer.put(2, 0.0);

		Map<GearSlot, SetupBuilder.Pick> start = new EnumMap<>(GearSlot.class);
		start.put(GearSlot.RING, item("Prayer ring", 1));

		Map<GearSlot, List<SetupBuilder.Pick>> shortlists = new LinkedHashMap<>();
		shortlists.put(GearSlot.RING,
			Arrays.asList(item("Prayer ring", 1), item("Damage ring", 2)));

		// The damage ring is half a second faster over the whole raid.
		Map<GearSlot, SetupBuilder.Pick> out = GearResolver.improve(start, shortlists,
			picks -> picks.get(GearSlot.RING).getItemId() == 2 ? 99.5 : 100.0,
			picks -> prayerOf(picks, prayer));

		assertEquals("half a second is not worth 8 prayer", 1, out.get(GearSlot.RING).getItemId());
	}

	/** A real gain still wins — prayer breaks ties, it does not outrank damage. */
	@Test
	public void aRealGainStillBeatsPrayerBonus()
	{
		Map<Integer, Double> prayer = new LinkedHashMap<>();
		prayer.put(1, 8.0);
		prayer.put(2, 0.0);

		Map<GearSlot, SetupBuilder.Pick> start = new EnumMap<>(GearSlot.class);
		start.put(GearSlot.RING, item("Prayer ring", 1));

		Map<GearSlot, List<SetupBuilder.Pick>> shortlists = new LinkedHashMap<>();
		shortlists.put(GearSlot.RING,
			Arrays.asList(item("Prayer ring", 1), item("Damage ring", 2)));

		Map<GearSlot, SetupBuilder.Pick> out = GearResolver.improve(start, shortlists,
			picks -> picks.get(GearSlot.RING).getItemId() == 2 ? 70.0 : 100.0,
			picks -> prayerOf(picks, prayer));

		assertEquals("30 seconds is a real gain", 2, out.get(GearSlot.RING).getItemId());
	}

	/** Equal prayer and no meaningful time difference: leave the slot alone. */
	@Test
	public void nothingToChooseBetweenLeavesTheIncumbent()
	{
		Map<GearSlot, SetupBuilder.Pick> start = new EnumMap<>(GearSlot.class);
		start.put(GearSlot.AMMO, item("Broad arrows", 1));

		Map<GearSlot, List<SetupBuilder.Pick>> shortlists = new LinkedHashMap<>();
		shortlists.put(GearSlot.AMMO,
			Arrays.asList(item("Broad arrows", 1), item("Bronze arrows", 2)));

		Map<GearSlot, SetupBuilder.Pick> out = GearResolver.improve(
			start, shortlists, picks -> 100.0, picks -> 0);

		assertEquals(1, out.get(GearSlot.AMMO).getItemId());
	}
}
