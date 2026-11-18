package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.ComponentPort;
import art.arcane.adapt.api.adaptation.AbilityApiPort;
import art.arcane.adapt.api.adaptation.AdaptationTeleportPort;
import art.arcane.adapt.api.adaptation.EntityGuardPort;
import art.arcane.adapt.api.adaptation.LearningEconomyPort;
import art.arcane.adapt.api.insight.GlossInsightPort;
import art.arcane.adapt.api.potion.BrewEventPort;
import art.arcane.adapt.api.protection.ProtectorPort;
import art.arcane.adapt.content.adaptation.golemancy.ReactSuperStackPort;
import art.arcane.adapt.content.adaptation.rift.AdvancedChestsPort;
import org.bukkit.plugin.Plugin;

/**
 * Adaptation-framework ports: component item and experience operations, protector registry, ability API,
 * entity guards and the Vault learning economy. Registry and economy are read from {@link Adapt#instance} on each
 * call, so they may be created after install.
 */
public final class BukkitAdaptationPorts {
  private BukkitAdaptationPorts() {
  }

  public static void install(Adapt plugin) {
    AdaptPorts.install(ComponentPort.class, new BukkitComponentPort());
    AdaptPorts.install(ProtectorPort.class, new BukkitProtectorPort());
    AdaptPorts.install(EntityGuardPort.class, new BukkitEntityGuardPort());
    AdaptPorts.install(LearningEconomyPort.class, new BukkitLearningEconomyPort());
    BukkitAbilityApiPort.start(plugin);
    AdaptPorts.install(AbilityApiPort.class, new BukkitAbilityApiPort());
    AdaptPorts.install(AdaptationTeleportPort.class, new BukkitAdaptationTeleportPort());
    AdaptPorts.install(AdvancedChestsPort.class, new BukkitAdvancedChestsPort());
    AdaptPorts.install(ReactSuperStackPort.class, new BukkitReactSuperStackPort());
    AdaptPorts.install(GlossInsightPort.class, new BukkitGlossInsightPort(plugin));
    AdaptPorts.install(BrewEventPort.class, new BukkitBrewEventPort());
  }

  public static void startAbilityApi(Plugin plugin) {
    BukkitAbilityApiPort.start(plugin);
  }

  public static void stopAbilityApi() {
    BukkitAbilityApiPort.stop();
  }

  public static void uninstall() {
    AdaptPorts.uninstall(BrewEventPort.class);
    AdaptPorts.uninstall(GlossInsightPort.class);
    AdaptPorts.uninstall(ReactSuperStackPort.class);
    AdaptPorts.uninstall(AdvancedChestsPort.class);
    AdaptPorts.uninstall(AdaptationTeleportPort.class);
    AdaptPorts.uninstall(AbilityApiPort.class);
    BukkitAbilityApiPort.stop();
    AdaptPorts.uninstall(LearningEconomyPort.class);
    AdaptPorts.uninstall(EntityGuardPort.class);
    AdaptPorts.uninstall(ProtectorPort.class);
    AdaptPorts.uninstall(ComponentPort.class);
  }
}
