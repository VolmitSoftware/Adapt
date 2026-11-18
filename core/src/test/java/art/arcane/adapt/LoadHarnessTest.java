package art.arcane.adapt;

import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.AdaptEventBus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoadHarnessTest extends AdaptTestBase {

    public static class LoadEvent extends AdaptEvent {
    }

    public static class NoopListener {
        public void onLoad(LoadEvent e) {
        }
    }

    @Test
    @DisplayName("simulated 1000-player data and dispatch micro-harness reports timing")
    void thousandPlayerLoadReport() {
        int players = 1000;
        List<PlayerData> data = new ArrayList<>(players);
        List<PlayerSkillLine> lines = new ArrayList<>(players);
        for (int i = 0; i < players; i++) {
            data.add(new PlayerData());
            lines.add(new PlayerSkillLine());
        }
        NoopListener listener = new NoopListener();
        AdaptEventBus bus = core.getBus();
        bus.register(listener, LoadEvent.class, listener::onLoad);
        LoadEvent event = new LoadEvent();

        Runnable oneTick = () -> {
            for (int i = 0; i < players; i++) {
                PlayerData pd = data.get(i);
                pd.addStat("blocks", 1.0);
                PlayerSkillLine line = lines.get(i);
                line.award(null, new XpAward("test:work", 1.0, null, false, XpAward.Kind.GAMEPLAY), 1.0, pd.getXpEarnings());
                if ((i & 7) == 0) {
                    line.flushXpPool(null);
                }
                bus.post(event);
                bus.post(event);
                bus.post(event);
            }
        };

        for (int warmup = 0; warmup < 3; warmup++) {
            oneTick.run();
        }

        int samples = 7;
        long[] nanos = new long[samples];
        for (int s = 0; s < samples; s++) {
            long t0 = System.nanoTime();
            oneTick.run();
            nanos[s] = System.nanoTime() - t0;
        }
        Arrays.sort(nanos);
        long medianNanos = nanos[samples / 2];
        double msPerTick = medianNanos / 1_000_000.0;
        double tickBudgetMs = 50.0;
        double budgetPercent = (msPerTick / tickBudgetMs) * 100.0;
        String verdict = msPerTick <= tickBudgetMs ? "PASS" : "WARN";

        System.out.println("==================== ADAPT 1K LOAD HARNESS ====================");
        System.out.println("players                : " + players);
        System.out.println("work per player/tick   : addStat + XP award (flush 1/8) + 3 event bus dispatches");
        System.out.println("excluded runtime work  : Bukkit players, schedulers, stat trackers, world/entity scans, persistence IO");
        System.out.println("interpretation         : micro-harness only; not a production 1K-server capacity claim");
        System.out.printf("median tick time       : %.3f ms%n", msPerTick);
        System.out.printf("server tick budget     : %.1f ms (one 20 TPS tick)%n", tickBudgetMs);
        System.out.printf("budget used            : %.2f %% [%s]%n", budgetPercent, verdict);
        System.out.println("===============================================================");

        bus.unregisterAll(listener);
        assertThat(data).hasSize(players);
        assertThat(medianNanos).isGreaterThan(0L);
    }
}
