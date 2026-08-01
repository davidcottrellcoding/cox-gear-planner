package com.coxgearplanner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The version appears in three places: the panel/export constant, the gradle
 * build, and the plugin hub properties. They drifted apart once already
 * because a scripted bump silently matched nothing, and the export then
 * reported a version that was two releases stale.
 */
public class VersionConsistencyTest
{
	private static String find(String file, String regex) throws IOException
	{
		String text = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);
		Matcher m = Pattern.compile(regex, Pattern.MULTILINE).matcher(text);
		assertTrue("no version found in " + file, m.find());
		return m.group(1);
	}

	@Test
	public void allThreeVersionsMatch() throws IOException
	{
		String gradle = find("build.gradle", "^version = '([^']+)'");
		String properties = find("runelite-plugin.properties", "^version=(.+)$");

		assertEquals("build.gradle disagrees with the VERSION constant",
			CoxGearPlannerPlugin.VERSION, gradle);
		assertEquals("runelite-plugin.properties disagrees with the VERSION constant",
			CoxGearPlannerPlugin.VERSION, properties.trim());
	}

	@Test
	public void pluginHubRequiresABuildEntry() throws IOException
	{
		// CI rejects the submission outright without this
		String text = new String(
			Files.readAllBytes(Paths.get("runelite-plugin.properties")), StandardCharsets.UTF_8);
		assertTrue("plugin hub needs build=standard or build=gradle",
			text.contains("build=standard") || text.contains("build=gradle"));
	}
}
