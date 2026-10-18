package art.arcane.adapt.command;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.gui.GolemGuiActions;
import art.arcane.adapt.api.golem.gui.RosterGui;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.command.Feedback;
import art.arcane.adapt.util.director.specialhandlers.NullablePlayerHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.compat.BukkitDirectorContext;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

@Director(name = "golem", origin = DirectorOrigin.BOTH, description = "Golem roster and admin tools", descriptionKey = "command.help.golem_roster_and_admin_tools")
public class CommandGolem {
  static final String ADMIN_PERMISSION = "adapt.golemancy.admin";
  private static final String SKILL = "golemancy";
  private static final int SHORT_ID_LENGTH = 8;

  @Director(origin = DirectorOrigin.PLAYER, description = "Open your golem roster", descriptionKey = "command.help.open_your_golem_roster")
  public void roster() {
    RosterGui.open(BukkitDirectorContext.player());
  }

  @Director(description = "List a player's golems", descriptionKey = "command.help.list_a_player_s_golems")
  public void list(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    Player target = resolveAdminTarget(player, sender);
    if (target == null) {
      return;
    }
    CommandTargetExecutor.run(target, () -> listTarget(target, sender), sender);
  }

  @Director(description = "Release one of a player's golems", descriptionKey = "command.help.release_one_of_a_player_s_golems")
  public void release(
      @Param(description = "Golem id, or enough of its first characters to match one golem", descriptionKey = "command.help.golem_id_or_enough_of_its_first_characters_to_match_one_golem")
      String id,
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    Player target = resolveAdminTarget(player, sender);
    if (target == null) {
      return;
    }
    GolemRegistry registry = runningRegistry(sender);
    if (registry == null) {
      return;
    }
    CommandTargetExecutor.run(target, () -> releaseTarget(registry, id, target, sender), sender);
  }

  @Director(description = "Wake a player's loaded golems", descriptionKey = "command.help.wake_a_player_s_loaded_golems")
  public void wake(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    Player target = resolveAdminTarget(player, sender);
    if (target == null) {
      return;
    }
    GolemRegistry registry = runningRegistry(sender);
    if (registry == null) {
      return;
    }
    CommandTargetExecutor.run(target, () -> wakeTarget(registry, target, sender), sender);
  }

  private void listTarget(Player target, CommandSender sender) {
    GolemRoster roster = readyRoster(target, sender);
    if (roster == null) {
      return;
    }
    List<GolemRoster.GolemEntry> golems = RosterGui.entries(roster);
    if (golems.isEmpty()) {
      CommandTargetExecutor.send(sender, FConst.info(AdaptLanguage.text(CommandRuntimeMessages.GOLEM_LIST_EMPTY,
          untrusted("player", target.getName()))));
      return;
    }
    Feedback.FeedbackBuilder report = Feedback.builder().message(FConst.infoText(AdaptLanguage.text(CommandRuntimeMessages.GOLEM_LIST_HEADER,
        untrusted("player", target.getName()),
        trusted("count", golems.size()))));
    for (GolemRoster.GolemEntry entry : golems) {
      report.message(FConst.infoText(describe(entry)));
    }
    CommandTargetExecutor.send(sender, report.build());
  }

  private void releaseTarget(GolemRegistry registry, String id, Player target, CommandSender sender) {
    GolemRoster roster = readyRoster(target, sender);
    if (roster == null) {
      return;
    }
    List<GolemRoster.GolemEntry> matches = roster.matching(id);
    if (matches.size() != 1) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
          matches.isEmpty() ? CommandRuntimeMessages.GOLEM_NOT_FOUND : CommandRuntimeMessages.GOLEM_AMBIGUOUS,
          untrusted("player", target.getName()),
          untrusted("id", id))));
      return;
    }
    UUID golem = matches.getFirst().id();
    String shortId = shortId(golem);
    String name = target.getName();
    registry.forceRelease(target.getUniqueId(), golem).thenAccept(released -> CommandTargetExecutor.send(sender, released
        ? FConst.success(AdaptLanguage.text(CommandRuntimeMessages.GOLEM_RELEASED, trusted("id", shortId), untrusted("player", name)))
        : FConst.error(AdaptLanguage.text(CommandRuntimeMessages.GOLEM_RELEASE_FAILED, trusted("id", shortId), untrusted("player", name)))));
  }

  private void wakeTarget(GolemRegistry registry, Player target, CommandSender sender) {
    if (readyRoster(target, sender) == null) {
      return;
    }
    int loaded = registry.wake(target.getUniqueId());
    CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(CommandRuntimeMessages.GOLEMS_WOKEN,
        untrusted("player", target.getName()),
        trusted("count", loaded))));
  }

  private static String describe(GolemRoster.GolemEntry entry) {
    String golem = AdaptLanguage.text(GolemancyMessages.ROSTER_ENTRY,
        trusted("body", AdaptLanguage.text(entry.body().displayName())),
        trusted("core", AdaptLanguage.text(entry.core().displayName())));
    return AdaptLanguage.text(CommandRuntimeMessages.GOLEM_LIST_ENTRY,
        trusted("id", shortId(entry.id())),
        trusted("golem", golem),
        trusted("status", AdaptLanguage.text(GolemGuiActions.statusName(entry.status()))),
        untrusted("location", RosterGui.lastSeen(entry)));
  }

  private static String shortId(UUID id) {
    return id.toString().substring(0, SHORT_ID_LENGTH);
  }

  private static Player resolveAdminTarget(Player player, CommandSender sender) {
    if (!sender.hasPermission(ADMIN_PERMISSION)) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", ADMIN_PERMISSION))).send(sender);
      return null;
    }
    if (player != null) {
      return player;
    }
    if (sender instanceof Player senderPlayer) {
      return senderPlayer;
    }
    FConst.error(AdaptLanguage.text(CommandRuntimeMessages.PLAYER_REQUIRED_FROM_CONSOLE)).send(sender);
    return null;
  }

  private static GolemRegistry runningRegistry(CommandSender sender) {
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    if (registry.isEmpty()) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.SKILL_DISABLED, untrusted("skill", SKILL))).send(sender);
      return null;
    }
    return registry.get();
  }

  private static GolemRoster readyRoster(Player target, CommandSender sender) {
    AdaptServer server = Adapt.instance == null ? null : Adapt.instance.getAdaptServer();
    AdaptPlayer adaptPlayer = server == null ? null : server.getOnlineAdaptPlayer(target.getUniqueId());
    if (adaptPlayer != null && adaptPlayer.isRuntimeReady() && adaptPlayer.getPlayer() == target) {
      return adaptPlayer.getData().getGolemancy();
    }
    CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(CommandRuntimeMessages.ADAPT_SERVER_NOT_READY)));
    return null;
  }
}
