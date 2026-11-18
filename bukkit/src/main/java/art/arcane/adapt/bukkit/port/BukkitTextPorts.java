package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.ItemCooldownGroupPort;
import art.arcane.adapt.api.attribute.AdaptAttributeResolver;
import art.arcane.adapt.api.fx.TpsPort;
import art.arcane.adapt.api.notification.HudPort;
import art.arcane.adapt.api.value.MaterialValuePort;
import art.arcane.adapt.localization.LanguageSwitcherPort;
import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.util.common.math.PlayerInputPort;
import art.arcane.adapt.util.common.misc.CustomModelPort;
import art.arcane.adapt.util.config.ConfigHotloadPort;

/** Text, language, HUD, item and attribute ports of the Bukkit plugin. */
public final class BukkitTextPorts {
  private BukkitTextPorts() {
  }

  public static void install(Adapt plugin) {
    AdaptPorts.install(MarkupPort.class, new BukkitMarkupPort());
    AdaptPorts.install(ConfigHotloadPort.class, new BukkitConfigHotloadPort(plugin));
    AdaptPorts.install(LanguageSwitcherPort.class, new BukkitLanguageSwitcherPort(plugin));
    AdaptPorts.install(HudPort.class, new BukkitHudPort(plugin));
    AdaptPorts.install(TpsPort.class, new BukkitTpsPort());
    AdaptPorts.install(ItemCooldownGroupPort.class, new BukkitItemCooldownGroupPort());
    AdaptPorts.install(PlayerInputPort.class, new BukkitPlayerInputPort());
    AdaptPorts.install(CustomModelPort.class, new BukkitCustomModelPort());
    AdaptPorts.install(MaterialValuePort.class, new BukkitMaterialValuePort());
    AdaptPorts.install(AdaptAttributeResolver.class, new BukkitAttributeResolver());
  }

  public static void uninstall() {
    LanguageSwitcherPort switcher = AdaptPorts.get(LanguageSwitcherPort.class, null);
    if (switcher != null) {
      switcher.close();
    }
    HudPort hud = AdaptPorts.get(HudPort.class, null);
    if (hud != null) {
      hud.shutdown();
    }
    AdaptPorts.uninstall(MarkupPort.class);
    AdaptPorts.uninstall(ConfigHotloadPort.class);
    AdaptPorts.uninstall(LanguageSwitcherPort.class);
    AdaptPorts.uninstall(HudPort.class);
    AdaptPorts.uninstall(TpsPort.class);
    AdaptPorts.uninstall(ItemCooldownGroupPort.class);
    AdaptPorts.uninstall(PlayerInputPort.class);
    AdaptPorts.uninstall(CustomModelPort.class);
    AdaptPorts.uninstall(MaterialValuePort.class);
    AdaptPorts.uninstall(AdaptAttributeResolver.class);
  }
}
