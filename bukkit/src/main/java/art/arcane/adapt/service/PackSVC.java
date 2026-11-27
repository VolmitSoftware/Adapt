package art.arcane.adapt.service;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.pack.runtime.PackBuilds;
import art.arcane.adapt.pack.runtime.PackReleases;
import art.arcane.adapt.pack.link.PackDelivery;
import art.arcane.adapt.util.common.plugin.AdaptService;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/** Builds the Adapt resource pack and runs its delivery while the plugin is enabled. */
public class PackSVC implements AdaptService {
  private static final List<String> BEDROCK_PLUGINS = List.of("floodgate", "Geyser-Spigot");
  private PackBuilds builds;

  @Override
  public void onEnable() {
    String adapt = AdaptCore.platform().host().adaptVersion();
    builds = PackBuilds.install(AdaptCore.get().assets(), adapt, Tasks::a);
    PackDelivery.start(PackSVC::bedrockLoaders,
        PackReleases.of(AdaptConfig.get().getPack().getReleaseUrl(), adapt, PackReleases.stockSha1(PackSVC.class)));
  }

  @Override
  public void onDisable() {
    PackDelivery.stop();
    if (builds != null) {
      builds.uninstall();
      builds = null;
    }
  }

  private static List<ClassLoader> bedrockLoaders() {
    List<ClassLoader> loaders = new ArrayList<>();
    for (String name : BEDROCK_PLUGINS) {
      Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
      if (plugin != null && plugin.isEnabled()) {
        loaders.add(plugin.getClass().getClassLoader());
      }
    }
    return loaders;
  }
}
