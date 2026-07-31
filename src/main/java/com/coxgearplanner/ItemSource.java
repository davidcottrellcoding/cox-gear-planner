package com.coxgearplanner;

/**
 * Where an item was last seen. Order matters: earlier sources are preferred
 * when the same item exists in several places.
 */
public enum ItemSource
{
	EQUIPMENT("Worn"),
	INVENTORY("Inventory"),
	BANK("Bank"),
	GROUP_STORAGE("Group storage");

	private final String displayName;

	ItemSource(String displayName)
	{
		this.displayName = displayName;
	}

	public String getDisplayName()
	{
		return displayName;
	}
}
