package art.arcane.adapt.api.internal;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/** What the engine does for ability damage, minion ownership and the health minions cost. */
@ApiStatus.Internal
public interface CombatRuntime {
  void dealAbilityDamage(LivingRef target, double amount, EntityRef source);

  boolean dealingAbilityDamage();

  void fixHit(long dispatchId);

  boolean hitFixed(long dispatchId);

  boolean isMinion(EntityRef entity);

  @Nullable UUID minionOwner(EntityRef entity);

  void setMinionBurden(double healthPerMinion, double minimumOwnerMaxHealth);

  void burden(PlayerRef owner, LivingRef minion);

  void releaseBurden(UUID owner, EntityRef minion);

  void holdBurden(UUID owner, Set<UUID> minions);
}
