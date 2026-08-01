package com.coxgearplanner;

/**
 * Something a raid layout can demand from the team's gear: a full combat
 * style loadout, or a specific utility item.
 */
public enum GearNeed
{
	MELEE("Melee"),
	RANGED("Ranged"),
	MAGIC("Magic"),
	PICKAXE("Pickaxe (Guardians)"),
	AXE("Axe (Ice Demon kindling)"),
	LOCKPICK("Lockpick (Thieving)"),
	FIRE_SPELLS("Fire spells (Ice Demon)"),
	DEF_REDUCTION("Defence reduction special");

	private final String displayName;

	GearNeed(String displayName)
	{
		this.displayName = displayName;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public boolean isCombatStyle()
	{
		return this == MELEE || this == RANGED || this == MAGIC;
	}
}
