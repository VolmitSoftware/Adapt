package art.arcane.adapt.engine.ability.internal;

@FunctionalInterface
public interface AbilityProviderSource<T> {
  AbilityProviderIndex<T> index();
}
