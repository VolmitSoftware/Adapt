package art.arcane.adapt.bukkit.compat;

import art.arcane.adapt.api.adaptation.AbilityApiBridge;
import art.arcane.adapt.bukkit.port.BukkitAdaptationPorts;
import org.bukkit.plugin.Plugin;

public final class AbilityBridge {
  private AbilityBridge() {
  }

  @ApiBridge(value = AbilityApiBridge.class, instance = false)
  public static void install(Plugin plugin) {
    BukkitAdaptationPorts.startAbilityApi(plugin);
  }

  @ApiBridge(value = AbilityApiBridge.class, instance = false)
  public static void uninstall() {
    BukkitAdaptationPorts.stopAbilityApi();
  }
}
