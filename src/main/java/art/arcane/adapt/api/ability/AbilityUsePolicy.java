package art.arcane.adapt.api.ability;

@FunctionalInterface
public interface AbilityUsePolicy {
  AbilityUseDecision evaluate(AbilityContext context);

  default String providerId() {
    return getClass().getName();
  }

  default AbilityScope scope() {
    return AbilityScope.everything();
  }
}
