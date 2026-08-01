package com.coxgearplanner;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Representative monster (and how many of them) per combat room. HP is the
 * base value; party scaling is applied by the estimator. Stats are
 * approximate wiki values — edit freely.
 */
public final class RoomMonsters
{
	public static class Encounter
	{
		private final MonsterProfile profile;
		private final int count;

		Encounter(MonsterProfile profile, int count)
		{
			this.profile = profile;
			this.count = count;
		}

		public MonsterProfile getProfile()
		{
			return profile;
		}

		public int getCount()
		{
			return count;
		}
	}

	private static final Map<CoxRoom, Encounter> ENCOUNTERS = new EnumMap<>(CoxRoom.class);

	private RoomMonsters()
	{
	}

	static
	{
		// name, hp, def, magic, dStab, dSlash, dCrush, dMagic, dRange, large, draconic, styles
		ENCOUNTERS.put(CoxRoom.TEKTON, new Encounter(new MonsterProfile(
			"Tekton", 300, 205, 205, 155, 165, 105, 600, 600, true, false,
			GearNeed.MELEE), 1));
		ENCOUNTERS.put(CoxRoom.MUTTADILES, new Encounter(new MonsterProfile(
			"Muttadile", 225, 128, 200, 80, 90, 75, 55, 65, true, false,
			GearNeed.MELEE, GearNeed.RANGED), 2));
		ENCOUNTERS.put(CoxRoom.GUARDIANS, new Encounter(new MonsterProfile(
			"Guardian", 250, 100, 1, 90, 90, 80, 600, 600, true, false,
			GearNeed.MELEE), 2));
		ENCOUNTERS.put(CoxRoom.VESPULA, new Encounter(new MonsterProfile(
			"Vespula", 200, 88, 150, 60, 60, 60, 40, 30, true, false,
			GearNeed.RANGED, GearNeed.MAGIC), 1));
		ENCOUNTERS.put(CoxRoom.SHAMANS, new Encounter(new MonsterProfile(
			"Lizardman shaman", 150, 130, 130, 70, 70, 70, 60, 40, true, false,
			GearNeed.MELEE, GearNeed.RANGED), 3));
		ENCOUNTERS.put(CoxRoom.VASA, new Encounter(new MonsterProfile(
			"Vasa Nistirio", 300, 175, 230, 170, 170, 170, 230, 60, true, false,
			GearNeed.RANGED), 1));
		// Skeletal mystics are undead, so the salve amulet applies here — the
		// only CoX room where it does. Add .undead() to others if that changes.
		ENCOUNTERS.put(CoxRoom.MYSTICS, new Encounter(new MonsterProfile(
			"Skeletal mystic", 160, 187, 140, 70, 70, 70, 80, 50, false, false,
			GearNeed.MELEE, GearNeed.RANGED).undead(), 3));
		ENCOUNTERS.put(CoxRoom.VANGUARDS, new Encounter(new MonsterProfile(
			"Vanguard", 180, 110, 150, 50, 50, 50, 50, 50, false, false,
			GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC), 3));
		ENCOUNTERS.put(CoxRoom.TIGHTROPE, new Encounter(new MonsterProfile(
			"Deathly ranger/mage", 80, 80, 100, 60, 60, 60, 40, 40, false, false,
			GearNeed.RANGED, GearNeed.MAGIC), 4));
		ENCOUNTERS.put(CoxRoom.ICE_DEMON, new Encounter(new MonsterProfile(
			"Ice demon", 175, 160, 140, 200, 200, 200, 60, 200, false, false,
			GearNeed.MAGIC), 1));
		// Head + both hands rolled into one HP pool; magic level is the
		// head's (what the twisted bow scales from). Draconic → dragonbane.
		ENCOUNTERS.put(CoxRoom.OLM, new Encounter(new MonsterProfile(
			"Great Olm (head + hands)", 1020, 175, 250, 200, 200, 200, 80, 145, true, true,
			GearNeed.RANGED, GearNeed.MAGIC), 1));
	}

	public static Encounter get(CoxRoom room)
	{
		return ENCOUNTERS.get(room);
	}

	public static Map<CoxRoom, Encounter> all()
	{
		return Collections.unmodifiableMap(ENCOUNTERS);
	}
}
