package art.arcane.adapt.client.insight;

import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.net.InsightSink;

import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * What the server's Insight currently reveals. The target is dropped when the server goes quiet for
 * {@link #TTL_MILLIS}, since it refreshes a held target every second; marks live and die with the target.
 */
public final class InsightState implements InsightSink {
    public static final long TTL_MILLIS = 2500L;
    static final long GHOST_HOLD_MILLIS = 350L;
    static final long GHOST_FADE_MILLIS = 300L;

    private final LongSupplier clock;
    private ServerFrame.InsightTarget view;
    private double health;
    private double absorption;
    private long heardAt;
    private long receivedAt;
    private double ghostHealth;
    private long ghostAt;
    private List<ServerFrame.InsightMarks.Mark> marks = List.of();

    public InsightState(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void target(ServerFrame.InsightTarget next) {
        long now = clock.getAsLong();
        if (view != null && view.entityId() == next.entityId()) {
            damaged(next.health(), now);
        } else {
            ghostHealth = 0D;
        }
        view = next;
        health = next.health();
        absorption = next.absorption();
        heardAt = now;
        receivedAt = now;
    }

    @Override
    public void vitals(int entityId, double nextHealth, double nextAbsorption) {
        if (view == null || view.entityId() != entityId) {
            return;
        }
        long now = clock.getAsLong();
        damaged(nextHealth, now);
        health = nextHealth;
        absorption = nextAbsorption;
        heardAt = now;
    }

    @Override
    public void marks(List<ServerFrame.InsightMarks.Mark> next) {
        marks = List.copyOf(next);
    }

    @Override
    public void clear() {
        view = null;
        marks = List.of();
        ghostHealth = 0D;
    }

    @Override
    public void reset() {
        clear();
    }

    /** Drops a target the server stopped refreshing. */
    public void expire() {
        if (view != null && clock.getAsLong() - heardAt > TTL_MILLIS) {
            clear();
        }
    }

    public boolean active() {
        return view != null;
    }

    /** The focused target, or null. */
    public ServerFrame.InsightTarget view() {
        return view;
    }

    public double health() {
        return health;
    }

    public double absorption() {
        return absorption;
    }

    public List<ServerFrame.InsightMarks.Mark> marks() {
        return marks;
    }

    /** Health the bar still shows as recently lost, fading out; equal to {@link #health()} when nothing was lost. */
    public double ghostHealth() {
        if (ghostHealth <= health) {
            return health;
        }
        long age = clock.getAsLong() - ghostAt;
        if (age <= GHOST_HOLD_MILLIS) {
            return ghostHealth;
        }
        double fade = Math.min(1D, (age - GHOST_HOLD_MILLIS) / (double) GHOST_FADE_MILLIS);
        return ghostHealth + (health - ghostHealth) * fade;
    }

    /** Ticks left on an effect of the current target, counted down since it arrived; -1 for infinite effects. */
    public int remainingTicks(int sentTicks) {
        if (sentTicks < 0) {
            return sentTicks;
        }
        long elapsedTicks = (clock.getAsLong() - receivedAt) / 50L;
        return (int) Math.max(0L, sentTicks - elapsedTicks);
    }

    private void damaged(double nextHealth, long now) {
        if (nextHealth < health) {
            ghostHealth = Math.max(ghostHealth(), health);
            ghostAt = now;
        }
    }
}
