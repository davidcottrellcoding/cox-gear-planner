package com.coxgearplanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All-or-nothing armour set effects.
 *
 * These can't be handled the way crystal armour and Inquisitor's are (a bonus
 * per piece), because they only pay out when every piece is worn — and the
 * planner picks each slot independently, so it would never assemble one by
 * accident. The estimator therefore tries each set you own as a complete
 * alternative loadout and keeps it only when it is genuinely faster.
 *
 * Multipliers are applied to the attack roll and to the max hit.
 */
public final class GearSetBonus
{
	// Void Knight equipment
	private static final int VOID_MELEE_HELM = 11665;
	private static final int VOID_RANGER_HELM = 11664;
	private static final int VOID_MAGE_HELM = 11663;
	private static final int[] VOID_TOPS = {8839, 13072};   // regular, elite
	private static final int[] VOID_ROBES = {8840, 13073};
	private static final int VOID_GLOVES = 8842;
	private static final int ELITE_TOP = 13072;
	private static final int ELITE_ROBE = 13073;

	// Obsidian armour, which only pays out alongside an obsidian weapon
	private static final int OBSIDIAN_HELM = 21298;
	private static final int OBSIDIAN_BODY = 21301;
	private static final int OBSIDIAN_LEGS = 21304;
	private static final int[] OBSIDIAN_WEAPONS = {6523, 6525, 6528, 23235}; // ak, ek, om, om (t)

	/** A set the player owns, expressed as the slots it would occupy. */
	public static class SetOption
	{
		private final String name;
		private final GearNeed style;
		private final Map<GearSlot, Integer> pieces;

		SetOption(String name, GearNeed style, Map<GearSlot, Integer> pieces)
		{
			this.name = name;
			this.style = style;
			this.pieces = pieces;
		}

		public String getName()
		{
			return name;
		}

		public GearNeed getStyle()
		{
			return style;
		}

		public Map<GearSlot, Integer> getPieces()
		{
			return pieces;
		}
	}

	private GearSetBonus()
	{
	}

	/**
	 * Accuracy and damage multipliers granted by whatever complete set is
	 * present in {@code equipped}.
	 *
	 * @return {accuracyMultiplier, damageMultiplier}
	 */
	public static double[] multipliers(Set<Integer> equipped, GearNeed style, int weaponId)
	{
		boolean top = containsAny(equipped, VOID_TOPS);
		boolean robe = containsAny(equipped, VOID_ROBES);
		boolean gloves = equipped.contains(VOID_GLOVES);
		boolean elite = equipped.contains(ELITE_TOP) && equipped.contains(ELITE_ROBE);

		if (top && robe && gloves)
		{
			// Void: helm decides which style the set boosts
			if (style == GearNeed.MELEE && equipped.contains(VOID_MELEE_HELM))
			{
				return new double[]{1.10, 1.10};
			}
			if (style == GearNeed.RANGED && equipped.contains(VOID_RANGER_HELM))
			{
				return new double[]{1.10, elite ? 1.125 : 1.10};
			}
			if (style == GearNeed.MAGIC && equipped.contains(VOID_MAGE_HELM))
			{
				// Magic void is an accuracy set; only elite adds damage
				return new double[]{1.45, elite ? 1.05 : 1.0};
			}
		}

		if (style == GearNeed.MELEE
			&& equipped.contains(OBSIDIAN_HELM)
			&& equipped.contains(OBSIDIAN_BODY)
			&& equipped.contains(OBSIDIAN_LEGS)
			&& contains(OBSIDIAN_WEAPONS, weaponId))
		{
			return new double[]{1.10, 1.10};
		}

		return new double[]{1.0, 1.0};
	}

	/** Every complete set the player owns, as slot overrides to try. */
	public static List<SetOption> ownedSets(
		GearNeed style,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage)
	{
		List<SetOption> owned = new ArrayList<>();

		Integer top = firstOwned(items, includeGroupStorage, VOID_TOPS);
		Integer robe = firstOwned(items, includeGroupStorage, VOID_ROBES);
		Integer gloves = firstOwned(items, includeGroupStorage, new int[]{VOID_GLOVES});
		int helmId = style == GearNeed.MELEE ? VOID_MELEE_HELM
			: style == GearNeed.RANGED ? VOID_RANGER_HELM
			: VOID_MAGE_HELM;
		Integer helm = firstOwned(items, includeGroupStorage, new int[]{helmId});

		if (top != null && robe != null && gloves != null && helm != null)
		{
			Map<GearSlot, Integer> pieces = new LinkedHashMap<>();
			pieces.put(GearSlot.HEAD, helm);
			pieces.put(GearSlot.BODY, top);
			pieces.put(GearSlot.LEGS, robe);
			pieces.put(GearSlot.GLOVES, gloves);
			boolean elite = top == ELITE_TOP && robe == ELITE_ROBE;
			owned.add(new SetOption((elite ? "Elite void" : "Void") + " ("
				+ style.getDisplayName().toLowerCase() + ")", style, pieces));
		}

		if (style == GearNeed.MELEE)
		{
			Integer oHelm = firstOwned(items, includeGroupStorage, new int[]{OBSIDIAN_HELM});
			Integer oBody = firstOwned(items, includeGroupStorage, new int[]{OBSIDIAN_BODY});
			Integer oLegs = firstOwned(items, includeGroupStorage, new int[]{OBSIDIAN_LEGS});
			if (oHelm != null && oBody != null && oLegs != null)
			{
				Map<GearSlot, Integer> pieces = new LinkedHashMap<>();
				pieces.put(GearSlot.HEAD, oHelm);
				pieces.put(GearSlot.BODY, oBody);
				pieces.put(GearSlot.LEGS, oLegs);
				owned.add(new SetOption("Obsidian armour", style, pieces));
			}
		}

		return owned;
	}

	/** Item ids of the obsidian weapons the obsidian set requires. */
	public static int[] obsidianWeapons()
	{
		return Arrays.copyOf(OBSIDIAN_WEAPONS, OBSIDIAN_WEAPONS.length);
	}

	private static Integer firstOwned(
		Map<ItemSource, Map<Integer, Integer>> items, boolean includeGroupStorage, int[] ids)
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
			for (int id : ids)
			{
				Integer qty = pool.get(id);
				if (qty != null && qty > 0)
				{
					return id;
				}
			}
		}
		return null;
	}

	private static boolean containsAny(Set<Integer> equipped, int[] ids)
	{
		for (int id : ids)
		{
			if (equipped.contains(id))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean contains(int[] ids, int value)
	{
		for (int id : ids)
		{
			if (id == value)
			{
				return true;
			}
		}
		return false;
	}

	static List<int[]> voidPieceIds()
	{
		return Collections.unmodifiableList(Arrays.asList(
			VOID_TOPS, VOID_ROBES, new int[]{VOID_GLOVES},
			new int[]{VOID_MELEE_HELM, VOID_RANGER_HELM, VOID_MAGE_HELM}));
	}
}
