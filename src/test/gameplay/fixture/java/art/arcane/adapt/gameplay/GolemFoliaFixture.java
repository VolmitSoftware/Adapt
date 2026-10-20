package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.data.WorldData;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeFaces;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodePlacementVerdict;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class GolemFoliaFixture extends JavaPlugin {
    private static final int HEIGHT = 100;
    private final Map<UUID, Stage> stages = new ConcurrentHashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player admin) || !admin.isOp() || args.length != 2) {
            sender.sendMessage("ADAPT_QA ERROR operator and actor/action required");
            return true;
        }
        Player actor = Bukkit.getPlayerExact(args[0]);
        if (actor == null || actor.isOp() || !actor.getName().startsWith("AQA")) {
            sender.sendMessage("ADAPT_QA ERROR ordinary AQA actor required");
            return true;
        }
        CompletableFuture<JsonObject> response;
        try {
            response = switch (args[1]) {
                case "prepare" -> prepare(actor);
                case "snapshot" -> snapshot(current(actor));
                case "refill" -> refill(current(actor));
                case "cleanup" -> cleanup(current(actor));
                case "settled" -> settled(current(actor));
                default -> CompletableFuture.failedFuture(new IllegalArgumentException("Unknown Folia golem action: " + args[1]));
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

    private CompletableFuture<JsonObject> prepare(Player player) {
        return entity(player, () -> {
            for (String adaptation : List.of("golem-animus-binding", "golem-node-weaving", "golem-harvester-core")) {
                learn(player, adaptation, 5);
            }
            player.getInventory().clear();
            player.getInventory().setItem(0, GolemItems.crook());
            player.getInventory().setItem(1, new ItemStack(Material.PAPER));
            player.getInventory().setItem(8, GolemItems.crook());
            player.getInventory().setHeldItemSlot(0);
            return player.getWorld();
        }).thenCompose(world -> player.teleportAsync(new Location(world, 14.5D, HEIGHT, 0.5D)).thenCompose(teleported -> {
            if (!teleported) {
                return CompletableFuture.failedFuture(new IllegalStateException("Folia fixture actor teleport refused"));
            }
            List<CompletableFuture<Void>> chunks = new ArrayList<>();
            for (int chunkX = 0; chunkX <= 1; chunkX++) {
                chunks.add(prepareChunk(world, chunkX));
            }
            return CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new)).thenCompose(ignored -> stage(player, world, 20));
        })).thenCompose(this::snapshot);
    }

    private CompletableFuture<Void> prepareChunk(World world, int chunkX) {
        return world.getChunkAtAsync(chunkX, 0).thenCompose(chunk -> at(new Location(world, chunkX * 16 + 8, HEIGHT, 8), () -> {
            int minimumX = Math.max(8, chunkX * 16);
            int maximumX = Math.min(23, chunkX * 16 + 15);
            for (int x = minimumX; x <= maximumX; x++) {
                for (int z = 0; z <= 15; z++) {
                    world.getBlockAt(x, HEIGHT - 1, z).setType(Material.STONE, false);
                    for (int y = HEIGHT; y <= HEIGHT + 3; y++) {
                        world.getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                }
            }
            for (int x = Math.max(14, minimumX); x <= Math.min(18, maximumX); x++) {
                for (int z = 2; z <= 4; z++) {
                    world.getBlockAt(x, HEIGHT - 1, z).setType(Material.FARMLAND, false);
                }
            }
            if (chunkX == 0) {
                world.getBlockAt(14, HEIGHT + 3, 3).setType(Material.GLOWSTONE, false);
                world.getBlockAt(13, HEIGHT - 1, 3).setType(Material.WATER, false);
                world.getBlockAt(12, HEIGHT, 7).setType(Material.CHEST, false);
                chest(world).getBlockInventory().setItem(0, new ItemStack(Material.WHEAT_SEEDS, 8));
            } else {
                world.getBlockAt(17, HEIGHT + 3, 3).setType(Material.GLOWSTONE, false);
            }
            return null;
        }));
    }

    private CompletableFuture<Stage> stage(Player player, World world, int attempts) {
        Location anchor = new Location(world, 15, HEIGHT - 1, 1);
        return at(anchor, () -> {
            NodeService nodes = GolemancyRuntime.nodes().orElseThrow();
            NodeKey key = new NodeKey(world.getUID(), 15, HEIGHT - 1, 1, BlockFace.UP);
            if (!nodes.index().isReady(world.getUID(), key.chunkKey())) {
                return null;
            }
            NodePlacementVerdict placed = nodes.place(player, world.getBlockAt(15, HEIGHT - 1, 1), BlockFace.UP, NodeType.HARVEST,
                GolemancyRuntime.nodeWeaving().orElseThrow());
            if (placed != NodePlacementVerdict.READY) {
                throw new IllegalStateException("Folia fixture node placement failed: " + placed);
            }
            nodes.update(player.getUniqueId(), key, record -> record.toBuilder().area(new NodeCuboid(-7, -8, -1, 8, 7, 14)).build()).orElseThrow();
            Snowman golem = world.spawn(new Location(world, 14.5D, HEIGHT, 6.5D), Snowman.class);
            GolemHandle handle = GolemancyRuntime.registry().orElseThrow().bind(player, golem, GolemBody.FROST, GolemCoreType.HARVESTER);
            handle.setHomeChest(GolemState.Anchor.of(world.getUID(), 12, HEIGHT, 7));
            handle.setMode(GolemMode.WORK);
            GolemancyRuntime.bindListener().bound(player, handle);
            Stage stage = new Stage(player, world, key, handle);
            stages.put(player.getUniqueId(), stage);
            return stage;
        }).thenCompose(ready -> {
            if (ready != null) {
                return CompletableFuture.completedFuture(ready);
            }
            if (attempts <= 1) {
                return CompletableFuture.failedFuture(new IllegalStateException("Folia fixture node chunk did not become ready"));
            }
            CompletableFuture<Void> delayed = new CompletableFuture<>();
            if (!J.runAt(anchor, () -> delayed.complete(null), 2)) {
                delayed.completeExceptionally(new IllegalStateException("Folia fixture ready retry refused"));
            }
            return delayed.thenCompose(ignored -> stage(player, world, attempts - 1));
        });
    }

    private CompletableFuture<JsonObject> snapshot(Stage stage) {
        CompletableFuture<Integer> west = crops(stage.world(), 14, 15);
        CompletableFuture<Integer> east = crops(stage.world(), 16, 18);
        CompletableFuture<Integer> seeds = at(chestLocation(stage.world()), () -> count(chest(stage.world()).getBlockInventory().getContents()));
        CompletableFuture<Integer> cargo = entity(stage.handle().entity(), () -> count(stage.handle().state().carriedItems().toArray(ItemStack[]::new)));
        CompletableFuture<Integer> slot = entity(stage.player(), () -> stage.player().getInventory().getHeldItemSlot());
        return CompletableFuture.allOf(west, east, seeds, cargo, slot).thenApply(ignored -> {
            JsonObject result = new JsonObject();
            result.addProperty("crops", west.join() + east.join());
            result.addProperty("westCrops", west.join());
            result.addProperty("eastCrops", east.join());
            result.addProperty("chestSeeds", seeds.join());
            result.addProperty("cargoSeeds", cargo.join());
            result.addProperty("heldSlot", slot.join());
            result.addProperty("golemId", stage.handle().entity().getEntityId());
            GolemancyRuntime.nodes().flatMap(nodes -> nodes.node(stage.key())).ifPresent(record -> describe(stage.key(), record, result));
            GolemancyRuntime.nodes().flatMap(nodes -> nodes.renderer().bundle(stage.key()))
                .ifPresent(bundle -> result.addProperty("interactionId", bundle.hitbox().getEntityId()));
            return result;
        });
    }

    private CompletableFuture<Integer> crops(World world, int minimumX, int maximumX) {
        return at(new Location(world, minimumX, HEIGHT, 3), () -> {
            int crops = 0;
            for (int x = minimumX; x <= maximumX; x++) {
                for (int z = 2; z <= 4; z++) {
                    if (world.getBlockAt(x, HEIGHT, z).getType() == Material.WHEAT) {
                        crops++;
                    }
                }
            }
            return crops;
        });
    }

    private CompletableFuture<JsonObject> refill(Stage stage) {
        return at(chestLocation(stage.world()), () -> {
            chest(stage.world()).getBlockInventory().setItem(0, new ItemStack(Material.WHEAT_SEEDS, 4));
            return null;
        }).thenCompose(ignored -> snapshot(stage));
    }

    private CompletableFuture<JsonObject> cleanup(Stage stage) {
        return entity(stage.handle().entity(), () -> {
            GolemancyRuntime.registry().orElseThrow().release(stage.handle());
            stage.handle().entity().remove();
            return null;
        }).thenCompose(ignored -> at(stage.key().center(stage.world()), () -> {
            GolemancyRuntime.nodes().orElseThrow().remove(stage.player(), stage.key());
            JsonObject result = new JsonObject();
            result.addProperty("cleanupStarted", true);
            return result;
        }));
    }

    private CompletableFuture<JsonObject> settled(Stage stage) {
        return at(stage.key().center(stage.world()), () -> {
            boolean settled = WorldData.loaded(stage.world()).map(data -> {
                NodeFaces faces = data.get(stage.key().x(), stage.key().y(), stage.key().z(), NodeFaces.class);
                return faces == null || faces.get(stage.key().face()) == null;
            }).orElse(false);
            JsonObject result = new JsonObject();
            result.addProperty("settled", settled);
            if (settled) {
                stages.remove(stage.player().getUniqueId(), stage);
            }
            return result;
        });
    }

    private static void describe(NodeKey key, NodeRecord record, JsonObject result) {
        JsonArray min = new JsonArray();
        JsonArray max = new JsonArray();
        min.add(record.area().minX());
        min.add(record.area().minY());
        min.add(record.area().minZ());
        max.add(record.area().maxX());
        max.add(record.area().maxY());
        max.add(record.area().maxZ());
        result.add("min", min);
        result.add("max", max);
        result.addProperty("volume", record.area().volume());
        result.addProperty("containsNode", record.area().contains(0, 0, 0));
        result.addProperty("minChunkX", (key.x() + record.area().minX()) >> 4);
        result.addProperty("maxChunkX", (key.x() + record.area().maxX()) >> 4);
    }

    private static Chest chest(World world) {
        return (Chest) world.getBlockAt(12, HEIGHT, 7).getState();
    }

    private static Location chestLocation(World world) {
        return new Location(world, 12, HEIGHT, 7);
    }

    private Stage current(Player player) {
        return Objects.requireNonNull(stages.get(player.getUniqueId()), "Prepare Folia golem fixture first");
    }

    private static int count(ItemStack[] items) {
        int count = 0;
        for (ItemStack item : items) {
            if (item != null && item.getType() == Material.WHEAT_SEEDS) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private static <T> CompletableFuture<T> at(Location location, Supplier<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        if (!J.runAt(location, () -> complete(result, task))) {
            result.completeExceptionally(new IllegalStateException("Folia fixture region task refused: " + location));
        }
        return result;
    }

    private static <T> CompletableFuture<T> entity(Entity entity, Supplier<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        if (!J.runEntity(entity, () -> complete(result, task))) {
            result.completeExceptionally(new IllegalStateException("Folia fixture entity task refused: " + entity.getUniqueId()));
        }
        return result;
    }

    private static <T> void complete(CompletableFuture<T> result, Supplier<T> task) {
        try {
            result.complete(task.get());
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
        }
    }

    private static void learn(Player player, String name, int level) {
        AdaptPlayer runtime = Objects.requireNonNull(Adapt.instance.getAdaptServer().getPlayer(player));
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (adaptation.getName().equals(name)) {
                    runtime.getData().getSkillLine(skill.getName()).setAdaptation(adaptation, level);
                    return;
                }
            }
        }
        throw new IllegalArgumentException("Unknown adaptation: " + name);
    }

    private void fail(Player admin, Throwable failure) {
        getLogger().log(Level.SEVERE, "Folia golem fixture action failed", failure);
        J.runEntity(admin, () -> admin.sendMessage("ADAPT_QA ERROR Folia golem fixture: " + failure.getMessage()));
    }

    private static void send(Player admin, String action, JsonObject result) {
        admin.sendMessage("ADAPT_QA FOLIA_GOLEM " + action + " 0 1 " + result);
    }

    private record Stage(Player player, World world, NodeKey key, GolemHandle handle) {
    }
}
