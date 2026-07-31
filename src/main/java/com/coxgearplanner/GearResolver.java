package com.coxgearplanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/**
 * Chooses what to wear per slot by scanning <em>every</em> item you own and
 * scoring it with the client's real equipment stats, rather than matching
 * against a hand-written list of item ids.
 *
 * This is what makes new or obscure gear work without a code change: if an
 * item is in your bank, is equipable, and has offensive stats, it competes.
 * {@link GearDatabase} is still used for the weapon tier order (weapon choice
 * needs special-case knowledge like the twisted bow's scaling) and to name a
 * best-in-slot target for slots where you own nothing at all.
 *
 * Must be used on the client thread — {@link ItemManager#getItemComposition}
 * and {@link ItemManager#getItemStats} both read client data.
 */
public class GearResolver
{
	// EquipmentInventorySlot indices, as reported by ItemEquipmentStats.getSlot()
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
	private static final int SLOT_AMMO = 13;

	/** Crystal armour pieces and the bow they buff, for set-effect scoring. */
	private static final Set<Integer> CRYSTAL_PIECES = new HashSet<>(Arrays.asList(
		23971, 27705, 27717, 27729, 27741, 27753, 27765, 27777, // helms
		23975, 27697, 27709, 27721, 27733, 27745, 27757, 27769, // bodies
		23979, 27701, 27713, 27725, 27737, 27749, 27761, 27773)); // legs
	private static final Set<Integer> CRYSTAL_BOWS = new HashSet<>(Arrays.asList(
		25865, 25867, 25884, 25886, 25888, 25890, 25892, 25894, 25896, // bofa
		4212, 4214)); // crystal bow

	private final ItemManager itemManager;

	// The scan walks the whole bank through the client's item cache, and the
	// planner asks for it once per room per style, so memoise it. Keyed on the
	// identity of the snapshot map, which computePlan takes once and reuses.
	private Map<ItemSource, Map<Integer, Integer>> cachedFor;
	private boolean cachedIncludeGroup;
	private Map<GearSlot, List<SetupBuilder.Pick>> cachedScan;
	private final Map<GearNeed, Map<GearSlot, SetupBuilder.Pick>> cachedResolve =
		new java.util.EnumMap<>(GearNeed.class);
	private final Map<Integer, Double> cachedScores = new java.util.HashMap<>();

	public GearResolver(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	private void useSnapshot(Map<ItemSource, Map<Integer, Integer>> items, boolean includeGroupStorage)
	{
		if (cachedFor != items || cachedIncludeGroup != includeGroupStorage)
		{
			cachedFor = items;
			cachedIncludeGroup = includeGroupStorage;
			cachedScan = null;
			cachedResolve.clear();
			cachedScores.clear();
		}
	}

	/**
	 * Best owned item per slot for a style. Slots with nothing owned map to
	 * null. No two-handed/shield suppression — callers decide that per
	 * candidate weapon.
	 */
	public Map<GearSlot, SetupBuilder.Pick> resolve(
		GearNeed style,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage)
	{
		if (itemManager == null)
		{
			return SetupBuilder.resolveLoadout(style, items, includeGroupStorage);
		}

		useSnapshot(items, includeGroupStorage);
		Map<GearSlot, SetupBuilder.Pick> memo = cachedResolve.get(style);
		if (memo != null)
		{
			return new LinkedHashMap<>(memo);
		}

		// The curated tier list still drives weapon and ammo choice
		Map<GearSlot, SetupBuilder.Pick> picks =
			SetupBuilder.resolveLoadout(style, items, includeGroupStorage);

		Map<GearSlot, List<SetupBuilder.Pick>> owned = scan(items, includeGroupStorage);
		boolean crystalBowOwned = ownsCrystalBow(items, includeGroupStorage);

		for (Map.Entry<GearSlot, List<SetupBuilder.Pick>> entry : owned.entrySet())
		{
			GearSlot slot = entry.getKey();
			SetupBuilder.Pick current = picks.get(slot);

			if (slot == GearSlot.WEAPON || slot == GearSlot.AMMO)
			{
				// Only fall back to a scanned item when the curated list found
				// nothing you own — these slots need special-case knowledge.
				if (current == null)
				{
					picks.put(slot, best(entry.getValue(), style, crystalBowOwned));
				}
				continue;
			}

			SetupBuilder.Pick scanned = best(entry.getValue(), style, crystalBowOwned);
			if (scanned == null)
			{
				continue;
			}
			if (current == null
				|| score(scanned.getItemId(), style, crystalBowOwned)
					> score(current.getItemId(), style, crystalBowOwned))
			{
				picks.put(slot, scanned);
			}
		}

		cachedResolve.put(style, new LinkedHashMap<>(picks));
		return picks;
	}

	/**
	 * Records, for each armour slot, which item was chosen and what it beat.
	 * Scores are the internal ranking numbers, not DPS.
	 */
	public void describeChoices(
		GearNeed style,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage,
		PlanExplanation explanation)
	{
		if (itemManager == null || explanation == null)
		{
			return;
		}

		Map<GearSlot, SetupBuilder.Pick> chosen = resolve(style, items, includeGroupStorage);
		Map<GearSlot, List<SetupBuilder.Pick>> owned = scan(items, includeGroupStorage);
		boolean crystalBow = ownsCrystalBow(items, includeGroupStorage);

		for (GearSlot slot : GearSlot.values())
		{
			SetupBuilder.Pick pick = chosen.get(slot);
			if (pick == null)
			{
				explanation.addGearChoice(style.getDisplayName() + " " + slot.getDisplayName()
					+ ": nothing owned");
				continue;
			}

			// Best owned alternative that isn't the chosen item
			SetupBuilder.Pick runnerUp = null;
			double runnerUpScore = Double.NEGATIVE_INFINITY;
			for (SetupBuilder.Pick candidate : owned.getOrDefault(slot, java.util.Collections.emptyList()))
			{
				if (candidate.getItemId() == pick.getItemId())
				{
					continue;
				}
				double s = score(candidate.getItemId(), style, crystalBow);
				if (s > runnerUpScore)
				{
					runnerUpScore = s;
					runnerUp = candidate;
				}
			}

			String detail = runnerUp == null
				? "only option you own"
				: String.format("beat %s (%.0f vs %.0f)", runnerUp.getOption().getName(),
					score(pick.getItemId(), style, crystalBow), runnerUpScore);
			explanation.addGearChoice(style.getDisplayName() + " " + slot.getDisplayName()
				+ ": " + pick.getOption().getName() + " — " + detail);
		}
	}

	/** Every owned equipable item, grouped by the slot it actually occupies. */
	public Map<GearSlot, List<SetupBuilder.Pick>> scan(
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage)
	{
		useSnapshot(items, includeGroupStorage);
		if (cachedScan != null)
		{
			return cachedScan;
		}

		Map<GearSlot, List<SetupBuilder.Pick>> result = new LinkedHashMap<>();
		Set<Integer> seen = new HashSet<>();

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

			for (Map.Entry<Integer, Integer> owned : pool.entrySet())
			{
				int id = owned.getKey();
				Integer qty = owned.getValue();
				if (qty == null || qty <= 0 || !seen.add(id))
				{
					continue;
				}

				ItemStats stats = itemManager.getItemStats(id);
				if (stats == null || !stats.isEquipable() || stats.getEquipment() == null)
				{
					continue;
				}
				GearSlot slot = toGearSlot(stats.getEquipment().getSlot());
				if (slot == null)
				{
					continue;
				}

				ItemOption option = stats.getEquipment().isTwoHanded()
					? ItemOption.twoHanded(nameOf(id), id)
					: ItemOption.of(nameOf(id), id);
				result.computeIfAbsent(slot, k -> new ArrayList<>())
					.add(new SetupBuilder.Pick(option, source, id, qty));
			}
		}

		cachedScan = result;
		return result;
	}

	private String nameOf(int id)
	{
		try
		{
			String name = itemManager.getItemComposition(id).getName();
			return name == null || name.isEmpty() || "null".equals(name)
				? "Item " + id
				: name;
		}
		catch (RuntimeException e)
		{
			return "Item " + id;
		}
	}

	private SetupBuilder.Pick best(List<SetupBuilder.Pick> candidates, GearNeed style, boolean crystalBow)
	{
		SetupBuilder.Pick best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (SetupBuilder.Pick candidate : candidates)
		{
			double s = score(candidate.getItemId(), style, crystalBow);
			if (s > bestScore)
			{
				bestScore = s;
				best = candidate;
			}
		}
		return best;
	}

	/**
	 * Rough offensive value of an item for a style, used to order candidates.
	 * Strength-type bonuses dominate damage, so they are weighted above raw
	 * accuracy; the reported room times still come from the real DPS formulas.
	 */
	private double score(int itemId, GearNeed style, boolean crystalBow)
	{
		int key = itemId * 8 + style.ordinal();
		Double memo = cachedScores.get(key);
		if (memo != null)
		{
			return memo;
		}
		double computed = computeScore(itemId, style, crystalBow);
		cachedScores.put(key, computed);
		return computed;
	}

	private double computeScore(int itemId, GearNeed style, boolean crystalBow)
	{
		ItemStats stats = itemManager.getItemStats(itemId);
		if (stats == null || stats.getEquipment() == null)
		{
			return Double.NEGATIVE_INFINITY;
		}
		ItemEquipmentStats eq = stats.getEquipment();

		double value;
		switch (style)
		{
			case MELEE:
				value = eq.getStr() * 10.0
					+ Math.max(eq.getAstab(), Math.max(eq.getAslash(), eq.getAcrush()));
				break;
			case RANGED:
				value = eq.getRstr() * 10.0 + eq.getArange();
				// Crystal armour only pays off with a crystal bow / bofa
				if (crystalBow && CRYSTAL_PIECES.contains(itemId))
				{
					value += 60;
				}
				break;
			case MAGIC:
				value = eq.getMdmg() * 15.0 + eq.getAmagic();
				break;
			default:
				return Double.NEGATIVE_INFINITY;
		}
		return value;
	}

	private boolean ownsCrystalBow(Map<ItemSource, Map<Integer, Integer>> items, boolean includeGroupStorage)
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
			for (int id : CRYSTAL_BOWS)
			{
				Integer qty = pool.get(id);
				if (qty != null && qty > 0)
				{
					return true;
				}
			}
		}
		return false;
	}

	static GearSlot toGearSlot(int slotIdx)
	{
		switch (slotIdx)
		{
			case SLOT_HEAD:
				return GearSlot.HEAD;
			case SLOT_CAPE:
				return GearSlot.CAPE;
			case SLOT_AMULET:
				return GearSlot.NECK;
			case SLOT_WEAPON:
				return GearSlot.WEAPON;
			case SLOT_BODY:
				return GearSlot.BODY;
			case SLOT_SHIELD:
				return GearSlot.SHIELD;
			case SLOT_LEGS:
				return GearSlot.LEGS;
			case SLOT_GLOVES:
				return GearSlot.GLOVES;
			case SLOT_BOOTS:
				return GearSlot.BOOTS;
			case SLOT_RING:
				return GearSlot.RING;
			case SLOT_AMMO:
				return GearSlot.AMMO;
			default:
				return null; // ARMS/HAIR/JAW — not equipment the planner cares about
		}
	}
}
