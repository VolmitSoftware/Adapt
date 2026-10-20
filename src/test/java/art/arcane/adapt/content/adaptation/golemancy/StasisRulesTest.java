package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRoster;
import org.bukkit.Material;
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
  void boneGolemsAreNeverPackedWhileEveryOtherBodyIs() {
    assertThat(StasisRules.packable(GolemBody.BONE)).isFalse();
    assertThat(StasisRules.packable(GolemBody.FROST)).isTrue();
    assertThat(StasisRules.packable(GolemBody.COPPER)).isTrue();
    assertThat(StasisRules.packable(GolemBody.IRON)).isTrue();
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
    assertThat(GolemancyRules.unlocked(2, 2)).isTrue();
    assertThat(GolemancyRules.unlocked(1, 2)).isFalse();
    assertThat(GolemancyRules.unlocked(3, 0)).isTrue();
    assertThat(GolemancyRules.unlocked(0, 0)).isFalse();
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
    assertThat(StasisRules.awakenVerdict(with(ready, Field.WORLD, false))).isEqualTo(AwakenVerdict.PROTECTED);
  }

  @Test
  void aSkippedWorldNamesTheWorldBeforeTheMissingLevel() {
    AwakenCheck blacklisted = with(with(ready(), Field.WORLD, false), Field.LEVEL, 0);

    assertThat(StasisRules.awakenVerdict(blacklisted)).isEqualTo(AwakenVerdict.PROTECTED);
    assertThat(StasisRules.awakenVerdict(with(blacklisted, Field.OWNER, false))).isEqualTo(AwakenVerdict.NOT_OWNER);
  }

  @Test
  void lavaAndFireAreNeverOpenAndWaterIsClosedOnlyToFrostGolems() {
    for (Material burning : List.of(Material.LAVA, Material.FIRE, Material.SOUL_FIRE)) {
      for (GolemBody body : List.of(GolemBody.FROST, GolemBody.COPPER, GolemBody.IRON)) {
        assertThat(StasisRules.awakenVerdict(with(with(ready(), Field.REPLACED, burning), Field.BODY_TYPE, body)))
            .as("%s for a %s golem", burning, body).isEqualTo(AwakenVerdict.NO_ROOM);
      }
    }
    for (Material wet : List.of(Material.WATER, Material.BUBBLE_COLUMN, Material.SEAGRASS, Material.KELP_PLANT)) {
      assertThat(StasisRules.awakenVerdict(with(with(ready(), Field.REPLACED, wet), Field.BODY_TYPE, GolemBody.FROST)))
          .as("%s for a frost golem", wet).isEqualTo(AwakenVerdict.NO_ROOM);
      assertThat(StasisRules.awakenVerdict(with(with(ready(), Field.REPLACED, wet), Field.BODY_TYPE, GolemBody.IRON))).isEqualTo(AwakenVerdict.READY);
      assertThat(StasisRules.awakenVerdict(with(with(ready(), Field.REPLACED, wet), Field.BODY_TYPE, GolemBody.COPPER))).isEqualTo(AwakenVerdict.READY);
    }
    AwakenCheck waterlogged = with(with(ready(), Field.WATERLOGGED, true), Field.BODY_TYPE, GolemBody.FROST);
    assertThat(StasisRules.awakenVerdict(waterlogged)).isEqualTo(AwakenVerdict.NO_ROOM);
    assertThat(StasisRules.awakenVerdict(with(waterlogged, Field.BODY_TYPE, GolemBody.IRON))).isEqualTo(AwakenVerdict.READY);
    assertThat(StasisRules.awakenVerdict(with(with(ready(), Field.REPLACED, Material.SHORT_GRASS), Field.BODY_TYPE, GolemBody.FROST)))
        .isEqualTo(AwakenVerdict.READY);
    assertThat(StasisRules.awakenVerdict(with(ready(), Field.REPLACED, null))).isEqualTo(AwakenVerdict.READY);
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
    return new AwakenCheck(true, true, true, 1, true, GolemRoster.Status.PACKED, true, true, null, true, Material.AIR, false, GolemBody.IRON, true);
  }

  private static AwakenCheck with(AwakenCheck base, Field field, Object value) {
    return new AwakenCheck(
        field == Field.READABLE ? (Boolean) value : base.readable(),
        field == Field.OWNER ? (Boolean) value : base.owner(),
        field == Field.WORLD ? (Boolean) value : base.worldEnabled(),
        field == Field.LEVEL ? (Integer) value : base.level(),
        field == Field.ROSTER ? (Boolean) value : base.rosterKnown(),
        field == Field.STATUS ? (GolemRoster.Status) value : base.status(),
        field == Field.SEALED ? (Boolean) value : base.sealed(),
        field == Field.BODY ? (Boolean) value : base.bodyUnlocked(),
        field == Field.BLOCK ? (GolemRegistry.BindBlock) value : base.registryBlock(),
        field == Field.SPACE ? (Boolean) value : base.spaceClear(),
        field == Field.REPLACED ? (Material) value : base.replaced(),
        field == Field.WATERLOGGED ? (Boolean) value : base.waterlogged(),
        field == Field.BODY_TYPE ? (GolemBody) value : base.body(),
        field == Field.ALLOWED ? (Boolean) value : base.allowed());
  }

  private enum Field {
    READABLE,
    OWNER,
    WORLD,
    LEVEL,
    ROSTER,
    STATUS,
    SEALED,
    BODY,
    BLOCK,
    SPACE,
    REPLACED,
    WATERLOGGED,
    BODY_TYPE,
    ALLOWED
  }
}
