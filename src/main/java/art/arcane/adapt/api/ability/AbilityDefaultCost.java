package art.arcane.adapt.api.ability;

@FunctionalInterface
public interface AbilityDefaultCost {
  AbilityDefaultCost NONE = () -> true;

  boolean take();
}
