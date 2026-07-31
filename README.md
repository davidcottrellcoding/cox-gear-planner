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
   items the layout demands, then picks the best item you own for every slot:
   - Melee for Tekton / Guardians / Vanguards, ranged for Vespula / Shamans /
     Muttadiles / Vasa / Mystics / Olm, magic for Ice Demon / Vanguards / Olm.
   - Utilities: pickaxe (Guardians), axe + fire-spell staff (Ice Demon), lockpick
     (Thieving), Dragon warhammer / Elder maul / BGS (Tekton and Olm).
   - Two-handed picks (scythe, bows, Shadow) suppress the shield slot.
   Each line is colour-coded by where the item is: **on you**, **bank**,
   **group storage** (blue), or **missing** (red, with the best item to chase).

Item preference order per slot lives in `GearDatabase.java` as a plain
best-first list of `(name, item ids)` — edit it to taste. A few niche item ids
(ornament variants, newer rings) are worth spot-checking against the wiki if a
suggestion looks off; each is a one-line fix in that file.

## Building

Requires JDK 11+ (any recent JDK works).

```
gradle build
```

The built jar is in `build/libs/`. To run a dev client with the plugin loaded,
run `CoxGearPlannerPluginTest` from your IDE (standard RuneLite external plugin
workflow), then enable **CoX Gear Planner** in the plugin list.

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
