package art.arcane.adapt.content.adaptation.rift;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RiftConduitLinkTest {
  @Test
  void encodeAndDecodeRoundTripsBlockCoordinates() {
    String encoded = RiftConduit.encodeLocation("minecraft:overworld", 12, -7, 340);
    RiftConduit.ConduitLocation decoded = RiftConduit.decodeLocation(encoded);

    assertThat(decoded).isNotNull();
    assertThat(decoded.worldKey()).isEqualTo("minecraft:overworld");
    assertThat(decoded.x()).isEqualTo(12);
    assertThat(decoded.y()).isEqualTo(-7);
    assertThat(decoded.z()).isEqualTo(340);
  }

  @Test
  void decodePreservesNamespacedWorldKeys() {
    RiftConduit.ConduitLocation decoded = RiftConduit.decodeLocation(
        RiftConduit.encodeLocation("weird:world/name", -1, 64, -2));

    assertThat(decoded).isNotNull();
    assertThat(decoded.worldKey()).isEqualTo("weird:world/name");
    assertThat(decoded.x()).isEqualTo(-1);
    assertThat(decoded.y()).isEqualTo(64);
    assertThat(decoded.z()).isEqualTo(-2);
  }

  @Test
  void decodeRejectsMalformedInput() {
    assertThat(RiftConduit.decodeLocation(null)).isNull();
    assertThat(RiftConduit.decodeLocation("")).isNull();
    assertThat(RiftConduit.decodeLocation("world|1|2")).isNull();
    assertThat(RiftConduit.decodeLocation("world|x|2|3")).isNull();
    assertThat(RiftConduit.decodeLocation("world|1|2|3")).isNull();
    assertThat(RiftConduit.decodeLocation("|1|2|3")).isNull();
  }

  @Test
  void throughputClampsToInclusiveHardBounds() {
    assertThat(RiftConduit.boundedThroughput(-5D)).isEqualTo(1);
    assertThat(RiftConduit.boundedThroughput(0D)).isEqualTo(1);
    assertThat(RiftConduit.boundedThroughput(144D)).isEqualTo(144);
    assertThat(RiftConduit.boundedThroughput(RiftConduit.HARD_MAX_FLOW_ITEMS + 500D))
        .isEqualTo(RiftConduit.HARD_MAX_FLOW_ITEMS);
    assertThat(RiftConduit.boundedThroughput(Double.NaN)).isEqualTo(1);
  }

  @Test
  void rangeClampsToInclusiveHardBounds() {
    assertThat(RiftConduit.boundedRange(-10D)).isEqualTo(1D);
    assertThat(RiftConduit.boundedRange(74D)).isEqualTo(74D);
    assertThat(RiftConduit.boundedRange(RiftConduit.HARD_MAX_RANGE + 1000D))
        .isEqualTo(RiftConduit.HARD_MAX_RANGE);
    assertThat(RiftConduit.boundedRange(Double.POSITIVE_INFINITY)).isEqualTo(1D);
  }

  @Test
  void followUpDeadlineOffsetsNowByTheWindow() {
    assertThat(RiftConduit.followUpDeadline(1000L))
        .isEqualTo(1000L + RiftConduit.CAPTURE_FOLLOWUP_WINDOW_MILLIS);
  }

  @Test
  void followUpWindowRejectsMissingDeadline() {
    assertThat(RiftConduit.isWithinFollowUpWindow(1000L, null)).isFalse();
  }

  @Test
  void followUpWindowHoldsUntilTheInclusiveDeadline() {
    long now = 5000L;
    long deadline = RiftConduit.followUpDeadline(now);
    assertThat(RiftConduit.isWithinFollowUpWindow(now, deadline)).isTrue();
    assertThat(RiftConduit.isWithinFollowUpWindow(deadline, deadline)).isTrue();
  }

  @Test
  void followUpWindowExpiresAfterTheDeadline() {
    long now = 5000L;
    long deadline = RiftConduit.followUpDeadline(now);
    assertThat(RiftConduit.isWithinFollowUpWindow(deadline + 1L, deadline)).isFalse();
  }

  @Test
  void deferredProviderCostMustSettleWhenItReplacesTheDefaultTaglock() {
    assertThat(RiftConduit.acceptSettlement(true, false, false)).isTrue();
    assertThat(RiftConduit.acceptSettlement(false, false, false)).isTrue();
    assertThat(RiftConduit.acceptSettlement(false, true, true)).isTrue();
    assertThat(RiftConduit.acceptSettlement(false, true, false)).isFalse();
  }

  @Test
  void bindFinalizationRequiresCurrentOnlineLearnedState() {
    assertThat(RiftConduit.canFinalizeBind(true, true, true)).isTrue();
    assertThat(RiftConduit.canFinalizeBind(false, true, true)).isFalse();
    assertThat(RiftConduit.canFinalizeBind(true, false, true)).isFalse();
    assertThat(RiftConduit.canFinalizeBind(true, true, false)).isFalse();
  }

  @Test
  void rollbackOnlyRestoresTheLinkWrittenByItsOperation() {
    assertThat(RiftConduit.shouldRestoreEndpoint("operation", "operation")).isTrue();
    assertThat(RiftConduit.shouldRestoreEndpoint("newer", "operation")).isFalse();
    assertThat(RiftConduit.shouldRestoreEndpoint(null, "operation")).isFalse();
    assertThat(RiftConduit.shouldRestoreEndpoint("operation", null)).isFalse();
  }
}
