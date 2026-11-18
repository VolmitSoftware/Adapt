package art.arcane.adapt.command;

import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.Optional;

/**
 * Whoever issued an Adapt command: a player or the console. Messages may be sent from any thread; implementations hop
 * to the right thread where the platform requires it.
 */
public interface CommandSource {
  String name();

  /** The player behind the command, or empty for the console and other non-player sources. */
  Optional<PlayerRef> player();

  boolean hasPermission(String node);

  /** Operator status; the console counts as an operator. */
  boolean isOp();

  /** Legacy section-coded text; MiniMessage tags in it stay literal. */
  void sendSection(String sectionText);

  /** Plain text without any formatting. */
  void sendLiteral(String plainText);

  /** Trusted Adapt markup (MiniMessage tags and section codes). */
  void sendMarkup(String markup);
}
