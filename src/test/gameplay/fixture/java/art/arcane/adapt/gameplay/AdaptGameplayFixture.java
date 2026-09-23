package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
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
            if (Bukkit.getOnlineMode() || !"127.0.0.1".equals(Bukkit.getIp())
                    || !source.lines().anyMatch("isolated=true"::equals)) {
                throw new IllegalStateException("Fixture requires an isolated offline loopback Multiplexor instance");
            }
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
            if (args.length == 3 && args[0].equals("setup")) {
                setup(admin, args[1], args[2]);
                sender.sendMessage("ADAPT_QA SETUP " + arena.getName());
            } else if (args.length == 2 && args[0].equals("stage")) {
                stage(args[1]);
                sender.sendMessage("ADAPT_QA STAGE " + args[1]);
            } else if (args.length == 3 && args[0].equals("snapshot")) {
                sender.sendMessage("ADAPT_QA SNAPSHOT " + args[2] + " " + snapshot(args[1]));
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

    private void setup(Player admin, String name, String otherName) {
        if (arena != null) throw new IllegalStateException("Cleanup the previous fixture first");
        actor = ordinaryPlayer(name);
        opponent = ordinaryPlayer(otherName);
        if (actor == opponent) throw new IllegalArgumentException("Two distinct actors required");
        arena = Objects.requireNonNull(new WorldCreator("adapt_gameplay_" + UUID.randomUUID().toString().replace("-", ""))
                .generator(new EmptyGenerator()).generateStructures(false).createWorld());
        arena.setTime(6000);
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
        for (Player player : new Player[]{actor, opponent}) {
            player.closeInventory();
            player.getInventory().clear();
            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(20);
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
            case "blocking" -> {
                give(opponent, Material.WOODEN_SWORD, 1);
                actor.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
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
            default -> throw new IllegalArgumentException("Unknown skill stage " + name);
        }
    }

    private void give(Player player, Material material, int amount) {
        player.getInventory().addItem(new ItemStack(material, amount));
    }

    private JsonObject snapshot(String name) {
        Player player = ordinaryPlayer(name);
        AdaptPlayer adaptPlayer = Adapt.instance.getAdaptServer().getPlayer(player);
        if (adaptPlayer == null || !adaptPlayer.isRuntimeReady()) throw new IllegalStateException("Adapt player is not ready");
        JsonObject result = new JsonObject();
        result.addProperty("player", name);
        result.addProperty("operator", player.isOp());
        result.addProperty("health", player.getHealth());
        JsonObject xp = new JsonObject();
        JsonArray registered = new JsonArray();
        JsonArray enabled = new JsonArray();
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
            String skillName = skill.getName();
            registered.add(skillName);
            if (skill.isEnabled()) enabled.add(skillName);
            PlayerSkillLine line = adaptPlayer.getData().getSkillLineNullable(skillName);
            xp.addProperty(skillName, line == null ? 0 : line.getXp() + line.getPooledXp());
        }
        result.add("registered", registered);
        result.add("enabled", enabled);
        result.add("xp", xp);
        return result;
    }

    private void cleanup() {
        if (arena == null) return;
        for (Map.Entry<UUID, Location> entry : origins.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.teleport(entry.getValue());
                player.setGameMode(gameModes.get(entry.getKey()));
            }
        }
        origins.clear();
        gameModes.clear();
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
