package art.arcane.adapt.api.ability.internal;

import art.arcane.adapt.api.ability.AbilityContext;
import art.arcane.adapt.api.ability.AbilityCostContext;
import art.arcane.adapt.api.ability.AbilityCostKind;
import art.arcane.adapt.api.ability.AbilityCostProvider;
import art.arcane.adapt.api.ability.AbilityScope;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class AbilityGatewayFixture {
  static final AtomicLong CLOCK = new AtomicLong(1_000L);

  private AbilityGatewayFixture() {
  }

  static Logger silentLogger() {
    Logger logger = Logger.getLogger("AbilityApiTest-" + UUID.randomUUID());
    logger.setUseParentHandlers(false);
    logger.setLevel(Level.OFF);
    logger.addHandler(new Handler() {
      @Override
      public void publish(LogRecord record) {
      }

      @Override
      public void flush() {
      }

      @Override
      public void close() {
      }
    });
    return logger;
  }

  static Player player() {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    return player;
  }

  static AbilityContext check(String abilityId, String skillId, Player player) {
    return AbilityContext.check(abilityId, skillId, 3, player);
  }

  static AbilityCostContext cost(String abilityId, String skillId, Player player, String costKey) {
    return new AbilityCostContext(AbilityContext.activate(abilityId, skillId, 3, player, null), costKey,
        AbilityCostKind.ITEM, Optional.empty(), 1);
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
    return AbilityProviderRegistration.of(provider, id, "TestPlugin", ServicePriority.Normal, scope);
  }

  static AbilityProviderRegistration<AbilityCostProvider> provider(String id, AbilityScope scope,
                                                                   AbilityCostProvider provider) {
    return AbilityProviderRegistration.of(provider, id, "TestPlugin", ServicePriority.Normal, scope);
  }

  static <T> List<AbilityProviderRegistration<T>> list(AbilityProviderRegistration<T> first,
                                                       AbilityProviderRegistration<T> second) {
    List<AbilityProviderRegistration<T>> out = new ArrayList<>(2);
    out.add(first);
    out.add(second);
    return out;
  }
}
