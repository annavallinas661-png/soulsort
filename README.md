# soulsort
A Minecraft mod that restores your dropped items to their exact pre-death inventory slots, armor, and off-hand positions when you pick them back up — available for Fabric, Forge, and NeoForge on both 1.21.11 and 26.2.

## Why this exists

If you play with `keepInventory` off (most people do - it's part of the risk that
makes death mean something), dying still has one annoying side effect that has
nothing to do with difficulty: your items land on the ground in a random pile,
and picking them back up scatters them into whatever inventory slots happen to
be empty at the time. Your armor doesn't go back on. Your hotbar is a mess.
If someone else is also looting the same spot, their stuff and yours get mixed
together. None of that is a meaningful part of the challenge - it's just
busywork you do after the challenge is already over.

SoulSort doesn't change what keepInventory does. Items still drop, you can
still lose them for good if you don't make it back in time, other players can
still grab them first. The only thing it changes is what happens *after* you
successfully recover something: it goes back exactly where it used to be.
Helmets get worn again, shields go back in your off-hand, and a stack of
arrows that was split across two slots ends up back near where it was instead
of wherever the inventory's "first empty slot" logic happens to land it.

## How it works

**Everything that actually moves your items runs on the server.** The moment
a player is about to die, the mod takes a snapshot of their hotbar, main
inventory, armor and off-hand - before anything drops. From then on, every
time that player picks something up (off the ground, or out of a chest/grave
from some other mod, see below), the mod checks whether what they just picked
up matches something from that snapshot, and if so quietly moves it to where
it used to be. If two different deaths both wanted the same physical slot
(you can't wear two helmets), the older death keeps the original slot and the
newer one just falls back to *some* empty slot - no crash, no items lost,
just a sensible compromise.

The client side of the mod does **not** do any of this. It only exists to
tell you, once, whether the multiplayer server you just joined seems to have
SoulSort installed - because if it doesn't, the feature simply won't do
anything, and it's better to know that than to wonder why. In singleplayer
(or when you're hosting a LAN game) this check is skipped entirely, since the
server is just part of your own game. The server's configuration is never
sent to or shown on the client - it has no reason to leave the server.

## Commands

Any player can run these, server policy permitting (see Configuration below):

```
/soulsort on / off            master switch
/soulsort armor on / off      auto re-equip armor on pickup
/soulsort offhand on / off    auto re-equip off-hand item on pickup
/soulsort container on / off  also restore items taken out of a chest/grave (see below)
/soulsort expire <seconds>    how long an un-recovered death is remembered (server caps this)
/soulsort <ids> on / off      stop/resume sorting specific items, comma separated,
                              e.g. /soulsort glass,stone,command_block off
/soulsort list                show your current settings
/soulsort list on all         re-enable sorting for every excluded item (armor/offhand unaffected)
```

Item ids without a namespace are assumed to be `minecraft:`. Anything that
isn't a real item id gets rejected with the bad id named in the error message
instead of silently doing nothing.

## Grave/chest compatibility

A lot of "keep your death items" mods (Corail Tombstone, YIGD, Corpse, and
similar) work by putting everything into a container at your death spot
instead of scattering it on the ground. SoulSort doesn't know or care which
mod made that container - it just watches every inventory-screen click, and
if items move into your own inventory, it tries to sort them the same way it
would for a ground pickup. Turn it off with `/soulsort container off` if you
ever don't want that.

## Configuration

Two JSON files live in the server's `config/` folder.

**`soulsort_players.json`** is just everyone's personal preferences (the
things `/soulsort` changes), saved automatically and reloaded on restart.

**`soulsort.json`** is server policy - whether players are trusted to change
their own settings at all:

```jsonc
{
  "playerOverrideAllowed": true,   // false = players need explicit permission below
  "modEnabledServerControlled": false,
  "armorServerControlled": false,
  "offhandServerControlled": false,
  "itemListServerControlled": false,
  "serverModEnabled": true,
  "serverArmorEnabled": true,
  "serverOffhandEnabled": true,
  "serverDisabledItems": [],
  "defaultExpireSeconds": 300,
  "maxExpireSeconds": 1800,
  "deniedMessageZh": "...",
  "deniedMessageEn": "..."
}
```

If `playerOverrideAllowed` is `true` (the default), players control
everything themselves and the rest of this file does nothing.

If it's `false`, players normally can't change *anything* - they get the
denied message instead. You then opt individual features back in (or lock
them to a fixed value) per category: setting `armorServerControlled: true`
means armor sorting follows `serverArmorEnabled` for every player and
`/soulsort armor` is rejected, while leaving `offhandServerControlled: false`
means off-hand sorting is still each player's own call. Categories you
haven't explicitly locked are simply left to the players. `expire` always
respects `maxExpireSeconds` as a hard ceiling regardless of this setting, and
`container` is always a personal choice - it never gets server-locked.

All player-facing text (command feedback, the denied message, the "this
server might not have the mod" join notice) is bilingual: SoulSort looks at
the language the client reported when it connected and answers in Chinese or
English accordingly, since the mod has no client-side language files of its
own for a vanilla client to fall back on.

## Building

Needs JDK 25 (Minecraft 26.2's own requirement, nothing to do with this mod).
`gradlew.bat clean build` / `./gradlew clean build` - the build script will
fetch a matching JDK automatically via Gradle's toolchain support if your
machine doesn't already have one.

## Known limitations

- Only one death is actively being restored at a time. If you die again before
  recovering the first pile, whichever pile you touch first becomes the one that
  gets sorted, and the other death's items still return but land in whatever
  slots vanilla picks. This is deliberate - it's what keeps repeated deaths from
  scrambling each other, which older versions did.
- Per-player preferences persist in `config/soulsort_players.json` and server
  policy in `config/soulsort.json`; pending un-recovered deaths live in server
  memory and are forgotten on restart (the dropped items themselves are
  unaffected, they just won't auto-sort after a restart).
- If one item type was split across several original slots, recovered copies
  usually consolidate into whichever slot resolves first rather than splitting
  back apart - identical stacks are indistinguishable once merged on the ground.
