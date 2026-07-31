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
		keyName = "assumeElitePrayers",
		name = "Assume Piety/Rigour/Augury",
		description = "Assume elite offensive prayers are active when estimating room times",
		position = 5
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
		position = 6
	)
	default int minSwitchSeconds()
	{
		return 3;
	}
}
