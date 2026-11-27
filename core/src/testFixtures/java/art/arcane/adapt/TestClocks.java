package art.arcane.adapt;

import art.arcane.adapt.api.AdaptClock;

import java.util.function.LongSupplier;

/** Clocks a test stubs onto a spied core ({@code when(core.clock()).thenReturn(...)}) to drive {@code AdaptApi.clock()}. */
public final class TestClocks {
  private TestClocks() {
  }

  /** Wall time read from {@code millis} on every call, at tick 0. */
  public static AdaptClock reading(LongSupplier millis) {
    return new AdaptClock() {
      @Override
      public long millis() {
        return millis.getAsLong();
      }

      @Override
      public long tick() {
        return 0L;
      }
    };
  }
}
