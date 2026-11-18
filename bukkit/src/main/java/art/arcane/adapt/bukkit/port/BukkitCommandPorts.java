package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.bukkit.command.BukkitCommandHelpPort;
import art.arcane.adapt.command.CommandHelpPort;
import art.arcane.adapt.command.DebugDumpPort;

public final class BukkitCommandPorts {
  private BukkitCommandPorts() {
  }

  public static void install() {
    AdaptPorts.install(CommandHelpPort.class, new BukkitCommandHelpPort());
    AdaptPorts.install(DebugDumpPort.class, new BukkitDebugDumpPort());
  }

  public static void uninstall() {
    AdaptPorts.uninstall(CommandHelpPort.class);
    AdaptPorts.uninstall(DebugDumpPort.class);
  }
}
