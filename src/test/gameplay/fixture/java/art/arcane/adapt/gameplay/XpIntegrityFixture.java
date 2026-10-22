package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationRequirement;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.util.common.scheduling.J;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Furnace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class XpIntegrityFixture extends JavaPlugin implements Listener {
    private static final Gson JSON = new Gson();
    private static final int HEIGHT = 100;
    private final Map<UUID, Trial> trials = new ConcurrentHashMap<>();
    private final AtomicInteger nextArena = new AtomicInteger();

    @Override
    public void onEnable() {
        try {
            String source = Files.readString(Path.of(".server-source"));
            if (Bukkit.getOnlineMode() || !"127.0.0.1".equals(Bukkit.getIp())
                    || !source.lines().anyMatch("isolated=true"::equals)) {
                throw new IllegalStateException("XP fixture requires an isolated offline loopback Multiplexor instance");
            }
            Bukkit.getPluginManager().registerEvents(this, this);
        } catch (IOException | IllegalStateException failure) {
            getLogger().log(Level.SEVERE, "XP fixture refused this server", failure);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player admin) || !admin.isOp() || args.length < 2) {
            sender.sendMessage("ADAPT_QA ERROR operator and actor/action required");
            return true;
        }
        Player player = Bukkit.getPlayerExact(args[0]);
        if (player == null || player.isOp() || !player.getName().startsWith("AQAx")) {
            sender.sendMessage("ADAPT_QA ERROR ordinary AQAx actor required");
            return true;
        }
        CompletableFuture<JsonObject> response;
        try {
            response = switch (args[1]) {
                case "prepare" -> prepare(player, args.length > 2 ? args[2] : "crafting");
                case "resume" -> resume(player, Integer.parseInt(args[2]), args[3]);
                case "snapshot" -> entity(player, () -> snapshot(player, current(player)));
                case "ore" -> at(current(player).anchor(), () -> fillOre(current(player), Material.valueOf(args[2])))
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
            fail(admin, failure);
            return true;
        }
        response.whenComplete((result, failure) -> {
            if (failure != null) {
                fail(admin, failure);
            } else {
                J.runEntity(admin, () -> send(admin, args[1], result));
            }
        });
        return true;
    }

    private CompletableFuture<JsonObject> prepare(Player player, String mode) {
        if (trials.containsKey(player.getUniqueId())) {
            return CompletableFuture.failedFuture(new IllegalStateException("Use a fresh actor for each trial"));
        }
        if (!Set.of("crafting", "mining", "combat", "alternating", "enchanting", "furnace", "orbs", "unity").contains(mode)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown trial mode: " + mode));
        }
        int x = 256 + nextArena.getAndIncrement() * 64;
        return entity(player, () -> {
            runtime(player);
            return new Trial(new Preparation(player.getLocation(), x, mode));
        }).thenCompose(trial -> trial.world.getChunkAtAsync(x >> 4, 16)
                .thenCompose(chunk -> at(trial.anchor(), () -> build(trial)))
                .thenCompose(ignored -> player.teleportAsync(trial.spawn()))
                .thenCompose(teleported -> {
                    if (!teleported) {
                        return CompletableFuture.failedFuture(new IllegalStateException("XP fixture teleport refused"));
                    }
                    return entity(player, () -> {
                        player.setGameMode(GameMode.SURVIVAL);
                        player.setHealth(20D);
                        player.setFoodLevel(20);
                        player.setSaturation(20F);
                        player.getInventory().clear();
                        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND_PICKAXE));
                        player.getInventory().setItem(1, new ItemStack(Material.DIAMOND_SWORD));
                        player.getInventory().setItem(2, new ItemStack(Material.OAK_LOG, 64));
                        player.getInventory().setItem(3, new ItemStack(Material.GOLD_INGOT, 64));
                        player.getInventory().setItem(4, new ItemStack(Material.DIAMOND_BLOCK, 64));
                        for (int slot = 6; slot < 15; slot++) {
                            player.getInventory().setItem(slot, new ItemStack(Material.DIAMOND, 64));
                        }
                        player.getInventory().setItem(5, new ItemStack(Material.EXPERIENCE_BOTTLE, 64));
                        player.getInventory().setHeldItemSlot(0);
                        if (mode.equals("enchanting")) {
                            player.getInventory().setItem(15, new ItemStack(Material.BOOK, 64));
                            player.getInventory().setItem(16, new ItemStack(Material.LAPIS_LAZULI, 64));
                            player.setLevel(1000);
                        }
                        if (mode.equals("unity")) {
                            for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
                                for (Adaptation<?> adaptation : skill.getAdaptations()) {
                                    if (adaptation.getName().equals("discovery-unity")) {
                                        runtime(player).getData().getSkillLine(skill.getName()).setAdaptation(adaptation, adaptation.getMaxLevel());
                                    }
                                }
                            }
                        }
                        trials.put(player.getUniqueId(), trial);
                        return snapshot(player, trial);
                    });
                }));
    }

    private CompletableFuture<JsonObject> resume(Player player, int x, String mode) {
        if (x < 256 || x > 65536 || x % 64 != 0 || trials.containsKey(player.getUniqueId())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid persisted trial anchor"));
        }
        return entity(player, () -> {
            Trial trial = new Trial(new Preparation(player.getLocation(), x, mode));
            trials.put(player.getUniqueId(), trial);
            return snapshot(player, trial);
        });
    }

    private CompletableFuture<JsonObject> reload(Player player) {
        CompletableFuture<Void> loaded = new CompletableFuture<>();
        J.s(() -> complete(loaded, () -> {
            if (!AdaptConfig.reload() || !Adapt.instance.getAdaptServer().getSkillRegistry().hotReloadSkillConfig("crafting")) {
                throw new IllegalStateException("XP fixture configuration reload failed");
            }
            return null;
        }));
        return loaded.thenCompose(ignored -> entity(player, () -> snapshot(player, current(player))));
    }

    private Void build(Trial trial) {
        for (int x = trial.x; x < trial.x + 16; x++) {
            for (int z = 256; z < 272; z++) {
                trial.world.getBlockAt(x, HEIGHT - 1, z).setType(Material.STONE, false);
                for (int y = HEIGHT; y < HEIGHT + 5; y++) {
                    trial.world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
        trial.world.getBlockAt(trial.x + 7, HEIGHT, 264).setType(Material.CRAFTING_TABLE, false);
        if (trial.mode.equals("enchanting")) {
            trial.world.getBlockAt(trial.x + 7, HEIGHT, 264).setType(Material.ENCHANTING_TABLE, false);
            for (int x = trial.x + 5; x <= trial.x + 9; x++) {
                for (int z = 262; z <= 266; z++) {
                    if (x == trial.x + 5 || x == trial.x + 9 || z == 262 || z == 266) {
                        trial.world.getBlockAt(x, HEIGHT, z).setType(Material.BOOKSHELF, false);
                    }
                }
            }
        }
        if (trial.mode.equals("mining") || trial.mode.equals("alternating")) {
            fillOre(trial, Material.DIAMOND_ORE);
        }
        if (trial.mode.equals("combat")) {
            spawnCow(trial);
        }
        if (trial.mode.equals("furnace")) {
            for (int x = trial.x + 4; x <= trial.x + 6; x++) {
                for (int z = 262; z <= 264; z++) {
                    trial.world.getBlockAt(x, HEIGHT, z).setType(Material.FURNACE, false);
                    Furnace furnace = (Furnace) trial.world.getBlockAt(x, HEIGHT, z).getState();
                    furnace.getInventory().setFuel(new ItemStack(Material.COAL, 16));
                    furnace.getInventory().setSmelting(new ItemStack(Material.RAW_GOLD, 64));
                }
            }
        }
        return null;
    }

    private Void fillOre(Trial trial, Material material) {
        if (!Set.of(Material.DIAMOND_ORE, Material.EMERALD_ORE, Material.STONE).contains(material)) {
            throw new IllegalArgumentException("Unsupported mining material");
        }
        for (int y = HEIGHT; y <= HEIGHT + 2; y++) {
            for (int z = 263; z <= 265; z++) {
                trial.world.getBlockAt(trial.x + 11, y, z).setType(material, false);
            }
        }
        return null;
    }

    private Void spawnCow(Trial trial) {
        Cow cow = trial.world.spawn(new Location(trial.world, trial.x + 10.5D, HEIGHT, 264.5D), Cow.class);
        cow.setAI(false);
        cow.setSilent(true);
        cow.setPersistent(false);
        cow.addScoreboardTag("adapt-xp-qa");
        return null;
    }

    private JsonObject spawnOrbs(Player player, int count) {
        if (count < 1 || count > 200) {
            throw new IllegalArgumentException("Orb count must be 1 through 200");
        }
        Trial trial = current(player);
        for (int index = 0; index < count; index++) {
            if (!J.runEntity(player, () -> {
                if (trials.get(player.getUniqueId()) == trial) {
                    ExperienceOrb orb = player.getWorld().spawn(player.getLocation(), ExperienceOrb.class);
                    orb.setExperience(1);
                }
            }, 1 + index * 4)) {
                throw new IllegalStateException("Orb scheduling refused");
            }
        }
        return snapshot(player, trial);
    }

    private JsonObject snapshot(Player player, Trial trial) {
        PlayerData data = runtime(player).getData();
        JsonObject result = new JsonObject();
        result.addProperty("player", player.getName());
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
        result.addProperty("vanillaXp", player.calculateTotalExperiencePoints());
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
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
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

    private CompletableFuture<JsonObject> cleanup(Player player) {
        Trial trial = current(player);
        return at(trial.anchor(), () -> {
            for (Entity entity : trial.world.getChunkAt(trial.x >> 4, 16).getEntities()) {
                if (!(entity instanceof Player)) {
                    J.runEntity(entity, entity::remove);
                }
            }
            for (int x = trial.x + 4; x <= trial.x + 6; x++) {
                for (int z = 262; z <= 264; z++) {
                    trial.world.getBlockAt(x, HEIGHT, z).setType(Material.AIR, false);
                }
            }
            return null;
        }).thenCompose(ignored -> player.teleportAsync(trial.origin))
                .thenCompose(ignored -> entity(player, () -> {
                    JsonObject result = snapshot(player, trial);
                    trials.remove(player.getUniqueId());
                    return result;
                }));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void experience(PlayerExpChangeEvent event) {
        Trial trial = trials.get(event.getPlayer().getUniqueId());
        if (trial != null && event.getAmount() > 0) {
            trial.orbEvents++;
            trial.orbPoints += event.getAmount();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void craft(CraftItemEvent event) {
        Trial trial = trials.get(event.getWhoClicked().getUniqueId());
        if (trial != null) {
            trial.craftEvents++;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void mine(BlockBreakEvent event) {
        Trial trial = trials.get(event.getPlayer().getUniqueId());
        if (trial != null) {
            trial.breakEvents++;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            Trial trial = trials.get(player.getUniqueId());
            if (trial != null && event.getFinalDamage() > 0D) {
                trial.hitEvents++;
                trial.finalDamage += event.getFinalDamage();
            }
        }
    }

    private Trial current(Player player) {
        return Objects.requireNonNull(trials.get(player.getUniqueId()), "Prepare the XP trial first");
    }

    private static AdaptPlayer runtime(Player player) {
        AdaptPlayer runtime = Adapt.instance.getAdaptServer().getOnlineAdaptPlayer(player.getUniqueId());
        if (runtime == null || !runtime.isRuntimeReady() || runtime.getPlayer() != player) {
            throw new IllegalStateException("Adapt player is not ready");
        }
        return runtime;
    }

    private static <T> CompletableFuture<T> entity(Entity entity, Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        if (!J.runEntity(entity, () -> complete(result, action))) {
            result.completeExceptionally(new IllegalStateException("XP fixture entity scheduler refused"));
        }
        return result;
    }

    private static <T> CompletableFuture<T> at(Location location, Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        if (!J.runAt(location, () -> complete(result, action))) {
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

    private void fail(Player admin, Throwable failure) {
        getLogger().log(Level.SEVERE, "XP fixture command failed", failure);
        J.runEntity(admin, () -> admin.sendMessage("ADAPT_QA ERROR XP fixture: " + failure.getMessage()));
    }

    private static void send(Player admin, String action, JsonObject result) {
        String json = result.toString();
        int total = (json.length() + 2499) / 2500;
        for (int index = 0; index < total; index++) {
            admin.sendMessage("ADAPT_QA XP " + action + " " + index + " " + total + " "
                    + json.substring(index * 2500, Math.min(json.length(), (index + 1) * 2500)));
        }
    }

    private static final class Trial {
        private final World world;
        private final int x;
        private final String mode;
        private final Location origin;
        private final long startedAt = System.currentTimeMillis();
        private int orbEvents;
        private int orbPoints;
        private int craftEvents;
        private int breakEvents;
        private int hitEvents;
        private double finalDamage;

        private Trial(Preparation preparation) {
            world = preparation.origin().getWorld();
            x = preparation.x();
            mode = preparation.mode();
            origin = preparation.origin();
        }

        private Location anchor() {
            return new Location(world, x + 8D, HEIGHT, 264D);
        }

        private Location spawn() {
            return new Location(world, x + 8.5D, HEIGHT, 264.5D, -90F, 0F);
        }
    }

    private record Preparation(Location origin, int x, String mode) {
    }
}
