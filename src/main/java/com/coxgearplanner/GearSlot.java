package com.coxgearplanner;

/**
 * Equipment slots, ordered so the weapon is resolved before the shield
 * (a two-handed pick suppresses the shield slot).
 */
public enum GearSlot
{
	WEAPON("Weapon"),
	SHIELD("Shield"),
	HEAD("Head"),
	CAPE("Cape"),
	NECK("Neck"),
	AMMO("Ammo"),
	BODY("Body"),
	LEGS("Legs"),
	GLOVES("Gloves"),
	BOOTS("Boots"),
	RING("Ring");

	private final String displayName;

	GearSlot(String displayName)
	{
		this.displayName = displayName;
	}

	public String getDisplayName()
	{
		return displayName;
	}
}
