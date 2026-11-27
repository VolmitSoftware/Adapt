package art.arcane.adapt.api.command;

import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.permission.PermissionDefault;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A command an addon adds below {@code /adapt}, registered with
 * {@link art.arcane.adapt.api.addon.ContentRegistrar#command}. The same command, with the same words, exists on every
 * platform Adapt runs on, and leaves with its addon.
 *
 * <p>A command is a <b>group</b> of further commands, or a <b>command that runs</b> with typed
 * {@link CommandArgument}s, as Adapt's own commands are. Adapt's help menu lists it with its {@link #help()} text,
 * {@code /adapt <name> help} shows a group's page, tab completion offers its words and argument values, and an argument
 * may be given in order or as {@code name=value}.
 *
 * <pre>{@code
 * AdaptCommand.named("geology")
 *     .help(Text.key("example.command.geology"))
 *     .then(AdaptCommand.named("status")
 *         .help(Text.key("example.command.status"))
 *         .permission("example.geology.status")
 *         .argument(PLAYER)
 *         .executes(invocation -> ...))
 *     .build();
 * }</pre>
 */
@ApiStatus.AvailableSince("1.0.0")
public final class AdaptCommand {
  private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9_-]*");

  private final String name;
  private final List<String> aliases;
  private final Text help;
  private final Optional<String> permission;
  private final PermissionDefault permissionDefault;
  private final boolean playerOnly;
  private final List<CommandArgument<?>> arguments;
  private final List<AdaptCommand> children;
  private final Optional<CommandAction> action;

  private AdaptCommand(Builder builder) {
    this.name = builder.name;
    this.aliases = List.copyOf(builder.aliases);
    this.help = builder.help;
    this.permission = builder.permission;
    this.permissionDefault = builder.permissionDefault;
    this.playerOnly = builder.playerOnly;
    this.arguments = List.copyOf(builder.arguments);
    this.children = List.copyOf(builder.children);
    this.action = builder.action;
  }

  /** Starts a command; the name is lower case, {@code [a-z0-9_-]}, starting with a letter or digit. */
  public static Builder named(String name) {
    return new Builder(requireName(name));
  }

  /** The word that runs this command and names it in help. */
  public String name() {
    return name;
  }

  /** Further words that run this command, in the order they were added. */
  public List<String> aliases() {
    return aliases;
  }

  /** The words that run this command: its name, then its aliases. */
  public List<String> words() {
    List<String> words = new ArrayList<>(aliases.size() + 1);
    words.add(name);
    words.addAll(aliases);
    return List.copyOf(words);
  }

  /** One line for the help menu. */
  public Text help() {
    return help;
  }

  /** The permission a caller needs for this command and everything below it; the console always has it. */
  public Optional<String> permission() {
    return permission;
  }

  /** Who has {@link #permission()} when no permission plugin says otherwise; Adapt registers the node with it. */
  public PermissionDefault permissionDefault() {
    return permissionDefault;
  }

  /** Whether only a player may run it; the console is told so. */
  public boolean playerOnly() {
    return playerOnly;
  }

  /** The arguments of a command that runs, in order; empty for a group. */
  public List<CommandArgument<?>> arguments() {
    return arguments;
  }

  /** The commands of a group; empty for a command that runs. */
  public List<AdaptCommand> children() {
    return children;
  }

  /** Whether this is a group of commands rather than a command that runs. */
  public boolean isGroup() {
    return action.isEmpty();
  }

  /** What runs; empty for a group. */
  public Optional<CommandAction> action() {
    return action;
  }

  @Override
  public String toString() {
    return "AdaptCommand[" + name + "]";
  }

  private static String requireName(String name) {
    Objects.requireNonNull(name, "name");
    if (!NAME.matcher(name).matches()) {
      throw new IllegalArgumentException("Invalid command name '" + name + "': use lower case a-z, 0-9, _ and -");
    }
    return name;
  }

  /** Builds one {@link AdaptCommand}. */
  public static final class Builder {
    private final String name;
    private final List<String> aliases = new ArrayList<>();
    private Text help;
    private Optional<String> permission = Optional.empty();
    private PermissionDefault permissionDefault = PermissionDefault.OP;
    private boolean playerOnly;
    private final List<CommandArgument<?>> arguments = new ArrayList<>();
    private final List<AdaptCommand> children = new ArrayList<>();
    private Optional<CommandAction> action = Optional.empty();

    private Builder(String name) {
      this.name = name;
    }

    /** Another word for the command, under the same rules as the name. */
    public Builder alias(String alias) {
      aliases.add(requireName(alias));
      return this;
    }

    /** Required. Usually a key in the addon's own messages. */
    public Builder help(Text text) {
      this.help = Objects.requireNonNull(text, "text");
      return this;
    }

    /** A permission node that operators have by default. */
    public Builder permission(String node) {
      return permission(node, PermissionDefault.OP);
    }

    /**
     * A permission node, stored in lower case, that callers have by {@code fallback} when no permission plugin says
     * otherwise.
     *
     * @throws IllegalArgumentException when the node is blank
     */
    public Builder permission(String node, PermissionDefault fallback) {
      Objects.requireNonNull(node, "node");
      if (node.isBlank()) {
        throw new IllegalArgumentException("A command permission cannot be blank");
      }
      this.permission = Optional.of(node.toLowerCase(Locale.ROOT));
      this.permissionDefault = Objects.requireNonNull(fallback, "fallback");
      return this;
    }

    /** Lets only players run the command. */
    public Builder playerOnly() {
      this.playerOnly = true;
      return this;
    }

    /** Adds the next argument. Optional arguments come after every required one, and a rest-of-line argument last. */
    public Builder argument(CommandArgument<?> argument) {
      Objects.requireNonNull(argument, "argument");
      if (!arguments.isEmpty()) {
        CommandArgument<?> previous = arguments.get(arguments.size() - 1);
        if (previous.type().consumesRest()) {
          throw new IllegalArgumentException("Argument " + previous.name() + " of " + name + " takes the rest of the line");
        }
        if (previous.isOptional() && !argument.isOptional()) {
          throw new IllegalArgumentException("Required argument " + argument.name() + " of " + name
              + " follows an optional one");
        }
      }
      for (CommandArgument<?> existing : arguments) {
        if (existing.name().equals(argument.name())) {
          throw new IllegalArgumentException("Command " + name + " has two arguments named " + argument.name());
        }
      }
      arguments.add(argument);
      return this;
    }

    /** Adds a command to this group. */
    public Builder then(AdaptCommand child) {
      children.add(Objects.requireNonNull(child, "child"));
      return this;
    }

    /** Makes this a command that runs. */
    public Builder executes(CommandAction action) {
      this.action = Optional.of(Objects.requireNonNull(action, "action"));
      return this;
    }

    /**
     * Throws {@link IllegalStateException} without help text, for a group without commands or with arguments, for a
     * command that both runs and has children, and when two children share a word.
     */
    public AdaptCommand build() {
      if (help == null) {
        throw new IllegalStateException("Command " + name + " needs help text");
      }
      if (action.isPresent() && !children.isEmpty()) {
        throw new IllegalStateException("Command " + name + " both runs and has subcommands");
      }
      if (action.isEmpty() && children.isEmpty()) {
        throw new IllegalStateException("Command " + name + " neither runs nor has subcommands");
      }
      if (action.isEmpty() && !arguments.isEmpty()) {
        throw new IllegalStateException("Command group " + name + " cannot take arguments");
      }
      Set<String> words = new HashSet<>();
      for (AdaptCommand child : children) {
        for (String word : child.words()) {
          if (!words.add(word)) {
            throw new IllegalStateException("Two subcommands of " + name + " use the word " + word);
          }
        }
      }
      return new AdaptCommand(this);
    }
  }
}
