package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.bukkit.command.BukkitCommandSource;
import art.arcane.adapt.command.CommandSource;
import art.arcane.adapt.command.DebugDumpPort;

/** {@code /adapt debug dump} through VolmLib's Bukkit diagnostic report. */
public final class BukkitDebugDumpPort implements DebugDumpPort {
  @Override
  public void request(CommandSource source, boolean upload) {
    Adapt.instance.debugDump().request(BukkitCommandSource.sender(source), upload);
  }
}
