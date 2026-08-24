package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.advancement.AdvancementManager;
import art.arcane.adapt.util.common.io.SQLManager;
import art.arcane.adapt.util.project.redis.RedisSync;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdaptPlayerTransferTest extends AdaptTestBase {
  @Test
  void finalTransferSnapshotStripsTransientGrantsAndRetiresTheOldFence() {
    UUID playerId = UUID.randomUUID();
    UUID ownerToken = UUID.randomUUID();
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(playerId);
    when(plugin.getManager()).thenReturn(mock(AdvancementManager.class));

    PlayerData data = new PlayerData();
    data.getStats().put("transfer-proof", 37D);
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine("transfer");
    line.getAdaptations().put("transfer-owned", adaptation("transfer-owned", false));
    line.getAdaptations().put("transfer-region", adaptation("transfer-region", true));
    data.getSkillLines().put("transfer", line);
    data.setRegionGrantedCount(1);
    data.setRegionPowerBonus(4);

    AdaptPlayer adaptPlayer = new AdaptPlayer(
        player, LoadedPlayerData.owned(data, ownerToken, 7L, 5L));

    assertThat(adaptPlayer.retireForTransfer(UUID.randomUUID(), 7L)).isNull();
    assertThat(adaptPlayer.isRuntimeReady()).isTrue();

    FencedPlayerSnapshot snapshot = adaptPlayer.retireForTransfer(ownerToken, 7L);

    assertThat(snapshot).isNotNull();
    assertThat(snapshot.ownerToken()).isEqualTo(ownerToken);
    assertThat(snapshot.epoch()).isEqualTo(7L);
    assertThat(snapshot.sequence()).isEqualTo(6L);
    assertThat(adaptPlayer.isRuntimeReady()).isFalse();
    assertThat(adaptPlayer.persistenceFenceEpoch()).isEqualTo(-1L);
    assertThat(adaptPlayer.captureFencedSnapshot()).isNull();
    assertThat(adaptPlayer.retireForTransfer(ownerToken, 7L)).isNull();

    PlayerData restored = PlayerData.fromJson(snapshot.json());
    assertThat(restored).isNotNull();
    assertThat(restored.getStat("transfer-proof")).isEqualTo(37D);
    assertThat(restored.getSkillLines().get("transfer").getAdaptations())
        .containsOnlyKeys("transfer-owned");
    assertThat(restored.getRegionGrantedCount()).isZero();
    assertThat(restored.getRegionPowerBonus()).isZero();
  }

  @Test
  void failedDurableTransferReadPreventsCommittedSqlFallback() {
    UUID playerId = UUID.randomUUID();
    UUID ownerToken = UUID.randomUUID();
    RedisSync redisSync = mock(RedisSync.class);
    SQLManager.SqlToken predecessor = new SQLManager.SqlToken(ownerToken, 7L);
    when(plugin.getRedisSync()).thenReturn(redisSync);
    when(redisSync.awaitTransfers(playerId, ownerToken, 7L, 25L))
        .thenReturn(CompletableFuture.failedFuture(
            new IllegalStateException("staging read failed")));

    assertThatThrownBy(() -> AdaptPlayer.collectRedisTransfer(
        new ArrayList<>(), playerId, predecessor, 25L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("could not be verified");
  }

  @Test
  void successfulAdoptionAcknowledgesTheExactPredecessorFence() {
    UUID playerId = UUID.randomUUID();
    UUID ownerToken = UUID.randomUUID();
    RedisSync redisSync = mock(RedisSync.class);
    when(plugin.getRedisSync()).thenReturn(redisSync);

    AdaptPlayer.acknowledgeAdoptedTransfer(playerId, ownerToken, 9L);

    verify(redisSync).acknowledgeTransfer(playerId, ownerToken, 9L);
  }

  @Test
  void remoteFenceAdvanceRetiresWithoutAdoptingTheResetCredential() {
    UUID playerId = UUID.randomUUID();
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(playerId);
    when(plugin.getManager()).thenReturn(mock(AdvancementManager.class));
    AdaptPlayer adaptPlayer = new AdaptPlayer(
        player,
        LoadedPlayerData.owned(new PlayerData(), UUID.randomUUID(), 7L, 4L)
    );

    assertThat(adaptPlayer.retireForRemoteFenceAdvance(7L)).isFalse();
    assertThat(adaptPlayer.isRuntimeReady()).isTrue();

    assertThat(adaptPlayer.retireForRemoteFenceAdvance(8L)).isTrue();
    assertThat(adaptPlayer.isRuntimeReady()).isFalse();
    assertThat(adaptPlayer.persistenceFenceEpoch()).isEqualTo(-1L);
    assertThat(adaptPlayer.captureFencedSnapshot()).isNull();
  }

  @Test
  void rejectedOwnerDispatchCanInvalidateTheExactFenceWithoutCapturingData() {
    UUID playerId = UUID.randomUUID();
    UUID ownerToken = UUID.randomUUID();
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(playerId);
    when(plugin.getManager()).thenReturn(mock(AdvancementManager.class));
    AdaptPlayer adaptPlayer = new AdaptPlayer(
        player,
        LoadedPlayerData.owned(new PlayerData(), ownerToken, 11L, 4L)
    );

    assertThat(adaptPlayer.invalidatePersistenceFence(UUID.randomUUID(), 11L)).isFalse();
    assertThat(adaptPlayer.invalidatePersistenceFence(ownerToken, 11L)).isTrue();
    assertThat(adaptPlayer.persistenceFenceEpoch()).isEqualTo(-1L);
    assertThat(adaptPlayer.captureFencedSnapshot()).isNull();
  }

  private static PlayerAdaptation adaptation(String id, boolean regionGranted) {
    PlayerAdaptation adaptation = new PlayerAdaptation();
    adaptation.setId(id);
    adaptation.setLevel(1);
    adaptation.setRegionGranted(regionGranted);
    return adaptation;
  }
}
