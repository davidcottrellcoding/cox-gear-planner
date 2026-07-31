# CoX Gear Planner (RuneLite plugin)

Suggests a Chambers of Xeric gear setup from the gear you actually own — personal
bank, inventory, worn equipment, **and Group Ironman shared storage** — based on
the rooms in your raid layout.

## How it works

1. **Sync your items.** The plugin passively records what it sees. Open your bank
   once and (for GIM) your group storage once; inventory and equipment are always
   tracked. Bank/group contents are remembered between sessions (toggleable).
2. **Pick your layout.** Open the "CoX Gear Planner" side panel and tick the rooms
   in your raid (Great Olm is pre-ticked since every raid ends there). You can also
   press **Import layout from clipboard** — copy any scout text that names the
   rooms (scouting Discord posts, etc.) and it ticks the matching boxes.
3. **Suggest gear setup.** The planner works out which combat styles and utility
   items the layout demands, then picks the best item you own for every slot,
   and estimates expected kill time per room using real DPS math:
   - Melee for Tekton / Guardians / Vanguards, ranged for Vespula / Shamans /
     Muttadiles / Vasa / Mystics / Olm, magic for Ice Demon / Vanguards / Olm.
   - Utilities: pickaxe (Guardians), axe + fire-spell staff (Ice Demon), lockpick
     (Thieving), Dragon warhammer / Elder maul / BGS (Tekton and Olm).
   - Two-handed picks (scythe, bows, Shadow) suppress the shield slot.
   Each line is colour-coded by where the item is: **on you**, **bank**,
   **group storage** (blue), or **missing** (red, with the best item to chase).

## Room time estimates (v1.1)

Below the loadout, the panel shows **estimated room times**: for every selected
combat room, each weapon you own is evaluated with the standard OSRS DPS
formulas (accuracy roll vs the monster's per-style defence, max hit from your
strength/ranged/magic bonuses, attack speed) and the fastest weapon per room is
reported with an expected kill time, plus a total.

- **Your live stats are used.** Boosted levels are read from the client when
  you're logged in; otherwise maxed stats are assumed. An overload (+) boost
  and Piety/Rigour/Augury are assumed by default (both toggleable in config).
- **Item stats come from the client's own database** (`ItemManager.getItemStats`),
  so armour/weapon bonuses are always current — no hardcoded stat tables.
- **Special weapon behaviour is modelled**: twisted bow scaling from the
  target's magic level (with the CoX 350 cap — it correctly dominates at Olm
  and Vasa), dragon hunter crossbow's dragonbane bonus vs Olm, scythe's
  triple hit on large monsters, fang's double accuracy roll, powered-staff
  built-in spells (Shadow's bonus tripling, sang/tridents, harmonised).
- **Party scaling**: monster HP scales with the configured party size.
- Monster stats per room live in `RoomMonsters.java` as editable data — some
  values are approximate; expect times to be indicative, not exact. Expected
  TTK ignores movement, mechanics, phases and spec weapons (DWH/BGS specs are
  listed as utility items but not simulated), so treat it as a ranking tool:
  which of *your* weapons is fastest where, and roughly how long rooms take.

Item preference order per slot lives in `GearDatabase.java` as a plain
best-first list of `(name, item ids)` — edit it to taste. A few niche item ids
(ornament variants, newer rings) are worth spot-checking against the wiki if a
suggestion looks off; each is a one-line fix in that file.

## Switch advice (v1.2)

When a layout needs more than one combat style, the panel prices every armour
switch: the style with the most estimated combat time becomes your **base
outfit**, and for each other style's armour piece the planner computes how many
seconds carrying it actually saves across the rooms where that style is used —
versus just leaving the base outfit's piece on. Switches saving less than the
**Minimum switch value** config setting (default 3 seconds, 0 to show
everything as worth carrying) are flagged **Skip**, with what to wear instead
and a summary of how many inventory slots you free for how little time lost.
Items shared between styles (e.g. barrows gloves in two loadouts) are shown as
"already worn". Weapons are never flagged — the weapon is the style switch.

## Full raid loadout (v1.3)

The top of the results is now a single concrete layout for the whole run:

- **Wear** — the full kit for your primary style (the one with the most
  estimated combat time), including weapon and ammo.
- **Inventory** — only what actually earns its slot: the other styles'
  weapons (plus their ammo), armour switches that pass the minimum-switch-value
  threshold, and the utility items your rooms demand — deduped, colour-coded
  by where each item currently is, with a count of how many of the 28 slots
  remain free for brews/restores/food.

The per-style sections below it are reference (what each style would ideally
wear), not a packing list. v1.3 also refreshes the item database with
2023–2025 gear: Amulet of rancour, Oathplate helm/chest/legs (incl. radiant),
Avernic treads, Confliction gauntlets, Dizana's quiver, Soulreaper axe,
Noxious halberd and Amulet of blood fury.

## Explicit 11 + 28 layout and debug panel (v1.6)

The plan is now stated as a real loadout you can actually pack:

- **EQUIPPED (n/11 slots)** lists all eleven worn slots every time, including
  `(empty)` ones and the shield slot suppressed by a two-hander.
- **INVENTORY (n/28 used by gear)** numbers each item that occupies a slot,
  and warns in red if the gear alone can't fit in 28.

**Switch selection was rewritten.** It used to price each armour piece
independently against a *fully* switched loadout, which measured every piece
at the point of smallest marginal value — so a whole melee set could look
marginal and get dropped piece by piece, leaving an incoherent partial switch
(a weapon, gloves and tassets but no helm or body). It now uses greedy forward
selection: starting from "wear the base outfit and just swap the weapon", it
repeatedly adds whichever remaining piece saves the most time, stopping when
the best remaining piece is worth less than the **Minimum switch value**. The
result is a coherent set — the high-value pieces are carried and only the
genuinely marginal tail is skipped.

Enable **Show debug panel** in config to see the reasoning:

- *weapon choice per room* — winner and its DPS, plus the runner-up
- *switch decisions* — what each piece saved when it was added, versus the threshold
- *best owned item per slot* — the chosen item, what it beat, and both scores

## Every item you own is considered (v1.5)

Armour is no longer chosen by matching a hand-written list of item ids. The
planner scans **every item in your bank, inventory, equipment and group
storage**, asks the client what slot it occupies and what its real stats are
(`ItemStats.isEquipable` / `ItemEquipmentStats.getSlot`), and picks the
best-scoring item per slot for each style. New, obscure or recently-released
gear works with no code change — if it's equipable and has offensive stats,
it competes.

Two things still use the curated `GearDatabase` list:

- **Weapons and ammo**, because weapon choice needs special-case knowledge
  (twisted bow scaling, DHCB dragonbane, scythe triple hits, fang's double
  roll, powered staves). Any owned weapon *not* in the list is still added as
  a candidate and evaluated with the generic formulas, so nothing is excluded.
- **"BiS to chase"** text for slots where you own nothing at all.

Item ranking uses a strength-weighted score of the real stats; the reported
room times always come from the full DPS formulas. Set effects other than
crystal armour + bow of faerdhinen (e.g. void, obsidian, inquisitor's crush
bonus) aren't credited in that ranking.

## Comprehensive gear database (v1.4)

Every slot in every style now has mid → high tier options, so the planner can
always find *something* you own: crystal armour (all recolours), god/blessed
d'hide, barrows pieces **in any degrade state**, bloodbark, infinity, obsidian,
inquisitor's, blood moon, Elidinis' ward, mage's book, odium ward, spiked
manacles, guardian boots, Ring of shadows, fremennik helms, ornamented
variants of the zenyte jewellery, dragon/diamond/ruby bolts (e) and more.

The **crystal armour set effect with the bow of faerdhinen is modelled**
(helm 5% acc / 2.5% dmg, body 15% / 7.5%, legs 10% / 5%), so a bowfa +
full crystal setup is ranked with its real +30% accuracy / +15% damage —
crystal armour beats black d'hide by a mile for bowfa users, and the room
times reflect that. Bolt proc effects (ruby/diamond) are not simulated;
bolts are ranked by their listed order.

## Running the plugin

Requires only a JDK (11+). From the project folder:

```
.\gradlew.bat runClient      (Windows)
./gradlew runClient          (Mac/Linux)
```

This launches a RuneLite developer client with the plugin loaded. Enable
**CoX Gear Planner** in the plugin list (wrench icon), then click the "CX"
sidebar icon. The first run downloads Gradle and the RuneLite client, so it
takes a few minutes; later runs are fast.

Notes on the dev client:

- Logging in with a **legacy username/password account** works directly.
- **Jagex accounts** can't type a password into a non-launcher client; see the
  RuneLite wiki page "Using Jagex Accounts" for the developer workaround.
- This client is separate from your normal installation but shares the same
  `.runelite` settings folder, so your profile/plugin settings carry over.

`gradle build` alone produces the jar in `build/libs/`, and running
`CoxGearPlannerPluginTest` from an IDE (with `-ea`) is the equivalent IDE
workflow. For everyday use inside the *normal* RuneLite client, the plugin
would need to be submitted to the RuneLite Plugin Hub — sideloading jars into
the launcher-installed client is deliberately not supported by RuneLite.

## Notes and limitations

- Automatic layout detection from inside the raid isn't implemented — reading
  CoX room layouts requires decoding instance template chunks (what the built-in
  Raids plugin does). Room selection is manual or via clipboard import, which
  covers the common "scout, then bank" flow.
- Group storage contents are only known after you open the shared bank UI once
  (containers 659/660). Untradeables can't be in group storage anyway, so your
  fire cape/torso suggestions always come from personal sources.
- The planner suggests one loadout per needed style; it doesn't dedupe items
  shared across styles (e.g. barrows gloves appearing in two sections) because
  you swap between setups mid-raid, not carry three of everything.
