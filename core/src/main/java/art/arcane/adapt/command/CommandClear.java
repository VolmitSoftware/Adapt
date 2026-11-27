package art.arcane.adapt.command;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.engine.player.PlayerApi;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.director.specialhandlers.NullablePlayerHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

@Director(name = "clear", origin = DirectorOrigin.BOTH, description = "Clear player progression data", descriptionKey = "adapt.command.help.clear_player_progression_data")
public class CommandClear {

  @Director(description = "Clear all player data (XP, knowledge, adaptations, stats, discoveries, advancements, wisdom)", descriptionKey = "adapt.command.help.clear_all_player_data_xp_knowledge_adaptations_stats_discoveries_advancements_wisdom")
  public void all(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "adapt.command.help.target_player_defaults_to_you")
      PlayerRef player
  ) {
    CommandSource sender = CommandContext.source();
    PlayerRef targetPlayer = resolveTarget(player, sender);
    if (targetPlayer == null) {
      return;
    }

    CommandTargetExecutor.run(targetPlayer, () -> {
      PlayerProfile profile = resolveReadyProfile(targetPlayer, sender);
      if (profile == null) {
        return;
      }
      profile.reset();
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
          CommandRuntimeMessages.CLEARED_ALL,
          untrusted("player", targetPlayer.name())
      )));
    }, sender);
  }

  @Director(description = "Clear XP across all skill lines", descriptionKey = "adapt.command.help.clear_xp_across_all_skill_lines")
  public void xp(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "adapt.command.help.target_player_defaults_to_you")
      PlayerRef player
  ) {
    CommandSource sender = CommandContext.source();
    PlayerRef targetPlayer = resolveTarget(player, sender);
    if (targetPlayer == null) {
      return;
    }

    CommandTargetExecutor.run(targetPlayer, () -> {
      PlayerProfile profile = resolveReadyProfile(targetPlayer, sender);
      if (profile == null) {
        return;
      }
      profile.clearXp();
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
          CommandRuntimeMessages.CLEARED_XP,
          untrusted("player", targetPlayer.name())
      )));
    }, sender);
  }

  @Director(description = "Clear knowledge across all skill lines", descriptionKey = "adapt.command.help.clear_knowledge_across_all_skill_lines")
  public void knowledge(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "adapt.command.help.target_player_defaults_to_you")
      PlayerRef player
  ) {
    CommandSource sender = CommandContext.source();
    PlayerRef targetPlayer = resolveTarget(player, sender);
    if (targetPlayer == null) {
      return;
    }

    CommandTargetExecutor.run(targetPlayer, () -> {
      PlayerProfile profile = resolveReadyProfile(targetPlayer, sender);
      if (profile == null) {
        return;
      }
      profile.clearKnowledge();
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
          CommandRuntimeMessages.CLEARED_KNOWLEDGE,
          untrusted("player", targetPlayer.name())
      )));
    }, sender);
  }

  @Director(description = "Unlearn all adaptations across all skill lines", descriptionKey = "adapt.command.help.unlearn_all_adaptations_across_all_skill_lines")
  public void adaptations(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "adapt.command.help.target_player_defaults_to_you")
      PlayerRef player
  ) {
    CommandSource sender = CommandContext.source();
    PlayerRef targetPlayer = resolveTarget(player, sender);
    if (targetPlayer == null) {
      return;
    }

    CommandTargetExecutor.run(targetPlayer, () -> {
      PlayerProfile profile = resolveReadyProfile(targetPlayer, sender);
      if (profile == null) {
        return;
      }
      profile.clearAdaptations();
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
          CommandRuntimeMessages.CLEARED_ADAPTATIONS,
          untrusted("player", targetPlayer.name())
      )));
    }, sender);
  }

  @Director(description = "Clear the stats map", descriptionKey = "adapt.command.help.clear_the_stats_map")
  public void stats(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "adapt.command.help.target_player_defaults_to_you")
      PlayerRef player
  ) {
    CommandSource sender = CommandContext.source();
    PlayerRef targetPlayer = resolveTarget(player, sender);
    if (targetPlayer == null) {
      return;
    }

    CommandTargetExecutor.run(targetPlayer, () -> {
      PlayerProfile profile = resolveReadyProfile(targetPlayer, sender);
      if (profile == null) {
        return;
      }
      profile.clearStats();
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
          CommandRuntimeMessages.CLEARED_STATS,
          untrusted("player", targetPlayer.name())
      )));
    }, sender);
  }

  @Director(description = "Clear all discovery data (biomes, mobs, foods, items, recipes, etc.)", descriptionKey = "adapt.command.help.clear_all_discovery_data_biomes_mobs_foods_items_recipes_etc")
  public void discoveries(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "adapt.command.help.target_player_defaults_to_you")
      PlayerRef player
  ) {
    CommandSource sender = CommandContext.source();
    PlayerRef targetPlayer = resolveTarget(player, sender);
    if (targetPlayer == null) {
      return;
    }

    CommandTargetExecutor.run(targetPlayer, () -> {
      PlayerProfile profile = resolveReadyProfile(targetPlayer, sender);
      if (profile == null) {
        return;
      }
      profile.clearDiscoveries();
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
          CommandRuntimeMessages.CLEARED_DISCOVERIES,
          untrusted("player", targetPlayer.name())
      )));
    }, sender);
  }

  private PlayerRef resolveTarget(PlayerRef player, CommandSource sender) {
    if (!sender.hasPermission("adapt.clear")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.clear")))
          .send(sender);
      return null;
    }

    if (player != null) {
      return player;
    }

    PlayerRef senderPlayer = sender.player().orElse(null);
    if (senderPlayer == null) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.PLAYER_REQUIRED_FROM_CONSOLE))
          .send(sender);
      return null;
    }

    return senderPlayer;
  }

  private PlayerProfile resolveReadyProfile(PlayerRef targetPlayer, CommandSource sender) {
    PlayerProfile profile = AdaptCore.current() == null ? null : PlayerApi.directory().find(targetPlayer).orElse(null);
    if (profile != null && profile.isReady()) {
      return profile;
    }
    CommandTargetExecutor.send(
        sender,
        FConst.error(AdaptLanguage.text(CommandRuntimeMessages.ADAPT_SERVER_NOT_READY))
    );
    return null;
  }
}
