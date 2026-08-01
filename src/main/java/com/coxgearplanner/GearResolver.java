package com.coxgearplanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
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
		23983)); // crystal bow (pre-rework ids normalise onto this)

	private final ItemManager itemManager;

	// The scan walks the whole bank through the client's item cache, and the
	// planner asks for it once per room per style, so memoise it. Keyed on the
	// identity of the snapshot map, which computePlan takes once and reuses.
	private Map<ItemSource, Map<Integer, Integer>> cachedFor;
	private boolean cachedIncludeGroup;
	private Map<GearSlot, List<SetupBuilder.Pick>> cachedScan;
	/** What each style picked for itself, before any pin replaced it. */
	private final Map<GearNeed, Map<GearSlot, SetupBuilder.Pick>> prePin =
		new java.util.EnumMap<>(GearNeed.class);
	private final Map<GearNeed, Map<GearSlot, SetupBuilder.Pick>> cachedResolve =
		new java.util.EnumMap<>(GearNeed.class);
	private final Map<Integer, Double> cachedScores = new java.util.HashMap<>();

	// Ranking gear by a made-up score ("1% magic damage is worth 15 magic
	// attack") gets the easy calls right and the close ones wrong, because the
	// real value of an accuracy bonus collapses once you are already accurate.
	// When this context is supplied the resolver prices each slot with the
	// same DPS formulas that produce the room times, so a slot's pick and the
	// number reported for it come from one source. Without it — as in most
	// unit tests — it falls back to the heuristic.
	private RoomTimeEstimator dpsEstimator;
	private PlayerSnapshot dpsPlayer;
	private Set<CoxRoom> dpsRooms;
	private boolean dpsElitePrayers;

	public GearResolver(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	public void setDpsContext(
		RoomTimeEstimator estimator,
		PlayerSnapshot player,
		Set<CoxRoom> rooms,
		boolean elitePrayers)
	{
		this.dpsEstimator = estimator;
		this.dpsPlayer = player;
		this.dpsRooms = rooms;
		this.dpsElitePrayers = elitePrayers;
		cachedResolve.clear();
	}

	/** Seconds this style would spend on its targets wearing {@code picks}. */
	double totalSeconds(
		GearNeed style,
		SetupBuilder.Pick weapon,
		Map<GearSlot, SetupBuilder.Pick> picks,
		List<MonsterProfile> targets,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage)
	{
		double total = 0;
		for (MonsterProfile monster : targets)
		{
			double dps = dpsEstimator.loadoutDps(style, weapon, picks,
				java.util.Collections.emptyMap(), items, includeGroupStorage,
				dpsPlayer, monster, dpsElitePrayers);
			if (dps > 0)
			{
				total += monster.getHp() / dps;
			}
		}
		return total;
	}

	/**
	 * Monsters this style will actually be pointed at, deduplicated. Weighting
	 * falls out of their health: a slot that helps only on a 50hp add barely
	 * moves the total, which is the intended behaviour.
	 */
	List<MonsterProfile> dpsTargets(GearNeed style)
	{
		List<MonsterProfile> targets = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (CoxRoom room : CoxRoom.values())
		{
			if (dpsRooms == null || !dpsRooms.contains(room))
			{
				continue;
			}
			for (RoomMonsters.Encounter encounter : RoomMonsters.getAll(room))
			{
				MonsterProfile monster = encounter.getProfile();
				if (monster.getUsableStyles().contains(style) && seen.add(monster.getName()))
				{
					targets.add(monster);
				}
			}
		}
		return targets;
	}

	/**
	 * Re-picks each armour slot by the DPS it actually produces. The heuristic
	 * still runs first and narrows each slot to a shortlist — scoring every
	 * bank item against every target would be far more work for the same
	 * answer — but the decision between the finalists is made on real damage.
	 *
	 * Two passes, because slots interact: crystal and set bonuses mean the
	 * best helm can depend on the body already chosen.
	 */
	private Map<GearSlot, SetupBuilder.Pick> refineByDps(
		GearNeed style,
		Map<GearSlot, SetupBuilder.Pick> picks,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage)
	{
		SetupBuilder.Pick weapon = picks.get(GearSlot.WEAPON);
		if (dpsEstimator == null || dpsPlayer == null || weapon == null || refining)
		{
			return picks;
		}
		// Pricing a loadout runs the estimator, and the estimator resolves
		// gear. Nothing on that path calls back in today, but this runs on the
		// client thread, so a future edit that closed the loop would hang the
		// game rather than fail a test. Refuse to re-enter instead.
		refining = true;
		try
		{
			return refineByDps(style, picks, items, includeGroupStorage, weapon);
		}
		finally
		{
			refining = false;
		}
	}

	private boolean refining;

	private Map<GearSlot, SetupBuilder.Pick> refineByDps(
		GearNeed style,
		Map<GearSlot, SetupBuilder.Pick> picks,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage,
		SetupBuilder.Pick weapon)
	{
		List<MonsterProfile> targets = dpsTargets(style);
		if (targets.isEmpty())
		{
			return picks;
		}

		boolean twoHanded = weapon.getOption().isTwoHanded();
		boolean crystalBow = ownsCrystalBow(items, includeGroupStorage);
		Map<GearSlot, List<SetupBuilder.Pick>> owned = scan(items, includeGroupStorage);

		Map<GearSlot, List<SetupBuilder.Pick>> shortlists = new LinkedHashMap<>();
		Set<GearSlot> skip = EnumSet.of(GearSlot.WEAPON, GearSlot.AMMO);
		if (twoHanded)
		{
			skip.add(GearSlot.SHIELD);
		}
		for (GearSlot slot : GearSlot.values())
		{
			if (!skip.contains(slot))
			{
				shortlists.put(slot, shortlist(owned.get(slot), style, crystalBow));
			}
		}

		return improve(picks, shortlists,
			probe -> totalSeconds(style, weapon, probe, targets, items, includeGroupStorage),
			this::prayerBonus);
	}

	/**
	 * Greedy slot-by-slot search for the loadout with the lowest total time.
	 *
	 * Kept free of item lookups so the search itself can be tested: it takes a
	 * pricing function and a shortlist per slot, and knows nothing about what
	 * an item is.
	 *
	 * It climbs one slot at a time, which has a consequence worth stating: it
	 * cannot <em>discover</em> a set whose pieces are each individually worse
	 * than what they replace, because the first swap looks like a loss and is
	 * rejected before the second can pay for it. Crystal armour is exactly
	 * that shape. It is reached anyway because the heuristic seed already
	 * favours crystal when you own a crystal bow, and the search will not
	 * break the set up once seeded — every single-piece removal costs the set
	 * bonus and is rejected. So the crystal weighting in {@link #computeScore}
	 * is load-bearing, not legacy, and GearSearchTest pins both halves.
	 *
	 * The second pass exists for the cheaper interaction: once a slot changes,
	 * an earlier slot may have a better answer than it did.
	 */
	static Map<GearSlot, SetupBuilder.Pick> improve(
		Map<GearSlot, SetupBuilder.Pick> start,
		Map<GearSlot, List<SetupBuilder.Pick>> shortlists,
		java.util.function.ToDoubleFunction<Map<GearSlot, SetupBuilder.Pick>> seconds)
	{
		return improve(start, shortlists, seconds, picks -> 0);
	}

	/**
	 * As above, but with prayer bonus deciding anything the clock cannot.
	 *
	 * A tenth of a second across a whole raid is not a real difference - it is
	 * inside the error of everything this plugin estimates - so spending a
	 * slot on it while giving up prayer bonus is a bad trade. Within a second
	 * either way, the higher prayer bonus wins.
	 */
	static Map<GearSlot, SetupBuilder.Pick> improve(
		Map<GearSlot, SetupBuilder.Pick> start,
		Map<GearSlot, List<SetupBuilder.Pick>> shortlists,
		java.util.function.ToDoubleFunction<Map<GearSlot, SetupBuilder.Pick>> seconds,
		java.util.function.ToDoubleFunction<Map<GearSlot, SetupBuilder.Pick>> prayer)
	{
		Map<GearSlot, SetupBuilder.Pick> current = new LinkedHashMap<>(start);
		double best = seconds.applyAsDouble(current);
		if (!(best > 0))
		{
			return current;
		}
		double bestPrayer = prayer.applyAsDouble(current);

		for (int pass = 0; pass < 2; pass++)
		{
			boolean changed = false;
			for (Map.Entry<GearSlot, List<SetupBuilder.Pick>> entry : shortlists.entrySet())
			{
				for (SetupBuilder.Pick candidate : entry.getValue())
				{
					SetupBuilder.Pick previous = current.get(entry.getKey());
					if (previous != null && candidate != null
						&& previous.getItemId() == candidate.getItemId())
					{
						continue;
					}
					current.put(entry.getKey(), candidate);
					double now = seconds.applyAsDouble(current);
					double nowPrayer = prayer.applyAsDouble(current);
					double saved = best - now;

					boolean better;
					if (now <= 0)
					{
						better = false;
					}
					else if (saved > TIE_SECONDS)
					{
						better = true;
					}
					else if (saved < -TIE_SECONDS)
					{
						better = false;
					}
					else
					{
						better = nowPrayer > bestPrayer;
					}

					if (better)
					{
						best = now;
						bestPrayer = nowPrayer;
						changed = true;
					}
					else
					{
						current.put(entry.getKey(), previous);
					}
				}
			}
			if (!changed)
			{
				break;
			}
		}
		return current;
	}

	/** Total prayer bonus of a worn set. */
	private double prayerBonus(Map<GearSlot, SetupBuilder.Pick> picks)
	{
		double total = 0;
		for (SetupBuilder.Pick pick : picks.values())
		{
			if (pick == null)
			{
				continue;
			}
			ItemStats stats = itemManager.getItemStats(pick.getItemId());
			if (stats != null && stats.getEquipment() != null)
			{
				total += stats.getEquipment().getPrayer();
			}
		}
		return total;
	}

	/** Seconds below which a difference is noise, and prayer decides instead. */
	static final double TIE_SECONDS = 1.0;

	/** The heuristic's top few for a slot, as finalists for the DPS compare. */
	private List<SetupBuilder.Pick> shortlist(
		List<SetupBuilder.Pick> candidates, GearNeed style, boolean crystalBow)
	{
		if (candidates == null || candidates.isEmpty())
		{
			return java.util.Collections.emptyList();
		}
		List<SetupBuilder.Pick> sorted = new ArrayList<>(candidates);
		sorted.sort((a, b) -> Double.compare(
			score(b.getItemId(), style, crystalBow),
			score(a.getItemId(), style, crystalBow)));
		return sorted.subList(0, Math.min(SHORTLIST, sorted.size()));
	}

	private static final int SHORTLIST = 6;

	private void useSnapshot(Map<ItemSource, Map<Integer, Integer>> items, boolean includeGroupStorage)
	{
		if (cachedFor != items || cachedIncludeGroup != includeGroupStorage)
		{
			cachedFor = items;
			cachedIncludeGroup = includeGroupStorage;
			cachedScan = null;
			cachedResolve.clear();
			prePin.clear();
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
		// The pin is checked before the no-ItemManager fallback: a pinned
		// outfit is a decision that has already been made, and falling back
		// would silently discard it.
		useSnapshot(items, includeGroupStorage);
		Map<GearSlot, SetupBuilder.Pick> memo = cachedResolve.get(style);
		if (memo != null)
		{
			return new LinkedHashMap<>(memo);
		}

		if (itemManager == null)
		{
			return SetupBuilder.resolveLoadout(style, items, includeGroupStorage);
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

		picks = refineByDps(style, picks, items, includeGroupStorage);

		cachedResolve.put(style, new LinkedHashMap<>(picks));
		return picks;
	}

	/**
	 * Pins a style's resolved loadout, so every later lookup in this snapshot
	 * returns it.
	 *
	 * The switch advisor may trade a base slot to remove an expensive switch,
	 * which makes the base outfit something {@link #resolve} would not produce
	 * on its own. Everything that renders the plan resolves the style again to
	 * find out what is being worn, so without pinning, the item list and the
	 * times are computed from two different loadouts.
	 */
	public void pinResolved(
		GearNeed style,
		Map<GearSlot, SetupBuilder.Pick> picks,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage)
	{
		if (picks == null)
		{
			return;
		}
		// Keep what the style would have chosen for itself. The pin replaces
		// it, but callers still need to tell "this is the base style's own
		// gear" from "this was traded in for another style" - colouring the
		// worn set depends on the difference.
		prePin.put(style, new LinkedHashMap<>(resolve(style, items, includeGroupStorage)));
		// Bind the pin to its snapshot first. Otherwise the next resolve sees
		// an unfamiliar snapshot, clears the caches, and drops the pin - which
		// happens to work only because the advisor resolves before it pins.
		useSnapshot(items, includeGroupStorage);
		cachedResolve.put(style, new LinkedHashMap<>(picks));
	}

	/** What a style chose for itself, ignoring any pin. Never null. */
	public Map<GearSlot, SetupBuilder.Pick> ownPicks(
		GearNeed style,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage)
	{
		Map<GearSlot, SetupBuilder.Pick> own = prePin.get(style);
		return own != null ? own : resolve(style, items, includeGroupStorage);
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

			// A score comparison ("6 vs 0") tells you nothing about whether the
			// slot mattered. Seconds do: an item that wins its slot but saves
			// a fraction of a second is worth knowing about, because it looks
			// like a considered choice and is really just the only thing left.
			String detail = runnerUp == null
				? "only option you own"
				: String.format("beat %s", runnerUp.getOption().getName());

			SetupBuilder.Pick own = ownPicks(style, items, includeGroupStorage).get(slot);
			if (own != null && own.getItemId() != pick.getItemId())
			{
				// This slot was traded to another style to remove a switch, so
				// it did not win anything here. Saying it beat the runner-up
				// would be describing a comparison that never decided it.
				explanation.addGearChoice(style.getDisplayName() + " " + slot.getDisplayName()
					+ ": " + pick.getOption().getName()
					+ " — traded in for another style; " + own.getOption().getName()
					+ " is what this style would wear on its own");
				continue;
			}

			String worth = worthOf(style, slot, pick, chosen, runnerUp, items, includeGroupStorage);
			if (worth != null)
			{
				detail += worth;
			}
			else if (runnerUp != null)
			{
				detail += String.format(" (%.0f vs %.0f)",
					score(pick.getItemId(), style, crystalBow), runnerUpScore);
			}
			explanation.addGearChoice(style.getDisplayName() + " " + slot.getDisplayName()
				+ ": " + pick.getOption().getName() + " — " + detail);
		}
	}

	/**
	 * What a chosen slot is actually worth, in seconds saved over the next
	 * best thing you own and over leaving the slot empty — null when there is
	 * no DPS context to price it with.
	 */
	private String worthOf(
		GearNeed style,
		GearSlot slot,
		SetupBuilder.Pick pick,
		Map<GearSlot, SetupBuilder.Pick> chosen,
		SetupBuilder.Pick runnerUp,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage)
	{
		SetupBuilder.Pick weapon = chosen.get(GearSlot.WEAPON);
		if (dpsEstimator == null || dpsPlayer == null || weapon == null
			|| slot == GearSlot.WEAPON || slot == GearSlot.AMMO)
		{
			return null;
		}
		List<MonsterProfile> targets = dpsTargets(style);
		if (targets.isEmpty())
		{
			return null;
		}

		Map<GearSlot, SetupBuilder.Pick> probe = new LinkedHashMap<>(chosen);
		double with = totalSeconds(style, weapon, probe, targets, items, includeGroupStorage);
		if (with <= 0)
		{
			return null;
		}

		probe.put(slot, null);
		double empty = totalSeconds(style, weapon, probe, targets, items, includeGroupStorage);

		StringBuilder sb = new StringBuilder();
		if (runnerUp != null)
		{
			probe.put(slot, runnerUp);
			double next = totalSeconds(style, weapon, probe, targets, items, includeGroupStorage);
			sb.append(String.format(" by %.1fs", Math.max(0, next - with)));
		}
		if (empty > 0)
		{
			sb.append(String.format("; worth %.1fs over an empty slot", Math.max(0, empty - with)));
		}
		return sb.length() == 0 ? null : sb.toString();
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
				// Salve amulets are not generic gear: they have no offensive
				// stats, and their whole value is the situational bonus
				// against the undead. Letting them compete for the neck slot
				// here could win them the BASE pick against an undead-heavy
				// target pool — which silences the per-room extra-switch path
				// that is their one honest route into the plan, and wears a
				// statless neck in every room that is not undead.
				if (isSalve(id))
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

	/** True for any salve amulet variant — handled as a room extra, never as base gear. */
	private static boolean isSalve(int id)
	{
		for (int salve : RoomTimeEstimator.SALVE_IDS)
		{
			if (id == salve)
			{
				return true;
			}
		}
		return false;
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

		// Prayer bonus is worth something in every slot and costs nothing, but
		// it is not damage, so it only ever breaks a tie. Without this the
		// ammo slot is decided arbitrarily: nothing you can put there has
		// melee stats, so with a melee base outfit every candidate scores zero
		// and whichever the bank scan reached first wins - which is how broad
		// arrows beat a Rada's blessing next to a bow that fires no ammo.
		return value + eq.getPrayer() * TIEBREAK_WEIGHT;
	}

	/** Small enough that prayer never outranks a real offensive difference. */
	private static final double TIEBREAK_WEIGHT = 0.01;

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
