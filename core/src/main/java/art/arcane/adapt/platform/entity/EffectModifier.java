package art.arcane.adapt.platform.entity;

/** One attribute modifier a mob effect applies at a given amplifier, with the attribute's translation key on this server. */
public record EffectModifier(String translationKey, AttributeOperation operation, double amount) {
}
