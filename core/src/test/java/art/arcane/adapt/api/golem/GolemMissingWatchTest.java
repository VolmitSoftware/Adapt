package art.arcane.adapt.api.golem;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GolemMissingWatchTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
  private static final UUID OTHER_OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
  private static final UUID GOLEM = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final UUID NETHER = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

  @Test
  void expectationsExpireOnlyAfterTheirChunkLoadsAndTheGraceRunsOut() {
    GolemMissingWatch watch = new GolemMissingWatch();
    GolemMissingWatch.Expected expected = new GolemMissingWatch.Expected(OWNER, GOLEM, WORLD, 42L);
    watch.expect(expected);

    assertThat(watch.expired(99_999L)).isEmpty();

    watch.chunkLoaded(WORLD, 42L, 1_000L);

    assertThat(GolemMissingWatch.GRACE_MILLIS).isEqualTo(5_000L);
    assertThat(watch.expired(5_999L)).isEmpty();
    assertThat(watch.expired(6_000L)).containsExactly(expected);
    assertThat(watch.expired(99_999L)).isEmpty();
    assertThat(watch.hasExpectations()).isFalse();
  }

  @Test
  void otherChunksAndWorldsDoNotArmTheDeadline() {
    GolemMissingWatch watch = new GolemMissingWatch();
    watch.expect(new GolemMissingWatch.Expected(OWNER, GOLEM, WORLD, 42L));

    watch.chunkLoaded(WORLD, 43L, 0L);
    watch.chunkLoaded(NETHER, 42L, 0L);

    assertThat(watch.expired(99_999L)).isEmpty();
    assertThat(watch.hasExpectations()).isTrue();
  }

  @Test
  void aGolemThatRegistersCancelsItsDeadline() {
    GolemMissingWatch watch = new GolemMissingWatch();
    watch.expect(new GolemMissingWatch.Expected(OWNER, GOLEM, WORLD, 42L));
    watch.chunkLoaded(WORLD, 42L, 0L);

    watch.forget(GOLEM);

    assertThat(watch.expired(99_999L)).isEmpty();
    assertThat(watch.hasExpectations()).isFalse();
  }

  @Test
  void reloadingTheChunkDoesNotExtendAnArmedDeadline() {
    GolemMissingWatch watch = new GolemMissingWatch();
    GolemMissingWatch.Expected expected = new GolemMissingWatch.Expected(OWNER, GOLEM, WORLD, 42L);
    watch.expect(expected);
    watch.chunkLoaded(WORLD, 42L, 0L);
    watch.chunkLoaded(WORLD, 42L, 4_000L);

    assertThat(watch.expired(5_000L)).containsExactly(expected);
  }

  @Test
  void reExpectingMovesTheGolemToItsNewChunkAndDisarmsTheOld() {
    GolemMissingWatch watch = new GolemMissingWatch();
    watch.expect(new GolemMissingWatch.Expected(OWNER, GOLEM, WORLD, 42L));
    watch.chunkLoaded(WORLD, 42L, 0L);
    GolemMissingWatch.Expected moved = new GolemMissingWatch.Expected(OWNER, GOLEM, WORLD, 50L);

    watch.expect(moved);

    assertThat(watch.expired(99_999L)).isEmpty();
    watch.chunkLoaded(WORLD, 50L, 100_000L);
    assertThat(watch.expired(105_000L)).containsExactly(moved);
  }

  @Test
  void forgettingAnOwnerDropsOnlyTheirGolems() {
    GolemMissingWatch watch = new GolemMissingWatch();
    GolemMissingWatch.Expected kept = new GolemMissingWatch.Expected(OTHER_OWNER, SECOND, WORLD, 42L);
    watch.expect(new GolemMissingWatch.Expected(OWNER, GOLEM, WORLD, 42L));
    watch.expect(kept);

    watch.forgetOwner(OWNER);
    watch.chunkLoaded(WORLD, 42L, 0L);

    assertThat(watch.expired(5_000L)).containsExactly(kept);
  }

  @Test
  void idleWatchReportsNoDeadlines() {
    GolemMissingWatch watch = new GolemMissingWatch();

    assertThat(watch.hasDeadlines()).isFalse();
    watch.expect(new GolemMissingWatch.Expected(OWNER, GOLEM, WORLD, 42L));
    assertThat(watch.hasDeadlines()).isFalse();
    watch.chunkLoaded(WORLD, 42L, 0L);
    assertThat(watch.hasDeadlines()).isTrue();
  }
}
