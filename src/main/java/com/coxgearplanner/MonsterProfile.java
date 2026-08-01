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
	/** Undead monsters take the salve amulet's damage/accuracy bonus. */
	private boolean undead;
	/** Demons take the demonbane weapons' bonus (arclight, emberlight). */
	private boolean demon;
	/** Tekton takes 80% reduced magic damage despite having no magic defence. */
	private double magicDamageMult = 1.0;
	/** The ice demon reduces all non-fire damage by 67%. */
	private double nonFireDamageMult = 1.0;
	/** ...and takes 150% extra from standard-spellbook fire spells. */
	private double fireSpellDamageMult = 1.0;
	/** The ice demon rolls magic defence off its Defence level, not Magic. */
	private boolean magicDefenceFromDefence;
	/**
	 * Styles the fight actually favours for positional reasons — safespotting,
	 * a target you can't reach in melee, a mechanic that demands one style.
	 * DPS alone can't see any of this. Empty means "no preference, pick on
	 * damage". Falls back to {@link #usableStyles} if you own nothing for it.
	 */
	private Set<GearNeed> preferredStyles = Collections.emptySet();
	private String styleNote;
	/**
	 * A utility item class that is the ONLY thing able to damage this monster
	 * — the Guardians take damage from pickaxes and nothing else. Null means
	 * any weapon of a usable style works.
	 */
	private GearNeed requiredWeapon;

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

	/** Marks this monster as undead (salve amulet applies). */
	MonsterProfile undead()
	{
		this.undead = true;
		return this;
	}

	public boolean isUndead()
	{
		return undead;
	}

	MonsterProfile demon()
	{
		this.demon = true;
		return this;
	}

	MonsterProfile magicDamage(double multiplier)
	{
		this.magicDamageMult = multiplier;
		return this;
	}

	MonsterProfile nonFireDamage(double multiplier)
	{
		this.nonFireDamageMult = multiplier;
		return this;
	}

	MonsterProfile fireSpellDamage(double multiplier)
	{
		this.fireSpellDamageMult = multiplier;
		return this;
	}

	MonsterProfile magicDefenceFromDefenceLevel()
	{
		this.magicDefenceFromDefence = true;
		return this;
	}

	/** Marks the styles this fight favours, and why. */
	MonsterProfile prefers(String note, GearNeed... styles)
	{
		this.styleNote = note;
		this.preferredStyles = Collections.unmodifiableSet(EnumSet.of(styles[0], styles));
		return this;
	}

	/** Restricts damage to one utility item class, e.g. pickaxes. */
	MonsterProfile requiresWeapon(GearNeed utility)
	{
		this.requiredWeapon = utility;
		return this;
	}

	/** The only weapon class that can damage this monster, or null. */
	public GearNeed getRequiredWeapon()
	{
		return requiredWeapon;
	}

	public Set<GearNeed> getPreferredStyles()
	{
		return preferredStyles;
	}

	/** Why this fight favours those styles, for the room line. */
	public String getStyleNote()
	{
		return styleNote;
	}

	public boolean isDemon()
	{
		return demon;
	}

	public double getMagicDamageMult()
	{
		return magicDamageMult;
	}

	public double getNonFireDamageMult()
	{
		return nonFireDamageMult;
	}

	public double getFireSpellDamageMult()
	{
		return fireSpellDamageMult;
	}

	/** The level a magic defence roll is computed from. */
	public int getMagicDefenceLevel()
	{
		return magicDefenceFromDefence ? defenceLevel : magicLevel;
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
