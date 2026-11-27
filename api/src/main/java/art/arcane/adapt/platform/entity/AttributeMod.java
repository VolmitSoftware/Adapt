package art.arcane.adapt.platform.entity;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Attribute modifier keyed by id, so the same key replaces rather than stacks.
 *
 * @param key namespaced key that identifies the modifier on its attribute
 * @param amount modifier amount; must be finite
 * @param operation how {@code amount} combines with the attribute value
 */
@ApiStatus.AvailableSince("1.0.0")
public record AttributeMod(Id key, double amount, AttributeOperation operation) {
  public AttributeMod {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(operation, "operation");
    if (!Double.isFinite(amount)) {
      throw new IllegalArgumentException("amount must be finite: " + amount);
    }
  }
}
