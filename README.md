# SoulSort (Forge)

MinecraftForge 1.21.11 (Forge 61.x) port of SoulSort. Same idea as the Fabric build:
when you recover items after dying, they go back to the exact inventory slots,
armor, and off-hand they were in at the moment of death, instead of scattering
into whatever slots happen to be free.

## Why this exists

Playing with keepInventory off, death drops your items on the ground and picking
them back up reshuffles everything - armor doesn't re-equip, your hotbar is a mess,
and if someone else loots the same spot your stuff mixes with theirs. That cleanup
isn't part of the challenge, it's just busywork after the fact. SoulSort changes
only what happens once you successfully recover an item: it returns to where it was.

## How it works

All the logic that moves items is server-side and rides vanilla classes, so this
port shares its core (`RestoreManager`) byte-for-byte with the Fabric version - only
the event hooks differ:

- **Death snapshot** - `LivingDeathEvent` fires before drops, so we record the
  hotbar, main inventory, armor and off-hand layout there.
- **Ground pickup** - `PlayerEvent.ItemPickupEvent` runs after an item is added to
  the inventory; that's when we slot it back to its remembered place.
- **Grave/chest mods** - Forge has no per-click container event, so while a non-
  inventory menu is open and you've opted in (`/soulsort container on`, default on),
  we re-check each player tick. It's a cheap no-op when nothing is pending.

Only one death is ever being restored at a time: the first pile you touch becomes
active and other un-recovered deaths are dropped (their items still return, just
wherever vanilla puts them). This keeps repeated deaths from scrambling each other.

Unlike the Fabric build there's no custom networking for "is the mod on this
server" - Forge's own mod-list handling covers that. `mods.toml` sets
`displayTest="IGNORE_ALL_VERSION"` so a client with SoulSort can still join servers
without it (and vice-versa); restore just does nothing where the mod is absent.

## Commands

Any player may run these; the server config decides what actually sticks.

```
/soulsort on | off            master switch
/soulsort armor on | off      auto re-equip armor
/soulsort offhand on | off    auto re-equip off-hand
/soulsort container on | off  also restore items taken from a chest/grave
/soulsort expire <seconds>    how long an un-recovered death is remembered (server caps it)
/soulsort <ids> on | off      stop/resume sorting specific items, comma separated
/soulsort list                show current settings
/soulsort list on all         re-enable sorting for every excluded item
```

Item ids without a namespace default to `minecraft:`. Invalid ids are rejected with
the bad id named, rather than silently ignored. Player-facing text is Chinese or
English based on the language the client reported when it connected.

## Configuration

`config/soulsort_players.json` holds per-player preferences (saved automatically).
`config/soulsort.json` is server policy - `playerOverrideAllowed=true` (default) lets
players manage themselves; set it false and opt features back in via the `lock*`
flags to pin armor/offhand/items/master to server-chosen values.

## Building

Needs JDK 21 installed locally (Minecraft 1.21.11's requirement). Use the bundled
wrapper, NOT a system-wide `gradle`, to avoid Gradle-version mismatch errors:

```
gradlew.bat build      # Windows
./gradlew build         # macOS / Linux
```

Output jar lands in `build/libs/` - drop it in the server or client `mods/` folder.
