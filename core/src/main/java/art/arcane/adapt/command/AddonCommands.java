package art.arcane.adapt.command;

import art.arcane.adapt.api.command.AdaptCommand;
import art.arcane.adapt.api.internal.HostPermissions;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.compat.DirectorAnnotationCompatibility;
import art.arcane.volmlib.util.director.runtime.DirectorNodeDescriptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The commands addons add below {@code /adapt}, by addon namespace. {@link AdaptCommands} grafts them into its Director
 * tree, so help pages and command words work as for Adapt's own commands, and runs them through
 * {@link AddonCommandRunner}. Kept across core restarts only while an addon is applied: the addon lifecycle installs
 * and removes them.
 */
public final class AddonCommands {
  private static final Map<String, List<AdaptCommand>> BY_NAMESPACE = new LinkedHashMap<>();
  private static final AtomicLong REVISION = new AtomicLong();
  private static volatile Set<String> builtinWords;

  private AddonCommands() {
  }

  /**
   * Throws {@link IllegalStateException} when a word of {@code commands} is Adapt's or another addon's, or when a
   * permission node does not start with {@code <namespace>.}.
   */
  public static synchronized void requireFree(String namespace, List<AdaptCommand> commands) {
    Set<String> seen = new LinkedHashSet<>();
    for (AdaptCommand command : commands) {
      requireOwnPermissions(namespace, command);
      for (String word : command.words()) {
        if (builtinWords().contains(word)) {
          throw new IllegalStateException("Addon " + namespace + " registers the command /adapt " + word
              + ", which is one of Adapt's own commands");
        }
        if (!seen.add(word)) {
          throw new IllegalStateException("Addon " + namespace + " registers the command /adapt " + word + " twice");
        }
        for (Map.Entry<String, List<AdaptCommand>> entry : BY_NAMESPACE.entrySet()) {
          if (entry.getKey().equals(namespace)) {
            continue;
          }
          for (AdaptCommand other : entry.getValue()) {
            if (other.words().contains(word)) {
              throw new IllegalStateException("Addon " + namespace + " registers the command /adapt " + word
                  + ", which addon " + entry.getKey() + " already has");
            }
          }
        }
      }
    }
  }

  /** Adds an addon's commands and registers their permissions; replaces what the namespace had. */
  public static synchronized void install(String namespace, List<AdaptCommand> commands, HostPermissions permissions) {
    uninstall(namespace, permissions);
    if (commands.isEmpty()) {
      return;
    }
    requireFree(namespace, commands);
    BY_NAMESPACE.put(namespace, List.copyOf(commands));
    if (permissions != null) {
      for (AdaptCommand command : commands) {
        registerPermissions(command, permissions);
      }
    }
    REVISION.incrementAndGet();
  }

  public static synchronized void uninstall(String namespace, HostPermissions permissions) {
    List<AdaptCommand> removed = BY_NAMESPACE.remove(namespace);
    if (removed == null) {
      return;
    }
    if (permissions != null) {
      for (AdaptCommand command : removed) {
        unregisterPermissions(command, permissions);
      }
    }
    REVISION.incrementAndGet();
  }

  /** Every addon's commands are gone: the core stopped. */
  public static synchronized void clear() {
    if (!BY_NAMESPACE.isEmpty()) {
      BY_NAMESPACE.clear();
      REVISION.incrementAndGet();
    }
  }

  /** Changes on every install and removal. */
  public static long revision() {
    return REVISION.get();
  }

  /** The installed commands with the namespace of the addon that registered each. */
  public static synchronized Map<String, List<AdaptCommand>> installed() {
    return Map.copyOf(BY_NAMESPACE);
  }

  private static void requireOwnPermissions(String namespace, AdaptCommand command) {
    Optional<String> node = command.permission();
    if (node.isPresent() && !node.get().startsWith(namespace + ".")) {
      throw new IllegalStateException("Addon " + namespace + " gives /adapt " + command.name() + " the permission node "
          + node.get() + ", which must start with " + namespace + ".");
    }
    for (AdaptCommand child : command.children()) {
      requireOwnPermissions(namespace, child);
    }
  }

  private static void registerPermissions(AdaptCommand command, HostPermissions permissions) {
    if (command.permission().isPresent()) {
      permissions.add(command.permission().get(), "Allows /adapt " + command.name(), command.permissionDefault());
    }
    for (AdaptCommand child : command.children()) {
      registerPermissions(child, permissions);
    }
  }

  private static void unregisterPermissions(AdaptCommand command, HostPermissions permissions) {
    if (command.permission().isPresent()) {
      permissions.unregister(command.permission().get());
    }
    for (AdaptCommand child : command.children()) {
      unregisterPermissions(child, permissions);
    }
  }

  /** The words of Adapt's own top-level commands, read from the {@code @Director} tree of {@link CommandAdapt}. */
  static Set<String> builtinWords() {
    Set<String> words = builtinWords;
    if (words != null) {
      return words;
    }
    Set<String> found = new LinkedHashSet<>(List.of(AdaptCommands.LANGUAGE, "help"));
    for (Field field : CommandAdapt.class.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers()) && field.getType().isAnnotationPresent(Director.class)) {
        DirectorAnnotationCompatibility.fromType(field.getType()).ifPresent((DirectorNodeDescriptor descriptor) -> addWords(found, descriptor));
      }
    }
    for (Method method : CommandAdapt.class.getDeclaredMethods()) {
      if (!Modifier.isStatic(method.getModifiers()) && method.isAnnotationPresent(Director.class)) {
        DirectorAnnotationCompatibility.fromMethod(method).ifPresent((DirectorNodeDescriptor descriptor) -> addWords(found, descriptor));
      }
    }
    words = Set.copyOf(found);
    builtinWords = words;
    return words;
  }

  private static void addWords(Set<String> words, DirectorNodeDescriptor descriptor) {
    words.add(descriptor.getName().toLowerCase(Locale.ROOT));
    for (String alias : descriptor.getAliases()) {
      words.add(alias.toLowerCase(Locale.ROOT));
    }
  }
}
