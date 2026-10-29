package art.arcane.adapt.api.claims;

import art.arcane.adapt.Adapt;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class ClaimRepository implements AutoCloseable {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private final Path path;
  private final ScheduledExecutorService writer;
  private final AtomicReference<Supplier<ClaimState>> pending = new AtomicReference<>();
  private final AtomicBoolean scheduled = new AtomicBoolean();
  private volatile IOException failure;
  private volatile boolean closing;
  private volatile long retryDelayMillis = 100;

  public ClaimRepository(Path path) {
    this.path = Objects.requireNonNull(path).toAbsolutePath();
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, task -> {
      Thread thread = new Thread(task, "Adapt Claims persistence");
      thread.setDaemon(true);
      return thread;
    });
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    writer = executor;
  }

  public ClaimState load() throws IOException {
    if (!Files.exists(path)) {
      return ClaimState.empty();
    }
    try (Reader input = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      JsonObject document = GSON.fromJson(input, JsonObject.class);
      if (document == null) {
        throw new IOException("Claims data is empty: " + path);
      }
      return ClaimJson.read(document);
    } catch (RuntimeException exception) {
      throw new IOException("Cannot read Claims data: " + path, exception);
    }
  }

  public void requestSave(Supplier<ClaimState> state) {
    if (closing) {
      throw new IllegalStateException("Claims persistence is closed");
    }
    pending.set(Objects.requireNonNull(state));
    schedule();
  }

  public void flush() throws IOException {
    if (writer.isTerminated()) {
      if (failure != null) {
        throw failure;
      }
      return;
    }
    try {
      writer.submit(this::writePending).get();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while saving Claims data", exception);
    } catch (ExecutionException exception) {
      throw new IOException("Cannot save Claims data", exception.getCause());
    }
    if (failure != null) {
      throw failure;
    }
  }

  @Override
  public void close() throws IOException {
    closing = true;
    try {
      flush();
    } finally {
      writer.shutdown();
      try {
        if (!writer.awaitTermination(30, TimeUnit.SECONDS)) {
          throw new IOException("Claims persistence did not finish shutting down");
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while closing Claims persistence", exception);
      }
    }
  }

  private void schedule() {
    if (scheduled.compareAndSet(false, true)) {
      writer.schedule(this::drain, retryDelayMillis, TimeUnit.MILLISECONDS);
    }
  }

  private void drain() {
    try {
      writePending();
    } finally {
      scheduled.set(false);
      if (pending.get() != null && !closing) {
        schedule();
      }
    }
  }

  private void writePending() {
    Supplier<ClaimState> source = pending.getAndSet(null);
    if (source == null) {
      return;
    }
    try {
      write(source.get());
      failure = null;
      retryDelayMillis = 100;
    } catch (IOException | RuntimeException exception) {
      IOException previous = failure;
      failure = new IOException("Cannot persist Claims data: " + path, exception);
      pending.compareAndSet(null, source);
      retryDelayMillis = Math.min(30_000, Math.max(1_000, retryDelayMillis * 2));
      if (previous == null) {
        Adapt.error(failure);
      }
    }
  }

  private void write(ClaimState state) throws IOException {
    Files.createDirectories(path.getParent());
    Path temporary = Files.createTempFile(path.getParent(), "claims-", ".json.tmp");
    try {
      try (Writer output = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
        GSON.toJson(ClaimJson.write(state), output);
      }
      try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
        output.force(true);
      }
      Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
