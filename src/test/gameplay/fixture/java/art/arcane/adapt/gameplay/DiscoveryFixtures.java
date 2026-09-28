package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.structure.StructureType;
import org.bukkit.util.StructureSearchResult;
import org.bukkit.block.Biome;
import org.bukkit.block.BrushableBlock;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DiscoveryFixtures {
    private static final Set<String> STAGES = Set.of("discovery-qa-brush", "discovery-qa-keen", "discovery-qa-polymath",
            "discovery-qa-trail", "discovery-qa-villager", "discovery-qa-siphon", "discovery-qa-grind", "discovery-qa-lapis", "discovery-qa-soul", "discovery-qa-cartographer", "discovery-qa-sixth");
    private static final List<LivingEntity> targets = new ArrayList<>();
    private static World exploration;
    private static Location structureTarget;
    private static Location explorationPlatform;

    private DiscoveryFixtures() {
    }

    public static boolean stage(String name, World world, Player actor, Player opponent) {
        if (!STAGES.contains(name)) return false;
        targets.clear();
        actor.setLevel(100);
        actor.setExp(0);
        actor.setEnchantmentSeed(424242);
        actor.setFoodLevel(17);
        actor.setSaturation(0);
        opponent.teleport(new Location(world, 18.5, 100, 18.5));
        switch (name) {
            case "discovery-qa-cartographer", "discovery-qa-sixth" -> {
                prepareExploration(world, actor, opponent);
                if (name.equals("discovery-qa-cartographer")) actor.getInventory().addItem(new ItemStack(Material.COMPASS));
            }
            case "discovery-qa-brush" -> {
                actor.getInventory().addItem(new ItemStack(Material.BRUSH));
                world.getBlockAt(2, 100, 0).setType(Material.SUSPICIOUS_SAND, false);
                BrushableBlock brushable = (BrushableBlock) world.getBlockAt(2, 100, 0).getState();
                brushable.setItem(new ItemStack(Material.PAPER));
                brushable.update(true, false);
            }
            case "discovery-qa-keen" -> world.getBlockAt(2, 100, 0).setType(Material.CHEST, false);
            case "discovery-qa-polymath" -> {
                PlayerData data = Adapt.instance.getAdaptServer().getPlayer(actor).getData();
                data.getMultipliers().clear();
                data.getSkillLine("discovery").setXp(XP.getXpForLevel(5));
            }
            case "discovery-qa-trail" -> {
                for (int x = -8; x <= 8; x++) {
                    for (int z = -8; z <= 8; z++) world.setBiome(x, 100, z, Biome.JAGGED_PEAKS);
                }
            }
            case "discovery-qa-villager" -> {
                actor.getInventory().addItem(new ItemStack(Material.EMERALD, 64));
                Villager villager = world.spawn(new Location(world, 2.5, 100, 0.5), Villager.class);
                villager.setAI(false);
                villager.setAdult();
                villager.setProfession(Villager.Profession.FARMER);
                MerchantRecipe recipe = new MerchantRecipe(new ItemStack(Material.BREAD), 0, 100, false, 0, 0F);
                recipe.addIngredient(new ItemStack(Material.EMERALD, 20));
                villager.setRecipes(List.of(recipe));
                targets.add(villager);
            }
            case "discovery-qa-siphon" -> {
                actor.getInventory().addItem(new ItemStack(Material.WOODEN_AXE));
                for (int i = 0; i < 20; i++) {
                    Husk target = world.spawn(new Location(world, 2.5, 100, 0.5), Husk.class);
                    target.setAI(false);
                    target.setCollidable(false);
                    target.setHealth(1);
                    ItemStack helmet = enchanted(Material.DIAMOND_HELMET, Enchantment.PROTECTION, 2);
                    target.getEquipment().setHelmet(helmet);
                    target.getEquipment().setHelmetDropChance(0);
                    targets.add(target);
                }
            }
            case "discovery-qa-grind", "discovery-qa-lapis" -> {
                boolean grind = name.equals("discovery-qa-grind");
                world.getBlockAt(2, 100, 0).setType(grind ? Material.GRINDSTONE : Material.ENCHANTING_TABLE, false);
                for (int i = 0; i < 30; i++) actor.getInventory().addItem(grind
                        ? enchanted(Material.IRON_SWORD, Enchantment.SHARPNESS, 2) : new ItemStack(Material.IRON_SWORD));
                if (!grind) actor.getInventory().addItem(new ItemStack(Material.LAPIS_LAZULI, 64));
            }
            case "discovery-qa-soul" -> {
                world.getBlockAt(2, 100, 2).setType(Material.ANVIL, false);
                actor.setHealth(2);
                actor.getInventory().addItem(enchanted(Material.DIAMOND_PICKAXE, Enchantment.EFFICIENCY, 3));
                opponent.teleport(new Location(world, 2.5, 100, 0.5));
                opponent.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
                opponent.setFoodLevel(17);
                opponent.setSaturation(0);
            }
            default -> throw new IllegalArgumentException("Unknown discovery fixture: " + name);
        }
        return true;
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        PlayerData data = Adapt.instance.getAdaptServer().getPlayer(player).getData();
        result.addProperty("xpMultiplier", data.computeXpMultiplier(player));
        result.addProperty("senses", data.getStat("discovery.sixth-sense.senses"));
        result.addProperty("food", player.getFoodLevel());
        result.addProperty("compassX", player.getCompassTarget().getX());
        result.addProperty("compassZ", player.getCompassTarget().getZ());
        if (structureTarget != null && player.getWorld() == exploration) {
            result.addProperty("structureX", structureTarget.getX());
            result.addProperty("structureZ", structureTarget.getZ());
        }
        result.addProperty("polymathBoosts", data.getStat("discovery.polymath.boosts"));
        JsonObject qualifyingLevels = new JsonObject();
        for (Map.Entry<String, PlayerSkillLine> entry : data.getSkillLines().entrySet()) {
            if (entry.getValue().getLevel() >= 5) qualifyingLevels.addProperty(entry.getKey(), entry.getValue().getLevel());
        }
        result.add("qualifyingLevels", qualifyingLevels);
        result.addProperty("trailDiscoveries", data.getStat("discovery.trailblazer.discoveries"));
        result.addProperty("biomeDiscovered", data.getStat("discovery.trailblazer.biome.minecraft:jagged_peaks"));
        JsonArray targetStates = new JsonArray();
        for (LivingEntity target : targets) {
            if (target.getWorld() != player.getWorld()) continue;
            JsonObject state = new JsonObject();
            state.addProperty("entityId", target.getEntityId());
            state.addProperty("dead", target.isDead());
            state.addProperty("health", target.getHealth());
            targetStates.add(state);
        }
        result.add("targets", targetStates);
        return result;
    }

    public static void cleanup() {
        if (exploration == null) return;
        if (!exploration.getPlayers().isEmpty()) throw new IllegalStateException("Return players before unloading the discovery fixture");
        if (!Bukkit.unloadWorld(exploration, false)) throw new IllegalStateException("Could not unload discovery fixture " + exploration.getName());
        exploration = null;
        structureTarget = null;
        explorationPlatform = null;
    }

    private static void prepareExploration(World arena, Player actor, Player opponent) {
        if (exploration == null) {
            String name = arena.getName() + "_discovery";
            if (Bukkit.getWorld(name) != null) throw new IllegalStateException("Discovery fixture world already exists: " + name);
            exploration = Objects.requireNonNull(new WorldCreator(name).seed(424242L).generateStructures(true).createWorld());
            exploration.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            exploration.setGameRule(GameRule.NATURAL_REGENERATION, false);
            StructureSearchResult found = exploration.locateNearestStructure(exploration.getSpawnLocation(), StructureType.JIGSAW, 128, false);
            if (found == null) throw new IllegalStateException("Generated discovery world has no Jigsaw structure within 128 chunks");
            Location target = found.getLocation();
            int x = target.getBlockX() + 96;
            int z = target.getBlockZ();
            int y = Math.min(exploration.getMaxHeight() - 8, Math.max(160, exploration.getHighestBlockYAt(x, z) + 12));
            explorationPlatform = new Location(exploration, x + 0.5, y, z + 0.5);
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    exploration.getBlockAt(x + dx, y - 1, z + dz).setType(Material.STONE, false);
                    for (int dy = 0; dy <= 4; dy++) exploration.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR, false);
                }
            }
            StructureSearchResult nearest = exploration.locateNearestStructure(explorationPlatform, StructureType.JIGSAW, 88, false);
            structureTarget = Objects.requireNonNull(nearest, "Generated structure vanished during exploration setup").getLocation();
        }
        actor.teleport(explorationPlatform);
        opponent.teleport(explorationPlatform.clone().add(4, 0, 4));
        actor.setCompassTarget(exploration.getSpawnLocation());
    }

    private static ItemStack enchanted(Material material, Enchantment enchantment, int level) {
        ItemStack item = new ItemStack(material);
        item.addUnsafeEnchantment(enchantment, level);
        return item;
    }
}
