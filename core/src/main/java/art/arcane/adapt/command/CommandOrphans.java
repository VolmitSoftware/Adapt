package art.arcane.adapt.command;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.player.OrphanedLine;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.engine.player.Orphans;
import art.arcane.adapt.engine.player.PlayerApi;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.command.Feedback;
import art.arcane.adapt.util.director.specialhandlers.NullablePlayerHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.regex.Pattern;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

@CommandPermission(CommandOrphans.PERMISSION)
@Director(name = "orphans", origin = DirectorOrigin.BOTH, description = "Saved progress whose addon is not installed", descriptionKey = "adapt.command.help.orphans")
public class CommandOrphans {
  public static final String PERMISSION = "adapt.orphans";
  private static final String EVERY_NAMESPACE = "*";
  private static final String EVERY_PLAYER = "---";
  private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");

  @Director(description = "List saved progress of a namespace that no installed content uses", descriptionKey = "adapt.command.help.orphans_list")
  public void list(
      @Param(description = "The namespace of the missing addon, or * for every one", descriptionKey = "adapt.command.help.orphan_namespace")
      String namespace,
      @Param(description = "Online player, defaults to every online player", defaultValue = EVERY_PLAYER, customHandler = NullablePlayerHandler.class, descriptionKey = "adapt.command.help.orphan_player")
      PlayerRef player
  ) {
    CommandSource sender = CommandContext.source();
    String filter = namespace(namespace, sender);
    if (filter == null) {
      return;
    }
    for (PlayerRef target : targets(player)) {
      CommandTargetExecutor.run(target, () -> {
        PlayerProfile profile = PlayerApi.directory().find(target).orElse(null);
        if (profile != null) {
          for (Feedback line : listing(profile, filter)) {
            CommandTargetExecutor.send(sender, line);
          }
        }
      }, sender);
    }
  }

  @Director(description = "Delete a namespace's orphaned adaptations and refund their knowledge", descriptionKey = "adapt.command.help.orphans_refund")
  public void refund(
      @Param(description = "The namespace of the missing addon, or * for every one", descriptionKey = "adapt.command.help.orphan_namespace")
      String namespace,
      @Param(description = "Online player, defaults to every online player", defaultValue = EVERY_PLAYER, customHandler = NullablePlayerHandler.class, descriptionKey = "adapt.command.help.orphan_player")
      PlayerRef player
  ) {
    CommandSource sender = CommandContext.source();
    String filter = namespace(namespace, sender);
    if (filter == null) {
      return;
    }
    for (PlayerRef target : targets(player)) {
      CommandTargetExecutor.run(target, () -> {
        PlayerProfile profile = PlayerApi.directory().find(target).orElse(null);
        if (profile != null) {
          long refunded = 0L;
          for (String each : namespaces(profile, filter)) {
            refunded += profile.refundOrphans(each);
          }
          CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(CommandRuntimeMessages.ORPHANS_REFUNDED,
              trusted("knowledge", refunded), untrusted("namespace", filter), untrusted("player", target.name()))));
        }
      }, sender);
    }
  }

  @Director(description = "Delete all of a namespace's orphaned progress", descriptionKey = "adapt.command.help.orphans_purge")
  public void purge(
      @Param(description = "The namespace of the missing addon, or * for every one", descriptionKey = "adapt.command.help.orphan_namespace")
      String namespace,
      @Param(description = "Online player, defaults to every online player", defaultValue = EVERY_PLAYER, customHandler = NullablePlayerHandler.class, descriptionKey = "adapt.command.help.orphan_player")
      PlayerRef player
  ) {
    CommandSource sender = CommandContext.source();
    String filter = namespace(namespace, sender);
    if (filter == null) {
      return;
    }
    for (PlayerRef target : targets(player)) {
      CommandTargetExecutor.run(target, () -> {
        PlayerProfile profile = PlayerApi.directory().find(target).orElse(null);
        if (profile != null) {
          int purged = 0;
          for (String each : namespaces(profile, filter)) {
            purged += profile.purgeOrphans(each);
          }
          CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(CommandRuntimeMessages.ORPHANS_PURGED,
              trusted("count", purged), untrusted("namespace", filter), untrusted("player", target.name()))));
        }
      }, sender);
    }
  }

  /** What {@code list} says for one player: each orphan of the namespace, or that there is none. */
  static List<Feedback> listing(PlayerProfile profile, String namespace) {
    List<Feedback> lines = new ArrayList<>();
    String player = profile.player().name();
    for (OrphanedLine orphan : profile.orphans()) {
      if (!EVERY_NAMESPACE.equals(namespace) && !orphan.namespace().equals(namespace)) {
        continue;
      }
      if (orphan.adaptation().isPresent()) {
        Map.Entry<Id, Integer> held = orphan.adaptations().entrySet().iterator().next();
        lines.add(FConst.info(AdaptLanguage.text(CommandRuntimeMessages.ORPHAN_ADAPTATION, untrusted("player", player),
            untrusted("adaptation", held.getKey().toString()), trusted("level", held.getValue()),
            untrusted("line", Orphans.saveKey(orphan.line())), trusted("knowledge", orphan.knowledge()))));
      } else {
        lines.add(FConst.info(AdaptLanguage.text(CommandRuntimeMessages.ORPHAN_LINE, untrusted("player", player),
            untrusted("line", Orphans.saveKey(orphan.line())), untrusted("namespace", orphan.namespace()),
            trusted("xp", Math.round(orphan.xp())), trusted("knowledge", orphan.knowledge()),
            trusted("adaptations", orphan.adaptations().size()))));
      }
    }
    if (lines.isEmpty()) {
      lines.add(FConst.info(AdaptLanguage.text(CommandRuntimeMessages.ORPHANS_NONE, untrusted("player", player),
          untrusted("namespace", namespace))));
    }
    return lines;
  }

  /** {@code filter} itself, or for {@code *} every namespace the profile holds orphans of. */
  static Set<String> namespaces(PlayerProfile profile, String filter) {
    if (!EVERY_NAMESPACE.equals(filter)) {
      return Set.of(filter);
    }
    Set<String> namespaces = new LinkedHashSet<>();
    for (OrphanedLine orphan : profile.orphans()) {
      namespaces.add(orphan.namespace());
    }
    return namespaces;
  }

  /** The namespace typed, lower-cased; null after telling the sender why when it is not allowed. */
  private static String namespace(String typed, CommandSource sender) {
    if (!sender.hasPermission(PERMISSION)) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", PERMISSION)))
          .send(sender);
      return null;
    }
    String namespace = typed == null ? "" : typed.trim().toLowerCase(Locale.ROOT);
    if (namespace.isEmpty() || (!EVERY_NAMESPACE.equals(namespace) && !NAMESPACE.matcher(namespace).matches())) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.ORPHAN_NAMESPACE_REQUIRED)).send(sender);
      return null;
    }
    return namespace;
  }

  private static List<PlayerRef> targets(PlayerRef player) {
    if (player != null) {
      return List.of(player);
    }
    if (AdaptCore.current() == null) {
      return List.of();
    }
    List<PlayerRef> online = new ArrayList<>();
    for (PlayerProfile profile : PlayerApi.directory().online()) {
      online.add(profile.player());
    }
    return online;
  }
}
