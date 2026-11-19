package art.arcane.adapt.util.common.scheduling;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One thread per task for blocking work (player data claims). Uses virtual threads when the running Java has them
 * (21+), else unbounded daemon platform threads, so the same jar behaves alike on Java 17, 21 and 25. The lookup is
 * reflective because the Java 17 build is a bytecode-downgraded jar that cannot link the Java 21 factory directly.
 */
public final class BlockingTaskExecutor {
  private static final MethodType FACTORY_TYPE = MethodType.methodType(ExecutorService.class);

  private BlockingTaskExecutor() {
  }

  /** Executor for blocking tasks; {@code name} prefixes the platform threads used where virtual threads are absent. */
  public static ExecutorService create(String name) {
    MethodHandle virtualFactory = virtualThreadFactory();
    return virtualFactory == null ? platformThreads(name) : virtualThreads(virtualFactory);
  }

  static MethodHandle virtualThreadFactory() {
    try {
      return MethodHandles.publicLookup().findStatic(Executors.class, "newVirtualThreadPerTaskExecutor", FACTORY_TYPE);
    } catch (NoSuchMethodException | IllegalAccessException missing) {
      return null;
    }
  }

  static ExecutorService virtualThreads(MethodHandle factory) {
    try {
      return (ExecutorService) factory.invokeExact();
    } catch (RuntimeException | Error failure) {
      throw failure;
    } catch (Throwable failure) {
      throw new IllegalStateException("Virtual thread executor failed to start", failure);
    }
  }

  static ExecutorService platformThreads(String name) {
    AtomicInteger counter = new AtomicInteger();
    ThreadFactory factory = (Runnable task) -> {
      Thread thread = new Thread(task, name + "-" + counter.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    };
    return Executors.newCachedThreadPool(factory);
  }
}
