package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.chunk.GeneratedChunkStructures;
import art.arcane.volmlib.nativelib.chunk.ChunkPosition;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.gameplay.demo.sets.StructureDiscoverySets;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SenseAuditFixtures implements Listener {
    private static final Map<UUID, AtomicInteger> newChunks = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<ChunkPosition>> loadedChunks = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<ChunkPosition, List<GeneratedChunkStructures.Entry>>> knownStarts = new ConcurrentHashMap<>();
    private static final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public static void install(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(new SenseAuditFixtures(), plugin);
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent event) {
        UUID worldId = event.getWorld().getUID();
        Chunk chunk = event.getChunk();
        ChunkPosition position = new ChunkPosition(chunk.getX(), chunk.getZ());
        loadedChunks.computeIfAbsent(worldId, id -> ConcurrentHashMap.newKeySet()).add(position);
        if (event.isNewChunk()) newChunks.computeIfAbsent(worldId, id -> new AtomicInteger()).incrementAndGet();
        J.runAt(new Location(event.getWorld(), position.x() * 16D, 0D, position.z() * 16D), () -> {
            List<GeneratedChunkStructures.Entry> starts = starts(chunk);
            if (!starts.isEmpty()) knownStarts.computeIfAbsent(worldId, id -> new ConcurrentHashMap<>()).put(position, starts);
        }, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnload(ChunkUnloadEvent event) {
        loadedChunks.computeIfAbsent(event.getWorld().getUID(), id -> ConcurrentHashMap.newKeySet())
                .remove(new ChunkPosition(event.getChunk().getX(), event.getChunk().getZ()));
    }

    public static JsonObject execute(Player player, String[] args) {
        switch (args[2]) {
            case "prepare" -> prepare(player);
            case "range" -> {
                double range = Double.parseDouble(args[3]);
                Object config = adaptation().getConfig();
                set(config, "detectionRangeBase", range);
                set(config, "detectionRangeFactor", 0D);
                set(config, "maxDetectionRange", range);
                adaptation().onPlayerPreferencesChanged(Adapt.instance.getAdaptServer().getPlayer(player));
            }
            case "preference" -> StructureDiscoverySets.preference(
                    player, "discovery-sixth-sense", "structures", args[3]);
            case "unload-target" -> prepareUnloadedTarget(player);
            case "unload" -> {
                Session session = sessions.get(player.getUniqueId());
                StructureInfo selected = session.selected;
                Location start = new Location(session.world, selected.chunkX() * 16D, 0D, selected.chunkZ() * 16D);
                J.runAt(start, () -> {
                    session.world.unloadChunkRequest(selected.chunkX(), selected.chunkZ());
                    J.runAt(start, () -> session.startUnloaded = !session.world.isChunkLoaded(
                            selected.chunkX(), selected.chunkZ()), 120);
                });
            }
            case "learn" -> learn(player);
            case "snapshot" -> { }
            case "cleanup" -> sessions.remove(player.getUniqueId());
            default -> throw new IllegalArgumentException("Unknown generated-structure fixture operation");
        }
        return snapshot(player);
    }

    private static void prepare(Player player) {
        World world = Bukkit.getWorld("world");
        if (world == null) throw new IllegalStateException("Native overworld required");
        Session session = new Session(world, world.getSpawnLocation().add(1024D, 0D, 0D));
        sessions.put(player.getUniqueId(), session);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(true);
        player.setFlying(true);
        heartbeat(player, session);
        generateBatch(player, session);
    }

    private static void prepareUnloadedTarget(Player player) {
        Session session = sessions.get(player.getUniqueId());
        try {
            Object target = ((Map<?, ?>) field(adaptation(), "targets")).get(player.getUniqueId());
            if (target == null) throw new IllegalStateException("Sense a native target before unloading its start");
            double x = (double) invoke(target, "x");
            double z = (double) invoke(target, "z");
            MatchingStart start = matchingStart(player.getWorld(), x, z);
            if (start == null) throw new IllegalStateException("Native target start was not observed");
            AdaptPlayer learner = Adapt.instance.getAdaptServer().getPlayer(player);
            learner.getData().getSkillLine("discovery").setAdaptation(adaptation(), 0);
            adaptation().onPlayerPreferencesChanged(learner);
            double dx = x - (start.position().x() * 16D + 8D);
            double dz = z - (start.position().z() * 16D + 8D);
            double length = Math.hypot(dx, dz);
            if (length < 1D) { dx = 1D; dz = 0D; length = 1D; }
            GeneratedChunkStructures.Bounds box = start.entry().bounds();
            double standOff = Math.max(32D, 112D - length);
            session.selected = new StructureInfo(new BoundingBox(box.minX(), box.minY(), box.minZ(),
                    box.maxX(), box.maxY(), box.maxZ()), x + dx / length * standOff, z + dz / length * standOff,
                    start.position().x(), start.position().z());
            session.ready = false;
            session.startUnloaded = false;
            finishPreparation(player, session);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static MatchingStart matchingStart(World world, double x, double z) {
        for (Map.Entry<ChunkPosition, List<GeneratedChunkStructures.Entry>> starts : knownStarts
                .getOrDefault(world.getUID(), Map.of()).entrySet()) {
            for (GeneratedChunkStructures.Entry start : starts.getValue()) {
                GeneratedChunkStructures.Bounds box = start.bounds();
                if (Double.compare((box.minX() + box.maxX()) / 2D, x) == 0
                        && Double.compare((box.minZ() + box.maxZ()) / 2D, z) == 0) {
                    return new MatchingStart(starts.getKey(), start);
                }
            }
        }
        return null;
    }

    private static void heartbeat(Player player, Session session) {
        if (sessions.get(player.getUniqueId()) != session) return;
        session.ticks.incrementAndGet();
        J.runEntity(player, () -> heartbeat(player, session), 1);
    }

    private static void generateBatch(Player player, Session session) {
        if (sessions.get(player.getUniqueId()) != session || session.failure != null) return;
        for (int batch = 0; batch < 4 && session.inFlight.get() < 8; batch++) {
            int index = session.requested.getAndIncrement();
            if (index >= 289) break;
            int x = (session.origin.getBlockX() >> 4) + index % 17 - 8;
            int z = (session.origin.getBlockZ() >> 4) + index / 17 - 8;
            session.inFlight.incrementAndGet();
            Location location = new Location(session.world, x * 16D, 0D, z * 16D);
            J.runAt(location, () -> session.world.getChunkAtAsync(x, z, true, false).whenComplete((chunk, failure) -> {
                if (failure != null) {
                    fail(session, failure);
                    return;
                }
                inspectPrepared(session, chunk);
            }));
        }
        if (session.completed.get() == 289) {
            J.runEntity(player, () -> finishPreparation(player, session));
        } else {
            J.s(() -> generateBatch(player, session), 1);
        }
    }

    private static void inspectPrepared(Session session, Chunk chunk) {
        try {
            if (!Bukkit.isOwnedByCurrentRegion(chunk.getWorld(), chunk.getX(), chunk.getZ())) {
                throw new IllegalStateException("Preparation callback is not chunk-owned");
            }
            if (chunk.isLoaded()) session.loadedCallbacks.incrementAndGet();
            List<GeneratedChunkStructures.Entry> metadata = starts(chunk);
            if (!metadata.isEmpty()) knownStarts.computeIfAbsent(chunk.getWorld().getUID(), id -> new ConcurrentHashMap<>())
                    .put(new ChunkPosition(chunk.getX(), chunk.getZ()), metadata);
            for (GeneratedChunkStructures.Entry generated : metadata) {
                session.preparedTypes.computeIfAbsent(generated.typeKey().toString(), key -> new AtomicInteger()).incrementAndGet();
                String type = generated.typeKey().getKey();
                if (!List.of("mineshaft", "jigsaw", "ruined_portal", "shipwreck").contains(type)) continue;
                GeneratedChunkStructures.Bounds box = generated.bounds();
                BoundingBox bounds = new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
                double x = bounds.getMaxX() + 48D;
                double z = bounds.getCenterZ();
                if (Math.hypot(x - bounds.getCenterX(), z - bounds.getCenterZ()) > 450D) continue;
                synchronized (session) {
                    if (session.selected == null) session.selected = new StructureInfo(bounds.clone(), x, z, chunk.getX(), chunk.getZ());
                }
            }
        } catch (RuntimeException failure) {
            fail(session, failure);
        } finally {
            session.completed.incrementAndGet();
            session.inFlight.decrementAndGet();
        }
    }

    private static void finishPreparation(Player player, Session session) {
        if (session.selected == null) {
            fail(session, new IllegalStateException("Bounded native terrain contains no supported structure"));
            return;
        }
        StructureInfo selected = session.selected;
        Location destination = new Location(session.world, selected.x(), 200D, selected.z());
        player.teleportAsync(destination).whenComplete((success, failure) -> {
            if (failure != null || !Boolean.TRUE.equals(success)) {
                fail(session, failure == null ? new IllegalStateException("Preparation teleport rejected") : failure);
                return;
            }
            J.runEntity(player, () -> {
                player.setAllowFlight(true);
                player.setFlying(true);
                J.runEntity(player, () -> session.ready = true, 100);
            });
        });
    }

    private static void learn(Player player) {
        Session session = sessions.get(player.getUniqueId());
        session.loadedBeforeSense = Set.copyOf(loadedChunks.getOrDefault(player.getWorld().getUID(), Set.of()));
        AdaptPlayer learner = Adapt.instance.getAdaptServer().getPlayer(player);
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                learner.getData().getSkillLine(skill.getName()).setAdaptation(adaptation,
                        adaptation.getName().equals("discovery-sixth-sense") ? 5 : 0);
            }
        }
    }

    private static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        Session session = sessions.get(player.getUniqueId());
        result.addProperty("ready", session != null && session.ready);
        result.addProperty("failure", session == null || session.failure == null ? "" : session.failure.toString());
        result.addProperty("ticks", session == null ? 0 : session.ticks.get());
        result.addProperty("startUnloaded", session != null && session.startUnloaded);
        result.addProperty("preparedChunks", session == null ? 0 : session.completed.get());
        result.addProperty("loadedCallbacks", session == null ? 0 : session.loadedCallbacks.get());
        JsonObject preparedTypes = new JsonObject();
        if (session != null) session.preparedTypes.forEach((key, count) -> preparedTypes.addProperty(key, count.get()));
        result.add("preparedTypes", preparedTypes);
        result.addProperty("x", player.getX());
        result.addProperty("y", player.getY());
        result.addProperty("z", player.getZ());
        result.addProperty("dead", player.isDead());
        result.addProperty("activeLevel", adaptation().getActiveLevel(player));
        result.addProperty("hasOnlineLearner", Adapt.instance.getAdaptServer().hasOnlineLearner(player.getUniqueId(), adaptation().getName()));
        result.addProperty("newChunks", newChunks.computeIfAbsent(player.getWorld().getUID(), id -> new AtomicInteger()).get());
        result.addProperty("senses", Adapt.instance.getAdaptServer().getPlayer(player).getData().getStat("discovery.sixth-sense.senses"));
        if (session != null && session.selected != null) {
            result.addProperty("expectedX", session.selected.bounds().getCenterX());
            result.addProperty("expectedZ", session.selected.bounds().getCenterZ());
        }
        try {
            Object search = ((Map<?, ?>) field(adaptation(), "searches")).get(player.getUniqueId());
            result.addProperty("searchRunning", search != null && (boolean) invoke(search, "running"));
            result.addProperty("searchCancelled", search != null && ((AtomicBoolean) field(search, "cancelled")).get());
            Object queue = field(adaptation(), "structureSearch");
            result.addProperty("searchClosed", (boolean) field(queue, "closed"));
            result.addProperty("searchInFlight", ((AtomicInteger) field(queue, "inFlight")).get());
            Object target = ((Map<?, ?>) field(adaptation(), "targets")).get(player.getUniqueId());
            result.addProperty("target", target != null);
            if (target != null) {
                result.addProperty("targetX", (double) invoke(target, "x"));
                result.addProperty("targetZ", (double) invoke(target, "z"));
                result.addProperty("targetName", (String) invoke(target, "name"));
                double targetX = (double) invoke(target, "x");
                double targetZ = (double) invoke(target, "z");
                MatchingStart start = matchingStart(player.getWorld(), targetX, targetZ);
                result.addProperty("targetStartKnown", start != null);
                result.addProperty("targetWasUnloaded", start != null && session != null
                        && !session.loadedBeforeSense.contains(start.position()));
                result.addProperty("distance", Math.hypot((double) invoke(target, "x") - player.getX(),
                        (double) invoke(target, "z") - player.getZ()));
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
        return result;
    }

    private static Adaptation<?> adaptation() {
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (adaptation.getName().equals("discovery-sixth-sense")) return adaptation;
            }
        }
        throw new IllegalStateException("Sixth Sense unavailable");
    }

    private static List<GeneratedChunkStructures.Entry> starts(Chunk chunk) {
        return NativeAdapters.require(GeneratedChunkStructures.class).read(chunk).starts();
    }

    private static Object invoke(Object object, String name) throws ReflectiveOperationException {
        Method method = object.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(object);
    }

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void set(Object object, String name, Object value) {
        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(object, value);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void fail(Session session, Throwable failure) {
        session.failure = failure;
        Adapt.error("Generated-structure gameplay preparation failed", failure);
    }

    private static final class Session {
        private final World world;
        private final Location origin;
        private final AtomicInteger ticks = new AtomicInteger();
        private final AtomicInteger requested = new AtomicInteger();
        private final AtomicInteger completed = new AtomicInteger();
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicInteger loadedCallbacks = new AtomicInteger();
        private final Map<String, AtomicInteger> preparedTypes = new ConcurrentHashMap<>();
        private volatile StructureInfo selected;
        private volatile boolean ready;
        private volatile boolean startUnloaded;
        private volatile Set<ChunkPosition> loadedBeforeSense = Set.of();
        private volatile Throwable failure;

        private Session(World world, Location origin) {
            this.world = world;
            this.origin = origin;
        }
    }

    private record MatchingStart(ChunkPosition position, GeneratedChunkStructures.Entry entry) {
    }

    private record StructureInfo(BoundingBox bounds, double x, double z, int chunkX, int chunkZ) {
    }
}
