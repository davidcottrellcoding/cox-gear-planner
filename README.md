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

## Per-style sections now match the inventory (v1.14)

The Melee / Ranged / Magic sections used to show each style's *ideal* loadout
— the best item you own for every slot — regardless of whether that item was
actually being brought. So the magic section could tell you to wear an
ancestral hat that the switch advisor had already decided wasn't worth an
inventory slot. Two sections of the same panel disagreed about what you'd be
wearing.

They are now derived from the loadout instead of computed independently, and
describe **what you will actually be wearing once you've swapped**:

- Slots you swap show the item plus **SWAP IN**
- Slots you don't show the base outfit's item plus **stays on**
- Two-handed weapons still blank the shield slot, and empty slots say so

By construction every item named in these sections is either in the EQUIPPED
list or the INVENTORY list above — there is a test (`styleSectionsOnlyName
GearYouAreActuallyBringing`) that fails the build if a section ever names
gear that isn't being brought, and that a skipped switch is never described
as worn.

## Max items per switch (v1.13)

**Minimum switch value** answers "is this piece worth carrying?" one item at a
time, which can still leave you with an awkward 7- or 8-item swap. The new
**Max items per switch** setting is a hard cap on the size of every swap:
set it to 4 and each secondary style is a 4-way switch, no more.

- The **weapon counts** toward the total, as does ammo the base outfit isn't
  already wearing — a "4-way switch" is four clicks, so a bow plus its arrows
  leaves room for two armour pieces.
- Pieces are still chosen by greedy value order, so the ones kept are the
  most valuable that fit — the cap trims the tail, not the head.
- Pieces cut by the cap (rather than by low value) are listed separately as
  **"Over limit"** with what they were worth, so you can see the cost of the
  constraint and raise it if a piece was worth more than you thought.
- **0 means no limit** (the default), leaving the value threshold in charge.

Both settings apply together: a piece must clear the **minimum switch value**
*and* fit within the **max items per switch** to be carried.

## Set bonuses, effects and interactions (v1.10 / v1.11)

Anything whose value is a *multiplier* rather than an equipment stat is
invisible to the stats-based picker, so each one has to be modelled by hand.
All figures below were verified against the OSRS wiki.

**Weapon effects**

| Effect | Applies to | Modelled as |
|---|---|---|
| Twisted bow scaling | target magic level, CoX cap 350 | accuracy + damage curve |
| Dragon hunter crossbow | draconic (all Olm parts) | 1.30 acc / 1.25 dmg |
| **Dragon hunter lance** | draconic (all Olm parts) | 1.20 acc / 1.20 dmg |
| Scythe of vitur | large targets | 1.75× average damage (3 hits) |
| Osmumten's fang | all | double accuracy roll |
| **Eye of ayak** | — | `⌊Magic/3⌋−6` at **3 ticks** (fastest magic weapon) |
| Tumeken's shadow | — | `⌊Magic/3⌋+1` at 5 ticks, triples magic bonuses (dmg capped 100%) |
| Sanguinesti / tridents / harmonised | — | own formulas and speeds |

**Per-piece effects**

| Effect | Applies to | Modelled as |
|---|---|---|
| Crystal armour | crystal bow / bofa only | helm 5%/2.5%, body 15%/7.5%, legs 10%/5% |
| Salve amulet | undead (Skeletal Mystics) | 15%/20%, imbued extends to ranged and magic |
| **Inquisitor's** | **crush style only** | helm 0.5%, hauberk 1%, skirt 1% |
| **Tome of fire** | standard fire spells, **not** powered staves | **+10% vs NPCs** (the quoted 50% is PvP) |

**Complete-set effects** (new machinery — these pay out only when every piece
is worn, and the planner picks slots independently, so it would never
assemble one by accident. Each owned set is now evaluated as a whole
alternative loadout and kept only when it is genuinely faster.)

| Set | Effect |
|---|---|
| Void (melee) | 1.10 accuracy and damage |
| Void (ranged) | 1.10 accuracy, 1.10 damage — elite 1.125 damage |
| Void (magic) | 1.45 accuracy, no damage — elite 1.05 damage |
| Obsidian armour | 1.10 accuracy and damage, **only with an obsidian weapon** |

The void helm decides which style the set boosts, and all four of
helm/top/robe/gloves are required.

**Not modelled**, and why: bolt proc effects (ruby/diamond) need a
current-HP simulation; special attacks including DWH/BGS/elder maul defence
reduction; Olm's head 66% non-ranged mitigation (see above); demon-bane
weapons such as arclight and emberlight (magic dominates the Ice Demon room
regardless, since its magic defence is 60 against 200 melee).

## Uncharged and broken items count as owned (v1.9)

An uncharged scythe is still a scythe — you just charge it before the raid.
Previously the stats-based picker scored the uncharged item on its
(near-worthless) uncharged bonuses and discarded it, so a bank full of
uncharged gear looked like a bank full of nothing.

Container snapshots now collapse uncharged / inactive / broken / empty /
degraded forms onto the charged item, right after the noted-and-placeholder
canonicalisation. The table is **generated** from runelite-api's gameval
`ItemID` constants — every name ending in `_UNCHARGED`, `_INACTIVE`,
`_BROKEN`, `_EMPTY` or `_DEGRADED` matched to its base (or `_CHARGED` /
`_LOADED` / `_FULL`) counterpart — 264 mappings, plus a handful of hand-added
ones whose names don't follow the pattern (toxic blowpipe empty→loaded, the
pre-rework crystal bow and halberd, Dizana's quiver broken variants).

Covered among others: **toxic blowpipe, Tumeken's shadow, eye of ayak, bow of
faerdhinen, blade of saeldor**, sanguinesti staff, scythe of vitur, both
tridents, venator bow, tonalztics, all crystal armour and tools (inactive),
every barrows piece (broken), void, infernal cape, Ava's assembler, avernic
defender and the dragonfire shield/ward.

Items you own *only* in uncharged form are flagged **"CHARGE IT FIRST"** in
the inventory list, so you find out at the bank rather than inside the raid.
Chains are resolved transitively with a hop limit.

This also caught three wrong ids in the curated list: Blade of saeldor was
pointing at a *dummy* item (24553), and the dragonfire shield and ward were
both pointing at their **uncharged** ids.

## The Great Olm is three targets, not one (v1.8)

Olm was previously modelled as a single 1020 HP blob with one style — which
is why it never asked you to bring a full melee kit. It is now three separate
targets with deliberately opposite defensive profiles (wiki values):

| Part | HP | Def | Magic | Stab/Slash/Crush | Magic def | Ranged def | Best style |
|---|---|---|---|---|---|---|---|
| Left claw (melee hand) | 600 | 175 | 175 | **50** | 200 | 200 | melee |
| Right claw (mage hand) | 600 | 175 | **87** | 200 | **50** | 200 | magic |
| Head | 800 | 150 | 250 | 200 | 200 | **50** | ranged |

That opposition is the whole design of the fight, and it's why Olm alone
demands all three styles. Each part is now planned, timed and gear-weighted
independently, and appears as its own line ("Great Olm — Olm head: ~1:12 —
Twisted bow (ranged)"). Because the switch advisor groups by style, the
greedy selection now sees the real melee and magic workload at Olm and
carries those switches instead of dropping them.

Other Olm specifics modelled:

- **Phases** = 4, plus one per eight players. The last is the head phase, so
  each claw is fought **three times** in a standard raid (every phase but the
  final one) and its HP pool is multiplied accordingly; the head is killed
  once.
- **Draconic** on all three parts, so the dragon hunter crossbow's bonus
  applies to the claws and head alike.
- The head's magic level of 250 is what the **twisted bow** scales from —
  combined with its ranged defence of 50, the tbow dominates the head.

Approximations worth knowing: the head also has 66% mitigation against
non-ranged damage and heals if hit outside the final phase, so in practice
its HP is taken down during the final phase where the mitigation is off —
it is therefore not applied (its ranged defence of 50 already makes ranged
correct by a wide margin). All Olm numbers live in `RoomMonsters.java` as
editable data.

## Group storage fix (v1.8.1)

Container **660 is `INV_PLAYER_TEMP`** — your *own* inventory as shown inside
the shared-storage interface — and was being read as the group's contents,
so group storage was frequently overwritten with your inventory. The shared
storage is container **659** (`INV_GROUP_TEMP`); 660 and 661 are now ignored
outright since container 93 already tracks your inventory.

Two related hardenings: an empty reading from the bank or shared storage no
longer overwrites known contents (both report empty briefly while their
interface opens and closes), and a **Forget stored bank/group data** button
clears the remembered snapshots so a bad one can be reset. The side panel
now says explicitly when group storage has never synced, or when it is
disabled in config.

## Salve amulet at Skeletal Mystics (v1.7)

Skeletal mystics are the one undead encounter in the raid, so the salve
amulet applies there. Its bonus is a damage/accuracy multiplier that does
**not** appear anywhere in the item's equipment stats, which means the
stats-based picker in v1.5 could never find it — the amulet looks weak on
paper and always lost to anguish/torture/occult.

It is now modelled explicitly:

- Skeletal mystics carry an `undead` flag (`RoomMonsters.java`).
- Salve amulet **+15%**, **(e) +20%** (melee only), **(i) +15%** and
  **(ei) +20%** (all three styles) to both accuracy and max hit vs undead.
- For undead rooms the planner tries your best owned salve as a neck swap
  against the normally-best amulet and keeps whichever is actually faster,
  so the Mystics room time reflects it and the amulet is added to the
  inventory list as "for Skeletal Mystics".

Other multiplier-style effects that don't live in item stats (void, obsidian,
inquisitor's crush bonus, bolt procs) are still not modelled.

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

- *weapons found in your storage* — per style, which known weapons were found
  (and where), which were **not found**, and how many equipable weapons were
  seen in total. This distinguishes "that weapon lost" from "that weapon was
  never a candidate because it isn't in any synced storage", which the
  per-room ranking alone cannot tell you.
- *weapon choice per room* — the **full ranked list** of every candidate with
  its DPS (top 8), winner marked with `>`
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
