package com.coxgearplanner;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(CoxGearPlannerConfig.GROUP)
public interface CoxGearPlannerConfig extends Config
{
	String GROUP = "coxgearplanner";

	@ConfigItem(
		keyName = "includeGroupStorage",
		name = "Include group storage",
		description = "Also pull gear suggestions from your group ironman shared storage",
		position = 1
	)
	default boolean includeGroupStorage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "rememberBank",
		name = "Remember bank between sessions",
		description = "Persist the last-seen contents of your bank and group storage so suggestions work without reopening them",
		position = 2
	)
	default boolean rememberBank()
	{
		return true;
	}

	@Range(min = 1, max = 100)
	@ConfigItem(
		keyName = "partySize",
		name = "Party size",
		description = "Raid party size, used to scale monster HP for room time estimates",
		position = 3
	)
	default int partySize()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "assumeOverload",
		name = "Assume overload",
		description = "Assume an overload (+) boost when estimating room times (ignored if you're logged in with boosts active)",
		position = 4
	)
	default boolean assumeOverload()
	{
		return true;
	}

	@ConfigItem(
		keyName = "assumeImbuedHeart",
		name = "Assume imbued heart",
		description = "Assume an imbued heart boost (+1 and 10% of your Magic level). Note it does NOT stack with an overload and is weaker than one at every level, so this only changes anything when 'Assume overload' is off — for example the first rooms before you brew overloads.",
		position = 5
	)
	default boolean assumeImbuedHeart()
	{
		return false;
	}

	@ConfigItem(
		keyName = "assumeElitePrayers",
		name = "Assume Piety/Rigour/Augury",
		description = "Assume elite offensive prayers are active when estimating room times",
		position = 6
	)
	default boolean assumeElitePrayers()
	{
		return true;
	}

	@Range(min = 0, max = 60)
	@ConfigItem(
		keyName = "minSwitchSeconds",
		name = "Minimum switch value (seconds)",
		description = "Gear switches that save less than this many seconds across the selected rooms are flagged as not worth the inventory slot. 0 shows every switch as worth carrying.",
		position = 7
	)
	default int minSwitchSeconds()
	{
		return 3;
	}

	@Range(min = 0, max = 11)
	@ConfigItem(
		keyName = "maxSwitchItems",
		name = "Max items per switch",
		description = "Hard cap on how many ARMOUR pieces you swap for each secondary style. Weapons and their ammo are not swaps and always come, so they do not count. Only the most valuable pieces are kept. 0 means no limit.",
		position = 8
	)
	default int maxSwitchItems()
	{
		return 0;
	}

	@Range(min = 0, max = 28)
	@ConfigItem(
		keyName = "totalSwapItems",
		name = "Total swap items (all styles)",
		description = "Carry exactly this many ARMOUR swaps in total and let the planner spend them wherever they save the most time — it may end up 8 items on one style and 2 on another. Weapons, their ammo and offhands do not count: they always come. 0 uses the per-style cap above instead.",
		position = 9
	)
	default int totalSwapItems()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "olmFourTick",
		name = "Force 4-tick weapons at Olm",
		description = "Restricts the melee and magic weapon at Olm to 4-tick options, so both styles share one attack rhythm. Easier to learn than mixing a 5-tick scythe or shadow with a 4-tick swap. Falls back to your fastest option if you own no 4-tick weapon for a style.",
		position = 10
	)
	default boolean olmFourTick()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideMissing",
		name = "Hide unowned recommendations",
		description = "Only plan with gear you actually own — hide the red 'BiS to chase' lines for slots where you own nothing",
		position = 11
	)
	default boolean hideMissing()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showDebug",
		name = "Show debug panel",
		description = "Adds a section explaining why each item, weapon and switch was chosen over the alternatives you own",
		position = 12
	)
	default boolean showDebug()
	{
		return false;
	}
}
