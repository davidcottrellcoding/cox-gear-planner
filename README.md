# CoX Gear Planner

A RuneLite plugin that plans a **Chambers of Xeric** loadout from the gear you
actually own — personal bank, inventory, worn equipment and **Group Ironman
shared storage** — using OSRS DPS maths for the rooms in your raid.

![Raid loadout](totalgear.png)

## What it actually does

You tick the rooms in your layout (or import scout text from the clipboard)
and press **Suggest gear setup**. It then:

1. **Works out what you own.** Every item in your inventory, worn equipment,
   bank and shared storage is read through the client's own item database, so
   each item's real slot and stats are used rather than a hand-written list.
   New gear works with no plugin update. Uncharged, inactive and broken items
   count as owned and are flagged **CHARGE IT FIRST**.
2. **Estimates a time for every target** using the standard OSRS accuracy and
   max-hit formulas with your live boosted stats.
3. **Chooses a base outfit** by trying each combat style as the worn set,
   choosing its switches, and keeping whichever gives the lowest total time.
4. **Decides which switches earn their slot**, priced in seconds saved, under
   either a per-style cap or one shared budget across all styles.
5. **Prints one concrete layout**: 11 worn slots, the items in your 28-slot
   inventory, and how many slots are left for supplies.

Items are coloured by what they are for (melee red, ranged green, magic blue,
utility grey) with a separate tag for where they currently are.

### Things it knows that raw stats don't tell you

- **Multi-target rooms.** Olm is three targets with opposite defences (melee
  left claw, magic right claw, ranged head). Vanguards are three, each soft to
  one style. Muttadiles are two. Vasa is two — himself plus the crystal.
  Tightrope is the ranger and the mage.
- **Positional reality.** Melee cannot reach the tightrope platforms. The
  Guardians take damage only from pickaxes. Skeletal Mystics are safespotted.
  The abyssal portal at Vespula sits 8 tiles out, so a 10-tile bow stays on
  rapid while a 7-tile weapon must use longrange and loses a tick.
- **Effects that are not in item stats**: twisted bow scaling, dragonbane,
  demonbane, salve amulet, crystal armour, void, obsidian, Inquisitor's crush
  bonus, tome of fire, powered-staff speeds, the Guardians' pickaxe damage
  formula, and Vasa's crystal being a stab check.
- **Which attack style to use** — a fang left on slash hits the Vasa crystal's
  +180 defence instead of its -5.

## Sharing a plan

**Copy plan for a friend** puts the whole plan on your clipboard as plain text
and writes `cox-gear-plan.txt` to your `.runelite` folder. It includes the
settings and stats behind the plan, not just the item list, because a reviewer
cannot judge a loadout without knowing what constrained it.

## Settings worth knowing

| Setting | What it does |
|---|---|
| Party size | Scales monster HP. See the caveat below |
| Minimum switch value | Seconds a switch must save to be worth a slot |
| Max items per switch | Per-style cap. Weapon counts, offhand is free |
| Total swap items | One shared budget across all styles; overrides the per-style cap |
| Force 4-tick weapons at Olm | Keeps melee and magic on one rhythm while learning |
| Show debug panel | Explains every weapon, switch and slot decision |

## Building and running

Requires only a JDK (11+):

```
./gradlew runClient        # dev client with the plugin loaded
./gradlew build            # jar in build/libs, runs the test suite
```

---

# Limitations

**Read this before trusting a number.** The plugin is considerably better at
ranking gear than at predicting a run. Errors in monster HP cancel out when
comparing two of your own weapons against the same target, so "which of my
weapons is best here" survives mistakes that "this room takes 1:12" does not.

### The times are not predictions

Every time is expected kill time from DPS alone. It ignores movement, room
mechanics, phase transitions, downtime, prayer switching, eating, and special
attacks. Two rooms are especially misleading:

- **Vespula** is duty-cycle limited, not DPS limited. The Redemption method is
  attack, retreat, heal, restore prayer. The reported time is a **floor**.
- **Guardians** ignores their 1 HP per 8 ticks regeneration and the flinching
  most players do to dodge the stomp.

### Party scaling is the weakest number in the model

Jagex has never published the CoX scaling formula and the wiki does not carry
one. The linear-per-member figure used here comes from a wiki **talk page**,
not an article. Times also assume the party splits damage evenly, which is a
convenience rather than a fact. **Solo estimates are the most trustworthy.**

### Monster stats are the solo, maxed-player baseline

Every CoX infobox states its stats are "scaled for a player with maxed combat
stats", and the raid scales to the highest-combat player in the team. The
plugin uses that baseline and scales from it. **Challenge Mode is not modelled
at all.**

### Known approximations, and why

| Approximation | Why |
|---|---|
| Olm claw HP is per-phase, times three | The exact per-phase damage split is not published |
| Olm head's 66% non-ranged mitigation is not applied | The head heals if hit outside the final phase, so its HP realistically comes down during the phase where the mitigation is off |
| Demonbane bypassing the Ice Demon's 67% cut | Stated on the Ice demon and CoX strategy pages, but **not corroborated** by the `Demon (attribute)` mechanics page. The wiki's own Emberlight recommendation only makes sense under this reading |
| Muttadile meat-tree healing is not modelled | It is preventable; baking it in would overstate the room for anyone who stops them |
| Vasa crystal count is 1 | He siphons from one at a time, but may visit more than one in a fight |
| Armour is ranked by a strength-weighted score | Not a full per-monster DPS solve. Reported times always use the real formulas; only the *ordering* of candidates is heuristic |
| Weapon reach is a hand-maintained table | RuneLite's item stats do not expose attack range |

### Not modelled at all

- **Bolt proc effects** (ruby, diamond), which need a current-HP simulation
- **Special attacks.** DWH, BGS and elder maul are listed as utility items but
  their defence reduction is never simulated, so Tekton and Olm times ignore it
- **Thralls, Book of the Dead, poison, venom and burn effects**
- **Supplies.** The plan reports free slots but does not plan brews, restores
  or food
- **The berserker necklace's +20%** with obsidian weapons

### Known bugs

- **Osmumten's fang's double accuracy roll is applied to every attack style.**
  Since January 2024 it only applies on stab, so the fang is currently
  overrated on slash.
- **The scythe is treated as hitting 3 times on any "large" target.** It hits
  twice on 2x2 monsters such as Skeletal Mystics, so it is overrated there.
- **Item naming can be inconsistent** between the curated list and the item
  scanner — "Avernic treads" versus "Avernic treads (base)" can refer to
  different database entries for what looks like the same item.

### Operational limits

- **No automatic layout detection.** Reading the raid's rooms from inside CoX
  means decoding instance template chunks, as the built-in Raids plugin does.
  Room selection is manual or via clipboard import.
- **Shared storage is only known after you open the chest once.** Contents are
  remembered between sessions; use **Forget stored bank/group data** to reset.
- **Anything the client has not shown the plugin is invisible** — POH armour
  stand, looting bag, seed vault, or another account.

---

# Still to implement

The community's reference efficiency sheet optimises **points per hour**, and
its Olm tab quantifies three things this plugin does not model at all:

- **Special attacks are assumed, and they are large.** "1 maul/dwh per phase,
  and a ZCB on head phase — this is optimal usage." One elder maul on Olm's
  melee hand moves accuracy from 73.04% to 81.98%, cutting time-to-kill from
  88.0 to 77.8 ticks. That is an **11.6% swing** this plugin silently omits,
  and it is the single biggest gap in the Olm numbers.
- **Dead time dominates points per hour.** 25 ticks from the floor end to your
  first Olm attack, 80 ticks between phases, 30 ticks from the kill to being
  back in a raid. Around 135 ticks of pure overhead per raid at Olm alone,
  against a reported total time-to-kill of 741 ticks. This plugin reports zero
  overhead, so any per-hour figure derived from it would be badly optimistic.
- **Thralls are worth about 5.5%.** 0.625 DPS flat, which the sheet applies as
  a 5.45-5.89% adjustment depending on the target.

One discrepancy worth chasing: the sheet uses **703.2 HP for Olm's head**
where this plugin uses 800. That is a scaling difference worth understanding
before trusting either number.

Roughly in order of value:

1. **Validate the DPS engine against a reference.** The wiki's own calculator
   (weirdgloop/osrs-dps-calc) is open source. Encoding around 30 fixtures
   spanning styles and effects, and asserting this engine matches, would turn
   "the formulas look right" into "the formulas are right".
2. **Expected-ranking tests.** Assert community consensus directly — tbow wins
   Olm's head, magic wins the right claw, stab wins the crystal — so the build
   fails when a change breaks one, instead of a user noticing.
3. **Per-monster DPS-based armour selection**, replacing the heuristic score,
   then verifying the greedy switch choice against exhaustive search on small
   candidate sets to measure how often greedy is wrong.
4. **Fix the two known combat bugs** above (fang stab-only, scythe on 2x2).
5. **Special attack simulation** (elder maul, DWH, ZCB), quantified above at
   roughly 11.6% of Olm melee time-to-kill, plus **bolt procs**.
6. **Points per hour**, which is what the community actually optimises. Needs
   the CoX points formula plus the fixed overheads above, so that the output
   becomes points/hour rather than raw kill time.
7. **Thralls**, a flat 0.625 DPS and about a 5.5% uplift.
8. **Challenge Mode**, largely a 1.5x stat multiplier plus different tactics.
9. **Empirical validation in-game** — subscribe to hitsplat events and compare
   observed hit rate and average damage against predicted, over real raids.
   The strongest possible evidence, and the slowest to gather.

# Accuracy and sourcing

Monster stats, scaling and item effects are taken from the OSRS Wiki and
pinned by tests. Where the wiki is silent or contradicts itself, the code says
so in a comment and this file records the caveat. Item stats themselves always
come from the client, never from a hardcoded table.

The test suite covers the combat formulas, the item data, the room
constraints, and the consistency rule that **every item named in a per-style
section must be either equipped or in the inventory list** — a rule that has
caught several real bugs.

Data lives as plain editable tables: `RoomMonsters.java` for monster stats and
room mechanics, `GearDatabase.java` for the curated weapon and ammo tiers,
`ChargedVariants.java` for uncharged item mappings.

---

# Change history

Newest first. Entries marked *(superseded)* describe behaviour that has since
been replaced.

| Version | Change |
|---|---|
| 1.26 | Items coloured by combat style, with location as a separate tag |
| 1.25.2 | Swap budget counted only one weapon per style, so a budget of 10 packed 14 items |
| 1.25.1 | A weapon used in three rooms was never carried; equipped ammo slot disagreed with the sections; export omitted the active limit |
| 1.25 | Vasa's crystal became a real target with its wiki stats, replacing a hardcoded stab list *(supersedes 1.24)*; room lines now name the melee attack style |
| 1.24 | Stab weapon added as a Vasa requirement *(superseded by 1.25)* |
| 1.23 | One shared swap budget across all styles |
| 1.22.1 | The large muttadile can be meleed once it emerges |
| 1.22 | Base outfit chosen by lowest total raid time, not most combat seconds; offhand counts as part of the weapon swap |
| 1.21.1 | Dropped the axe requirement at the Ice Demon — the room spawns one |
| 1.21 | Shareable plan export; fixed version drift across three files |
| 1.20 | Vespula modelled as the 8-tile portal reach problem |
| 1.19 | Room times were party-size too long; Guardians given their real HP formula and pickaxe damage multiplier; Vespula retargeted at the portal |
| 1.18 | Ice Demon corrected — no fire staff needed, demonbane modelled |
| 1.17 | Force 4-tick weapons at Olm; style sections split per weapon |
| 1.16 | Per-room style constraints and mechanical preferences |
| 1.15 | Monster stats and party scaling re-sourced from the wiki |
| 1.14 | Per-style sections derived from the loadout so they cannot disagree with it |
| 1.13 | Max items per switch |
| 1.12 | Debug panel showing full weapon rankings and ownership |
| 1.11 | Complete-set bonuses: void and obsidian |
| 1.10 | Eye of ayak, dragon hunter lance, Inquisitor's, tome of fire |
| 1.9 | Uncharged, inactive and broken items count as owned |
| 1.8.1 | Group storage was reading the player's own inventory container |
| 1.8 | Olm modelled as three separate targets |
| 1.7 | Salve amulet at Skeletal Mystics |
| 1.6 | Explicit 11-slot and 28-slot layout; greedy switch selection |
| 1.5 | Armour chosen by scanning every owned item's real stats |
| 1.4 | Crystal armour set effect; broader gear database |
| 1.3 | Single raid loadout view |
| 1.2 | Switch advice with a minimum switch value |
| 1.1 | Room time estimates from DPS |
| 1.0 | Initial gear planner |
