# Adapt API examples

Two standalone Gradle builds that use `art.arcane.adapt:adapt-api` and nothing else of Adapt. Both compile the same
content from `example-content`: the `example:geology` skill with four adaptations, a mutation kit, an adaptation
added to the built-in Pickaxes skill, English and German text and 16x16 sprites.

| Folder | Output |
|---|---|
| `example-plugin` | `build/libs/AdaptGeologyPlugin-1.0.0.jar`, a Bukkit plugin with `depend: [Adapt]` |
| `example-mod` | `build/libs/AdaptGeologyMod-1.0.0-{fabric,forge,neoforge}.jar`, one jar per loader from one source tree |

Build the API into the local Maven repository first, then each example:

```sh
./gradlew :api:publishToMavenLocal
./gradlew -p examples/example-plugin build
./gradlew -p examples/example-mod build
```

| Class | Shows |
|---|---|
| `GeologyAddon` | Messages, assets, the skill, an adaptation on `adapt:pickaxe`, a listener |
| `GeologySkill` | Two XP sources, groups, an advancement with a milestone, the kit |
| `StoneSkin` | A passive armor bonus through the attribute service |
| `Prospect` | An active ability with a cooldown and HUD status |
| `CoreSample` | A block-break effect with a config, a preference and a requirement on `adapt:pickaxe/gem_polish` |
| `GeodeLens` | A custom item and the shaped recipe that unlocks with the adaptation |
| `Aftershock` | An adaptation registered under the built-in Pickaxes skill |
| `GeologyKit` | The six mutation blocks |
| `FieldNotes` | A player section declared through the registrar, kept in the save while the addon is gone |
| `GeologyConsole` | The catalog, player progress, preferences and events, run as `/adapt geology <action>` on every platform |

`qa/run.sh <paper|fabric|forge|neoforge> <version> <jar>` runs `qa/example-geology.mjs` on a burner Multiplexor
server through `bukkit/src/test/gameplay/platform-run.sh` and deletes the server afterwards.
