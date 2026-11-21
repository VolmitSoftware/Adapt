package art.arcane.adapt.gameplay;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxBudget;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerInput;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.fx.ParticleSpec;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.math.PlayerInputPort;
import art.arcane.adapt.util.common.scheduling.Tasks;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class GameplayFixture {
    private static final Id MAX_HEALTH = Id.minecraft("max_health");
    private static final int ARENA_RADIUS = 24;
    private static final int ARENA_FLOOR = 99;
    private static GameplayFixture instance;

    private final FixturePlatform platform;
    private final Map<UUID, Loc> origins = new HashMap<>();
    private final Map<UUID, GameMode> gameModes = new HashMap<>();
    private boolean installed;
    private WorldRef arena;
    private PlayerRef actor;
    private PlayerRef opponent;

    private GameplayFixture(FixturePlatform platform) {
        this.platform = platform;
    }

    public static GameplayFixture start(FixturePlatform platform) {
        if (instance != null) {
            throw new IllegalStateException("Gameplay fixture already started");
        }
        platform.requireIsolatedServer();
        Fixtures.install(platform);
        instance = new GameplayFixture(platform);
        return instance;
    }

    public static GameplayFixture get() {
        return instance;
    }

    public static boolean isFixtureWorld(WorldRef world) {
        GameplayFixture fixture = instance;
        if (world == null || fixture == null) {
            return false;
        }
        return fixture.arena != null && fixture.arena.uid().equals(world.uid()) || Fixtures.isGeneratedWorld(world);
    }

    public WorldRef arena() {
        return arena;
    }

    public PlayerRef opponent() {
        return opponent;
    }

    public void stop() {
        cleanup();
        Fixtures.unregisterAll();
        installed = false;
        instance = null;
    }

    private void ensureInstalled() {
        if (installed) {
            return;
        }
        installed = true;
        RangedFixtures.install();
        CombatAuditFixtures.install();
        HarvestFixtures.install();
        TamingFixtures.install();
        DefenseFixtures.install();
        RiftFixtures.install();
        ProjectileFixtures.install();
        ProjectileTargetingFixtures.install();
        GuardFixtures.install();
        CraftingFixtures.install();
        ChronosFixtures.install();
        TraversalFixtures.install();
        NatureFixtures.install();
        NetherFixtures.install();
        UtilityFixtures.install();
        KineticsFixtures.install();
        KnowledgeFixtures.install();
        XpIntegrityFixture.install();
        ItemsAuditFixtures.install();
        ExperienceFixtures.install();
        BlinkPreferencesFixtures.install();
        MiningAuditFixtures.install();
    }

    public void xp(FixtureSender sender, String[] args) {
        ensureInstalled();
        XpIntegrityFixture.execute(sender, args);
    }

    public boolean execute(FixtureSender sender, String[] args) {
        PlayerRef admin = sender.player();
        if (admin == null || !admin.isOp()) {
            sender.send("ADAPT_QA ERROR operator player required");
            return true;
        }
        try {
            ensureInstalled();
            if (args.length == 3 && args[0].equals("setup")) {
                setup(sender, admin, args[1], args[2]);
            } else if (args.length == 2 && args[0].equals("stage")) {
                requireSetup();
                Loc site = stageSite(args[1]);
                if (onSite(admin, site)) {
                    stage(args[1]);
                    Fixtures.afterCommands(site, () -> sender.send("ADAPT_QA STAGE " + args[1]));
                } else {
                    relocate(admin, site).whenComplete((Void ignored, Throwable failure) -> Tasks.runEntity(admin, () -> stageOnSite(sender, args[1], failure)));
                }
            } else if (args.length == 3 && args[0].equals("snapshot")) {
                PlayerRef subject = ordinaryPlayer(args[1]);
                if (Tasks.isOwnedByCurrentRegion(subject)) {
                    sendJson(sender, "SNAPSHOT " + args[2], snapshot(args[1]));
                } else {
                    // On Folia the operator can stand in another region (Iris moves it into its new world).
                    Tasks.runEntity(subject, () -> snapshotOwned(sender, admin, args[1], args[2]));
                }
            } else if (args.length == 2 && args[0].equals("catalog")) {
                sendJson(sender, "CATALOG " + args[1], catalog());
            } else if (args.length == 3 && (args[0].equals("learn") || args[0].equals("learn-add"))) {
                int level = Integer.parseInt(args[2]);
                learn(args[1], level, args[0].equals("learn"));
                sender.send("ADAPT_QA " + (args[0].equals("learn") ? "LEARN " : "LEARN-ADD ") + args[1] + " " + level);
            } else if (args.length == 3 && args[0].equals("entity-ai")) {
                requireSetup();
                boolean enabled = Boolean.parseBoolean(args[2]);
                int changed = 0;
                for (EntityRef entity : actor.world().entities()) {
                    if (entity instanceof LivingRef living && Fixtures.tags(living).contains(args[1])) {
                        living.setAi(enabled);
                        changed++;
                    }
                }
                sender.send("ADAPT_QA ENTITY-AI " + args[1] + " " + changed);
            } else if (args.length == 2 && args[0].equals("fill-storage")) {
                requireSetup();
                for (int slot = 0; slot < 36; slot++) {
                    actor.inventory().setItem(slot, Fixtures.item(args[1], 64));
                }
                actor.updateInventory();
                sender.send("ADAPT_QA FILL-STORAGE " + args[1]);
            } else if (args.length == 2 && args[0].equals("effects")) {
                requireSetup();
                if (!args[1].equals("true") && !args[1].equals("false")) {
                    throw new IllegalArgumentException("Effects requires true or false");
                }
                runtimePlayer(actor).getData().setEffectsEnabled(Boolean.parseBoolean(args[1]));
                sender.send("ADAPT_QA EFFECTS " + args[1]);
            } else if (args.length == 2 && args[0].equals("earth-refill")) {
                requireSetup();
                String soil = switch (args[1]) {
                    case "sand" -> "sand";
                    case "dirt" -> "dirt";
                    default -> throw new IllegalArgumentException("Soil must be sand or dirt");
                };
                EarthFixtures.refill(arena, soil);
                sender.send("ADAPT_QA EARTH-REFILL " + args[1]);
            } else if (args.length == 1 && args[0].equals("feedback")) {
                requireSetup();
                Loc at = actor.location();
                AdaptCore.platform().fx().particle(actor, ParticleSpec.dust(Rgb.of(0xFF0000), 1.0F), at.x(), at.y(), at.z(), 2, 0, 0, 0, 0);
                Fx.now(Fixtures.skill("agility"), actor.location(), FxPriority.GAMEPLAY).dustBurst(3, 0.1D, 1.0F);
                sender.send("ADAPT_QA FEEDBACK DUST");
            } else if (args.length >= 3 && args[0].equals("experience")) {
                sendJson(sender, "EXPERIENCE " + args[2], ExperienceFixtures.execute(ordinaryPlayer(args[1]), args));
            } else if (args.length >= 3 && args[0].equals("golem-controls")) {
                sendJson(sender, "GOLEM_CONTROLS " + args[2], GolemControlsFixtures.execute(ordinaryPlayer(args[1]), args));
            } else if (args.length == 3 && args[0].equals("golem-menu")) {
                sendJson(sender, "GOLEM_MENU " + args[2], GolemMenuFixtures.execute(Objects.requireNonNull(Fixtures.host().player(args[1]), "Player is not online"), args));
            } else if (args.length >= 3 && args[0].equals("hud")) {
                sendJson(sender, "HUD " + args[2], HudFixtures.execute(Objects.requireNonNull(Fixtures.host().player(args[1]), "Player is not online"), args));
            } else if (args.length >= 3 && args[0].equals("golem-lifecycle")) {
                sendJson(sender, "GOLEM_LIFECYCLE " + args[2], GolemLifecycleFixtures.execute(ordinaryPlayer(args[1]), args));
            } else if (args.length >= 3 && args[0].equals("lumber-replant")) {
                sendJson(sender, "LUMBER_REPLANT " + args[2], LumberReplantFixtures.execute(ordinaryPlayer(args[1]), args));
            } else if (args.length >= 3 && args[0].equals("custom-recipes")) {
                sendJson(sender, "CUSTOM_RECIPES " + args[2], CustomRecipeFixtures.execute(ordinaryPlayer(args[1]), args));
            } else if (args.length >= 3 && args[0].equals("temporal")) {
                sendJson(sender, "TEMPORAL " + args[2], TemporalAuditFixtures.execute(ordinaryPlayer(args[1]), args));
            } else if (args.length >= 3 && args[0].equals("mining-audit")) {
                sendJson(sender, "MINING_AUDIT " + args[2], MiningAuditFixtures.execute(ordinaryPlayer(args[1]), args));
            } else if (args.length >= 3 && args[0].equals("demolition")) {
                sendJson(sender, "DEMOLITION " + args[2], DemolitionFixtures.execute(ordinaryPlayer(args[1]), args));
            } else if (args.length >= 3 && args[0].equals("appearance")) {
                sendJson(sender, "APPEARANCE " + args[2], AppearanceFixtures.execute(ordinaryPlayer(args[1]), args));
            } else if (args.length >= 3 && args[0].equals("catalog-behavior")) {
                sendJson(sender, "CATALOG_BEHAVIOR " + args[2], CatalogPreferenceFixtures.execute(ordinaryPlayer(args[1]), args));
            } else if (args.length >= 3 && args[0].equals("preferences")) {
                sendJson(sender, "PREFERENCES " + args[2], PreferenceFixtures.execute(ordinaryPlayer(args[1]), args));
            } else if (args.length >= 3 && args[0].equals("blink")) {
                sendJson(sender, "BLINK " + args[2], BlinkPreferencesFixtures.execute(ordinaryPlayer(args[1]), args));
            } else if (args.length == 3 && args[0].equals("afford")) {
                afford(sender, ordinaryPlayer(args[1]), args[2]);
            } else if (args.length == 2 && args[0].equals("attribute-registry")) {
                sendJson(sender, "ATTRIBUTE_REGISTRY " + args[1], ProtocolFixtures.attributes());
            } else if (args.length >= 3 && args[0].equals("items-audit")) {
                sendJson(sender, "ITEMS_AUDIT " + args[2], ItemsAuditFixtures.execute(ordinaryPlayer(args[1]), args));
            } else if (args.length == 1 && args[0].equals("cleanup")) {
                cleanup();
                sender.send("ADAPT_QA CLEANUP");
            } else {
                return false;
            }
        } catch (RuntimeException failure) {
            Fixtures.host().log().error("Adapt gameplay fixture command failed", failure);
            sender.send("ADAPT_QA ERROR " + failure.getMessage());
        }
        return true;
    }

    private void snapshotOwned(FixtureSender sender, PlayerRef admin, String name, String label) {
        try {
            JsonObject value = snapshot(name);
            Tasks.runEntity(admin, () -> sendJson(sender, "SNAPSHOT " + label, value));
        } catch (RuntimeException failure) {
            Fixtures.host().log().error("Adapt gameplay fixture command failed", failure);
            Tasks.runEntity(admin, () -> sender.send("ADAPT_QA ERROR " + failure.getMessage()));
        }
    }

    public static void sendJson(FixtureSender sender, String response, JsonObject value) {
        String json = asciiJson(value.toString());
        int total = (json.length() + 2499) / 2500;
        for (int index = 0; index < total; index++) {
            sender.send("ADAPT_QA " + response + " " + index + " " + total + " "
                    + json.substring(index * 2500, Math.min(json.length(), (index + 1) * 2500)));
        }
    }

    private static String asciiJson(String json) {
        StringBuilder escaped = new StringBuilder(json.length());
        for (int index = 0; index < json.length(); index++) {
            char character = json.charAt(index);
            if (character >= 0x20 && character < 0x7F) {
                escaped.append(character);
            } else {
                escaped.append(String.format("\\u%04x", (int) character));
            }
        }
        return escaped.toString();
    }

    public PlayerRef ordinaryPlayer(String name) {
        PlayerRef player = Objects.requireNonNull(Fixtures.host().player(name), "Player is not online");
        if (player.isOp() || !Fixtures.ownsDemoState(player.name())) {
            throw new IllegalArgumentException("Fixture actor must be an ordinary studio player");
        }
        return player;
    }

    private void requireSetup() {
        if (arena == null || actor == null) {
            throw new IllegalStateException("Setup required");
        }
    }

    private void setup(FixtureSender sender, PlayerRef admin, String name, String otherName) {
        if (arena != null) {
            throw new IllegalStateException("Cleanup the previous fixture first");
        }
        actor = ordinaryPlayer(name);
        opponent = ordinaryPlayer(otherName);
        if (actor.uuid().equals(opponent.uuid())) {
            throw new IllegalArgumentException("Two distinct actors required");
        }
        arena = platform.createArena("adapt_gameplay_" + UUID.randomUUID().toString().replace("-", ""));
        for (PlayerRef player : new PlayerRef[]{admin, actor, opponent}) {
            origins.put(player.uuid(), player.location());
            gameModes.put(player.uuid(), player.gameMode());
        }
        Loc site = Fixtures.loc(arena, 0.5, 100, 0.5);
        if (onSite(admin, site)) {
            finishSetup(admin);
            sender.send("ADAPT_QA SETUP " + arena.name());
            return;
        }
        relocate(admin, site).whenComplete((Void ignored, Throwable failure) -> Tasks.runEntity(admin, () -> {
            if (failure != null) {
                sender.send("ADAPT_QA ERROR " + failure.getMessage());
                return;
            }
            try {
                finishSetup(admin);
                sender.send("ADAPT_QA SETUP " + arena.name());
            } catch (RuntimeException setupFailure) {
                Fixtures.host().log().error("Adapt gameplay fixture command failed", setupFailure);
                sender.send("ADAPT_QA ERROR " + setupFailure.getMessage());
            }
        }));
    }

    private void finishSetup(PlayerRef admin) {
        // A long run reaches a natural rain cycle; rain melts snow golems and changes later cases.
        Fixtures.gameRule(arena, "advance_weather", "false");
        Fixtures.commandIn(arena, "weather clear");
        resetArena();
        for (PlayerRef player : new PlayerRef[]{admin, actor, opponent}) {
            Fixtures.teleport(player, arena, 0.5, 100, player == admin ? 12.5 : 0.5);
        }
        admin.setGameMode(GameMode.SPECTATOR);
        stage("agility");
    }

    private Loc stageSite(String name) {
        Loc integration = platform.integrationSite(name);
        if (integration != null) {
            return integration;
        }
        if (NetherFixtures.usesNether(name) || NatureFixtures.usesNether(name)) {
            return Fixtures.loc(NetherFixtures.world(arena), 0.5, 100, 0.5);
        }
        if (DiscoveryFixtures.usesExploration(name)) {
            return DiscoveryFixtures.explorationSite(arena);
        }
        if (name.equals("move-pressure")) {
            return MovementFixtures.pressureSite(arena);
        }
        return Fixtures.loc(arena, 0.5, 100, 0.5);
    }

    private boolean onSite(PlayerRef admin, Loc site) {
        if (!Tasks.isFoliaThreading()) {
            return true;
        }
        for (PlayerRef player : new PlayerRef[]{admin, actor, opponent}) {
            if (!player.world().uid().equals(site.world().uid()) || !Tasks.isOwnedByCurrentRegion(player)) {
                return false;
            }
        }
        return Tasks.isOwnedByCurrentRegion(site, 24, 24);
    }

    private CompletableFuture<Void> relocate(PlayerRef admin, Loc site) {
        return CompletableFuture.allOf(
                admin.teleport(site.add(0, 0, 12), TeleportCause.PLUGIN),
                actor.teleport(site, TeleportCause.PLUGIN),
                opponent.teleport(site.add(2, 0, 0), TeleportCause.PLUGIN));
    }

    private void stageOnSite(FixtureSender sender, String name, Throwable failure) {
        if (failure != null) {
            sender.send("ADAPT_QA ERROR " + failure.getMessage());
            return;
        }
        try {
            stage(name);
            Fixtures.afterCommands(stageSite(name), () -> sender.send("ADAPT_QA STAGE " + name));
        } catch (RuntimeException stageFailure) {
            Fixtures.host().log().error("Adapt gameplay fixture command failed", stageFailure);
            sender.send("ADAPT_QA ERROR " + stageFailure.getMessage());
        }
    }

    private void stage(String name) {
        requireSetup();
        boolean arenaOwned = Tasks.isOwnedByCurrentRegion(arena, 0, 0);
        boolean onArena = stageSite(name).world().uid().equals(arena.uid());
        if (arenaOwned) {
            removeEntities();
        }
        if (name.equals("golemancy") || name.startsWith("golemancy-")) {
            GolemancyFixtures.clearRosters(actor, opponent);
        }
        TraversalFixtures.reset();
        DiscoveryFixtures.reset();
        if (arenaOwned) {
            resetArena();
            removeEntities();
        }
        for (PlayerRef player : new PlayerRef[]{actor, opponent}) {
            NatureFixtures.reset(player);
            player.setFallDistance(0F);
            player.setVelocity(Vec3.ZERO);
            player.closeInventory();
            Fixtures.clearInventory(player);
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(Math.min(20, player.attributeValue(MAX_HEALTH)));
            player.setFoodLevel(20);
            player.setSaturation(5);
            player.setExhaustion(0F);
            player.setFireTicks(0);
            Fixtures.platform().restoreAir(player);
            player.setFreezeTicks(0);
            player.setNoDamageTicks(0);
            // A pet staged with AI attacks whatever the owner last hit (owner-hurt target goals), even in an earlier case.
            Fixtures.platform().forgetCombat(player);
            for (EffectInstance effect : player.effects()) {
                player.removeEffect(effect.type());
            }
            player.setAbsorption(0);
        }
        if (onArena) {
            // Night stages (utility, rift) leave the clock at 18000 for every later case otherwise.
            Fixtures.commandIn(arena, "time set 6000");
            Fixtures.teleport(actor, Fixtures.loc(arena, 0.5, 100, 0.5, -90, 0));
            Fixtures.teleport(opponent, Fixtures.loc(arena, 2.5, 100, 0.5, 90, 0));
            Fixtures.set(arena, 2, 100, 2, "air");
        }
        switch (name) {
            case "agility", "unarmed", "stealth", "tragoul" -> {
            }
            case "discovery" -> Fixtures.set(arena, -2, 100, 2, "amethyst_block");
            case "axes" -> Fixtures.give(actor, "wooden_axe", 1);
            case "swords" -> Fixtures.give(actor, "wooden_sword", 1);
            case "combat-unarmed" -> {
                opponent.setFoodLevel(17);
                opponent.setSaturation(0);
            }
            case "combat-swords", "sword-low-health", "swords-dual" -> {
                Fixtures.give(actor, "wooden_sword", 1);
                opponent.setFoodLevel(17);
                opponent.setSaturation(0);
                if (name.equals("sword-low-health")) {
                    opponent.setHealth(10);
                }
                if (name.equals("swords-dual")) {
                    actor.inventory().setOffHand(Fixtures.item("wooden_sword"));
                }
            }
            case "axes-armored" -> {
                Fixtures.give(actor, "wooden_axe", 1);
                opponent.inventory().setArmor(EquipmentSlot.CHEST, Fixtures.item("iron_chestplate"));
                opponent.setFoodLevel(17);
                opponent.setSaturation(0);
            }
            case "blocking" -> {
                Fixtures.give(opponent, "wooden_sword", 1);
                actor.inventory().setOffHand(Fixtures.item("shield"));
            }
            case "block-log", "block-leaves", "block-ore", "block-iron", "block-clay",
                 "block-pumpkin", "block-carrots", "block-glass", "block-stone-plane", "block-clay-plane" -> stageBlock(name);
            case "architect" -> Fixtures.give(actor, "stone_bricks", 8);
            case "pickaxe" -> {
                Fixtures.give(actor, "iron_pickaxe", 1);
                Fixtures.set(arena, 2, 100, 2, "diamond_ore");
            }
            case "excavation" -> {
                Fixtures.give(actor, "iron_shovel", 1);
                Fixtures.set(arena, 3, 100, 2, "clay");
            }
            case "nether" -> Fixtures.set(arena, 2, 100, 2, "wither_rose");
            case "crafting" -> Fixtures.give(actor, "oak_log", 4);
            case "herbalism" -> {
                actor.setFoodLevel(10);
                Fixtures.give(actor, "apple", 2);
            }
            case "brewing", "chronos" -> {
                ItemRef potion = Fixtures.item("potion");
                potion.setPotionContents(new PotionContents(Id.minecraft("swiftness"), null, java.util.List.of(), null));
                actor.inventory().addItem(potion);
            }
            case "ranged" -> {
                Fixtures.give(actor, "bow", 1);
                Fixtures.give(actor, "arrow", 16);
            }
            case "rift" -> Fixtures.give(actor, "ender_pearl", 2);
            case "seaborne" -> {
                for (int x = -3; x <= 3; x++) {
                    for (int z = -3; z <= 3; z++) {
                        Fixtures.set(arena, x, 98, z, "stone");
                        Fixtures.set(arena, x, 99, z, "water");
                        Fixtures.set(arena, x, 100, z, "water");
                    }
                }
                Fixtures.set(arena, 2, 99, 2, "dirt");
                Fixtures.teleport(actor, arena, 0.5, 99, 0.5);
                Fixtures.give(actor, "iron_shovel", 1);
            }
            case "hunter" -> {
                Fixtures.give(actor, "wooden_sword", 1);
                Fixtures.spawnStill(arena, "cow", 2.5, 100, 2.5).setHealth(1);
            }
            case "taming" -> {
                Fixtures.give(actor, "wheat", 32);
                Fixtures.spawn(arena, "cow", 2.5, 100, 2.5);
                Fixtures.spawn(arena, "cow", -1.5, 100, 2.5);
            }
            case "enchanting" -> {
                actor.setLevel(30);
                Fixtures.give(actor, "iron_sword", 1);
                Fixtures.give(actor, "lapis_lazuli", 64);
                Fixtures.set(arena, 2, 100, 2, "enchanting_table");
            }
            case "kinetics" -> {
                for (int x = -2; x <= 6; x++) {
                    for (int z = -2; z <= 2; z++) {
                        Fixtures.set(arena, x, 99, z, "hay_block");
                    }
                }
                Fixtures.set(arena, 0, 107, 0, "stone");
                Fixtures.teleport(actor, Fixtures.loc(arena, 0.5, 108, 0.5, -90, 0));
            }
            case "golemancy" -> {
                Fixtures.give(actor, "snow_block", 2);
                Fixtures.give(actor, "carved_pumpkin", 1);
            }
            default -> {
                if (!BrewingFixtures.stage(name, arena, actor) && !MovementFixtures.stage(name, arena, actor)
                        && !RangedFixtures.stage(name, arena, actor, opponent) && !ArchitectFixtures.stage(name, arena, actor)
                        && !TamingFixtures.stage(name, arena, actor, opponent) && !ChronosFixtures.stage(name, arena, actor, opponent)
                        && !NetherFixtures.stage(name, arena, actor, opponent)
                        && !DefenseFixtures.stage(name, arena, actor, opponent)
                        && !HarvestFixtures.stage(name, arena, actor, opponent)
                        && !TragoulFixtures.stage(name, arena, actor, opponent)
                        && !AreaTargetingFixtures.stage(name, arena, actor, opponent)
                        && !ProjectileTargetingFixtures.stage(name, arena, actor, opponent)
                        && !KnowledgeFixtures.stage(name, arena, actor, opponent)
                        && !RiftFixtures.stage(name, arena, actor, opponent)
                        && !CraftingFixtures.stage(name, arena, actor, opponent)
                        && !EarthFixtures.stage(name, arena, actor, opponent)
                        && !ProjectileFixtures.stage(name, arena, actor, opponent)
                        && !GuardFixtures.stage(name, arena, actor, opponent)
                        && !DiscoveryFixtures.stage(name, arena, actor, opponent)
                        && !KineticsFixtures.stage(name, arena, actor, opponent)
                        && !UtilityFixtures.stage(name, arena, actor, opponent)
                        && !NatureFixtures.stage(name, arena, actor, opponent)
                        && !platform.integrationStage(name, arena, actor, opponent)
                        && !TraversalFixtures.stage(name, arena, actor, opponent)
                        && !GolemancyFixtures.stage(name, arena, actor, opponent)) {
                    throw new IllegalArgumentException("Unknown skill stage " + name);
                }
            }
        }
        // Bukkit sends the slot clears directly without updating the menu's remote slots, so an item
        // re-staged into the same slot (a fresh shield replacing the last trial's shield) is never resent.
        actor.updateInventory();
        opponent.updateInventory();
    }

    private void removeEntities() {
        for (EntityRef entity : arena.entities()) {
            if (!(entity instanceof PlayerRef)) {
                entity.remove();
            }
        }
    }

    private void stageBlock(String name) {
        Fixtures.teleport(opponent, arena, -5.5, 100, 5.5);
        String tool = switch (name) {
            case "block-log", "block-leaves" -> "diamond_axe";
            case "block-ore", "block-iron", "block-stone-plane" -> "diamond_pickaxe";
            case "block-clay", "block-clay-plane" -> "diamond_shovel";
            case "block-pumpkin", "block-carrots" -> "diamond_hoe";
            default -> null;
        };
        if (tool != null) {
            Fixtures.give(actor, tool, 1);
        }
        String material = switch (name) {
            case "block-log" -> "oak_log";
            case "block-leaves" -> "oak_leaves";
            case "block-ore" -> "diamond_ore";
            case "block-iron" -> "iron_ore";
            case "block-clay", "block-clay-plane" -> "clay";
            case "block-pumpkin" -> "pumpkin";
            case "block-carrots" -> "carrots";
            case "block-glass" -> "glass";
            default -> "stone";
        };
        Fixtures.set(arena, 3, 100, 0, material);
        if (name.equals("block-log") || name.equals("block-leaves")) {
            for (int y = 100; y <= 102; y++) {
                Fixtures.set(arena, 3, y, 0, material.equals("oak_leaves") ? "oak_leaves[persistent=true]" : material);
            }
        }
        if (name.equals("block-ore")) {
            for (int x = 3; x <= 4; x++) {
                for (int y = 100; y <= 101; y++) {
                    Fixtures.set(arena, x, y, 0, material);
                }
            }
        }
        if (name.endsWith("-plane")) {
            for (int y = 99; y <= 101; y++) {
                for (int z = -1; z <= 1; z++) {
                    Fixtures.set(arena, 3, y, z, material);
                }
            }
        }
        if (name.equals("block-carrots")) {
            Fixtures.set(arena, 3, 99, 0, "farmland[moisture=7]");
            Fixtures.set(arena, 3, 100, 0, "carrots[age=7]");
        }
    }

    private void resetArena() {
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                if (!arena.blockAt(x, 99, z).type().path().equals("stone")) {
                    Fixtures.set(arena, x, 99, z, "stone");
                }
                for (int y = 100; y <= 108; y++) {
                    if (!arena.blockAt(x, y, z).isAir()) {
                        Fixtures.set(arena, x, y, z, "air");
                    }
                }
            }
        }
    }

    private JsonObject catalog() {
        JsonObject result = new JsonObject();
        JsonArray skills = new JsonArray();
        for (Skill<?> skill : Fixtures.core().getAdaptServer().getSkillRegistry().getAllSkills()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", skill.getName());
            entry.addProperty("enabled", skill.isEnabled());
            JsonArray adaptations = new JsonArray();
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                JsonObject detail = new JsonObject();
                detail.addProperty("name", adaptation.getName());
                detail.addProperty("enabled", adaptation.isEnabled());
                detail.addProperty("maxLevel", adaptation.getMaxLevel());
                adaptations.add(detail);
            }
            entry.add("adaptations", adaptations);
            skills.add(entry);
        }
        result.add("skills", skills);
        result.addProperty("platform", platform.name());
        return result;
    }

    private static void afford(FixtureSender sender, PlayerRef subject, String skill) {
        Tasks.runEntity(subject, () -> {
            try {
                AdaptPlayer adaptPlayer = runtimePlayer(subject);
                adaptPlayer.getData().getSkillLine(skill).giveKnowledge(1_000L);
                adaptPlayer.getData().giveMasterXp(XP.getXpForLevel(30));
                sender.send("ADAPT_QA AFFORD " + skill);
            } catch (RuntimeException failure) {
                sender.send("ADAPT_QA ERROR " + failure.getMessage());
            }
        });
    }

    private void learn(String name, int level, boolean exclusive) {
        requireSetup();
        AdaptPlayer adaptPlayer = runtimePlayer(actor);
        Adaptation<?> selected = Fixtures.adaptation(name);
        if (!selected.isEnabled() || !selected.getSkill().isEnabled()) {
            throw new IllegalArgumentException("Adaptation is disabled " + name);
        }
        if (level < 0 || level > selected.getMaxLevel()) {
            throw new IllegalArgumentException("Level outside adaptation range");
        }
        for (Skill<?> skill : Fixtures.core().getAdaptServer().getSkillRegistry().getAllSkills()) {
            PlayerSkillLine line = adaptPlayer.getData().getSkillLine(skill.getName());
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (exclusive || adaptation == selected) {
                    int target = adaptation == selected ? level : 0;
                    boolean unlearned = target == 0 && line.getAdaptationLevel(adaptation.getName()) > 0;
                    line.setAdaptation(adaptation, target);
                    if (unlearned) {
                        PlayerPreferences.reconcile(adaptation, adaptPlayer);
                    }
                }
            }
        }
    }

    public static AdaptPlayer runtimePlayer(PlayerRef player) {
        AdaptPlayer adaptPlayer = Fixtures.adaptPlayer(player);
        if (adaptPlayer == null || !adaptPlayer.isRuntimeReady()) {
            throw new IllegalStateException("Adapt player is not ready");
        }
        return adaptPlayer;
    }

    private JsonObject snapshot(String name) {
        PlayerRef player = ordinaryPlayer(name);
        AdaptPlayer adaptPlayer = runtimePlayer(player);
        JsonObject result = new JsonObject();
        result.addProperty("player", name);
        result.addProperty("platform", platform.name());
        result.addProperty("minecraftVersion", Fixtures.host().minecraftVersion());
        result.addProperty("operator", player.isOp());
        result.addProperty("sneaking", player.isSneaking());
        result.addProperty("effectsEnabled", adaptPlayer.getData().isEffectsEnabled());
        result.addProperty("health", player.health());
        result.addProperty("absorption", player.absorption());
        result.addProperty("processId", ProcessHandle.current().pid());
        result.addProperty("food", player.foodLevel());
        result.add("ranged", RangedFixtures.snapshot(player));
        result.add("taming", TamingFixtures.snapshot(player));
        result.add("nether", NetherFixtures.snapshot(player));
        result.add("defense", DefenseFixtures.snapshot(player));
        result.add("combatAudit", CombatAuditFixtures.snapshot(player));
        result.add("harvest", HarvestFixtures.snapshot(player));
        result.add("tragoul", TragoulFixtures.snapshot(player));
        result.add("areaTargets", AreaTargetingFixtures.snapshot(player));
        result.add("projectileTargeting", ProjectileTargetingFixtures.snapshot(player));
        result.add("knowledge", KnowledgeFixtures.snapshot(player));
        result.add("rift", RiftFixtures.snapshot(player));
        result.add("crafting", CraftingFixtures.snapshot(player));
        result.add("traversal", TraversalFixtures.snapshot(player));
        result.add("chronos", ChronosFixtures.snapshot(player));
        result.add("nature", NatureFixtures.snapshot(player));
        result.add("integration", platform.integrationSnapshot(player));
        result.add("utility", UtilityFixtures.snapshot(player));
        result.add("kinetics", KineticsFixtures.snapshot(player));
        result.add("discovery", DiscoveryFixtures.snapshot(player));
        result.add("guard", GuardFixtures.snapshot(player));
        result.add("projectile", ProjectileFixtures.snapshot(player));
        result.add("earth", EarthFixtures.snapshot(player));
        result.add("golemancy", GolemancyFixtures.snapshot(player));
        result.addProperty("saturation", player.saturation());
        result.addProperty("allowFlight", player.allowFlight());
        result.addProperty("flying", player.isFlying());
        result.addProperty("remainingAir", player.remainingAir());
        result.addProperty("inWater", player.isInWater());
        result.addProperty("onGround", player.isOnGround());
        result.addProperty("blocking", player.isBlocking());
        result.addProperty("sneaking", player.isSneaking());
        result.addProperty("feedbackTransitionDensity", FxBudget.densityScalar(FxPriority.TRANSITION));
        result.addProperty("feedbackLoadBand", FxBudget.shedBand());
        result.addProperty("tps", platform.tps());
        result.addProperty("world", player.world().name());
        AdaptPlayer.FxPosition fxPosition = adaptPlayer.getFxPosition();
        if (fxPosition != null) {
            JsonObject feedbackPosition = new JsonObject();
            feedbackPosition.addProperty("world", fxPosition.world().name());
            feedbackPosition.addProperty("x", fxPosition.x());
            feedbackPosition.addProperty("y", fxPosition.y());
            feedbackPosition.addProperty("z", fxPosition.z());
            result.add("feedbackPosition", feedbackPosition);
        }
        Loc position = player.location();
        JsonObject location = new JsonObject();
        location.addProperty("x", position.x());
        location.addProperty("y", position.y());
        location.addProperty("z", position.z());
        location.addProperty("yaw", position.yaw());
        location.addProperty("pitch", position.pitch());
        result.add("location", location);
        Vec3 currentVelocity = player.velocity();
        JsonObject velocity = new JsonObject();
        velocity.addProperty("x", currentVelocity.x());
        velocity.addProperty("y", currentVelocity.y());
        velocity.addProperty("z", currentVelocity.z());
        result.add("velocity", velocity);
        Vec3 currentMovement = player.movement();
        JsonObject movement = new JsonObject();
        movement.addProperty("x", currentMovement.x());
        movement.addProperty("y", currentMovement.y());
        movement.addProperty("z", currentMovement.z());
        result.add("movement", movement);
        PlayerInput currentInput = AdaptPorts.get(PlayerInputPort.class, PlayerInputPort.NONE).currentInput(player);
        JsonObject input = new JsonObject();
        input.addProperty("forward", currentInput != null && currentInput.forward());
        input.addProperty("backward", currentInput != null && currentInput.backward());
        input.addProperty("left", currentInput != null && currentInput.left());
        input.addProperty("right", currentInput != null && currentInput.right());
        input.addProperty("jump", currentInput != null && currentInput.jump());
        input.addProperty("sneak", currentInput != null && currentInput.sneak());
        input.addProperty("sprint", currentInput != null && currentInput.sprint());
        result.add("input", input);
        JsonObject xp = new JsonObject();
        JsonObject committedXp = new JsonObject();
        JsonObject pooledXp = new JsonObject();
        JsonObject learned = new JsonObject();
        JsonArray registered = new JsonArray();
        JsonArray enabled = new JsonArray();
        for (Skill<?> skill : Fixtures.core().getAdaptServer().getSkillRegistry().getAllSkills()) {
            String skillName = skill.getName();
            registered.add(skillName);
            if (skill.isEnabled()) {
                enabled.add(skillName);
            }
            PlayerSkillLine line = adaptPlayer.getData().getSkillLineNullable(skillName);
            xp.addProperty(skillName, line == null ? 0 : line.getXp() + line.getPooledXp());
            committedXp.addProperty(skillName, line == null ? 0 : line.getXp());
            pooledXp.addProperty(skillName, line == null ? 0 : line.getPooledXp());
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                learned.addProperty(adaptation.getName(), line == null ? 0 : line.getAdaptationLevel(adaptation.getName()));
            }
        }
        result.add("registered", registered);
        result.add("enabled", enabled);
        result.add("xp", xp);
        result.add("committedXp", committedXp);
        result.add("pooledXp", pooledXp);
        result.addProperty("pooledWindowMillis", AdaptConfig.get().getXpIntegrity().getPooledWindowMillis());
        result.addProperty("pooledIdleFlushMillis", AdaptConfig.get().getXpIntegrity().getPooledIdleFlushMillis());
        result.add("learned", learned);
        JsonObject stats = new JsonObject();
        for (Map.Entry<String, Double> entry : adaptPlayer.getData().getStats().entrySet()) {
            stats.addProperty(entry.getKey(), entry.getValue());
        }
        result.add("stats", stats);
        JsonObject attributes = new JsonObject();
        for (Id attribute : Fixtures.host().registries().ids(RegistryKind.ATTRIBUTE)) {
            if (player.hasAttribute(attribute)) {
                attributes.addProperty(attribute.toString(), player.attributeValue(attribute));
            }
        }
        result.add("attributes", attributes);
        JsonArray effects = new JsonArray();
        for (EffectInstance effect : player.effects()) {
            JsonObject detail = new JsonObject();
            detail.addProperty("type", effect.type().toString());
            detail.addProperty("amplifier", effect.amplifier());
            detail.addProperty("duration", effect.durationTicks());
            effects.add(detail);
        }
        result.add("effects", effects);
        return result;
    }

    /**
     * Where the arena is a dimension players also join in (Folia, modded), a recorded origin can lie inside the
     * arena; parking returning players at the world spawn keeps idle bots out of later stages' lines of fire.
     */
    private Loc outsideArena(Loc destination) {
        if (!destination.world().uid().equals(arena.uid()) || Math.abs(destination.x()) > ARENA_RADIUS
                || Math.abs(destination.z()) > ARENA_RADIUS || destination.y() < ARENA_FLOOR - 4) {
            return destination;
        }
        return arena.spawnLocation();
    }

    private void cleanup() {
        ExperienceFixtures.cleanup();
        GolemMenuFixtures.cleanup();
        if (arena == null) {
            return;
        }
        TraversalFixtures.reset();
        List<CompletableFuture<Boolean>> returns = new ArrayList<>();
        for (Map.Entry<UUID, Loc> entry : origins.entrySet()) {
            PlayerRef player = Fixtures.host().player(entry.getKey());
            if (player != null) {
                NatureFixtures.reset(player);
                returns.add(player.teleport(outsideArena(entry.getValue()), TeleportCause.PLUGIN));
                player.setGameMode(gameModes.get(entry.getKey()));
            }
        }
        if (!origins.isEmpty()) {
            Loc fallback = outsideArena(origins.values().iterator().next());
            for (PlayerRef straggler : arena.players()) {
                if (!origins.containsKey(straggler.uuid())) {
                    returns.add(straggler.teleport(fallback, TeleportCause.PLUGIN));
                }
            }
        }
        origins.clear();
        gameModes.clear();
        ProjectileTargetingFixtures.cleanup();
        WorldRef returned = arena;
        CompletableFuture.allOf(returns.toArray(CompletableFuture[]::new))
                .whenComplete((Void ignored, Throwable failure) -> AdaptCore.platform().scheduler().runGlobal(() -> {
                    NetherFixtures.cleanup();
                    DiscoveryFixtures.cleanup();
                    platform.unloadWorld(returned);
                }));
        arena = null;
        actor = null;
        opponent = null;
    }
}
