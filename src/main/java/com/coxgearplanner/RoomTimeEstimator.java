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
	private static final int TRIDENT_SEAS = 11905;
	private static final int HARMONISED = 24423;

	// Which ranged weapons consume which ammo class
	private static final Set<Integer> ARROW_WEAPONS = new HashSet<>(Arrays.asList(20997, 12788));
	private static final Set<Integer> BOLT_WEAPONS = new HashSet<>(Arrays.asList(26374, 21012, 11785, 21902, 9185));
	private static final Set<Integer> ARROW_IDS = new HashSet<>(Arrays.asList(11212, 21326, 892, 890));
	private static final Set<Integer> BOLT_IDS = new HashSet<>(Arrays.asList(21946, 21944, 9243, 9242));

	// Bow of faerdhinen + crystal armour set effect (per piece: acc%, dmg%)
	private static final Set<Integer> BOFA = new HashSet<>(Arrays.asList(
		25865, 25867, 25884, 25886, 25888, 25890, 25892, 25894, 25896));
	private static final Set<Integer> CRYSTAL_HELM = new HashSet<>(Arrays.asList(
		23971, 27705, 27717, 27729, 27741, 27753, 27765, 27777));
	private static final Set<Integer> CRYSTAL_BODY = new HashSet<>(Arrays.asList(
		23975, 27697, 27709, 27721, 27733, 27745, 27757, 27769));
	private static final Set<Integer> CRYSTAL_LEGS = new HashSet<>(Arrays.asList(
		23979, 27701, 27713, 27725, 27737, 27749, 27761, 27773));

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
	}

	private final ItemManager itemManager;
	private final GearResolver resolver;

	public RoomTimeEstimator(ItemManager itemManager)
	{
		this.itemManager = itemManager;
		this.resolver = new GearResolver(itemManager);
	}

	public GearResolver getResolver()
	{
		return resolver;
	}

	/** Approximate CoX HP scaling with party size. */
	public static double hpMultiplier(int partySize)
	{
		return 1.0 + 0.5 * (Math.max(1, partySize) - 1);
	}

	public List<RoomTime> estimate(
		Set<CoxRoom> rooms,
		Map<ItemSource, Map<Integer, Integer>> items,
		boolean includeGroupStorage,
		PlayerSnapshot player,
		int partySize,
		boolean elitePrayers)
	{
		double hpMult = hpMultiplier(partySize);

		List<RoomTime> results = new ArrayList<>();
		for (CoxRoom room : CoxRoom.values())
		{
			if (!rooms.contains(room))
			{
				continue;
			}
			RoomMonsters.Encounter encounter = RoomMonsters.get(room);
			if (encounter == null)
			{
				continue; // non-combat room
			}

			MonsterProfile monster = encounter.getProfile();
			double bestDps = 0;
			GearNeed bestStyle = null;
			SetupBuilder.Pick bestWeapon = null;

			for (GearNeed style : monster.getUsableStyles())
			{
				Map<GearSlot, SetupBuilder.Pick> picks = resolver.resolve(style, items, includeGroupStorage);

				for (SetupBuilder.Pick weapon : weaponCandidates(style, items, includeGroupStorage))
				{
					double dps = loadoutDps(style, weapon, picks, Collections.emptyMap(),
						items, includeGroupStorage, player, monster, elitePrayers);
					if (dps > bestDps)
					{
						bestDps = dps;
						bestStyle = style;
						bestWeapon = weapon;
					}
				}
			}

			if (bestDps <= 0)
			{
				results.add(new RoomTime(room, "no usable weapon owned", 0, false, null, null));
			}
			else
			{
				double totalHp = monster.getHp() * hpMult * encounter.getCount();
				String label = bestWeapon.getOption().getName()
					+ " (" + bestStyle.getDisplayName().toLowerCase() + ")";
				results.add(new RoomTime(room, label, totalHp / bestDps, true, bestStyle, bestWeapon));
			}
		}
		return results;
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
		return candidates;
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

		double best = 0;
		for (int[] s : styles)
		{
			double acc = CombatFormulas.accuracy(
				CombatFormulas.attackRoll(effAtk, s[0]),
				CombatFormulas.defenceRoll(m.getDefenceLevel(), s[1]));
			if (weaponId == FANG)
			{
				// Fang rolls accuracy twice
				acc = 1 - (1 - acc) * (1 - acc);
			}
			double avgMax = maxHit;
			if (SCYTHES.contains(weaponId) && m.isLarge())
			{
				// Scythe hits 100% + 50% + 25% on large targets
				avgMax *= 1.75;
			}
			best = Math.max(best, CombatFormulas.dps(acc, avgMax, eq.speedTicks));
		}
		return best;
	}

	private static double rangedDps(int weaponId, EquipmentTotals eq,
		PlayerSnapshot p, MonsterProfile m, boolean elite)
	{
		// Rigour; rapid style (one tick faster, no level bonus)
		int effStr = CombatFormulas.effectiveLevel(p.getRanged(), elite ? 1.23 : 1.0, 0);
		int effAtk = CombatFormulas.effectiveLevel(p.getRanged(), elite ? 1.20 : 1.0, 0);
		int maxHit = CombatFormulas.maxHit(effStr, eq.rangedStr);

		double atkRoll = CombatFormulas.attackRoll(effAtk, eq.rangedAtk);
		double defRoll = CombatFormulas.defenceRoll(m.getDefenceLevel(), m.getDRange());
		double avgMax = maxHit;

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
		int speed = Math.max(1, eq.speedTicks - 1); // rapid
		return CombatFormulas.dps(acc, avgMax, speed);
	}

	private static double magicDps(int weaponId, EquipmentTotals eq,
		PlayerSnapshot p, MonsterProfile m, boolean elite)
	{
		int magic = p.getMagic();
		int baseHit;
		int speed;
		double magicAtkBonus = eq.magicAtk;
		double dmgPercent = eq.magicDmgPercent;

		if (weaponId == SHADOW)
		{
			baseHit = magic / 3 + 1;
			speed = 5;
			// Shadow triples the equipment's magic accuracy and damage bonuses
			magicAtkBonus *= 3;
			dmgPercent = Math.min(dmgPercent * 3, 100);
		}
		else if (SANG.contains(weaponId))
		{
			baseHit = magic / 3 - 1;
			speed = 4;
		}
		else if (weaponId == TRIDENT_SWAMP)
		{
			baseHit = magic / 3 - 2;
			speed = 4;
		}
		else if (weaponId == TRIDENT_SEAS)
		{
			baseHit = magic / 3 - 5;
			speed = 4;
		}
		else if (weaponId == HARMONISED)
		{
			baseHit = 24; // Fire Surge, no cast delay
			speed = 4;
		}
		else
		{
			baseHit = 24; // Fire Surge on an ordinary staff
			speed = 5;
		}

		int maxHit = (int) (baseHit * (1 + dmgPercent / 100.0));
		// Augury boosts accuracy only
		int effMagic = (int) (magic * (elite ? 1.25 : 1.0)) + 9;
		double acc = CombatFormulas.accuracy(
			effMagic * (magicAtkBonus + 64),
			CombatFormulas.defenceRoll(m.getMagicLevel(), m.getDMagic()));
		return CombatFormulas.dps(acc, maxHit, speed);
	}
}
