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
	// The abyssal portal is the kill target, and it needs 7+ tile reach
	VESPULA("Vespula", true, new String[]{"vespula", "vesp"},
		GearNeed.RANGED, GearNeed.MAGIC),
	SHAMANS("Lizardman Shamans", true, new String[]{"shaman", "lizardman"},
		GearNeed.RANGED),
	// Vasa himself wants ranged (range defence 40 against magic's 400), but
	// the glowing crystals he siphons from need a STAB weapon: they are immune
	// to ranged, take 66% less from magic, and resist crush and slash. Failing
	// to break one in the ~40s window lets him heal back what he siphoned.
	VASA("Vasa Nistirio", true, new String[]{"vasa"},
		GearNeed.RANGED, GearNeed.MELEE),
	MYSTICS("Skeletal Mystics", true, new String[]{"mystic", "skeletal"},
		GearNeed.RANGED, GearNeed.MELEE),
	VANGUARDS("Vanguards", true, new String[]{"vanguard", "vangs"},
		GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC),
	// The room supplies its own tinderbox AND bronze axe, and the ice is
	// cleared by burning kindling — no fire spell is required to progress.
	//
	// No axe is listed either: kindling per chop scales with Woodcutting
	// level, not axe tier, so a better axe only buys chopping speed on the
	// ~54 kindling needed. That is not worth a permanent inventory slot when
	// the room hands you an axe for free.
	//
	// Fire spells and demonbane weapons matter for damage, not for entry.
	ICE_DEMON("Ice Demon", false, new String[]{"ice demon", "ice"},
		GearNeed.MAGIC, GearNeed.MELEE),
	THIEVING("Thieving", false, new String[]{"thieving", "thiev"},
		GearNeed.LOCKPICK),
	TIGHTROPE("Tightrope", false, new String[]{"tightrope", "rope"},
		GearNeed.RANGED),
	CRABS("Crabs", false, new String[]{"crab"}),
	// Olm's three targets each favour a different style: melee on the left
	// claw, magic on the right claw, ranged on the head.
	OLM("Great Olm", true, new String[]{"olm"},
		GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC, GearNeed.DEF_REDUCTION);

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
