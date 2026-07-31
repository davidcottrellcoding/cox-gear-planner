package com.coxgearplanner;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Combat-relevant stats for one CoX room's representative monster.
 * Values are the wiki's base (small-party) numbers; a few are approximate —
 * they live here as plain data so corrections are one-line fixes.
 */
public class MonsterProfile
{
	private final String name;
	private final int hp;
	private final int defenceLevel;
	private final int magicLevel;
	private final int dStab;
	private final int dSlash;
	private final int dCrush;
	private final int dMagic;
	private final int dRange;
	private final boolean large;
	private final boolean draconic;
	private final Set<GearNeed> usableStyles;

	MonsterProfile(String name, int hp, int defenceLevel, int magicLevel,
		int dStab, int dSlash, int dCrush, int dMagic, int dRange,
		boolean large, boolean draconic, GearNeed... usableStyles)
	{
		this.name = name;
		this.hp = hp;
		this.defenceLevel = defenceLevel;
		this.magicLevel = magicLevel;
		this.dStab = dStab;
		this.dSlash = dSlash;
		this.dCrush = dCrush;
		this.dMagic = dMagic;
		this.dRange = dRange;
		this.large = large;
		this.draconic = draconic;
		this.usableStyles = Collections.unmodifiableSet(EnumSet.of(usableStyles[0], usableStyles));
	}

	public String getName()
	{
		return name;
	}

	public int getHp()
	{
		return hp;
	}

	public int getDefenceLevel()
	{
		return defenceLevel;
	}

	public int getMagicLevel()
	{
		return magicLevel;
	}

	public int getDStab()
	{
		return dStab;
	}

	public int getDSlash()
	{
		return dSlash;
	}

	public int getDCrush()
	{
		return dCrush;
	}

	public int getDMagic()
	{
		return dMagic;
	}

	public int getDRange()
	{
		return dRange;
	}

	public boolean isLarge()
	{
		return large;
	}

	public boolean isDraconic()
	{
		return draconic;
	}

	public Set<GearNeed> getUsableStyles()
	{
		return usableStyles;
	}
}
