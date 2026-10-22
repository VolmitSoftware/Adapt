package art.arcane.adapt.content.adaptation.crafting;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

class CraftingCommitTest {
  @Test
  void provisionerAwardsOnceOnlyForMatchingCommittedFood() {
    UUID playerId = UUID.randomUUID();
    CraftItemEvent event = mock(CraftItemEvent.class);
    Map<UUID, CraftingProvisioner.PendingPortions> pending = new HashMap<>();
    CraftingProvisioner.PendingPortions portions = new CraftingProvisioner.PendingPortions(event, Material.BREAD, 3);
    pending.put(playerId, portions);

    assertThat(CraftingProvisioner.committedPortions(pending, playerId, stack(Material.BREAD, 1))).isSameAs(portions);
    assertThat(CraftingProvisioner.committedPortions(pending, playerId, stack(Material.BREAD, 1))).isNull();
  }

  @Test
  void cancelledMissingAndMismatchedFoodNeverAwardsPortions() {
    UUID playerId = UUID.randomUUID();
    CraftItemEvent event = mock(CraftItemEvent.class);
    Map<UUID, CraftingProvisioner.PendingPortions> pending = new HashMap<>();
    CraftingProvisioner.PendingPortions portions = new CraftingProvisioner.PendingPortions(event, Material.BREAD, 3);
    pending.put(playerId, portions);
    assertThat(CraftingProvisioner.committedPortions(pending, playerId, null)).isNull();
    pending.put(playerId, portions);
    assertThat(CraftingProvisioner.committedPortions(pending, playerId, stack(Material.BREAD, 0))).isNull();
    pending.put(playerId, portions);
    assertThat(CraftingProvisioner.committedPortions(pending, playerId, stack(Material.APPLE, 1))).isNull();
    pending.put(playerId, portions);
    when(event.isCancelled()).thenReturn(true);
    assertThat(CraftingProvisioner.committedPortions(pending, playerId, stack(Material.BREAD, 1))).isNull();
  }

  @Test
  void bulkBatchRequiresOneMatchingCommittedResult() {
    UUID playerId = UUID.randomUUID();
    CraftItemEvent event = mock(CraftItemEvent.class);
    Map<UUID, CraftingBulkArtisan.PendingBatch> pending = new HashMap<>();
    CraftingBulkArtisan.PendingBatch batch = new CraftingBulkArtisan.PendingBatch(event, List.of(), Material.EMERALD, 1, 32, true);
    pending.put(playerId, batch);
    assertThat(CraftingBulkArtisan.committedBatch(pending, playerId, stack(Material.EMERALD, 1))).isSameAs(batch);
    assertThat(CraftingBulkArtisan.committedBatch(pending, playerId, stack(Material.EMERALD, 1))).isNull();
    pending.put(playerId, batch);
    when(event.isCancelled()).thenReturn(true);
    assertThat(CraftingBulkArtisan.committedBatch(pending, playerId, stack(Material.EMERALD, 1))).isNull();
  }

  @Test
  void resourceConversionStillCommitsItsBatchWithoutXpOrMilestoneCredit() {
    UUID playerId = UUID.randomUUID();
    CraftItemEvent event = mock(CraftItemEvent.class);
    CraftingBulkArtisan.PendingBatch batch = new CraftingBulkArtisan.PendingBatch(
        event, List.of(), Material.IRON_INGOT, 9, 32, false);
    Map<UUID, CraftingBulkArtisan.PendingBatch> pending = new HashMap<>();
    pending.put(playerId, batch);
    assertThat(CraftingBulkArtisan.committedBatch(pending, playerId, stack(Material.IRON_INGOT, 9))).isSameAs(batch);
    assertThat(batch.cap()).isEqualTo(32);

    Player player = mock(Player.class);
    TestBulkArtisan adaptation = mock(TestBulkArtisan.class);
    doCallRealMethod().when(adaptation).rewardBatch(player, batch, 27);
    adaptation.rewardBatch(player, batch, 27);
    verify(adaptation, never()).addStat(any(Player.class), anyString(), anyDouble());
    verify(adaptation, never()).xp(any(Player.class), anyDouble());
  }

  private static ItemStack stack(Material material, int amount) {
    ItemStack stack = mock(ItemStack.class);
    when(stack.getType()).thenReturn(material);
    when(stack.getAmount()).thenReturn(amount);
    return stack;
  }

  private static class TestBulkArtisan extends CraftingBulkArtisan {
    @Override
    public void addStat(Player player, String stat, double amount) {
      super.addStat(player, stat, amount);
    }
  }
}
