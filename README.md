# CoX Gear Planner

A RuneLite plugin that plans a **Chambers of Xeric** loadout from the gear you
actually own — personal bank, inventory, worn equipment and **Group Ironman
shared storage** — using real OSRS DPS maths for the rooms in your raid.

![Raid loadout](totalgear.png)

## What it does

- **Tick your rooms** (or import scout text from the clipboard) and get one
  concrete layout: the 11 slots you wear and the items that go in your 28
  inventory slots, with the free slots left for brews and restores.
- **Every item you own is considered.** Armour is chosen by scanning your
  storage and reading each item's real stats from the client, not by matching
  a hand-written list — so new gear works without a plugin update. Uncharged,
  inactive and broken items count as owned and are flagged **CHARGE IT FIRST**.
- **Real per-room DPS**, using your live boosted stats: twisted bow scaling,
  dragonbane, demonbane, salve, crystal armour, void, Inquisitor's, powered
  staff speeds and more — see the effects tables below.
- **Switch advice**: each armour swap is priced in seconds saved across the
  rooms that use it, with a **minimum switch value** threshold and an optional
  **max items per switch** cap so you can force clean 4-way swaps.
- **Room mechanics, not just numbers**: melee can't reach the tightrope
  platforms, the Guardians take pickaxes only, Skeletal Mystics are
  safespotted, and the Great Olm is modelled as its three separate targets.
- **Debug panel** explaining every choice — which weapons were found and
  where, the full DPS ranking per room, and what each switch was worth.

## Building and running

Requires only a JDK (11+):

```
./gradlew runClient        # launches a dev client with the plugin loaded
./gradlew build            # jar in build/libs, runs the test suite
```

## Accuracy

Monster stats, scaling and item effects are sourced from the OSRS Wiki and
pinned by tests. Where the wiki is silent or self-contradictory the code says
so in a comment and the README records the caveat. Gear *ranking* is far more
trustworthy than absolute room times — errors in monster HP cancel out when
comparing two of your weapons against the same target.

---

# Development history

The sections below record what changed and why, including the bugs found and
the wiki research behind each correction.

## Ice Demon corrected (v1.18)

Research overturned three things I had assumed here.

**No fire staff is required.** The room supplies its own **tinderbox and
bronze axe**; the ice is cleared by chopping saplings for kindling and burning
it in the four braziers. Fire spells are a damage choice, not a gate — so
`FIRE_SPELLS` is no longer listed as a room requirement. An axe is still worth
bringing, since a better one yields more kindling per chop.

**The 150% fire weakness is additive, not a multiplier.** Elemental weakness
adds 1% magic damage *and* 1% magic accuracy per point, applied to the spell's
**base** max after other bonuses — so "250% damage" only holds at zero magic
damage bonus. Note this covers standard-spellbook fire spells only: a
sanguinesti staff or shadow casts its own built-in spell, which does **not**
qualify and eats the full 67% reduction.

**Demonbane weapons are competitive, and were missing entirely.** The ice
demon has **115% demonbane effectiveness**, which scales the weapon's own
bonus — Emberlight and Arclight's 70% becomes **80.5%** — and demonbane damage
is exempt from the 67% reduction. The wiki's own advice is that Emberlight
outclasses fire spells unless you are casting Fire Surge off a Harmonised
staff. Added: Emberlight, Arclight, Darklight, Silverlight, Scorching bow and
the Purging staff.

One honest caveat: that demonbane weapons *bypass* the 67% reduction is stated
on the Ice demon and CoX strategy pages but is not corroborated by the
`Demon (attribute)` mechanics page. The wiki's own Emberlight recommendation
only makes sense under that reading, which is the best available evidence,
but it is inference rather than a directly documented mechanic.

## The base outfit is chosen by total time (v1.22)

The base outfit used to be whichever style had the most combat seconds. That
was self-defeating: seconds are HP/DPS, so making a style **stronger** cut its
share of the clock and made it *less* likely to be worn. Forcing a slower
4-tick staff at Olm could flip the base outfit from melee to magic purely
because magic had been made worse — which showed up as the ring changing from
berserker to seers for no good reason.

Each style is now tried as the base outfit, its switches chosen by the same
greedy selection, and whichever produces the **lowest total raid time** wins.
That optimises the thing you actually care about and cannot be gamed by a
style being slow. With the debug panel on, the totals for all three candidates
are listed with the chosen one marked.

## Room style constraints (v1.16)

DPS alone can't tell you that a target is across a gap, or that you'd be
giving up a safespot. Each room now carries the mechanical reality as well as
the numbers, and the room line shows the reason in brackets.

**Hard constraints** — the style is removed from the room entirely:

| Room | Constraint |
|---|---|
| Tightrope (Deathly ranger/mage) | Ranged or magic only — they stand on platforms across the gap |

| Guardians | **Pickaxe only** — the restriction binds the weapon, not just the style, so no other melee weapon is ever suggested |

**Mechanical preferences** — the favoured style wins even when another rates
higher on raw DPS, but falls back gracefully if you own no weapon for it, so
you still get a plan rather than "infeasible":

| Room | Prefers | Why |
|---|---|---|
| Skeletal Mystics | Ranged / magic | Safespotted from behind the pillars |
| Muttadile (large) | Ranged / magic | Emerges when the small one dies; its stomp hits everyone in melee range |
| Ice Demon | Magic / melee | Fire spells and demonbane weapons both bypass its 67% damage cut |
| Vasa Nistirio | Ranged | Teleports around the room |
| Vespula | Ranged | Flies until downed |
| Tekton | Melee | Fought at the anvil, no safespot |

Olm and the Vanguards are deliberately left unconstrained — both rooms are
designed to demand all three styles across their separate targets.

## Per-style sections now match the inventory (v1.14)

The Melee / Ranged / Magic sections used to show each style's *ideal* loadout
— the best item you own for every slot — regardless of whether that item was
actually being brought. So the magic section could tell you to wear an
ancestral hat that the switch advisor had already decided wasn't worth an
inventory slot. Two sections of the same panel disagreed about what you'd be
wearing.

They are now derived from the loadout instead of computed independently, and
describe **what you will actually be wearing once you've swapped**:

- **One section per weapon, not per style.** A style can win different rooms
  with different weapons — magic might use a 3-tick eye of ayak on the
  tightrope but a 4-tick staff at Olm. A single section per style showed only
  the busiest weapon, leaving the other sitting in the inventory with nothing
  explaining it. Sections are now titled e.g. "Magic with Eye of ayak —
  while fighting Tightrope".
- Slots you swap show the item plus **SWAP IN**
- Slots you don't show the base outfit's item plus **stays on**
- Two-handed weapons still blank the shield slot, and empty slots say so

By construction every item named in these sections is either in the EQUIPPED
list or the INVENTORY list above — there is a test (`styleSectionsOnlyName
GearYouAreActuallyBringing`) that fails the build if a section ever names
gear that isn't being brought, and that a skipped switch is never described
as worn.

## Two colours, two questions (v1.26)

The equipped and inventory lists used one colour for everything, which meant
"what is this for?" and "where is it right now?" were competing for the same
signal. They are now separate:

- **The item name** is coloured by what it is for — melee red, ranged green,
  magic blue, utility grey. At a glance the inventory shows how your swaps
  split between styles.
- **A bracketed tag** after it says where the item currently is, in a
  deliberately different palette: `[worn]` gold, `[inventory]` orange,
  `[bank]` grey, `[group storage]` purple. Missing items stay red.

The legend below the plan spells out both scales.

## Vasa's crystal is a real target (v1.25)

v1.24 bolted a hardcoded "bring a stab weapon" list onto the Vasa room, which
meant it would add a fang whether or not you already owned something better.
The crystal is now modelled as an actual target with its wiki stats, and the
DPS maths picks the weapon:

| | HP | Def | Stab | Slash | Crush | Notes |
|---|---|---|---|---|---|---|
| Glowing crystal | 120 | 100 | **-5** | +180 | +180 | Immune to ranged, 1/3 from magic, 4x4 |

Because it is a target rather than a checklist entry, the planner compares
every melee weapon you own against it. **If your existing melee weapon already
stabs well enough it is reused and costs no extra slot**; a separate stab
weapon is only carried when it genuinely wins. Nothing is added on principle.

Room lines now also name the **attack style**, not just the weapon — "use a
fang" is only half the instruction when the target has -5 stab against +180
slash. You will see e.g. `Vasa Nistirio — Glowing crystal: 0:14 — Osmumten's
fang (melee, stab style)`, and the per-weapon section names the target it is
for.

## Vasa needs a stab weapon (v1.24, superseded)

Vasa's room asks for two different things and the planner only saw one. Vasa
himself is a ranged target — his ranged defence is 40 against magic's 400 —
but the **glowing crystals** he siphons from are **immune to ranged**, take
**66% less from magic**, and resist crush and slash. Stab is the only real
option, and the ranged setup that kills Vasa cannot touch them. Miss the
~40 second window and he heals back everything he siphoned.

A stab weapon is now a listed requirement for the room, alongside the pickaxe
for Guardians and the lockpick for Thieving. It is a utility rather than a
combat style, so if your melee weapon already stabs — fang, rapier, hasta,
dragon hunter lance — the loadout dedupes it and it costs no extra slot.

## One shared swap budget (v1.23)

**Total swap items (all styles)** replaces per-style thinking with a single
inventory allowance. Set it to 10 and the planner carries exactly ten swap
items, spending each one wherever it saves the most time — which may land on
eight items for one style and two for another. A per-style cap cannot move a
slot from a style that barely benefits to one that does; this can.

- Allocation is by **time saved**, not fairness. An uneven split is the point.
- **Weapons and their ammo count** against the allowance, since they occupy
  inventory too. An offhand rides free with its weapon.
- Items the budget could not afford are listed as **"Over limit"** with what
  they were worth, so you can see the cost of the constraint.
- **0 turns it off** and falls back to the per-style cap below.

With the debug panel on, you get the running allocation: how much went to
weapons, and each purchase in order with what it bought and what was left.

## Max items per switch (v1.13)

**Minimum switch value** answers "is this piece worth carrying?" one item at a
time, which can still leave you with an awkward 7- or 8-item swap. The new
**Max items per switch** setting is a hard cap on the size of every swap:
set it to 4 and each secondary style is a 4-way switch, no more.

- The **weapon counts** toward the total, as does ammo the base outfit isn't
  already wearing — a "4-way switch" is four clicks, so a bow plus its arrows
  leaves room for two armour pieces.
- **An offhand rides along with the weapon and is free.** A fang plus a dragon
  defender is one swap, not two, so a 4-item cap still allows three real
  armour switches beside it. The shield is still priced for value; it just
  doesn't consume cap budget.
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
| Crystal armour | crystal bow / bofa only | helm 5%/2.5%, body 15%/7.5%, legs 10%/5% — **per piece, no full-set requirement**, so a partial set is a partial bonus |
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
