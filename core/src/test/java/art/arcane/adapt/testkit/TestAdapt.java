package art.arcane.adapt.testkit;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.ComponentRegistrar;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Runs a real {@link AdaptCore} runtime (ticker, player server, skills, attributes, advancements, bus listeners) on a
 * {@link TestPlatform}, skipping the services half (language download, HUD, SQL). Close it after the test.
 */
public final class TestAdapt implements AutoCloseable {
  private final TestPlatform platform;
  private final AdaptEventBus bus;
  private final AdaptCore core;
  private final List<Throwable> handlerFailures = new ArrayList<>();

  private TestAdapt(TestPlatform platform, List<Class<? extends Skill<?>>> skillTypes) {
    this.platform = platform;
    bus = new AdaptEventBus((Object owner, AdaptEvent event, Throwable error) -> handlerFailures.add(error));
    ComponentRegistrar registrar = new ComponentRegistrar(bus);
    core = new AdaptCore(platform.platform(), bus, registrar, skillTypes);
  }

  /** Installs the environment for {@code platform} and starts the core runtime with {@code skillTypes}. */
  public static TestAdapt start(TestPlatform platform, List<Class<? extends Skill<?>>> skillTypes) {
    Path data = platform.host().dataDirectory();
    AdaptEnvironment.install(platform.log(), () -> data);
    TestAdapt adapt = new TestAdapt(platform, skillTypes);
    adapt.core.startRuntime();
    return adapt;
  }

  public AdaptCore core() {
    return core;
  }

  public TestPlatform platform() {
    return platform;
  }

  public AdaptEventBus bus() {
    return bus;
  }

  /** Posts {@code event} through every priority, like a platform bridge. */
  public <E extends AdaptEvent> E post(E event) {
    return bus.post(event);
  }

  /**
   * Advances the scheduler one tick at a time until {@code condition} holds; work from other threads (player data
   * claims) gets real time to land. Throws when it does not hold within {@code timeoutMillis}.
   */
  public void awaitTicks(BooleanSupplier condition, long timeoutMillis) {
    long deadline = System.currentTimeMillis() + timeoutMillis;
    while (!condition.getAsBoolean()) {
      if (System.currentTimeMillis() > deadline) {
        throw new AssertionError("Condition not reached within " + timeoutMillis + " ms");
      }
      platform.scheduler().advance(1);
      try {
        Thread.sleep(2L);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted while waiting", interrupted);
      }
    }
  }

  /** Posts the join for an online player and waits until its Adapt profile is loaded. */
  public AdaptPlayer join(PlayerRef player) {
    post(new PlayerJoinEvent(player));
    awaitTicks(() -> core.getAdaptServer().getPlayer(player) != null, 10_000L);
    return core.getAdaptServer().getPlayer(player);
  }

  /** Exceptions thrown by bus handlers so far. */
  public List<Throwable> handlerFailures() {
    return List.copyOf(handlerFailures);
  }

  @Override
  public void close() {
    core.stopRuntime();
    platform.scheduler().cancelAll();
    AdaptPorts.clear();
  }
}
