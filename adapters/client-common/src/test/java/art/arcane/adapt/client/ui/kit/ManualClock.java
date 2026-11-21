package art.arcane.adapt.client.ui.kit;

import java.util.function.LongSupplier;

/** A clock tests move by hand. */
public final class ManualClock implements LongSupplier {
    private long now = 1_000L;

    @Override
    public long getAsLong() {
        return now;
    }

    public void advance(long millis) {
        now += millis;
    }

    public UiMotion motion() {
        return new UiMotion(this, false);
    }
}
