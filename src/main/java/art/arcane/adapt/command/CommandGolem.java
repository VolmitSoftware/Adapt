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
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.director.specialhandlers.NullableOfflinePlayerHandler;
import art.arcane.adapt.util.director.specialhandlers.NullablePlayerHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.compat.BukkitDirectorContext;
import org.bukkit.OfflinePlayer;
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
    if (runningRegistry(BukkitDirectorContext.sender()) == null) {
      return;
    }
    RosterGui.open(BukkitDirectorContext.player());
  }

  @Director(description = "List a player's golems", descriptionKey = "command.help.list_a_player_s_golems")
  public void list(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullableOfflinePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      OfflinePlayer player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    OfflinePlayer target = anyAdminTarget(player, sender);
    if (target == null) {
      return;
    }
    Player online = target.getPlayer();
    if (online != null) {
      CommandTargetExecutor.run(online, () -> listOnline(online, sender), sender);
      return;
    }
    J.a(() -> listOffline(target, sender));
  }

  @Director(description = "Release one of a player's golems", descriptionKey = "command.help.release_one_of_a_player_s_golems")
  public void release(
      @Param(description = "Golem id, or enough of its first characters to match one golem", descriptionKey = "command.help.golem_id_or_enough_of_its_first_characters_to_match_one_golem")
      String id,
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    Player target = onlineAdminTarget(player, sender);
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
    Player target = onlineAdminTarget(player, sender);
    if (target == null) {
      return;
    }
    GolemRegistry registry = runningRegistry(sender);
    if (registry == null) {
      return;
    }
    CommandTargetExecutor.run(target, () -> wakeTarget(registry, target, sender), sender);
  }

  private void listOnline(Player target, CommandSender sender) {
    GolemRoster roster = readyRoster(target, sender);
    if (roster != null) {
      report(roster, target.getName(), sender);
    }
  }

  private void listOffline(OfflinePlayer target, CommandSender sender) {
    AdaptServer server = Adapt.instance == null ? null : Adapt.instance.getAdaptServer();
    if (server == null) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(CommandRuntimeMessages.ADAPT_SERVER_NOT_READY)));
      return;
    }
    report(server.peekData(target.getUniqueId()).getGolemancy(), nameOf(target), sender);
  }

  private void report(GolemRoster roster, String name, CommandSender sender) {
    List<GolemRoster.GolemEntry> golems = RosterGui.entries(roster);
    if (golems.isEmpty()) {
      CommandTargetExecutor.send(sender, FConst.info(AdaptLanguage.text(CommandRuntimeMessages.GOLEM_LIST_EMPTY, untrusted("player", name))));
      return;
    }
    String header = golems.size() == 1
        ? AdaptLanguage.text(CommandRuntimeMessages.GOLEM_LIST_HEADER_ONE, untrusted("player", name))
        : AdaptLanguage.text(CommandRuntimeMessages.GOLEM_LIST_HEADER, untrusted("player", name), trusted("count", golems.size()));
    Feedback.FeedbackBuilder report = Feedback.builder().message(FConst.infoText(header));
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
    registry.releaseFor(target.getUniqueId(), golem).thenAccept(release -> CommandTargetExecutor.send(sender, switch (release) {
      case RELEASED -> FConst.success(AdaptLanguage.text(CommandRuntimeMessages.GOLEM_RELEASED, trusted("id", shortId), untrusted("player", name)));
      case ACTIVE_UNLOADED -> FConst.error(AdaptLanguage.text(CommandRuntimeMessages.GOLEM_RELEASE_ACTIVE_UNLOADED, trusted("id", shortId),
          untrusted("player", name)));
      case REFUSED -> FConst.error(AdaptLanguage.text(CommandRuntimeMessages.GOLEM_RELEASE_FAILED, trusted("id", shortId), untrusted("player", name)));
    }));
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

  private static String nameOf(OfflinePlayer player) {
    String name = player.getName();
    return name == null ? player.getUniqueId().toString() : name;
  }

  private static Player onlineAdminTarget(Player player, CommandSender sender) {
    if (!admin(sender)) {
      return null;
    }
    return player != null ? player : senderTarget(sender);
  }

  private static OfflinePlayer anyAdminTarget(OfflinePlayer player, CommandSender sender) {
    if (!admin(sender)) {
      return null;
    }
    return player != null ? player : senderTarget(sender);
  }

  private static boolean admin(CommandSender sender) {
    if (sender.hasPermission(ADMIN_PERMISSION)) {
      return true;
    }
    FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", ADMIN_PERMISSION))).send(sender);
    return false;
  }

  private static Player senderTarget(CommandSender sender) {
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
