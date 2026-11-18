package art.arcane.adapt.command;

import art.arcane.adapt.platform.entity.PlayerRef;

/** The source of the Adapt command running on this thread, set around each Director invocation. */
public final class CommandContext {
  private static final ThreadLocal<CommandSource> CURRENT = new ThreadLocal<>();

  private CommandContext() {
  }

  public static void touch(CommandSource source) {
    CURRENT.set(source);
  }

  public static void remove() {
    CURRENT.remove();
  }

  public static CommandSource source() {
    CommandSource source = CURRENT.get();
    if (source == null) {
      throw new IllegalStateException("No Adapt command is running on this thread");
    }
    return source;
  }

  /** The player running the command, or null for the console. */
  public static PlayerRef player() {
    return source().player().orElse(null);
  }

  public static boolean isPlayer() {
    return source().player().isPresent();
  }

  public static boolean hasPermission(String node) {
    return source().hasPermission(node);
  }
}
