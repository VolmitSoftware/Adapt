package art.arcane.adapt.platform.entity;

import org.jetbrains.annotations.ApiStatus;

/**
 * One attribute modifier a mob effect applies at a given amplifier, with the attribute's translation key on this
 * server.
 *
 * @param translationKey translation key of the modified attribute
 * @param operation how {@code amount} combines with the attribute value
 * @param amount modifier amount at the requested amplifier
 */
@ApiStatus.AvailableSince("1.0.0")
public record EffectModifier(String translationKey, AttributeOperation operation, double amount) {
}
