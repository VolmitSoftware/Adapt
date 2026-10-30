package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.claims.ClaimBlock;
import art.arcane.adapt.api.claims.ClaimChunk;
import art.arcane.adapt.api.claims.ClaimCity;
import art.arcane.adapt.api.claims.ClaimManager;
import art.arcane.adapt.api.claims.ClaimWorldAccess;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.portal.NetherPortalAccess;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class ClaimsPortalFixture implements Listener {
    private static final Gson JSON = new Gson();
    private static final int HEIGHT = 80;

    private final ClaimsFixture plugin;
    private final Map<UUID, Location> origins = new HashMap<>();
    private final Map<UUID, List<JsonObject>> observations = new HashMap<>();
    private final Map<UUID, Entity> mounts = new HashMap<>();
    private final List<Frame> frames = new ArrayList<>();
    private CompletableFuture<Void> arena;
    private World overworld;
    private World nether;
    private World end;
    private Player founder;
    private Player member;
    private Location anchor;
    private Location personal;
    private Location entry;
    private NetherPortalAccess nativePortals;

    ClaimsPortalFixture(ClaimsFixture plugin) {
        this.plugin = plugin;
    }

    CompletableFuture<JsonObject> command(Player player, String[] args) {
        return switch (args[1]) {
            case "portal-prepare" -> prepare(player, args[2]);
            case "portal-layout" -> layout(args[2]).thenCompose(ignored -> snapshot(player));
            case "portal-enter" -> enter(player, args.length > 2 ? args[2] : "nether");
            case "portal-reload" -> plugin.reload().thenCompose(ignored -> snapshot(player));
            case "portal-snapshot" -> snapshot(player);
            case "portal-watch" -> ClaimsFixture.entity(player, () -> {
                observations.get(player.getUniqueId()).clear();
                return null;
            }).thenCompose(ignored -> snapshot(player));
            case "portal-mount" -> mount(player);
            case "portal-cleanup" -> cleanup(player);
            default -> CompletableFuture.failedFuture(new IllegalArgumentException("Unknown portal fixture action"));
        };
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void portal(PlayerPortalEvent event) {
        teleport(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void teleport(PlayerTeleportEvent event) {
        List<JsonObject> events = observations.get(event.getPlayer().getUniqueId());
        if (events == null) {
            return;
        }
        JsonObject observation = new JsonObject();
        observation.addProperty("kind", event instanceof PlayerPortalEvent ? "portal" : "teleport");
        observation.addProperty("cause", event.getCause().name());
        observation.addProperty("cancelled", event.isCancelled());
        observation.addProperty("pending", ClaimsFixture.service().travel().pending(event.getPlayer()));
        observation.addProperty("allowed", ClaimsFixture.service().canTeleport(event.getPlayer(), event.getTo()));
        observation.add("from", position(event.getFrom()));
        observation.add("to", position(event.getTo()));
        if (event instanceof PlayerPortalEvent portal) {
            observation.addProperty("searchRadius", portal.getSearchRadius());
            observation.add("motion", vector(nativePortals.movement(event.getPlayer())));
        }
        events.add(observation);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void arrived(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        List<JsonObject> events = observations.get(player.getUniqueId());
        if (events == null) {
            return;
        }
        JsonObject observation = new JsonObject();
        observation.addProperty("kind", "arrival");
        observation.addProperty("allowed", ClaimsFixture.service().canTeleport(player, player.getLocation()));
        observation.add("to", position(player.getLocation()));
        events.add(observation);
        J.runEntity(player, () -> {
            observation.addProperty("cooldown", player.getPortalCooldown());
            observation.add("velocity", vector(player.getVelocity()));
        }, 2);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void created(PortalCreateEvent event) {
        if (!(event.getEntity() instanceof Player player) || !observations.containsKey(player.getUniqueId())) {
            return;
        }
        JsonObject observation = new JsonObject();
        observation.addProperty("kind", "create");
        observation.addProperty("reason", event.getReason().name());
        observation.addProperty("cancelled", event.isCancelled());
        JsonArray blocks = new JsonArray();
        for (BlockState state : event.getBlocks()) {
            JsonObject block = position(state.getLocation());
            block.addProperty("material", state.getType().name());
            block.addProperty("before", state.getBlock().getType().name());
            block.addProperty("allowed", ClaimsFixture.service().canPrepareArrival(player, state.getLocation()));
            blocks.add(block);
        }
        observation.add("blocks", blocks);
        observations.get(player.getUniqueId()).add(observation);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void entered(EntityPortalEnterEvent event) {
        List<JsonObject> events = observations.get(event.getEntity().getUniqueId());
        if (events == null || events.size() >= 24) {
            return;
        }
        JsonObject observation = new JsonObject();
        observation.addProperty("kind", "enter");
        observation.addProperty("cancelled", event.isCancelled());
        observation.add("to", position(event.getLocation()));
        events.add(observation);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void entityPortal(EntityPortalEvent event) {
        Deque<Entity> passengers = new ArrayDeque<>(event.getEntity().getPassengers());
        while (!passengers.isEmpty()) {
            Entity passenger = passengers.removeFirst();
            if (passenger instanceof Player player && observations.containsKey(player.getUniqueId())) {
                JsonObject observation = new JsonObject();
                observation.addProperty("kind", "vehiclePortal");
                observation.addProperty("vehicle", event.getEntity().getUniqueId().toString());
                observation.addProperty("cancelled", event.isCancelled());
                observation.add("to", position(event.getTo()));
                observations.get(player.getUniqueId()).add(observation);
            }
            passengers.addAll(passenger.getPassengers());
        }
    }

    private CompletableFuture<JsonObject> prepare(Player player, String role) {
        return ClaimsFixture.entity(player, () -> {
            if (J.isFoliaThreading() || !List.of("founder", "member", "attacker").contains(role)) {
                throw new IllegalArgumentException("Portal fixture requires Paper and a supported role");
            }
            ClaimsFixture.runtime(player);
            overworld = Objects.requireNonNull(Bukkit.getWorld(NamespacedKey.minecraft("overworld")));
            nether = Objects.requireNonNull(Bukkit.getWorld(NamespacedKey.minecraft("the_nether")));
            end = Objects.requireNonNull(Bukkit.getWorld(NamespacedKey.minecraft("the_end")));
            nativePortals = NativeAdapters.find(NetherPortalAccess.class).orElseThrow();
            if (origins.putIfAbsent(player.getUniqueId(), player.getLocation()) != null) {
                throw new IllegalStateException("Use fresh portal actors");
            }
            observations.put(player.getUniqueId(), new ArrayList<>());
            player.setGameMode(GameMode.CREATIVE);
            player.setFlying(false);
            player.setGravity(false);
            player.setInvulnerable(true);
            if (!role.equals("attacker")) {
                Skill<?> skill = Adapt.instance.getAdaptServer().getSkillRegistry().getAnySkill("claims");
                PlayerSkillLine line = ClaimsFixture.runtime(player).getData().getSkillLine("claims");
                for (Adaptation<?> adaptation : skill.getAdaptations()) {
                    line.setAdaptation(adaptation, 1);
                }
                ClaimsFixture.service().refreshRanks(player);
            }
            if (role.equals("founder")) {
                founder = player;
            } else if (role.equals("member")) {
                member = player;
            }
            return null;
        }).thenCompose(ignored -> prepareArena()).thenCompose(ignored -> ClaimsFixture.entity(player, () -> {
            if (role.equals("founder")) {
                found(new Location(overworld, 4104, HEIGHT - 1, 4104), true);
                initialFrames();
            } else if (role.equals("member")) {
                join();
            }
            return null;
        })).thenCompose(ignored -> snapshot(player));
    }

    private synchronized CompletableFuture<Void> prepareArena() {
        if (arena == null) {
            arena = CompletableFuture.allOf(
                    floor(overworld, 254, 261, 254, 259, HEIGHT),
                    floor(nether, 31, 33, 31, 33, HEIGHT),
                    floor(end, 4, 8, -2, 2, 49));
        }
        return arena;
    }

    private CompletableFuture<Void> floor(World world, int minX, int maxX, int minZ, int maxZ, int height) {
        List<CompletableFuture<Void>> loading = new ArrayList<>();
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                int x = chunkX;
                int z = chunkZ;
                loading.add(world.getChunkAtAsync(x, z).thenCompose(chunk -> ClaimsFixture.at(new Location(world, x * 16, height, z * 16), () -> {
                    world.addPluginChunkTicket(x, z, plugin);
                    for (int blockX = x * 16; blockX < (x + 1) * 16; blockX++) {
                        for (int blockZ = z * 16; blockZ < (z + 1) * 16; blockZ++) {
                            world.getBlockAt(blockX, height - 1, blockZ).setType(Material.STONE, false);
                            for (int y = height; y <= height + 6; y++) {
                                world.getBlockAt(blockX, y, blockZ).setType(Material.AIR, false);
                            }
                        }
                    }
                    return null;
                })));
            }
        }
        return CompletableFuture.allOf(loading.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Void> layout(String name) {
        return ClaimsFixture.entity(founder, () -> {
            clearFrames();
            switch (name) {
                case "initial" -> {
                    found(new Location(overworld, 4104, HEIGHT - 1, 4104), true);
                    initialFrames();
                }
                case "empty" -> source(513, 513.5);
                case "boundary" -> source(509.75, 513.5);
                case "homestead" -> source(519, 513.5);
                case "anchor" -> {
                    found(new Location(overworld, 4104, HEIGHT - 1, 4108), true);
                    source(513, 513.5);
                }
                case "end" -> {
                    found(new Location(end, 100, 48, 0), false);
                    for (int x = 4168; x <= 4170; x++) {
                        for (int z = 4110; z <= 4112; z++) {
                            overworld.getBlockAt(x, HEIGHT, z).setType(Material.END_PORTAL, false);
                        }
                    }
                    entry = new Location(overworld, 4169.5, HEIGHT, 4111.5);
                }
                default -> throw new IllegalArgumentException("Unknown portal layout");
            }
            return null;
        });
    }

    private void found(Location replacement, boolean expand) {
        ClaimManager manager = ClaimsFixture.service().manager();
        ClaimCity previous = manager.snapshot().cityOf(founder.getUniqueId()).orElse(null);
        if (previous != null) {
            manager.removeCity(previous.id());
        }
        if (anchor != null) {
            anchor.getBlock().setType(Material.STONE, false);
        }
        anchor = replacement;
        anchor.getBlock().setType(Material.LODESTONE, false);
        ClaimWorldAccess access = new ClaimWorldAccess(anchor.getWorld().getName(), anchor.getWorld().getEnvironment() == World.Environment.NORMAL);
        manager.found(founder.getUniqueId(), "Portal Trial", new ClaimBlock(anchor.getWorld().getUID(), anchor.getBlockX(), anchor.getBlockY(), anchor.getBlockZ()),
                ClaimsFixture.service().ranks(founder), access);
        if (expand) {
            for (int x = 258; x <= 260; x++) {
                for (int z = 255; z <= 257; z++) {
                    manager.expand(founder.getUniqueId(), new ClaimChunk(overworld.getUID(), x, z), access);
                }
            }
        }
        if (member != null) {
            join();
        }
    }

    private void join() {
        ClaimManager manager = ClaimsFixture.service().manager();
        long now = System.currentTimeMillis();
        manager.invite(founder.getUniqueId(), member.getUniqueId(), now);
        manager.accept(member.getUniqueId(), founder.getUniqueId(), now);
        if (anchor.getWorld() == overworld) {
            personal = new Location(overworld, 4152, HEIGHT - 1, 4104);
            personal.getBlock().setType(Material.LODESTONE, false);
            manager.createPlot(member.getUniqueId(), new ClaimBlock(overworld.getUID(), personal.getBlockX(), personal.getBlockY(), personal.getBlockZ()),
                    ClaimsFixture.service().ranks(member));
        }
    }

    private void initialFrames() {
        frame(new Location(overworld, 4104, HEIGHT, 4108), Axis.X);
        frame(new Location(overworld, 4078, HEIGHT, 4108), Axis.X);
        frame(new Location(overworld, 4073, HEIGHT, 4108), Axis.Z);
        source(513, 513.5);
    }

    private void source(double x, double z) {
        frame(new Location(nether, (int) Math.floor(x - 0.5), HEIGHT, (int) Math.floor(z)), Axis.X);
        entry = new Location(nether, x, HEIGHT, z);
    }

    private void frame(Location location, Axis axis) {
        World world = location.getWorld();
        for (int across = -1; across <= 2; across++) {
            for (int y = -1; y <= 3; y++) {
                int x = location.getBlockX() + (axis == Axis.X ? across : 0);
                int z = location.getBlockZ() + (axis == Axis.Z ? across : 0);
                if (across == -1 || across == 2 || y == -1 || y == 3) {
                    world.getBlockAt(x, HEIGHT + y, z).setType(Material.OBSIDIAN, false);
                } else {
                    Orientable portal = (Orientable) Material.NETHER_PORTAL.createBlockData();
                    portal.setAxis(axis);
                    world.getBlockAt(x, HEIGHT + y, z).setBlockData(portal, false);
                }
            }
        }
        frames.add(new Frame(location.clone(), axis));
    }

    private void clearFrames() {
        for (Frame frame : frames) {
            Location location = frame.location();
            for (int across = -1; across <= 2; across++) {
                for (int y = -1; y <= 3; y++) {
                    int x = location.getBlockX() + (frame.axis() == Axis.X ? across : 0);
                    int z = location.getBlockZ() + (frame.axis() == Axis.Z ? across : 0);
                    location.getWorld().getBlockAt(x, HEIGHT + y, z).setType(y == -1 ? Material.STONE : Material.AIR, false);
                }
            }
        }
        frames.clear();
        for (int x = 4064; x <= 4191; x++) {
            for (int z = 4064; z <= 4159; z++) {
                for (int y = HEIGHT; y <= HEIGHT + 6; y++) {
                    Material material = overworld.getBlockAt(x, y, z).getType();
                    if (material == Material.NETHER_PORTAL || material == Material.OBSIDIAN) {
                        overworld.getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private CompletableFuture<JsonObject> enter(Player player, String source) {
        Location target = source.equals("overworld") ? new Location(overworld, 4105, HEIGHT, 4108.5) : entry.clone();
        Location approach = target.clone().add(0, 0, -2);
        if (source.equals("end") || source.equals("stationary")) {
            approach = target;
        }
        Location destination = approach;
        return ClaimsFixture.entity(player, () -> {
            Entity mount = mounts.remove(player.getUniqueId());
            if (mount != null) {
                mount.eject();
                mount.remove();
            }
            player.setPortalCooldown(300);
            player.setFlying(false);
            player.setVelocity(new Vector());
            return null;
        }).thenCompose(ignored -> player.teleportAsync(destination)).thenCompose(moved -> {
            if (!moved) {
                return CompletableFuture.failedFuture(new IllegalStateException("Portal approach teleport refused"));
            }
            return ClaimsFixture.entity(player, () -> {
                observations.get(player.getUniqueId()).clear();
                player.setPortalCooldown(0);
                return null;
            });
        }).thenCompose(ignored -> snapshot(player));
    }

    private CompletableFuture<JsonObject> mount(Player player) {
        return ClaimsFixture.entity(player, () -> {
            player.setPortalCooldown(300);
            return null;
        }).thenCompose(ignored -> player.teleportAsync(entry)).thenCompose(moved -> ClaimsFixture.entity(player, () -> {
            observations.get(player.getUniqueId()).clear();
            Entity vehicle = entry.getWorld().spawnEntity(entry, EntityType.OAK_BOAT);
            vehicle.setGravity(false);
            vehicle.setInvulnerable(true);
            vehicle.setPortalCooldown(0);
            if (!vehicle.addPassenger(player)) {
                throw new IllegalStateException("Boat mount refused");
            }
            mounts.put(player.getUniqueId(), vehicle);
            return null;
        })).thenCompose(ignored -> snapshot(player));
    }

    private CompletableFuture<JsonObject> snapshot(Player player) {
        return ClaimsFixture.entity(player, () -> {
            JsonObject result = new JsonObject();
            result.addProperty("player", player.getUniqueId().toString());
            result.addProperty("operator", player.isOp());
            result.addProperty("bypass", ClaimsFixture.service().bypass(player));
            result.addProperty("pending", ClaimsFixture.service().travel().pending(player));
            result.addProperty("gameMode", player.getGameMode().name());
            result.addProperty("portalDelay", player.getWorld().getGameRuleValue(GameRule.PLAYERS_NETHER_PORTAL_CREATIVE_DELAY));
            result.addProperty("portalEnabled", player.getWorld().getGameRuleValue(GameRule.ALLOW_ENTERING_NETHER_USING_PORTALS));
            result.addProperty("cooldown", player.getPortalCooldown());
            result.add("location", position(player.getLocation()));
            result.add("velocity", vector(player.getVelocity()));
            result.add("events", JSON.toJsonTree(observations.get(player.getUniqueId())));
            result.add("city", JSON.toJsonTree(ClaimsFixture.service().manager().snapshot().cityOf(founder.getUniqueId()).orElse(null)));
            result.add("plots", JSON.toJsonTree(ClaimsFixture.service().manager().snapshot().plots()));
            result.add("anchor", position(anchor));
            result.addProperty("anchorMaterial", anchor.getBlock().getType().name());
            result.addProperty("personalMaterial", personal == null ? "AIR" : personal.getBlock().getType().name());
            result.add("entry", position(entry));
            result.addProperty("mounted", player.isInsideVehicle());
            if (player.getVehicle() != null) {
                result.addProperty("vehicle", player.getVehicle().getUniqueId().toString());
                result.add("vehicleLocation", position(player.getVehicle().getLocation()));
            }
            return result;
        });
    }

    private CompletableFuture<JsonObject> cleanup(Player player) {
        return ClaimsFixture.entity(player, () -> {
            Entity mount = mounts.remove(player.getUniqueId());
            if (mount != null) {
                mount.eject();
                mount.remove();
            }
            observations.remove(player.getUniqueId());
            ClaimsFixture.service().travel().cancel(player);
            player.setGravity(true);
            player.setInvulnerable(false);
            player.setPortalCooldown(300);
            ClaimManager manager = ClaimsFixture.service().manager();
            ClaimCity city = manager.snapshot().cityOf(player.getUniqueId()).orElse(null);
            if (city != null && city.founder().equals(player.getUniqueId())) {
                manager.removeCity(city.id());
            }
            return origins.remove(player.getUniqueId());
        }).thenCompose(origin -> origin == null ? CompletableFuture.completedFuture(true) : player.teleportAsync(origin))
                .thenApply(ignored -> {
                    if (origins.isEmpty()) {
                        for (World world : List.of(overworld, nether, end)) {
                            world.removePluginChunkTickets(plugin);
                        }
                    }
                    JsonObject result = new JsonObject();
                    result.addProperty("cleaned", true);
                    return result;
                });
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

    private static JsonObject vector(Vector vector) {
        JsonObject result = new JsonObject();
        result.addProperty("x", vector.getX());
        result.addProperty("y", vector.getY());
        result.addProperty("z", vector.getZ());
        return result;
    }

    private record Frame(Location location, Axis axis) {
    }
}
