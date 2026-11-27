package art.arcane.adapt.api.internal;

import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** What the engine does for XP bookkeeping, per-player state and the platform checks content runs. */
@ApiStatus.Internal
public interface ProgressRuntime {
  boolean usable(Collection<PlatformNeed> needs);

  Id itemOr(Id preferred, Id fallback);

  String materialOr(String preferred, String fallback);

  double adjacencyBonusMultiplier(PlayerRef player, BlockRef placed);

  double fieldCycleMultiplier(PlayerRef player, BlockRef crop);

  double claimCombat(String ledger, PlayerRef player, UUID victim, double damage, double healthBudget);

  double claimTransfer(PlayerRef player, String material, int amount, int stackSize, long windowMillis);

  <V> Map<UUID, V> playerState();
}
