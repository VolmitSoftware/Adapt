package art.arcane.adapt.client.hud;

/** Time for the HUD: a monotonic clock for animation and expiry, and wall time to read server timestamps. */
public interface HudClock {
    HudClock SYSTEM = new HudClock() {
        @Override
        public long millis() {
            return System.nanoTime() / 1_000_000L;
        }

        @Override
        public long epochMillis() {
            return System.currentTimeMillis();
        }
    };

    /** Monotonic milliseconds; only differences mean anything. */
    long millis();

    long epochMillis();
}
