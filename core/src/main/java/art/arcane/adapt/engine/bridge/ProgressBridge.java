package art.arcane.adapt.engine.bridge;

import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.internal.ProgressRuntime;
import art.arcane.adapt.engine.adaptation.PlayerStateRegistry;
import art.arcane.adapt.engine.support.PlatformSupport;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerDataAccess;
import art.arcane.adapt.engine.xp.XpNovelty;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** {@link ProgressRuntime} over the engine's platform checks, XP novelty state and the players' XP ledgers. */
final class ProgressBridge implements ProgressRuntime {
  @Override
  public boolean usable(Collection<PlatformNeed> needs) {
    return PlatformSupport.check(needs).usable();
  }

  @Override
  public Id itemOr(Id preferred, Id fallback) {
    return PlatformSupport.itemOr(preferred, fallback);
  }

  @Override
  public String materialOr(String preferred, String fallback) {
    return PlatformSupport.materialOr(preferred, fallback);
  }

  @Override
  public double adjacencyBonusMultiplier(PlayerRef player, BlockRef placed) {
    return XpNovelty.adjacencyBonusMultiplier(player, placed);
  }

  @Override
  public double fieldCycleMultiplier(PlayerRef player, BlockRef crop) {
    return XpNovelty.fieldCycleMultiplier(player, crop);
  }

  @Override
  public double claimCombat(String ledger, PlayerRef player, UUID victim, double damage, double healthBudget) {
    PlayerData data = PlayerDataAccess.data(player);
    return data == null ? 0D
        : data.getCombatXp().claim(ledger, victim, damage, healthBudget, System.currentTimeMillis());
  }

  @Override
  public double claimTransfer(PlayerRef player, String material, int amount, int stackSize, long windowMillis) {
    PlayerData data = PlayerDataAccess.data(player);
    return data == null ? 0D
        : data.getItemTransferXp().claim(material, amount, stackSize, windowMillis, System.currentTimeMillis());
  }

  @Override
  public <V> Map<UUID, V> playerState() {
    return PlayerStateRegistry.newPlayerMap();
  }
}
