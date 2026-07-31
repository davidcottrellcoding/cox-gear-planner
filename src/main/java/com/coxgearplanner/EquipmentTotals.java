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

	EquipmentTotals copy()
	{
		EquipmentTotals c = new EquipmentTotals();
		c.stabAtk = stabAtk;
		c.slashAtk = slashAtk;
		c.crushAtk = crushAtk;
		c.rangedAtk = rangedAtk;
		c.magicAtk = magicAtk;
		c.meleeStr = meleeStr;
		c.rangedStr = rangedStr;
		c.magicDmgPercent = magicDmgPercent;
		c.speedTicks = speedTicks;
		return c;
	}
}
