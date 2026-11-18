package art.arcane.adapt.platform.entity;

import art.arcane.adapt.platform.value.Id;

/** One attribute modifier a mob effect applies at a given amplifier. */
public record EffectModifier(Id attribute, AttributeOperation operation, double amount) {
}
