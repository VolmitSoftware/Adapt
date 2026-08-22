package art.arcane.adapt.command;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.director.specialhandlers.NullablePlayerHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.compat.BukkitDirectorContext;
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
    Player targetPlayer = resolveTarget(player);
    if (targetPlayer == null) return;

    PlayerData data = Adapt.instance.getAdaptServer().getPlayer(targetPlayer).getData();
    data.clearAll();
    FConst.success(AdaptLanguage.text(CommandRuntimeMessages.CLEARED_ALL, untrusted("player", targetPlayer.getName())))
        .send(BukkitDirectorContext.sender());
  }

  @Director(description = "Clear XP across all skill lines", descriptionKey = "command.help.clear_xp_across_all_skill_lines")
  public void xp(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player
  ) {
    Player targetPlayer = resolveTarget(player);
    if (targetPlayer == null) return;

    PlayerData data = Adapt.instance.getAdaptServer().getPlayer(targetPlayer).getData();
    data.clearXp();
    FConst.success(AdaptLanguage.text(CommandRuntimeMessages.CLEARED_XP, untrusted("player", targetPlayer.getName())))
        .send(BukkitDirectorContext.sender());
  }

  @Director(description = "Clear knowledge across all skill lines", descriptionKey = "command.help.clear_knowledge_across_all_skill_lines")
  public void knowledge(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player
  ) {
    Player targetPlayer = resolveTarget(player);
    if (targetPlayer == null) return;

    PlayerData data = Adapt.instance.getAdaptServer().getPlayer(targetPlayer).getData();
    data.clearKnowledge();
    FConst.success(AdaptLanguage.text(CommandRuntimeMessages.CLEARED_KNOWLEDGE, untrusted("player", targetPlayer.getName())))
        .send(BukkitDirectorContext.sender());
  }

  @Director(description = "Unlearn all adaptations across all skill lines", descriptionKey = "command.help.unlearn_all_adaptations_across_all_skill_lines")
  public void adaptations(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player
  ) {
    Player targetPlayer = resolveTarget(player);
    if (targetPlayer == null) return;

    PlayerData data = Adapt.instance.getAdaptServer().getPlayer(targetPlayer).getData();
    data.clearAdaptations();
    FConst.success(AdaptLanguage.text(CommandRuntimeMessages.CLEARED_ADAPTATIONS, untrusted("player", targetPlayer.getName())))
        .send(BukkitDirectorContext.sender());
  }

  @Director(description = "Clear the stats map", descriptionKey = "command.help.clear_the_stats_map")
  public void stats(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player
  ) {
    Player targetPlayer = resolveTarget(player);
    if (targetPlayer == null) return;

    PlayerData data = Adapt.instance.getAdaptServer().getPlayer(targetPlayer).getData();
    data.clearStats();
    FConst.success(AdaptLanguage.text(CommandRuntimeMessages.CLEARED_STATS, untrusted("player", targetPlayer.getName())))
        .send(BukkitDirectorContext.sender());
  }

  @Director(description = "Clear all discovery data (biomes, mobs, foods, items, recipes, etc.)", descriptionKey = "command.help.clear_all_discovery_data_biomes_mobs_foods_items_recipes_etc")
  public void discoveries(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player
  ) {
    Player targetPlayer = resolveTarget(player);
    if (targetPlayer == null) return;

    PlayerData data = Adapt.instance.getAdaptServer().getPlayer(targetPlayer).getData();
    data.clearDiscoveries();
    FConst.success(AdaptLanguage.text(CommandRuntimeMessages.CLEARED_DISCOVERIES, untrusted("player", targetPlayer.getName())))
        .send(BukkitDirectorContext.sender());
  }

  private Player resolveTarget(Player player) {
    if (!BukkitDirectorContext.hasPermission("adapt.clear")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.clear")))
          .send(BukkitDirectorContext.sender());
      return null;
    }

    if (player != null) {
      return player;
    }

    if (BukkitDirectorContext.isConsole()) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.PLAYER_REQUIRED_FROM_CONSOLE))
          .send(BukkitDirectorContext.sender());
      return null;
    }

    return BukkitDirectorContext.player();
  }
}
