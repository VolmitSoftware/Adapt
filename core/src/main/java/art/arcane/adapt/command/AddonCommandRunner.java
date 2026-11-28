package art.arcane.adapt.command;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.command.AdaptCommand;
import art.arcane.adapt.api.command.CommandArgument;
import art.arcane.adapt.api.command.CommandCaller;
import art.arcane.adapt.api.command.CommandInvocation;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionMode;
import art.arcane.volmlib.util.director.runtime.DirectorNodeDescriptor;
import art.arcane.volmlib.util.director.runtime.DirectorParameterDescriptor;
import art.arcane.volmlib.util.director.runtime.DirectorRoute;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeMessages;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

/**
 * Runs addon commands. Each {@link AdaptCommand} is grafted into Adapt's Director tree as a {@link Graft} node, so
 * Director routes its words, draws its help page and completes its subcommand names exactly as for Adapt's own
 * commands; the arguments of a command that runs are parsed and completed here from its {@link CommandArgument}s.
 */
final class AddonCommandRunner {
  private static final Method MARKER;

  static {
    try {
      MARKER = Graft.class.getDeclaredMethod("marker");
    } catch (NoSuchMethodException missing) {
      throw new ExceptionInInitializerError(missing);
    }
  }

  private AddonCommandRunner() {
  }

  /** Adds every installed addon command below {@code root}. */
  static void graftAll(DirectorRuntimeNode root) {
    for (List<AdaptCommand> commands : AddonCommands.installed().values()) {
      for (AdaptCommand command : commands) {
        root.addChild(graft(command, root));
      }
    }
  }

  /** The addon command a route ends on, if it ends on one. */
  static Optional<AdaptCommand> command(DirectorRoute route) {
    if (route == null || route.node() == null || !(route.node().getInstance() instanceof Graft graft)) {
      return Optional.empty();
    }
    return Optional.of(graft.command());
  }

  /** Runs the addon command {@code route} ends on; false after telling the source why it did not run. */
  static boolean run(CommandSource source, DirectorRoute route) {
    AdaptCommand command = command(route).orElseThrow();
    List<String> path = path(route.node());
    Optional<String> denied = deniedPermission(source, route.node());
    if (denied.isPresent()) {
      error(source, AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", denied.get())));
      return false;
    }
    if (command.isGroup()) {
      error(source, AdaptLanguage.text(CommandRuntimeMessages.UNKNOWN_ADAPT_COMMAND));
      return false;
    }
    if (command.playerOnly() && source.player().isEmpty()) {
      error(source, AdaptLanguage.text(CommandRuntimeMessages.PLAYER_ONLY));
      return false;
    }
    Map<CommandArgument<?>, Object> values = new LinkedHashMap<>();
    Optional<String> problem = parse(source, command, route.remainingArgs(), values);
    if (problem.isPresent()) {
      error(source, problem.get());
      return false;
    }
    Invocation invocation = new Invocation(new Caller(source), path, command.arguments(), Map.copyOf(values));
    Tasks.s(() -> {
      try (LanguageAudience.Scope audience = LanguageAudience.open(audience(source))) {
        command.action().orElseThrow().run(invocation);
      } catch (RuntimeException | LinkageError failure) {
        AdaptLog.warn("Addon command /" + String.join(" ", path) + " failed", failure);
        error(source, director(DirectorRuntimeMessages.EXECUTION_FAILED, untrusted("command", "/" + String.join(" ", path)),
            untrusted("reason", failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage())));
      }
    });
    return true;
  }

  /** Completions for the last of {@code words}, which follow the addon command {@code route} ends on. */
  static List<String> complete(CommandSource source, DirectorRoute route, String typed) {
    AdaptCommand command = command(route).orElseThrow();
    if (command.isGroup() || deniedPermission(source, route.node()).isPresent()) {
      return List.of();
    }
    Caller caller = new Caller(source);
    String current = typed == null ? "" : typed;
    int split = current.indexOf('=');
    if (split > 0) {
      CommandArgument<?> keyed = byName(command, current.substring(0, split));
      if (keyed == null) {
        return List.of();
      }
      String prefix = current.substring(0, split + 1);
      List<String> keyedSuggestions = new ArrayList<>();
      for (String value : matching(keyed.type().suggest(caller, current.substring(split + 1)), current.substring(split + 1))) {
        keyedSuggestions.add(prefix + value);
      }
      return keyedSuggestions;
    }
    List<CommandArgument<?>> open = new ArrayList<>(command.arguments());
    int positional = 0;
    for (String word : route.remainingArgs()) {
      int equals = word.indexOf('=');
      CommandArgument<?> keyed = equals > 0 ? byName(command, word.substring(0, equals)) : null;
      if (keyed != null) {
        open.remove(keyed);
      } else {
        positional++;
      }
    }
    List<String> suggestions = new ArrayList<>();
    if (!open.isEmpty()) {
      CommandArgument<?> next = open.get(Math.min(positional, open.size() - 1));
      if (positional < open.size() || next.type().consumesRest()) {
        suggestions.addAll(matching(next.type().suggest(caller, current), current));
      }
    }
    return suggestions;
  }

  private static Optional<String> parse(CommandSource source, AdaptCommand command, List<String> words,
                                        Map<CommandArgument<?>, Object> values) {
    Map<CommandArgument<?>, String> raw = new LinkedHashMap<>();
    List<String> positional = new ArrayList<>();
    for (String word : words) {
      if (word == null || word.isEmpty()) {
        continue;
      }
      int split = word.indexOf('=');
      CommandArgument<?> keyed = split > 0 ? byName(command, word.substring(0, split)) : null;
      if (keyed != null && !raw.containsKey(keyed)) {
        raw.put(keyed, word.substring(split + 1));
      } else {
        positional.add(word);
      }
    }
    int next = 0;
    for (CommandArgument<?> argument : command.arguments()) {
      if (raw.containsKey(argument) || next >= positional.size()) {
        continue;
      }
      if (argument.type().consumesRest()) {
        raw.put(argument, String.join(" ", positional.subList(next, positional.size())));
        next = positional.size();
      } else {
        raw.put(argument, positional.get(next++));
      }
    }
    if (next < positional.size()) {
      return Optional.of(director(DirectorRuntimeMessages.UNEXPECTED_ARGUMENT, untrusted("argument", positional.get(next))));
    }
    Caller caller = new Caller(source);
    for (CommandArgument<?> argument : command.arguments()) {
      String text = raw.get(argument);
      String type = argument.type().valueType().getSimpleName();
      if (text == null) {
        if (!argument.isOptional()) {
          return Optional.of(director(DirectorRuntimeMessages.MISSING_ARGUMENT, untrusted("parameter", argument.name()),
              untrusted("type", type)));
        }
        continue;
      }
      Optional<?> value = argument.type().parse(caller, text);
      if (value == null || value.isEmpty()) {
        return Optional.of(director(DirectorRuntimeMessages.CONVERSION_FAILED, untrusted("value", text),
            untrusted("type", type), untrusted("parameter", argument.name())));
      }
      values.put(argument, value.get());
    }
    return Optional.empty();
  }

  private static CommandArgument<?> byName(AdaptCommand command, String name) {
    for (CommandArgument<?> argument : command.arguments()) {
      if (argument.name().equalsIgnoreCase(name)) {
        return argument;
      }
    }
    return null;
  }

  private static List<String> matching(List<String> offered, String typed) {
    String lowered = typed.toLowerCase(Locale.ROOT);
    List<String> kept = new ArrayList<>();
    for (String option : offered) {
      if (option != null && option.toLowerCase(Locale.ROOT).startsWith(lowered)) {
        kept.add(option);
      }
    }
    return kept;
  }

  /** True when {@code node} is an addon command or group. */
  static boolean isGraft(DirectorRuntimeNode node) {
    return node.getInstance() instanceof Graft;
  }

  /** The first permission along the route's addon commands that the source lacks. */
  static Optional<String> deniedPermission(CommandSource source, DirectorRuntimeNode node) {
    List<AdaptCommand> chain = new ArrayList<>();
    for (DirectorRuntimeNode cursor = node; cursor != null && cursor.getInstance() instanceof Graft graft;
         cursor = cursor.getParent()) {
      chain.add(0, graft.command());
    }
    for (AdaptCommand step : chain) {
      if (step.permission().isPresent() && !source.hasPermission(step.permission().get())) {
        return step.permission();
      }
    }
    return Optional.empty();
  }

  private static List<String> path(DirectorRuntimeNode node) {
    List<String> words = new ArrayList<>();
    for (DirectorRuntimeNode cursor = node; cursor != null; cursor = cursor.getParent()) {
      words.add(0, cursor.getDescriptor().getName());
    }
    return List.copyOf(words);
  }

  private static DirectorRuntimeNode graft(AdaptCommand command, DirectorRuntimeNode parent) {
    List<DirectorParameterDescriptor> parameters = new ArrayList<>();
    for (CommandArgument<?> argument : command.arguments()) {
      Text help = argument.help();
      parameters.add(new DirectorParameterDescriptor(argument.name(), help.isLiteral() ? "" : help.key(),
          help.resolve(), argument.type().valueType(), !argument.isOptional(), false, false, null, List.of()));
    }
    Text help = command.help();
    DirectorNodeDescriptor descriptor = new DirectorNodeDescriptor(command.name(), help.isLiteral() ? "" : help.key(),
        help.resolve(), command.aliases(), command.playerOnly() ? DirectorOrigin.PLAYER : DirectorOrigin.BOTH,
        DirectorExecutionMode.SYNC, command.isGroup(), false, parameters);
    DirectorRuntimeNode node = new DirectorRuntimeNode(descriptor, parent, new Graft(command),
        command.isGroup() ? null : MARKER, List.of());
    for (AdaptCommand child : command.children()) {
      node.addChild(graft(child, node));
    }
    return node;
  }

  private static String director(TextKey key, MessageArgument... arguments) {
    return AdaptLanguage.directorResolver().resolve(key, arguments);
  }

  private static void error(CommandSource source, String message) {
    source.sendSection(C.RED + message);
  }

  private static UUID audience(CommandSource source) {
    return source.player().map(PlayerRef::uuid).orElse(null);
  }

  /**
   * The Director node of an addon command. Director lists a node as a runnable command only when it has a method;
   * addon commands always run through {@link AddonCommandRunner}, so the method does nothing.
   */
  record Graft(AdaptCommand command) {
    @SuppressWarnings("unused")
    void marker() {
    }
  }

  private record Caller(CommandSource source) implements CommandCaller {
    @Override
    public String name() {
      return source.name();
    }

    @Override
    public Optional<PlayerRef> player() {
      return source.player();
    }

    @Override
    public boolean hasPermission(String node) {
      return source.hasPermission(node);
    }

    @Override
    public boolean isOperator() {
      return source.isOp();
    }

    @Override
    public void send(Text text) {
      Optional<PlayerRef> player = source.player();
      source.sendSection(player.isPresent() ? text.resolve(player.get()) : text.resolve());
    }
  }

  private record Invocation(CommandCaller caller, List<String> path, List<CommandArgument<?>> declared,
                            Map<CommandArgument<?>, Object> values) implements CommandInvocation {
    @Override
    public <T> T get(CommandArgument<T> argument) {
      return find(argument).orElseThrow(() -> new IllegalStateException("Argument " + argument.name()
          + " was left out; read an optional argument with find"));
    }

    @Override
    public <T> Optional<T> find(CommandArgument<T> argument) {
      if (!declared.contains(argument)) {
        throw new IllegalArgumentException("Command /" + String.join(" ", path) + " has no argument " + argument.name());
      }
      Object value = values.get(argument);
      return value == null ? Optional.empty() : Optional.of(argument.type().valueType().cast(value));
    }
  }
}
