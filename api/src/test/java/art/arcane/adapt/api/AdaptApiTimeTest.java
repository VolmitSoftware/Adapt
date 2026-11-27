package art.arcane.adapt.api;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.internal.EngineBridge;
import art.arcane.adapt.platform.AdaptPlatform;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.TaskHandle;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.fx.FxHost;
import art.arcane.adapt.platform.menu.MenuHost;
import art.arcane.adapt.platform.permission.PermissionHost;
import art.arcane.adapt.platform.protection.ProtectionHost;
import art.arcane.adapt.platform.recipe.RecipeHost;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.viewer.ViewerHost;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptApiTimeTest {
  private EngineBridge running;

  @AfterEach
  void stop() {
    if (running != null) {
      Engine.stop(TestHost.key(), running);
    }
  }

  @Test
  void withoutACoreTheSchedulerDoesNothingAndNeverReturnsNull() {
    AdaptScheduler scheduler = AdaptApi.scheduler();
    List<String> ran = new ArrayList<>();
    EntityRef entity = TestStubs.stub(EntityRef.class, Map.of());
    Loc spot = new Loc(TestStubs.stub(WorldRef.class, Map.of()), 0D, 0D, 0D);

    assertThat(scheduler.runGlobal(() -> ran.add("global"))).isFalse();
    assertThat(scheduler.runEntity(entity, () -> ran.add("entity"), () -> ran.add("retired"))).isFalse();
    assertThat(scheduler.runAt(spot, () -> ran.add("at"), 5L)).isFalse();
    scheduler.runAsync(() -> ran.add("async"));
    TaskHandle repeating = scheduler.repeatGlobal(() -> ran.add("repeat"), 0L, 1L);
    TaskHandle located = scheduler.repeatAt(spot, () -> ran.add("repeat-at"), 0L, 1L);

    assertThat(repeating).isNotNull();
    assertThat(repeating.isCancelled()).isTrue();
    assertThat(located.isCancelled()).isTrue();
    assertThat(scheduler.isTickThread()).isFalse();
    assertThat(scheduler.owns(entity)).isFalse();
    assertThat(ran).isEmpty();
  }

  @Test
  void withoutACoreTheClockIsSystemTimeAtTickZero() {
    long before = System.currentTimeMillis();
    long read = AdaptApi.clock().millis();

    assertThat(read).isBetween(before, System.currentTimeMillis());
    assertThat(AdaptApi.clock().tick()).isZero();
  }

  @Test
  void withACoreTheSchedulerForwardsClampedDelaysAndTheClockIsTheCores() {
    List<Long> delays = new ArrayList<>();
    AdaptScheduler scheduler = TestStubs.stub(AdaptScheduler.class, Map.of(
        "runGlobal", (Object[] args) -> delays.add((Long) args[1]),
        "repeatGlobal", (Object[] args) -> {
          delays.add((Long) args[1]);
          delays.add((Long) args[2]);
          return null;
        },
        "isTickThread", (Object[] args) -> true));
    AdaptClock clock = new AdaptClock() {
      @Override
      public long millis() {
        return 42_000L;
      }

      @Override
      public long tick() {
        return 7L;
      }
    };
    running = bridge(scheduler, clock);
    Engine.start(TestHost.key(), running);

    assertThat(AdaptApi.scheduler().runGlobal(() -> { }, -4L)).isTrue();
    assertThat(AdaptApi.scheduler().repeatGlobal(() -> { }, -1L, 0L).isCancelled()).isTrue();
    assertThat(AdaptApi.scheduler().runGlobal(null)).isFalse();
    assertThat(AdaptApi.scheduler().isTickThread()).isTrue();

    assertThat(delays).containsExactly(0L, 0L, 1L);
    assertThat(AdaptApi.clock().millis()).isEqualTo(42_000L);
    assertThat(AdaptApi.clock().tick()).isEqualTo(7L);
  }

  private static EngineBridge bridge(AdaptScheduler scheduler, AdaptClock clock) {
    AdaptPlatform platform = new AdaptPlatform(stub(PlatformHost.class), scheduler, stub(FxHost.class),
        stub(ViewerHost.class), stub(MenuHost.class), stub(PermissionHost.class), stub(ProtectionHost.class),
        stub(RecipeHost.class));
    Map<String, Function<Object[], Object>> answers = Map.of(
        "platform", (Object[] args) -> platform,
        "clock", (Object[] args) -> clock);
    return TestStubs.stub(EngineBridge.class, answers);
  }

  private static <T> T stub(Class<T> type) {
    return TestStubs.stub(type, Map.of());
  }
}
