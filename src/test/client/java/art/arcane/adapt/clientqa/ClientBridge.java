package art.arcane.adapt.clientqa;

import art.arcane.adapt.clientqa.mixin.AnvilScreenAccessor;
import art.arcane.adapt.clientqa.mixin.ContainerScreenAccessor;
import art.arcane.adapt.clientqa.mixin.StonecutterScreenAccessor;
import art.arcane.adapt.clientqa.mixin.DefaultPlayerSkinAccessor;
import art.arcane.adapt.clientqa.mixin.MinecraftAccessor;
import art.arcane.adapt.clientqa.mixin.KeyMappingAccessor;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.combo_options.AudioCodec;
import com.moulberry.flashback.combo_options.ExportProjection;
import com.moulberry.flashback.combo_options.VideoCodec;
import com.moulberry.flashback.combo_options.VideoContainer;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.exporting.ExportSettings;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private static Screen cursorScreen;
    private static double cursorStartX;
    private static double cursorStartY;
    private static double cursorTargetX;
    private static double cursorTargetY;
    private static long cursorStarted;
    private static long cursorDuration = 50_000_000L;
    private static LocalPlayer turningPlayer;
    private static float turnStartYaw;
    private static float turnStartPitch;
    private static float turnTargetYaw;
    private static float turnTargetPitch;
    private static long turnStarted;
    private static long turnDuration;
    private static final int EVENT_LIMIT = 4096;
    private static final List<String> INPUT_KEYS = List.of("forward", "back", "left", "right", "jump", "sneak", "sprint", "attack", "use",
            "swapHands", "drop", "inventory");
    private static final Set<String> CLICK_DRIVEN_KEYS = Set.of("swapHands", "drop", "inventory");
    private static final Set<Identifier> DEFAULT_SKIN_TEXTURES = defaultSkinTextures();
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
    private static volatile long recordStartTick = -1;
    private static volatile long recordStopTick = -1;
    private static volatile boolean exportStarted;
    private static volatile String lastExportOutput;
    private static LiveCapture capture;
    private static long captureFrames;
    private static double captureSeconds;
    private static String captureSource;

    private ClientBridge() {
    }

    public static void tick(Minecraft client) {
        updateLook(client);
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

    public static void captureFrame(Minecraft client) {
        if (capture != null) {
            capture.frame(client);
        }
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
        if (capture != null) {
            capture.abort();
            capture = null;
        }
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
        if (exchange.getRequestMethod().equals("GET") && exchange.getRequestURI().getPath().equals("/export-status")) {
            respond(exchange, 200, exportStatus());
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

    private static JsonObject exportStatus() {
        ReplayServer replayServer = Flashback.getReplayServer();
        JsonObject status = new JsonObject();
        status.addProperty("ok", true);
        status.addProperty("exportStarted", exportStarted);
        status.addProperty("exporting", Flashback.EXPORT_JOB != null);
        status.addProperty("replayOpen", replayServer != null);
        status.addProperty("replayReady", replayServer != null && replayServer.isReady());
        status.addProperty("lastExportOutput", lastExportOutput);
        return status;
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
            case "keys", "click", "look", "cursor", "window", "slot", "anvil-name" -> client.getFramerateLimitTracker().onInputReceived();
            default -> { }
        }
        switch (operation) {
            case "state" -> { }
            case "cursor" -> cursor(client, input);
            case "container" -> {
                JsonObject result = new JsonObject();
                result.addProperty("ok", true);
                result.addProperty("ticks", ticks);
                result.addProperty("containerId", client.player != null && client.gui.screen() instanceof AbstractContainerScreen<?> ? client.player.containerMenu.containerId : null);
                return result;
            }
            case "keys" -> {
                int lease = input.has("leaseTicks") ? input.get("leaseTicks").getAsInt() : 100;
                if (lease < 1 || lease > 200) {
                    release(client);
                    throw new IllegalArgumentException("leaseTicks must be between 1 and 200");
                }
                for (String name : INPUT_KEYS) {
                    if (input.has(name)) {
                        setKey(client, name, input.get(name).getAsBoolean());
                    }
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
            case "look" -> look(client, input);
            case "slot" -> selectSlot(client, requiredInt(input, "index"));
            case "window" -> window(client, input);
            case "anvil-name" -> anvilName(client, input.get("text").getAsString());
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
            case "record" -> record(client, input.get("action").getAsString());
            case "capture" -> capture(client, input);
            case "fit-window" -> LiveCapture.fitWindow(client, requiredInt(input, "width"), requiredInt(input, "height"));
            case "replay-open" -> openReplay(input.get("path").getAsString());
            case "export" -> export(client, input);
            case "disconnect" -> disconnect(client);
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

    private static void look(Minecraft client, JsonObject input) {
        requirePlayer(client);
        float yaw = input.get("yaw").getAsFloat();
        float pitch = input.get("pitch").getAsFloat();
        int duration = optionalInt(input, "ticks", 0);
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || Math.abs(pitch) > 90) {
            throw new IllegalArgumentException("Finite yaw and pitch between -90 and 90 required");
        }
        if (duration < 0 || duration > 200) {
            throw new IllegalArgumentException("Look ticks must be between 0 and 200");
        }
        updateLook(client);
        turningPlayer = null;
        if (duration == 0) {
            client.player.setYRot(yaw);
            client.player.setXRot(pitch);
            return;
        }
        turningPlayer = client.player;
        turnStartYaw = turningPlayer.getYRot();
        turnStartPitch = turningPlayer.getXRot();
        turnTargetYaw = turnStartYaw + Mth.wrapDegrees(yaw - turnStartYaw);
        turnTargetPitch = pitch;
        turnStarted = System.nanoTime();
        turnDuration = duration * 50_000_000L;
    }

    public static void updateLook(Minecraft client) {
        if (turningPlayer == null) {
            return;
        }
        if (client.player != turningPlayer || client.getConnection() == null) {
            turningPlayer = null;
            return;
        }
        double progress = Math.clamp((System.nanoTime() - turnStarted) / (double) turnDuration, 0.0, 1.0);
        double eased = progress * progress * (3.0 - 2.0 * progress);
        float yaw = (float) (turnStartYaw + (turnTargetYaw - turnStartYaw) * eased);
        float pitch = (float) (turnStartPitch + (turnTargetPitch - turnStartPitch) * eased);
        turningPlayer.setYRot(yaw);
        turningPlayer.setXRot(pitch);
        turningPlayer.yRotO = yaw;
        turningPlayer.xRotO = pitch;
        if (progress >= 1.0) {
            turningPlayer = null;
        }
    }

    private static void record(Minecraft client, String action) {
        requirePlayer(client);
        switch (action) {
            case "start" -> {
                if (Flashback.isInReplay()) {
                    throw new IllegalStateException("Cannot record inside a replay");
                }
                if (Flashback.RECORDER != null) {
                    throw new IllegalStateException("Flashback is already recording");
                }
                Flashback.startRecordingReplay();
                if (Flashback.RECORDER == null) {
                    throw new IllegalStateException("Flashback did not start recording");
                }
                recordStartTick = ticks;
                recordStopTick = -1;
            }
            case "stop" -> {
                if (Flashback.RECORDER == null) {
                    throw new IllegalStateException("Flashback is not recording");
                }
                recordStopTick = ticks;
                Flashback.finishRecordingReplay();
            }
            default -> throw new IllegalArgumentException("record action must be start or stop");
        }
    }

    private static void capture(Minecraft client, JsonObject input) {
        switch (input.get("action").getAsString()) {
            case "start" -> {
                if (capture != null) {
                    throw new IllegalStateException("A live capture is already running");
                }
                if (Flashback.EXPORT_JOB != null) {
                    throw new IllegalStateException("Cannot capture while an export is queued or running");
                }
                capture = LiveCapture.start(client, new LiveCapture.Settings(Path.of(input.get("path").getAsString()).toAbsolutePath().normalize(),
                        Path.of(input.get("ffmpeg").getAsString()), requiredInt(input, "width"), requiredInt(input, "height"), requiredInt(input, "fps")));
                captureFrames = 0;
                captureSeconds = 0;
                captureSource = capture.source();
            }
            case "stop" -> {
                if (capture == null) {
                    throw new IllegalStateException("No live capture is running");
                }
                LiveCapture finished = capture;
                capture = null;
                try {
                    finished.stop(client);
                } finally {
                    captureFrames = finished.frames();
                    captureSeconds = finished.seconds();
                }
            }
            default -> throw new IllegalArgumentException("capture action must be start or stop");
        }
    }

    private static void openReplay(String path) {
        if (capture != null) {
            throw new IllegalStateException("Cannot open a replay or export while a live capture is running");
        }
        if (Flashback.EXPORT_JOB != null) {
            throw new IllegalStateException("Cannot open a replay while an export is queued or running");
        }
        if (Flashback.RECORDER != null) {
            throw new IllegalStateException("Cannot open a replay while recording");
        }
        Path replay = Path.of(path);
        if (!Files.isRegularFile(replay)) {
            throw new IllegalArgumentException("Replay not found: " + replay);
        }
        exportStarted = false;
        lastExportOutput = null;
        Flashback.openReplayWorld(replay);
        if (!Flashback.isInReplay()) {
            throw new IllegalStateException("Flashback could not open replay " + replay);
        }
    }

    private static void export(Minecraft client, JsonObject input) {
        if (capture != null) {
            throw new IllegalStateException("Cannot open a replay or export while a live capture is running");
        }
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer == null || !replayServer.isReady()) {
            throw new IllegalStateException("Replay world is not ready");
        }
        if (Flashback.EXPORT_JOB != null) {
            throw new IllegalStateException("An export is already queued or running");
        }
        ExportSettings settings = exportSettings(client, replayServer, input);
        lastExportOutput = settings.output().toString();
        Flashback.EXPORT_JOB = new ExportJob(settings);
        exportStarted = true;
    }

    private static void disconnect(Minecraft client) {
        if (Flashback.EXPORT_JOB != null) {
            throw new IllegalStateException("Cannot disconnect while an export is queued or running");
        }
        turningPlayer = null;
        release(client);
        if (Flashback.getReplayServer() == null) {
            client.disconnectWithSavingScreen();
            return;
        }
        if (client.level != null) {
            client.level.disconnect(Component.empty());
        }
        client.disconnectWithProgressScreen();
        client.gui.setScreen(new TitleScreen());
    }

    private static void selectSlot(Minecraft client, int index) {
        requirePlayer(client);
        if (index < 0 || index >= Inventory.SELECTION_SIZE) {
            throw new IllegalArgumentException("Hotbar index must be between 0 and " + (Inventory.SELECTION_SIZE - 1));
        }
        client.player.getInventory().setSelectedSlot(index);
    }

    private static void window(Minecraft client, JsonObject input) {
        requirePlayer(client);
        if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            throw new IllegalStateException("No container screen is open");
        }
        checkContainer(client, input);
        String action = input.get("action").getAsString();
        switch (action) {
            case "list" -> { }
            case "click" -> containerInput(client, input, ContainerInput.PICKUP);
            case "shift" -> containerInput(client, input, ContainerInput.QUICK_MOVE);
            case "drop" -> containerInput(client, input, ContainerInput.THROW);
            case "button" -> containerButton(client, screen, requiredInt(input, "index"));
            case "close" -> screen.onClose();
            default -> throw new IllegalArgumentException("window action must be list, click, shift, drop, button or close");
        }
    }

    public static boolean hasActiveAttackLease() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && client.getConnection() != null && ticks < keyDeadline
                && HELD_KEYS.getOrDefault("attack", false);
    }

    public static boolean hasCursor() {
        Screen screen = Minecraft.getInstance().gui.screen();
        return cursorScreen != null && screen == cursorScreen && screen instanceof AbstractContainerScreen<?>;
    }

    public static double cursorX() {
        return cursorStartX + (cursorTargetX - cursorStartX) * cursorProgress();
    }

    public static double cursorY() {
        return cursorStartY + (cursorTargetY - cursorStartY) * cursorProgress();
    }

    private static double cursorProgress() {
        double progress = Math.clamp((System.nanoTime() - cursorStarted) / (double) cursorDuration, 0.0, 1.0);
        return progress * progress * (3.0 - 2.0 * progress);
    }

    private static void cursor(Minecraft client, JsonObject input) {
        requirePlayer(client);
        Screen screen = client.gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?>)) {
            throw new IllegalStateException("No container screen is open");
        }
        checkContainer(client, input);
        double x = input.get("x").getAsDouble();
        double y = input.get("y").getAsDouble();
        int duration = optionalInt(input, "ticks", 1);
        if (duration < 1 || duration > 200) {
            throw new IllegalArgumentException("Cursor ticks must be between 1 and 200");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || y < 0 || x >= screen.width || y >= screen.height) {
            throw new IllegalArgumentException("Cursor coordinates must be inside the GUI");
        }
        cursorStartX = client.mouseHandler.getScaledXPos(client.getWindow());
        cursorStartY = client.mouseHandler.getScaledYPos(client.getWindow());
        cursorTargetX = x;
        cursorTargetY = y;
        cursorStarted = System.nanoTime();
        cursorDuration = duration * 50_000_000L;
        cursorScreen = screen;
        screen.mouseMoved(x, y);
        screen.afterMouseMove();
    }

    private static void checkContainer(Minecraft client, JsonObject input) {
        if (input.has("containerId") && requiredInt(input, "containerId") != client.player.containerMenu.containerId) {
            throw new IllegalStateException("Container changed before input");
        }
    }

    private static void containerInput(Minecraft client, JsonObject input, ContainerInput type) {
        AbstractContainerMenu menu = client.player.containerMenu;
        int slot = requiredInt(input, "slot");
        if (slot < 0 || slot >= menu.slots.size()) {
            throw new IllegalArgumentException("Slot must be between 0 and " + (menu.slots.size() - 1));
        }
        int button = optionalInt(input, "button", 0);
        if (button != 0 && button != 1) {
            throw new IllegalArgumentException("button must be 0 or 1");
        }
        client.gameMode.handleContainerInput(menu.containerId, slot, button, type, client.player);
    }

    private static void containerButton(Minecraft client, AbstractContainerScreen<?> screen, int index) {
        ContainerControl selected = null;
        for (ContainerControl control : containerControls(client.player.containerMenu, screen)) {
            if (control.index() == index) {
                selected = control;
                break;
            }
        }
        if (selected == null) {
            throw new IllegalArgumentException("Container control is not available: " + index);
        }
        double x = client.mouseHandler.getScaledXPos(client.getWindow());
        double y = client.mouseHandler.getScaledYPos(client.getWindow());
        if (!hasCursor() || Math.abs(x - selected.screenX()) > 1 || Math.abs(y - selected.screenY()) > 1) {
            throw new IllegalStateException("Cursor must settle at the container control before clicking");
        }
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));
        boolean accepted = screen.mouseClicked(event, false);
        screen.mouseReleased(event);
        screen.afterMouseAction();
        if (!accepted) {
            throw new IllegalStateException("Container control rejected the click: " + index);
        }
    }

    private static List<ContainerControl> containerControls(AbstractContainerMenu menu, AbstractContainerScreen<?> screen) {
        List<ContainerControl> controls = new ArrayList<>();
        ContainerScreenAccessor position = (ContainerScreenAccessor) screen;
        if (screen instanceof EnchantmentScreen && menu instanceof EnchantmentMenu enchantment) {
            LocalPlayer player = Minecraft.getInstance().player;
            for (int index = 0; index < enchantment.costs.length; index++) {
                if (enchantment.costs[index] <= 0 || enchantment.getSlot(0).getItem().isEmpty()) {
                    continue;
                }
                if (!player.hasInfiniteMaterials() && (enchantment.getSlot(1).getItem().getCount() < index + 1
                        || player.experienceLevel < index + 1 || player.experienceLevel < enchantment.costs[index])) {
                    continue;
                }
                controls.add(new ContainerControl(index, position.adaptLeftPos() + 114, position.adaptTopPos() + 23 + 19 * index));
            }
        } else if (screen instanceof StonecutterScreen && menu instanceof StonecutterMenu stonecutter) {
            StonecutterScreenAccessor recipes = (StonecutterScreenAccessor) screen;
            if (recipes.adaptDisplayRecipes()) {
                int start = recipes.adaptStartIndex();
                int end = Math.min(start + 12, stonecutter.getNumberOfVisibleRecipes());
                for (int index = start; index < end; index++) {
                    int offset = index - start;
                    controls.add(new ContainerControl(index, position.adaptLeftPos() + 60 + offset % 4 * 16,
                            position.adaptTopPos() + 23 + offset / 4 * 18));
                }
            }
        }
        return controls;
    }

    private static void anvilName(Minecraft client, String text) {
        requirePlayer(client);
        if (!(client.gui.screen() instanceof AnvilScreen screen)) {
            throw new IllegalStateException("No anvil screen is open");
        }
        if (text.isEmpty() || text.length() > AnvilMenu.MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Anvil name must have 1 to " + AnvilMenu.MAX_NAME_LENGTH + " characters");
        }
        if (!screen.getMenu().getSlot(AnvilMenu.INPUT_SLOT).hasItem()) {
            throw new IllegalStateException("The anvil input slot is empty");
        }
        ((AnvilScreenAccessor) screen).nameField().setValue(text);
    }

    private static ExportSettings exportSettings(Minecraft client, ReplayServer replayServer, JsonObject input) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState == null) {
            throw new IllegalStateException("Replay editor state is unavailable");
        }
        LocalPlayer viewer = client.player;
        if (viewer == null) {
            throw new IllegalStateException("Replay viewer is not spawned");
        }
        int totalTicks = replayServer.getTotalReplayTicks();
        int startTick = optionalInt(input, "startTick", 0);
        int endTick = optionalInt(input, "endTick", totalTicks);
        if (startTick < 0 || startTick >= endTick || endTick > totalTicks) {
            throw new IllegalArgumentException("Export ticks must satisfy 0 <= startTick < endTick <= " + totalTicks);
        }
        Path output = Path.of(input.get("output").getAsString()).toAbsolutePath();
        Path outputDirectory = output.getParent();
        if (outputDirectory == null || !Files.isDirectory(outputDirectory)) {
            throw new IllegalArgumentException("Export directory does not exist: " + outputDirectory);
        }
        JsonObject camera = input.has("camera") && !input.get("camera").isJsonNull() ? input.getAsJsonObject("camera") : null;
        Vec3 cameraPosition = camera == null ? viewer.position() : new Vec3(camera.get("x").getAsDouble(), camera.get("y").getAsDouble(), camera.get("z").getAsDouble());
        float cameraYaw = camera == null ? viewer.getYRot() : camera.get("yaw").getAsFloat();
        float cameraPitch = camera == null ? viewer.getXRot() : camera.get("pitch").getAsFloat();
        VideoContainer container = VideoContainer.valueOf(input.get("container").getAsString());
        VideoCodec codec = VideoCodec.valueOf(input.get("codec").getAsString());
        if (!Arrays.asList(container.getSupportedVideoCodecs(false)).contains(codec)) {
            throw new IllegalArgumentException(container + " does not support " + codec);
        }
        String[] encoders = codec.getEncoders();
        if (encoders.length == 0) {
            throw new IllegalStateException("No encoder available for " + codec);
        }
        AudioCodec audioCodec = input.get("audio").getAsBoolean() ? AudioCodec.AAC : null;
        if (audioCodec != null && !Arrays.asList(container.getSupportedAudioCodecs()).contains(audioCodec)) {
            throw new IllegalArgumentException(container + " does not support " + audioCodec + " audio");
        }
        return new ExportSettings(
                input.get("name").getAsString(),
                editorState.copy(),
                cameraPosition,
                cameraYaw,
                cameraPitch,
                input.get("width").getAsInt(),
                input.get("height").getAsInt(),
                startTick,
                endTick,
                ExportProjection.PERSPECTIVE,
                0f,
                Math.max(1.0, input.get("fps").getAsDouble()),
                true,
                false,
                container,
                codec,
                encoders[0],
                input.get("bitrate").getAsInt(),
                false,
                false,
                input.get("noGui").getAsBoolean(),
                false,
                audioCodec,
                output,
                null);
    }

    private static int requiredInt(JsonObject input, String name) {
        if (!input.has(name) || input.get(name).isJsonNull()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return input.get(name).getAsInt();
    }

    private static int optionalInt(JsonObject input, String name, int fallback) {
        return input.has(name) && !input.get(name).isJsonNull() ? input.get(name).getAsInt() : fallback;
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
            case "swapHands" -> client.options.keySwapOffhand;
            case "drop" -> client.options.keyDrop;
            case "inventory" -> client.options.keyInventory;
            default -> throw new IllegalArgumentException("Unknown input key " + name);
        };
    }

    private static void setKey(Minecraft client, String name, boolean down) {
        KeyMapping mapping = key(client, name);
        if (down && CLICK_DRIVEN_KEYS.contains(name) && !HELD_KEYS.getOrDefault(name, false)) {
            KeyMapping.click(((KeyMappingAccessor) mapping).currentKey());
        }
        HELD_KEYS.put(name, down);
        mapping.setDown(down);
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
        result.addProperty("hiddenRenderer", Boolean.parseBoolean(System.getProperty("adapt.qa.hidden", "true")));
        result.addProperty("windowVisible", GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_TRUE);
        result.addProperty("windowFocused", GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE);
        result.addProperty("cursorMode", GLFW.glfwGetInputMode(client.getWindow().handle(), GLFW.GLFW_CURSOR));
        result.addProperty("renderWidth", client.gameRenderer.mainRenderTarget().width);
        result.addProperty("renderHeight", client.gameRenderer.mainRenderTarget().height);
        result.addProperty("mouseGrabbed", client.mouseHandler.isMouseGrabbed());
        result.addProperty("bridgeAttackActive", hasActiveAttackLease());
        result.addProperty("turning", turningPlayer != null);
        result.addProperty("cursorMoving", hasCursor() && System.nanoTime() - cursorStarted < cursorDuration);
        Screen screen = client.gui.screen();
        JsonObject cursor = new JsonObject();
        cursor.addProperty("x", client.mouseHandler.getScaledXPos(client.getWindow()));
        cursor.addProperty("y", client.mouseHandler.getScaledYPos(client.getWindow()));
        result.add("cursor", cursor);
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
        result.addProperty("skinLoaded", accountSkinLoaded(client));
        result.addProperty("recording", Flashback.RECORDER != null);
        result.addProperty("recordStartTick", recordStartTick);
        result.addProperty("recordStopTick", recordStopTick);
        result.addProperty("capturing", capture != null);
        result.addProperty("captureFrames", capture != null ? capture.frames() : captureFrames);
        result.addProperty("captureSeconds", capture != null ? capture.seconds() : captureSeconds);
        result.addProperty("captureSource", captureSource);
        ReplayServer replayServer = Flashback.getReplayServer();
        result.addProperty("replayOpen", replayServer != null);
        result.addProperty("replayReady", replayServer != null && replayServer.isReady());
        if (player != null) {
            result.addProperty("player", player.getName().getString());
            result.addProperty("uuid", player.getUUID().toString());
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
            Inventory inventory = player.getInventory();
            result.addProperty("selectedSlot", inventory.getSelectedSlot());
            JsonArray items = new JsonArray();
            for (int index = 0; index < inventory.getContainerSize(); index++) {
                ItemStack stack = inventory.getItem(index);
                if (!stack.isEmpty()) {
                    items.add(slotEntry(index, stack));
                }
            }
            result.add("inventory", items);
            result.add("container", screen instanceof AbstractContainerScreen<?> containerScreen ? container(player.containerMenu, containerScreen) : null);
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

    private static boolean accountSkinLoaded(Minecraft client) {
        ProfileResult account = finished(((MinecraftAccessor) client).profileFuture(), null);
        if (account == null) {
            return false;
        }
        PlayerSkin skin = finished(client.getSkinManager().get(account.profile()), Optional.<PlayerSkin>empty()).orElse(null);
        return skin != null && !DEFAULT_SKIN_TEXTURES.contains(skin.body().texturePath());
    }

    private static <T> T finished(CompletableFuture<T> future, T pending) {
        return future.isDone() && !future.isCompletedExceptionally() ? future.join() : pending;
    }

    private static Set<Identifier> defaultSkinTextures() {
        PlayerSkin[] skins = DefaultPlayerSkinAccessor.defaultSkins();
        Set<Identifier> textures = new HashSet<>(skins.length * 2);
        for (PlayerSkin skin : skins) {
            textures.add(skin.body().texturePath());
        }
        return Set.copyOf(textures);
    }

    private static JsonObject vector(Vec3 position) {
        JsonObject vector = new JsonObject();
        vector.addProperty("x", position.x);
        vector.addProperty("y", position.y);
        vector.addProperty("z", position.z);
        return vector;
    }

    private static JsonObject container(AbstractContainerMenu menu, AbstractContainerScreen<?> screen) {
        JsonObject container = new JsonObject();
        container.addProperty("id", menu.containerId);
        JsonArray slots = new JsonArray();
        ContainerScreenAccessor position = (ContainerScreenAccessor) screen;
        for (Slot slot : menu.slots) {
            JsonObject entry = slotEntry(slot.index, slot.getItem());
            entry.addProperty("screenX", position.adaptLeftPos() + slot.x + 8);
            entry.addProperty("screenY", position.adaptTopPos() + slot.y + 8);
            slots.add(entry);
        }
        container.add("slots", slots);
        JsonArray controls = new JsonArray();
        for (ContainerControl control : containerControls(menu, screen)) {
            JsonObject entry = new JsonObject();
            entry.addProperty("index", control.index());
            entry.addProperty("screenX", control.screenX());
            entry.addProperty("screenY", control.screenY());
            controls.add(entry);
        }
        container.add("controls", controls);
        container.add("carried", itemEntry(menu.getCarried()));
        return container;
    }

    private static JsonObject slotEntry(int index, ItemStack stack) {
        JsonObject entry = new JsonObject();
        entry.addProperty("index", index);
        entry.addProperty("item", itemId(stack));
        entry.addProperty("count", stack.getCount());
        return entry;
    }

    private static JsonObject itemEntry(ItemStack stack) {
        JsonObject entry = new JsonObject();
        entry.addProperty("item", itemId(stack));
        entry.addProperty("count", stack.getCount());
        return entry;
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
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

    private record ContainerControl(int index, int screenX, int screenY) {
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
