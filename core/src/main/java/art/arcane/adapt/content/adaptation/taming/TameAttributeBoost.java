package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.attribute.LegacyModifierKey;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * One multiplied-base attribute modifier kept on pets. The tier passed to {@link #update} decides when the modifier is
 * rewritten; a pet already holding the same tier is left alone.
 */
public final class TameAttributeBoost {
  private final LegacyModifierKey key;
  private final Id attribute;
  private final Consumer<LivingRef> onApplied;
  private final Map<UUID, Integer> appliedTiers = new ConcurrentHashMap<>();

  /** {@code onApplied} runs on the pet's thread each time the modifier is written. */
  public TameAttributeBoost(Id modifierId, Id attribute, Consumer<LivingRef> onApplied) {
    this.key = LegacyModifierKey.of(modifierId);
    this.attribute = attribute;
    this.onApplied = onApplied;
  }

  /** Writes {@code amount} for {@code tier}, or removes the modifier when {@code tier} is 0 or less. */
  public void update(LivingRef pet, int tier, double amount) {
    UUID petId = pet.uuid();
    if (!pet.hasAttribute(attribute)) {
      appliedTiers.remove(petId);
      return;
    }

    Integer appliedTier = appliedTiers.get(petId);
    if (tier <= 0) {
      if (appliedTier != null || hasModifier(pet)) {
        key.remove(pet, attribute);
        if (!pet.isDead()) {
          clampHealth(pet);
        }
      }
      appliedTiers.remove(petId);
      return;
    }

    if (appliedTier != null && appliedTier == tier && hasModifier(pet)) {
      return;
    }

    key.setTransient(pet, attribute, amount, AttributeOperation.ADD_MULTIPLIED_BASE);
    appliedTiers.put(petId, tier);
    onApplied.accept(pet);
  }

  public void remove(LivingRef pet) {
    if (!pet.isValid() || pet.isDead()) {
      appliedTiers.remove(pet.uuid());
      return;
    }

    if (pet.hasAttribute(attribute) && hasModifier(pet)) {
      key.remove(pet, attribute);
      clampHealth(pet);
    }
    appliedTiers.remove(pet.uuid());
  }

  public void forget(UUID petId) {
    appliedTiers.remove(petId);
  }

  public boolean isApplied(UUID petId) {
    return appliedTiers.containsKey(petId);
  }

  public boolean isEmpty() {
    return appliedTiers.isEmpty();
  }

  public void clear() {
    appliedTiers.clear();
  }

  private boolean hasModifier(LivingRef pet) {
    return key.present(pet, attribute);
  }

  private void clampHealth(LivingRef pet) {
    if (!Attributes.MAX_HEALTH.equals(attribute)) {
      return;
    }
    double maximum = pet.attributeValue(Attributes.MAX_HEALTH);
    if (pet.health() > maximum) {
      pet.setHealth(Math.max(0D, maximum));
    }
  }
}
