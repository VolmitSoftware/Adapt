package art.arcane.adapt.platform.entity;

import art.arcane.adapt.platform.value.Id;

import java.util.Objects;

/** Attribute modifier keyed by id, so the same key replaces rather than stacks. */
public record AttributeMod(Id key, double amount, AttributeOperation operation) {
  public AttributeMod {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(operation, "operation");
    if (!Double.isFinite(amount)) {
      throw new IllegalArgumentException("amount must be finite: " + amount);
    }
  }
}
