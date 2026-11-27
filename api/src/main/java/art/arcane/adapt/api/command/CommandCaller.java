package art.arcane.adapt.api.command;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;

/** Whoever typed a command: a player or the console. */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface CommandCaller {
  /** The sender's name as the platform reports it: the player's name, or the console's. */
  String name();

  /** The player, or empty for the console and other non-player senders. */
  Optional<PlayerRef> player();

  /** The console has every permission. */
  boolean hasPermission(String node);

  /** Whether the sender is a server operator; the console is. */
  @ApiStatus.Experimental
  boolean isOperator();

  /** Sends a message in the caller's language; the console reads the server's. Safe from any thread. */
  void send(Text text);
}
