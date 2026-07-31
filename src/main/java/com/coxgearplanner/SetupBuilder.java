package com.coxgearplanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
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

	/** A resolved item: which option, which concrete item id, and where it is. */
	public static class Pick
	{
		private final ItemOption option;
		private final ItemSource source;
		private final int itemId;
		private final int quantity;

		Pick(ItemOption option, ItemSource source, int itemId, int quantity)
		{
			this.option = option;
			this.source = source;
			this.itemId = itemId;
			this.quantity = quantity;
		}

		public ItemOption getOption()
		{
			return option;
		}

		public ItemSource getSource()
		{
			return source;
		}

		public int getItemId()
		{
			return itemId;
		}

		public int getQuantity()
		{
			return quantity;
		}
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

	/**
	 * Best owned pick for a single option, searching sources in preference
	 * order. Returns null if not owned anywhere.
	 */
	public static Pick findOwned(
		ItemOption option,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage)
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
					return new Pick(option, source, id, qty);
				}
			}
		}
		return null;
	}

	/** First owned option from a best-first list, or null. */
	public static Pick pickBest(
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
			Pick pick = findOwned(option, items, includeGroupStorage);
			if (pick != null)
			{
				return pick;
			}
		}
		return null;
	}

	/**
	 * Best owned item per slot for a style. Slots with nothing owned map to
	 * null. No two-handed/shield suppression here — callers decide that per
	 * candidate weapon.
	 */
	public static Map<GearSlot, Pick> resolveLoadout(
		GearNeed style,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage)
	{
		Map<GearSlot, Pick> picks = new LinkedHashMap<>();
		for (Map.Entry<GearSlot, List<ItemOption>> entry : GearDatabase.loadout(style).entrySet())
		{
			picks.put(entry.getKey(), pickBest(entry.getValue(), items, includeGroupStorage));
		}
		return picks;
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

			Map<GearSlot, Pick> picks = resolveLoadout(style, items, includeGroupStorage);
			Pick weapon = picks.get(GearSlot.WEAPON);
			boolean twoHandedChosen = weapon != null && weapon.getOption().isTwoHanded();

			for (Map.Entry<GearSlot, Pick> entry : picks.entrySet())
			{
				GearSlot slot = entry.getKey();
				if (slot == GearSlot.SHIELD && twoHandedChosen)
				{
					section.getLines().add(new Line(slot.getDisplayName(), "— (two-handed weapon)", null, false, 0));
					continue;
				}

				Pick pick = entry.getValue();
				if (pick != null)
				{
					section.getLines().add(new Line(slot.getDisplayName(), pick.getOption().getName(),
						pick.getSource(), false, pick.getQuantity()));
				}
				else
				{
					List<ItemOption> options = GearDatabase.loadout(style).get(slot);
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
					utilities.getLines().add(new Line(need.getDisplayName(), pick.getOption().getName(),
						pick.getSource(), false, pick.getQuantity()));
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
}
