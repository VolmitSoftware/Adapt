package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.engine.bridge.HostKeys;
import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.AddonHandle;
import art.arcane.adapt.api.addon.AddonState;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.internal.Engine;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AddonProvidersTest {
  private final Plugin alchemy = mock(Plugin.class);
  private final Plugin forge = mock(Plugin.class);

  @AfterEach
  void uninstall() {
    for (AddonHandle handle : Engine.addons().snapshot(HostKeys.key())) {
      handle.uninstall();
    }
  }

  @Test
  void disablingAPluginRemovesOnlyTheAddonsItProvided() {
    AddonHandle transmute = Engine.addons().install(new Alchemist());
    AddonHandle anvils = Engine.addons().install(new Smith());
    Map<Class<?>, Plugin> owners = Map.of(Alchemist.class, alchemy, Smith.class, forge);
    AddonProviders providers = new AddonProviders(owners::get);

    PluginDisableEvent disabled = mock(PluginDisableEvent.class);
    when(disabled.getPlugin()).thenReturn(alchemy);

    providers.on(disabled);

    assertThat(transmute.state()).isEqualTo(AddonState.UNINSTALLED);
    assertThat(anvils.state()).isEqualTo(AddonState.WAITING);
    assertThat(Engine.addons().find("alchemist")).isEmpty();
    assertThat(Engine.addons().find("smith")).isPresent();
  }

  private static final class Alchemist implements AdaptAddon {
    @Override
    public String namespace() {
      return "alchemist";
    }

    @Override
    public void register(ContentRegistrar registrar) {
    }
  }

  private static final class Smith implements AdaptAddon {
    @Override
    public String namespace() {
      return "smith";
    }

    @Override
    public void register(ContentRegistrar registrar) {
    }
  }
}
