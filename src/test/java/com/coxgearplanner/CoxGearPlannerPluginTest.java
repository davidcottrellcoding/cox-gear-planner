package com.coxgearplanner;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Launches a development RuneLite client with the plugin loaded.
 * Run this class from your IDE to test in-game.
 */
public class CoxGearPlannerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CoxGearPlannerPlugin.class);
		RuneLite.main(args);
	}
}
