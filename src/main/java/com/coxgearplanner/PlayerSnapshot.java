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

	public PlayerSnapshot(int attack, int strength, int ranged, int magic)
	{
		this.attack = attack;
		this.strength = strength;
		this.ranged = ranged;
		this.magic = magic;
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
}
