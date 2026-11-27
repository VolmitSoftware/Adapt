package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Loc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Splits damage the owner takes across nearby owned pets without taking any pet below its health floor. */
public final class TamePainShare {
  public static final double MINIMUM_TRANSFER = 0.01D;
  private static final int HARD_MAX_PETS = 16;

  private final Adaptation<?> owner;
  private final Cooldowns redirectFx;

  public TamePainShare(Adaptation<?> owner, Cooldowns redirectFx) {
    this.owner = owner;
    this.redirectFx = redirectFx;
  }

  /** Damages the eligible pets for up to {@code requestedTransfer} in total and returns the damage they absorbed. */
  public double share(PlayerRef player, double requestedTransfer, double radius, double healthFloor, int petLimit) {
    UUID ownerId = player.uuid();
    List<PetTarget> pets = findEligiblePets(player, ownerId, radius, healthFloor, petLimit);
    if (pets.isEmpty()) {
      return 0D;
    }

    double[] capacities = new double[pets.size()];
    for (int i = 0; i < pets.size(); i++) {
      capacities[i] = pets.get(i).capacity();
    }
    double[] shares = damageShares(requestedTransfer, capacities);
    Loc ownerChest = player.location().add(0, 1, 0);
    double transferred = applyShares(pets, shares, ownerChest);
    if (transferred <= MINIMUM_TRANSFER) {
      return transferred;
    }

    if (redirectFx.isReady(ownerId, 500L)) {
      redirectFx.mark(ownerId);
      TamingSharedPain.redirectFx(owner, ownerChest);
    }
    return transferred;
  }

  /**
   * Shares {@code sharePercent} of the hit as it lands, after armor, with the pets and lowers the hit by what they
   * absorbed; returns that amount.
   */
  public double shareHit(EntityDamageEvent e, PlayerRef player, double sharePercent, double radius, double healthFloor,
                         int petLimit) {
    double landing = e.finalDamage();
    double requestedTransfer = landing * sharePercent;
    if (requestedTransfer <= MINIMUM_TRANSFER) {
      return 0D;
    }
    double transferred = share(player, requestedTransfer, radius, healthFloor, petLimit);
    if (transferred > MINIMUM_TRANSFER) {
      e.setDamage(e.damage() * (Math.max(0D, landing - transferred) / landing));
    }
    return transferred;
  }

  public static int petLimit(int configured) {
    return Math.max(1, Math.min(configured, HARD_MAX_PETS));
  }

  static double[] damageShares(double requestedDamage, double[] capacities) {
    double[] shares = new double[capacities.length];
    double remaining = Math.max(0, requestedDamage);
    boolean[] available = new boolean[capacities.length];
    int availableCount = 0;
    for (int i = 0; i < capacities.length; i++) {
      if (Double.isFinite(capacities[i]) && capacities[i] > MINIMUM_TRANSFER) {
        available[i] = true;
        availableCount++;
      }
    }

    while (remaining > MINIMUM_TRANSFER && availableCount > 0) {
      double equalShare = remaining / availableCount;
      double allocated = 0;
      for (int i = 0; i < capacities.length; i++) {
        if (!available[i]) {
          continue;
        }
        double capacity = Math.max(0, capacities[i] - shares[i]);
        double amount = Math.min(equalShare, capacity);
        shares[i] += amount;
        allocated += amount;
        if (capacity - amount <= MINIMUM_TRANSFER) {
          available[i] = false;
          availableCount--;
        }
      }
      if (allocated <= MINIMUM_TRANSFER) {
        break;
      }
      remaining -= allocated;
    }
    return shares;
  }

  static double transferredDamage(double beforeHealth, double beforeAbsorption, double afterHealth,
                                  double afterAbsorption, double requestedDamage) {
    double before = Math.max(0, beforeHealth) + Math.max(0, beforeAbsorption);
    double after = Math.max(0, afterHealth) + Math.max(0, afterAbsorption);
    return Math.min(Math.max(0, requestedDamage), Math.max(0, before - after));
  }

  private List<PetTarget> findEligiblePets(PlayerRef player, UUID ownerId, double radius, double healthFloor, int limit) {
    List<PetTarget> pets = new ArrayList<>(limit);
    for (EntityRef entity : player.nearbyEntities(radius, radius, radius)) {
      if (pets.size() >= limit) {
        break;
      }
      if (!(entity instanceof LivingRef living) || !entity.has(EntityTrait.TAMEABLE)
          || !AdaptApi.scheduler().owns(living)) {
        continue;
      }
      if (!owner.preference(player, TamingPreferences.PETS).accepts(living.type()) || !AdaptApi.platform().host().animals().isTamed(living) || !living.isValid() || living.isDead() || !isOwnedBy(living, ownerId)) {
        continue;
      }
      double capacity = living.health() + living.absorption() - owner.preference(player, TamingPreferences.HEALTH).floor(living.maxHealth(), healthFloor);
      if (capacity > MINIMUM_TRANSFER) {
        pets.add(new PetTarget(living, capacity));
      }
    }
    return pets;
  }

  private double applyShares(List<PetTarget> pets, double[] shares, Loc ownerChest) {
    double transferred = 0;
    for (int i = 0; i < pets.size(); i++) {
      double share = shares[i];
      if (share <= MINIMUM_TRANSFER) {
        continue;
      }

      LivingRef pet = pets.get(i).pet();
      double beforeHealth = pet.health();
      double beforeAbsorption = pet.absorption();
      pet.damage(share, (EntityRef) null);
      double actual = transferredDamage(beforeHealth, beforeAbsorption, pet.health(), pet.absorption(), share);
      if (actual <= MINIMUM_TRANSFER) {
        continue;
      }

      transferred += actual;
      UUID petId = pet.uuid();
      if (redirectFx.isReady(petId, 500L)) {
        redirectFx.mark(petId);
        Loc petChest = pet.location().add(0, 1, 0);
        TamingSharedPain.petPainFx(owner, petChest, ownerChest);
      }
    }
    return transferred;
  }

  private static boolean isOwnedBy(LivingRef tameable, UUID ownerId) {
    UUID petOwner = tameable.tameOwner();
    return petOwner != null && ownerId.equals(petOwner);
  }

  private record PetTarget(LivingRef pet, double capacity) {
  }
}
