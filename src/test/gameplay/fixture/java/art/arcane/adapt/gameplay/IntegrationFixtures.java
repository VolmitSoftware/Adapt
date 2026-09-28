package art.arcane.adapt.gameplay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

public final class IntegrationFixtures {
    private static final String IRIS_API = "art.arcane.iris.api.tree.IrisTreeFellerService";
    private static final String GLOSS_API = "art.arcane.gloss.api.GlossAPI";
    private static World treeWorld;
    private static int treeHeight = 4;

    private IntegrationFixtures() {
    }

    public static boolean stage(String name, World world, Player actor, Player opponent) {
        if (name.equals("integration-insight")) {
            requireProvider(GLOSS_API, "Gloss");
            Cow cow = world.spawn(new Location(world, 5.5, 100, 0.5), Cow.class);
            cow.setAI(false);
            cow.setCustomName("Insight Cow");
            cow.addScoreboardTag("adapt-qa-integration-target");
            opponent.teleport(new Location(world, 0.5, 100, 6.5));
            return true;
        }
        if (!name.equals("integration-iris") && !name.equals("integration-iris-latch")) {
            return false;
        }
        treeHeight = name.equals("integration-iris-latch") ? 40 : 4;
        RegisteredServiceProvider<?> service = requireProvider(IRIS_API, "Iris");
        treeWorld = Bukkit.getWorlds().stream()
                .filter(candidate -> candidate.getName().contains("adapt_gameplay_iris"))
                .findFirst().orElseThrow(() -> new IllegalStateException("Create the purpose-owned adapt_gameplay_iris world with Iris before this integration case"));
        Object engine = engine(service.getPlugin(), treeWorld);
        treeWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        treeWorld.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        treeWorld.setGameRule(GameRule.NATURAL_REGENERATION, false);
        for (int x = -1; x <= 0; x++) {
            for (int z = -1; z <= 0; z++) {
                treeWorld.getChunkAt(x, z).load();
            }
        }
        for (Entity entity : treeWorld.getEntities()) {
            if (!(entity instanceof Player) && Math.abs(entity.getLocation().getX()) < 12 && Math.abs(entity.getLocation().getZ()) < 12) {
                entity.remove();
            }
        }
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                treeWorld.getBlockAt(x, 99, z).setType(Material.STONE, false);
                for (int y = 100; y <= 145; y++) {
                    treeWorld.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
        Object mantle = invoke(invoke(engine, "getMantle"), "getMantle");
        String marker = "trees/adapt-qa@" + (UUID.randomUUID().hashCode() & Integer.MAX_VALUE);
        for (int y = 100; y < 100 + treeHeight; y++) {
            treeBlock(service.getPlugin(), mantle, treeWorld.getBlockAt(3, y, 0), Material.OAK_LOG, marker);
        }
        for (int x = 2; x <= 4; x++) {
            for (int z = -1; z <= 1; z++) {
                treeBlock(service.getPlugin(), mantle, treeWorld.getBlockAt(x, 100 + treeHeight, z), Material.OAK_LEAVES, marker);
            }
        }
        if (!isTreeBlock(service, treeWorld.getBlockAt(3, 100, 0))) {
            throw new IllegalStateException("Real Iris service rejected the initial tree provenance");
        }
        if (actor.hasPermission("iris.treefeller")) {
            throw new IllegalStateException("Integration control requires an ordinary player without standalone Iris tree-feller permission");
        }
        actor.setFoodLevel(20);
        actor.setSaturation(0);
        actor.getInventory().addItem(new ItemStack(Material.IRON_AXE));
        actor.teleport(new Location(treeWorld, 0.5, 100, 0.5));
        opponent.teleport(new Location(treeWorld, 0.5, 100, 6.5));
        return true;
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        providerDetails(result, GLOSS_API, "gloss");
        providerDetails(result, IRIS_API, "iris");
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof LivingEntity target && entity.getScoreboardTags().contains("adapt-qa-integration-target")) {
                JsonObject detail = new JsonObject();
                detail.addProperty("entityId", target.getEntityId());
                detail.addProperty("health", target.getHealth());
                result.add("target", detail);
            }
        }
        result.addProperty("world", player.getWorld().getName());
        if (player.getWorld() != treeWorld) {
            return result;
        }
        int logs = 0;
        JsonArray marked = new JsonArray();
        RegisteredServiceProvider<?> service = requireProvider(IRIS_API, "Iris");
        for (int y = 100; y < 100 + treeHeight; y++) {
            Block block = treeWorld.getBlockAt(3, y, 0);
            if (block.getType() == Material.OAK_LOG) {
                logs++;
                marked.add(isTreeBlock(service, block));
            }
        }
        result.addProperty("logs", logs);
        result.add("markedLogs", marked);
        int dropped = 0;
        for (Entity entity : treeWorld.getEntities()) {
            if (entity instanceof Item item && item.getItemStack().getType() == Material.OAK_LOG) {
                dropped += item.getItemStack().getAmount();
            }
        }
        int inventoryLogs = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.OAK_LOG) {
                inventoryLogs += item.getAmount();
            }
        }
        result.addProperty("recoveredLogs", dropped + inventoryLogs);
        result.addProperty("standaloneAllowed", player.hasPermission("iris.treefeller"));
        ItemStack hand = player.getInventory().getItemInMainHand();
        result.addProperty("toolDamage", hand.getItemMeta() instanceof Damageable damage ? damage.getDamage() : 0);
        return result;
    }

    private static Object engine(Plugin iris, World world) {
        try {
            Class<?> toolbelt = iris.getClass().getClassLoader().loadClass("art.arcane.iris.world.IrisToolbelt");
            Object access = toolbelt.getMethod("access", World.class).invoke(null, world);
            if (access == null) {
                throw new IllegalStateException("The integration world has no real Iris generator");
            }
            return invoke(access, "getEngine");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not inspect the real Iris world API", error);
        }
    }

    private static void treeBlock(Plugin iris, Object mantle, Block block, Material material, String marker) {
        block.setType(material, false);
        try {
            Method set = mantle.getClass().getMethod("set", int.class, int.class, int.class, Object.class);
            int y = block.getY() - block.getWorld().getMinHeight();
            set.invoke(mantle, block.getX(), y, block.getZ(), marker);
            Class<?> materialClass = iris.getClass().getClassLoader().loadClass("art.arcane.iris.generation.decoration.tree.TreeBlockMaterial");
            Object expected = materialClass.getMethod("of", String.class).invoke(null, block.getBlockData().getAsString());
            set.invoke(mantle, block.getX(), y, block.getZ(), expected);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not write the initial tree provenance through Iris storage", error);
        }
    }

    private static Object invoke(Object target, String method) {
        try {
            Object value = target.getClass().getMethod(method).invoke(target);
            if (value == null) {
                throw new IllegalStateException("Required Iris API value missing: " + method);
            }
            return value;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not access Iris API method " + method, error);
        }
    }

    private static boolean isTreeBlock(RegisteredServiceProvider<?> service, Block block) {
        try {
            return Boolean.TRUE.equals(service.getService().getMethod("isTreeBlock", Block.class).invoke(service.getProvider(), block));
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException error) {
            throw new IllegalStateException("Real Iris tree inspection failed", error);
        }
    }

    private static RegisteredServiceProvider<?> requireProvider(String api, String pluginName) {
        RegisteredServiceProvider<?> provider = provider(api);
        if (provider == null || !provider.getPlugin().getName().equals(pluginName) || !provider.getPlugin().isEnabled()) {
            throw new IllegalStateException("Required real " + pluginName + " API is unavailable");
        }
        return provider;
    }

    private static RegisteredServiceProvider<?> provider(String api) {
        for (Class<?> service : Bukkit.getServicesManager().getKnownServices()) {
            if (service.getName().equals(api)) {
                return Bukkit.getServicesManager().getRegistration(service);
            }
        }
        return null;
    }

    private static void providerDetails(JsonObject result, String api, String key) {
        RegisteredServiceProvider<?> provider = provider(api);
        if (provider != null) {
            JsonObject detail = new JsonObject();
            detail.addProperty("plugin", provider.getPlugin().getName());
            detail.addProperty("enabled", provider.getPlugin().isEnabled());
            detail.addProperty("implementation", provider.getProvider().getClass().getName());
            result.add(key, detail);
        }
    }
}
