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

Preset IDs tab-complete and are configured under `presets` in `config.yml`.
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
