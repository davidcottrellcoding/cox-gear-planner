package com.coxgearplanner;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Chambers of Xeric rooms and what each one demands from your gear.
 * Aliases are used to match room names when importing a layout from
 * pasted/copied scout text.
 */
public enum CoxRoom
{
	TEKTON("Tekton", true, new String[]{"tekton", "tek"},
		GearNeed.MELEE, GearNeed.DEF_REDUCTION),
	MUTTADILES("Muttadiles", true, new String[]{"muttadile", "mutta", "mutt"},
		GearNeed.RANGED, GearNeed.MELEE),
	GUARDIANS("Guardians", true, new String[]{"guardians", "guards"},
		GearNeed.MELEE, GearNeed.PICKAXE),
	VESPULA("Vespula", true, new String[]{"vespula", "vesp"},
		GearNeed.RANGED),
	SHAMANS("Lizardman Shamans", true, new String[]{"shaman", "lizardman"},
		GearNeed.RANGED),
	VASA("Vasa Nistirio", true, new String[]{"vasa"},
		GearNeed.RANGED, GearNeed.MELEE),
	MYSTICS("Skeletal Mystics", true, new String[]{"mystic", "skeletal"},
		GearNeed.RANGED, GearNeed.MELEE),
	VANGUARDS("Vanguards", true, new String[]{"vanguard", "vangs"},
		GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC),
	ICE_DEMON("Ice Demon", false, new String[]{"ice demon", "ice"},
		GearNeed.MAGIC, GearNeed.FIRE_SPELLS, GearNeed.AXE),
	THIEVING("Thieving", false, new String[]{"thieving", "thiev"},
		GearNeed.LOCKPICK),
	TIGHTROPE("Tightrope", false, new String[]{"tightrope", "rope"},
		GearNeed.RANGED),
	CRABS("Crabs", false, new String[]{"crab"}),
	OLM("Great Olm", true, new String[]{"olm"},
		GearNeed.RANGED, GearNeed.MAGIC, GearNeed.DEF_REDUCTION);

	private final String displayName;
	private final boolean boss;
	private final String[] aliases;
	private final Set<GearNeed> needs;

	CoxRoom(String displayName, boolean boss, String[] aliases, GearNeed... needs)
	{
		this.displayName = displayName;
		this.boss = boss;
		this.aliases = aliases;
		this.needs = needs.length == 0
			? Collections.unmodifiableSet(EnumSet.noneOf(GearNeed.class))
			: Collections.unmodifiableSet(EnumSet.of(needs[0], needs));
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public boolean isBoss()
	{
		return boss;
	}

	public String[] getAliases()
	{
		return aliases;
	}

	public Set<GearNeed> getNeeds()
	{
		return needs;
	}
}
