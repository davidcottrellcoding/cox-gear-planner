package com.coxgearplanner;

/**
 * The combat levels used for DPS math — boosted levels straight from the
 * client when logged in, or assumed levels otherwise.
 */
public class PlayerSnapshot
{
	private final int attack;
	private final int strength;
	private final int ranged;
	private final int magic;
	/** Mining drives both the Guardians' HP and your damage against them. */
	private final int mining;

	public PlayerSnapshot(int attack, int strength, int ranged, int magic)
	{
		this(attack, strength, ranged, magic, 99);
	}

	public PlayerSnapshot(int attack, int strength, int ranged, int magic, int mining)
	{
		this.attack = attack;
		this.strength = strength;
		this.ranged = ranged;
		this.magic = magic;
		this.mining = mining;
	}

	public int getAttack()
	{
		return attack;
	}

	public int getStrength()
	{
		return strength;
	}

	public int getRanged()
	{
		return ranged;
	}

	public int getMagic()
	{
		return magic;
	}

	public int getMining()
	{
		return mining;
	}
}
