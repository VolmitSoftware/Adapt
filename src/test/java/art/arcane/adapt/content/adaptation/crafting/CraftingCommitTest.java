package art.arcane.adapt.content.adaptation.crafting;

import org.bukkit.Material;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
    CraftingBulkArtisan.PendingBatch batch = new CraftingBulkArtisan.PendingBatch(event, List.of(), Material.EMERALD, 1, 32);
    pending.put(playerId, batch);
    assertThat(CraftingBulkArtisan.committedBatch(pending, playerId, stack(Material.EMERALD, 1))).isSameAs(batch);
    assertThat(CraftingBulkArtisan.committedBatch(pending, playerId, stack(Material.EMERALD, 1))).isNull();
    pending.put(playerId, batch);
    when(event.isCancelled()).thenReturn(true);
    assertThat(CraftingBulkArtisan.committedBatch(pending, playerId, stack(Material.EMERALD, 1))).isNull();
  }

  private static ItemStack stack(Material material, int amount) {
    ItemStack stack = mock(ItemStack.class);
    when(stack.getType()).thenReturn(material);
    when(stack.getAmount()).thenReturn(amount);
    return stack;
  }
}
