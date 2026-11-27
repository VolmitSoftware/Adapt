package art.arcane.adapt.bukkit.feature;


import art.arcane.adapt.client.server.ClientUiService;
import art.arcane.adapt.client.server.panel.ClientAwareMenuHost;
import art.arcane.adapt.client.server.panel.PanelLink;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.bukkit.feature.advancement.BukkitAdvancementHost;
import art.arcane.adapt.bukkit.feature.ai.BukkitGoals;
import art.arcane.adapt.bukkit.feature.display.BukkitDisplays;
import art.arcane.adapt.bukkit.feature.fx.BukkitFxHost;
import art.arcane.adapt.bukkit.feature.menu.BukkitMenuHost;
import art.arcane.adapt.bukkit.feature.protection.BukkitProtectionHost;
import art.arcane.adapt.bukkit.feature.recipe.BukkitRecipeHost;
import art.arcane.adapt.bukkit.feature.recipe.BukkitRecipeRegistry;
import art.arcane.adapt.bukkit.feature.viewer.BukkitViewerHost;
import art.arcane.adapt.bukkit.platform.BukkitHosts;
import art.arcane.adapt.bukkit.platform.BukkitLog;
import art.arcane.adapt.bukkit.platform.BukkitNatives;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.util.common.compat.PaperApi;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.nativelib.entity.EntityGlow;
import art.arcane.volmlib.nativelib.entity.VirtualPlayerAccess;
import art.arcane.volmlib.nativelib.entity.VirtualPlayers;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Supplier;

/**
 * Registers the Bukkit feature hosts with {@link BukkitHosts}. Call during plugin enable, before the platform
 * starts. {@code glow} comes from the plugin, which owns its lifecycle; it may supply null when the native is
 * unavailable. The advancement host loads UltimateAdvancementAPI itself and disables it when the platform stops.
 */
public final class BukkitFeatures {
  private BukkitFeatures() {
  }

  public static void install(JavaPlugin plugin, Supplier<EntityGlow> glow, Object glowLock) {
    PlatformLog log = new BukkitLog();
    BukkitHosts.fx(new BukkitFxHost());
    BukkitHosts.menus(new ClientAwareMenuHost(new BukkitMenuHost(plugin), PanelLink.of(ClientUiService::activeSessions)));
    BukkitRecipeRegistry.shared().listen(plugin);
    BukkitHosts.recipes(new BukkitRecipeHost(plugin.getServer(), BukkitRecipeRegistry.shared()));
    BukkitAdvancementHost advancements = BukkitAdvancementHost.create(plugin,
        (Player player, Runnable task, int delayTicks) -> J.runEntity(player, task, delayTicks), log);
    BukkitHosts.advancements(advancements);
    BukkitHosts.viewers(new BukkitViewerHost(plugin, glow, glowLock, virtualPlayers(plugin), log));
    BukkitHosts.protection(new BukkitProtectionHost());
    BukkitHosts.displays(new BukkitDisplays());
    if (PaperApi.isPaper()) {
      BukkitHosts.goals(BukkitGoals.server());
    }
    BukkitHosts.onStopping(() -> BukkitHosts.menus().closeAll());
    BukkitHosts.onStopping(advancements::shutdown);
  }

  private static Supplier<VirtualPlayers> virtualPlayers(JavaPlugin plugin) {
    return new Supplier<>() {
      private volatile VirtualPlayers players;

      @Override
      public VirtualPlayers get() {
        VirtualPlayers current = players;
        if (current != null) {
          return current;
        }
        synchronized (this) {
          if (players == null) {
            players = BukkitNatives.find(VirtualPlayerAccess.class).map(access -> access.create(plugin, J::runEntity)).orElse(null);
          }
          return players;
        }
      }
    };
  }
}
