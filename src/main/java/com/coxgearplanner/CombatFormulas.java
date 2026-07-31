package com.coxgearplanner;

/**
 * The standard OSRS combat formulas, as documented on the wiki
 * (https://oldschool.runescape.wiki/w/Damage_per_second). Pure math with no
 * client dependencies so it is unit-testable.
 */
public final class CombatFormulas
{
	/** Ticks are 0.6 seconds. */
	public static final double TICK_SECONDS = 0.6;

	/** Twisted bow's target-magic cap inside the Chambers of Xeric. */
	private static final int TBOW_COX_MAGIC_CAP = 350;

	private CombatFormulas()
	{
	}

	/** floor(boosted * prayerMultiplier) + 8 + styleBonus */
	public static int effectiveLevel(int boostedLevel, double prayerMultiplier, int styleBonus)
	{
		return (int) (boostedLevel * prayerMultiplier) + 8 + styleBonus;
	}

	/** Melee/ranged max hit: floor(0.5 + effStr * (strBonus + 64) / 640) */
	public static int maxHit(int effectiveStrength, int strengthBonus)
	{
		return (int) (0.5 + effectiveStrength * (strengthBonus + 64) / 640.0);
	}

	public static int attackRoll(int effectiveLevel, int attackBonus)
	{
		return effectiveLevel * (attackBonus + 64);
	}

	public static int defenceRoll(int defenceLevel, int styleDefenceBonus)
	{
		return (defenceLevel + 9) * (styleDefenceBonus + 64);
	}

	/** Standard hit-chance formula. */
	public static double accuracy(double attackRoll, double defenceRoll)
	{
		if (attackRoll > defenceRoll)
		{
			return 1.0 - (defenceRoll + 2.0) / (2.0 * (attackRoll + 1.0));
		}
		return attackRoll / (2.0 * (defenceRoll + 1.0));
	}

	/** Twisted bow accuracy multiplier vs a target's magic level (CoX cap 350). */
	public static double tbowAccuracyMult(int targetMagicLevel)
	{
		int m = Math.min(targetMagicLevel, TBOW_COX_MAGIC_CAP);
		double pct = 140.0 + (3.0 * m - 10.0) / 100.0 - Math.pow(0.3 * m - 100.0, 2) / 100.0;
		return clamp(pct, 0, 140) / 100.0;
	}

	/** Twisted bow damage multiplier vs a target's magic level (CoX cap 350). */
	public static double tbowDamageMult(int targetMagicLevel)
	{
		int m = Math.min(targetMagicLevel, TBOW_COX_MAGIC_CAP);
		double pct = 250.0 + (3.0 * m - 14.0) / 100.0 - Math.pow(0.3 * m - 140.0, 2) / 100.0;
		return clamp(pct, 0, 250) / 100.0;
	}

	/** Average damage per attack (accuracy * uniform 0..max) over attack speed. */
	public static double dps(double accuracy, double averageMaxHit, int speedTicks)
	{
		return accuracy * (averageMaxHit / 2.0) / (speedTicks * TICK_SECONDS);
	}

	private static double clamp(double v, double lo, double hi)
	{
		return Math.max(lo, Math.min(hi, v));
	}
}
