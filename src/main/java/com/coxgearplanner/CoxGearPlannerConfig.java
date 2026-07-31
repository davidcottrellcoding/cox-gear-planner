package com.coxgearplanner;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

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
}
