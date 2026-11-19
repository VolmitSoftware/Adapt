package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.bukkit.platform.world.BukkitBlockStateRef;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.Capability;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.TeleportCause;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BukkitPlatformHostTest {
  @Test
  void capabilitiesFollowInstalledPlugins() {
    PluginManager plugins = mock(PluginManager.class);
    when(plugins.isPluginEnabled("WorldGuard")).thenReturn(true);
    when(plugins.isPluginEnabled("PlaceholderAPI")).thenReturn(true);
    BukkitPlatformHost host = host(plugins, false);

    Set<Capability> present = EnumSet.noneOf(Capability.class);
    for (Capability capability : Capability.values()) {
      if (host.has(capability)) {
        present.add(capability);
      }
    }

    assertThat(present).containsExactlyInAnyOrder(Capability.WORLDGUARD, Capability.PROTECTION_PLUGINS, Capability.PLACEHOLDERS);
  }

  @Test
  void foliaRegionsFollowTheScheduler() {
    PluginManager plugins = mock(PluginManager.class);

    assertThat(host(plugins, true).has(Capability.FOLIA_REGIONS)).isTrue();
    assertThat(host(plugins, false).has(Capability.FOLIA_REGIONS)).isFalse();
  }

  @Test
  void everyProtectionPluginCounts() {
    for (String name : BukkitPlatformHost.PROTECTION_PLUGINS) {
      PluginManager plugins = mock(PluginManager.class);
      when(plugins.isPluginEnabled(name)).thenReturn(true);
      assertThat(host(plugins, false).has(Capability.PROTECTION_PLUGINS)).as(name).isTrue();
    }
  }

  @Test
  void enumConversionsRoundTrip() {
    for (GameMode mode : GameMode.values()) {
      assertThat(BukkitConversions.gameMode(BukkitConversions.gameMode(mode))).isEqualTo(mode);
    }
    for (AttributeOperation operation : AttributeOperation.values()) {
      assertThat(BukkitConversions.operation(BukkitConversions.operation(operation))).isEqualTo(operation);
    }
    for (TeleportCause cause : TeleportCause.values()) {
      assertThat(BukkitConversions.cause(BukkitConversions.cause(cause))).isEqualTo(cause);
    }
  }

  @Test
  void blockStatePropertiesParseFromBukkitStrings() {
    BlockData wheat = mock(BlockData.class);
    when(wheat.getAsString()).thenReturn("minecraft:wheat[age=7]");
    when(wheat.getMaterial()).thenReturn(Material.WHEAT);
    when(wheat.clone()).thenReturn(wheat);

    BukkitBlockStateRef state = new BukkitBlockStateRef(wheat);

    assertThat(state.property("age")).isEqualTo("7");
    assertThat(state.property("facing")).isNull();
    assertThat(state.properties()).containsOnlyKeys("age");
    assertThat(state.with("facing", "north")).isSameAs(state);
  }

  private static BukkitPlatformHost host(PluginManager plugins, boolean regionized) {
    Server server = mock(Server.class);
    when(server.getPluginManager()).thenReturn(plugins);
    AdaptScheduler scheduler = mock(AdaptScheduler.class);
    when(scheduler.isRegionized()).thenReturn(regionized);
    return new BukkitPlatformHost(server, mock(Plugin.class), mock(PlatformLog.class), scheduler);
  }
}
