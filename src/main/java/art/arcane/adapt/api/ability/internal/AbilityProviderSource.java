package art.arcane.adapt.api.ability.internal;

@FunctionalInterface
public interface AbilityProviderSource<T> {
  AbilityProviderIndex<T> index();
}
