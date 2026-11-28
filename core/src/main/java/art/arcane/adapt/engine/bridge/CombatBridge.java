package art.arcane.adapt.engine.bridge;

import art.arcane.adapt.api.internal.CombatRuntime;
import art.arcane.adapt.api.internal.MinionKinds;
import art.arcane.adapt.engine.adaptation.AbilityDamage;
import art.arcane.adapt.engine.minion.MinionBurden;
import art.arcane.adapt.engine.minion.MinionIdentity;
import art.arcane.adapt.api.ability.MinionKind;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/** {@link CombatRuntime} over the engine's ability damage guard, minion identity and minion burden. */
final class CombatBridge implements CombatRuntime {
  /** The minion kinds other plugins register through the API, as one engine kind. */
  private static final MinionKind REGISTERED = new MinionKind() {
    @Override
    public boolean matches(EntityRef entity) {
      return MinionKinds.kindOf(entity) != null;
    }

    @Override
    public UUID ownerOf(EntityRef entity) {
      return MinionKinds.ownerOf(entity);
    }
  };

  CombatBridge() {
    MinionIdentity.registerKind(REGISTERED);
  }

  @Override
  public void dealAbilityDamage(LivingRef target, double amount, EntityRef source) {
    AbilityDamage.deal(target, amount, source);
  }

  @Override
  public boolean dealingAbilityDamage() {
    return AbilityDamage.dealing();
  }

  @Override
  public void fixHit(long dispatchId) {
    AbilityDamage.fix(dispatchId);
  }

  @Override
  public boolean hitFixed(long dispatchId) {
    return AbilityDamage.fixed(dispatchId);
  }

  @Override
  public boolean isMinion(EntityRef entity) {
    return MinionIdentity.isMinion(entity);
  }

  @Override
  public @Nullable UUID minionOwner(EntityRef entity) {
    return MinionIdentity.ownerOf(entity);
  }

  @Override
  public void setMinionBurden(double healthPerMinion, double minimumOwnerMaxHealth) {
    MinionBurden.get().configure(healthPerMinion, minimumOwnerMaxHealth);
  }

  @Override
  public void burden(PlayerRef owner, LivingRef minion) {
    MinionBurden.get().register(owner, minion);
  }

  @Override
  public void releaseBurden(UUID owner, EntityRef minion) {
    MinionBurden.get().unregister(owner, minion);
  }

  @Override
  public void holdBurden(UUID owner, Set<UUID> minions) {
    MinionBurden.get().hold(owner, minions);
  }
}
