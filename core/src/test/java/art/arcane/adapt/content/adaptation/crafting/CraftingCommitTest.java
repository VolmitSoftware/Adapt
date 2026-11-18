package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
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
    CraftingProvisioner.PendingPortions portions = new CraftingProvisioner.PendingPortions(event, Id.minecraft("bread"), 3);
    pending.put(playerId, portions);

    assertThat(CraftingProvisioner.committedPortions(pending, playerId, stack(Id.minecraft("bread"), 1))).isSameAs(portions);
    assertThat(CraftingProvisioner.committedPortions(pending, playerId, stack(Id.minecraft("bread"), 1))).isNull();
  }

  @Test
  void cancelledMissingAndMismatchedFoodNeverAwardsPortions() {
    UUID playerId = UUID.randomUUID();
    CraftItemEvent event = mock(CraftItemEvent.class);
    Map<UUID, CraftingProvisioner.PendingPortions> pending = new HashMap<>();
    CraftingProvisioner.PendingPortions portions = new CraftingProvisioner.PendingPortions(event, Id.minecraft("bread"), 3);
    pending.put(playerId, portions);
    assertThat(CraftingProvisioner.committedPortions(pending, playerId, null)).isNull();
    pending.put(playerId, portions);
    assertThat(CraftingProvisioner.committedPortions(pending, playerId, stack(Id.minecraft("bread"), 0))).isNull();
    pending.put(playerId, portions);
    assertThat(CraftingProvisioner.committedPortions(pending, playerId, stack(Id.minecraft("apple"), 1))).isNull();
    pending.put(playerId, portions);
    when(event.dispatchCancelled()).thenReturn(true);
    assertThat(CraftingProvisioner.committedPortions(pending, playerId, stack(Id.minecraft("bread"), 1))).isNull();
  }

  @Test
  void aCraftCancelledAfterAdaptsMonitorHandlerStillPaysNothing() {
    UUID playerId = UUID.randomUUID();
    CraftItemEvent event = mock(CraftItemEvent.class, CALLS_REAL_METHODS);
    boolean[] nativeCancelled = {false};
    event.setDispatchCancelled(() -> nativeCancelled[0]);
    Map<UUID, CraftingProvisioner.PendingPortions> portions = new HashMap<>();
    portions.put(playerId, new CraftingProvisioner.PendingPortions(event, Id.minecraft("bread"), 3));
    Map<UUID, CraftingBulkArtisan.PendingBatch> batches = new HashMap<>();
    batches.put(playerId, new CraftingBulkArtisan.PendingBatch(event, List.of(), Id.minecraft("emerald"), 1, 32, true));

    assertThat(event.isCancelled()).isFalse();
    nativeCancelled[0] = true;

    assertThat(CraftingProvisioner.committedPortions(portions, playerId, stack(Id.minecraft("bread"), 1))).isNull();
    assertThat(CraftingBulkArtisan.committedBatch(batches, playerId, stack(Id.minecraft("emerald"), 1))).isNull();
  }

  @Test
  void bulkBatchRequiresOneMatchingCommittedResult() {
    UUID playerId = UUID.randomUUID();
    CraftItemEvent event = mock(CraftItemEvent.class);
    Map<UUID, CraftingBulkArtisan.PendingBatch> pending = new HashMap<>();
    CraftingBulkArtisan.PendingBatch batch = new CraftingBulkArtisan.PendingBatch(event, List.of(), Id.minecraft("emerald"), 1, 32, true);
    pending.put(playerId, batch);
    assertThat(CraftingBulkArtisan.committedBatch(pending, playerId, stack(Id.minecraft("emerald"), 1))).isSameAs(batch);
    assertThat(CraftingBulkArtisan.committedBatch(pending, playerId, stack(Id.minecraft("emerald"), 1))).isNull();
    pending.put(playerId, batch);
    when(event.dispatchCancelled()).thenReturn(true);
    assertThat(CraftingBulkArtisan.committedBatch(pending, playerId, stack(Id.minecraft("emerald"), 1))).isNull();
  }

  @Test
  void resourceConversionStillCommitsItsBatchWithoutXpOrMilestoneCredit() {
    UUID playerId = UUID.randomUUID();
    CraftItemEvent event = mock(CraftItemEvent.class);
    CraftingBulkArtisan.PendingBatch batch = new CraftingBulkArtisan.PendingBatch(
        event, List.of(), Id.minecraft("iron_ingot"), 9, 32, false);
    Map<UUID, CraftingBulkArtisan.PendingBatch> pending = new HashMap<>();
    pending.put(playerId, batch);
    assertThat(CraftingBulkArtisan.committedBatch(pending, playerId, stack(Id.minecraft("iron_ingot"), 9))).isSameAs(batch);
    assertThat(batch.cap()).isEqualTo(32);

    PlayerRef player = mock(PlayerRef.class);
    TestBulkArtisan adaptation = mock(TestBulkArtisan.class);
    doCallRealMethod().when(adaptation).rewardBatch(player, batch, 27);
    adaptation.rewardBatch(player, batch, 27);
    verify(adaptation, never()).addStat(any(PlayerRef.class), anyString(), anyDouble());
    verify(adaptation, never()).xp(any(PlayerRef.class), anyDouble());
  }

  private static ItemRef stack(Id material, int amount) {
    return new FakeItem(material, amount);
  }

  private static class TestBulkArtisan extends CraftingBulkArtisan {
    @Override
    public void addStat(PlayerRef player, String stat, double amount) {
      super.addStat(player, stat, amount);
    }
  }
}
