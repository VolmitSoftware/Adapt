package art.arcane.adapt.clientqa;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public final class LiveCapture {
    private static final Logger LOGGER = LoggerFactory.getLogger("AdaptClientQA");
    private static final String HARDWARE_ENCODER = "h264_videotoolbox";
    private static final String SOFTWARE_ENCODER = "libx264";
    private static final String BITRATE = "40M";
    private static final int MAX_FPS = 60;
    private static final int MAX_SIZE = 7680;
    private static final int GPU_BUFFERS = 3;
    private static final int QUEUED_FRAMES = 8;
    private static final long PROBE_TIMEOUT_SECONDS = 10;
    private static final long FINISH_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final long DRAIN_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
    private static final long DRAIN_POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(1);
    private static final long ABORT_TIMEOUT_SECONDS = 2;
    private static final int MENU_BAR_MARGIN = 40;
    private static final byte[] END = new byte[0];
    private static final Map<Path, String> ENCODERS = new ConcurrentHashMap<>();

    private final Settings settings;
    private final int sourceWidth;
    private final int sourceHeight;
    private final long intervalNanos;
    private final Path log;
    private final String encoder;
    private final Process process;
    private final Thread writer;
    private final ArrayDeque<GpuBuffer> idleBuffers = new ArrayDeque<>(GPU_BUFFERS);
    private final ArrayBlockingQueue<byte[]> spareFrames = new ArrayBlockingQueue<>(QUEUED_FRAMES);
    private final ArrayBlockingQueue<byte[]> pendingFrames = new ArrayBlockingQueue<>(QUEUED_FRAMES + 1);
    private final long startNanos;
    private long stopNanos = -1;
    private long slots;
    private long droppedFrames;
    private boolean closed;
    private RuntimeException renderFailure;
    private volatile long frames;
    private volatile IOException writeFailure;

    private LiveCapture(Settings settings, RenderTarget target) throws IOException {
        this.settings = settings;
        sourceWidth = target.width;
        sourceHeight = target.height;
        intervalNanos = TimeUnit.SECONDS.toNanos(1) / settings.fps();
        log = settings.output().resolveSibling(settings.output().getFileName() + ".log");
        encoder = encoder(settings.ffmpeg());
        writer = Thread.ofPlatform().daemon().name("AdaptClientQA live capture").unstarted(this::write);
        int frameBytes = Math.multiplyExact(Math.multiplyExact(sourceWidth, sourceHeight), 4);
        for (int index = 0; index < QUEUED_FRAMES; index++) {
            spareFrames.add(new byte[frameBytes]);
        }
        GpuDevice device = RenderSystem.getDevice();
        for (int index = 0; index < GPU_BUFFERS; index++) {
            idleBuffers.add(device.createBuffer(() -> "Adapt client QA live capture", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, frameBytes));
        }
        try {
            process = new ProcessBuilder(command()).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(log.toFile()).start();
        } catch (IOException failure) {
            for (GpuBuffer buffer : idleBuffers) {
                buffer.close();
            }
            throw failure;
        }
        startNanos = System.nanoTime();
    }

    public static LiveCapture start(Minecraft client, Settings settings) {
        validate(settings);
        RenderTarget target = client.gameRenderer.mainRenderTarget();
        GpuTexture texture = target.getColorTexture();
        if (texture == null) {
            throw new IllegalStateException("The main render target has no color texture");
        }
        if (texture.getFormat() != GpuFormat.RGBA8_UNORM) {
            throw new IllegalStateException("The main render target is " + texture.getFormat() + ", not RGBA8_UNORM");
        }
        LiveCapture capture;
        try {
            capture = new LiveCapture(settings, target);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot start " + settings.ffmpeg() + ": " + failure.getMessage(), failure);
        }
        capture.writer.start();
        client.gui.toastManager().clear();
        FramerateLimitTracker limiter = client.getFramerateLimitTracker();
        limiter.setFramerateLimit(Math.max(client.options.framerateLimit().get(), settings.fps() * 2));
        limiter.onInputReceived();
        LOGGER.info("Live capture of {}x{} at {} fps with {} to {}", capture.sourceWidth, capture.sourceHeight, settings.fps(), capture.encoder,
                settings.output());
        return capture;
    }

    public static void fitWindow(Minecraft client, int width, int height) {
        Window window = client.getWindow();
        if (window.isFullscreen()) {
            throw new IllegalStateException("The game window is fullscreen");
        }
        long handle = window.handle();
        if (contentSize(handle).equals(width + "x" + height)) {
            return;
        }
        int[] left = new int[1];
        int[] top = new int[1];
        int[] right = new int[1];
        int[] bottom = new int[1];
        GLFW.glfwGetWindowFrameSize(handle, left, top, right, bottom);
        GLFW.glfwSetWindowPos(handle, left[0], top[0] + MENU_BAR_MARGIN);
        GLFW.glfwSetWindowSize(handle, width, height);
        String fitted = contentSize(handle);
        if (!fitted.equals(width + "x" + height)) {
            throw new IllegalStateException("The main display holds only a " + fitted + " game window, not " + width + "x" + height);
        }
        LOGGER.info("Game window moved to the main display at {}x{}", width, height);
    }

    public void stop(Minecraft client) {
        drain();
        finish();
        client.getFramerateLimitTracker().setFramerateLimit(client.options.framerateLimit().get());
        long deadline = System.nanoTime() + FINISH_TIMEOUT_NANOS;
        try {
            writer.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
            if (!process.waitFor(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("ffmpeg did not finish " + settings.output() + " within 10 s; see " + log);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("Interrupted while finishing " + settings.output(), interrupted);
        }
        if (renderFailure != null) {
            throw new IllegalStateException("Reading frames for " + settings.output() + " failed: " + renderFailure, renderFailure);
        }
        if (writeFailure != null) {
            throw new IllegalStateException("Writing frames to ffmpeg failed: " + writeFailure + "; see " + log, writeFailure);
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("ffmpeg exited with code " + process.exitValue() + " for " + settings.output() + "; see " + log);
        }
        LOGGER.info("Live capture finished: {} frames in {} s, {} dropped, {}", frames, String.format("%.2f", seconds()), droppedFrames,
                settings.output());
    }

    public void abort() {
        finish();
        try {
            if (!process.waitFor(ABORT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    public void frame(Minecraft client) {
        if (closed || renderFailure != null) {
            return;
        }
        try {
            capture(client);
        } catch (RuntimeException failure) {
            renderFailure = failure;
            LOGGER.error("Live capture stopped reading frames for {}", settings.output(), failure);
        }
    }

    public long frames() {
        return frames;
    }

    public double seconds() {
        return ((stopNanos < 0 ? System.nanoTime() : stopNanos) - startNanos) / 1.0e9;
    }

    public String source() {
        return sourceWidth + "x" + sourceHeight;
    }

    private static void validate(Settings settings) {
        Path directory = settings.output().getParent();
        if (directory == null || !Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Capture directory does not exist: " + directory);
        }
        if (!Files.isRegularFile(settings.ffmpeg()) || !Files.isExecutable(settings.ffmpeg())) {
            throw new IllegalArgumentException("ffmpeg is not an executable file: " + settings.ffmpeg());
        }
        if (!evenSize(settings.width()) || !evenSize(settings.height())) {
            throw new IllegalArgumentException("Capture width and height must be even numbers from 2 to " + MAX_SIZE);
        }
        if (settings.fps() < 1 || settings.fps() > MAX_FPS) {
            throw new IllegalArgumentException("Capture fps must be between 1 and " + MAX_FPS);
        }
    }

    private static boolean evenSize(int value) {
        return value >= 2 && value <= MAX_SIZE && value % 2 == 0;
    }

    private static String contentSize(long handle) {
        int[] width = new int[1];
        int[] height = new int[1];
        GLFW.glfwGetWindowSize(handle, width, height);
        return width[0] + "x" + height[0];
    }

    private static String encoder(Path ffmpeg) {
        return ENCODERS.computeIfAbsent(ffmpeg, LiveCapture::probeEncoder);
    }

    private static String probeEncoder(Path ffmpeg) {
        try {
            Process probe = new ProcessBuilder(ffmpeg.toString(), "-hide_banner", "-encoders").redirectErrorStream(true).start();
            String listing = new String(probe.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!probe.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                probe.destroyForcibly();
                throw new IllegalStateException("Listing the encoders of " + ffmpeg + " took longer than " + PROBE_TIMEOUT_SECONDS + " s");
            }
            return listing.contains(" " + HARDWARE_ENCODER + " ") ? HARDWARE_ENCODER : SOFTWARE_ENCODER;
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot list the encoders of " + ffmpeg + ": " + failure.getMessage(), failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while listing the encoders of " + ffmpeg, interrupted);
        }
    }

    private List<String> command() {
        return List.of(settings.ffmpeg().toString(), "-hide_banner", "-nostats", "-y", "-f", "rawvideo", "-pix_fmt", "rgba",
                "-s", source(), "-r", Integer.toString(settings.fps()), "-i", "-", "-vf", filter(), "-c:v", encoder,
                "-b:v", BITRATE, "-pix_fmt", "yuv420p", settings.output().toString());
    }

    private String filter() {
        if (sourceWidth == settings.width() && sourceHeight == settings.height()) {
            return "vflip";
        }
        long wide = (long) sourceWidth * settings.height();
        long tall = (long) sourceHeight * settings.width();
        int cropWidth = wide > tall ? (int) (tall / settings.height()) / 2 * 2 : sourceWidth;
        int cropHeight = wide > tall ? sourceHeight : (int) (wide / settings.width()) / 2 * 2;
        return "vflip,crop=" + cropWidth + ":" + cropHeight + ",scale=" + settings.width() + ":" + settings.height() + ":flags=lanczos";
    }

    private void capture(Minecraft client) {
        client.getFramerateLimitTracker().onInputReceived();
        client.gui.toastManager().clear();
        long due = (System.nanoTime() - startNanos) / intervalNanos + 1;
        if (due <= slots) {
            return;
        }
        RenderTarget target = client.gameRenderer.mainRenderTarget();
        GpuTexture texture = target.getColorTexture();
        if (texture == null || target.width != sourceWidth || target.height != sourceHeight || idleBuffers.isEmpty()) {
            return;
        }
        GpuBuffer buffer = idleBuffers.poll();
        slots = due;
        RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(texture, buffer, 0L, () -> read(buffer), 0);
    }

    private void read(GpuBuffer buffer) {
        if (closed) {
            buffer.close();
            return;
        }
        byte[] frame = spareFrames.poll();
        if (frame == null) {
            droppedFrames++;
            idleBuffers.add(buffer);
            return;
        }
        try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
            view.data().get(0, frame);
        }
        idleBuffers.add(buffer);
        pendingFrames.add(frame);
    }

    private void drain() {
        long deadline = System.nanoTime() + DRAIN_TIMEOUT_NANOS;
        while (idleBuffers.size() < GPU_BUFFERS && renderFailure == null && System.nanoTime() < deadline) {
            RenderSystem.executePendingTasks();
            if (idleBuffers.size() < GPU_BUFFERS) {
                LockSupport.parkNanos(DRAIN_POLL_NANOS);
            }
        }
    }

    private void finish() {
        if (closed) {
            return;
        }
        closed = true;
        stopNanos = System.nanoTime();
        for (GpuBuffer buffer : idleBuffers) {
            buffer.close();
        }
        idleBuffers.clear();
        pendingFrames.add(END);
    }

    private void write() {
        try (OutputStream input = process.getOutputStream()) {
            while (true) {
                byte[] frame = pendingFrames.take();
                if (frame == END) {
                    return;
                }
                input.write(frame);
                frames++;
                spareFrames.add(frame);
            }
        } catch (IOException failure) {
            writeFailure = failure;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public record Settings(Path output, Path ffmpeg, int width, int height, int fps) {
    }
}
