package com.coxgearplanner;

/**
 * One candidate item for a slot. May cover several item ids (charged and
 * uncharged variants, ornament kits, etc.) that are all "the same item"
 * for planning purposes.
 */
public class ItemOption
{
	private final String name;
	private final boolean twoHanded;
	private final int[] itemIds;

	private ItemOption(String name, boolean twoHanded, int[] itemIds)
	{
		this.name = name;
		this.twoHanded = twoHanded;
		this.itemIds = itemIds;
	}

	public static ItemOption of(String name, int... itemIds)
	{
		return new ItemOption(name, false, itemIds);
	}

	public static ItemOption twoHanded(String name, int... itemIds)
	{
		return new ItemOption(name, true, itemIds);
	}

	public String getName()
	{
		return name;
	}

	public boolean isTwoHanded()
	{
		return twoHanded;
	}

	public int[] getItemIds()
	{
		return itemIds;
	}
}
