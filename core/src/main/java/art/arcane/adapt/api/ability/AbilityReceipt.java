package art.arcane.adapt.api.ability;

public interface AbilityReceipt {
  static AbilityReceipt of(String label) {
    return new SimpleReceipt(label);
  }

  record SimpleReceipt(String label) implements AbilityReceipt {
    public SimpleReceipt {
      label = AbilityText.sanitize(label);
    }
  }
}
