# LumiTags

LumiTags requires Paper (Java 21+) and LuckPerms.

## Transferable tag items

Run `/lumitags give <online-player> <preset>` from console or with `lumitags.give`
(default: operators). `/lumitag` remains supported. For example:

```
lumitags give Steve coffee
lumitags give Steve cherry-blossom
lumitags give Steve wave
```

Preset IDs tab-complete for `/lumitags give <player> <preset>`,
`/lumitags set <preset>`, and `/lumitags <player> <preset>`, filtered by
the permissions for each command. They are configured under `presets` in
`config.yml`. Set commands apply a preset’s formatting and prefix/suffix type;
inputs that do not match a preset remain custom text.
All 26 rewards from the prototype are included, with their original formatting.
The 13 text rewards are prefixes; the 13 emoji/symbol rewards are suffixes.
Emoji items (for example, `wave`) replace only the current suffix, so players
can use a prefix and an emoji together. Configure them with `suffix` instead
of `prefix`; `suffix-weight` controls their independent LuckPerms priority.
Previously issued emoji items are recognized by matching the configured suffix.
An input that is not a preset ID creates a custom prefix item, using the same
transformations as `/lumitags set <prefix>`. Multiword inputs are supported.

The recipient can trade or store the item. Right-click the air or a block to
consume one item and replace the player's current prefix or suffix of that type, without requiring
`lumitags.use` or granting permission to create custom prefixes. Renaming an
ordinary item cannot make it a prefix item. Items retain their prefix through
restarts and configuration changes. A failed LuckPerms save returns the item;
repeated clicks while a save is pending are ignored. A full recipient inventory
rejects the give command.

Copy `examples/FancyCrate.yml` into your CrazyCrates crates directory to use the
prototype with actual prefix item rewards. Its duplicate prize number is fixed.
For other crates, use a prize command such as:

```yaml
Commands:
  - "lumitags give %player% coffee"
```

## Removing tags and returning items

Use `/lumitags unset [prefix|suffix]` (default: prefix).
Removing your own tag requires no custom-tag permission.
Staff can use `/lumitags <player> unset [prefix|suffix]` with the moderator
permission.

An item applied with right-click is held while its tag is active. Replacing
that same tag type, including through a set command, or unsetting it returns
the exact item. Command-created tags do not produce items when removed.
Prefix and suffix ownership are independent.

Command-set tags require the player's configured `permissions.use` permission
(`lumitags.use` by default) to remain active, including tags set by staff.
Online players are checked every second. Revoking that permission removes their
command-set prefix and suffix at the configured LumiTags weights, one slot per
check. This also handles permission expiry, inherited/contextual changes and
revocations while offline after the player rejoins. Tags redeemed from items
remain active without this permission; other LuckPerms priorities are untouched.

Ownership and returned items are saved in `tag-items.yml` across restarts.
If the inventory is full or the player is offline, returns wait in a queue.
They are delivered on joining or with `/lumitags claim`; no permission is needed.
Items applied before ownership tracking was installed cannot be recovered
because the original consumed item was not recorded. Changes made directly
in LuckPerms are outside this plugin's item-return lifecycle.

## Verification

Build with `mvn package`. Before deployment, test on a Paper server with LuckPerms:

- Give a preset to a player without `lumitags.use`, trade it to another player,
  and apply it. Check that exactly one item is consumed and the formatted prefix
  replaces the previous LumiTag.
- Apply `coffee`, then `wave`, and confirm both a prefix and suffix remain.
  Apply `heart` and confirm only the suffix changes.
- Test stacked items, offhand application, repeated clicks, and persistence
  after a restart. An ordinary or anvil-renamed name tag should do nothing.
- Confirm a player without `lumitags.give` cannot create items, and a full
  inventory rejects delivery.
- Open the example crate and verify its reward is redeemable.

- Apply an item, replace it using another item or a set command, then unset
  the new tag. Each applied item should return exactly once; unsetting again
  must not create another item. Check prefix and suffix independently.
- Restart with an item applied, then unset it. Fill the inventory before
  unsetting and confirm `/lumitags claim` delivers the queued item once there
  is space. Disconnect while applying an item and confirm any return arrives
  on the next join.

