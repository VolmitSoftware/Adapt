package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.gloss.api.GlossAPI;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlossInsightIntegrationTest {
  @Test
  void sendsViewerDetailsAndRespectsProviderRejection() {
    Fixture fixture = new Fixture();
    Player viewer = mock(Player.class);
    LivingEntity target = mock(LivingEntity.class);
    UUID viewerId = UUID.randomUUID();
    List<String> details = List.of("Speed 0.25", "Toughness 4");
    when(fixture.provider.updateEntityInsight(fixture.adapt, viewer, target, details, 2000L)).thenReturn(true, false);

    assertThat(fixture.integration.update(viewer, target, details, 2000L)).isTrue();
    assertThat(fixture.integration.update(viewer, target, details, 2000L)).isFalse();
    fixture.integration.clear(viewerId);

    verify(fixture.provider, times(2)).updateEntityInsight(fixture.adapt, viewer, target, details, 2000L);
    verify(fixture.provider).clearEntityInsight(fixture.adapt, viewerId);
    verify(fixture.services).getKnownServices();
  }

  @Test
  void restrictionIsReappliedWhenGlossReturns() {
    Fixture fixture = new Fixture();
    fixture.integration.setRestricted(true);
    clearInvocations(fixture.provider);
    when(fixture.gloss.isEnabled()).thenReturn(false);
    assertThat(fixture.integration.available()).isFalse();

    when(fixture.gloss.isEnabled()).thenReturn(true);
    fixture.integration.refresh();

    assertThat(fixture.integration.available()).isTrue();
    verify(fixture.provider).restrictEntityOverlays(fixture.adapt, true);
    fixture.integration.setRestricted(false);
    verify(fixture.provider).restrictEntityOverlays(fixture.adapt, false);
  }

  @Test
  void absentGlossDiscoveryIsThrottledAndExplicitRefreshRecoversImmediately() {
    Fixture fixture = new Fixture();
    when(fixture.services.getKnownServices()).thenReturn(Set.of());

    assertThat(fixture.integration.available()).isFalse();
    assertThat(fixture.integration.available()).isFalse();
    verify(fixture.services).getKnownServices();
    fixture.clock.addAndGet(5000L);
    assertThat(fixture.integration.available()).isFalse();
    verify(fixture.services, times(2)).getKnownServices();

    when(fixture.services.getKnownServices()).thenReturn(Set.of(GlossAPI.class));
    fixture.integration.refresh();
    assertThat(fixture.integration.available()).isTrue();
    verify(fixture.services, times(3)).getKnownServices();
  }

  private static final class Fixture {
    private final ServicesManager services = mock(ServicesManager.class);
    private final Plugin adapt = mock(Plugin.class);
    private final Plugin gloss = mock(Plugin.class);
    private final GlossAPI provider = mock(GlossAPI.class);
    private final AtomicLong clock = new AtomicLong(1000L);
    private final GlossInsightIntegration integration = new GlossInsightIntegration(
        adapt, new GlossInsightIntegration.Access(() -> services, clock::get));

    private Fixture() {
      when(gloss.isEnabled()).thenReturn(true);
      when(services.getKnownServices()).thenReturn(Set.of(GlossAPI.class));
      when(services.getRegistrations(GlossAPI.class)).thenReturn(List.of(
          new RegisteredServiceProvider<>(GlossAPI.class, provider, ServicePriority.Normal, gloss)));
    }
  }
}
