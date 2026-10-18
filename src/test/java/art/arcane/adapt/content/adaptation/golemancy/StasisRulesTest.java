package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRoster;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class StasisRulesTest {
  private static final List<Double> CHANNEL = List.of(2D, 0D, 0D);
  private static final long SECOND = 1_000L;

  @Test
  void levelOneChannelsForTwoSecondsAndHigherLevelsPackAtOnce() {
    assertThat(StasisRules.channelTicks(CHANNEL, 1)).isEqualTo(40);
    assertThat(StasisRules.channelTicks(CHANNEL, 2)).isZero();
    assertThat(StasisRules.channelTicks(CHANNEL, 3)).isZero();
    assertThat(StasisRules.channelTicks(CHANNEL, 7)).isZero();
    assertThat(StasisRules.channelTicks(List.of(1.5D), 4)).isEqualTo(30);
    assertThat(StasisRules.channelTicks(CHANNEL, 0)).isZero();
    assertThat(StasisRules.channelTicks(List.of(), 1)).isZero();
  }

  @Test
  void channelSecondsLoadFiniteAndWithinThirtySeconds() {
    List<Double> loaded = StasisRules.clampChannelSeconds(new ArrayList<>(Arrays.asList(-1D, Double.NaN, 45D, 2.5D, null)));

    assertThat(loaded).containsExactly(0D, 0D, 30D, 2.5D, 0D);
    assertThat(StasisRules.clampChannelSeconds(null)).isEmpty();
  }

  @Test
  void packedGolemsHealOneAmountPerFullIntervalUpToTheirMaximum() {
    assertThat(StasisRules.packedHealth(10D, 100D, 0L, 35 * SECOND, 10, 1D)).isCloseTo(13D, offset(1e-9));
    assertThat(StasisRules.packedHealth(10D, 100D, 0L, 9 * SECOND, 10, 1D)).isCloseTo(10D, offset(1e-9));
    assertThat(StasisRules.packedHealth(95D, 100D, 0L, 600 * SECOND, 10, 1D)).isEqualTo(100D);
    assertThat(StasisRules.packedHealth(10D, 100D, 0L, 3_600 * SECOND, 10, 0D)).isEqualTo(10D);
    assertThat(StasisRules.packedHealth(10D, 100D, 50 * SECOND, 20 * SECOND, 10, 1D)).isEqualTo(10D);
    assertThat(StasisRules.packedHealth(10D, 100D, 0L, 20 * SECOND, 0, 2D)).isCloseTo(50D, offset(1e-9));
  }

  @Test
  void awakeningNeverExceedsTheMaximumAndAnEmptyRecordWakesAtFullHealth() {
    assertThat(StasisRules.packedHealth(150D, 100D, 0L, 0L, 10, 1D)).isEqualTo(100D);
    assertThat(StasisRules.packedHealth(0D, 40D, 0L, 0L, 10, 1D)).isEqualTo(40D);
    assertThat(StasisRules.packedHealth(Double.NaN, 40D, 0L, 0L, 10, 1D)).isEqualTo(40D);
  }

  @Test
  void unlocksNeedTheirLevel() {
    assertThat(StasisRules.unlocked(2, 2)).isTrue();
    assertThat(StasisRules.unlocked(1, 2)).isFalse();
    assertThat(StasisRules.unlocked(3, 0)).isTrue();
    assertThat(StasisRules.unlocked(0, 0)).isFalse();
  }

  @Test
  void anAwakeningIsReadyOnlyWhenEveryCheckPasses() {
    assertThat(StasisRules.awakenVerdict(ready())).isEqualTo(AwakenVerdict.READY);
  }

  @Test
  void theAwakenDecisionTableRefusesInOrder() {
    AwakenCheck ready = ready();

    assertThat(StasisRules.awakenVerdict(with(ready, Field.READABLE, false))).isEqualTo(AwakenVerdict.UNREADABLE);
    assertThat(StasisRules.awakenVerdict(with(ready, Field.OWNER, false))).isEqualTo(AwakenVerdict.NOT_OWNER);
    assertThat(StasisRules.awakenVerdict(with(ready, Field.LEVEL, 0))).isEqualTo(AwakenVerdict.LOCKED);
    assertThat(StasisRules.awakenVerdict(with(ready, Field.ROSTER, false))).isEqualTo(AwakenVerdict.UNAVAILABLE);
    assertThat(StasisRules.awakenVerdict(with(ready, Field.STATUS, GolemRoster.Status.ACTIVE))).isEqualTo(AwakenVerdict.VOID);
    assertThat(StasisRules.awakenVerdict(with(ready, Field.STATUS, null))).isEqualTo(AwakenVerdict.VOID);
    assertThat(StasisRules.awakenVerdict(with(ready, Field.SEALED, false))).isEqualTo(AwakenVerdict.VOID);
    assertThat(StasisRules.awakenVerdict(with(ready, Field.BODY, false))).isEqualTo(AwakenVerdict.BODY_LOCKED);
    assertThat(StasisRules.awakenVerdict(with(ready, Field.BLOCK, GolemRegistry.BindBlock.CORE_NOT_ALLOWED))).isEqualTo(AwakenVerdict.CORE_NOT_ALLOWED);
    assertThat(StasisRules.awakenVerdict(with(ready, Field.BLOCK, GolemRegistry.BindBlock.OWNER_DATA_UNAVAILABLE))).isEqualTo(AwakenVerdict.UNAVAILABLE);
    assertThat(StasisRules.awakenVerdict(with(ready, Field.SPACE, false))).isEqualTo(AwakenVerdict.NO_ROOM);
    assertThat(StasisRules.awakenVerdict(with(ready, Field.BLOCK, GolemRegistry.BindBlock.CHUNK_CAP))).isEqualTo(AwakenVerdict.CHUNK_CAP);
    assertThat(StasisRules.awakenVerdict(with(ready, Field.BLOCK, GolemRegistry.BindBlock.SERVER_CAP))).isEqualTo(AwakenVerdict.SERVER_CAP);
    assertThat(StasisRules.awakenVerdict(with(ready, Field.ALLOWED, false))).isEqualTo(AwakenVerdict.PROTECTED);
  }

  @Test
  void earlierRefusalsWinOverLaterOnes() {
    AwakenCheck released = with(with(ready(), Field.STATUS, null), Field.SPACE, false);
    AwakenCheck foreign = with(with(ready(), Field.OWNER, false), Field.STATUS, null);
    AwakenCheck unlearned = with(with(ready(), Field.LEVEL, 0), Field.STATUS, GolemRoster.Status.ACTIVE);
    AwakenCheck crowded = with(with(ready(), Field.SPACE, false), Field.BLOCK, GolemRegistry.BindBlock.CHUNK_CAP);

    assertThat(StasisRules.awakenVerdict(released)).isEqualTo(AwakenVerdict.VOID);
    assertThat(StasisRules.awakenVerdict(foreign)).isEqualTo(AwakenVerdict.NOT_OWNER);
    assertThat(StasisRules.awakenVerdict(unlearned)).isEqualTo(AwakenVerdict.LOCKED);
    assertThat(StasisRules.awakenVerdict(crowded)).isEqualTo(AwakenVerdict.NO_ROOM);
  }

  @Test
  void recallStartsAPerOwnerCooldownThatOtherOwnersDoNotShare() {
    RecallCooldowns cooldowns = new RecallCooldowns();
    UUID owner = UUID.randomUUID();
    UUID other = UUID.randomUUID();

    assertThat(cooldowns.tryStart(owner, 1_000L, 120 * SECOND)).isTrue();
    assertThat(cooldowns.tryStart(owner, 2_000L, 120 * SECOND)).isFalse();
    assertThat(cooldowns.remainingMillis(owner, 2_000L)).isEqualTo(119 * SECOND);
    assertThat(cooldowns.tryStart(other, 2_000L, 120 * SECOND)).isTrue();
    assertThat(cooldowns.remainingMillis(owner, 121_000L)).isZero();
    assertThat(cooldowns.tryStart(owner, 121_000L, 120 * SECOND)).isTrue();
  }

  @Test
  void aCancelledRecallFreesTheCooldownAtOnce() {
    RecallCooldowns cooldowns = new RecallCooldowns();
    UUID owner = UUID.randomUUID();

    assertThat(cooldowns.tryStart(owner, 1_000L, 120 * SECOND)).isTrue();
    cooldowns.cancel(owner);

    assertThat(cooldowns.remainingMillis(owner, 1_001L)).isZero();
    assertThat(cooldowns.tryStart(owner, 1_001L, 120 * SECOND)).isTrue();
  }

  @Test
  void aZeroCooldownNeverBlocksARecall() {
    RecallCooldowns cooldowns = new RecallCooldowns();
    UUID owner = UUID.randomUUID();

    assertThat(cooldowns.tryStart(owner, 1_000L, 0L)).isTrue();
    assertThat(cooldowns.tryStart(owner, 1_000L, 0L)).isTrue();
    assertThat(cooldowns.remainingMillis(owner, 1_000L)).isZero();
  }

  private static AwakenCheck ready() {
    return new AwakenCheck(true, true, 1, true, GolemRoster.Status.PACKED, true, true, null, true, true);
  }

  private static AwakenCheck with(AwakenCheck base, Field field, Object value) {
    return new AwakenCheck(
        field == Field.READABLE ? (Boolean) value : base.readable(),
        field == Field.OWNER ? (Boolean) value : base.owner(),
        field == Field.LEVEL ? (Integer) value : base.level(),
        field == Field.ROSTER ? (Boolean) value : base.rosterKnown(),
        field == Field.STATUS ? (GolemRoster.Status) value : base.status(),
        field == Field.SEALED ? (Boolean) value : base.sealed(),
        field == Field.BODY ? (Boolean) value : base.bodyUnlocked(),
        field == Field.BLOCK ? (GolemRegistry.BindBlock) value : base.registryBlock(),
        field == Field.SPACE ? (Boolean) value : base.spaceClear(),
        field == Field.ALLOWED ? (Boolean) value : base.allowed());
  }

  private enum Field {
    READABLE,
    OWNER,
    LEVEL,
    ROSTER,
    STATUS,
    SEALED,
    BODY,
    BLOCK,
    SPACE,
    ALLOWED
  }
}
