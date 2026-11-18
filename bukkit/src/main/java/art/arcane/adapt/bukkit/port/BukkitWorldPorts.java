package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.fx.ViewerDisplayPort;
import art.arcane.adapt.api.fx.ViewerGlowPort;
import art.arcane.adapt.api.protection.ProtectionProbePort;
import art.arcane.adapt.content.integration.hiddenore.HiddenOrePort;
import art.arcane.adapt.content.integration.iris.IrisTreeFellerPort;
import art.arcane.adapt.api.data.BlockStorePort;
import art.arcane.adapt.api.data.WorldDataPort;
import art.arcane.adapt.api.skill.SkillRegistryPort;
import art.arcane.adapt.api.world.EntityMarkerPort;
import art.arcane.adapt.api.world.PlaceholderPort;
import art.arcane.adapt.api.world.PlayerSessionPort;
import art.arcane.adapt.api.world.PlayerTransferPort;

public final class BukkitWorldPorts {
  private BukkitWorldPorts() {
  }

  public static void install(Adapt plugin) {
    AdaptPorts.install(PlayerTransferPort.class, new BukkitPlayerTransferPort());
    AdaptPorts.install(PlaceholderPort.class, new BukkitPlaceholderPort());
    AdaptPorts.install(SkillRegistryPort.class, new BukkitSkillRegistryPort());
    AdaptPorts.install(PlayerSessionPort.class, new BukkitPlayerSessionPort());
    AdaptPorts.install(WorldDataPort.class, new BukkitWorldDataPort());
    AdaptPorts.install(BlockStorePort.class, new BukkitBlockStorePort());
    AdaptPorts.install(ProtectionProbePort.class, new BukkitProtectionProbePort());
    AdaptPorts.install(HiddenOrePort.class, new BukkitHiddenOrePort());
    AdaptPorts.install(IrisTreeFellerPort.class, new BukkitIrisTreeFellerPort());
    AdaptPorts.install(ViewerDisplayPort.class, new BukkitViewerDisplayPort());
    AdaptPorts.install(ViewerGlowPort.class, new BukkitViewerGlowPort());
    AdaptPorts.install(EntityMarkerPort.class, new BukkitEntityMarkerPort());
  }

  public static void uninstall() {
    AdaptPorts.uninstall(PlayerTransferPort.class);
    AdaptPorts.uninstall(PlaceholderPort.class);
    AdaptPorts.uninstall(SkillRegistryPort.class);
    AdaptPorts.uninstall(PlayerSessionPort.class);
    AdaptPorts.uninstall(WorldDataPort.class);
    AdaptPorts.uninstall(BlockStorePort.class);
    AdaptPorts.uninstall(ProtectionProbePort.class);
    AdaptPorts.uninstall(HiddenOrePort.class);
    AdaptPorts.uninstall(IrisTreeFellerPort.class);
    AdaptPorts.uninstall(ViewerDisplayPort.class);
    AdaptPorts.uninstall(ViewerGlowPort.class);
    AdaptPorts.uninstall(EntityMarkerPort.class);
  }
}
