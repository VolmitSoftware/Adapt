package art.arcane.adapt.engine.world;

import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.xp.SpatialXP;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpatialXpLedgerTest {
  @Test
  void newerRemoteTicketDoesNotBlockOlderNearbyTicket() {
    SpatialXpLedger ledger = new SpatialXpLedger();
    WorldRef world = world();
    Skill<?> nearbySkill = mock(Skill.class);
    Skill<?> remoteSkill = mock(Skill.class);
    long now = System.currentTimeMillis();

    assertThat(ledger.offer(ticket(world, nearbySkill, 0D, 0D, 100D, now), now)).isTrue();
    assertThat(ledger.offer(ticket(world, remoteSkill, 1_000D, 1_000D, 100D, now), now)).isTrue();

    SpatialXpLedger.Claim claim = ledger.claim(new Loc(world, 0D, 0D, 0D), now);

    assertThat(claim).isNotNull();
    assertThat(claim.skill()).isSameAs(nearbySkill);
  }

  @Test
  void ticketsAreIsolatedByWorld() {
    SpatialXpLedger ledger = new SpatialXpLedger();
    WorldRef firstWorld = world();
    WorldRef secondWorld = world();
    Skill<?> skill = mock(Skill.class);
    long now = System.currentTimeMillis();
    ledger.offer(ticket(firstWorld, skill, 0D, 0D, 20D, now), now);

    assertThat(ledger.claim(new Loc(secondWorld, 0D, 0D, 0D), now)).isNull();
    assertThat(ledger.size()).isEqualTo(1);
  }

  @Test
  void concurrentClaimsCannotExceedTicketBalance() throws Exception {
    SpatialXpLedger ledger = new SpatialXpLedger();
    WorldRef world = world();
    Skill<?> skill = mock(Skill.class);
    long now = System.currentTimeMillis();
    ledger.offer(ticket(world, skill, 0D, 0D, 100D, now), now);
    ExecutorService executor = Executors.newFixedThreadPool(8);
    try {
      List<Callable<Double>> tasks = new ArrayList<>();
      for (int index = 0; index < 64; index++) {
        tasks.add(() -> {
          SpatialXpLedger.Claim claim = ledger.claim(new Loc(world, 0D, 0D, 0D), now);
          return claim == null ? 0D : claim.xp();
        });
      }

      List<Future<Double>> futures = executor.invokeAll(tasks);
      executor.shutdown();
      assertThat(executor.awaitTermination(5L, TimeUnit.SECONDS)).isTrue();
      double awarded = 0D;
      for (Future<Double> future : futures) {
        awarded += future.get();
      }

      assertThat(awarded).isCloseTo(100D, org.assertj.core.data.Offset.offset(0.000_001D));
      assertThat(ledger.size()).isZero();
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void indexingAtMaximumChunkDoesNotWrap() {
    SpatialXpLedger ledger = new SpatialXpLedger();
    WorldRef world = world();
    long now = System.currentTimeMillis();
    double x = ((double) Integer.MAX_VALUE - 1D) * 16D;
    SpatialXP ticket = ticket(world, mock(Skill.class), x, 0D, 100D, now);

    assertTimeoutPreemptively(Duration.ofSeconds(1L), () ->
        assertThat(ledger.offer(ticket, now)).isTrue());
  }

  @Test
  void expiredTicketsArePurgedFromAllIndexedCells() {
    SpatialXpLedger ledger = new SpatialXpLedger();
    WorldRef world = world();
    long now = System.currentTimeMillis();
    SpatialXP ticket = ticket(world, mock(Skill.class), 0D, 0D, 20D, now);
    ticket.setMs(now - 1L);

    assertThat(ledger.offer(ticket, now - 2L)).isTrue();
    ledger.purgeExpired(now);

    assertThat(ledger.size()).isZero();
    assertThat(ledger.claim(new Loc(world, 0D, 0D, 0D), now)).isNull();
  }

  private static SpatialXP ticket(WorldRef world, Skill<?> skill, double x, double z, double xp, long now) {
    SpatialXP ticket = new SpatialXP(new Loc(world, x, 0D, z), skill, xp, 32D, 10_000L);
    ticket.setMs(now + 10_000L);
    return ticket;
  }

  private static WorldRef world() {
    WorldRef world = mock(WorldRef.class);
    when(world.uid()).thenReturn(UUID.randomUUID());
    return world;
  }
}
