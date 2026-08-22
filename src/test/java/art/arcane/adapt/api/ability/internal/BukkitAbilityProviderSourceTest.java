package art.arcane.adapt.api.ability.internal;

import art.arcane.adapt.api.ability.AbilityContext;
import art.arcane.adapt.api.ability.AbilityScope;
import art.arcane.adapt.api.ability.AbilityUseDecision;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BukkitAbilityProviderSourceTest {
  @Test
  void aPolicyThatThrowsFromScopeIsStillIndexedAsUnscoped() {
    HostilePolicy provider = new HostilePolicy("thrower");
    provider.scopeError = new IllegalStateException("hostile scope");

    AbilityProviderIndex<AbilityUsePolicy> index = index(registration(provider));

    assertThat(index.size()).isOne();
    assertThat(index.all().getFirst().unscoped()).isTrue();
    assertThat(index.matching("tragoul-lance", "tragoul")).hasSize(1);
  }

  @Test
  void aPolicyThatReturnsNullFromScopeIsStillIndexedAsUnscoped() {
    HostilePolicy provider = new HostilePolicy("nuller");
    provider.scope = null;

    AbilityProviderIndex<AbilityUsePolicy> index = index(registration(provider));

    assertThat(index.size()).isOne();
    assertThat(index.all().getFirst().unscoped()).isTrue();
  }

  @Test
  void aPolicyWithABlankOrThrowingProviderIdIsIgnoredWithoutKillingTheIndex() {
    HostilePolicy blank = new HostilePolicy("   ");
    HostilePolicy thrower = new HostilePolicy("boom");
    thrower.idError = new IllegalStateException("hostile providerId");
    HostilePolicy good = new HostilePolicy("good");

    AbilityProviderIndex<AbilityUsePolicy> index =
        index(registration(blank), registration(thrower), registration(good));

    assertThat(index.size()).isOne();
    assertThat(index.all().getFirst().providerId()).isEqualTo("good");
  }

  @Test
  void aServiceRegistrationThatThrowsWhileBeingReadIsSkippedAndTheOthersSurvive() {
    HostilePolicy good = new HostilePolicy("good");
    RegisteredServiceProvider<AbilityUsePolicy> broken = registration(new HostilePolicy("broken"));
    when(broken.getPriority()).thenThrow(new IllegalStateException("hostile registration"));

    AbilityProviderIndex<AbilityUsePolicy> index = index(broken, registration(good));

    assertThat(index.size()).isOne();
    assertThat(index.all().getFirst().providerId()).isEqualTo("good");
  }

  @Test
  void aServicesManagerFailureIsCachedAsAnEmptyIndexRatherThanRethrownOnEveryCheck() {
    ServicesManager services = mock(ServicesManager.class);
    when(services.getRegistrations(AbilityUsePolicy.class)).thenThrow(new IllegalStateException("services are down"));

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getServicesManager).thenReturn(services);
      BukkitAbilityProviderSource<AbilityUsePolicy> source = source();

      assertThatCode(() -> {
        for (int call = 0; call < 100; call++) {
          assertThat(source.index().isEmpty()).isTrue();
        }
      }).doesNotThrowAnyException();

      verify(services, times(1)).getRegistrations(AbilityUsePolicy.class);
    }
  }

  @Test
  void scopeIsReadOnceWhenTheIndexIsBuiltAndNeverPerCheck() {
    HostilePolicy provider = new HostilePolicy("counted");
    provider.scope = AbilityScope.skill("tragoul");
    RegisteredServiceProvider<AbilityUsePolicy> registration = registration(provider);
    ServicesManager services = mock(ServicesManager.class);
    when(services.getRegistrations(AbilityUsePolicy.class)).thenReturn(List.of(registration));

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getServicesManager).thenReturn(services);
      BukkitAbilityProviderSource<AbilityUsePolicy> source = source();

      for (int call = 0; call < 50; call++) {
        assertThat(source.index().matching("tragoul-lance", "tragoul")).hasSize(1);
      }

      assertThat(provider.scopeCalls.get()).isOne();
      source.invalidate();
      assertThat(source.index().size()).isOne();
      assertThat(provider.scopeCalls.get()).isEqualTo(2);
    }
  }

  private static BukkitAbilityProviderSource<AbilityUsePolicy> source() {
    return new BukkitAbilityProviderSource<>(AbilityUsePolicy.class, "use policy",
        AbilityGatewayFixture.silentLogger(), AbilityUsePolicy::providerId, AbilityUsePolicy::scope);
  }

  @SafeVarargs
  private static AbilityProviderIndex<AbilityUsePolicy> index(RegisteredServiceProvider<AbilityUsePolicy>... raw) {
    ServicesManager services = mock(ServicesManager.class);
    when(services.getRegistrations(AbilityUsePolicy.class)).thenReturn(List.of(raw));

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getServicesManager).thenReturn(services);
      return source().index();
    }
  }

  @SuppressWarnings("unchecked")
  private static RegisteredServiceProvider<AbilityUsePolicy> registration(AbilityUsePolicy provider) {
    Plugin owner = mock(Plugin.class);
    when(owner.getName()).thenReturn("HostilePlugin");
    when(owner.isEnabled()).thenReturn(true);
    RegisteredServiceProvider<AbilityUsePolicy> registration = mock(RegisteredServiceProvider.class);
    when(registration.getProvider()).thenReturn(provider);
    when(registration.getPlugin()).thenReturn(owner);
    when(registration.getPriority()).thenReturn(ServicePriority.Normal);
    return registration;
  }

  private static final class HostilePolicy implements AbilityUsePolicy {
    private final AtomicInteger scopeCalls = new AtomicInteger();
    private final String id;

    private AbilityScope scope = AbilityScope.everything();
    private RuntimeException scopeError;
    private RuntimeException idError;

    private HostilePolicy(String id) {
      this.id = id;
    }

    @Override
    public String providerId() {
      if (idError != null) {
        throw idError;
      }

      return id;
    }

    @Override
    public AbilityScope scope() {
      scopeCalls.incrementAndGet();

      if (scopeError != null) {
        throw scopeError;
      }

      return scope;
    }

    @Override
    public AbilityUseDecision evaluate(AbilityContext context) {
      return AbilityUseDecision.allow();
    }
  }
}
