package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArchitectAuditFixTest extends AdaptTestBase {
  @Test
  void demolitionClampsInvalidTrackingCaps() {
    assertThat(ArchitectDemolition.trackingCap(-20)).isZero();
    assertThat(ArchitectDemolition.trackingCap(0)).isZero();
    assertThat(ArchitectDemolition.trackingCap(64)).isEqualTo(64);
  }

  @Test
  void elevatorTreatsMaximumHeightAsExclusive() {
    assertThat(ArchitectElevator.upwardScanDistance(319, 320, 32)).isZero();
    assertThat(ArchitectElevator.upwardScanDistance(318, 320, 32)).isEqualTo(1);
    assertThat(ArchitectElevator.upwardScanDistance(300, 320, 8)).isEqualTo(8);
    assertThat(ArchitectElevator.isWithinBuildHeight(-64, -64, 320)).isTrue();
    assertThat(ArchitectElevator.isWithinBuildHeight(319, -64, 320)).isTrue();
    assertThat(ArchitectElevator.isWithinBuildHeight(320, -64, 320)).isFalse();
  }

  @Test
  void elevatorRewardsOnlyACompletedSuccessfulTeleport() {
    assertThat(ArchitectElevator.shouldRewardTeleport(true, null, true)).isTrue();
    assertThat(ArchitectElevator.shouldRewardTeleport(false, null, true)).isFalse();
    assertThat(ArchitectElevator.shouldRewardTeleport(null, null, true)).isFalse();
    assertThat(ArchitectElevator.shouldRewardTeleport(
        true,
        new IllegalStateException("teleport failed"),
        true
    )).isFalse();
    assertThat(ArchitectElevator.shouldRewardTeleport(true, null, false)).isFalse();
  }

  @Test
  void supplyLineWindowOnlyAdvancesForSuccessfulRefills() {
    long now = 120000L;
    ArchitectSupplyLine.RefillWindow full = new ArchitectSupplyLine.RefillWindow(now - 1000L, 4);

    assertThat(ArchitectSupplyLine.hasRefillCapacity(null, now, 4)).isTrue();
    assertThat(ArchitectSupplyLine.hasRefillCapacity(full, now, 4)).isFalse();
    assertThat(ArchitectSupplyLine.hasRefillCapacity(full, now + 60000L, 4)).isTrue();
    assertThat(ArchitectSupplyLine.nextSuccessfulWindow(null, now))
        .isEqualTo(new ArchitectSupplyLine.RefillWindow(now, 1));
    assertThat(ArchitectSupplyLine.nextSuccessfulWindow(
        new ArchitectSupplyLine.RefillWindow(now - 1000L, 2), now))
        .isEqualTo(new ArchitectSupplyLine.RefillWindow(now - 1000L, 3));
  }

  @Test
  void failedSupplyLookupDoesNotConsumeQuota() throws Exception {
    ArchitectSupplyLine adaptation = new ArchitectSupplyLine();
    PlayerRef player = mock(PlayerRef.class);
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    ItemRef emptyHand = mock(ItemRef.class);
    ItemRef template = mock(ItemRef.class);
    UUID playerId = UUID.randomUUID();
    when(player.isOnline()).thenReturn(true);
    when(player.uuid()).thenReturn(playerId);
    when(player.inventory()).thenReturn(inventory);
    when(inventory.mainHand()).thenReturn(emptyHand);
    when(emptyHand.type()).thenReturn(Id.minecraft("air"));
    when(template.type()).thenReturn(Id.minecraft("stone"));

    Method refill = ArchitectSupplyLine.class.getDeclaredMethod(
        "refill",
        PlayerRef.class,
        Hand.class,
        ItemRef.class,
        int.class
    );
    refill.setAccessible(true);
    refill.invoke(adaptation, player, Hand.MAIN_HAND, template, 4);

    assertThat(refillWindows(adaptation)).doesNotContainKey(playerId);
  }

  @SuppressWarnings("unchecked")
  private static Map<UUID, ArchitectSupplyLine.RefillWindow> refillWindows(
      ArchitectSupplyLine adaptation
  ) throws Exception {
    Field field = ArchitectSupplyLine.class.getDeclaredField("windows");
    field.setAccessible(true);
    return (Map<UUID, ArchitectSupplyLine.RefillWindow>) field.get(adaptation);
  }
}
