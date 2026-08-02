package com.coxgearplanner;

import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemStats;

/**
 * Summed offensive bonuses of a candidate loadout. Attack speed is the
 * weapon's, set explicitly rather than summed.
 */
public class EquipmentTotals
{
	int stabAtk;
	int slashAtk;
	int crushAtk;
	int rangedAtk;
	int magicAtk;
	int meleeStr;
	int rangedStr;
	double magicDmgPercent;
	int speedTicks = 4;
	// Crystal armour set bonus vs crystal bow / bow of faerdhinen
	double crystalAcc;
	double crystalDmg;
	// Salve amulet multiplier vs undead: 1.0 when no salve is worn. The
	// non-imbued versions only boost melee, so the two are tracked apart.
	double salveMeleeMult = 1.0;
	double salveRangedMagicMult = 1.0;
	/** Magic is separate: the (i) gives 15% to magic but 16.67% to melee/ranged. */
	double salveMagicMult = 1.0;
	// Inquisitor's armour: crush accuracy and damage only, summed per piece
	double inquisitorCrush;
	/** Confliction gauntlets: a second accuracy roll, worth most when inaccurate. */
	boolean confliction;
	// Tome of fire: standard-spellbook fire spells only, not powered staves
	double fireSpellMult = 1.0;
	// Complete-set effects (void, obsidian) — see GearSetBonus
	double setAccMult = 1.0;
	double setDmgMult = 1.0;

	void add(ItemStats stats)
	{
		if (stats == null)
		{
			return;
		}
		ItemEquipmentStats eq = stats.getEquipment();
		if (eq == null)
		{
			return;
		}
		stabAtk += eq.getAstab();
		slashAtk += eq.getAslash();
		crushAtk += eq.getAcrush();
		rangedAtk += eq.getArange();
		magicAtk += eq.getAmagic();
		meleeStr += eq.getStr();
		rangedStr += eq.getRstr();
		magicDmgPercent += eq.getMdmg();
	}

}
