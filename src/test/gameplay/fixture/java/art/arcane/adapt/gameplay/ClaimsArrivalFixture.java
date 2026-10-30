package art.arcane.adapt.gameplay;

import art.arcane.adapt.api.claims.ClaimCity;
import art.arcane.adapt.api.claims.ClaimManager;
import art.arcane.adapt.claims.ClaimsService;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.scheduling.J;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

final class ClaimsArrivalFixture implements Listener {
    private static final Gson JSON = new Gson();

    private final ClaimsFixture plugin;
    private final ClaimsPortalFixture portals;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<World, Location> spawns = new HashMap<>();

    ClaimsArrivalFixture(ClaimsFixture plugin, ClaimsPortalFixture portals) {
        this.plugin = plugin;
        this.portals = portals;
    }

    void close() {
        restore();
        for (State state : states.values()) {
            restoreClearance(state);
        }
        states.clear();
    }

    CompletableFuture<JsonObject> command(Player player, String[] args) {
        return switch (args[1]) {
            case "arrival-prepare" -> portals.command(player, new String[]{player.getName(), "portal-prepare", args[2]})
                    .thenCompose(ignored -> ClaimsFixture.entity(player, () -> {
                        states.put(player.getUniqueId(), new State(player.getName()));
                        return null;
                    })).thenCompose(ignored -> snapshot(player));
            case "arrival-snapshot" -> snapshot(player);
            case "arrival-respawn" -> respawn(player, Site.valueOf(args[2].toUpperCase(Locale.ROOT)));
            case "arrival-ability" -> ability(player, args[2]);
            case "arrival-clearance" -> ClaimsFixture.entity(player, () -> {
                clearance(player, args[2]);
                return null;
            }).thenCompose(ignored -> snapshot(player));
            case "arrival-history" -> ClaimsFixture.entity(player, () -> history(args[2]));
            case "arrival-restore" -> ClaimsFixture.entity(player, () -> {
                restore();
                JsonObject result = new JsonObject();
                result.addProperty("restored", true);
                return result;
            });
            case "arrival-cleanup" -> portals.command(player, new String[]{player.getName(), "portal-cleanup"})
                    .thenApply(result -> {
                        State state = states.remove(player.getUniqueId());
                        if (state != null) {
                            restoreClearance(state);
                        }
                        return result;
                    });
            default -> CompletableFuture.failedFuture(new IllegalArgumentException("Unknown arrival fixture action"));
        };
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void died(PlayerDeathEvent event) {
        observe(event.getEntity(), "death");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void selectRespawn(PlayerRespawnEvent event) {
        State state = states.get(event.getPlayer().getUniqueId());
        if (state != null && state.respawning) {
            state.respawning = false;
            event.setRespawnLocation(state.requested);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void selectedRespawn(PlayerRespawnEvent event) {
        State state = states.get(event.getPlayer().getUniqueId());
        if (state == null) {
            return;
        }
        JsonObject observation = new JsonObject();
        observation.addProperty("kind", "respawn");
        observation.addProperty("allowed", ClaimsFixture.service().canTeleport(event.getPlayer(), event.getRespawnLocation()));
        observation.addProperty("loaded", event.getRespawnLocation().isChunkLoaded());
        observation.add("to", position(event.getRespawnLocation()));
        state.lifecycle.add(observation);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void postRespawn(PlayerPostRespawnEvent event) {
        if (!states.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        observe(event.getPlayer(), "postRespawn");
        if (event.getPlayer().isOnline()) {
            event.getPlayer().setGravity(false);
            event.getPlayer().setInvulnerable(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void arrived(PlayerChangedWorldEvent event) {
        State state = states.get(event.getPlayer().getUniqueId());
        if (state != null) {
            state.arrivals++;
            observe(event.getPlayer(), "arrival");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void kicked(PlayerKickEvent event) {
        JsonObject observation = observe(event.getPlayer(), "kick");
        if (observation != null) {
            observation.addProperty("cancelled", event.isCancelled());
            observation.addProperty("reason", PlainTextComponentSerializer.plainText().serialize(event.reason()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) {
        observe(event.getPlayer(), "quit");
    }

    private CompletableFuture<JsonObject> respawn(Player player, Site site) {
        return source(player, site).thenCompose(ignored -> ClaimsFixture.entity(player, () -> {
            State state = states.get(player.getUniqueId());
            state.respawning = true;
            player.setGameMode(GameMode.SURVIVAL);
            player.setInvulnerable(false);
            J.runEntity(player, () -> player.setHealth(0), 5);
            return null;
        })).thenCompose(ignored -> snapshot(player));
    }

    private CompletableFuture<JsonObject> ability(Player player, String cause) {
        return source(player, Site.TOWN).thenCompose(ignored -> ClaimsFixture.entity(player, () -> {
            State state = states.get(player.getUniqueId());
            JsonObject ability = new JsonObject();
            ability.addProperty("done", false);
            ability.add("submitted", position(player.getLocation()));
            state.ability = ability;
            CompletableFuture<Boolean> future = cause.equals("default")
                    ? PaperCompat.teleportAsync(player, state.requested)
                    : PaperCompat.teleportAsync(player, state.requested, PlayerTeleportEvent.TeleportCause.valueOf(cause));
            ability.addProperty("initiallyDone", future.isDone());
            future.whenComplete((moved, failure) -> {
                if (!Bukkit.isPrimaryThread()) {
                    IllegalStateException wrongThread = new IllegalStateException("Ability future completed outside the Paper server thread");
                    plugin.getLogger().log(Level.SEVERE, "Arrival fixture completion failed", wrongThread);
                    J.runEntity(player, () -> completed(player, state, moved, wrongThread));
                    return;
                }
                completed(player, state, moved, failure);
            });
            return null;
        })).thenCompose(ignored -> snapshot(player));
    }

    private void completed(Player player, State state, Boolean moved, Throwable failure) {
        JsonObject ability = state.ability;
        ability.addProperty("done", true);
        ability.addProperty("result", Boolean.TRUE.equals(moved));
        ability.addProperty("allowed", ClaimsFixture.service().canTeleport(player, player.getLocation()));
        ability.addProperty("arrivalsAtCompletion", state.arrivals);
        ability.add("completed", position(player.getLocation()));
        if (failure != null) {
            plugin.getLogger().log(Level.SEVERE, "Arrival fixture ability failed", failure);
            ability.addProperty("failure", failure.toString());
        }
    }

    private void clearance(Player player, String mode) {
        State state = states.get(player.getUniqueId());
        AttributeInstance scale = Objects.requireNonNull(player.getAttribute(Attribute.SCALE));
        switch (mode) {
            case "normal" -> {
                restoreClearance(state);
                state.scale = scale.getBaseValue();
                state.ceiling = player.getLocation().clone().add(0, 2, 0).getBlock();
                state.ceilingData = state.ceiling.getBlockData();
                state.ceiling.setType(Material.STONE, false);
                scale.setBaseValue(1);
            }
            case "large" -> scale.setBaseValue(1.32);
            case "restore" -> restoreClearance(state);
            default -> throw new IllegalArgumentException("Unknown clearance fixture mode");
        }
    }

    private void restoreClearance(State state) {
        if (state.ceiling != null) {
            state.ceiling.setBlockData(state.ceilingData, false);
            state.ceiling = null;
            state.ceilingData = null;
        }
        if (state.scale != null) {
            Player player = Bukkit.getPlayerExact(state.name);
            if (player != null) {
                Objects.requireNonNull(player.getAttribute(Attribute.SCALE)).setBaseValue(state.scale);
            }
            state.scale = null;
        }
    }

    private CompletableFuture<Void> source(Player player, Site site) {
        World overworld = Objects.requireNonNull(Bukkit.getWorld(NamespacedKey.minecraft("overworld")));
        World nether = Objects.requireNonNull(Bukkit.getWorld(NamespacedKey.minecraft("the_nether")));
        boolean blocked = site != Site.TOWN;
        Location source = site == Site.CLAIMED
                ? new Location(overworld, 4106.5, overworld.getMaxHeight() - 2, 4106.5)
                : new Location(nether, 520.5, blocked ? nether.getMaxHeight() - 2 : 80, 520.5);
        return ClaimsFixture.entity(player, () -> {
            if (blocked) {
                for (World world : Bukkit.getWorlds()) {
                    Location spawn = world.getSpawnLocation();
                    spawns.putIfAbsent(world, spawn.clone());
                    spawn.setY(world.getMaxHeight() - 2);
                    world.setSpawnLocation(spawn);
                }
            }
            player.setPortalCooldown(300);
            player.setGravity(false);
            player.setFlying(false);
            player.setVelocity(new Vector());
            player.setInvulnerable(true);
            if (site == Site.CLAIMED) {
                ClaimManager manager = ClaimsFixture.service().manager();
                ClaimCity city = manager.snapshot().cityAt(ClaimsService.chunk(source)).orElseThrow();
                long now = System.currentTimeMillis();
                manager.invite(city.founder(), player.getUniqueId(), now);
                manager.accept(player.getUniqueId(), city.founder(), now);
            }
            return null;
        }).thenCompose(ignored -> player.teleportAsync(source)).thenCompose(moved -> ClaimsFixture.entity(player, () -> {
            if (site == Site.CLAIMED) {
                ClaimsFixture.service().manager().leave(player.getUniqueId());
            }
            if (!moved) {
                throw new IllegalStateException("Arrival source teleport refused");
            }
            return null;
        })).thenCompose(ignored -> portals.command(player, new String[]{player.getName(), "portal-watch"}))
                .thenCompose(ignored -> ClaimsFixture.entity(player, () -> {
                    State state = states.get(player.getUniqueId());
                    state.lifecycle = new JsonArray();
                    state.ability = null;
                    state.arrivals = 0;
                    state.source = player.getLocation().clone();
                    state.sourceAllowed = ClaimsFixture.service().canTeleport(player, state.source);
                    state.requested = new Location(overworld, 4104.5, blocked ? overworld.getMaxHeight() - 2 : 80, 4104.5);
                    return null;
                }));
    }

    private CompletableFuture<JsonObject> snapshot(Player player) {
        return portals.command(player, new String[]{player.getName(), "portal-snapshot"}).thenApply(result -> {
            State state = states.get(player.getUniqueId());
            result.add("lifecycle", JSON.toJsonTree(state.lifecycle));
            result.addProperty("dead", player.isDead());
            result.addProperty("scale", Objects.requireNonNull(player.getAttribute(Attribute.SCALE)).getValue());
            result.addProperty("height", player.getHeight());
            result.addProperty("bodyClear", bodyClear(player));
            if (state.requested != null) {
                result.add("requested", position(state.requested));
                result.add("source", position(state.source));
                result.addProperty("sourceAllowed", state.sourceAllowed);
            }
            if (state.ability != null) {
                result.add("ability", JSON.toJsonTree(state.ability));
            }
            return result;
        });
    }

    private boolean bodyClear(Player player) {
        BoundingBox body = player.getBoundingBox();
        for (int x = Location.locToBlock(body.getMinX()); x <= Location.locToBlock(Math.nextDown(body.getMaxX())); x++) {
            for (int y = Location.locToBlock(body.getMinY()); y <= Location.locToBlock(Math.nextDown(body.getMaxY())); y++) {
                for (int z = Location.locToBlock(body.getMinZ()); z <= Location.locToBlock(Math.nextDown(body.getMaxZ())); z++) {
                    if (player.getWorld().getBlockAt(x, y, z).getCollisionShape().overlaps(body.clone().shift(-x, -y, -z))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private JsonObject history(String name) {
        for (State state : states.values()) {
            if (state.name.equals(name)) {
                JsonObject result = new JsonObject();
                result.add("lifecycle", JSON.toJsonTree(state.lifecycle));
                result.add("requested", position(state.requested));
                result.add("source", position(state.source));
                result.addProperty("sourceAllowed", state.sourceAllowed);
                return result;
            }
        }
        throw new IllegalArgumentException("Unknown arrival actor");
    }

    private JsonObject observe(Player player, String kind) {
        State state = states.get(player.getUniqueId());
        if (state == null) {
            return null;
        }
        JsonObject observation = new JsonObject();
        observation.addProperty("kind", kind);
        observation.addProperty("online", player.isOnline());
        observation.addProperty("allowed", ClaimsFixture.service().canTeleport(player, player.getLocation()));
        observation.add("to", position(player.getLocation()));
        state.lifecycle.add(observation);
        return observation;
    }

    private void restore() {
        for (Map.Entry<World, Location> entry : spawns.entrySet()) {
            entry.getKey().setSpawnLocation(entry.getValue());
        }
        spawns.clear();
    }

    private static JsonObject position(Location location) {
        JsonObject result = new JsonObject();
        result.addProperty("world", location.getWorld().getKey().toString());
        result.addProperty("x", location.getX());
        result.addProperty("y", location.getY());
        result.addProperty("z", location.getZ());
        result.addProperty("yaw", location.getYaw());
        return result;
    }

    private static final class State {
        private final String name;
        private JsonArray lifecycle = new JsonArray();
        private Location requested;
        private Location source;
        private boolean sourceAllowed;
        private boolean respawning;
        private int arrivals;
        private JsonObject ability;
        private Double scale;
        private Block ceiling;
        private BlockData ceilingData;

        private State(String name) {
            this.name = name;
        }
    }

    private enum Site {
        TOWN,
        BLOCKED,
        CLAIMED
    }
}
