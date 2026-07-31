package com.coxgearplanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the best available loadout for a set of selected rooms against
 * everything the plugin has seen in your inventory, equipment, bank and
 * group storage.
 */
public final class SetupBuilder
{
	private SetupBuilder()
	{
	}

	/** One rendered line of the suggestion: "Weapon: Twisted bow [Bank]". */
	public static class Line
	{
		private final String label;
		private final String itemName;
		private final ItemSource source; // null when the item isn't owned anywhere
		private final boolean missing;
		private final int quantity;

		Line(String label, String itemName, ItemSource source, boolean missing, int quantity)
		{
			this.label = label;
			this.itemName = itemName;
			this.source = source;
			this.missing = missing;
			this.quantity = quantity;
		}

		public String getLabel()
		{
			return label;
		}

		public String getItemName()
		{
			return itemName;
		}

		public ItemSource getSource()
		{
			return source;
		}

		public boolean isMissing()
		{
			return missing;
		}

		public int getQuantity()
		{
			return quantity;
		}
	}

	/** A titled group of lines (one per combat style, plus utilities). */
	public static class Section
	{
		private final String title;
		private final List<Line> lines = new ArrayList<>();

		Section(String title)
		{
			this.title = title;
		}

		public String getTitle()
		{
			return title;
		}

		public List<Line> getLines()
		{
			return lines;
		}
	}

	public static List<Section> build(
		Set<CoxRoom> rooms,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage)
	{
		EnumSet<GearNeed> needs = EnumSet.noneOf(GearNeed.class);
		for (CoxRoom room : rooms)
		{
			needs.addAll(room.getNeeds());
		}

		List<Section> sections = new ArrayList<>();

		for (GearNeed style : Arrays.asList(GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC))
		{
			if (!needs.contains(style))
			{
				continue;
			}

			String forRooms = rooms.stream()
				.filter(r -> r.getNeeds().contains(style))
				.map(CoxRoom::getDisplayName)
				.collect(Collectors.joining(", "));
			Section section = new Section(style.getDisplayName() + "  (" + forRooms + ")");

			boolean twoHandedChosen = false;
			for (Map.Entry<GearSlot, List<ItemOption>> entry : GearDatabase.loadout(style).entrySet())
			{
				GearSlot slot = entry.getKey();
				if (slot == GearSlot.SHIELD && twoHandedChosen)
				{
					section.getLines().add(new Line(slot.getDisplayName(), "— (two-handed weapon)", null, false, 0));
					continue;
				}

				Pick pick = pickBest(entry.getValue(), items, includeGroupStorage);
				if (pick != null)
				{
					if (slot == GearSlot.WEAPON && pick.option.isTwoHanded())
					{
						twoHandedChosen = true;
					}
					section.getLines().add(new Line(slot.getDisplayName(), pick.option.getName(), pick.source, false, pick.quantity));
				}
				else
				{
					List<ItemOption> options = entry.getValue();
					String best = options.isEmpty() ? "?" : options.get(0).getName();
					section.getLines().add(new Line(slot.getDisplayName(), "none owned (best: " + best + ")", null, true, 0));
				}
			}
			sections.add(section);
		}

		List<GearNeed> utilityNeeds = needs.stream()
			.filter(n -> !n.isCombatStyle())
			.collect(Collectors.toList());
		if (!utilityNeeds.isEmpty())
		{
			Section utilities = new Section("Utility items");
			for (GearNeed need : utilityNeeds)
			{
				Pick pick = pickBest(GearDatabase.utility(need), items, includeGroupStorage);
				if (pick != null)
				{
					utilities.getLines().add(new Line(need.getDisplayName(), pick.option.getName(), pick.source, false, pick.quantity));
				}
				else
				{
					List<ItemOption> options = GearDatabase.utility(need);
					String best = options.isEmpty() ? "?" : options.get(0).getName();
					utilities.getLines().add(new Line(need.getDisplayName(), "none owned (best: " + best + ")", null, true, 0));
				}
			}
			sections.add(utilities);
		}

		return sections;
	}

	private static class Pick
	{
		final ItemOption option;
		final ItemSource source;
		final int quantity;

		Pick(ItemOption option, ItemSource source, int quantity)
		{
			this.option = option;
			this.source = source;
			this.quantity = quantity;
		}
	}

	private static Pick pickBest(
		List<ItemOption> options,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage)
	{
		if (options == null)
		{
			return null;
		}

		for (ItemOption option : options)
		{
			for (ItemSource source : ItemSource.values())
			{
				if (source == ItemSource.GROUP_STORAGE && !includeGroupStorage)
				{
					continue;
				}

				Map<Integer, Integer> pool = items.get(source);
				if (pool == null)
				{
					continue;
				}

				for (int id : option.getItemIds())
				{
					Integer qty = pool.get(id);
					if (qty != null && qty > 0)
					{
						return new Pick(option, source, qty);
					}
				}
			}
		}
		return null;
	}
}
