# Adapt demo studio

Records each adaptation of a skill on a real Minecraft 26.2 client, exports two clips per adaptation (player view and an observer camera that is fixed or follows the actor), and embeds them in the skill's wiki page.

## Commands

Run everything from the Adapt project root.

| Command | Purpose |
|---|---|
| `python3 src/test/demo/demo.py --skill agility` | Build, record, and export every adaptation of the skill. |
| `--only id1,id2` | Limit the run to the listed adaptation ids. |
| `--lane N` | Record on lane `N` (default 1). Each lane has its own Prism instance, client bridge, and server, so runs on different lanes work at the same time. See Lanes. |
| `--failed` | Limit the run to ids that are missing from the manifest or have status `failed`. |
| `--record-only` / `--export-only` | Run a single phase. Cannot be combined. |
| `--skip-build` | Skip `./gradlew build prepareGameplay`. This also skips the fixture rebuild, so run `./gradlew gameplayFixtureJar prepareGameplay` after editing fixture Java. The client mod build, the mod download check, and the copy of the jars into `jars/` still run. |
| `--no-audio` | Export without sound. |
| `--output <dir>` | Output folder. Default `build/demo/<skill>`. |
| `--ffmpeg` / `--ffprobe` | Tool paths. Default `~/.local/bin/<tool>` when present, otherwise the tool on `PATH`. |
| `python3 src/test/demo/docs_sync.py --skill agility` | Embed clips under each adaptation's heading in the skill's page. The page is the `../docs/adapt/*-skill-*.md` file whose ``### <name> (`<id>`)`` headings name the most of the skill's ids in `src/test/gameplay/adaptation-matrix.json`, so the file name does not have to match the skill id (`pickaxe` resolves to `25-skill-pickaxes.md`). A skill without matrix ids, no page with one of its headings, or two pages with the same count exits 2. The embedded ids come from the clip files on disk, and only ids with both clips present are embedded. A second run changes nothing. `--docs <dir>` points at another docs checkout. |
| `python3 -m unittest discover -s src/test/demo/tests -p 'test_*.py' -v` | Unit tests. |

`demo.py` exits 0 when every selected id is recorded or exported and cleanup succeeded, 1 when a take failed or cleanup reported errors, and 2 when the sheet is invalid, an id is unknown, or another run holds the lane or the output folder. A run that reaches the end prints `gallery:` (this skill's gallery), `index:` (the gallery index), `logs:` (this run's server and client logs), and `failed:` as its last lines.

Recording needs a packed Iris jar in `../Iris/build/libs`, the Iris `overworld` pack at `../../IrisDimensions/overworld` (or the folder named by `ADAPT_DEMO_IRIS_PACK`), Prism Launcher, and the Multiplexor workspace at `../../[Minecraft Server]`.

## Output

- `build/demo/<skill>/jars/`: the `adapt-client-qa.jar`, `Adapt.jar`, and `AdaptGameplayFixture.jar` this run installs, copied from `build/client-qa` and `build/gameplay/plugins` at the end of its build.
- `build/demo/<skill>/manifest.json`: one take per id with status `recorded`, `failed`, `exported`, or `skipped`, the evidence detail, and the last export error.
- `build/demo/<skill>/replays/<id>.zip`: the Flashback replay of the accepted take.
- `build/demo/<skill>/intermediate/<id>-pov-live.mp4` and `<id>-pov-live.mp4.log`: the live pov capture of a `povCapture` take and the encoder log (see Live pov capture). A retake overwrites both.
- `build/demo/<skill>/gallery.html`: review page with both clips, facts per take, a copyable list of failed ids, and a Skipped list. A visual-only take shows `visual-only: <text>` in place of the evidence facts.
- `build/demo/index.html`: one link per `build/demo/<folder>/gallery.html`, in name order, with the take counts by status from that folder's `manifest.json` and the time the gallery was written. `demo.py` rewrites it at the end of every run, so it lists the galleries of every skill shot so far, including those of other lanes.
- `build/demo/<skill>/logs/<stamp>-server-install.log`, `<stamp>-server.log`, `<stamp>-client.log`, and `<stamp>-opponent.log`: the first-boot server log, the server log of the run, the client log of the run, and the opponent client's log when the run launched one (see Opponent). `<stamp>` is the run's suffix, the part after `adapt-demo-` in the server name (for example `1790799315-bce7b6`), so every run keeps its own logs and none is overwritten.
- `../docs/adapt-assets/demos/<skill>/<id>-pov.webm` and `<id>-observer.webm`: the published clips. An export failure removes both clips of that id. An id later changed to `skip` keeps its old clips, and `docs_sync.py` keeps embedding them, until they are deleted by hand.
- The console prints `[LOCATE] <x> <z> <biome>` with the search time, or with the cache file when the site comes from `build/demo/site-cache.json`, and the gallery header names the plate origin and biome.
- Before the first take the console prints `[SKIN] loaded <seconds> s after the client connected`, or `[SKIN] default ...` when the clips of the run will show a default skin (see Pipeline).
- Before the first take the console prints `[TPS] 1-minute TPS <value> after <seconds> s`, or a `[TPS] warning:` line when the server stayed below the minimum or the reply had no TPS line.

## Pipeline

Before launching an inactive studio instance, the studio reloads its settings in a running Prism launcher by briefly moving that instance outside the watched instance folder, waiting for its removal, restoring its configuration and folder, and waiting for Prism to load it again. Active clients are rejected before this operation. All instance files and mods are preserved.

`--export-only` builds and installs only the client bridge and client mods, then launches the lane’s client at the main menu and waits for its bridge to respond. It opens the accepted Flashback replays directly; it does not create a server, load an Iris world, prepare a plate, launch an opponent, or warm up an actor. Existing accepted replay and live POV files are required. Client shutdown and lane cleanup still run after export.

Studio start: build and copy the jars into `jars/` under the build lock (see Lanes), isolated Paper 26.2 server seeded with `78264193`, first boot, action-bar popups muted, restart, the sheet's world (the Iris world, created on this server, or the Nether; see World and plate), plate location (from the site cache when it has the site), plate, client refresh and launch, the actor's window fitted to 1920x1080 (see Prism instances), the opponent client when a selected entry needs one (see Opponent), a warm-up that places the actor (and the opponent) on the `arena` set and waits `WARM_UP_TICKS` (400) client ticks so the chunks around the plate are loaded before the first take, and the skin wait.

Skin wait: the actor joins the offline-mode server as `AQAClient262`, so the server sends no skin for it, and the game window always shows a default skin, which is also what a live pov capture records. Flashback writes the actor into each replay with the skin of the Microsoft account Prism launched the client with, which the client fetches from Mojang's session server once at startup. When that fetch answers, both exported clips show the account skin; when it times out, they show a default skin such as Alex. Every bridge reply carries `skinLoaded`, which is true once that fetch has returned the account's skin and the skin's texture has loaded and is none of the game's default skin textures. After the warm-up the studio reads it every `SKIN_POLL` (0.5 s) until it is true or `SKIN_TIMEOUT` (30 s) have passed since the actor's client connected, then prints `[SKIN] loaded <seconds> s after the client connected` or `[SKIN] default <seconds> s after the client connected: the session lookup did not answer, so the actor wears a default skin`. The run records either way, so the clips of a run that printed `[SKIN] default` show a default skin.

The restart reads the server pid from `runtime status` before `runtime stop`. After the stop it asks `runtime status` every `RUNTIME_POLL` (1 s) until Multiplexor reports the runtime stopped and that pid has exited, for up to `RUNTIME_EXIT_TIMEOUT` (120 s), and fails the start with `Server <name> was still running 120 s after runtime stop` when it has not. Only then does it move the first-boot log away and run `runtime start`. A `runtime start` that exits non-zero prints `[RESTART] <error>; starting again in 5 s` and runs once more after `START_RETRY_SECONDS` (5 s); a second failure fails the start.

After the first boot the studio sets `actionbarNotifyXp`, `actionbarNotifyLevel`, and `actionbarNotifyMasterLevel` to `false` in `plugins/Adapt/adapt.toml` and leaves the rest of the file unchanged, so no XP or level popup appears in the clips.

Unless the batch skill is `discovery`, the studio also sets the top-level `enabled` key of `plugins/Adapt/skills/discovery.toml` to `false` at the same point, which turns the Discovery skill off on that server. A fresh server has fresh player data, so otherwise the first look at a block or biome during a take plays a Discovery celebration (aqua dust helix or `end_rod` sparks, an expanding ring at the feet, the amethyst chime and the challenge toast sound) and can level Discovery up with its burst. The particle switches (`effects.skillParticleOverrides` in `adapt.toml`, `showParticles` in `discovery.toml`) leave those sounds on, so the studio does not use them. A `discovery` batch keeps the skill on. Either edit fails the start when its key is missing from the top level of the file.

Before the first take of a run the studio sends `tps` over RCON every 2 s until the 1-minute value (the `1m` column of `TPS from last 1m, 5m, 15m:`, color codes ignored) is at least `TPS_MINIMUM` (19.0), for up to `TPS_TIMEOUT` (180 s). On timeout, or when the reply has no TPS line, it prints a warning and records anyway. Adapt thins its sounds and particles while the 1-minute TPS is below 19 and drops the low-priority ones below 17, and the server sits between 16.6 and 16.9 right after world creation, so a take recorded before the wait would miss its evidence. A run with nothing to record (only skips, or `--export-only`) does not wait.

Per take:

1. `adaptqa demo set <set>`, `demo actor`, `demo actor <opponent> opponent` for an entry with `"opponent": true` (`demo park <opponent>` for any other entry while the opponent client runs), `demo sparring`, clear adaptations, claim the adaptation at `level`.
2. `pre` commands.
3. `adaptqa demo sync <actor>` (and `demo sync <opponent>` for an opponent entry), so the client shows the inventory the server holds after `pre`.
4. 60-tick settle (`PRE_SETTLE_TICKS`).
5. `prelude` beats.
6. Recording starts. For an entry with `"povCapture": true` the live pov capture starts right after it.
7. 20-tick settle.
8. `beats`. The take fails when the client tick passes the recording start tick plus `maxTicks`.
9. All keys released, on the opponent client too.
10. 20-tick settle.
11. Recording stops, then the live pov capture of a `povCapture` entry. Evidence is checked against the events between the start and stop ticks.

A take with missing evidence or a timed-out beat is shot up to three times. An error inside a take fails that take and the batch moves to the next id. This covers fixture errors (`ADAPT_QA ERROR`), vanilla command errors (raised as `AssertionError` by the RCON client), bridge rejections, and a missing replay. The batch stops when the client bridge fails to answer two state requests in a row, or on Ctrl-C.

Per exported angle: open the replay, wait for it to load, warm up for `WARM_UP_SECONDS` (6 s), spectate the actor for the pov angle, export from replay tick `START_TICK` (10) at 1920x1080 30 fps as H264 at 40 Mbps, encode to VP9 WebM at CRF 27, and grab a thumbnail from the middle of the clip. Each export may take up to `EXPORT_TIMEOUT` (1800 s). The pov angle of a `povCapture` take skips the replay: its live capture is encoded the same way with the first `LIVE_TRIM_SECONDS` (0.5 s, the length of `START_TICK`) cut, and only the observer comes from Flashback.

The bridge answers a command with HTTP 504 when the client does not take it within five seconds, which happens to the first `replay-open` after a batch of recordings while the client is still saving the last replay. The export then waits up to `OPEN_RETRY_SECONDS` (30 s) for `/export-status` to report the replay open and sends `replay-open` once more when it is not, then waits for the replay to load as usual. When it still does not load, the take's export fails with both timeouts in its error. A rejection (HTTP 400) is never resent. `disconnect` after an export is handled the same way, with a 10 s wait.

## Prism instances

Each lane records with its own persistent Prism instance. Lane 1 uses folder `AdaptDemoStudio`, shown as `Adapt Demo Studio`. The first run creates it with a 4096 MB heap, the bundled Java 25 runtime, and the Minecraft 26.2 Fabric components. Later runs reuse it, and the studio never deletes it. It stays launchable from the Prism window, and shaders or mods added there are left in place.

Lane `N` above 1 uses folder `AdaptDemoStudio-N`, shown as `Adapt Demo Studio N`. When that folder is missing, the studio copies `AdaptDemoStudio` into it, so the mods, shader packs, and options of lane 1 carry over. The copy leaves out `.minecraft/flashback/replays`, `.minecraft/flashback/temp`, `.minecraft/logs`, `.minecraft/saves`, and `.minecraft/crash-reports`, and drops the `uuid` key from `instance.cfg` so Prism assigns the lane its own. An existing lane folder is never replaced or deleted, so mods or options added to lane 1 after a lane was copied do not reach that lane; the files and keys listed below are refreshed in every lane on every run. Lane 1 must exist before another lane can be copied from it. A running Prism window reports a newly copied folder as unknown until it rescans; the studio waits for the rescan and launches once more.

Each run refreshes only what the studio owns:

- `.minecraft/mods/AdaptClientQa.jar`, the client bridge.
- The mods pinned in `src/test/client/mods.json`. A jar of the same mod id at a different version is replaced. A different Flashback version stops the run with an error, because the bridge is built against Flashback 0.43.6.
- `OverrideJavaArgs` and `JvmArgs` in `instance.cfg`, which carry the bridge port, token, and output folder. Every run writes a fresh token. The bridge listens on 127.0.0.1 only, so the token is a loopback-only credential for that run.
- `OverrideWindow` (`true`), `MinecraftWinWidth` (1920), and `MinecraftWinHeight` (1080) in `instance.cfg`, so the game opens a 1920x1080 window and a live pov capture is native 1080p. The game still launches windowed: `LaunchMaximized` and the `fullscreen` option are left as they are. Other `instance.cfg` keys are untouched.

macOS opens the game window on the display it chooses and shrinks a window that does not fit there, for example to 1920x1022 under the menu bar and title bar of a 1920x1080 display; the game cannot center itself on the main display because GLFW reports no monitors on this macOS. So once the actor's client connects, the studio sends the bridge verb `fit-window` with 1920x1080. When the window's content is not that size, the bridge moves the window to the top left of the main display (the one with the menu bar), 40 points below its top edge, sets the size, and fails when the content still comes out smaller. Then the console prints `[WINDOW] warning: ...` and the run continues; live pov captures come from the smaller window. The opponent's window is left where it opens.
- `configVersion`, `recording.recordHotbar`, `recording.localPlayerUpdatesPerSecond`, and `recordingControls.quicksave` in `config/flashback/flashback.json`. Other keys are kept.
- `pauseOnLostFocus:false`, `tutorialStep:none`, and `autoJump:false` in `options.txt`. Other options are kept. A missing `options.txt` is written with the full studio defaults.

A run stops with an error when its lane's game is already running. At the end of a run the studio quits the game it launched and copies its log to `logs/<stamp>-client.log` in the output folder.

The opponent client has its own persistent instances, created and refreshed the same way: lane 1 uses folder `AdaptDemoOpponent`, shown as `Adapt Demo Opponent`, copied from `AdaptDemoStudio` when missing, and lane `N` above 1 uses `AdaptDemoOpponent-N`, shown as `Adapt Demo Opponent N`, copied from `AdaptDemoOpponent` (which is first copied from `AdaptDemoStudio` when it is missing too). The copies leave out the same folders and drop `uuid`, an existing opponent folder is never replaced or deleted, and every run refreshes the same files and keys as for the studio instance, with the opponent's own bridge port and token. It stays launchable from the Prism window, so skins or mods added there are kept.

## Lanes

- Output stays per skill in `build/demo/<skill>/`, whatever the lane. Run one skill per lane; a second run on the same output folder stops with exit code 2.
- `build/demo/lane-N.lock` keeps two runs off one lane, and `run.lock` in the output folder keeps two runs off one output folder. Both are released when the run ends.
- `build/demo/build.lock` serializes the build phase of every run: the Gradle build (unless `--skip-build`), the mod download check, the client bridge build, and the copy of `adapt-client-qa.jar`, `Adapt.jar`, and `AdaptGameplayFixture.jar` into the run's `jars/` folder. The server and the Prism instance take their jars from that folder, so a build started by another lane never changes the jars of a running lane. The build lock and `mux.lock` are never held together. `src/test/client/build.sh` compiles into a private folder and renames the finished jar over `build/client-qa/adapt-client-qa.jar`, so a copy never sees a partly written bridge.
- Every Multiplexor command the studio runs (`server create`, `instance port`, `gameplay prepare`, `instance path`, `runtime start`, `runtime status`, `runtime stop`, `instance delete`) holds the file lock `build/demo/mux.lock`, so lanes never change the Multiplexor registry at the same time. The lock is taken per command, so the status polls of a restart let other lanes run their commands in between.
- The server port and the client bridge port come from `build/demo/ports.json`. Each port is bind-tested while `mux.lock` is held and recorded with the run's process id. A port recorded by a live run is never handed out again, records of runs that have ended are dropped, and a run removes its own ports when it stops.
- Each run has its own server instance and bridge token, and records into its own lane's Prism folder.

## World and plate

- A sheet plays on one world for all its entries, named by its top-level `world` key: `iris:adapt_demo` when the key is absent, or `minecraft:the_nether`. The actor, the opponent, and every lane go to that world; the opponent is parked in the server's main world as usual.
- Every server is created with `level-seed=78264193` in `server.properties`, so the Nether has the same terrain on every run.
- Iris world `adapt_demo`, pack `overworld`, seed `78264193`, created with `iris create name=adapt_demo type=overworld seed=78264193` only when the sheet plays on it. The pack is copied into `plugins/Iris/packs/overworld` without its `.git` and `.iris` folders, and the server restarts once before the world is created. World loading may take up to `WORLD_TIMEOUT` (600 s).
- The Nether is the server's own `minecraft:the_nether` dimension, loaded at server start. Paper 26.2 has no `allow-nether` in `server.properties`; the switch is `misc.enable-nether` in `config/paper-global.yml`, `true` by default, and the studio leaves it on. `adaptqa demo world <key>` fails at once for a `minecraft:` world that is not loaded, instead of waiting for it like the Iris world.
- `adaptqa demo locate <radius> <step>` picks the plate origin in the world that `demo world` selected. It scans grid points `step` blocks apart within `radius` blocks of 0 0, nearest first, and returns the first origin whose footprint qualifies. The studio asks for 6144 256 in the Iris world and 1536 64 in the Nether (`LOCATE_GRIDS`).
- In the Iris world a background thread samples the footprint every 8 blocks through Iris's terrain service, which does not load chunks: every sample must be land, its surface biome key must contain `plains`, `meadow`, `grass`, or `savanna` (case-insensitive), and the sampled heights may span at most 4 blocks. For a candidate that passes, the command then reads the world at every sample and requires a grass block as the highest block below any leaves, which rules out water.
- In the Nether a background thread loads each candidate's chunks through the server and reads chunk snapshots. The plate's floor is the origin column's lowest solid block at or above the Nether's sea level (y 32, the top of the lava sea) with at least 40 air blocks above it (`NetherSurvey.HEADROOM`), so the plate's cleared space of `CLEAR_HEIGHT` (32) blocks lies in open air under the Nether's ceiling. That floor must be netherrack or soul soil, and the biome at the origin and at every footprint sample (every 8 blocks, at the floor's height) must be `minecraft:nether_wastes` or `minecraft:soul_sand_valley`, which keeps the fungus forests and the basalt deltas out. At least 80 percent of the samples need a solid block from 3 blocks below the floor up to the floor, so the plate rests on the ground, and at least 80 percent of the sampled blocks of the cleared space must already be air, so the plate stands in an open cavern rather than a carved room. No lava may be able to reach the plate: within 8 blocks of the footprint (`LAVA_MARGIN`, one more than lava spreads in the Nether), from the plate's top layer up to the ceiling, a lava block with air below it or beside it, or with the plate's cleared space there, rejects the site. That catches lava springs and lavafalls above or beside the plate even in freshly generated chunks, where they have not started to flow yet. Lava sealed in the rock stays where it is, and lava inside the footprint is overwritten by the plate, below the floor by its netherrack layers and above it by the cleared air, so none is left inside the footprint. The reply's biome is the vanilla biome key at the origin floor, such as `minecraft:nether_wastes`.
- While either scan runs the command replies `ADAPT_QA DEMO LOCATE PENDING <checked>/<total>` and the studio asks again every second, for up to `LOCATE_TIMEOUT` (600 s). The final reply is `ADAPT_QA DEMO LOCATE <x> <z> <biomeKey>`, or `ADAPT_QA ERROR` with the rejection counts and the most common origin biomes when no origin qualifies. With seed `78264193` the Iris world within 2048 blocks of 0 0 is mostly ocean, tundra, and mountains, and the scan settles on -1280 5120 in `temperate/plains`.
- With seed `78264193` the Nether scan settles on 768 448 in `minecraft:soul_sand_valley` after about 100 s, with the plate at y 43 on a soul soil floor in a long open valley. That biome fills the air around the client with its ambient `ash` particles, and the evidence check counts every particle the client renders, so a Nether entry never expects `ash`.
- The located site is kept in `build/demo/site-cache.json` under the key `<world>:<seed>:<version>`, with the value `{"x", "z", "biome"}`. For the Iris world the version is the `version` field of `dimensions/overworld.json` in the pack source folder (`iris:adapt_demo:78264193:4013`); for the Nether it is the Minecraft version (`minecraft:the_nether:78264193:26.2`), so each world keeps its own site. A run whose key is in the file plates at that site without `demo locate`. A successful locate adds or replaces its key and keeps the other keys. All lanes share the file, and writes hold `build/demo/mux.lock`. An Iris pack without a `version` field is located on every run and never cached. Delete the file, or its key, to search again, for example after changing the locate criteria or editing the pack without raising its version.
- `adaptqa demo plate <x> <z>` places the plate at the located origin, with y one block above the median surface of a 3x3 sample grid (leaves ignored). In the Nether y is one block above the origin column's floor as defined for the locate, and the command fails when that column has none.
- The plate spans x -15..+15 and z -60..+10 around the origin: three layers of grass over dirt in the Iris world, three layers of netherrack in the Nether, cleared to air from the origin y through `CLEAR_HEIGHT` (32) blocks above it, 33 layers in all. `demo reset` and every `demo set` restore the top layer with the same material.
- North is -z. Yaw 180 faces north.
- The selected world has no day cycle, weather, mob spawning, or fire spread. Keep inventory is on, difficulty is Normal, and time is fixed at noon in the Iris world (the Nether has no world clock, so `demo world` leaves its time alone).
- Every `demo set` resets the gamerules that entries change: `fall_damage` false, `random_tick_speed` 3, and `mob_griefing` true, so a value set in one take's `pre` never reaches the next take. A take that needs fall damage opts in with `"pre": ["execute at {actor} run gamerule fall_damage true"]`.

## Sets

Each skill keeps its sets in its own provider, `src/test/gameplay/fixture/java/art/arcane/adapt/gameplay/demo/sets/<Skill>Sets.java`, a class implementing `DemoSetProvider` (`skill()` returns the skill id, `sets()` its sets). A new provider is registered by adding it to `DemoSets.PROVIDERS` in `demo/DemoSets.java`, one line per provider. `demo set <name>` looks the name up across all registered providers.

Set names share one namespace across skills. When two providers define the same name, every `demo set` fails with `ADAPT_QA ERROR Duplicate demo set name <name>: provided by both <skill> and <skill>; set names must be unique across DemoSets.PROVIDERS`. Reuse another skill's set by its name instead of copying it.

`AgilitySets` holds `arena`, `runway`, `corridor`, `ladder`, `ledge`, `fence`, `pressure`, `sparring` (zombie), and `archery` (skeleton). Each set defines an actor start pose, a default observer camera pose, whether that camera follows the actor (`followCamera()`, false unless the set says otherwise), and optional sparring mobs. Most sets are `SimpleSet` records: name, actor start, camera, follow flag, sparring list, and a builder that places the set's blocks around the origin (`SimpleSet.OPEN_PLATE` places none). Builders use the block helpers in `DemoGeometry`: `fill` (a cuboid of one material), `fenceRow` (a connected fence line along x), and `ladder` (one ladder facing a given side).

A set that places blocks outside the space the plate restores (below the grass layer, or more than `CLEAR_HEIGHT` blocks above the origin) implements `DemoSet.clear` to remove them. Every `demo set` and `demo reset` calls `clear` on the previous set before it restores the plate. `SimpleSet` builds only inside that space and clears nothing.

`runway` is the open plate with a following camera: the eye stays 3 blocks east, 4.5 blocks south, and 1.2 blocks above the actor's body center, turned 33.7 degrees left of the actor's facing and pitched 12.5 degrees down, so a runner heading north stays large and in frame for the whole sprint.

`corridor` is two stone brick walls 20 blocks tall and 9 blocks long (z -4..+4), at x -1 and x +2, leaving a 2-block gap at x 0 and x 1. The actor starts at 0.5 0 0.5 facing north. The camera eye sits at 1.0 6.0 -17.5, 18 blocks north of the actor, facing south through the north opening and pitched 10 degrees up, so the whole climb and the fall stay in frame.

`pressure` is a walkway one block high and 3 wide (x -1..+1) from z -2 to z -26: 75 redstone lamps at y 0, each under a stone pressure plate at y 1, with a row of smooth stone slabs at z -1 and z -27 as steps. A triggered plate lights the lamp under it and the lamps next to that one, and the east side of the walkway shows the lit lamps to the camera; a plate hides the top of the lamp under it. The actor starts at 0.5 0 0.5 facing north. The camera eye sits at 14.0 7.0 -14.0 facing west and pitched 22 degrees down, so the whole walkway stays in frame.

`seaborne-shaft` is a flooded shaft 5 blocks wide (x -2..+2) and 6 long (z -7..-2), lined with prismarine bricks and lit by sea lanterns, with its water surface level with the grass. Its floor sits 8 blocks below the world's sea level (`World.getSeaLevel()`), and at least 8 blocks below the origin, so an actor on the floor has its eye at least 6.4 blocks below sea level. `demo set` fails when that floor would lie more than 24 blocks below the origin. The actor starts on the grass at 0.5 0 0.5 facing north and walks in. The following camera sits 1.6 east, 1 north, and 2.8 above the actor's body center, turned 122 degrees left and pitched 56 degrees down, so it looks back at the diver from inside the shaft. `clear` fills the shaft with dirt.

`NetherSets` holds the sets of the Nether skill, for sheets with `"world": "minecraft:the_nether"`. They are built mostly from blackstone, polished blackstone, soul soil, basalt, and netherrack. The sets with fire or lava (`nether-soulfire`, `nether-fire`, `nether-brawl`, `nether-lava`, `nether-strider`) contain nothing flammable, so the flames have nothing to burn. Every block sits on the plate's top layer or in the cleared space above it, so `demo reset` removes them all.

- `nether-soul`: a soul soil path 5 blocks wide (x -2..+2) from z +1 to z -30 in the top layer, curbed with polished blackstone bricks, with basalt posts at its far end. Following camera of `runway`.
- `nether-soulfire` and `nether-fire`: soul fire on soul soil, or fire on netherrack, 2 blocks north of the actor (0 0 -2), in a 3 by 3 ring of polished blackstone in the top layer. Fixed camera at 6.5 2.4 0.5 facing west.
- `nether-lava`: a lava pool 5 blocks wide (x -2..+2) from z -1 to z -16 in the top layer, rimmed with blackstone, with basalt posts at the far corners. The actor starts on the rim. Following camera of `runway`.
- `nether-quarry`: a face 5 blocks wide and 2 high right north of the actor: blackstone at z -1, basalt at z -2, netherrack at z -3. Fixed camera at 6.5 2.4 0.5 facing west.
- `nether-magma`: a 3 by 3 pad of magma blocks (x -1..+1, z -3..-1) in a 5 by 5 ring of polished blackstone in the top layer, right north of the actor. Fixed camera at 5.5 2.6 2.5, yaw 128.7, pitch 14.
- `nether-brawl`: the `nether-fire` hearth with a piglin sparring mob at 2.5 0 -2.0, east of the fire. Fixed camera at 5.5 2.8 3.5, yaw 144, pitch 14.8.
- `nether-blast`: a TNT block 3 blocks north of the actor (0 0 -3) on an obsidian pad 15 by 15 (x -7..+7, z -10..+4) in the top layer. The pad reaches past the blast radius of TNT, so the explosion leaves the plate whole. Fixed camera at 9.5 3.0 0.5, yaw 99.5, pitch 12.4.
- `nether-barter`: three piglin sparring mobs at -1.0 0 -1.5, 0.5 0 -2.0, and 2.0 0 -1.5, each 2.5 blocks from the actor, on a floor of polished blackstone bricks with gilded blackstone corners. Fixed camera at 8.5 4.0 -0.5 facing west, pitched 20.6 degrees down.
- `nether-skull`: a polished blackstone brick wall 7 blocks wide and 5 high (x -3..+3) at z -14 between basalt posts. Fixed camera at 9.5 3.5 -6.5 facing west, pitched 12.5 degrees down.
- `nether-strider`: the `nether-lava` pool with a strider sparring mob on the lava at 0.5 0 -2.5, within reach of the actor on the rim. Fixed camera at 7.5 3.5 1.5, yaw 116.6, pitch 15.6.
- `nether-wither`: wither roses on soul soil (x +2..+4, z -1..+1) in a ring of polished blackstone, east of the actor, who starts facing east (yaw -90). Fixed camera at 6.5 2.8 5.5, yaw 140.7, pitch 12.7.
- `nether-harvest`: a wither skeleton sparring mob at 0.5 0 -2.0 on a nether brick floor (x -2..+2, z -4..-1) with nether brick fence posts at its far corners. Fixed camera at 5.5 2.8 2.5, yaw 125, pitch 16.4.
- `nether-feast`: a crimson nylium bed (x -4..-2) and a warped nylium bed (x +2..+4), both from z -4 to z -2, each with one fungus and three roots, leaving the view straight north open. Fixed camera at 1.5 2.6 -7.0, yaw 8.1, pitch 9.6, facing the actor.

`kinetics-spire` is a stone brick column 3 by 3 and 50 blocks tall, with the actor on top at 0.5 50 0.5 facing north. `clear` removes the whole column. The spire and the 20-block `kinetics-tower` share a following camera 6 east, 1 north, and 1.5 above the actor's body center, turned 99.5 degrees left and pitched 13.9 degrees down, so the column stays behind the falling actor.

A set can also place the opponent client by overriding `opponentStart()` in its `DemoSet` (a `SimpleSet` always uses the default). The default puts the opponent 3 blocks south of the actor start, at the same height, facing north (yaw 180), toward the actor.

All poses are relative to the plate origin, except following camera poses. Camera poses give the eye position. Actor poses give the feet position. The export lowers a fixed camera's y by `PLAYER_EYE_HEIGHT` (1.62) to place the Flashback camera.

### Following cameras

A following camera pose is an offset from the actor, written in world axes:

- `x`, `y`, `z`: the camera eye relative to the actor's body center, which is the feet position plus half the player's current height: 0.9 standing, 0.3 while swimming. East, up, and south are positive.
- `yaw`: added to the actor's body yaw. 0 looks the way the actor faces, negative turns the view left, positive turns it right.
- `pitch`: the view pitch. Positive looks down.

`demo set` reports such a camera unchanged, with `"follow": true` added, and the take stores it the same way in the manifest. The observer export then writes a Flashback `TRACK_ENTITY` track instead of the fixed `CAMERA` track, with one keyframe at tick 0:

```json
{"type": "track_entity", "target": "<actor uuid>", "bodyPart": "BODY", "yawOffset": -33.7, "pitchOffset": 12.5,
 "positionOffset": [3.0, 1.2, 4.5], "viewOffset": [0.0, 0.0, 0.0], "roll": 0.0, "interpolation_type": "HOLD"}
```

`yawOffset` and `pitchOffset` are the pose yaw and pitch, `positionOffset` is the pose `x`, `y`, `z`, and `target` is the take's actor uuid. Flashback 0.43.6 re-applies the keyframe every frame: it takes the actor's interpolated position plus half its height and its body yaw, adds `positionOffset` without rotating it, adds `yawOffset` to the body yaw, uses `pitchOffset` as the pitch, and lowers the result by the viewer's eye height, so the pose is an eye position. `viewOffset` would be rotated with the view and stays zero. The export passes no initial camera for a following take, so Flashback starts from the replay viewer's pose, as for the pov angle.

The position offset does not turn with the actor, while the view does. A following camera suits takes where the actor keeps its heading, such as the runway sprints.

`demo actor <player>` puts the actor in survival with an empty inventory, no effects, full health and food, a hidden name tag, and teleports it to the set's start pose. `demo actor <player> opponent` does the same for the opponent client's player and teleports it to the set's `opponentStart()` instead. It then removes every entity inside the plate (x -15..+15, z -60..+10, from 3 blocks below the origin to the top of the cleared space) except players and the set's tracked sparring mobs, sets vanilla XP to level 0 with an empty bar, refills the air supply, clears fire and arrows stuck in the body, and resends the inventory to the client. Animals outside the plate stay.

`demo park <player>` puts the player in spectator mode at the spawn of the server's main world, away from the plate, and replies `ADAPT_QA DEMO PARK <player>`. The studio parks the opponent this way for every take without `"opponent": true`, so it never appears in those clips.

`demo sync <player>` resends the player's inventory to the client and replies `ADAPT_QA DEMO SYNC <player>`. Every take runs it after the `pre` commands, because a client can keep showing an empty hotbar while the server holds the item that `pre` gave.

## Sheets

`src/test/demo/sheets/<skill>.json` maps every adaptation id of the skill in `src/test/gameplay/adaptation-matrix.json` to an entry. The sheet must cover the matrix exactly.

| Field | Meaning |
|---|---|
| `world` | Top level of the sheet, beside the entries, not inside one: `iris:adapt_demo` (the default) or `minecraft:the_nether`. Every entry of the sheet plays on that world. Any other value fails validation. See World and plate. |
| `set` | Set name. Required. |
| `level` | Adaptation level to claim. |
| `pre` | RCON commands run after the claim, before the settle. `{actor}` is replaced by the actor name and `{opponent}` by the opponent name. |
| `opponent` | `true` when the take needs the opponent client. See Opponent. |
| `povCapture` | `true` records the pov clip live from the actor's window instead of from the replay. See Live pov capture. |
| `prelude` | Beats run before recording, usually a `look` or `lookAt` to aim. |
| `beats` | Recorded beats. Required and non-empty. |
| `expect` | `{"sound": ..., "particle": ...}` with at least one of the two, or `{"visual": "<what must be seen>"}` alone. See Visual-only takes. |
| `maxTicks` | Recording budget for the beats, counted from the recording start. Default 400. |
| `camera` | Observer camera override `{"x", "y", "z", "yaw", "pitch"}` in plate coordinates at eye height. With `"follow": true` the values are an offset from the actor, as for a following set (see Following cameras). |
| `skip` | Reason text. See Skips. |

### Beats

Camera turns and cursor travel execute inside the client as single eased animations. The director waits for completion before the next action; an animation that remains active beyond four extra ticks fails the take.

| Verb | Fields | Behavior |
|---|---|---|
| `look` | `yaw`, `pitch`, optional `ticks` | Set the view angle, or ease toward it over 1–200 ticks using the shortest yaw turn. |
| `lookAt` | `offset: [x, y, z]`, optional `ticks` | Aim from the actor's eye at a plate-relative point; `ticks` eases the turn and waits before the next action. |
| `keys` | `hold`, `ticks` | Hold the keys for `ticks`, then release. |
| `press` | `hold`, `off`, `ticks` | Set `hold` keys down and `off` keys up with a lease of `ticks`, and continue immediately. |
| `tap` | `key`, `ticks` | Hold one key for `ticks` (default 2), then release. |
| `click` | `key` | Click `attack`, `use`, `swapHands`, or `drop` once. |
| `wait` | `ticks` | Wait client ticks. |
| `waitFor` | `sound` or `particle`, `timeout` | Wait until the event arrives. `timeout` defaults to 100 ticks. On timeout the take fails. |
| `command` | `text` | Run an RCON command. `{actor}` and `{opponent}` are replaced. |
| `hit` | `amount` | Damage the actor, credited to the nearest sparring mob when one exists. |
| `slot` | `index` | Select hotbar slot `index` (0 to 8), as the number keys do. |
| `window` | `action`, `slot`, `button` | Act on the open container screen. `click` clicks `slot` with `button` 0 (left, the default) or 1 (right), `shift` shift-clicks it, `drop` throws from it (`button` 0 one item, 1 the whole stack), `close` closes the screen, and `list` changes nothing. See Containers. |
| `anvilName` | `text` | Type `text` into the open anvil's name field. The renamed item appears in the result slot. See Containers. |

Keys: `forward`, `back`, `left`, `right`, `jump`, `sneak`, `sprint`, `attack`, `use`, `swapHands`, `drop`.

`swapHands` is the swap item with off hand key (F by default): it swaps the selected hotbar item with the off-hand item. `drop` is the drop item key (Q by default): it throws one item of the selected stack. The game acts on these two once per key press, not while the key is held, so `click`, `tap`, `keys`, and `press` each act once when the key goes down, and holding it longer or sending it again while it is still held does nothing more. To act again, release the key first (`keys` and `tap` release it at their end, `press` when its lease runs out), or use another `click`.

Any beat of `look`, `lookAt`, `keys`, `press`, `tap`, `click`, `slot`, `window`, and `anvilName` may add `"actor": "opponent"` to drive the opponent client instead of the actor. `lookAt` then aims from the opponent's eye. `keys` and `tap` still count their ticks on the actor's client, which is the recording clock, and `wait`, `waitFor`, `command`, and `hit` always belong to the actor.

Validation rules:

- `keys` and `press` need a non-empty `hold`.
- `ticks` on `keys`, `press`, and `tap` is an integer from 1 to 200.
- `off` is only allowed on `press`.
- `tap` keys must be in the key list. `click` takes `attack`, `use`, `swapHands`, or `drop`.
- `waitFor` needs a sound or particle. Its `timeout` is an integer of at least 1.
- `slot` needs an `index`, an integer from 0 to 8.
- `window` needs an `action`: `list`, `click`, `shift`, `drop`, or `close`. `click`, `shift`, and `drop` need a `slot`, an integer of at least 0, and take an optional `button` of 0 or 1. `list` and `close` take neither.
- `anvilName` needs a `text` of 1 to 50 characters.
- `actor`, when present, must be `opponent`, only on the verbs listed above, and only in an entry with `"opponent": true`. `{opponent}` in `pre` or in a `command` beat also needs `"opponent": true`.

### Opponent

Some adaptations need a second player: a handler that checks for a `Player` damager, or an effect that concerns another player. Such an entry sets `"opponent": true`, and its beats drive a second Minecraft 26.2 client, the opponent, through `"actor": "opponent"`. Sparring mobs remain the default; use the opponent only when a mob cannot trigger the adaptation or the effect is about another player.

- The studio launches the opponent only when at least one selected entry that is not a skip has `"opponent": true`, and never for `--export-only`. It joins the same server as offline player `AQAOpponent` from its own Prism instance (see Prism instances), with its own client bridge on its own port and token.
- The warm-up places the opponent at the `arena` set's opponent start. Each opponent take places it at its set's `opponentStart()` with `demo actor AQAOpponent opponent`, which clears its inventory and hides its name tag, and `pre` can equip it through `{opponent}` (`give {opponent} minecraft:iron_sword 1`). Every other take parks it with `demo park AQAOpponent`.
- The opponent is never recorded and never spectated. It appears in the clips only as the other player in the actor's recording.
- At the end of the run the studio quits the opponent client with the actor's client, copies its log to `logs/<stamp>-opponent.log`, and keeps its instance.

`agility-kip-up` is the reference entry: the opponent aims at the actor in the prelude, walks up with `press forward`, hits with `click attack`, and the actor jumps right after the hit.

### Containers

`window` and `anvilName` act on the screen the client has open, so the take opens it first: aim at the block (crafting table, anvil, chest), `click` `use`, and `wait` 5 ticks before the first `window` beat. The bridge rejects a `window` beat when no container screen is open, and an `anvilName` beat when no anvil screen is open or the anvil's first slot is empty. A rejection fails the take, so `{"verb": "window", "action": "list"}` also checks that the container opened.

Slot clicks, shift-clicks, and drops move a visible cursor to the slot over 2–6 ticks according to distance, pause for one tick, then act and allow two ticks for the result. Budget up to nine extra ticks per slot action in `maxTicks`. Cursor movement uses GUI coordinates and leaves the desktop mouse alone. Hover highlights, tooltips, and carried items use the same cursor, which is drawn into live POV captures. The bridge rejects cursor and slot actions when the expected container ID has changed. Replay-only footage still omits the inventory screen.

`window` with `action: hover` moves to `slot` without clicking and holds for 12 ticks so its tooltip can be read (allow up to 18 ticks total). `action: button` uses `index` for an available enchanting offer (0–2) or visible stonecutter recipe, moves to its button, then invokes the actual screen click (allow up to nine ticks). Bridge state lists available button centers under `container.controls`; an unavailable control fails before input.

The `slot` of a `window` beat is the open menu's slot number, not the inventory index. The menu of a block lists the block's own slots first, then the 27 inventory slots, then the 9 hotbar slots:

| Screen | Slots |
|---|---|
| Crafting table | 0 result, 1 to 9 the grid row by row from the top left, 10 to 36 inventory, 37 to 45 hotbar (hotbar slot `n` is `37 + n`) |
| Anvil | 0 and 1 inputs, 2 result, 3 to 29 inventory, 30 to 38 hotbar (hotbar slot `n` is `30 + n`) |

A left click picks up a stack or puts the carried stack down, and a right click with a carried stack puts down one item. A furnace from 8 cobblestone in hotbar slot 1 of a crafting table: `click` slot 38, `click` slots 1, 2, 3, 4, 6, 7, 8, and 9 with `button` 1, `click` slot 38 to put the rest back, `wait` 5 ticks, then `shift` slot 0 to move the furnace into the inventory.

An anvil rename costs levels, so give some in `pre` (`xp add {actor} 5 levels`). Move the item into slot 0 (`shift` its inventory slot), `wait` 5 ticks, then `anvilName`: the anvil screen sets the name field back to the item's own name whenever slot 0 changes. Take the result with `shift` slot 2.

Every bridge reply carries `selectedSlot`, `inventory` (the non-empty player inventory slots as `index`, `item`, and `count`, with the hotbar at 0 to 8), and `container`: the open container's `id`, every slot's `index`, `item`, and `count`, and the `carried` stack, or null when no container screen is open.

### Live pov capture

Flashback replays do not record open screens, so a pov clip exported from the replay never shows a crafting grid, a chest, an anvil, a merchant's trades, or any other container screen. An entry whose point is such a screen sets `"povCapture": true`. Its pov clip then comes from the actor's own window while the take runs, with the screen, the hotbar, and the rest of the HUD exactly as the player saw them. The observer clip still comes from the replay. Keep the flag off for every other entry.

- The bridge verb `capture` with `action` `start` takes `path`, `ffmpeg`, `width`, `height`, and `fps`, and starts the given ffmpeg reading raw RGBA frames of the main render target from its standard input and writing an H264 MP4 at 40 Mbps with `h264_videotoolbox`, or with `libx264` when that ffmpeg lists no `h264_videotoolbox`. A render target of another size is cropped around its center to the aspect of `width` by `height` and scaled to that size. `capture` with `action` `stop` closes the input, waits up to 10 s for ffmpeg to finish, and fails when ffmpeg exits with an error. `start` is refused while a capture runs or an export is queued or running, `stop` when no capture runs, and `replay-open` and `export` are refused while a capture runs.
- The studio starts it right after the recording starts, with `path` `intermediate/<id>-pov-live.mp4` in the output folder, the `--ffmpeg` tool, 1920x1080, and 30 fps, and stops it right after the recording stops. The console then prints `[CAPTURE] <id> <frames> frames in <seconds> s (<rate> fps), source <width>x<height>`, where the source is the size of the render target the frames came from.
- Frames are paced by the wall clock: each 1/30 s slot gets at most one frame, taken from the first frame the game renders in it. While a capture runs the client renders at up to twice the capture rate (60 fps) and is never throttled as idle; the configured frame limit returns when the capture stops.
- Toasts are cleared at the start and on every frame while a capture runs, so neither Flashback's `Started recording` toast nor advancement or recipe toasts reach the clip; a replay export never shows them either.
- Every bridge reply carries `capturing`, `captureFrames`, `captureSeconds`, and `captureSource`: the running capture's frames written so far, seconds since it started, and render target size, or the last finished capture's values.
- The export encodes the live file to the pov WebM as usual, after cutting the first 0.5 s so it starts where the replay export starts. `--export-only` reuses the live file of the last recording and fails the take when it is missing.

The trade-off is that the capture is real time. A slot in which the client renders no frame (a stall, a loaded machine, other lanes exporting) is missing from the clip, so the clip plays slightly short and fast instead of freezing, and the frame count and rate on the `[CAPTURE]` line show how close it came to 30 fps. The clip has no game sound. A minimized window renders at 10 fps, so leave the lane's window open while it records.

## Background input

Authorized held attack input follows vanilla block-breaking logic even when the studio client has not captured the desktop mouse. The bridge bypasses only the mouse-grab condition for an active attack lease; GUI, item-use, target, and survival checks still apply. Synthetic interactions count as client activity to prevent idle throttling. Bridge state exposes `mouseGrabbed` and `bridgeAttackActive` for verification. The desktop cursor remains under user control.

## Evidence

- A name without a namespace means `minecraft:`, so `cloud` matches `minecraft:cloud`. Names must match exactly after that.
- Sounds count when the client plays them between the start and stop ticks.
- Particles count only when the client rendered them. The particle must appear in the pov view.
- The manifest detail reads `sound=<found> particle=<found>`. An expectation that is not set reads `n/a`.

## Visual-only takes

An adaptation that plays no sound and spawns no particle, but whose effect is visible on screen (movement, blocks, entities), gets `expect.visual`, a text naming what the clips must show:

```json
"expect": {"visual": "The actor is launched forward and lands 8 blocks ahead."}
```

- `visual` must be a non-empty string and cannot be combined with `sound` or `particle`.
- The take passes the evidence check without any event. A timed-out beat or a take error still fails it.
- The manifest keeps the text in `visual`, the evidence detail reads `visual-only: <text>`, and the console prints it on the `[TAKE]` line.
- The gallery labels the take `visual-only: <text>` instead of `evidence ok`.
- Nothing checks the footage automatically. Whoever runs the batch confirms from frames of both clips that the text is true, and reshoots the id or turns the entry into a skip when it is not.

An adaptation with a sound or a particle uses `sound` or `particle`, even when its effect is also visible. An adaptation with nothing visible gets a skip.

## Skips

An adaptation with nothing visible in a learned-only clip gets an entry with only a reason:

```json
"agility-marathoner": {"skip": "Reduces hunger drain while sprinting; nothing visible in a learned-only clip."}
```

A skip entry needs no `set`, `beats`, or `expect`, and cannot carry beats. The manifest records it with status `skipped` and the reason as its detail. It has no replay, is never exported or embedded in the docs, and appears under Skipped in the gallery.
