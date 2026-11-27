package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.engine.ComponentPort;
import art.arcane.adapt.engine.adaptation.EntityGuardPort;
import art.arcane.adapt.engine.adaptation.LearningEconomyPort;
import art.arcane.adapt.engine.insight.GlossInsightPort;
import art.arcane.adapt.engine.potion.BrewEventPort;
import art.arcane.adapt.engine.integration.ReactSuperStackPort;
import art.arcane.adapt.engine.integration.AdvancedChestsPort;

/**
 * Adaptation-framework ports: component item and experience operations, entity guards and the Vault learning
 * economy. The economy is read from {@link Adapt#instance} on each call, so it may be created after install.
 */
public final class BukkitAdaptationPorts {
  private BukkitAdaptationPorts() {
  }

  public static void install(Adapt plugin) {
    AdaptPorts.install(ComponentPort.class, new BukkitComponentPort());
    AdaptPorts.install(EntityGuardPort.class, new BukkitEntityGuardPort());
    AdaptPorts.install(LearningEconomyPort.class, new BukkitLearningEconomyPort());
    AdaptPorts.install(AdvancedChestsPort.class, new BukkitAdvancedChestsPort());
    AdaptPorts.install(ReactSuperStackPort.class, new BukkitReactSuperStackPort());
    AdaptPorts.install(GlossInsightPort.class, new BukkitGlossInsightPort(plugin));
    AdaptPorts.install(BrewEventPort.class, new BukkitBrewEventPort());
  }

  public static void uninstall() {
    AdaptPorts.uninstall(BrewEventPort.class);
    AdaptPorts.uninstall(GlossInsightPort.class);
    AdaptPorts.uninstall(ReactSuperStackPort.class);
    AdaptPorts.uninstall(AdvancedChestsPort.class);
    AdaptPorts.uninstall(LearningEconomyPort.class);
    AdaptPorts.uninstall(EntityGuardPort.class);
    AdaptPorts.uninstall(ComponentPort.class);
  }
}
