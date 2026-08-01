package com.coxgearplanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/**
 * Estimates expected kill time per selected room by evaluating every owned
 * weapon (with the best owned armour for its style) against the room's
 * monster using the standard DPS formulas, then keeping the fastest.
 *
 * Expected time-to-kill from DPS is a proxy for real room time — movement,
 * mechanics and spec weapons aren't modelled — but it ranks gear correctly,
 * which is what the planner needs.
 */
public class RoomTimeEstimator
{
	// Weapon ids with special handling
	private static final Set<Integer> SCYTHES = new HashSet<>(Arrays.asList(22325, 25736, 25739));
	private static final int FANG = 26219;
	private static final int TBOW = 20997;
	private static final int DHCB = 21012;
	private static final int BLOWPIPE = 12926;
	private static final int SHADOW = 27275;
	private static final Set<Integer> SANG = new HashSet<>(Arrays.asList(22323, 25731));
	private static final int TRIDENT_SWAMP = 12899;
	static final int EYE_OF_AYAK = 31113;
	/** 20% accuracy and damage vs draconic targets — including the Great Olm. */
	static final int DRAGON_HUNTER_LANCE = 22978;
	static final int INQUISITOR_HELM = 24419;
	static final int INQUISITOR_HAUBERK = 24420;
	static final int INQUISITOR_SKIRT = 24421;
	static final int TOME_OF_FIRE = 20714;
	private static final int TRIDENT_SEAS = 11905;
	private static final int HARMONISED = 24423;

	// Which ranged weapons consume which ammo class
	private static final Set<Integer> ARROW_WEAPONS = new HashSet<>(Arrays.asList(20997, 12788));
	private static final Set<Integer> BOLT_WEAPONS = new HashSet<>(Arrays.asList(26374, 21012, 11785, 21902, 9185));
	private static final Set<Integer> ARROW_IDS = new HashSet<>(Arrays.asList(11212, 21326, 892, 890));
	private static final Set<Integer> BOLT_IDS = new HashSet<>(Arrays.asList(21946, 21944, 9243, 9242));

	// Bow of faerdhinen + crystal armour set effect (per piece: acc%, dmg%)
	/**
	 * Bows that crystal armour buffs. The bonus applies to the crystal bow as
	 * well as the bow of faerdhinen, and each piece contributes independently
	 * — there is no full-set requirement, so a partial set is a partial bonus.
	 */
	private static final Set<Integer> BOFA = new HashSet<>(Arrays.asList(
		25865, 25867, 25884, 25886, 25888, 25890, 25892, 25894, 25896, // bofa + recolours
		23983)); // crystal bow
	private static final Set<Integer> CRYSTAL_HELM = new HashSet<>(Arrays.asList(
		23971, 27705, 27717, 27729, 27741, 27753, 27765, 27777));
	private static final Set<Integer> CRYSTAL_BODY = new HashSet<>(Arrays.asList(
		23975, 27697, 27709, 27721, 27733, 27745, 27757, 27769));
	private static final Set<Integer> CRYSTAL_LEGS = new HashSet<>(Arrays.asList(
		23979, 27701, 27713, 27725, 27737, 27749, 27761, 27773));

	// Salve amulet vs undead. The plain and enchanted versions boost melee
	// only; the imbued versions boost all three styles.
	static final int SALVE = 4081;
	static final int SALVE_E = 10588;
	static final int SALVE_I = 12017;
	static final int SALVE_EI = 12018;
	/** Best-first: (ei)/(e) give 20%, (i)/plain give 16.67% (magic 15% on the (i)). */
	static final int[] SALVE_IDS = {SALVE_EI, SALVE_I, SALVE_E, SALVE};

	/**
	 * Demonbane weapons and their own accuracy/damage bonus vs demons. The
	 * monster's demonbane effectiveness scales this — the ice demon is 115%,
	 * so Arclight's 70% lands at 80.5%.
	 */
	private static final Map<Integer, Double> DEMONBANE = new java.util.HashMap<>();

	static
	{
		DEMONBANE.put(29589, 0.70); // Emberlight
		DEMONBANE.put(19675, 0.70); // Arclight
		DEMONBANE.put(6746, 0.60);  // Darklight
		DEMONBANE.put(2402, 0.60);  // Silverlight
		DEMONBANE.put(29591, 0.30); // Scorching bow
		DEMONBANE.put(33243, 0.10); // Infernal tecpatl
	}

	/** Combined demonbane multiplier for a weapon against a target, or 1.0. */
	static double demonbaneMultiplier(int weaponId, MonsterProfile monster)
	{
		if (!monster.isDemon())
		{
			return 1.0;
		}
		Double bonus = DEMONBANE.get(weaponId);
		return bonus == null ? 1.0 : 1.0 + bonus * monster.getDemonbaneEffectiveness();
	}

	/**
	 * Base attack reach in tiles, for targets that can only be hit from a
	 * distance. RuneLite's item stats do not expose reach, so this is a table.
	 * Longrange adds 2 tiles but gives up the rapid speed bonus, which is
	 * exactly why a 10-tile bow is worth carrying for the abyssal portal.
	 */
	private static final Map<Integer, Integer> WEAPON_REACH = new java.util.HashMap<>();
	private static final int LONGRANGE_BONUS = 2;
	private static final int DEFAULT_REACH = 7;

	static
	{
		// 10 tiles — reach the portal on rapid, no longrange needed
		WEAPON_REACH.put(20997, 10); // twisted bow
		WEAPON_REACH.put(25865, 10); // bow of faerdhinen
		WEAPON_REACH.put(25867, 10);
		WEAPON_REACH.put(23983, 10); // crystal bow
		WEAPON_REACH.put(29591, 10); // scorching bow
		// 8 tiles — also reach on rapid
		WEAPON_REACH.put(11785, 8);  // armadyl crossbow
		WEAPON_REACH.put(26374, 8);  // zaryte crossbow
		WEAPON_REACH.put(27275, 8);  // tumeken's shadow
		// 5 tiles — cannot reach the portal even on longrange
		WEAPON_REACH.put(12926, 5);  // toxic blowpipe
	}

	/** Base reach of a weapon in tiles, defaulting to the common 7. */
	static int weaponReach(int weaponId)
	{
		Integer reach = WEAPON_REACH.get(weaponId);
		return reach == null ? DEFAULT_REACH : reach;
	}

	/** True when this weapon can only reach the target by using longrange. */
	static boolean needsLongrange(int weaponId, MonsterProfile monster)
	{
		int required = monster.getMinReach();
		return required > 0 && weaponReach(weaponId) < required;
	}

	/** True when the weapon cannot reach the target even on longrange. */
	static boolean cannotReach(int weaponId, MonsterProfile monster)
	{
		int required = monster.getMinReach();
		return required > 0 && weaponReach(weaponId) + LONGRANGE_BONUS < required;
	}

	/** Pickaxe item id -> its Mining level requirement, for the Guardians' damage formula. */
	private static final Map<Integer, Integer> PICKAXE_TIER = new java.util.HashMap<>();

	static
	{
		PICKAXE_TIER.put(1265, 1);   // bronze
		PICKAXE_TIER.put(1267, 1);   // iron
		PICKAXE_TIER.put(1269, 6);   // steel
		PICKAXE_TIER.put(12297, 11); // black
		PICKAXE_TIER.put(1273, 21);  // mithril
		PICKAXE_TIER.put(1271, 31);  // adamant
		PICKAXE_TIER.put(1275, 41);  // rune
		PICKAXE_TIER.put(11920, 61); // dragon
		PICKAXE_TIER.put(23680, 61); // crystal — the formula caps at the dragon tier
	}

	/**
	 * Guardians take damage only from pickaxes, and the rolled damage is then
	 * scaled by (50 + Mining + pickaxe tier) / 150. The tier caps at 61, which
	 * is why a crystal pickaxe deals the same damage as a dragon one.
	 */
	static double guardianDamageMultiplier(int weaponId, int miningLevel)
	{
		Integer tier = PICKAXE_TIER.get(weaponId);
		if (tier == null)
		{
			return 0; // anything that is not a pickaxe deals nothing at all
		}
		return (50 + miningLevel + Math.min(61, tier)) / 150.0;
	}

	/**
	 * Guardian hitpoints, each: 151 x (1 + floor(T/2)) + floor(mining) x T.
	 * Their HP already accounts for team size, so the generic party scaling
	 * must not be applied on top.
	 */
	static double guardianHp(int partySize, int miningLevel)
	{
		int team = Math.max(1, partySize);
		return 151 * (1 + team / 2) + (double) miningLevel * team;
	}

	// Ranged strength of dragon darts, which the blowpipe's own stats omit
	private static final int BLOWPIPE_DART_RSTR = 20;

	public static class RoomTime
	{
		private final CoxRoom room;
		private final String detail;
		private final double seconds;
		private final boolean feasible;
		private final GearNeed style;
		private final SetupBuilder.Pick weapon;
		/** Room-specific item the general loadout doesn't cover (salve amulet). */
		SetupBuilder.Pick extraSwitch;
		/** The target this entry is for, and its party-scaled HP pool. */
		MonsterProfile monster;
		double totalHp;
		/** Sub-target name for multi-part rooms (Olm's claws and head). */
		String partName;

		RoomTime(CoxRoom room, String detail, double seconds, boolean feasible,
			GearNeed style, SetupBuilder.Pick weapon)
		{
			this.room = room;
			this.detail = detail;
			this.seconds = seconds;
			this.feasible = feasible;
			this.style = style;
			this.weapon = weapon;
		}

		public CoxRoom getRoom()
		{
			return room;
		}

		public String getDetail()
		{
			return detail;
		}

		public double getSeconds()
		{
			return seconds;
		}

		public boolean isFeasible()
		{
			return feasible;
		}

		/** Combat style of the winning weapon; null when infeasible. */
		public GearNeed getStyle()
		{
			return style;
		}

		/** The winning weapon pick; null when infeasible. */
		public SetupBuilder.Pick getWeapon()
		{
			return weapon;
		}

		/** Room-specific extra item to bring (salve amulet), or null. */
		public SetupBuilder.Pick getExtraSwitch()
		{
			return extraSwitch;
		}

		/** The monster this entry was computed against; null when infeasible. */
		public MonsterProfile getMonster()
		{
			return monster;
		}

		/** Party-scaled HP this entry has to chew through. */
		public double getTotalHp()
		{
			return totalHp;
		}

		/** Sub-target name for Olm's parts, or null for ordinary rooms. */
		public String getPartName()
		{
			return partName;
		}

		/** Room name, plus the Olm part when this is a sub-target. */
		public String getDisplayName()
		{
			return partName == null
				? room.getDisplayName()
				: room.getDisplayName() + " — " + partName;
		}
	}

	private final ItemManager itemManager;
	private final GearResolver resolver;
	/** Restrict Olm's melee and magic weapons to 4-tick options. */
	private boolean olmFourTick;

	public RoomTimeEstimator(ItemManager itemManager)
	{
		this.itemManager = itemManager;
		this.resolver = new GearResolver(itemManager);
	}

	public GearResolver getResolver()
	{
		return resolver;
	}

	/**
	 * When set, Olm's melee and magic weapons are restricted to 4-tick
	 * options so both styles share one attack rhythm — much easier to learn
	 * than mixing a 5-tick scythe or shadow with a 4-tick swap. Ranged is
	 * left alone; it is on its own rhythm regardless.
	 */
	public void setOlmFourTick(boolean olmFourTick)
	{
		this.olmFourTick = olmFourTick;
	}

	/**
	 * CoX HP scaling. The wiki publishes no formula; the documented community
	 * figure is that each additional maxed party member adds the monster's
	 * base HP again, i.e. the multiplier is the party size (Tekton 300 solo,
	 * 600 duo). Listed stats are the solo maxed-player baseline.
	 */
	public static double hpMultiplier(int partySize)
	{
		return Math.max(1, partySize);
	}

	public List<RoomTime> estimate(
		Set<CoxRoom> rooms,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage,
		PlayerSnapshot player,
		int partySize,
		boolean elitePrayers)
	{
		return estimate(rooms, items, includeGroupStorage, player, partySize, elitePrayers, null);
	}

	public List<RoomTime> estimate(
		Set<CoxRoom> rooms,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage,
		PlayerSnapshot player,
		int partySize,
		boolean elitePrayers,
		PlanExplanation explanation)
	{
		double hpMult = hpMultiplier(partySize);

		List<RoomTime> results = new ArrayList<>();
		for (CoxRoom room : CoxRoom.values())
		{
			if (!rooms.contains(room))
			{
				continue;
			}
			for (RoomMonsters.Encounter encounter : RoomMonsters.getAll(room))
			{
				results.add(estimateTarget(room, encounter, items, includeGroupStorage,
					player, partySize, hpMult, elitePrayers, explanation));
			}
		}
		return results;
	}

	/** One target: an ordinary room's monster, or one of Olm's three parts. */
	private RoomTime estimateTarget(
		CoxRoom room,
		RoomMonsters.Encounter encounter,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage,
		PlayerSnapshot player,
		int partySize,
		double hpMult,
		boolean elitePrayers,
		PlanExplanation explanation)
	{
		{
			MonsterProfile monster = encounter.getProfile();
			boolean multiPart = RoomMonsters.getAll(room).size() > 1;
			double bestDps = 0;
			GearNeed bestStyle = null;
			SetupBuilder.Pick bestWeapon = null;
			SetupBuilder.Pick bestSalve = null;
			// Every candidate and its dps, so the debug panel can show the full
			// ranking rather than just the winner — "why wasn't X considered?"
			// is only answerable if the panel distinguishes "lost" from
			// "never evaluated".
			List<String> ranking = new ArrayList<>();

			// The globally-best amulet is anguish/torture/occult, but against
			// undead a salve amulet usually beats all of them. Its bonus isn't
			// in the item's stats, so it has to be tried explicitly.
			SetupBuilder.Pick salve = monster.isUndead()
				? findSalve(items, includeGroupStorage)
				: null;

			// Prefer the styles the fight actually favours — safespotting, a
			// target melee can't reach, a mechanic that demands one style.
			// DPS can't see any of that. Fall back to whatever is usable only
			// when you own no weapon for the preferred styles.
			Set<GearNeed> styles = monster.getUsableStyles();
			if (!monster.getPreferredStyles().isEmpty())
			{
				Set<GearNeed> preferred = new java.util.LinkedHashSet<>();
				for (GearNeed style : monster.getPreferredStyles())
				{
					if (styles.contains(style)
						&& !weaponCandidates(style, items, includeGroupStorage, monster,
							room == CoxRoom.OLM).isEmpty())
					{
						preferred.add(style);
					}
				}
				if (!preferred.isEmpty())
				{
					styles = preferred;
				}
			}

			for (GearNeed style : styles)
			{
				Map<GearSlot, SetupBuilder.Pick> picks = resolver.resolve(style, items, includeGroupStorage);

				Map<GearSlot, SetupBuilder.Pick> salveOverride = Collections.emptyMap();
				if (salve != null)
				{
					SetupBuilder.Pick neck = picks.get(GearSlot.NECK);
					if (neck == null || neck.getItemId() != salve.getItemId())
					{
						salveOverride = Collections.singletonMap(GearSlot.NECK, salve);
					}
				}

				// Complete sets (void, obsidian) only pay out when every piece
				// is worn, so they have to be tried as whole alternatives —
				// the per-slot picker would never assemble one on its own.
				List<Map<GearSlot, SetupBuilder.Pick>> setOverrides =
					setOverrides(style, items, includeGroupStorage);

				for (SetupBuilder.Pick weapon : weaponCandidates(style, items, includeGroupStorage,
					monster, room == CoxRoom.OLM))
				{
					double dps = loadoutDps(style, weapon, picks, Collections.emptyMap(),
						items, includeGroupStorage, player, monster, elitePrayers);
					SetupBuilder.Pick usedSalve = null;

					for (Map<GearSlot, SetupBuilder.Pick> setOverride : setOverrides)
					{
						double setDps = loadoutDps(style, weapon, picks, setOverride,
							items, includeGroupStorage, player, monster, elitePrayers);
						if (setDps > dps)
						{
							dps = setDps;
						}
					}

					if (!salveOverride.isEmpty())
					{
						double salveDps = loadoutDps(style, weapon, picks, salveOverride,
							items, includeGroupStorage, player, monster, elitePrayers);
						if (salveDps > dps)
						{
							dps = salveDps;
							usedSalve = salve;
						}
					}

					if (explanation != null)
					{
						ranking.add(String.format("%.2f|%s (%s)", dps,
							weapon.getOption().getName(), style.getDisplayName().toLowerCase()));
					}

					if (dps > bestDps)
					{
						bestDps = dps;
						bestStyle = style;
						bestWeapon = weapon;
						bestSalve = usedSalve;
					}
				}
			}

			String part = multiPart ? monster.getName() : null;

			if (bestDps <= 0)
			{
				RoomTime infeasible = new RoomTime(room, "no usable weapon owned", 0, false, null, null);
				infeasible.partName = part;
				return infeasible;
			}

			// Each claw is fought once per phase except the final head phase —
			// three times in a standard raid. The head is killed once.
			double phases = room == CoxRoom.OLM && monster.getName().contains("claw")
				? RoomMonsters.olmClawPhases(partySize)
				: 1;

			// The Guardians carry their own team-size term, so the generic
			// party scaling must not be applied on top of it.
			double totalHp = monster.getRequiredWeapon() == GearNeed.PICKAXE
				? guardianHp(partySize, player.getMining()) * encounter.getCount()
				: monster.getHp() * hpMult * encounter.getCount() * phases;

			// CoX scales monster HP with party size, and the party splits the
			// damage — so what matters is YOUR share, not the whole pool.
			// Without this the estimate is roughly party-size times too long.
			double yourShare = totalHp / Math.max(1, partySize);

			String label = bestWeapon.getOption().getName()
				+ " (" + bestStyle.getDisplayName().toLowerCase();
			// For melee, name the attack style too — "use a fang" is only half
			// the instruction if the target wants stab specifically.
			String meleeStyle = bestMeleeStyle(bestStyle, bestWeapon.getItemId(),
				resolver.resolve(bestStyle, items, includeGroupStorage),
				items, includeGroupStorage, player, monster, elitePrayers);
			if (meleeStyle != null)
			{
				label += ", " + meleeStyle + " style";
			}
			label += ")";
			if (bestSalve != null)
			{
				label += " + " + bestSalve.getOption().getName();
			}
			if (phases > 1)
			{
				label += String.format(" ×%.0f phases", phases);
			}
			if (monster.getStyleNote() != null)
			{
				label += " [" + monster.getStyleNote() + "]";
			}
			if (room == CoxRoom.OLM && olmFourTick && bestStyle != GearNeed.RANGED
				&& effectiveSpeedTicks(bestStyle, bestWeapon.getItemId()) == 4)
			{
				label += " [4-tick]";
			}

			RoomTime roomTime = new RoomTime(room, label, yourShare / bestDps, true, bestStyle, bestWeapon);
			roomTime.extraSwitch = bestSalve;
			roomTime.monster = monster;
			roomTime.totalHp = yourShare;
			roomTime.partName = part;

			if (explanation != null)
			{
				explanation.addWeaponChoice(String.format(
					"%s (%s, your share %.0f of %.0f hp) — %d weapons evaluated:",
					roomTime.getDisplayName(), monster.getName(), yourShare, totalHp, ranking.size()));

				// Highest dps first; the split key is the numeric dps prefix
				ranking.sort((a, b) -> Double.compare(
					Double.parseDouble(b.substring(0, b.indexOf('|'))),
					Double.parseDouble(a.substring(0, a.indexOf('|')))));
				int shown = 0;
				for (String entry : ranking)
				{
					int bar = entry.indexOf('|');
					explanation.addWeaponChoice(String.format("    %s%s — %s dps",
						shown == 0 ? "> " : "  ", entry.substring(bar + 1), entry.substring(0, bar)));
					if (++shown >= 8)
					{
						explanation.addWeaponChoice("    … " + (ranking.size() - shown) + " more");
						break;
					}
				}
			}
			return roomTime;
		}
	}

	/**
	 * DPS of a specific weapon plus a style's armour picks against a monster.
	 * Slots present in {@code overrides} replace the style's pick for that
	 * slot (a null value means the slot is left empty) — this is how the
	 * switch advisor prices "wear the other style's item instead".
	 */
	public double loadoutDps(
		GearNeed style,
		SetupBuilder.Pick weapon,
		Map<GearSlot, SetupBuilder.Pick> picks,
		Map<GearSlot, SetupBuilder.Pick> overrides,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage,
		PlayerSnapshot player,
		MonsterProfile monster,
		boolean elitePrayers)
	{
		boolean twoHanded = weapon.getOption().isTwoHanded();
		EquipmentTotals totals = new EquipmentTotals();
		Set<Integer> equippedIds = new HashSet<>();

		for (GearSlot slot : GearSlot.values())
		{
			if (slot == GearSlot.WEAPON || slot == GearSlot.AMMO)
			{
				continue;
			}
			if (slot == GearSlot.SHIELD && twoHanded)
			{
				continue;
			}
			SetupBuilder.Pick pick = overrides.containsKey(slot) ? overrides.get(slot) : picks.get(slot);
			if (pick != null)
			{
				totals.add(itemManager.getItemStats(pick.getItemId()));
				addCrystalSetBonus(totals, pick.getItemId());
				addSalveBonus(totals, pick.getItemId());
				addPieceEffects(totals, pick.getItemId());
				equippedIds.add(pick.getItemId());
			}
		}

		ItemStats weaponStats = itemManager.getItemStats(weapon.getItemId());
		totals.add(weaponStats);
		if (weaponStats != null && weaponStats.getEquipment() != null)
		{
			totals.speedTicks = Math.max(1, weaponStats.getEquipment().getAspeed());
		}
		if (style == GearNeed.RANGED)
		{
			addAmmo(totals, weapon.getItemId(), items, includeGroupStorage);
		}

		// Complete-set effects depend on the whole worn combination
		double[] set = GearSetBonus.multipliers(equippedIds, style, weapon.getItemId());
		totals.setAccMult = set[0];
		totals.setDmgMult = set[1];

		return dpsFor(style, weapon.getItemId(), totals, player, monster, elitePrayers);
	}

	private void addAmmo(EquipmentTotals totals, int weaponId,
		Map<ItemSource, Map<Integer, Integer>> items, boolean includeGroupStorage)
	{
		if (weaponId == BLOWPIPE)
		{
			totals.rangedStr += BLOWPIPE_DART_RSTR;
			return;
		}

		SetupBuilder.Pick ammo = findAmmo(weaponId, items, includeGroupStorage);
		if (ammo != null)
		{
			totals.add(itemManager.getItemStats(ammo.getItemId()));
		}
	}

	/**
	 * Weapons to try for a style: the curated tier list (which carries the
	 * special-case knowledge) plus any other owned weapon with a relevant
	 * offensive bonus, so a weapon missing from the list still competes.
	 */
	private List<SetupBuilder.Pick> weaponCandidates(
		GearNeed style,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage)
	{
		return weaponCandidates(style, items, includeGroupStorage, null, false);
	}

	/**
	 * @param monster when it restricts damage to one weapon class (the
	 * Guardians take damage only from pickaxes), the candidate list is that
	 * class alone — otherwise the planner would happily suggest a bludgeon
	 * that cannot hurt them.
	 */
	private List<SetupBuilder.Pick> weaponCandidates(
		GearNeed style,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage,
		MonsterProfile monster,
		boolean atOlm)
	{
		if (monster != null && monster.getRequiredWeapon() != null)
		{
			List<SetupBuilder.Pick> only = new ArrayList<>();
			for (ItemOption option : GearDatabase.utility(monster.getRequiredWeapon()))
			{
				SetupBuilder.Pick pick = SetupBuilder.findOwned(option, items, includeGroupStorage);
				if (pick != null)
				{
					only.add(pick);
				}
			}
			return only;
		}

		List<SetupBuilder.Pick> candidates = new ArrayList<>();
		Set<Integer> seen = new HashSet<>();

		for (ItemOption option : GearDatabase.loadout(style).get(GearSlot.WEAPON))
		{
			SetupBuilder.Pick pick = SetupBuilder.findOwned(option, items, includeGroupStorage);
			if (pick != null && seen.add(pick.getItemId()))
			{
				candidates.add(pick);
			}
		}

		for (SetupBuilder.Pick pick : resolver.scan(items, includeGroupStorage)
			.getOrDefault(GearSlot.WEAPON, Collections.emptyList()))
		{
			if (!seen.add(pick.getItemId()))
			{
				continue;
			}
			ItemStats stats = itemManager.getItemStats(pick.getItemId());
			if (stats == null || stats.getEquipment() == null)
			{
				continue;
			}
			ItemEquipmentStats eq = stats.getEquipment();
			boolean usable;
			switch (style)
			{
				case MELEE:
					usable = Math.max(eq.getAstab(), Math.max(eq.getAslash(), eq.getAcrush())) > 0;
					break;
				case RANGED:
					usable = eq.getArange() > 0;
					break;
				case MAGIC:
					usable = eq.getAmagic() > 0;
					break;
				default:
					usable = false;
			}
			if (usable)
			{
				candidates.add(pick);
			}
		}

		if (monster != null && monster.getMinReach() > 0)
		{
			List<SetupBuilder.Pick> reaching = new ArrayList<>();
			for (SetupBuilder.Pick pick : candidates)
			{
				if (!cannotReach(pick.getItemId(), monster))
				{
					reaching.add(pick);
				}
			}
			candidates = reaching;
		}

		if (atOlm && olmFourTick && (style == GearNeed.MELEE || style == GearNeed.MAGIC))
		{
			List<SetupBuilder.Pick> fourTick = new ArrayList<>();
			for (SetupBuilder.Pick pick : candidates)
			{
				if (effectiveSpeedTicks(style, pick.getItemId()) == 4)
				{
					fourTick.add(pick);
				}
			}
			// Only enforce it if you actually own something that qualifies,
			// otherwise you would get no plan at all for that style.
			if (!fourTick.isEmpty())
			{
				return fourTick;
			}
		}
		return candidates;
	}

	/**
	 * Per-piece effects that aren't expressed in an item's equipment stats.
	 *
	 * Inquisitor's boosts crush accuracy and damage only: great helm 0.5%,
	 * hauberk 1%, plateskirt 1% (2.5% for the full set). The tome of fire adds
	 * 10% damage to standard-spellbook fire spells against NPCs — the 50%
	 * figure quoted for the tome is player-versus-player only.
	 */
	static void addPieceEffects(EquipmentTotals totals, int itemId)
	{
		switch (itemId)
		{
			case INQUISITOR_HELM:
				totals.inquisitorCrush += 0.005;
				break;
			case INQUISITOR_HAUBERK:
			case INQUISITOR_SKIRT:
				totals.inquisitorCrush += 0.01;
				break;
			case TOME_OF_FIRE:
				totals.fireSpellMult = 1.10;
				break;
			default:
				break;
		}
	}

	/** Crystal armour boosts the crystal bow / bofa: helm 5%/2.5%, body 15%/7.5%, legs 10%/5%. */
	static void addCrystalSetBonus(EquipmentTotals totals, int itemId)
	{
		if (CRYSTAL_HELM.contains(itemId))
		{
			totals.crystalAcc += 0.05;
			totals.crystalDmg += 0.025;
		}
		else if (CRYSTAL_BODY.contains(itemId))
		{
			totals.crystalAcc += 0.15;
			totals.crystalDmg += 0.075;
		}
		else if (CRYSTAL_LEGS.contains(itemId))
		{
			totals.crystalAcc += 0.10;
			totals.crystalDmg += 0.05;
		}
	}

	/**
	 * Salve amulet: +15%/+20% accuracy and damage against undead. Only the
	 * imbued variants extend the bonus to ranged and magic. Applied by the
	 * per-style DPS methods, and only when the monster is undead.
	 */
	static void addSalveBonus(EquipmentTotals totals, int itemId)
	{
		switch (itemId)
		{
			case SALVE_EI:
				totals.salveMeleeMult = 1.20;
				totals.salveRangedMagicMult = 1.20;
				totals.salveMagicMult = 1.20;
				break;
			case SALVE_I:
				// Melee and ranged 16.67%, but magic only 15%
				totals.salveMeleeMult = 1.1667;
				totals.salveRangedMagicMult = 1.1667;
				totals.salveMagicMult = 1.15;
				break;
			case SALVE_E:
				totals.salveMeleeMult = 1.20;
				break;
			case SALVE:
				totals.salveMeleeMult = 1.1667;
				break;
			default:
				break;
		}
	}

	/**
	 * Reports which of a style's known weapons were found in your item pools
	 * and which were not — the direct answer to "why wasn't X considered?",
	 * which the per-room ranking alone cannot distinguish from "X lost".
	 */
	public void describeWeaponPool(
		GearNeed style,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage,
		PlanExplanation explanation)
	{
		if (explanation == null)
		{
			return;
		}

		StringBuilder owned = new StringBuilder();
		StringBuilder missing = new StringBuilder();
		for (ItemOption option : GearDatabase.loadout(style).get(GearSlot.WEAPON))
		{
			SetupBuilder.Pick pick = SetupBuilder.findOwned(option, items, includeGroupStorage);
			StringBuilder target = pick != null ? owned : missing;
			if (target.length() > 0)
			{
				target.append(", ");
			}
			target.append(option.getName());
			if (pick != null)
			{
				target.append(" [").append(pick.getSource().getDisplayName()).append("]");
			}
		}

		explanation.addWeaponPool(style.getDisplayName() + " OWNED: "
			+ (owned.length() == 0 ? "none" : owned));
		explanation.addWeaponPool(style.getDisplayName() + " not found: "
			+ (missing.length() == 0 ? "none" : missing));

		int scanned = resolver.scan(items, includeGroupStorage)
			.getOrDefault(GearSlot.WEAPON, Collections.emptyList()).size();
		explanation.addWeaponPool(style.getDisplayName()
			+ " — " + scanned + " total equipable weapons seen across all your storage");
	}

	/** Each complete set you own, expressed as slot overrides to evaluate. */
	private List<Map<GearSlot, SetupBuilder.Pick>> setOverrides(
		GearNeed style,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage)
	{
		List<Map<GearSlot, SetupBuilder.Pick>> overrides = new ArrayList<>();
		for (GearSetBonus.SetOption set : GearSetBonus.ownedSets(style, items, includeGroupStorage))
		{
			Map<GearSlot, SetupBuilder.Pick> override = new java.util.HashMap<>();
			boolean complete = true;
			for (Map.Entry<GearSlot, Integer> piece : set.getPieces().entrySet())
			{
				SetupBuilder.Pick pick = SetupBuilder.findOwned(
					ItemOption.of(set.getName(), piece.getValue()), items, includeGroupStorage);
				if (pick == null)
				{
					complete = false;
					break;
				}
				override.put(piece.getKey(), pick);
			}
			if (complete)
			{
				overrides.add(override);
			}
		}
		return overrides;
	}

	/**
	 * Cast speed of a magic weapon in ticks. Powered staves have their own
	 * built-in speeds, so this cannot be read from the item's attack speed —
	 * it is shared by the DPS maths and the Olm 4-tick filter so the two
	 * cannot drift apart.
	 */
	static int magicSpeedTicks(int weaponId)
	{
		if (weaponId == SHADOW)
		{
			return 5;
		}
		if (weaponId == EYE_OF_AYAK)
		{
			return 3;
		}
		if (SANG.contains(weaponId) || weaponId == TRIDENT_SWAMP
			|| weaponId == TRIDENT_SEAS || weaponId == HARMONISED)
		{
			return 4;
		}
		return 5; // ordinary staff casting from the standard spellbook
	}

	/** Attack rhythm a style would actually use, in ticks. */
	int effectiveSpeedTicks(GearNeed style, int weaponId)
	{
		if (style == GearNeed.MAGIC)
		{
			return magicSpeedTicks(weaponId);
		}
		ItemStats stats = itemManager.getItemStats(weaponId);
		int base = stats != null && stats.getEquipment() != null
			? Math.max(1, stats.getEquipment().getAspeed())
			: 4;
		// Ranged is assumed to be on rapid, which is one tick faster
		return style == GearNeed.RANGED ? Math.max(1, base - 1) : base;
	}

	/** Best owned salve amulet, or null — used as a neck swap for undead rooms. */
	static SetupBuilder.Pick findSalve(
		Map<ItemSource, Map<Integer, Integer>> items, boolean includeGroupStorage)
	{
		for (int id : SALVE_IDS)
		{
			String name = id == SALVE_EI ? "Salve amulet (ei)"
				: id == SALVE_I ? "Salve amulet (i)"
				: id == SALVE_E ? "Salve amulet (e)"
				: "Salve amulet";
			SetupBuilder.Pick pick = SetupBuilder.findOwned(
				ItemOption.of(name, id), items, includeGroupStorage);
			if (pick != null)
			{
				return pick;
			}
		}
		return null;
	}

	/** Whether the weapon consumes arrow/bolt ammo from the quiver slot. */
	public static boolean needsAmmo(int weaponId)
	{
		return ARROW_WEAPONS.contains(weaponId) || BOLT_WEAPONS.contains(weaponId);
	}

	/**
	 * Best owned ammo for the weapon's ammo class, or null if the weapon
	 * takes no ammo (bofa, crystal weapons, blowpipe) or none is owned.
	 */
	public static SetupBuilder.Pick findAmmo(int weaponId,
		Map<ItemSource, Map<Integer, Integer>> items, boolean includeGroupStorage)
	{
		Set<Integer> compatible = ARROW_WEAPONS.contains(weaponId) ? ARROW_IDS
			: BOLT_WEAPONS.contains(weaponId) ? BOLT_IDS
			: null;
		if (compatible == null)
		{
			return null;
		}

		for (ItemOption ammoOption : GearDatabase.loadout(GearNeed.RANGED).get(GearSlot.AMMO))
		{
			if (!compatible.contains(ammoOption.getItemIds()[0]))
			{
				continue;
			}
			SetupBuilder.Pick ammo = SetupBuilder.findOwned(ammoOption, items, includeGroupStorage);
			if (ammo != null)
			{
				return ammo;
			}
		}
		return null;
	}

	private static double dpsFor(GearNeed style, int weaponId, EquipmentTotals eq,
		PlayerSnapshot player, MonsterProfile m, boolean elitePrayers)
	{
		switch (style)
		{
			case MELEE:
				return meleeDps(weaponId, eq, player, m, elitePrayers);
			case RANGED:
				return rangedDps(weaponId, eq, player, m, elitePrayers);
			case MAGIC:
				return magicDps(weaponId, eq, player, m, elitePrayers);
			default:
				return 0;
		}
	}

	private static double meleeDps(int weaponId, EquipmentTotals eq,
		PlayerSnapshot p, MonsterProfile m, boolean elite)
	{
		// Piety; aggressive style (+3 strength)
		int effStr = CombatFormulas.effectiveLevel(p.getStrength(), elite ? 1.23 : 1.0, 3);
		int effAtk = CombatFormulas.effectiveLevel(p.getAttack(), elite ? 1.20 : 1.0, 0);
		int maxHit = CombatFormulas.maxHit(effStr, eq.meleeStr);

		int[][] styles = {
			{eq.stabAtk, m.getDStab()},
			{eq.slashAtk, m.getDSlash()},
			{eq.crushAtk, m.getDCrush()},
		};

		// Salve amulet multiplies both accuracy and damage against undead
		double salve = m.isUndead() ? eq.salveMeleeMult : 1.0;
		// Dragon hunter lance: 20% accuracy and damage vs draconic targets
		double bane = weaponId == DRAGON_HUNTER_LANCE && m.isDraconic() ? 1.20 : 1.0;
		// Demonbane weapons are exempt from a demon's damage reduction; anything
		// else eats it.
		double demonbane = demonbaneMultiplier(weaponId, m);
		double reduction = demonbane > 1.0 ? 1.0 : m.getNonFireDamageMult();
		// The Guardians scale rolled damage by the pickaxe formula, and take
		// nothing at all from anything that is not a pickaxe.
		double pickaxe = m.getRequiredWeapon() == GearNeed.PICKAXE
			? guardianDamageMultiplier(weaponId, p.getMining())
			: 1.0;
		maxHit = (int) Math.floor(maxHit * salve * bane * eq.setDmgMult * demonbane * reduction * pickaxe);

		double best = 0;
		int bestStyleIndex = 0;
		for (int i = 0; i < styles.length; i++)
		{
			int[] s = styles[i];
			// Inquisitor's pieces boost the crush style only
			boolean crush = i == 2;
			double inq = crush ? 1 + eq.inquisitorCrush : 1.0;
			int styleMax = (int) Math.floor(maxHit * inq);

			double acc = CombatFormulas.accuracy(
				CombatFormulas.attackRoll(effAtk, s[0]) * salve * bane * inq * eq.setAccMult * demonbane,
				CombatFormulas.defenceRoll(m.getDefenceLevel(), s[1]));
			if (weaponId == FANG)
			{
				// Fang rolls accuracy twice
				acc = 1 - (1 - acc) * (1 - acc);
			}
			double avgMax = styleMax;
			if (SCYTHES.contains(weaponId) && m.isLarge())
			{
				// Scythe hits 100% + 50% + 25% on large targets
				avgMax *= 1.75;
			}
			double dps = CombatFormulas.dps(acc, avgMax, eq.speedTicks);
			if (dps > best)
			{
				best = dps;
				bestStyleIndex = i;
			}
		}
		lastMeleeStyle.set(MELEE_STYLE_NAMES[bestStyleIndex]);
		return best;
	}

	/**
	 * Which attack style the last melee calculation settled on. The crystal at
	 * Vasa has -5 stab defence against +180 slash and crush, so "use a fang"
	 * is only half the instruction — you have to be on the stab style too.
	 */
	private static final String[] MELEE_STYLE_NAMES = {"stab", "slash", "crush"};
	private static final ThreadLocal<String> lastMeleeStyle = new ThreadLocal<>();

	/** The melee attack style a weapon should use against a target. */
	String bestMeleeStyle(GearNeed style, int weaponId,
		Map<GearSlot, SetupBuilder.Pick> picks,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage,
		PlayerSnapshot player, MonsterProfile monster, boolean elitePrayers)
	{
		if (style != GearNeed.MELEE)
		{
			return null;
		}
		SetupBuilder.Pick weapon = SetupBuilder.findOwned(
			ItemOption.of("weapon", weaponId), items, includeGroupStorage);
		if (weapon == null)
		{
			return null;
		}
		lastMeleeStyle.remove();
		loadoutDps(style, weapon, picks, Collections.emptyMap(), items,
			includeGroupStorage, player, monster, elitePrayers);
		return lastMeleeStyle.get();
	}

	private static double rangedDps(int weaponId, EquipmentTotals eq,
		PlayerSnapshot p, MonsterProfile m, boolean elite)
	{
		// Rigour; rapid style (one tick faster, no level bonus)
		int effStr = CombatFormulas.effectiveLevel(p.getRanged(), elite ? 1.23 : 1.0, 0);
		int effAtk = CombatFormulas.effectiveLevel(p.getRanged(), elite ? 1.20 : 1.0, 0);
		int maxHit = CombatFormulas.maxHit(effStr, eq.rangedStr);

		double demonbane = demonbaneMultiplier(weaponId, m);
		double reduction = demonbane > 1.0 ? 1.0 : m.getNonFireDamageMult();
		double atkRoll = CombatFormulas.attackRoll(effAtk, eq.rangedAtk) * eq.setAccMult * demonbane;
		double defRoll = CombatFormulas.defenceRoll(m.getDefenceLevel(), m.getDRange());
		maxHit = (int) Math.floor(maxHit * eq.setDmgMult * demonbane * reduction);
		double avgMax = maxHit;

		if (m.isUndead() && eq.salveRangedMagicMult > 1.0)
		{
			atkRoll *= eq.salveRangedMagicMult;
			maxHit = (int) Math.floor(maxHit * eq.salveRangedMagicMult);
			avgMax = maxHit;
		}

		if (weaponId == TBOW)
		{
			atkRoll *= CombatFormulas.tbowAccuracyMult(m.getMagicLevel());
			avgMax = Math.floor(maxHit * CombatFormulas.tbowDamageMult(m.getMagicLevel()));
		}
		else if (weaponId == DHCB && m.isDraconic())
		{
			atkRoll *= 1.30;
			avgMax = Math.floor(maxHit * 1.25);
		}
		else if (BOFA.contains(weaponId) && eq.crystalAcc > 0)
		{
			atkRoll *= 1 + eq.crystalAcc;
			avgMax = Math.floor(maxHit * (1 + eq.crystalDmg));
		}

		double acc = CombatFormulas.accuracy(atkRoll, defRoll);
		// Rapid is one tick faster, but a target that demands more reach than
		// the weapon has forces longrange, which gives that tick back.
		int speed = needsLongrange(weaponId, m)
			? eq.speedTicks
			: Math.max(1, eq.speedTicks - 1);
		return CombatFormulas.dps(acc, avgMax, speed);
	}

	private static double magicDps(int weaponId, EquipmentTotals eq,
		PlayerSnapshot p, MonsterProfile m, boolean elite)
	{
		int magic = p.getMagic();
		int baseHit;
		boolean castsFireSpell = false;
		double magicAtkBonus = eq.magicAtk;
		double dmgPercent = eq.magicDmgPercent;

		// Speed comes from the shared helper so the Olm 4-tick filter and the
		// damage maths can never disagree about a weapon's rhythm.
		int speed = magicSpeedTicks(weaponId);
		if (weaponId == SHADOW)
		{
			baseHit = magic / 3 + 1;
			// Shadow triples the equipment's magic accuracy and damage bonuses
			magicAtkBonus *= 3;
			dmgPercent = Math.min(dmgPercent * 3, 100);
		}
		else if (weaponId == EYE_OF_AYAK)
		{
			// The fastest magic weapon in the game at 3 ticks, which more than
			// offsets its lower base hit — without this it fell through to the
			// generic 24-at-speed-5 branch and lost to a plain trident.
			baseHit = magic / 3 - 6;
		}
		else if (SANG.contains(weaponId))
		{
			baseHit = magic / 3; // buffed 22 Jul 2026, was magic/3 - 1
		}
		else if (weaponId == TRIDENT_SWAMP)
		{
			baseHit = magic / 3 - 2;
		}
		else if (weaponId == TRIDENT_SEAS)
		{
			baseHit = magic / 3 - 5;
		}
		else if (weaponId == HARMONISED)
		{
			baseHit = 24; // Fire Surge, no cast delay
			castsFireSpell = true;
		}
		else
		{
			baseHit = 24; // Fire Surge on an ordinary staff
			castsFireSpell = true;
		}

		int maxHit = (int) (baseHit * (1 + dmgPercent / 100.0));
		// The tome of fire boosts standard-spellbook fire spells, not the
		// built-in spells of powered staves
		if (castsFireSpell)
		{
			maxHit = (int) Math.floor(maxHit * eq.fireSpellMult);
		}
		// Augury boosts accuracy only
		int effMagic = (int) (magic * (elite ? 1.25 : 1.0)) + 8;
		if (elite)
		{
			dmgPercent += 4; // Augury also grants +4% magic damage
		}
		double atkRoll = effMagic * (magicAtkBonus + 64) * eq.setAccMult;
		maxHit = (int) Math.floor(maxHit * eq.setDmgMult);

		double salve = m.isUndead() ? eq.salveMagicMult : 1.0;
		atkRoll *= salve;
		maxHit = (int) Math.floor(maxHit * salve);

		// Elemental weakness is ADDITIVE on the spell's base max, and grants the
		// same percentage as magic accuracy. Only standard-spellbook fire spells
		// qualify — a powered staff's built-in spell does not, so it eats the
		// demon's damage reduction instead.
		if (castsFireSpell && m.getElementalWeakness() > 0)
		{
			maxHit += (int) Math.floor(baseHit * m.getElementalWeakness());
			atkRoll *= 1 + m.getElementalWeakness();
		}
		else
		{
			maxHit = (int) Math.floor(maxHit * m.getNonFireDamageMult());
		}
		maxHit = (int) Math.floor(maxHit * m.getMagicDamageMult());

		double acc = CombatFormulas.accuracy(atkRoll,
			CombatFormulas.defenceRoll(m.getMagicDefenceLevel(), m.getDMagic()));
		return CombatFormulas.dps(acc, maxHit, speed);
	}
}
