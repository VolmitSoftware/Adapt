package art.arcane.adapt.api.ability;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.internal.MinionKinds;
import art.arcane.adapt.api.service.ServiceRegistration;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Entities that fight or work for a player. Every adaptation treats a minion as friendly and pays no kill reward for
 * it, Adapt's own minions and those of every registered {@link MinionKind} alike.
 *
 * <p>{@link #register} works at any time and lasts until withdrawn, across core restarts; withdraw it when your
 * plugin disables. The queries need a running core.
 */
@ApiStatus.Experimental
public final class Minions {
  private Minions() {
  }

  /** Adds a kind of minion; withdraw the returned registration to remove it. */
  public static ServiceRegistration register(MinionKind kind) {
    return MinionKinds.register(kind);
  }

  /** Whether {@code entity} is a minion of Adapt's or of any registered kind. */
  public static boolean isMinion(EntityRef entity) {
    return Engine.bridge().combat().isMinion(Objects.requireNonNull(entity, "entity"));
  }

  /** The player the minion belongs to; empty for anything that is not an owned minion. */
  public static Optional<UUID> ownerOf(EntityRef entity) {
    return Optional.ofNullable(Engine.bridge().combat().minionOwner(Objects.requireNonNull(entity, "entity")));
  }

  /** True when both are minions of the same player. */
  public static boolean sameOwner(EntityRef a, EntityRef b) {
    Optional<UUID> owner = ownerOf(a);
    return owner.isPresent() && owner.equals(ownerOf(b));
  }

  /**
   * Whether a minion of {@code owner} may attack {@code target} under {@code policy}'s rules: never the owner, any
   * minion, the owner's pets or anything else {@link Adaptation#canDamageTarget} refuses for the owner.
   */
  public static boolean mayAttack(Adaptation<?> policy, PlayerRef owner, LivingRef target) {
    Objects.requireNonNull(policy, "policy");
    if (owner.uuid().equals(target.uuid()) || !target.isValid() || target.isDead() || isMinion(target)) {
      return false;
    }
    return policy.canDamageTarget(owner, target);
  }

  /**
   * Sets what each burdening minion costs its owner: {@code healthPerMinion} maximum health, never taking the owner
   * below {@code minimumOwnerMaxHealth}. One setting for every kind; Adapt's skeletal servant sets it from its config.
   * Zero health per minion turns the cost off and gives the health back.
   */
  public static void setBurden(double healthPerMinion, double minimumOwnerMaxHealth) {
    Engine.bridge().combat().setMinionBurden(healthPerMinion, minimumOwnerMaxHealth);
  }

  /**
   * Counts the minion against its owner's maximum health until {@link #releaseBurden} or until it dies or unloads.
   * The minion is also kept out of mob stacker plugins.
   */
  public static void burden(PlayerRef owner, LivingRef minion) {
    Engine.bridge().combat().burden(Objects.requireNonNull(owner, "owner"), Objects.requireNonNull(minion, "minion"));
  }

  /** Stops counting the minion against the owner's maximum health. */
  public static void releaseBurden(UUID owner, EntityRef minion) {
    Engine.bridge().combat().releaseBurden(Objects.requireNonNull(owner, "owner"), Objects.requireNonNull(minion, "minion"));
  }

  /**
   * Replaces the set of minions the owner holds by id alone, for minions that are not always loaded. Each id counts
   * like a burdening minion; an empty set clears them.
   */
  public static void holdBurden(UUID owner, Set<UUID> minions) {
    Engine.bridge().combat().holdBurden(Objects.requireNonNull(owner, "owner"), Set.copyOf(minions));
  }
}
