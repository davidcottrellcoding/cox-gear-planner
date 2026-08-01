package com.coxgearplanner;

import java.util.Arrays;
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

	private static final Map<CoxRoom, List<Encounter>> ENCOUNTERS = new EnumMap<>(CoxRoom.class);

	private RoomMonsters()
	{
	}

	static
	{
		// name, hp, def, magic, dStab, dSlash, dCrush, dMagic, dRange, large, draconic, styles
		ENCOUNTERS.put(CoxRoom.TEKTON, Collections.singletonList(new Encounter(new MonsterProfile(
			"Tekton", 300, 205, 205, 155, 165, 105, 600, 600, true, false,
			GearNeed.MELEE), 1)));
		ENCOUNTERS.put(CoxRoom.MUTTADILES, Collections.singletonList(new Encounter(new MonsterProfile(
			"Muttadile", 225, 128, 200, 80, 90, 75, 55, 65, true, false,
			GearNeed.MELEE, GearNeed.RANGED), 2)));
		ENCOUNTERS.put(CoxRoom.GUARDIANS, Collections.singletonList(new Encounter(new MonsterProfile(
			"Guardian", 250, 100, 1, 90, 90, 80, 600, 600, true, false,
			GearNeed.MELEE), 2)));
		ENCOUNTERS.put(CoxRoom.VESPULA, Collections.singletonList(new Encounter(new MonsterProfile(
			"Vespula", 200, 88, 150, 60, 60, 60, 40, 30, true, false,
			GearNeed.RANGED, GearNeed.MAGIC), 1)));
		ENCOUNTERS.put(CoxRoom.SHAMANS, Collections.singletonList(new Encounter(new MonsterProfile(
			"Lizardman shaman", 150, 130, 130, 70, 70, 70, 60, 40, true, false,
			GearNeed.MELEE, GearNeed.RANGED), 3)));
		ENCOUNTERS.put(CoxRoom.VASA, Collections.singletonList(new Encounter(new MonsterProfile(
			"Vasa Nistirio", 300, 175, 230, 170, 170, 170, 230, 60, true, false,
			GearNeed.RANGED), 1)));
		// Skeletal mystics are undead, so the salve amulet applies here — the
		// only CoX room where it does. Add .undead() to others if that changes.
		ENCOUNTERS.put(CoxRoom.MYSTICS, Collections.singletonList(new Encounter(new MonsterProfile(
			"Skeletal mystic", 160, 187, 140, 70, 70, 70, 80, 50, false, false,
			GearNeed.MELEE, GearNeed.RANGED).undead(), 3)));
		ENCOUNTERS.put(CoxRoom.VANGUARDS, Collections.singletonList(new Encounter(new MonsterProfile(
			"Vanguard", 180, 110, 150, 50, 50, 50, 50, 50, false, false,
			GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC), 3)));
		ENCOUNTERS.put(CoxRoom.TIGHTROPE, Collections.singletonList(new Encounter(new MonsterProfile(
			"Deathly ranger/mage", 80, 80, 100, 60, 60, 60, 40, 40, false, false,
			GearNeed.RANGED, GearNeed.MAGIC), 4)));
		ENCOUNTERS.put(CoxRoom.ICE_DEMON, Collections.singletonList(new Encounter(new MonsterProfile(
			"Ice demon", 175, 160, 140, 200, 200, 200, 60, 200, false, false,
			GearNeed.MAGIC), 1)));
		// The Great Olm is three separate targets with deliberately opposite
		// defensive profiles, which is why it demands all three styles:
		//
		//   left claw  ("melee hand") — melee def 50, magic/range def 200
		//   right claw ("mage hand")  — magic def 50 and magic level 87,
		//                               stab/slash/crush and range def 200
		//   head                      — range def 50, everything else 200
		//
		// Wiki values. The head also has 66% mitigation against non-ranged
		// damage and heals if hit outside the final phase, so in practice its
		// 800 HP is taken down during the final phase where the mitigation is
		// off — hence it is not applied here. Its range defence of 50 already
		// makes ranged the correct answer by a wide margin.
		ENCOUNTERS.put(CoxRoom.OLM, Arrays.asList(
			new Encounter(new MonsterProfile(
				"Olm left claw (melee hand)", 600, 175, 175, 50, 50, 50, 200, 200, true, true,
				GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC), 1),
			new Encounter(new MonsterProfile(
				"Olm right claw (mage hand)", 600, 175, 87, 200, 200, 200, 50, 200, true, true,
				GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC), 1),
			new Encounter(new MonsterProfile(
				"Olm head", 800, 150, 250, 200, 200, 200, 200, 50, true, true,
				GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC), 1)));
	}

	/**
	 * Olm has at least four phases, plus one per eight players. The claws are
	 * re-crippled each phase, so claw damage is multiplied by this.
	 */
	public static int olmPhases(int partySize)
	{
		return 4 + Math.max(1, partySize) / 8;
	}

	/** First encounter for a room; see {@link #getAll} for multi-target rooms. */
	public static Encounter get(CoxRoom room)
	{
		List<Encounter> encounters = ENCOUNTERS.get(room);
		return encounters == null || encounters.isEmpty() ? null : encounters.get(0);
	}

	/** Every target in a room — Olm is three, everything else is one. */
	public static List<Encounter> getAll(CoxRoom room)
	{
		List<Encounter> encounters = ENCOUNTERS.get(room);
		return encounters == null ? Collections.emptyList() : encounters;
	}

	public static Map<CoxRoom, List<Encounter>> all()
	{
		return Collections.unmodifiableMap(ENCOUNTERS);
	}
}
