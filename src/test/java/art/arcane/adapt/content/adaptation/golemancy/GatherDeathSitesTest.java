package art.arcane.adapt.content.adaptation.golemancy;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class GatherDeathSitesTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  private static final UUID NETHER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
  private static final long FIVE_MINUTES = 300_000L;

  private final GatherDeathSites sites = new GatherDeathSites();

  @Test
  void itemsWithinEightBlocksOfARecentDeathAreProtected() {
    sites.record(WORLD, 10D, 64D, 10D, 1_000L, FIVE_MINUTES);

    assertThat(sites.protects(WORLD, 10D, 64D, 10D, 8D, 2_000L)).isTrue();
    assertThat(sites.protects(WORLD, 17.9D, 64D, 10D, 8D, 2_000L)).isTrue();
    assertThat(sites.protects(WORLD, 10D, 64D, 18.1D, 8D, 2_000L)).isFalse();
    assertThat(sites.protects(WORLD, 10D, 73D, 10D, 8D, 2_000L)).isFalse();
    assertThat(sites.protects(NETHER, 10D, 64D, 10D, 8D, 2_000L)).isFalse();
  }

  @Test
  void aDeathSiteLapsesAfterFiveMinutesAndIsSweptAway() {
    sites.record(WORLD, 0D, 64D, 0D, 1_000L, FIVE_MINUTES);

    assertThat(sites.protects(WORLD, 0D, 64D, 0D, 8D, 1_000L + FIVE_MINUTES - 1L)).isTrue();
    assertThat(sites.protects(WORLD, 0D, 64D, 0D, 8D, 1_000L + FIVE_MINUTES)).isFalse();
    assertThat(sites.isEmpty()).isFalse();

    sites.sweep(1_000L + FIVE_MINUTES);

    assertThat(sites.isEmpty()).isTrue();
  }

  @Test
  void recordingSweepsLapsedSitesAndAZeroDurationRecordsNothing() {
    sites.record(WORLD, 0D, 64D, 0D, 0L, 1_000L);
    sites.record(WORLD, 50D, 64D, 0D, 5_000L, FIVE_MINUTES);
    sites.record(WORLD, 90D, 64D, 0D, 5_000L, 0L);

    assertThat(sites.size(WORLD)).isEqualTo(1);
    assertThat(sites.protects(WORLD, 50D, 64D, 0D, 8D, 5_000L)).isTrue();
  }

  @Test
  void eachWorldKeepsOnlyItsNewestSitesWhenFullWithoutTouchingOtherWorlds() {
    sites.record(NETHER, 0D, 64D, 0D, 1_000L, FIVE_MINUTES);
    for (int i = 0; i < GatherDeathSites.MAX_SITES_PER_WORLD + 10; i++) {
      sites.record(WORLD, i * 100D, 64D, 0D, 1_000L + i, FIVE_MINUTES);
    }

    assertThat(sites.size(WORLD)).isEqualTo(GatherDeathSites.MAX_SITES_PER_WORLD);
    assertThat(sites.protects(WORLD, 0D, 64D, 0D, 8D, 2_000L)).isFalse();
    assertThat(sites.protects(WORLD, (GatherDeathSites.MAX_SITES_PER_WORLD + 9) * 100D, 64D, 0D, 8D, 2_000L)).isTrue();
    assertThat(sites.size(NETHER)).isEqualTo(1);
    assertThat(sites.protects(NETHER, 0D, 64D, 0D, 8D, 2_000L)).isTrue();
  }

  @Test
  void deathsRecordedFromManyThreadsAreAllKept() throws InterruptedException {
    ExecutorService pool = Executors.newFixedThreadPool(8);
    CountDownLatch start = new CountDownLatch(1);
    for (int i = 0; i < 64; i++) {
      double x = i * 100D;
      pool.execute(() -> {
        try {
          start.await();
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
        }
        sites.record(WORLD, x, 64D, 0D, 1_000L, FIVE_MINUTES);
      });
    }
    start.countDown();
    pool.shutdown();
    assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

    assertThat(sites.size(WORLD)).isEqualTo(64);
  }

  @Test
  void clearingForgetsEverySite() {
    sites.record(WORLD, 0D, 64D, 0D, 1_000L, FIVE_MINUTES);

    sites.clear();

    assertThat(sites.isEmpty()).isTrue();
  }
}
