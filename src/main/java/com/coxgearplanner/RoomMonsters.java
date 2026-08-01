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
		//
		// He fights in two defence profiles. After returning from the anvil he
		// is ENRAGED and much harder to hit: stab 280 / slash 290 / crush 180
		// against 155 / 165 / 105 asleep. The community efficiency sheet puts
		// the accuracy difference at 0.579 unenraged versus 0.384 enraged, so
		// treating the whole fight as unenraged badly overrates him.
		//
		// The 300 HP is split evenly between the two. The real split depends on
		// how much damage you land in the opening burst and how many anvil
		// trips happen, which is not published — but an even split is far
		// closer than pretending the whole fight happens asleep. Crush is the
		// soft style in BOTH profiles, so the weapon choice is unaffected;
		// what changes is how long he takes and therefore how much he pulls
		// the base outfit toward melee.
		ENCOUNTERS.put(CoxRoom.TEKTON, Arrays.asList(
			new Encounter(new MonsterProfile(
				"Tekton (unenraged)", 150, 205, 205, 155, 165, 105, 0, 0, true, false,
				GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC).size(4)
				.magicDamage(0.20)
				.prefers("meleed at the anvil — no safespot", GearNeed.MELEE), 1),
			new Encounter(new MonsterProfile(
				"Tekton (enraged)", 150, 205, 205, 280, 290, 180, 0, 0, true, false,
				GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC).size(4)
				.magicDamage(0.20)
				.prefers("enraged after the anvil — far harder to hit", GearNeed.MELEE), 1)));

		// Two separate muttadiles with very different stats. The large one is
		// submerged and unattackable only WHILE the small one is alive; once
		// the small one dies it emerges and can be fought with any style,
		// melee included. What actually argues for ranged is its stomp, which
		// hits everyone in melee range for around 50% more than a normal hit.
		//
		// Both eat from the meat tree at ~50% health, healing up to 50% each
		// and up to three times. That is not modelled: it is preventable, so
		// baking it in would overstate the room for anyone who stops them.
		ENCOUNTERS.put(CoxRoom.MUTTADILES, Arrays.asList(
			new Encounter(new MonsterProfile(
				"Muttadile (small)", 250, 138, 1, -5, 72, 50, 60, 0, true, false,
				GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC).size(3), 1),
			new Encounter(new MonsterProfile(
				"Muttadile (large)", 250, 220, 250, -5, 82, 60, 75, 0, true, false,
				GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC).size(5)
				.prefers("emerges when the small one dies; its stomp punishes melee range",
					GearNeed.RANGED, GearNeed.MAGIC), 1)));

		// Guardians can only be harmed with a pickaxe — the room's real
		// requirement is the pickaxe utility item, not a combat weapon.
		ENCOUNTERS.put(CoxRoom.GUARDIANS, Collections.singletonList(new Encounter(new MonsterProfile(
			"Guardian", 250, 100, 1, 80, 180, -10, 0, 0, true, false,
			GearNeed.MELEE).size(3)
			.requiresWeapon(GearNeed.PICKAXE)
			.prefers("can only be harmed with a pickaxe", GearNeed.MELEE), 2)));

		// The ABYSSAL PORTAL is what has to die to end the room — Vespula does
		// not have to be killed at all. The standard "Redemption method" never
		// brings her down: you flinch the portal from a safespot and let
		// Redemption absorb the retaliation. So the portal's 250 HP is the
		// target, not Vespula's 200.
		//
		// The portal can only be attacked by weapons with a reach of 7+ tiles
		// (Tumeken's shadow, eldritch staff and longbows at any setting; other
		// powered staves and crossbows need long range).
		//
		// Neither method is really DPS-limited — the Redemption cycle is
		// attack, retreat, heal, restore prayer — so this time is a floor,
		// not a prediction.
		ENCOUNTERS.put(CoxRoom.VESPULA, Collections.singletonList(new Encounter(new MonsterProfile(
			"Abyssal portal", 250, 88, 88, 0, 0, 0, 70, 60, true, false,
			GearNeed.RANGED, GearNeed.MAGIC).size(5)
			.minReach(8)
			.prefers("8 tiles out; 10-tile bows stay on rapid, shorter ones need longrange",
				GearNeed.RANGED, GearNeed.MAGIC), 1)));

		ENCOUNTERS.put(CoxRoom.SHAMANS, Collections.singletonList(new Encounter(new MonsterProfile(
			"Lizardman shaman", 190, 210, 130, 102, 160, 150, 160, 0, true, false,
			GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC).size(3), 3)));

		// Two targets with opposite answers. Vasa himself is a ranged fight
		// (range defence 40 against magic's 400). The glowing crystal he
		// siphons from is immune to ranged entirely and takes a third damage
		// from magic, so it needs a melee weapon the ranged setup cannot
		// provide. Break it inside the ~40s window or he heals back what he
		// took.
		//
		// Stab is far more ACCURATE against it — -5 stab defence against +180
		// slash — but that does not make stab the only answer. The crystal is
		// 4x4, so a scythe hits three times, and with oathplate that triple
		// hit outweighs halved accuracy: roughly 10.7 dps against a fang's
		// 8.5. Scythe is the Challenge Mode meta here for exactly that reason.
		// The estimator compares them on dps and picks whichever wins, which
		// is why the crystal is flagged large.
		//
		// Count is 1: four crystals sit in the corners but he siphons from one
		// at a time and you break the one he goes to.
		ENCOUNTERS.put(CoxRoom.VASA, Arrays.asList(
			new Encounter(new MonsterProfile(
				"Vasa", 300, 175, 230, 170, 190, 40, 400, 40, true, false,
				GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC).size(5)
				.prefers("teleports around the room and drains at range", GearNeed.RANGED), 1),
			new Encounter(new MonsterProfile(
				"Glowing crystal", 120, 100, 100, -5, 180, 180, 0, 0, true, false,
				GearNeed.MELEE, GearNeed.MAGIC).size(4)
				.magicDamage(1.0 / 3.0)
				.prefers("immune to ranged, 1/3 from magic; stab is accurate but a "
					+ "scythe triple-hits this 4x4 target", GearNeed.MELEE), 1)));

		// Skeletal mystics are undead → the salve amulet applies here.
		// 2x2, so the scythe hits twice rather than three times.
		ENCOUNTERS.put(CoxRoom.MYSTICS, Collections.singletonList(new Encounter(new MonsterProfile(
			"Skeletal mystic", 160, 187, 140, 155, 155, 75, 140, 115, false, false,
			GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC).size(2)
			.undead()
			.prefers("safespotted from behind the pillars", GearNeed.RANGED, GearNeed.MAGIC), 3)));

		// The three vanguards share levels but have deliberately opposite
		// defences: each is soft to exactly one style.
		ENCOUNTERS.put(CoxRoom.VANGUARDS, Arrays.asList(
			new Encounter(new MonsterProfile(
				"Vanguard (melee) — weak to magic", 180, 160, 150, 150, 150, 150, 20, 400, true, false,
				GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC).size(3), 1),
			new Encounter(new MonsterProfile(
				"Vanguard (ranged) — weak to melee", 180, 160, 150, 55, 60, 100, 400, 300, true, false,
				GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC).size(3), 1),
			new Encounter(new MonsterProfile(
				"Vanguard (magic) — weak to ranged", 180, 160, 150, 315, 340, 400, 110, 50, true, false,
				GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC).size(3), 1)));

		ENCOUNTERS.put(CoxRoom.TIGHTROPE, Arrays.asList(
			new Encounter(new MonsterProfile(
				"Deathly ranger", 120, 155, 155, 0, 0, 0, 0, 0, false, false,
				GearNeed.RANGED, GearNeed.MAGIC).size(1)
				.prefers("on platforms across the gap — melee cannot reach",
					GearNeed.RANGED, GearNeed.MAGIC), 2),
			new Encounter(new MonsterProfile(
				"Deathly mage", 120, 155, 210, 0, 0, 0, 0, 0, false, false,
				GearNeed.RANGED, GearNeed.MAGIC).size(1)
				.prefers("on platforms across the gap — melee cannot reach",
					GearNeed.RANGED, GearNeed.MAGIC), 2)));

		// The ice demon reduces all damage by 67% EXCEPT standard-spellbook fire
		// spells and demonbane weapons. Its 150% fire weakness is additive (+150%
		// magic damage and accuracy), and demonbane weapons hit at 115%
		// effectiveness — Emberlight's 70% becomes 80.5%. Its magic defence rolls
		// off its Defence level (160), not its Magic level (390).
		//
		// The room itself needs NO fire spell: a tinderbox and bronze axe spawn
		// inside, and the ice is cleared by burning kindling in the braziers.
		ENCOUNTERS.put(CoxRoom.ICE_DEMON, Collections.singletonList(new Encounter(new MonsterProfile(
			"Ice demon", 140, 160, 390, 70, 70, 110, 40, 140, false, false,
			GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC).size(2)
			.demon().nonFireDamage(0.33).elementalWeakness(1.50)
			.demonbaneEffectiveness(1.15).magicDefenceFromDefenceLevel()
			.prefers("fire spells and demonbane bypass its 67% damage cut",
				GearNeed.MAGIC, GearNeed.MELEE), 1)));

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
				GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC).size(3), 1),
			new Encounter(new MonsterProfile(
				"Olm right claw (mage hand)", 600, 175, 87, 200, 200, 200, 50, 200, true, true,
				GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC).size(3), 1),
			new Encounter(new MonsterProfile(
				"Olm head", 800, 150, 250, 200, 200, 200, 200, 50, true, true,
				GearNeed.MELEE, GearNeed.RANGED, GearNeed.MAGIC).size(3), 1)));
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
