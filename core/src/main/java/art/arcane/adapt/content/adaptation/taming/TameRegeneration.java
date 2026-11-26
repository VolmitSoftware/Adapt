package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.platform.entity.LivingRef;

/** Heals a pet up to its maximum health with the regeneration hearts. */
public final class TameRegeneration {
  private TameRegeneration() {
  }

  /** Heals {@code pet} by up to {@code requestedHealing} on its own thread and returns the health restored. */
  public static double heal(Adaptation<?> owner, LivingRef pet, double requestedHealing) {
    double currentHealth = pet.health();
    double maxHealth = pet.hasAttribute(Attributes.MAX_HEALTH) ? pet.attributeValue(Attributes.MAX_HEALTH) : currentHealth;
    double actualHealing = actualHealing(currentHealth, maxHealth, requestedHealing);
    if (actualHealing <= 0D) {
      return 0D;
    }

    pet.setHealth(currentHealth + actualHealing);
    TamingHealthRegeneration.regenerationFx(owner, pet, actualHealing);
    return actualHealing;
  }

  static double actualHealing(double currentHealth, double maxHealth, double requestedHealing) {
    if (!Double.isFinite(currentHealth)
        || !Double.isFinite(maxHealth)
        || !Double.isFinite(requestedHealing)
        || currentHealth < 0D
        || maxHealth <= currentHealth
        || requestedHealing <= 0D) {
      return 0D;
    }
    return Math.min(requestedHealing, maxHealth - currentHealth);
  }
}
