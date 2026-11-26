package art.arcane.adapt.clientui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class UiBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("AdaptUiBridge");
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final ArrayBlockingQueue<Request> REQUESTS = new ArrayBlockingQueue<>(64);
    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static HttpServer server;
    private static String token;
    private static long ticks;
    private static boolean muted;
    private static double cursorX;
    private static double cursorY;

    private UiBridge() {
    }

    public static void tick(Minecraft client) {
        if (server == null && token == null) {
            start();
        }
        ticks++;
        Gameplay.tick(client);
        if (!muted) {
            mute(client);
        }
        Step step = STEPS.peekFirst();
        if (step != null) {
            runStep(client, step);
        }
        for (int processed = 0; processed < 16; processed++) {
            Request request = REQUESTS.poll();
            if (request == null) {
                break;
            }
            if (request.result().isDone()) {
                continue;
            }
            try {
                execute(client, request);
            } catch (RuntimeException failure) {
                LOGGER.error("Adapt UI bridge command failed", failure);
                request.result().completeExceptionally(failure);
            }
        }
    }

    static long ticks() {
        return ticks;
    }

    public static void close() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private static void start() {
        token = System.getProperty("adapt.ui.token", "");
        int port = Integer.getInteger("adapt.ui.port", 0);
        if (token.length() < 16 || port <= 0) {
            LOGGER.warn("Adapt UI bridge disabled: adapt.ui.port or adapt.ui.token missing");
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), port), 8);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot bind Adapt UI bridge on port " + port, failure);
        }
        server.createContext("/state", exchange -> handle(exchange, false));
        server.createContext("/command", exchange -> handle(exchange, true));
        server.setExecutor(Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "Adapt UI bridge");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        LOGGER.info("Adapt UI bridge listening on 127.0.0.1:{}", port);
    }

    private static void mute(Minecraft client) {
        for (SoundSource source : SoundSource.values()) {
            if (client.options.getSoundSourceOptionInstance(source).get() != 0.0D) {
                client.options.getSoundSourceOptionInstance(source).set(0.0D);
            }
        }
        muted = true;
    }

    private static void handle(HttpExchange exchange, boolean command) throws IOException {
        int status = 200;
        JsonObject response;
        try {
            String supplied = exchange.getRequestHeaders().getFirst("X-Adapt-UI-Token");
            if (supplied == null || !MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
                status = 403;
                response = error("Invalid token");
            } else if (command != "POST".equals(exchange.getRequestMethod())) {
                status = 405;
                response = error("Use GET /state or POST /command");
            } else {
                JsonObject input = command
                        ? JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject()
                        : new JsonObject();
                if (!command) {
                    input.addProperty("op", "state");
                }
                response = dispatch(input);
            }
        } catch (TimeoutException failure) {
            status = 504;
            response = error("Timed out waiting for the client thread");
        } catch (RuntimeException | ExecutionException | InterruptedException failure) {
            status = 500;
            Throwable cause = failure instanceof ExecutionException && failure.getCause() != null ? failure.getCause() : failure;
            response = error(cause.getClass().getSimpleName() + ": " + cause.getMessage());
        }
        byte[] body = GSON.toJson(response).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream stream = exchange.getResponseBody()) {
            stream.write(body);
        }
    }

    private static JsonObject dispatch(JsonObject input) throws ExecutionException, InterruptedException, TimeoutException {
        String op = Json.string(input, "op", "state");
        if (op.equals("wait-screen")) {
            return waitScreen(input);
        }
        long timeout = Json.integer(input, "timeoutMs", 20_000);
        return submit(input).get(timeout, TimeUnit.MILLISECONDS);
    }

    private static CompletableFuture<JsonObject> submit(JsonObject input) {
        Request request = new Request(input, new CompletableFuture<>());
        if (!REQUESTS.offer(request)) {
            throw new IllegalStateException("Request queue full");
        }
        return request.result();
    }

    private static JsonObject waitScreen(JsonObject input) throws ExecutionException, InterruptedException, TimeoutException {
        String expectedClass = Json.string(input, "class", null);
        String expectedTitle = Json.string(input, "title", null);
        boolean none = input.has("none") && input.get("none").getAsBoolean();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Json.integer(input, "timeoutMs", 20_000));
        JsonObject state = new JsonObject();
        while (System.nanoTime() < deadline) {
            JsonObject probe = new JsonObject();
            probe.addProperty("op", "state");
            state = submit(probe).get(5, TimeUnit.SECONDS);
            JsonElement screen = state.get("screen");
            if (ScreenInfo.matches(screen, expectedClass, expectedTitle, none)) {
                return state;
            }
            Thread.sleep(50L);
        }
        JsonObject failure = error("Screen did not match within the timeout");
        failure.add("state", state);
        return failure;
    }

    private static void execute(Minecraft client, Request request) {
        JsonObject input = request.input();
        String op = Json.string(input, "op", "state");
        switch (op) {
            case "state" -> request.result().complete(state(client));
            case "command" -> {
                LocalPlayer player = requirePlayer(client);
                String command = Json.string(input, "command", "");
                player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
                request.result().complete(ok());
            }
            case "chat" -> {
                requirePlayer(client).connection.sendChat(Json.string(input, "text", ""));
                request.result().complete(ok());
            }
            case "mouse-move" -> {
                moveCursor(client, Json.number(input, "x"), Json.number(input, "y"));
                request.result().complete(ok());
            }
            case "mouse-down", "mouse-up" -> {
                if (input.has("x")) {
                    moveCursor(client, Json.number(input, "x"), Json.number(input, "y"));
                }
                Platform.button(client, button(input), op.equals("mouse-down"), Modifiers.mask(input));
                request.result().complete(ok());
            }
            case "click" -> {
                double x = Json.number(input, "x");
                double y = Json.number(input, "y");
                int button = button(input);
                int mask = Modifiers.mask(input);
                long count = Json.integer(input, "count", 1);
                Step click = new Step(request.result());
                click.add(() -> Modifiers.hold(client, input, true));
                click.add(() -> moveCursor(client, x, y));
                for (long index = 0; index < count; index++) {
                    click.add(() -> Platform.button(client, button, true, mask));
                    click.add(() -> Platform.button(client, button, false, mask));
                }
                click.add(() -> Modifiers.hold(client, input, false));
                STEPS.addLast(click);
            }
            case "drag" -> {
                JsonArray from = input.getAsJsonArray("from");
                JsonArray to = input.getAsJsonArray("to");
                int button = button(input);
                int mask = Modifiers.mask(input);
                long steps = Math.max(1, Json.integer(input, "steps", 8));
                double startX = from.get(0).getAsDouble();
                double startY = from.get(1).getAsDouble();
                double endX = to.get(0).getAsDouble();
                double endY = to.get(1).getAsDouble();
                Step drag = new Step(request.result());
                drag.add(() -> Modifiers.hold(client, input, true));
                drag.add(() -> moveCursor(client, startX, startY));
                drag.add(() -> Platform.button(client, button, true, mask));
                for (long index = 1; index <= steps; index++) {
                    double fraction = (double) index / steps;
                    drag.add(() -> moveCursor(client, startX + (endX - startX) * fraction, startY + (endY - startY) * fraction));
                }
                drag.add(() -> Platform.button(client, button, false, mask));
                drag.add(() -> Modifiers.hold(client, input, false));
                STEPS.addLast(drag);
            }
            case "scroll" -> {
                if (input.has("x")) {
                    moveCursor(client, Json.number(input, "x"), Json.number(input, "y"));
                }
                Platform.scroll(client, input.has("dx") ? Json.number(input, "dx") : 0.0D, input.has("dy") ? Json.number(input, "dy") : 0.0D);
                request.result().complete(ok());
            }
            case "key" -> {
                int key = Keys.code(Json.string(input, "name", ""));
                int mask = Modifiers.mask(input);
                String action = Json.string(input, "action", "tap");
                Step press = new Step(request.result());
                press.add(() -> Modifiers.hold(client, input, true));
                if (!action.equals("release")) {
                    press.add(() -> {
                        Keys.hold(key, true);
                        Platform.key(client, key, true, mask);
                    });
                }
                if (!action.equals("press")) {
                    press.add(() -> {
                        Keys.hold(key, false);
                        Platform.key(client, key, false, mask);
                    });
                }
                press.add(() -> Modifiers.hold(client, input, false));
                STEPS.addLast(press);
            }
            case "hold" -> {
                boolean down = !input.has("down") || input.get("down").getAsBoolean();
                Gameplay.hold(client, Json.string(input, "action", ""), down, Json.integer(input, "ticks", 0));
                request.result().complete(state(client));
            }
            case "press-action" -> {
                requirePlayer(client);
                Gameplay.click(client, Json.string(input, "action", ""));
                request.result().complete(ok());
            }
            case "jump-sequence" -> {
                requirePlayer(client);
                JsonArray offsets = input.getAsJsonArray("presses");
                long[] presses = new long[offsets.size()];
                for (int index = 0; index < presses.length; index++) {
                    presses[index] = offsets.get(index).getAsLong();
                }
                Gameplay.jumpSequence(presses, Json.integer(input, "tap", 2));
                request.result().complete(ok());
            }
            case "trace-entities" -> {
                requirePlayer(client);
                Gameplay.traceEntities(Json.integer(input, "ticks", 60), Json.number(input, "radius"));
                request.result().complete(ok());
            }
            case "clear-events" -> {
                Recorder.clear();
                request.result().complete(ok());
            }
            case "release" -> {
                Gameplay.releaseAll(client);
                request.result().complete(state(client));
            }
            case "look" -> {
                requirePlayer(client);
                Gameplay.look(client, (float) Json.number(input, "yaw"), (float) Json.number(input, "pitch"));
                request.result().complete(state(client));
            }
            case "type" -> {
                Platform.type(client, Json.string(input, "text", ""));
                request.result().complete(ok());
            }
            case "close-screen" -> {
                Platform.setScreen(client, null);
                request.result().complete(ok());
            }
            case "gui-scale" -> {
                Platform.setGuiScale(client, (int) Json.integer(input, "scale", 0));
                request.result().complete(state(client));
            }
            case "resize" -> {
                int width = (int) Math.max(320, Math.min(7680, Json.integer(input, "width", 1280)));
                int height = (int) Math.max(240, Math.min(4320, Json.integer(input, "height", 720)));
                Platform.resize(client, width, height);
                Step settle = new Step(request.result());
                settle.add(() -> {
                });
                settle.add(() -> {
                });
                STEPS.addLast(settle);
            }
            case "screenshot" -> {
                Path path = Path.of(Json.string(input, "path", ""));
                if (!path.isAbsolute()) {
                    throw new IllegalArgumentException("screenshot needs an absolute path");
                }
                try {
                    Files.createDirectories(path.getParent());
                } catch (IOException failure) {
                    throw new IllegalStateException("Cannot create " + path.getParent(), failure);
                }
                Platform.screenshot(client, path, request.result());
            }
            case "quit" -> {
                Gameplay.releaseAll(client);
                request.result().complete(ok());
                client.stop();
            }
            default -> throw new IllegalArgumentException("Unknown op " + op);
        }
    }

    private static void runStep(Minecraft client, Step step) {
        try {
            Runnable action = step.actions().pollFirst();
            if (action != null) {
                action.run();
            }
            if (step.actions().isEmpty()) {
                STEPS.pollFirst();
                step.result().complete(state(client));
            }
        } catch (RuntimeException failure) {
            STEPS.pollFirst();
            Keys.clear();
            step.result().completeExceptionally(failure);
        }
    }

    private static void moveCursor(Minecraft client, double scaledX, double scaledY) {
        cursorX = scaledX;
        cursorY = scaledY;
        double factor = (double) client.getWindow().getScreenWidth() / Math.max(1, client.getWindow().getGuiScaledWidth());
        double factorY = (double) client.getWindow().getScreenHeight() / Math.max(1, client.getWindow().getGuiScaledHeight());
        Platform.moveMouse(client, scaledX * factor, scaledY * factorY);
    }

    private static int button(JsonObject input) {
        return switch (Json.string(input, "button", "left")) {
            case "left" -> Platform.BUTTON_LEFT;
            case "right" -> Platform.BUTTON_RIGHT;
            case "middle" -> Platform.BUTTON_MIDDLE;
            default -> throw new IllegalArgumentException("button must be left, right or middle");
        };
    }

    private static LocalPlayer requirePlayer(Minecraft client) {
        if (client.player == null || client.getConnection() == null) {
            throw new IllegalStateException("Not connected to a server");
        }
        return client.player;
    }

    private static JsonObject state(Minecraft client) {
        JsonObject result = new JsonObject();
        result.addProperty("ticks", ticks);
        result.addProperty("minecraft", BuildInfo.MINECRAFT);
        result.addProperty("loader", BuildInfo.LOADER);
        result.addProperty("pendingSteps", STEPS.size());
        Screen screen = Platform.screen(client);
        result.add("screen", ScreenInfo.describe(screen));
        result.add("uiTree", UiTree.snapshot());
        JsonObject window = Platform.windowState(client);
        window.addProperty("width", client.getWindow().getWidth());
        window.addProperty("height", client.getWindow().getHeight());
        window.addProperty("screenWidth", client.getWindow().getScreenWidth());
        window.addProperty("screenHeight", client.getWindow().getScreenHeight());
        window.addProperty("guiScale", client.getWindow().getGuiScale());
        window.addProperty("guiScaleOption", client.options.guiScale().get());
        window.addProperty("scaledWidth", client.getWindow().getGuiScaledWidth());
        window.addProperty("scaledHeight", client.getWindow().getGuiScaledHeight());
        window.addProperty("cursorX", cursorX);
        window.addProperty("cursorY", cursorY);
        result.add("window", window);
        JsonObject audio = new JsonObject();
        double loudest = 0.0D;
        for (SoundSource source : SoundSource.values()) {
            loudest = Math.max(loudest, client.options.getSoundSourceOptionInstance(source).get());
        }
        audio.addProperty("loudestCategory", loudest);
        result.add("audio", audio);
        LocalPlayer player = client.player;
        JsonObject playerState = new JsonObject();
        playerState.addProperty("connected", player != null && client.getConnection() != null);
        if (player != null) {
            playerState.addProperty("name", player.getName().getString());
            playerState.addProperty("x", player.getX());
            playerState.addProperty("y", player.getY());
            playerState.addProperty("z", player.getZ());
        }
        Gameplay.describe(client, playerState);
        result.add("player", playerState);
        result.add("events", Recorder.snapshot());
        return result;
    }

    static JsonObject ok() {
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        return result;
    }

    private static JsonObject error(String message) {
        JsonObject result = new JsonObject();
        result.addProperty("error", message);
        return result;
    }

    private record Request(JsonObject input, CompletableFuture<JsonObject> result) {
    }

    private record Step(Deque<Runnable> actions, CompletableFuture<JsonObject> result) {
        Step(CompletableFuture<JsonObject> result) {
            this(new ArrayDeque<>(), result);
        }

        void add(Runnable action) {
            actions.addLast(action);
        }
    }
}
