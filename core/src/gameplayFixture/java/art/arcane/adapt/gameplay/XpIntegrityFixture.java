package art.arcane.adapt.gameplay;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.ComponentPort;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationRequirement;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.player.PlayerExpChangeEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class XpIntegrityFixture {
    private static final Gson JSON = new Gson();
    private static final int HEIGHT = 100;
    private static final Map<UUID, Trial> trials = new ConcurrentHashMap<>();
    private static final AtomicInteger nextArena = new AtomicInteger();

    private XpIntegrityFixture() {
    }

    public static void install() {
        Fixtures.register(new XpIntegrityFixture());
    }

    public static void execute(FixtureSender sender, String[] args) {
        PlayerRef admin = sender.player();
        if (admin == null || !admin.isOp() || args.length < 2) {
            sender.send("ADAPT_QA ERROR operator and actor/action required");
            return;
        }
        PlayerRef player = Fixtures.host().player(args[0]);
        if (player == null || player.isOp() || !player.name().startsWith("AQAx")) {
            sender.send("ADAPT_QA ERROR ordinary AQAx actor required");
            return;
        }
        CompletableFuture<JsonObject> response;
        try {
            response = switch (args[1]) {
                case "prepare" -> prepare(player, args.length > 2 ? args[2] : "crafting");
                case "resume" -> resume(player, Integer.parseInt(args[2]), args[3]);
                case "snapshot" -> entity(player, () -> snapshot(player, current(player)));
                case "ore" -> at(current(player).anchor(), () -> fillOre(current(player), args[2]))
                        .thenCompose(ignored -> entity(player, () -> snapshot(player, current(player))));
                case "cow" -> at(current(player).anchor(), () -> spawnCow(current(player)))
                        .thenCompose(ignored -> entity(player, () -> snapshot(player, current(player))));
                case "orbs" -> entity(player, () -> spawnOrbs(player, Integer.parseInt(args[2])));
                case "catalog" -> entity(player, XpIntegrityFixture::catalog);
                case "reload" -> reload(player);
                case "cleanup" -> cleanup(player);
                default -> CompletableFuture.failedFuture(new IllegalArgumentException("Unknown XP fixture action: " + args[1]));
            };
        } catch (RuntimeException failure) {
            fail(sender, admin, failure);
            return;
        }
        response.whenComplete((JsonObject result, Throwable failure) -> {
            if (failure != null) {
                fail(sender, admin, failure);
            } else {
                Tasks.runEntity(admin, () -> GameplayFixture.sendJson(sender, "XP " + args[1], result));
            }
        });
    }

    private static CompletableFuture<JsonObject> prepare(PlayerRef player, String mode) {
        if (trials.containsKey(player.uuid())) {
            return CompletableFuture.failedFuture(new IllegalStateException("Use a fresh actor for each trial"));
        }
        if (!Set.of("crafting", "mining", "combat", "alternating", "enchanting", "furnace", "orbs", "unity").contains(mode)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown trial mode: " + mode));
        }
        int x = 256 + nextArena.getAndIncrement() * 64;
        return entity(player, () -> {
            runtime(player);
            return new Trial(player.location(), x, mode);
        }).thenCompose((Trial trial) -> trial.world.loadChunkAsync(x >> 4, 16)
                .thenCompose(chunk -> at(trial.anchor(), () -> build(trial)))
                .thenCompose(ignored -> player.teleport(trial.spawn(), TeleportCause.PLUGIN))
                .thenCompose((Boolean teleported) -> {
                    if (!teleported) {
                        return CompletableFuture.failedFuture(new IllegalStateException("XP fixture teleport refused"));
                    }
                    return entity(player, () -> {
                        player.setGameMode(GameMode.SURVIVAL);
                        player.setHealth(20D);
                        player.setFoodLevel(20);
                        player.setSaturation(20F);
                        Fixtures.clearInventory(player);
                        InventoryRef inventory = player.inventory();
                        inventory.setItem(0, Fixtures.item("diamond_pickaxe"));
                        inventory.setItem(1, Fixtures.item("diamond_sword"));
                        inventory.setItem(2, Fixtures.item("oak_log", 64));
                        inventory.setItem(3, Fixtures.item("gold_ingot", 64));
                        inventory.setItem(4, Fixtures.item("diamond_block", 64));
                        for (int slot = 6; slot < 15; slot++) {
                            inventory.setItem(slot, Fixtures.item("diamond", 64));
                        }
                        inventory.setItem(5, Fixtures.item("experience_bottle", 64));
                        player.inventory().setHeldSlot(0);
                        if (mode.equals("enchanting")) {
                            inventory.setItem(15, Fixtures.item("book", 64));
                            inventory.setItem(16, Fixtures.item("lapis_lazuli", 64));
                            player.setLevel(1000);
                        }
                        if (mode.equals("unity")) {
                            Adaptation<?> unity = Fixtures.adaptation("discovery-unity");
                            runtime(player).getData().getSkillLine(unity.getSkill().getName()).setAdaptation(unity, unity.getMaxLevel());
                        }
                        trials.put(player.uuid(), trial);
                        return snapshot(player, trial);
                    });
                }));
    }

    private static CompletableFuture<JsonObject> resume(PlayerRef player, int x, String mode) {
        if (x < 256 || x > 65536 || x % 64 != 0 || trials.containsKey(player.uuid())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid persisted trial anchor"));
        }
        return entity(player, () -> {
            Trial trial = new Trial(player.location(), x, mode);
            trials.put(player.uuid(), trial);
            return snapshot(player, trial);
        });
    }

    private static CompletableFuture<JsonObject> reload(PlayerRef player) {
        CompletableFuture<Void> loaded = new CompletableFuture<>();
        Tasks.s(() -> complete(loaded, () -> {
            if (!AdaptConfig.reload() || !Fixtures.core().getAdaptServer().getSkillRegistry().hotReloadSkillConfig("crafting")) {
                throw new IllegalStateException("XP fixture configuration reload failed");
            }
            return null;
        }));
        return loaded.thenCompose(ignored -> entity(player, () -> snapshot(player, current(player))));
    }

    private static Void build(Trial trial) {
        for (int x = trial.x; x < trial.x + 16; x++) {
            for (int z = 256; z < 272; z++) {
                Fixtures.set(trial.world, x, HEIGHT - 1, z, "stone");
                for (int y = HEIGHT; y < HEIGHT + 5; y++) {
                    Fixtures.set(trial.world, x, y, z, "air");
                }
            }
        }
        Fixtures.set(trial.world, trial.x + 7, HEIGHT, 264, "crafting_table");
        if (trial.mode.equals("enchanting")) {
            Fixtures.set(trial.world, trial.x + 7, HEIGHT, 264, "enchanting_table");
            for (int x = trial.x + 5; x <= trial.x + 9; x++) {
                for (int z = 262; z <= 266; z++) {
                    if (x == trial.x + 5 || x == trial.x + 9 || z == 262 || z == 266) {
                        Fixtures.set(trial.world, x, HEIGHT, z, "bookshelf");
                    }
                }
            }
        }
        if (trial.mode.equals("mining") || trial.mode.equals("alternating")) {
            fillOre(trial, "DIAMOND_ORE");
        }
        if (trial.mode.equals("combat")) {
            spawnCow(trial);
        }
        if (trial.mode.equals("furnace")) {
            for (int x = trial.x + 4; x <= trial.x + 6; x++) {
                for (int z = 262; z <= 264; z++) {
                    Fixtures.set(trial.world, x, HEIGHT, z, "furnace");
                    InventoryRef furnace = trial.world.blockAt(x, HEIGHT, z).container();
                    furnace.setItem(1, Fixtures.item("coal", 16));
                    furnace.setItem(0, Fixtures.item("raw_gold", 64));
                }
            }
        }
        return null;
    }

    private static Void fillOre(Trial trial, String material) {
        if (!Set.of("DIAMOND_ORE", "EMERALD_ORE", "STONE").contains(material)) {
            throw new IllegalArgumentException("Unsupported mining material");
        }
        String type = material.toLowerCase(Locale.ROOT);
        for (int y = HEIGHT; y <= HEIGHT + 2; y++) {
            for (int z = 263; z <= 265; z++) {
                Fixtures.set(trial.world, trial.x + 11, y, z, type);
            }
        }
        return null;
    }

    private static Void spawnCow(Trial trial) {
        LivingRef cow = Fixtures.spawnStill(trial.world, "cow", trial.x + 10.5D, HEIGHT, 264.5D);
        cow.setSilent(true);
        cow.setPersistent(false);
        Fixtures.tag(cow, "adapt-xp-qa");
        return null;
    }

    private static JsonObject spawnOrbs(PlayerRef player, int count) {
        if (count < 1 || count > 200) {
            throw new IllegalArgumentException("Orb count must be 1 through 200");
        }
        Trial trial = current(player);
        for (int index = 0; index < count; index++) {
            if (!Tasks.runEntity(player, () -> {
                if (trials.get(player.uuid()) == trial) {
                    player.world().spawnExperience(player.location().position(), 1);
                }
            }, 1 + index * 4)) {
                throw new IllegalStateException("Orb scheduling refused");
            }
        }
        return snapshot(player, trial);
    }

    private static JsonObject snapshot(PlayerRef player, Trial trial) {
        PlayerData data = runtime(player).getData();
        JsonObject result = new JsonObject();
        result.addProperty("player", player.name());
        result.addProperty("operator", player.isOp());
        result.addProperty("mode", trial.mode);
        result.addProperty("now", System.currentTimeMillis());
        result.addProperty("startedAt", trial.startedAt);
        result.addProperty("processId", ProcessHandle.current().pid());
        result.addProperty("x", trial.x);
        result.addProperty("masterXp", data.getMasterXp());
        result.addProperty("masterLevel", data.getLevel());
        result.addProperty("power", data.getMaxPower());
        result.addProperty("usedPower", data.getUsedPower());
        result.addProperty("playerMultiplier", data.getMultiplier());
        result.addProperty("vanillaXp", AdaptPorts.get(ComponentPort.class, ComponentPort.NONE).totalExperiencePoints(player));
        result.addProperty("orbEvents", trial.orbEvents);
        result.addProperty("orbPoints", trial.orbPoints);
        result.addProperty("craftEvents", trial.craftEvents);
        result.addProperty("breakEvents", trial.breakEvents);
        result.addProperty("hitEvents", trial.hitEvents);
        result.addProperty("finalDamage", trial.finalDamage);
        JsonObject lines = new JsonObject();
        for (Map.Entry<String, PlayerSkillLine> entry : data.getSkillLines().entrySet()) {
            PlayerSkillLine line = entry.getValue();
            JsonObject state = new JsonObject();
            state.addProperty("xp", line.getXp());
            state.addProperty("pooledXp", line.getPooledXp());
            state.addProperty("totalXp", line.getXp() + line.getPooledXp());
            state.addProperty("level", line.getLevel());
            state.addProperty("knowledge", line.getKnowledge());
            state.addProperty("multiplier", line.getMultiplier());
            state.addProperty("monotony", line.getMonotonyMultiplier());
            state.add("boosts", JSON.toJsonTree(line.getMultipliers()));
            state.add("skillPressure", JSON.toJsonTree(line.getSkillStaleness()));
            state.add("activityPressure", JSON.toJsonTree(line.getActivityStaleness()));
            state.add("adaptations", JSON.toJsonTree(line.getAdaptations()));
            state.add("budget", JSON.toJsonTree(data.getXpEarnings().budget(entry.getKey())));
            lines.add(entry.getKey(), state);
        }
        result.add("lines", lines);
        result.add("earnings", JSON.toJsonTree(data.getXpEarnings().snapshot()));
        JsonObject profile = JSON.toJsonTree(data).getAsJsonObject();
        for (String key : List.of("xpEarnings", "xpNovelty")) {
            if (profile.has(key)) {
                result.add(key, profile.get(key));
            }
        }
        JsonObject settings = JSON.toJsonTree(AdaptConfig.get()).getAsJsonObject();
        JsonObject progression = new JsonObject();
        for (String key : List.of("xpCurve", "experienceMaxLevel", "farmPrevention", "xpIntegrity", "xpEarningLimits", "loginBonus")) {
            if (settings.has(key)) {
                progression.add(key, settings.get(key));
            }
        }
        result.add("config", progression);
        result.addProperty("unity", data.getStat("discovery.unity.orbs-distributed"));
        return result;
    }

    private static JsonObject catalog() {
        JsonObject result = new JsonObject();
        result.addProperty("maximumSkillLevel", AdaptConfig.get().experienceMaxLevel);
        result.addProperty("maximumSkillXp", XP.getXpForLevel(AdaptConfig.get().experienceMaxLevel));
        result.addProperty("powerPerLevel", AdaptConfig.get().getPowerPerLevel());
        result.addProperty("masterXpBase", AdaptConfig.get().getPlayerXpPerSkillLevelUpBase());
        result.addProperty("masterXpLevelMultiplier", AdaptConfig.get().getPlayerXpPerSkillLevelUpLevelMultiplier());
        JsonArray skills = new JsonArray();
        for (Skill<?> skill : Fixtures.core().getAdaptServer().getSkillRegistry().getAllSkills()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("skill", skill.getName());
            entry.addProperty("enabled", skill.isEnabled());
            JsonArray adaptations = new JsonArray();
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                JsonObject detail = new JsonObject();
                detail.addProperty("id", adaptation.getName());
                detail.addProperty("enabled", adaptation.isEnabled());
                detail.addProperty("maximumLevel", adaptation.getMaxLevel());
                detail.addProperty("knowledge", adaptation.getCostFor(adaptation.getMaxLevel(), 0));
                JsonArray requirements = new JsonArray();
                for (AdaptationRequirement requirement : adaptation.getRequirements()) {
                    requirements.add(JSON.toJsonTree(requirement));
                }
                detail.add("requirements", requirements);
                adaptations.add(detail);
            }
            entry.add("adaptations", adaptations);
            skills.add(entry);
        }
        result.add("skills", skills);
        return result;
    }

    private static CompletableFuture<JsonObject> cleanup(PlayerRef player) {
        Trial trial = current(player);
        return at(trial.anchor(), () -> {
            for (EntityRef entity : trial.world.chunkEntities(trial.x >> 4, 16)) {
                if (!(entity instanceof PlayerRef)) {
                    Tasks.runEntity(entity, entity::remove);
                }
            }
            for (int x = trial.x + 4; x <= trial.x + 6; x++) {
                for (int z = 262; z <= 264; z++) {
                    Fixtures.set(trial.world, x, HEIGHT, z, "air");
                }
            }
            return null;
        }).thenCompose(ignored -> player.teleport(trial.origin, TeleportCause.PLUGIN))
                .thenCompose(ignored -> entity(player, () -> {
                    JsonObject result = snapshot(player, trial);
                    trials.remove(player.uuid());
                    return result;
                }));
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void experience(PlayerExpChangeEvent event) {
        Trial trial = trials.get(event.player().uuid());
        if (trial != null && event.amount() > 0) {
            trial.orbEvents++;
            trial.orbPoints += event.amount();
        }
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void craft(CraftItemEvent event) {
        Trial trial = trials.get(event.player().uuid());
        if (trial != null) {
            trial.craftEvents++;
        }
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void mine(BlockBreakEvent event) {
        Trial trial = trials.get(event.player().uuid());
        if (trial != null) {
            trial.breakEvents++;
        }
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent event) {
        if (event.damager() instanceof PlayerRef player) {
            Trial trial = trials.get(player.uuid());
            if (trial != null && event.finalDamage() > 0D) {
                trial.hitEvents++;
                trial.finalDamage += event.finalDamage();
            }
        }
    }

    private static Trial current(PlayerRef player) {
        return Objects.requireNonNull(trials.get(player.uuid()), "Prepare the XP trial first");
    }

    private static AdaptPlayer runtime(PlayerRef player) {
        AdaptPlayer runtime = Fixtures.core().getAdaptServer().getOnlineAdaptPlayer(player.uuid());
        if (runtime == null || !runtime.isRuntimeReady() || runtime.getPlayer() == null || !runtime.getPlayer().isOnline()) {
            throw new IllegalStateException("Adapt player is not ready");
        }
        return runtime;
    }

    private static <T> CompletableFuture<T> entity(EntityRef entity, Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        if (!Tasks.runEntity(entity, () -> complete(result, action))) {
            result.completeExceptionally(new IllegalStateException("XP fixture entity scheduler refused"));
        }
        return result;
    }

    private static <T> CompletableFuture<T> at(Loc location, Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        if (!Tasks.runAt(location, () -> complete(result, action))) {
            result.completeExceptionally(new IllegalStateException("XP fixture region scheduler refused"));
        }
        return result;
    }

    private static <T> void complete(CompletableFuture<T> result, Supplier<T> action) {
        try {
            result.complete(action.get());
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
        }
    }

    private static void fail(FixtureSender sender, PlayerRef admin, Throwable failure) {
        Fixtures.host().log().error("XP fixture command failed", failure);
        Tasks.runEntity(admin, () -> sender.send("ADAPT_QA ERROR XP fixture: " + failure.getMessage()));
    }

    private static final class Trial {
        private final WorldRef world;
        private final int x;
        private final String mode;
        private final Loc origin;
        private final long startedAt = System.currentTimeMillis();
        private int orbEvents;
        private int orbPoints;
        private int craftEvents;
        private int breakEvents;
        private int hitEvents;
        private double finalDamage;

        private Trial(Loc origin, int x, String mode) {
            this.world = origin.world();
            this.x = x;
            this.mode = mode;
            this.origin = origin;
        }

        private Loc anchor() {
            return new Loc(world, x + 8D, HEIGHT, 264D);
        }

        private Loc spawn() {
            return new Loc(world, x + 8.5D, HEIGHT, 264.5D, -90F, 0F);
        }
    }
}
