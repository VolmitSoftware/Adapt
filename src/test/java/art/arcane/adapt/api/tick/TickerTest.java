package art.arcane.adapt.api.tick;

import art.arcane.adapt.AdaptTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TickerTest extends AdaptTestBase {
  private static Method tickMethod() throws NoSuchMethodException {
    Method method = Ticker.class.getDeclaredMethod("tick");
    method.setAccessible(true);
    return method;
  }

  private static Ticked baseMock(String id) {
    Ticked ticked = mock(Ticked.class);
    lenient().when(ticked.getId()).thenReturn(id);
    lenient().when(ticked.getGroup()).thenReturn("test");
    lenient().when(ticked.getInterval()).thenReturn(0L);
    lenient().when(ticked.getLastTick()).thenReturn(0L);
    lenient().when(ticked.hasTickDemand()).thenReturn(true);
    return ticked;
  }

  @Test
  @DisplayName("an exception thrown by one ticked does not stop the others")
  void exceptionIsolation() throws Exception {
    AtomicLong now = new AtomicLong(1_000L);
    Ticker ticker = new Ticker(now::get);
    AtomicInteger goodTicks = new AtomicInteger();
    Ticked good = baseMock("good");
    doAnswer(invocation -> {
      goodTicks.incrementAndGet();
      return null;
    }).when(good).tick();
    Ticked bad = baseMock("bad");
    doThrow(new RuntimeException("boom")).when(bad).tick();
    ticker.register(good);
    ticker.register(bad);

    tickMethod().invoke(ticker);

    assertThat(goodTicks.get()).isEqualTo(1);
    ticker.clear();
  }

  @Test
  @DisplayName("a tick is due exactly at its configured interval")
  void exactIntervalBoundaryIsDue() {
    assertThat(Ticker.isDue(1_050L, 1_000L, 50L)).isTrue();
    assertThat(Ticker.isDue(1_049L, 1_000L, 50L)).isFalse();
    assertThat(Ticker.isDue(999L, 1_000L, 50L)).isFalse();
    assertThat(Ticker.isDue(Long.MAX_VALUE, 1L, Long.MAX_VALUE)).isFalse();
  }

  @Test
  @DisplayName("objects without tick demand stay out of the execution path")
  void noDemandSkipsTickExecution() throws Exception {
    AtomicLong now = new AtomicLong(1_000L);
    Ticker ticker = new Ticker(now::get);
    Ticked idle = baseMock("idle");
    when(idle.hasTickDemand()).thenReturn(false);
    ticker.register(idle);

    Method tick = tickMethod();
    tick.invoke(ticker);
    now.addAndGet(TickedObject.MIN_INTERVAL_MILLIS - 1L);
    tick.invoke(ticker);

    verify(idle, never()).tick();
    ticker.clear();
  }

  @Test
  @DisplayName("an exception from tick demand does not stop other objects")
  void demandExceptionIsolation() throws Exception {
    AtomicLong now = new AtomicLong(1_000L);
    Ticker ticker = new Ticker(now::get);
    AtomicInteger goodTicks = new AtomicInteger();
    Ticked bad = baseMock("bad-demand");
    when(bad.hasTickDemand()).thenThrow(new RuntimeException("demand boom"));
    Ticked good = baseMock("good-demand");
    doAnswer(invocation -> {
      goodTicks.incrementAndGet();
      return null;
    }).when(good).tick();
    ticker.register(bad);
    ticker.register(good);

    tickMethod().invoke(ticker);

    verify(bad, never()).tick();
    assertThat(goodTicks.get()).isEqualTo(1);
    ticker.clear();
  }

  @Test
  @DisplayName("deadlines execute in due order with stable registration ordering for ties")
  void dueOrderingIsDeterministic() throws Exception {
    AtomicLong now = new AtomicLong(1_000L);
    Ticker ticker = new Ticker(now::get);
    ArrayList<String> order = new ArrayList<>();
    ControlledTicked latest = new ControlledTicked("latest", now).schedule(900L, 100L).recordInto(order);
    ControlledTicked earliest = new ControlledTicked("earliest", now).schedule(800L, 100L).recordInto(order);
    ControlledTicked tied = new ControlledTicked("tied", now).schedule(900L, 100L).recordInto(order);
    ticker.register(latest);
    ticker.register(earliest);
    ticker.register(tied);

    tickMethod().invoke(ticker);

    assertThat(order).containsExactly("earliest", "latest", "tied");
    ticker.clear();
  }

  @Test
  @DisplayName("cancelled registrations never execute")
  void cancellationRemovesScheduledWork() throws Exception {
    AtomicLong now = new AtomicLong(1_000L);
    Ticker ticker = new Ticker(now::get);
    ControlledTicked cancelled = new ControlledTicked("cancelled", now);
    ticker.register(cancelled);
    ticker.unregister(cancelled);

    tickMethod().invoke(ticker);

    assertThat(cancelled.tickCount()).isZero();
  }

  @Test
  @DisplayName("shutdown clears deadlines and rejects late registrations")
  void shutdownStopsFurtherScheduling() throws Exception {
    AtomicLong now = new AtomicLong(1_000L);
    Ticker ticker = new Ticker(now::get);
    ControlledTicked beforeShutdown = new ControlledTicked("before-shutdown", now);
    ControlledTicked afterShutdown = new ControlledTicked("after-shutdown", now);
    ticker.register(beforeShutdown);

    ticker.shutdown();
    ticker.register(afterShutdown);
    tickMethod().invoke(ticker);

    assertThat(beforeShutdown.tickCount()).isZero();
    assertThat(afterShutdown.tickCount()).isZero();
  }

  @Test
  @DisplayName("each registration preserves its interval cadence")
  void cadenceUsesTheActualLastTick() throws Exception {
    AtomicLong now = new AtomicLong(1_000L);
    Ticker ticker = new Ticker(now::get);
    ControlledTicked ticked = new ControlledTicked("cadence", now).schedule(1_000L, 100L);
    ticker.register(ticked);
    Method tick = tickMethod();

    tick.invoke(ticker);
    now.set(1_099L);
    tick.invoke(ticker);
    now.set(1_100L);
    tick.invoke(ticker);
    now.set(1_199L);
    tick.invoke(ticker);
    now.set(1_200L);
    tick.invoke(ticker);

    assertThat(ticked.tickCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("an interval change moves an existing registration to its new deadline")
  void rescheduleAppliesDynamicIntervalChanges() throws Exception {
    AtomicLong now = new AtomicLong(1_000L);
    Ticker ticker = new Ticker(now::get);
    ControlledTicked ticked = new ControlledTicked("dynamic", now).schedule(1_000L, 1_000L);
    ticker.register(ticked);

    ticked.setInterval(50L);
    ticker.reschedule(ticked);
    now.set(1_050L);
    tickMethod().invoke(ticker);

    assertThat(ticked.tickCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("idle future registrations are not scanned on every server tick")
  void futureDeadlinesAvoidIdleScans() throws Exception {
    AtomicLong now = new AtomicLong(1_000L);
    Ticker ticker = new Ticker(now::get);
    ArrayList<ControlledTicked> idle = new ArrayList<>(10_000);
    for (int index = 0; index < 10_000; index++) {
      ControlledTicked ticked = new ControlledTicked("idle-" + index, now).schedule(1_000L, 60_000L);
      idle.add(ticked);
      ticker.register(ticked);
    }

    Method tick = tickMethod();
    for (int index = 0; index < 100; index++) {
      now.incrementAndGet();
      tick.invoke(ticker);
    }

    assertThat(idle).allSatisfy(ticked -> {
      assertThat(ticked.scheduleReads()).isEqualTo(2);
      assertThat(ticked.tickCount()).isZero();
    });
  }

  @Test
  @DisplayName("concurrent registration publishes every unique deadline exactly once")
  void concurrentRegistrationIsSafe() throws Exception {
    AtomicLong now = new AtomicLong(1_000L);
    Ticker ticker = new Ticker(now::get);
    int registrationCount = 4_000;
    ArrayList<ControlledTicked> tickedObjects = new ArrayList<>(registrationCount);
    for (int index = 0; index < registrationCount; index++) {
      tickedObjects.add(new ControlledTicked("concurrent-" + index, now));
    }

    ExecutorService executor = Executors.newFixedThreadPool(8);
    CountDownLatch start = new CountDownLatch(1);
    ArrayList<Future<?>> futures = new ArrayList<>(8);
    try {
      for (int worker = 0; worker < 8; worker++) {
        int workerIndex = worker;
        futures.add(executor.submit(() -> {
          start.await();
          for (int index = workerIndex; index < registrationCount; index += 8) {
            ticker.register(tickedObjects.get(index));
          }
          return null;
        }));
      }
      start.countDown();
      for (Future<?> future : futures) {
        future.get(10L, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
      executor.awaitTermination(5L, TimeUnit.SECONDS);
    }

    tickMethod().invoke(ticker);
    assertThat(tickedObjects).allSatisfy(ticked -> assertThat(ticked.tickCount()).isEqualTo(1));
  }

  @Test
  @DisplayName("registering and ticking many objects does not throw")
  void registerAndTickManyDoesNotThrow() throws Exception {
    AtomicLong now = new AtomicLong(1_000L);
    Ticker ticker = new Ticker(now::get);
    for (int index = 0; index < 200; index++) {
      ticker.register(baseMock("t" + index));
    }
    Method tick = tickMethod();

    assertThatCode(() -> {
      for (int index = 0; index < 5; index++) {
        now.incrementAndGet();
        tick.invoke(ticker);
      }
    }).doesNotThrowAnyException();
    ticker.clear();
  }

  @Test
  @DisplayName("unregister removes only the requested object when ids match")
  void unregisterUsesObjectIdentity() throws Exception {
    AtomicLong now = new AtomicLong(1_000L);
    Ticker ticker = new Ticker(now::get);
    EqualTicked first = new EqualTicked("shared", now);
    EqualTicked second = new EqualTicked("shared", now);

    ticker.register(first);
    ticker.register(second);
    Method tick = tickMethod();
    tick.invoke(ticker);
    now.incrementAndGet();
    tick.invoke(ticker);
    ticker.unregister(first);
    now.incrementAndGet();
    tick.invoke(ticker);

    assertThat(first.tickCount()).isEqualTo(2);
    assertThat(second.tickCount()).isEqualTo(3);
    ticker.clear();
  }

  private static class ControlledTicked implements Ticked {
    private final String id;
    private final AtomicLong lastTick;
    private final AtomicLong interval;
    private final AtomicLong now;
    private final AtomicInteger ticks;
    private final AtomicInteger scheduleReads;
    private List<String> order;

    private ControlledTicked(String id, AtomicLong now) {
      this.id = id;
      this.lastTick = new AtomicLong();
      this.interval = new AtomicLong();
      this.now = now;
      this.ticks = new AtomicInteger();
      this.scheduleReads = new AtomicInteger();
      this.order = new ArrayList<>();
    }

    private ControlledTicked schedule(long lastTick, long interval) {
      this.lastTick.set(lastTick);
      this.interval.set(interval);
      return this;
    }

    private ControlledTicked recordInto(List<String> order) {
      this.order = order;
      return this;
    }

    final int tickCount() {
      return ticks.get();
    }

    final int scheduleReads() {
      return scheduleReads.get();
    }

    @Override
    public void unregister() {
    }

    @Override
    public boolean isBursting() {
      return false;
    }

    @Override
    public boolean isSkipping() {
      return false;
    }

    @Override
    public void stopBursting() {
    }

    @Override
    public void stopSkipping() {
    }

    @Override
    public long getAge() {
      return 0L;
    }

    @Override
    public void burst(int ticks) {
    }

    @Override
    public void skip(int ticks) {
    }

    @Override
    public long getLastTick() {
      scheduleReads.incrementAndGet();
      return lastTick.get();
    }

    @Override
    public long getInterval() {
      scheduleReads.incrementAndGet();
      return interval.get();
    }

    @Override
    public void setInterval(long ms) {
      interval.set(ms);
    }

    @Override
    public void tick() {
      ticks.incrementAndGet();
      order.add(id);
      lastTick.set(now.get());
    }

    @Override
    public String getGroup() {
      return "test";
    }

    @Override
    public String getId() {
      return id;
    }
  }

  private static final class EqualTicked extends ControlledTicked {
    private EqualTicked(String id, AtomicLong now) {
      super(id, now);
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof EqualTicked ticked && getId().equals(ticked.getId());
    }

    @Override
    public int hashCode() {
      return getId().hashCode();
    }
  }
}
