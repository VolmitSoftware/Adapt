package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;

/** Sends the owner's nearest eligible pet to take a shot aimed at the owner. */
public final class TameInterceptor {
  private final Adaptation<?> owner;

  public TameInterceptor(Adaptation<?> owner) {
    this.owner = owner;
  }

  /**
   * Leaps the nearest pet within {@code radius} at the owner and deals it {@code damage} reduced by
   * {@code petReduction}. Returns false when no pet can take the shot; the caller cancels the hit otherwise.
   */
  public boolean intercept(PlayerRef player, double damage, double radius, double petReduction, double leapStrength) {
    double petDamage = Math.max(0.0, damage * (1.0 - petReduction));
    LivingRef pet = findNearestPet(player, radius, petDamage);
    if (pet == null) {
      return false;
    }

    Loc ownerChest = player.location().add(0, 1, 0);
    AdaptApi.scheduler().runEntity(pet, () -> applyIntercept(pet, ownerChest, petDamage, leapStrength));
    TamingGuardianInstinct.interceptFx(owner, ownerChest);
    return true;
  }

  private LivingRef findNearestPet(PlayerRef player, double radius, double petDamage) {
    return TamePets.nearest(player, radius, (LivingRef living) ->
        owner.preference(player, TamingPreferences.PETS).accepts(living.type())
            && (owner.preference(player, TamingPreferences.HEALTH) == TamingPreferences.Health.DEFAULT
            || living.health() - petDamage >= owner.preference(player, TamingPreferences.HEALTH).floor(living.maxHealth(), 0D)));
  }

  private void applyIntercept(LivingRef pet, Loc ownerChest, double petDamage, double leapStrength) {
    if (!pet.isValid() || pet.isDead()) {
      return;
    }

    TamePets.leap(pet, ownerChest, leapStrength);
    TamingGuardianInstinct.leapFx(owner, pet, ownerChest);
    if (petDamage > 0.01) {
      pet.damage(petDamage, (EntityRef) null);
    }
  }
}
