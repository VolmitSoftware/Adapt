package art.arcane.adapt.engine.ability.internal;

import art.arcane.adapt.engine.ability.EngineAbilityCostContext;
import art.arcane.adapt.engine.ability.EngineAbilityContext;
import art.arcane.adapt.api.ability.AbilityContext;
import art.arcane.adapt.api.ability.AbilityCostContext;
import art.arcane.adapt.api.ability.AbilityCostKind;
import art.arcane.adapt.api.ability.AbilityCostProvider;
import art.arcane.adapt.api.ability.AbilityPhase;
import art.arcane.adapt.api.ability.AbilityScope;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class AbilityGatewayFixture {
  static final Id TRAGOUL = Id.adapt("tragoul");
  static final Id RIFT = Id.adapt("rift");
  static final Id SERVANT = Id.adapt("tragoul/skeletal_servant");

  private AbilityGatewayFixture() {
  }

  static PlatformLog silentLog() {
    return new RecordingLog();
  }

  static PlayerRef player() {
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(UUID.randomUUID());
    return player;
  }

  static AbilityContext check(Id ability, Id skill, PlayerRef player) {
    return context(ability, skill, 3, AbilityPhase.CHECK, player);
  }

  static AbilityContext activate(Id ability, Id skill, int level, PlayerRef player) {
    return context(ability, skill, level, AbilityPhase.ACTIVATE, player);
  }

  static AbilityContext context(Id ability, Id skill, int level, AbilityPhase phase, PlayerRef player) {
    return new EngineAbilityContext(UUID.randomUUID(), ability, skill, level, phase, player, Optional.empty());
  }

  static AbilityCostContext cost(AbilityContext ability, String costKey, int amount) {
    return new EngineAbilityCostContext(ability, costKey, AbilityCostKind.ITEM, Optional.empty(), amount);
  }

  static AbilityProviderSource<AbilityUsePolicy> useSource(List<AbilityProviderRegistration<AbilityUsePolicy>> list) {
    AbilityProviderIndex<AbilityUsePolicy> index = new AbilityProviderIndex<>(list);
    return () -> index;
  }

  static AbilityProviderSource<AbilityCostProvider> costSource(
      List<AbilityProviderRegistration<AbilityCostProvider>> list) {
    AbilityProviderIndex<AbilityCostProvider> index = new AbilityProviderIndex<>(list);
    return () -> index;
  }

  static AbilityProviderRegistration<AbilityUsePolicy> policy(String id, AbilityScope scope, AbilityUsePolicy provider) {
    return new AbilityProviderRegistration<>(provider, id, scope);
  }

  static AbilityProviderRegistration<AbilityCostProvider> provider(String id, AbilityScope scope,
                                                                   AbilityCostProvider provider) {
    return new AbilityProviderRegistration<>(provider, id, scope);
  }

  static <T> List<AbilityProviderRegistration<T>> list(AbilityProviderRegistration<T> first,
                                                       AbilityProviderRegistration<T> second) {
    List<AbilityProviderRegistration<T>> out = new ArrayList<>(2);
    out.add(first);
    out.add(second);
    return out;
  }

  static final class RecordingLog implements PlatformLog {
    final List<String> warnings = new ArrayList<>();
    final List<String> errors = new ArrayList<>();

    @Override
    public void info(String message) {
    }

    @Override
    public void warn(String message) {
      warnings.add(message);
    }

    @Override
    public void error(String message, Throwable error) {
      errors.add(message);
    }

    @Override
    public void verbose(String message) {
    }
  }
}
