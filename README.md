# SoulSort (NeoForge)

NeoForge 26.2 port of SoulSort. Same behaviour as the Fabric and Forge builds:
recovered items return to the exact inventory slots, armor, and off-hand they were
in at the moment of death, instead of scattering into whatever slots are free.

## Why this exists

With keepInventory off, dying drops your items and picking them back up reshuffles
everything - armor doesn't re-equip, the hotbar is a mess, and other players' loot
mixes with yours. That cleanup isn't part of the challenge, just busywork after it.
SoulSort changes only what happens once you recover an item: it goes back where it was.

## How it works

All item-moving logic is server-side and rides vanilla classes, so the core
(`RestoreManager`) is shared byte-for-byte with the Fabric and Forge builds - only the
event hooks differ. NeoForge uses the classic `NeoForge.EVENT_BUS.register(this)` +
`@SubscribeEvent` style:

- **Death snapshot** - `LivingDeathEvent` (before drops) records hotbar, inventory,
  armor and off-hand layout.
- **Ground pickup** - `ItemEntityPickupEvent.Post` runs after an item enters the
  inventory; that's when it's slotted back.
- **Grave/chest mods** - NeoForge has no per-click container event, so while a non-
  inventory menu is open and you've opted in (`/soulsort container on`, default on),
  it re-checks each player tick (a cheap no-op when nothing is pending).

Only one death is restored at a time: the first pile you touch becomes active, other
un-recovered deaths are dropped (items still return, just wherever vanilla puts them).
This stops repeated deaths from scrambling each other.

`neoforge.mods.toml` sets `displayTest="IGNORE_ALL_VERSION"`, so a client with SoulSort
can join servers without it (and vice-versa); restore simply does nothing where absent.

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

Item ids without a namespace default to `minecraft:`. Invalid ids are rejected with the
bad id named. Player-facing text is Chinese or English based on the client's reported language.

## Configuration

`config/soulsort_players.json` holds per-player preferences (saved automatically).
`config/soulsort.json` is server policy - `playerOverrideAllowed=true` (default) lets
players manage themselves; set it false and opt features back in via the `lock*` flags
to pin armor/offhand/items/master to server-chosen values.

## Building

Needs JDK 25 installed locally (Minecraft 26.2's own requirement - you already have it
if you run the game). Use the bundled wrapper, NOT a system-wide `gradle`: a mismatched
Gradle version is the usual cause of plugin-resolution errors.

```
gradlew.bat clean build      # Windows
./gradlew clean build         # macOS / Linux
```

Output jar lands in `build/libs/` - drop it in the server or client `mods/` folder.
