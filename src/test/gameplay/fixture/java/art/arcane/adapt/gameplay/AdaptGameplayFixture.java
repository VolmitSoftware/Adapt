package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.fx.FxBudget;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

public final class AdaptGameplayFixture extends JavaPlugin {
    private final Map<UUID, Location> origins = new HashMap<>();
    private final Map<UUID, GameMode> gameModes = new HashMap<>();
    private World arena;
    private Player actor;
    private Player opponent;

    @Override
    public void onEnable() {
        try {
            String source = Files.readString(Path.of(".server-source"));
            if (!"Paper".equals(Bukkit.getName()) || Bukkit.getOnlineMode() || !"127.0.0.1".equals(Bukkit.getIp())
                    || !source.lines().anyMatch("isolated=true"::equals)) {
                throw new IllegalStateException("Fixture requires an isolated offline loopback Paper Multiplexor instance");
            }
            RangedFixtures.install(this);
            HarvestFixtures.install(this);
            TamingFixtures.install(this);
            DefenseFixtures.install(this);
            RiftFixtures.install(this);
            ProjectileFixtures.install(this);
            ProjectileTargetingFixtures.install(this);
            GuardFixtures.install(this);
            getServer().getPluginManager().registerEvents(new BlinkPreferencesFixtures(), this);
            getServer().getPluginManager().registerEvents(new CraftingFixtures(), this);
            getServer().getPluginManager().registerEvents(new ChronosFixtures(), this);
            getServer().getPluginManager().registerEvents(new TraversalFixtures(), this);
            getServer().getPluginManager().registerEvents(new NatureFixtures(), this);
            getServer().getPluginManager().registerEvents(new NetherFixtures(), this);
            UtilityFixtures.install(this);
            KineticsFixtures.install(this);
        } catch (IOException | IllegalStateException failure) {
            getLogger().log(Level.SEVERE, "Adapt gameplay fixture refused this server", failure);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player admin) || !admin.isOp()) {
            sender.sendMessage("ADAPT_QA ERROR operator player required");
            return true;
        }
        try {
            if (args.length >= 3 && args[0].equals("catalog-behavior")) {
                sendJson(sender, "CATALOG_BEHAVIOR " + args[2], CatalogPreferenceFixtures.execute(ordinaryPlayer(args[1]), args));
            } else if (args.length >= 3 && args[0].equals("preferences")) {
                sendJson(sender, "PREFERENCES " + args[2], PreferenceFixtures.execute(ordinaryPlayer(args[1]), args));
            } else if (args.length >= 3 && args[0].equals("blink")) {
                sendJson(sender, "BLINK " + args[2], BlinkPreferencesFixtures.execute(ordinaryPlayer(args[1]), args));
            } else if (args.length == 3 && args[0].equals("setup")) {
                setup(admin, args[1], args[2]);
                sender.sendMessage("ADAPT_QA SETUP " + arena.getName());
            } else if (args.length == 2 && args[0].equals("stage")) {
                stage(args[1]);
                sender.sendMessage("ADAPT_QA STAGE " + args[1]);
            } else if (args.length == 3 && args[0].equals("snapshot")) {
                sendJson(sender, "SNAPSHOT " + args[2], snapshot(args[1]));
            } else if (args.length == 2 && args[0].equals("catalog")) {
                sendJson(sender, "CATALOG " + args[1], catalog());
            } else if (args.length == 2 && args[0].equals("attribute-registry")) {
                sendJson(sender, "ATTRIBUTE_REGISTRY " + args[1], ProtocolFixtures.attributes());
            } else if (args.length == 3 && (args[0].equals("learn") || args[0].equals("learn-add"))) {
                int level = Integer.parseInt(args[2]);
                learn(args[1], level, args[0].equals("learn"));
                sender.sendMessage("ADAPT_QA " + (args[0].equals("learn") ? "LEARN " : "LEARN-ADD ") + args[1] + " " + level);
            } else if (args.length == 2 && args[0].equals("effects")) {
                if (arena == null || actor == null) throw new IllegalStateException("Setup required");
                if (!args[1].equals("true") && !args[1].equals("false")) {
                    throw new IllegalArgumentException("Effects requires true or false");
                }
                runtimePlayer(actor).getData().setEffectsEnabled(Boolean.parseBoolean(args[1]));
                sender.sendMessage("ADAPT_QA EFFECTS " + args[1]);
            } else if (args.length == 2 && args[0].equals("earth-refill")) {
                if (arena == null || actor == null) throw new IllegalStateException("Setup required");
                Material soil = switch (args[1]) {
                    case "sand" -> Material.SAND;
                    case "dirt" -> Material.DIRT;
                    default -> throw new IllegalArgumentException("Soil must be sand or dirt");
                };
                EarthFixtures.refill(arena, soil);
                sender.sendMessage("ADAPT_QA EARTH-REFILL " + args[1]);
            } else if (args.length == 1 && args[0].equals("feedback")) {
                if (arena == null || actor == null) throw new IllegalStateException("Setup required");
                actor.spawnParticle(Particle.DUST, actor.getLocation(), 2, new Particle.DustOptions(Color.RED, 1.0F));
                Fx.now(Adapt.instance.getAdaptServer().getSkillRegistry().getSkill("agility"), actor.getLocation(), FxPriority.GAMEPLAY)
                        .dustBurst(3, 0.1D, 1.0F);
                sender.sendMessage("ADAPT_QA FEEDBACK " + Particles.REDSTONE.name());
            } else if (args.length == 1 && args[0].equals("cleanup")) {
                cleanup();
                sender.sendMessage("ADAPT_QA CLEANUP");
            } else {
                sender.sendMessage("ADAPT_QA ERROR invalid fixture command");
            }
        } catch (RuntimeException failure) {
            getLogger().log(Level.SEVERE, "Adapt gameplay fixture command failed", failure);
            sender.sendMessage("ADAPT_QA ERROR " + failure.getMessage());
        }
        return true;
    }

    private void sendJson(CommandSender sender, String response, JsonObject value) {
        String json = value.toString();
        int total = (json.length() + 2499) / 2500;
        for (int index = 0; index < total; index++) {
            sender.sendMessage("ADAPT_QA " + response + " " + index + " " + total + " "
                    + json.substring(index * 2500, Math.min(json.length(), (index + 1) * 2500)));
        }
    }

    private void setup(Player admin, String name, String otherName) {
        if (arena != null) throw new IllegalStateException("Cleanup the previous fixture first");
        actor = ordinaryPlayer(name);
        opponent = ordinaryPlayer(otherName);
        if (actor == opponent) throw new IllegalArgumentException("Two distinct actors required");
        arena = Objects.requireNonNull(new WorldCreator("adapt_gameplay_" + UUID.randomUUID().toString().replace("-", ""))
                .generator(new EmptyGenerator()).generateStructures(false).createWorld());
        arena.setTime(6000);
        arena.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        arena.setPVP(true);
        arena.setSpawnLocation(0, 100, 0);
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) arena.getBlockAt(x, 99, z).setType(Material.STONE, false);
        }
        for (Player player : new Player[]{admin, actor, opponent}) {
            origins.put(player.getUniqueId(), player.getLocation().clone());
            gameModes.put(player.getUniqueId(), player.getGameMode());
            player.teleport(new Location(arena, 0.5, 100, player == admin ? 12.5 : 0.5));
        }
        admin.setGameMode(GameMode.SPECTATOR);
        stage("agility");
    }

    private Player ordinaryPlayer(String name) {
        Player player = Objects.requireNonNull(Bukkit.getPlayerExact(name), "Player is not online");
        if (player.isOp() || !name.startsWith("AQA")) throw new IllegalArgumentException("Fixture actor must be an ordinary AQA player");
        return player;
    }

    private void stage(String name) {
        if (arena == null) throw new IllegalStateException("Setup required");
        for (Entity entity : arena.getEntities()) if (!(entity instanceof Player)) entity.remove();
        TraversalFixtures.reset();
        resetArena();
        for (Player player : new Player[]{actor, opponent}) {
            NatureFixtures.reset(player);
            player.setFallDistance(0F);
            player.setVelocity(new Vector());
            player.closeInventory();
            player.getInventory().clear();
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(Math.min(20, Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue()));
            player.setFoodLevel(20);
            player.setSaturation(5);
            player.setFireTicks(0);
            for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        }
        actor.teleport(new Location(arena, 0.5, 100, 0.5, -90, 0));
        opponent.teleport(new Location(arena, 2.5, 100, 0.5, 90, 0));
        arena.getBlockAt(2, 100, 2).setType(Material.AIR, false);
        switch (name) {
            case "agility", "unarmed", "stealth", "tragoul" -> { }
            case "discovery" -> arena.getBlockAt(-2, 100, 2).setType(Material.AMETHYST_BLOCK, false);
            case "axes" -> give(actor, Material.WOODEN_AXE, 1);
            case "swords" -> give(actor, Material.WOODEN_SWORD, 1);
            case "combat-unarmed" -> {
                opponent.setFoodLevel(17);
                opponent.setSaturation(0);
            }
            case "combat-swords", "sword-low-health", "swords-dual" -> {
                give(actor, Material.WOODEN_SWORD, 1);
                opponent.setFoodLevel(17);
                opponent.setSaturation(0);
                if (name.equals("sword-low-health")) opponent.setHealth(10);
                if (name.equals("swords-dual")) actor.getInventory().setItemInOffHand(new ItemStack(Material.WOODEN_SWORD));
            }
            case "axes-armored" -> {
                give(actor, Material.WOODEN_AXE, 1);
                opponent.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                opponent.setFoodLevel(17);
                opponent.setSaturation(0);
            }
            case "blocking" -> {
                give(opponent, Material.WOODEN_SWORD, 1);
                actor.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
            }
            case "block-log", "block-leaves", "block-ore", "block-iron", "block-clay",
                 "block-pumpkin", "block-carrots", "block-glass", "block-stone-plane", "block-clay-plane" -> {
                opponent.teleport(new Location(arena, -5.5, 100, 5.5));
                Material tool = switch (name) {
                    case "block-log", "block-leaves" -> Material.DIAMOND_AXE;
                    case "block-ore", "block-iron", "block-stone-plane" -> Material.DIAMOND_PICKAXE;
                    case "block-clay", "block-clay-plane" -> Material.DIAMOND_SHOVEL;
                    case "block-pumpkin", "block-carrots" -> Material.DIAMOND_HOE;
                    default -> Material.AIR;
                };
                if (tool != Material.AIR) give(actor, tool, 1);
                Material material = switch (name) {
                    case "block-log" -> Material.OAK_LOG;
                    case "block-leaves" -> Material.OAK_LEAVES;
                    case "block-ore" -> Material.DIAMOND_ORE;
                    case "block-iron" -> Material.IRON_ORE;
                    case "block-clay", "block-clay-plane" -> Material.CLAY;
                    case "block-pumpkin" -> Material.PUMPKIN;
                    case "block-carrots" -> Material.CARROTS;
                    case "block-glass" -> Material.GLASS;
                    default -> Material.STONE;
                };
                arena.getBlockAt(3, 100, 0).setType(material, false);
                if (name.equals("block-log") || name.equals("block-leaves")) {
                    for (int y = 100; y <= 102; y++) {
                        if (material == Material.OAK_LEAVES) {
                            arena.getBlockAt(3, y, 0).setBlockData(Bukkit.createBlockData("minecraft:oak_leaves[persistent=true]"), false);
                        } else {
                            arena.getBlockAt(3, y, 0).setType(material, false);
                        }
                    }
                }
                if (name.equals("block-ore")) {
                    for (int x = 3; x <= 4; x++) {
                        for (int y = 100; y <= 101; y++) arena.getBlockAt(x, y, 0).setType(material, false);
                    }
                }
                if (name.endsWith("-plane")) {
                    for (int y = 99; y <= 101; y++) {
                        for (int z = -1; z <= 1; z++) arena.getBlockAt(3, y, z).setType(material, false);
                    }
                }
                if (name.equals("block-carrots")) {
                    arena.getBlockAt(3, 99, 0).setBlockData(Bukkit.createBlockData("minecraft:farmland[moisture=7]"), false);
                    arena.getBlockAt(3, 100, 0).setBlockData(Bukkit.createBlockData("minecraft:carrots[age=7]"), false);
                }
            }
            case "architect" -> give(actor, Material.STONE_BRICKS, 8);
            case "pickaxe" -> {
                give(actor, Material.IRON_PICKAXE, 1);
                arena.getBlockAt(2, 100, 2).setType(Material.DIAMOND_ORE, false);
            }
            case "excavation" -> {
                give(actor, Material.IRON_SHOVEL, 1);
                arena.getBlockAt(3, 100, 2).setType(Material.CLAY, false);
            }
            case "nether" -> arena.getBlockAt(2, 100, 2).setType(Material.WITHER_ROSE, false);
            case "crafting" -> give(actor, Material.OAK_LOG, 4);
            case "herbalism" -> {
                actor.setFoodLevel(10);
                give(actor, Material.APPLE, 2);
            }
            case "brewing", "chronos" -> {
                ItemStack potion = new ItemStack(Material.POTION);
                PotionMeta meta = (PotionMeta) potion.getItemMeta();
                meta.setBasePotionType(PotionType.SWIFTNESS);
                potion.setItemMeta(meta);
                actor.getInventory().addItem(potion);
            }
            case "ranged" -> {
                give(actor, Material.BOW, 1);
                give(actor, Material.ARROW, 16);
            }
            case "rift" -> give(actor, Material.ENDER_PEARL, 2);
            case "seaborne" -> {
                for (int x = -3; x <= 3; x++) {
                    for (int z = -3; z <= 3; z++) {
                        arena.getBlockAt(x, 98, z).setType(Material.STONE, false);
                        arena.getBlockAt(x, 99, z).setType(Material.WATER, false);
                        arena.getBlockAt(x, 100, z).setType(Material.WATER, false);
                    }
                }
                arena.getBlockAt(2, 99, 2).setType(Material.DIRT, false);
                actor.teleport(new Location(arena, 0.5, 99, 0.5));
                give(actor, Material.IRON_SHOVEL, 1);
            }
            case "hunter" -> {
                give(actor, Material.WOODEN_SWORD, 1);
                Cow cow = arena.spawn(new Location(arena, 2.5, 100, 2.5), Cow.class);
                cow.setAI(false);
                cow.setHealth(1);
            }
            case "taming" -> {
                give(actor, Material.WHEAT, 32);
                arena.spawn(new Location(arena, 2.5, 100, 2.5), Cow.class);
                arena.spawn(new Location(arena, -1.5, 100, 2.5), Cow.class);
            }
            case "enchanting" -> {
                actor.setLevel(30);
                give(actor, Material.IRON_SWORD, 1);
                give(actor, Material.LAPIS_LAZULI, 64);
                arena.getBlockAt(2, 100, 2).setType(Material.ENCHANTING_TABLE, false);
            }
            case "kinetics" -> {
                for (int x = -2; x <= 6; x++) {
                    for (int z = -2; z <= 2; z++) arena.getBlockAt(x, 99, z).setType(Material.HAY_BLOCK, false);
                }
                arena.getBlockAt(0, 107, 0).setType(Material.STONE, false);
                actor.teleport(new Location(arena, 0.5, 108, 0.5, -90, 0));
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
                        && !IntegrationFixtures.stage(name, arena, actor, opponent)
                        && !TraversalFixtures.stage(name, arena, actor, opponent)) {
                    throw new IllegalArgumentException("Unknown skill stage " + name);
                }
            }
        }
    }

    private void give(Player player, Material material, int amount) {
        player.getInventory().addItem(new ItemStack(material, amount));
    }

    private void resetArena() {
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                if (arena.getBlockAt(x, 99, z).getType() != Material.STONE) {
                    arena.getBlockAt(x, 99, z).setType(Material.STONE, false);
                }
                for (int y = 100; y <= 108; y++) {
                    if (!arena.getBlockAt(x, y, z).isEmpty()) {
                        arena.getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private JsonObject catalog() {
        JsonObject result = new JsonObject();
        JsonArray skills = new JsonArray();
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
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
        return result;
    }

    private void learn(String name, int level, boolean exclusive) {
        if (arena == null || actor == null) throw new IllegalStateException("Setup required");
        AdaptPlayer adaptPlayer = runtimePlayer(actor);
        Adaptation<?> selected = null;
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (adaptation.getName().equals(name)) {
                    selected = adaptation;
                }
            }
        }
        if (selected == null) throw new IllegalArgumentException("Unknown adaptation " + name);
        if (!selected.isEnabled() || !selected.getSkill().isEnabled()) {
            throw new IllegalArgumentException("Adaptation is disabled " + name);
        }
        if (level < 0 || level > selected.getMaxLevel()) {
            throw new IllegalArgumentException("Level outside adaptation range");
        }
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
            PlayerSkillLine line = adaptPlayer.getData().getSkillLine(skill.getName());
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (exclusive || adaptation == selected) {
                    line.setAdaptation(adaptation, adaptation == selected ? level : 0);
                }
            }
        }
    }

    private AdaptPlayer runtimePlayer(Player player) {
        AdaptPlayer adaptPlayer = Adapt.instance.getAdaptServer().getPlayer(player);
        if (adaptPlayer == null || !adaptPlayer.isRuntimeReady()) throw new IllegalStateException("Adapt player is not ready");
        return adaptPlayer;
    }

    private JsonObject snapshot(String name) {
        Player player = ordinaryPlayer(name);
        AdaptPlayer adaptPlayer = runtimePlayer(player);
        JsonObject result = new JsonObject();
        result.addProperty("player", name);
        result.addProperty("operator", player.isOp());
        result.addProperty("effectsEnabled", adaptPlayer.getData().isEffectsEnabled());
        result.addProperty("health", player.getHealth());
        result.addProperty("processId", ProcessHandle.current().pid());
        result.addProperty("food", player.getFoodLevel());
        result.add("ranged", RangedFixtures.snapshot(player));
        result.add("taming", TamingFixtures.snapshot(player));
        result.add("nether", NetherFixtures.snapshot(player));
        result.add("defense", DefenseFixtures.snapshot(player));
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
        result.add("integration", IntegrationFixtures.snapshot(player));
        result.add("utility", UtilityFixtures.snapshot(player));
        result.add("kinetics", KineticsFixtures.snapshot(player));
        result.add("discovery", DiscoveryFixtures.snapshot(player));
        result.add("guard", GuardFixtures.snapshot(player));
        result.add("projectile", ProjectileFixtures.snapshot(player));
        result.add("earth", EarthFixtures.snapshot(player));
        result.addProperty("saturation", player.getSaturation());
        result.addProperty("allowFlight", player.getAllowFlight());
        result.addProperty("flying", player.isFlying());
        result.addProperty("remainingAir", player.getRemainingAir());
        result.addProperty("inWater", player.isInWater());
        result.addProperty("onGround", player.isOnGround());
        result.addProperty("blocking", player.isBlocking());
        result.addProperty("sneaking", player.isSneaking());
        result.addProperty("feedbackTransitionDensity", FxBudget.densityScalar(FxPriority.TRANSITION));
        result.addProperty("feedbackLoadBand", FxBudget.shedBand());
        result.addProperty("tps", Bukkit.getTPS()[0]);
        result.addProperty("world", player.getWorld().getName());
        AdaptPlayer.FxPosition fxPosition = adaptPlayer.getFxPosition();
        if (fxPosition != null) {
            JsonObject feedbackPosition = new JsonObject();
            feedbackPosition.addProperty("world", fxPosition.world().getName());
            feedbackPosition.addProperty("x", fxPosition.x());
            feedbackPosition.addProperty("y", fxPosition.y());
            feedbackPosition.addProperty("z", fxPosition.z());
            result.add("feedbackPosition", feedbackPosition);
        }
        JsonObject location = new JsonObject();
        location.addProperty("x", player.getX());
        location.addProperty("y", player.getY());
        location.addProperty("z", player.getZ());
        result.add("location", location);
        JsonObject xp = new JsonObject();
        JsonObject committedXp = new JsonObject();
        JsonObject pooledXp = new JsonObject();
        JsonObject learned = new JsonObject();
        JsonArray registered = new JsonArray();
        JsonArray enabled = new JsonArray();
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
            String skillName = skill.getName();
            registered.add(skillName);
            if (skill.isEnabled()) enabled.add(skillName);
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
        for (Attribute attribute : Registry.ATTRIBUTE) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) attributes.addProperty(attribute.getKey().toString(), instance.getValue());
        }
        result.add("attributes", attributes);
        JsonArray effects = new JsonArray();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            JsonObject detail = new JsonObject();
            detail.addProperty("type", effect.getType().getKey().toString());
            detail.addProperty("amplifier", effect.getAmplifier());
            detail.addProperty("duration", effect.getDuration());
            effects.add(detail);
        }
        result.add("effects", effects);
        return result;
    }

    private void cleanup() {
        if (arena == null) return;
        TraversalFixtures.reset();
        for (Map.Entry<UUID, Location> entry : origins.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                NatureFixtures.reset(player);
                player.teleport(entry.getValue());
                player.setGameMode(gameModes.get(entry.getKey()));
            }
        }
        origins.clear();
        gameModes.clear();
        NetherFixtures.cleanup();
        ProjectileTargetingFixtures.cleanup();
        DiscoveryFixtures.cleanup();
        Bukkit.unloadWorld(arena, false);
        arena = null;
        actor = null;
        opponent = null;
    }

    private static final class EmptyGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
            return createChunkData(world);
        }
    }
}
