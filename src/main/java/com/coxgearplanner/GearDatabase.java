package com.coxgearplanner;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiered item tables per combat style and utility need. Options are listed
 * best-first; the planner picks the first option you own somewhere.
 *
 * Item ids are written as raw numbers (with the human name alongside) so a
 * wrong id is a one-line data fix. Multiple ids on one option cover
 * charged/uncharged or ornamented variants.
 */
public final class GearDatabase
{
	private static final Map<GearNeed, Map<GearSlot, List<ItemOption>>> LOADOUTS = new EnumMap<>(GearNeed.class);
	private static final Map<GearNeed, List<ItemOption>> UTILITIES = new EnumMap<>(GearNeed.class);

	private GearDatabase()
	{
	}

	static
	{
		Map<GearSlot, List<ItemOption>> melee = new LinkedHashMap<>();
		melee.put(GearSlot.WEAPON, Arrays.asList(
			ItemOption.twoHanded("Scythe of vitur", 22325, 25736, 25739), // + holy/sanguine
			ItemOption.of("Osmumten's fang", 26219),
			ItemOption.twoHanded("Soulreaper axe", 28338, 33335), // + ornament
			ItemOption.twoHanded("Noxious halberd", 29796),
			ItemOption.of("Ghrazi rapier", 22324),
			ItemOption.of("Blade of saeldor (c)", 24553),
			ItemOption.of("Inquisitor's mace", 24417),
			ItemOption.of("Abyssal tentacle", 12006),
			ItemOption.of("Zamorakian hasta", 11889),
			ItemOption.of("Abyssal whip", 4151),
			ItemOption.of("Dragon scimitar", 4587)));
		melee.put(GearSlot.SHIELD, Arrays.asList(
			ItemOption.of("Avernic defender", 22322),
			ItemOption.of("Dragon defender", 12954),
			ItemOption.of("Dragonfire shield", 11284),
			ItemOption.of("Rune defender", 8850)));
		melee.put(GearSlot.HEAD, Arrays.asList(
			ItemOption.of("Oathplate helm", 30750, 30777), // + radiant
			ItemOption.of("Torva full helm", 26382),
			ItemOption.of("Neitiznot faceguard", 24271),
			ItemOption.of("Serpentine helm", 12931, 12929),
			ItemOption.of("Helm of neitiznot", 10828)));
		melee.put(GearSlot.CAPE, Arrays.asList(
			ItemOption.of("Infernal cape", 21295),
			ItemOption.of("Fire cape", 6570),
			ItemOption.of("Obsidian cape", 6568)));
		melee.put(GearSlot.NECK, Arrays.asList(
			ItemOption.of("Amulet of rancour", 29801, 29804), // + (s)
			ItemOption.of("Amulet of torture", 19553),
			ItemOption.of("Amulet of blood fury", 24780),
			ItemOption.of("Amulet of fury", 6585),
			ItemOption.of("Amulet of glory", 1704)));
		melee.put(GearSlot.BODY, Arrays.asList(
			ItemOption.of("Oathplate chest", 30753, 30779), // + radiant
			ItemOption.of("Torva platebody", 26384),
			ItemOption.of("Bandos chestplate", 11832),
			ItemOption.of("Fighter torso", 10551)));
		melee.put(GearSlot.LEGS, Arrays.asList(
			ItemOption.of("Oathplate legs", 30756, 30781), // + radiant
			ItemOption.of("Torva platelegs", 26386),
			ItemOption.of("Bandos tassets", 11834),
			ItemOption.of("Obsidian platelegs", 21301),
			ItemOption.of("Dragon platelegs", 4087)));
		melee.put(GearSlot.GLOVES, Arrays.asList(
			ItemOption.of("Ferocious gloves", 22981),
			ItemOption.of("Barrows gloves", 7462),
			ItemOption.of("Dragon gloves", 7461)));
		melee.put(GearSlot.BOOTS, Arrays.asList(
			ItemOption.of("Avernic treads", 31097, 31091, 31094, 31095), // melee-capable variants
			ItemOption.of("Primordial boots", 13239),
			ItemOption.of("Dragon boots", 11840)));
		melee.put(GearSlot.RING, Arrays.asList(
			ItemOption.of("Ultor ring", 28307),
			ItemOption.of("Berserker ring (i)", 11773),
			ItemOption.of("Brimstone ring", 22975),
			ItemOption.of("Berserker ring", 6737)));
		LOADOUTS.put(GearNeed.MELEE, Collections.unmodifiableMap(melee));

		Map<GearSlot, List<ItemOption>> ranged = new LinkedHashMap<>();
		ranged.put(GearSlot.WEAPON, Arrays.asList(
			ItemOption.twoHanded("Twisted bow", 20997),
			ItemOption.twoHanded("Bow of faerdhinen (c)", 25865, 25867),
			ItemOption.of("Zaryte crossbow", 26374),
			ItemOption.of("Dragon hunter crossbow", 21012), // strong vs the draconic Great Olm
			ItemOption.of("Toxic blowpipe", 12926),
			ItemOption.of("Armadyl crossbow", 11785),
			ItemOption.of("Dragon crossbow", 21902),
			ItemOption.of("Rune crossbow", 9185),
			ItemOption.twoHanded("Magic shortbow (i)", 12788)));
		ranged.put(GearSlot.SHIELD, Arrays.asList(
			ItemOption.of("Twisted buckler", 21000),
			ItemOption.of("Dragonfire ward", 22003),
			ItemOption.of("Book of law", 12610),
			ItemOption.of("Unholy book", 3842)));
		ranged.put(GearSlot.HEAD, Arrays.asList(
			ItemOption.of("Masori mask (f)", 27235),
			ItemOption.of("Masori mask", 27226),
			ItemOption.of("Armadyl helmet", 11826),
			ItemOption.of("Karil's coif", 4732)));
		ranged.put(GearSlot.CAPE, Arrays.asList(
			ItemOption.of("Dizana's quiver", 28951, 28953, 28955, 28957), // charged/trouver/infinite
			ItemOption.of("Ava's assembler", 22109),
			ItemOption.of("Ava's accumulator", 10499)));
		ranged.put(GearSlot.NECK, Arrays.asList(
			ItemOption.of("Necklace of anguish", 19547),
			ItemOption.of("Amulet of fury", 6585),
			ItemOption.of("Amulet of glory", 1704)));
		ranged.put(GearSlot.AMMO, Arrays.asList(
			ItemOption.of("Dragon arrow", 11212),
			ItemOption.of("Amethyst arrow", 21326),
			ItemOption.of("Rune arrow", 892),
			ItemOption.of("Diamond bolts (e)", 9243),
			ItemOption.of("Ruby bolts (e)", 9242)));
		ranged.put(GearSlot.BODY, Arrays.asList(
			ItemOption.of("Masori body (f)", 27238),
			ItemOption.of("Armadyl chestplate", 11828),
			ItemOption.of("Karil's leathertop", 4736),
			ItemOption.of("Black d'hide body", 2503)));
		ranged.put(GearSlot.LEGS, Arrays.asList(
			ItemOption.of("Masori chaps (f)", 27241),
			ItemOption.of("Armadyl chainskirt", 11830),
			ItemOption.of("Karil's leatherskirt", 4738),
			ItemOption.of("Black d'hide chaps", 2497)));
		ranged.put(GearSlot.GLOVES, Arrays.asList(
			ItemOption.of("Zaryte vambraces", 26235),
			ItemOption.of("Barrows gloves", 7462),
			ItemOption.of("Black d'hide vambraces", 2491)));
		ranged.put(GearSlot.BOOTS, Arrays.asList(
			ItemOption.of("Avernic treads", 31097, 31092, 31094, 31096), // ranged-capable variants
			ItemOption.of("Pegasian boots", 13237),
			ItemOption.of("Ranger boots", 2577),
			ItemOption.of("Snakeskin boots", 6328)));
		ranged.put(GearSlot.RING, Arrays.asList(
			ItemOption.of("Venator ring", 28310),
			ItemOption.of("Archers ring (i)", 11771),
			ItemOption.of("Archers ring", 6733)));
		LOADOUTS.put(GearNeed.RANGED, Collections.unmodifiableMap(ranged));

		Map<GearSlot, List<ItemOption>> magic = new LinkedHashMap<>();
		magic.put(GearSlot.WEAPON, Arrays.asList(
			ItemOption.twoHanded("Tumeken's shadow", 27275),
			ItemOption.of("Sanguinesti staff", 22323, 25731), // + holy
			ItemOption.twoHanded("Harmonised nightmare staff", 24423),
			ItemOption.of("Kodai wand", 21006),
			ItemOption.of("Trident of the swamp", 12899),
			ItemOption.of("Trident of the seas", 11905)));
		magic.put(GearSlot.SHIELD, Arrays.asList(
			ItemOption.of("Arcane spirit shield", 12825),
			ItemOption.of("Book of darkness", 12612),
			ItemOption.of("Malediction ward", 11924)));
		magic.put(GearSlot.HEAD, Arrays.asList(
			ItemOption.of("Ancestral hat", 21018),
			ItemOption.of("Virtus mask", 26241),
			ItemOption.of("Ahrim's hood", 4708)));
		magic.put(GearSlot.CAPE, Arrays.asList(
			ItemOption.of("Imbued saradomin cape", 21791),
			ItemOption.of("Imbued guthix cape", 21793),
			ItemOption.of("Imbued zamorak cape", 21795),
			ItemOption.of("Saradomin cape", 2412),
			ItemOption.of("Guthix cape", 2413),
			ItemOption.of("Zamorak cape", 2414)));
		magic.put(GearSlot.NECK, Arrays.asList(
			ItemOption.of("Occult necklace", 12002),
			ItemOption.of("Amulet of fury", 6585),
			ItemOption.of("Amulet of glory", 1704)));
		magic.put(GearSlot.BODY, Arrays.asList(
			ItemOption.of("Ancestral robe top", 21021),
			ItemOption.of("Virtus robe top", 26243),
			ItemOption.of("Ahrim's robetop", 4712),
			ItemOption.of("Mystic robe top", 4091)));
		magic.put(GearSlot.LEGS, Arrays.asList(
			ItemOption.of("Ancestral robe bottom", 21024),
			ItemOption.of("Virtus robe bottom", 26245),
			ItemOption.of("Ahrim's robeskirt", 4714),
			ItemOption.of("Mystic robe bottom", 4093)));
		magic.put(GearSlot.GLOVES, Arrays.asList(
			ItemOption.of("Confliction gauntlets", 31106),
			ItemOption.of("Tormented bracelet", 19544),
			ItemOption.of("Barrows gloves", 7462)));
		magic.put(GearSlot.BOOTS, Arrays.asList(
			ItemOption.of("Avernic treads", 31097, 31093, 31095, 31096), // magic-capable variants
			ItemOption.of("Eternal boots", 13235),
			ItemOption.of("Infinity boots", 6920)));
		magic.put(GearSlot.RING, Arrays.asList(
			ItemOption.of("Magus ring", 28313),
			ItemOption.of("Seers ring (i)", 11770),
			ItemOption.of("Seers ring", 6731)));
		LOADOUTS.put(GearNeed.MAGIC, Collections.unmodifiableMap(magic));

		UTILITIES.put(GearNeed.PICKAXE, Arrays.asList(
			ItemOption.of("Crystal pickaxe", 23680),
			ItemOption.of("Dragon pickaxe", 11920),
			ItemOption.of("Rune pickaxe", 1275)));
		UTILITIES.put(GearNeed.AXE, Arrays.asList(
			ItemOption.of("Crystal axe", 23673),
			ItemOption.of("Dragon axe", 6739),
			ItemOption.of("Rune axe", 1359)));
		UTILITIES.put(GearNeed.LOCKPICK, Collections.singletonList(
			ItemOption.of("Lockpick", 1523)));
		UTILITIES.put(GearNeed.FIRE_SPELLS, Arrays.asList(
			ItemOption.of("Smoke battlestaff", 11998),
			ItemOption.of("Fire battlestaff", 1387),
			ItemOption.of("Staff of fire", 1401)));
		UTILITIES.put(GearNeed.DEF_REDUCTION, Arrays.asList(
			ItemOption.of("Dragon warhammer", 13576),
			ItemOption.of("Elder maul", 21003),
			ItemOption.of("Bandos godsword", 11804)));
	}

	public static Map<GearSlot, List<ItemOption>> loadout(GearNeed style)
	{
		return LOADOUTS.get(style);
	}

	public static List<ItemOption> utility(GearNeed need)
	{
		return UTILITIES.get(need);
	}
}
