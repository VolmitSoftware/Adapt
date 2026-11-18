package art.arcane.adapt.bukkit.command;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.command.CommandSource;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;

/** A Bukkit command sender as the core command tree sees it. */
public record BukkitCommandSource(CommandSender sender) implements CommandSource {
  public BukkitCommandSource {
    Objects.requireNonNull(sender, "sender");
  }

  /** The Bukkit sender behind a source this platform created. */
  public static CommandSender sender(CommandSource source) {
    if (source instanceof BukkitCommandSource bukkit) {
      return bukkit.sender();
    }
    throw new IllegalArgumentException("Not a Bukkit command source: " + source.name());
  }

  @Override
  public String name() {
    return sender.getName();
  }

  @Override
  public Optional<PlayerRef> player() {
    return sender instanceof Player player ? Optional.of(BukkitRefs.player(player)) : Optional.empty();
  }

  @Override
  public boolean hasPermission(String node) {
    return sender.hasPermission(node);
  }

  @Override
  public boolean isOp() {
    return sender.isOp();
  }

  @Override
  public void sendSection(String sectionText) {
    ComponentMessenger.sendSection(sender, sectionText);
  }

  @Override
  public void sendLiteral(String plainText) {
    ComponentMessenger.sendLiteral(sender, plainText);
  }

  @Override
  public void sendMarkup(String markup) {
    ComponentMessenger.sendMarkup(sender, markup);
  }
}
