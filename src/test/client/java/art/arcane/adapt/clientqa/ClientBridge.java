package art.arcane.adapt.clientqa;

import art.arcane.adapt.clientqa.mixin.KeyMappingAccessor;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ClientBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("AdaptClientQA");
    private static final Gson GSON = new Gson();
    private static final int EVENT_LIMIT = 4096;
    private static final ArrayBlockingQueue<Request> REQUESTS = new ArrayBlockingQueue<>(64);
    private static final ArrayDeque<JsonObject> EVENTS = new ArrayDeque<>();
    private static final List<SoundProbe> SOUND_PROBES = new ArrayList<>();
    private static final Map<String, Boolean> HELD_KEYS = new HashMap<>();
    private static final ReferenceQueue<Particle> PARTICLE_REFERENCES = new ReferenceQueue<>();
    private static final LinkedHashMap<ParticleIdentity, JsonObject> PARTICLE_EVENTS = new LinkedHashMap<>();
    private static final Map<QuadParticleRenderState, Map<SingleQuadParticle.Layer, List<JsonObject>>> PARTICLE_GEOMETRY = new WeakHashMap<>();
    private static HttpServer server;
    private static Path output;
    private static String token;
    private static long ticks;
    private static long frames;
    private static long particleLayers;
    private static long droppedEvents;
    private static long keyDeadline;

    private ClientBridge() {
    }

    public static void tick(Minecraft client) {
        if (server == null) {
            start(client);
        }
        ticks++;
        if (ticks >= keyDeadline && !HELD_KEYS.isEmpty()) {
            release(client);
        }
        for (Map.Entry<String, Boolean> key : HELD_KEYS.entrySet()) {
            key(client, key.getKey()).setDown(key.getValue());
        }
        sampleSounds();
        for (int processed = 0; processed < 16; processed++) {
            Request request = REQUESTS.poll();
            if (request == null) {
                break;
            }
            if (request.result().isCancelled()) {
                continue;
            }
            try {
                request.result().complete(execute(client, request.input()));
            } catch (RuntimeException failure) {
                LOGGER.error("Client QA command failed", failure);
                request.result().completeExceptionally(failure);
            }
        }
    }

    public static synchronized void frame() {
        frames++;
        cleanParticleReferences();
    }

    public static synchronized void particleLayer(QuadParticleRenderState state, SingleQuadParticle.Layer layer) {
        particleLayers++;
        Map<SingleQuadParticle.Layer, List<JsonObject>> layers = PARTICLE_GEOMETRY.get(state);
        List<JsonObject> events = layers == null ? null : layers.get(layer);
        if (events == null) {
            return;
        }
        for (JsonObject event : events) {
            if (event.has("lastRenderedFrame") && event.get("lastRenderedFrame").getAsLong() == frames) {
                continue;
            }
            event.addProperty("rendered", true);
            event.addProperty("renderedFrames", event.get("renderedFrames").getAsLong() + 1);
            event.addProperty("lastRenderedFrame", frames);
        }
    }

    public static synchronized void particleGeometry(Particle particle, QuadParticleRenderState state,
            SingleQuadParticle.Layer layer) {
        JsonObject event = PARTICLE_EVENTS.get(new ParticleIdentity(particle, null));
        if (event == null) {
            return;
        }
        Map<SingleQuadParticle.Layer, List<JsonObject>> layers = PARTICLE_GEOMETRY.get(state);
        if (layers == null) {
            if (PARTICLE_GEOMETRY.size() >= 64) {
                return;
            }
            layers = new HashMap<>();
            PARTICLE_GEOMETRY.put(state, layers);
        }
        List<JsonObject> events = layers.computeIfAbsent(layer, ignored -> new ArrayList<>());
        if (events.size() < EVENT_LIMIT) {
            events.add(event);
            event.addProperty("extractedQuads", event.get("extractedQuads").getAsLong() + 1);
        }
    }

    public static synchronized void clearParticleGeometry(QuadParticleRenderState state) {
        PARTICLE_GEOMETRY.remove(state);
    }

    public static synchronized void particle(ParticleOptions options, double x, double y, double z, Particle created) {
        JsonObject event = event("particle");
        event.addProperty("name", BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType()).toString());
        event.addProperty("created", created != null);
        event.addProperty("rendered", false);
        event.addProperty("renderedFrames", 0);
        event.addProperty("extractedQuads", 0);
        event.addProperty("particleClass", created == null ? null : created.getClass().getName());
        event.add("position", vector(new Vec3(x, y, z)));
        append(event);
        if (created != null) {
            cleanParticleReferences();
            if (PARTICLE_EVENTS.size() >= EVENT_LIMIT) {
                PARTICLE_EVENTS.pollFirstEntry();
            }
            PARTICLE_EVENTS.put(new ParticleIdentity(created, PARTICLE_REFERENCES), event);
        }
    }

    public static synchronized void sound(SoundInstance sound, SoundEngine.PlayResult result,
            boolean active, ChannelAccess.ChannelHandle handle) {
        JsonObject event = event("sound");
        event.addProperty("name", sound.getIdentifier().toString());
        event.addProperty("volume", sound.getVolume());
        event.addProperty("pitch", sound.getPitch());
        event.addProperty("result", result.name());
        event.addProperty("engineActive", active);
        event.addProperty("channelPlaying", false);
        event.add("position", vector(new Vec3(sound.getX(), sound.getY(), sound.getZ())));
        append(event);
        if (handle != null && SOUND_PROBES.size() < 128) {
            SOUND_PROBES.add(new SoundProbe(handle, event, ticks + 20));
        }
    }

    public static void close() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private static void start(Minecraft client) {
        client.options.pauseOnLostFocus = false;
        token = System.getProperty("adapt.qa.token", "");
        if (token.length() < 16) {
            throw new IllegalStateException("Set adapt.qa.token to at least 16 characters");
        }
        output = Path.of(System.getProperty("adapt.qa.output",
                client.gameDirectory.toPath().resolve("client-qa").toString())).toAbsolutePath().normalize();
        try {
            Files.createDirectories(output);
            server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),
                    Integer.getInteger("adapt.qa.port", 18762)), 8);
            server.createContext("/", ClientBridge::http);
            server.setExecutor(Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory()));
            server.start();
            LOGGER.info("Client QA bridge listening on {} with output {}", server.getAddress(), output);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot start client QA bridge", failure);
        }
    }

    private static void http(HttpExchange exchange) throws IOException {
        String suppliedToken = exchange.getRequestHeaders().getFirst("X-Adapt-QA-Token");
        if (suppliedToken == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8)) || exchange.getRequestHeaders().containsKey("Origin")) {
            respond(exchange, 403, error("Client QA token required; browser origins are not accepted"));
            return;
        }
        JsonObject input;
        try {
            if (exchange.getRequestMethod().equals("GET") && exchange.getRequestURI().getPath().equals("/state")) {
                input = new JsonObject();
                input.addProperty("op", "state");
            } else if (exchange.getRequestMethod().equals("POST") && exchange.getRequestURI().getPath().equals("/command")) {
                byte[] body = exchange.getRequestBody().readNBytes(8193);
                if (body.length > 8192) {
                    throw new IllegalArgumentException("Request exceeds 8192 bytes");
                }
                input = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            } else {
                respond(exchange, 404, error("Use GET /state or POST /command"));
                return;
            }
        } catch (RuntimeException failure) {
            respond(exchange, 400, error(failure.toString()));
            return;
        }
        CompletableFuture<JsonObject> result = new CompletableFuture<>();
        if (!REQUESTS.offer(new Request(input, result))) {
            respond(exchange, 429, error("Client command queue is full"));
            return;
        }
        try {
            respond(exchange, 200, result.get(5, TimeUnit.SECONDS));
        } catch (TimeoutException failure) {
            result.cancel(false);
            respond(exchange, 504, error("Client did not process command within five seconds"));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            result.cancel(false);
            respond(exchange, 503, error("Client bridge interrupted"));
        } catch (ExecutionException failure) {
            respond(exchange, 400, error(failure.getCause().toString()));
        }
    }

    private static void respond(HttpExchange exchange, int status, JsonObject value) throws IOException {
        byte[] body = GSON.toJson(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (exchange) {
            exchange.getResponseBody().write(body);
        }
    }

    private static JsonObject execute(Minecraft client, JsonObject input) {
        String operation = input.get("op").getAsString();
        switch (operation) {
            case "state" -> { }
            case "keys" -> {
                for (String name : List.of("forward", "back", "left", "right", "jump", "sneak", "sprint", "attack", "use")) {
                    if (input.has(name)) {
                        boolean down = input.get(name).getAsBoolean();
                        HELD_KEYS.put(name, down);
                        key(client, name).setDown(down);
                    }
                }
                int lease = input.has("leaseTicks") ? input.get("leaseTicks").getAsInt() : 100;
                if (lease < 1 || lease > 200) {
                    release(client);
                    throw new IllegalArgumentException("leaseTicks must be between 1 and 200");
                }
                keyDeadline = ticks + lease;
            }
            case "release" -> release(client);
            case "click" -> {
                requirePlayer(client);
                KeyMapping mapping = key(client, input.get("key").getAsString());
                KeyMapping.click(((KeyMappingAccessor) mapping).currentKey());
            }
            case "chat" -> {
                requirePlayer(client);
                String command = input.get("command").getAsString();
                client.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
            }
            case "look" -> {
                requirePlayer(client);
                float yaw = input.get("yaw").getAsFloat();
                float pitch = input.get("pitch").getAsFloat();
                if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || Math.abs(pitch) > 90) {
                    throw new IllegalArgumentException("Finite yaw and pitch between -90 and 90 required");
                }
                client.player.setYRot(yaw);
                client.player.setXRot(pitch);
            }
            case "dismiss" -> client.gui.setScreen(null);
            case "connect" -> {
                String address = input.get("address").getAsString();
                if (!address.matches("127\\.0\\.0\\.1:[0-9]{1,5}")) {
                    throw new IllegalArgumentException("Only explicit IPv4 loopback servers are accepted");
                }
                ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address),
                        new ServerData("Adapt Client QA", address, ServerData.Type.OTHER), false, null);
            }
            case "screenshot" -> {
                String name = input.get("name").getAsString();
                if (!name.matches("[A-Za-z0-9_-]{1,80}")) {
                    throw new IllegalArgumentException("Screenshot name must contain 1-80 letters, digits, dashes or underscores");
                }
                Screenshot.grab(output.toFile(), name + ".png", client.gameRenderer.mainRenderTarget(), 1, message -> {
                    synchronized (ClientBridge.class) {
                        JsonObject event = event("screenshot");
                        event.addProperty("path", output.resolve("screenshots").resolve(name + ".png").toString());
                        event.addProperty("message", message.getString());
                        append(event);
                    }
                });
            }
            case "clear-events" -> clearEvents();
            case "disconnect" -> {
                release(client);
                client.disconnectWithSavingScreen();
            }
            case "quit" -> {
                release(client);
                client.stop();
            }
            default -> throw new IllegalArgumentException("Unknown operation " + operation);
        }
        return snapshot(client);
    }

    private static void requirePlayer(Minecraft client) {
        if (client.player == null || client.getConnection() == null) {
            throw new IllegalStateException("Client is not connected to a world");
        }
    }

    private static KeyMapping key(Minecraft client, String name) {
        return switch (name) {
            case "forward" -> client.options.keyUp;
            case "back" -> client.options.keyDown;
            case "left" -> client.options.keyLeft;
            case "right" -> client.options.keyRight;
            case "jump" -> client.options.keyJump;
            case "sneak" -> client.options.keyShift;
            case "sprint" -> client.options.keySprint;
            case "attack" -> client.options.keyAttack;
            case "use" -> client.options.keyUse;
            default -> throw new IllegalArgumentException("Unknown input key " + name);
        };
    }

    private static void release(Minecraft client) {
        for (String name : HELD_KEYS.keySet()) {
            key(client, name).setDown(false);
        }
        HELD_KEYS.clear();
    }

    private static synchronized void sampleSounds() {
        SOUND_PROBES.removeIf(probe -> ticks > probe.deadline());
        for (SoundProbe probe : SOUND_PROBES) {
            probe.handle().execute(channel -> {
                if (channel.playing()) {
                    synchronized (ClientBridge.class) {
                        probe.event().addProperty("channelPlaying", true);
                    }
                }
            });
        }
    }

    private static synchronized JsonObject snapshot(Minecraft client) {
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        result.addProperty("ticks", ticks);
        result.addProperty("processId", ProcessHandle.current().pid());
        result.addProperty("frames", frames);
        result.addProperty("particleRenderLayers", particleLayers);
        result.addProperty("droppedEvents", droppedEvents);
        result.addProperty("paused", client.isPaused());
        result.addProperty("windowActive", client.isWindowActive());
        Screen screen = client.gui.screen();
        result.addProperty("screen", screen == null ? null : screen.getClass().getName());
        result.addProperty("screenTitle", screen == null ? null : screen.getTitle().getString());
        JsonArray widgets = new JsonArray();
        if (screen != null) {
            for (GuiEventListener child : screen.children()) {
                if (child instanceof AbstractWidget widget) {
                    widgets.add(widget.getMessage().getString());
                }
            }
        }
        result.add("widgets", widgets);
        LocalPlayer player = client.player;
        result.addProperty("connected", player != null && client.getConnection() != null);
        if (player != null) {
            result.addProperty("player", player.getName().getString());
            result.add("position", vector(player.position()));
            result.add("velocity", vector(player.getDeltaMovement()));
            result.addProperty("health", player.getHealth());
            result.addProperty("onGround", player.onGround());
            result.addProperty("sprinting", player.isSprinting());
            result.addProperty("sneaking", player.isShiftKeyDown());
            result.addProperty("yaw", player.getYRot());
            result.addProperty("pitch", player.getXRot());
            JsonObject attributes = new JsonObject();
            for (AttributeInstance attribute : player.getAttributes().getSyncableAttributes()) {
                attributes.addProperty(BuiltInRegistries.ATTRIBUTE.getKey(attribute.getAttribute().value()).toString(), attribute.getValue());
            }
            result.add("attributes", attributes);
        }
        JsonArray events = new JsonArray();
        for (JsonObject event : EVENTS) {
            events.add(event.deepCopy());
        }
        result.add("events", events);
        return result;
    }

    private static synchronized void clearEvents() {
        EVENTS.clear();
        SOUND_PROBES.clear();
        PARTICLE_EVENTS.clear();
        PARTICLE_GEOMETRY.clear();
        cleanParticleReferences();
        droppedEvents = 0;
    }

    private static void cleanParticleReferences() {
        Reference<? extends Particle> reference;
        while ((reference = PARTICLE_REFERENCES.poll()) != null) {
            PARTICLE_EVENTS.remove(reference);
        }
    }

    private static JsonObject vector(Vec3 position) {
        JsonObject vector = new JsonObject();
        vector.addProperty("x", position.x);
        vector.addProperty("y", position.y);
        vector.addProperty("z", position.z);
        return vector;
    }

    private static JsonObject event(String type) {
        JsonObject event = new JsonObject();
        event.addProperty("type", type);
        event.addProperty("tick", ticks);
        event.addProperty("frame", frames);
        return event;
    }

    private static void append(JsonObject event) {
        if (EVENTS.size() >= EVENT_LIMIT) {
            EVENTS.removeFirst();
            droppedEvents++;
        }
        EVENTS.addLast(event);
    }

    private static JsonObject error(String message) {
        JsonObject response = new JsonObject();
        response.addProperty("ok", false);
        response.addProperty("error", message);
        return response;
    }

    private record Request(JsonObject input, CompletableFuture<JsonObject> result) {
    }

    private record SoundProbe(ChannelAccess.ChannelHandle handle, JsonObject event, long deadline) {
    }

    private static final class ParticleIdentity extends WeakReference<Particle> {
        private final int identityHash;

        private ParticleIdentity(Particle particle, ReferenceQueue<Particle> queue) {
            super(particle, queue);
            identityHash = System.identityHashCode(particle);
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            Particle particle = get();
            return particle != null && other instanceof ParticleIdentity identity && particle == identity.get();
        }
    }
}
