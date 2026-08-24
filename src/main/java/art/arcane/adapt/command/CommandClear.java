package art.arcane.adapt.command;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.director.specialhandlers.NullablePlayerHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.compat.BukkitDirectorContext;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

@Director(name = "clear", origin = DirectorOrigin.BOTH, description = "Clear player progression data", descriptionKey = "command.help.clear_player_progression_data")
public class CommandClear {

  @Director(description = "Clear all player data (XP, knowledge, adaptations, stats, discoveries, advancements, wisdom)", descriptionKey = "command.help.clear_all_player_data_xp_knowledge_adaptations_stats_discoveries_advancements_wisdom")
  public void all(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    Player targetPlayer = resolveTarget(player, sender);
    if (targetPlayer == null) {
      return;
    }

    CommandTargetExecutor.run(targetPlayer, () -> {
      PlayerData data = resolveReadyData(targetPlayer, sender);
      if (data == null) {
        return;
      }
      data.clearAll();
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
          CommandRuntimeMessages.CLEARED_ALL,
          untrusted("player", targetPlayer.getName())
      )));
    }, sender);
  }

  @Director(description = "Clear XP across all skill lines", descriptionKey = "command.help.clear_xp_across_all_skill_lines")
  public void xp(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    Player targetPlayer = resolveTarget(player, sender);
    if (targetPlayer == null) {
      return;
    }

    CommandTargetExecutor.run(targetPlayer, () -> {
      PlayerData data = resolveReadyData(targetPlayer, sender);
      if (data == null) {
        return;
      }
      data.clearXp();
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
          CommandRuntimeMessages.CLEARED_XP,
          untrusted("player", targetPlayer.getName())
      )));
    }, sender);
  }

  @Director(description = "Clear knowledge across all skill lines", descriptionKey = "command.help.clear_knowledge_across_all_skill_lines")
  public void knowledge(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    Player targetPlayer = resolveTarget(player, sender);
    if (targetPlayer == null) {
      return;
    }

    CommandTargetExecutor.run(targetPlayer, () -> {
      PlayerData data = resolveReadyData(targetPlayer, sender);
      if (data == null) {
        return;
      }
      data.clearKnowledge();
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
          CommandRuntimeMessages.CLEARED_KNOWLEDGE,
          untrusted("player", targetPlayer.getName())
      )));
    }, sender);
  }

  @Director(description = "Unlearn all adaptations across all skill lines", descriptionKey = "command.help.unlearn_all_adaptations_across_all_skill_lines")
  public void adaptations(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    Player targetPlayer = resolveTarget(player, sender);
    if (targetPlayer == null) {
      return;
    }

    CommandTargetExecutor.run(targetPlayer, () -> {
      PlayerData data = resolveReadyData(targetPlayer, sender);
      if (data == null) {
        return;
      }
      data.clearAdaptations();
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
          CommandRuntimeMessages.CLEARED_ADAPTATIONS,
          untrusted("player", targetPlayer.getName())
      )));
    }, sender);
  }

  @Director(description = "Clear the stats map", descriptionKey = "command.help.clear_the_stats_map")
  public void stats(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    Player targetPlayer = resolveTarget(player, sender);
    if (targetPlayer == null) {
      return;
    }

    CommandTargetExecutor.run(targetPlayer, () -> {
      PlayerData data = resolveReadyData(targetPlayer, sender);
      if (data == null) {
        return;
      }
      data.clearStats();
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
          CommandRuntimeMessages.CLEARED_STATS,
          untrusted("player", targetPlayer.getName())
      )));
    }, sender);
  }

  @Director(description = "Clear all discovery data (biomes, mobs, foods, items, recipes, etc.)", descriptionKey = "command.help.clear_all_discovery_data_biomes_mobs_foods_items_recipes_etc")
  public void discoveries(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    Player targetPlayer = resolveTarget(player, sender);
    if (targetPlayer == null) {
      return;
    }

    CommandTargetExecutor.run(targetPlayer, () -> {
      PlayerData data = resolveReadyData(targetPlayer, sender);
      if (data == null) {
        return;
      }
      data.clearDiscoveries();
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
          CommandRuntimeMessages.CLEARED_DISCOVERIES,
          untrusted("player", targetPlayer.getName())
      )));
    }, sender);
  }

  private Player resolveTarget(Player player, CommandSender sender) {
    if (!sender.hasPermission("adapt.clear")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.clear")))
          .send(sender);
      return null;
    }

    if (player != null) {
      return player;
    }

    if (!(sender instanceof Player senderPlayer)) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.PLAYER_REQUIRED_FROM_CONSOLE))
          .send(sender);
      return null;
    }

    return senderPlayer;
  }

  private PlayerData resolveReadyData(Player targetPlayer, CommandSender sender) {
    AdaptServer adaptServer = Adapt.instance == null ? null : Adapt.instance.getAdaptServer();
    AdaptPlayer adaptPlayer = adaptServer == null
        ? null
        : adaptServer.getOnlineAdaptPlayer(targetPlayer.getUniqueId());
    if (adaptPlayer != null && adaptPlayer.isRuntimeReady()
        && adaptPlayer.getPlayer() == targetPlayer) {
      return adaptPlayer.getData();
    }
    CommandTargetExecutor.send(
        sender,
        FConst.error(AdaptLanguage.text(CommandRuntimeMessages.ADAPT_SERVER_NOT_READY))
    );
    return null;
  }
}
