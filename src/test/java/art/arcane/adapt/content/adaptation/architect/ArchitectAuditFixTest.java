package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.AdaptTestBase;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArchitectAuditFixTest extends AdaptTestBase {
  private static final Path DEMOLITION_SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/architect/ArchitectDemolition.java"
  );

  @BeforeEach
  void configurePluginName() {
    lenient().when(plugin.getName()).thenReturn("Adapt");
    lenient().when(plugin.namespace()).thenReturn("adapt");
  }

  @Test
  void demolitionClampsInvalidTrackingCaps() {
    assertThat(ArchitectDemolition.trackingCap(-20)).isZero();
    assertThat(ArchitectDemolition.trackingCap(0)).isZero();
    assertThat(ArchitectDemolition.trackingCap(64)).isEqualTo(64);
  }

  @Test
  void demolitionActsAsAnUndoWithoutBlockOrExperienceDrops() throws IOException {
    String source = Files.readString(DEMOLITION_SOURCE);
    int start = source.indexOf("public void on(BlockBreakEvent e)");
    int end = source.indexOf("@EventHandler", start + 1);
    String handler = source.substring(start, end);

    assertThat(handler)
        .contains("e.setDropItems(false)", "e.setExpToDrop(0)")
        .doesNotContain("getDrops(", "dropItemNaturally(");
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
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    ItemStack emptyHand = mock(ItemStack.class);
    UUID playerId = UUID.randomUUID();
    when(player.isOnline()).thenReturn(true);
    when(player.getUniqueId()).thenReturn(playerId);
    when(player.getInventory()).thenReturn(inventory);
    when(inventory.getItemInMainHand()).thenReturn(emptyHand);
    when(emptyHand.getType()).thenReturn(Material.AIR);
    when(inventory.getStorageContents()).thenReturn(new ItemStack[0]);

    Method refill = ArchitectSupplyLine.class.getDeclaredMethod(
        "refill",
        Player.class,
        EquipmentSlot.class,
        Material.class,
        int.class
    );
    refill.setAccessible(true);
    refill.invoke(adaptation, player, EquipmentSlot.HAND, Material.STONE, 4);

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
