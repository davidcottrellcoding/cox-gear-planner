package com.coxgearplanner;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Monster stats per combat room, taken from the OSRS Wiki infoboxes.
 *
 * Every CoX infobox carries the note that its stats "are scaled for a player
 * with maxed combat stats" — so these are the **solo, maxed-player** baseline
 * and the estimator scales HP from there.
 *
 * Order: name, hp, defence, magic, dStab, dSlash, dCrush, dMagic, dRange,
 * large, draconic, styles...
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
		// Tekton has NO magic or ranged defence bonus, but takes 80% reduced
		// magic damage, which is what actually keeps you off magic here.
		ENCOUNTERS.put(CoxRoom.TEKTON, Collections.singletonList(new Encounter(new MonsterProfile(
			"Tekton", 300, 205, 205, 155, 165, 105, 0, 0, true, false,
			GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC)
			.magicDamage(0.20)
			.prefers("meleed at the anvil — no safespot", GearNeed.MELEE), 1)));

		// Two separate muttadiles with very different stats
		ENCOUNTERS.put(CoxRoom.MUTTADILES, Arrays.asList(
			new Encounter(new MonsterProfile(
				"Muttadile (small)", 250, 138, 1, -5, 72, 50, 60, 0, true, false,
				GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC), 1),
			new Encounter(new MonsterProfile(
				"Muttadile (large)", 250, 220, 250, -5, 82, 60, 75, 0, true, false,
				GearNeed.RANGED, GearNeed.MAGIC)
				.prefers("sits in the water, out of melee reach", GearNeed.RANGED), 1)));

		// Guardians can only be harmed with a pickaxe — the room's real
		// requirement is the pickaxe utility item, not a combat weapon.
		ENCOUNTERS.put(CoxRoom.GUARDIANS, Collections.singletonList(new Encounter(new MonsterProfile(
			"Guardian", 250, 100, 1, 80, 180, -10, 0, 0, true, false,
			GearNeed.MELEE).prefers("can only be harmed with a pickaxe", GearNeed.MELEE), 2)));

		ENCOUNTERS.put(CoxRoom.VESPULA, Collections.singletonList(new Encounter(new MonsterProfile(
			"Vespula", 200, 88, 88, 0, 0, 0, 70, 60, true, false,
			GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC)
			.prefers("flies until downed — ranged while airborne", GearNeed.RANGED), 1)));

		ENCOUNTERS.put(CoxRoom.SHAMANS, Collections.singletonList(new Encounter(new MonsterProfile(
			"Lizardman shaman", 190, 210, 130, 102, 160, 150, 160, 0, true, false,
			GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC), 3)));

		ENCOUNTERS.put(CoxRoom.VASA, Collections.singletonList(new Encounter(new MonsterProfile(
			"Vasa Nistirio", 300, 175, 230, 170, 190, 40, 400, 40, true, false,
			GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC)
			.prefers("teleports around the room and drains at range", GearNeed.RANGED), 1)));

		// Skeletal mystics are undead → the salve amulet applies here.
		// 2x2, so the scythe hits twice rather than three times.
		ENCOUNTERS.put(CoxRoom.MYSTICS, Collections.singletonList(new Encounter(new MonsterProfile(
			"Skeletal mystic", 160, 187, 140, 155, 155, 75, 140, 115, false, false,
			GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC)
			.undead()
			.prefers("safespotted from behind the pillars", GearNeed.RANGED, GearNeed.MAGIC), 3)));

		// The three vanguards share levels but have deliberately opposite
		// defences: each is soft to exactly one style.
		ENCOUNTERS.put(CoxRoom.VANGUARDS, Arrays.asList(
			new Encounter(new MonsterProfile(
				"Vanguard (melee) — weak to magic", 180, 160, 150, 150, 150, 150, 20, 400, true, false,
				GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC), 1),
			new Encounter(new MonsterProfile(
				"Vanguard (ranged) — weak to melee", 180, 160, 150, 55, 60, 100, 400, 300, true, false,
				GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC), 1),
			new Encounter(new MonsterProfile(
				"Vanguard (magic) — weak to ranged", 180, 160, 150, 315, 340, 400, 110, 50, true, false,
				GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC), 1)));

		ENCOUNTERS.put(CoxRoom.TIGHTROPE, Arrays.asList(
			new Encounter(new MonsterProfile(
				"Deathly ranger", 120, 155, 155, 0, 0, 0, 0, 0, false, false,
				GearNeed.RANGED, GearNeed.MAGIC)
				.prefers("on platforms across the gap — melee cannot reach",
					GearNeed.RANGED, GearNeed.MAGIC), 2),
			new Encounter(new MonsterProfile(
				"Deathly mage", 120, 155, 210, 0, 0, 0, 0, 0, false, false,
				GearNeed.RANGED, GearNeed.MAGIC)
				.prefers("on platforms across the gap — melee cannot reach",
					GearNeed.RANGED, GearNeed.MAGIC), 2)));

		// The ice demon reduces ALL non-fire damage by 67% and takes 150% extra
		// from fire spells — which is why the room is a fire-spell check rather
		// than a gear check. Its magic defence rolls off its Defence level.
		ENCOUNTERS.put(CoxRoom.ICE_DEMON, Collections.singletonList(new Encounter(new MonsterProfile(
			"Ice demon", 140, 160, 390, 70, 70, 110, 40, 140, false, false,
			GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC)
			.demon().nonFireDamage(0.33).fireSpellDamage(2.50).magicDefenceFromDefenceLevel()
			.prefers("fire spells break the ice and do 150% damage", GearNeed.MAGIC), 1)));

		// The Great Olm is three targets with deliberately opposite defensive
		// profiles, which is why it demands all three styles:
		//   left claw  ("melee hand") — melee def 50, magic/range def 200
		//   right claw ("mage hand")  — magic def 50 and magic level 87
		//   head                      — range def 50, everything else 200
		//
		// The head also has 66% mitigation against non-ranged damage and heals
		// if hit outside the final phase, so its 800 HP comes down during the
		// final phase where the mitigation is off — hence not applied. Its
		// range defence of 50 already makes ranged correct by a wide margin.
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
	 * Olm has at least four phases, plus one per eight players. The last is the
	 * head phase, so a standard raid is three claw phases then the head.
	 */
	public static int olmPhases(int partySize)
	{
		return 4 + Math.max(1, partySize) / 8;
	}

	/** How many times each claw is fought — every phase except the head phase. */
	public static int olmClawPhases(int partySize)
	{
		return olmPhases(partySize) - 1;
	}

	/** First encounter for a room; see {@link #getAll} for multi-target rooms. */
	public static Encounter get(CoxRoom room)
	{
		List<Encounter> encounters = ENCOUNTERS.get(room);
		return encounters == null || encounters.isEmpty() ? null : encounters.get(0);
	}

	/** Every target in a room — Olm, the vanguards and the muttadiles are several. */
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
