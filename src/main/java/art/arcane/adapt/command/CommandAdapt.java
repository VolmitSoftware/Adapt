package art.arcane.adapt.command;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationLearningTransaction;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.content.gui.ConfigGui;
import art.arcane.adapt.content.gui.SkillsGui;
import art.arcane.adapt.content.item.ExperienceOrb;
import art.arcane.adapt.content.item.KnowledgeOrb;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.localization.catalog.SnippetsMessages;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.director.context.AdaptationListingHandler;
import art.arcane.adapt.util.director.specialhandlers.NullablePlayerHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.compat.BukkitDirectorContext;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

@Director(name = "adapt", description = "Adapt skills, adaptations, and admin tools", descriptionKey = "command.help.adapt_skills_adaptations_and_admin_tools")
public class CommandAdapt {
  static final double MINIMUM_XP_BOOST = -0.99D;
  static final double MAXIMUM_XP_BOOST = 999D;
  static final long MAXIMUM_XP_BOOST_SECONDS = Long.MAX_VALUE / 1000L;

  private CommandDebug debug;
  private CommandClear clear;
  private CommandReset reset;
  private CommandDefault defaults;
  private CommandMutation mutations;

  @Director(name = "debugdump", sync = true, description = "Create and optionally upload a diagnostic report")
  public void debugdump(
    @Param(name = "upload", defaultValue = "true", description = "Upload the report to mclo.gs") boolean upload,
    @Param(name = "sender", contextual = true) CommandSender sender
  ) {
    Adapt.instance.debugDump().request(sender, upload);
  }

  @Director(description = "Choose your language or the server default")
  public void language() {
    AdaptLanguage.language(BukkitDirectorContext.sender(), new String[0]);
  }

  static boolean isValidXpBoost(long seconds, double multiplier) {
    return seconds > 0L
        && seconds <= MAXIMUM_XP_BOOST_SECONDS
        && Double.isFinite(multiplier)
        && multiplier >= MINIMUM_XP_BOOST
        && multiplier <= MAXIMUM_XP_BOOST;
  }

  static long xpBoostDurationMillis(long seconds) {
    return Math.multiplyExact(seconds, 1000L);
  }

  @Director(description = "Boost Target player Experience gain.", descriptionKey = "command.help.boost_target_player_experience_gain")
  public void boost(
      @Param(aliases = "seconds", description = "Positive duration in seconds", defaultValue = "10", descriptionKey = "command.help.amount_of_seconds")
      long seconds,
      @Param(aliases = "multiplier", description = "Additive XP multiplier from -0.99 to 999", defaultValue = "10", descriptionKey = "command.help.strength_of_the_boost")
      double multiplier,
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    if (!sender.hasPermission("adapt.boost")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.boost")))
          .send(sender);
      return;
    }

    if (!isValidXpBoost(seconds, multiplier)) {
      FConst.error(AdaptLanguage.text(
              CommandRuntimeMessages.INVALID_XP_BOOST,
              trusted("seconds", MAXIMUM_XP_BOOST_SECONDS),
              trusted("minimum", MINIMUM_XP_BOOST),
              trusted("maximum", MAXIMUM_XP_BOOST)
          ))
          .send(sender);
      return;
    }

    Player targetPlayer = resolveTargetPlayer(player, sender);
    if (targetPlayer == null) {
      return;
    }

    CommandTargetExecutor.run(
        targetPlayer,
        () -> boostTarget(targetPlayer, seconds, multiplier, sender),
        sender
    );
  }

  private void boostTarget(Player targetPlayer, long seconds, double multiplier, CommandSender sender) {
    AdaptPlayer adaptPlayer = resolveReadyTarget(targetPlayer, sender);
    if (adaptPlayer == null) {
      return;
    }
    PlayerData playerData = adaptPlayer.getData();
    playerData.globalXPMultiplier(multiplier, xpBoostDurationMillis(seconds));

    CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
        CommandRuntimeMessages.BOOSTED_XP,
        trusted("multiplier", multiplier),
        trusted("seconds", seconds)
    )));
  }

  @Director(description = "Boost Global Experience gain.", name = "global-boost", descriptionKey = "command.help.boost_global_experience_gain")
  public void globalBoost(
      @Param(aliases = "seconds", description = "Positive duration in seconds", defaultValue = "10", descriptionKey = "command.help.amount_of_seconds")
      long seconds,
      @Param(aliases = "multiplier", description = "Additive XP multiplier from -0.99 to 999", defaultValue = "10", descriptionKey = "command.help.strength_of_the_boost")
      double multiplier
  ) {
    if (!BukkitDirectorContext.hasPermission("adapt.boost.global")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.boost.global")))
          .send(BukkitDirectorContext.sender());
      return;
    }

    if (!isValidXpBoost(seconds, multiplier)) {
      FConst.error(AdaptLanguage.text(
              CommandRuntimeMessages.INVALID_XP_BOOST,
              trusted("seconds", MAXIMUM_XP_BOOST_SECONDS),
              trusted("minimum", MINIMUM_XP_BOOST),
              trusted("maximum", MAXIMUM_XP_BOOST)
          ))
          .send(BukkitDirectorContext.sender());
      return;
    }

    AdaptServer adaptServer = Adapt.instance.getAdaptServer();
    adaptServer.boostXP(multiplier, xpBoostDurationMillis(seconds));

    FConst.success(AdaptLanguage.text(
        CommandRuntimeMessages.BOOSTED_XP,
        trusted("multiplier", multiplier),
        trusted("seconds", seconds)
    )).send(BukkitDirectorContext.sender());
  }

  @Director(description = "Open the Adapt GUI", descriptionKey = "command.help.open_the_adapt_gui")
  public void gui(
      @Param(description = "GUI to open: main, skill:<name>, or adaptation:<name>", defaultValue = "main", descriptionKey = "command.help.gui_to_open_main_skill_name_or_adaptation_name")
      AdaptationListingHandler.AdaptationList target,
      @Param(aliases = "player", description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player,
      @Param(aliases = "force", description = "Bypass adapt.use permission checks when opening", defaultValue = "false", descriptionKey = "command.help.bypass_adapt_use_permission_checks_when_opening")
      boolean force
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    if (!sender.hasPermission("adapt.gui")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.gui")))
          .send(sender);
      return;
    }

    Player targetPlayer = resolveTargetPlayer(player, sender);
    if (targetPlayer == null) {
      return;
    }

    CommandTargetExecutor.run(
        targetPlayer,
        () -> openTargetGui(target, targetPlayer, force, sender),
        sender
    );
  }

  private void openTargetGui(
      AdaptationListingHandler.AdaptationList target,
      Player targetPlayer,
      boolean force,
      CommandSender sender
  ) {
    if (resolveReadyTarget(targetPlayer, sender) == null) {
      return;
    }

    if (target.equals("main")) {
      SkillsGui.open(targetPlayer);
      return;
    }

    if (target.startsWith("skill:")) {
      for (Skill<?> skill : SkillRegistry.skills.sortV()) {
        if (target.equals("skill:" + skill.getName())) {
          if (!skill.isEnabled()) {
            CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
                CommandRuntimeMessages.SKILL_DISABLED,
                untrusted("skill", skill.getName())
            )));
            return;
          }
          if (openSkillGui(skill, targetPlayer, force)) {
            CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
                CommandRuntimeMessages.GUI_OPENED,
                untrusted("target", skill.getName()),
                untrusted("player", targetPlayer.getName())
            )));
          } else {
            CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
                CommandRuntimeMessages.GUI_OPEN_DENIED,
                untrusted("target", skill.getName()),
                untrusted("player", targetPlayer.getName())
            )));
          }
          return;
        }
      }
    }

    if (target.startsWith("adaptation:")) {
      for (Skill<?> skill : SkillRegistry.skills.sortV()) {
        for (Adaptation<?> adaptation : skill.getAdaptations()) {
          if (target.equals("adaptation:" + adaptation.getName())) {
            if (!skill.isEnabled() || !adaptation.isEnabled()) {
              CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
                  CommandRuntimeMessages.ADAPTATION_DISABLED,
                  untrusted("adaptation", adaptation.getName())
              )));
              return;
            }
            if (openAdaptationGui(adaptation, targetPlayer, force)) {
              CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
                  CommandRuntimeMessages.GUI_OPENED,
                  untrusted("target", adaptation.getName()),
                  untrusted("player", targetPlayer.getName())
              )));
            } else {
              CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
                  CommandRuntimeMessages.GUI_OPEN_DENIED,
                  untrusted("target", adaptation.getName()),
                  untrusted("player", targetPlayer.getName())
              )));
            }
            return;
          }
        }
      }
    }

    CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
        CommandRuntimeMessages.UNKNOWN_GUI_TARGET,
        untrusted("target", target.name())
    )));
  }

  @Director(name = "effects", origin = DirectorOrigin.PLAYER, description = "Toggle Adapt effect visibility for yourself", descriptionKey = "command.help.toggle_adapt_effect_visibility_for_yourself")
  public void effects(
      @Param(aliases = "enabled", description = "Explicit on/off state, omit to toggle", defaultValue = "toggle", descriptionKey = "command.help.explicit_on_off_state_omit_to_toggle")
      String enabled
  ) {
    if (!BukkitDirectorContext.hasPermission("adapt.effects")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.effects")))
          .send(BukkitDirectorContext.sender());
      return;
    }

    CommandSender sender = BukkitDirectorContext.sender();
    Player player = BukkitDirectorContext.player();
    AdaptPlayer adaptPlayer = resolveReadyTarget(player, sender);
    if (adaptPlayer == null) {
      return;
    }
    PlayerData playerData = adaptPlayer.getData();
    String normalized = enabled == null ? "toggle" : enabled.trim().toLowerCase(Locale.ROOT);
    Boolean target = switch (normalized) {
      case "toggle" -> !playerData.isEffectsEnabled();
      case "true", "on", "yes", "enabled" -> Boolean.TRUE;
      case "false", "off", "no", "disabled" -> Boolean.FALSE;
      default -> null;
    };

    if (target == null) {
      FConst.error(AdaptLanguage.text(SnippetsMessages.EFFECTS_INVALID_STATE)).send(BukkitDirectorContext.sender());
      return;
    }

    playerData.setEffectsEnabled(target);
    if (target) {
      FConst.success(AdaptLanguage.text(SnippetsMessages.EFFECTS_ENABLED)).send(BukkitDirectorContext.sender());
    } else {
      FConst.success(AdaptLanguage.text(SnippetsMessages.EFFECTS_DISABLED)).send(BukkitDirectorContext.sender());
    }
  }

  @Director(name = "configure", aliases = {"config", "cfg"}, origin = DirectorOrigin.PLAYER, description = "Open the in-game Adapt config editor", descriptionKey = "command.help.open_the_in_game_adapt_config_editor")
  public void configure() {
    if (!ConfigGui.canConfigure(BukkitDirectorContext.player())) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.CONFIGURATOR_REQUIRED))
          .send(BukkitDirectorContext.sender());
      return;
    }

    ConfigGui.open(BukkitDirectorContext.player());
  }

  @Director(description = "Give an experience orb to yourself or a target player", descriptionKey = "command.help.give_an_experience_orb_to_yourself_or_a_target_player")
  public void experience(
      @Param(aliases = "skill", description = "Skill name, or all / random", descriptionKey = "command.help.skill_name_or_all_random")
      AdaptationListingHandler.AdaptationSkillList skillName,
      @Param(aliases = "amount", description = "Experience per orb", defaultValue = "10", descriptionKey = "command.help.experience_per_orb")
      int amount,
      @Param(aliases = "player", description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player

  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    if (!sender.hasPermission("adapt.cheatitem")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.cheatitem")))
          .send(sender);
      return;
    }

    Player targetPlayer = resolveTargetPlayer(player, sender, CommandRuntimeMessages.PLAYER_ONLY_OR_REFERENCE);
    if (targetPlayer == null) {
      return;
    }

    CommandTargetExecutor.run(
        targetPlayer,
        () -> giveExperienceOrb(skillName, amount, targetPlayer, sender),
        sender
    );
  }

  private void giveExperienceOrb(
      AdaptationListingHandler.AdaptationSkillList skillName,
      int amount,
      Player targetPlayer,
      CommandSender sender
  ) {
    if (skillName.equals("all")) {
      Map<String, Double> experienceMap = new HashMap<>();
      for (Skill<?> skill : allSkillSnapshot()) {
        experienceMap.put(skill.getName(), (double) amount);
      }
      targetPlayer.getInventory().addItem(ExperienceOrb.with(experienceMap));
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(CommandRuntimeMessages.GIVING_ALL_ORBS)));
      return;
    }

    if (skillName.equals("random")) {
      List<Skill<?>> skills = allSkillSnapshot();
      if (skills.isEmpty()) {
        CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(CommandRuntimeMessages.NO_SKILLS_REGISTERED)));
        return;
      }

      targetPlayer.getInventory().addItem(ExperienceOrb.with(skills.get(ThreadLocalRandom.current().nextInt(skills.size())).getName(), amount));
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(CommandRuntimeMessages.GIVING_RANDOM_ORB)));
      return;
    }

    Skill<?> skill = Adapt.instance.getAdaptServer().getSkillRegistry().getAnySkill(skillName.name());
    if (skill != null) {
      targetPlayer.getInventory().addItem(ExperienceOrb.with(skill.getName(), amount));
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
          CommandRuntimeMessages.GIVING_SKILL_ORB,
          untrusted("skill", skill.getName())
      )));
    } else {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.UNKNOWN_SKILL_ORB,
          untrusted("skill", skillName.name())
      )));
    }
  }

  @Director(description = "Give a knowledge orb to yourself or a target player", descriptionKey = "command.help.give_a_knowledge_orb_to_yourself_or_a_target_player")
  public void knowledge(
      @Param(aliases = "skill", description = "Skill name, or all / random", descriptionKey = "command.help.skill_name_or_all_random")
      AdaptationListingHandler.AdaptationSkillList skillName,
      @Param(aliases = "amount", description = "Knowledge per orb", defaultValue = "10", descriptionKey = "command.help.knowledge_per_orb")
      int amount,
      @Param(aliases = "player", description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    if (!sender.hasPermission("adapt.cheatitem")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.cheatitem")))
          .send(sender);
      return;
    }

    Player targetPlayer = resolveTargetPlayer(player, sender, CommandRuntimeMessages.PLAYER_ONLY);
    if (targetPlayer == null) {
      return;
    }

    CommandTargetExecutor.run(
        targetPlayer,
        () -> giveKnowledgeOrb(skillName, amount, targetPlayer, sender),
        sender
    );
  }

  private void giveKnowledgeOrb(
      AdaptationListingHandler.AdaptationSkillList skillName,
      int amount,
      Player targetPlayer,
      CommandSender sender
  ) {
    if (skillName.equals("all")) {
      Map<String, Integer> knowledgeMap = new HashMap<>();
      for (Skill<?> skill : allSkillSnapshot()) {
        knowledgeMap.put(skill.getName(), amount);
      }
      targetPlayer.getInventory().addItem(KnowledgeOrb.with(knowledgeMap));
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(CommandRuntimeMessages.GIVING_ALL_ORBS)));
      return;
    }

    if (skillName.equals("random")) {
      List<Skill<?>> skills = allSkillSnapshot();
      if (skills.isEmpty()) {
        CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(CommandRuntimeMessages.NO_SKILLS_REGISTERED)));
        return;
      }

      targetPlayer.getInventory().addItem(KnowledgeOrb.with(skills.get(ThreadLocalRandom.current().nextInt(skills.size())).getName(), amount));
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(CommandRuntimeMessages.GIVING_RANDOM_ORB)));
      return;
    }

    Skill<?> skill = Adapt.instance.getAdaptServer().getSkillRegistry().getAnySkill(skillName.name());
    if (skill != null) {
      targetPlayer.getInventory().addItem(KnowledgeOrb.with(skill.getName(), amount));
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
          CommandRuntimeMessages.GIVING_SKILL_ORB,
          untrusted("skill", skill.getName())
      )));
    } else {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.UNKNOWN_SKILL_ORB,
          untrusted("skill", skillName.name())
      )));
    }
  }

  @Director(description = "Assign or unassign an adaptation for a player, as if learning / unlearning it.", descriptionKey = "command.help.assign_or_unassign_an_adaptation_for_a_player_as_if_learning_unlearning_it")
  public void determine(
      @Param(aliases = "adaptationTarget", description = "Adaptation to modify, as skill:adaptation", descriptionKey = "command.help.adaptation_to_modify_as_skill_adaptation")
      AdaptationListingHandler.AdaptationProvider adaptationTarget,
      @Param(aliases = "assign", description = "true to learn, false to unlearn", descriptionKey = "command.help.true_to_learn_false_to_unlearn")
      boolean assign,
      @Param(aliases = "force", description = "Bypass costs and restrictions", descriptionKey = "command.help.bypass_costs_and_restrictions")
      boolean force,
      @Param(aliases = "level", description = "Adaptation level to apply", descriptionKey = "command.help.adaptation_level_to_apply")
      int level,
      @Param(aliases = "player", description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player

  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    if (!sender.hasPermission("adapt.determine")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.determine")))
          .send(sender);
      return;
    }

    Player targetPlayer = resolveTargetPlayer(player, sender);
    if (targetPlayer == null) {
      return;
    }

    String[] split = adaptationTarget.name().split(":", 2);
    if (split.length != 2) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.INVALID_ADAPTATION_TARGET))
          .send(sender);
      return;
    }

    CommandTargetExecutor.run(
        targetPlayer,
        () -> determineTarget(split[0], split[1], assign, force, level, targetPlayer, sender),
        sender
    );
  }

  private void determineTarget(
      String skillname,
      String adaptationname,
      boolean assign,
      boolean force,
      int level,
      Player targetPlayer,
      CommandSender sender
  ) {
    AdaptPlayer adaptPlayer = resolveReadyTarget(targetPlayer, sender);
    if (adaptPlayer == null) {
      return;
    }

    for (Skill<?> skill : SkillRegistry.skills.sortV()) {
      if (skill.getName().equalsIgnoreCase(skillname)) {
        for (Adaptation<?> adaptation : skill.getAdaptations()) {
          if (adaptation.getName().equalsIgnoreCase(adaptationname)) {
            PlayerSkillLine skillLine = adaptPlayer.getData().getSkillLine(skill.getName());
            int previousLevel = skillLine == null ? 0 : skillLine.getAdaptationLevel(adaptation.getName());

            if (assign) {
              adaptation.learn(targetPlayer, level, force);
            } else {
              adaptation.unlearn(targetPlayer, level, force);
            }

            int resultingLevel = skillLine == null ? 0 : skillLine.getAdaptationLevel(adaptation.getName());
            if (resultingLevel == previousLevel) {
              CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
                  CommandRuntimeMessages.NO_ADAPTATION_CHANGE,
                  untrusted("adaptation", adaptation.getName()),
                  untrusted("player", targetPlayer.getName()),
                  trusted("level", resultingLevel)
              )));
            } else {
              CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
                  assign ? CommandRuntimeMessages.LEARNED_ADAPTATION : CommandRuntimeMessages.UNLEARNED_ADAPTATION,
                  untrusted("adaptation", adaptation.getName()),
                  untrusted("player", targetPlayer.getName()),
                  trusted("level", resultingLevel)
              )));
            }
            return;
          }
        }
        CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
            CommandRuntimeMessages.UNKNOWN_ADAPTATION_IN_SKILL,
            untrusted("adaptation", adaptationname),
            untrusted("skill", skill.getName())
        )));
        return;
      }
    }

    CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
        CommandRuntimeMessages.UNKNOWN_SKILL_QUOTED,
        untrusted("skill", skillname)
    )));
  }

  @Director(name = "claim-skill", description = "Set a player's skill line level between 0 and 100 for custom UI integration.", descriptionKey = "command.help.set_a_player_s_skill_line_level_between_0_and_100_for_custom_ui_integration")
  public void claimSkill(
      @Param(aliases = "skill", description = "Skill line to set", descriptionKey = "command.help.skill_line_to_set")
      AdaptationListingHandler.SkillProvider skillTarget,
      @Param(aliases = "level", description = "Level between 0 and 100", descriptionKey = "command.help.level_between_0_and_100")
      int level,
      @Param(aliases = "player", description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    if (!sender.hasPermission("adapt.determine")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.determine")))
          .send(sender);
      return;
    }

    Player targetPlayer = resolveTargetPlayer(player, sender);
    if (targetPlayer == null) {
      return;
    }

    if (level < 0 || level > 100) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.SKILL_LEVEL_RANGE)).send(sender);
      return;
    }

    Skill<?> skill = Adapt.instance.getAdaptServer().getSkillRegistry().getAnySkill(skillTarget.name());
    if (skill == null) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.UNKNOWN_SKILL, untrusted("skill", skillTarget.name())))
          .send(sender);
      return;
    }

    CommandTargetExecutor.run(
        targetPlayer,
        () -> claimSkillTarget(skill, level, targetPlayer, sender),
        sender
    );
  }

  private void claimSkillTarget(Skill<?> skill, int level, Player targetPlayer, CommandSender sender) {
    AdaptPlayer adaptPlayer = resolveReadyTarget(targetPlayer, sender);
    if (adaptPlayer == null) {
      return;
    }
    PlayerData playerData = adaptPlayer.getData();
    PlayerSkillLine skillLine = playerData.getSkillLine(skill.getName());
    if (skillLine == null) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.SKILL_LINE_UNAVAILABLE,
          untrusted("skill", skill.getName())
      )));
      return;
    }

    double targetXp = XP.getXpForLevel(level);
    skillLine.setXp(targetXp);
    if (skillLine.getLastXP() > targetXp) {
      skillLine.setLastXP(targetXp);
    }
    if (skillLine.getLastLevel() > level) {
      skillLine.setLastLevel(level);
    }

    CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
        CommandRuntimeMessages.SET_SKILL_LEVEL,
        untrusted("player", targetPlayer.getName()),
        untrusted("skill", skill.getName()),
        trusted("level", level)
    )));
  }

  @Director(name = "claim-adaptation", description = "Set an adaptation level between 0 and 100 if the player can afford it.", descriptionKey = "command.help.set_an_adaptation_level_between_0_and_100_if_the_player_can_afford_it")
  public void claimAdaptation(
      @Param(aliases = "adaptationTarget", description = "Adaptation to set, as skill:adaptation", descriptionKey = "command.help.adaptation_to_set_as_skill_adaptation")
      AdaptationListingHandler.AdaptationProvider adaptationTarget,
      @Param(aliases = "level", description = "Level between 0 and 100, clamped to the adaptation max", descriptionKey = "command.help.level_between_0_and_100_clamped_to_the_adaptation_max")
      int level,
      @Param(aliases = "force", description = "Bypass power, knowledge, and permanence checks", defaultValue = "false", descriptionKey = "command.help.bypass_power_knowledge_and_permanence_checks")
      boolean force,
      @Param(aliases = "player", description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    if (!sender.hasPermission("adapt.determine")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.determine")))
          .send(sender);
      return;
    }

    Player targetPlayer = resolveTargetPlayer(player, sender);
    if (targetPlayer == null) {
      return;
    }

    if (level < 0 || level > 100) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.ADAPTATION_LEVEL_RANGE))
          .send(sender);
      return;
    }

    String[] split = adaptationTarget.name().split(":", 2);
    if (split.length != 2) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.INVALID_ADAPTATION_TARGET))
          .send(sender);
      return;
    }

    Skill<?> skill = Adapt.instance.getAdaptServer().getSkillRegistry().getAnySkill(split[0]);
    if (skill == null) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.UNKNOWN_SKILL, untrusted("skill", split[0])))
          .send(sender);
      return;
    }

    Adaptation<?> adaptation = null;
    for (Adaptation<?> candidate : skill.getAdaptations()) {
      if (candidate.getName().equalsIgnoreCase(split[1])) {
        adaptation = candidate;
        break;
      }
    }

    if (adaptation == null) {
      FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.UNKNOWN_ADAPTATION_IN_SKILL_PLAIN,
          untrusted("adaptation", split[1]),
          untrusted("skill", skill.getName())
      )).send(sender);
      return;
    }

    Adaptation<?> targetAdaptation = adaptation;
    CommandTargetExecutor.run(
        targetPlayer,
        () -> claimAdaptationTarget(skill, targetAdaptation, level, force, targetPlayer, sender),
        sender
    );
  }

  private void claimAdaptationTarget(
      Skill<?> skill,
      Adaptation<?> adaptation,
      int level,
      boolean force,
      Player targetPlayer,
      CommandSender sender
  ) {
    AdaptPlayer adaptPlayer = resolveReadyTarget(targetPlayer, sender);
    if (adaptPlayer == null) {
      return;
    }
    PlayerData playerData = adaptPlayer.getData();
    PlayerSkillLine skillLine = playerData.getSkillLine(skill.getName());
    if (skillLine == null) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.SKILL_LINE_UNAVAILABLE,
          untrusted("skill", skill.getName())
      )));
      return;
    }

    int currentLevel = skillLine.getAdaptationLevel(adaptation.getName());
    int targetLevel = Math.max(0, Math.min(level, adaptation.getMaxLevel()));
    if (targetLevel == currentLevel) {
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
          CommandRuntimeMessages.ADAPTATION_ALREADY_LEVEL,
          untrusted("adaptation", adaptation.getName()),
          trusted("level", currentLevel)
      )));
      return;
    }

    if (targetLevel > currentLevel) {
      int knowledgeCost = adaptation.getCostFor(targetLevel, currentLevel);
      int powerCost = adaptation.getPowerCostFor(targetLevel, currentLevel);
      AdaptationLearningTransaction.Result result =
          AdaptationLearningTransaction.learn(adaptation, targetPlayer, targetLevel, force);
      if (result != AdaptationLearningTransaction.Result.LEARNED) {
        sendLearningFailure(
            result,
            skill,
            adaptation,
            targetPlayer,
            playerData,
            skillLine,
            knowledgeCost,
            powerCost,
            sender
        );
        return;
      }
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
          CommandRuntimeMessages.SET_ADAPTATION_LEVEL,
          untrusted("player", targetPlayer.getName()),
          untrusted("adaptation", adaptation.getName()),
          trusted("level", targetLevel)
      )));
      return;
    }

    AdaptationLearningTransaction.Result result =
        AdaptationLearningTransaction.unlearn(adaptation, targetPlayer, targetLevel, force);
    if (result != AdaptationLearningTransaction.Result.UNLEARNED) {
      sendLearningFailure(result, skill, adaptation, targetPlayer, playerData, skillLine, 0, 0, sender);
      return;
    }

    CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
        CommandRuntimeMessages.SET_ADAPTATION_LEVEL,
        untrusted("player", targetPlayer.getName()),
        untrusted("adaptation", adaptation.getName()),
        trusted("level", targetLevel)
    )));
  }

  private void sendLearningFailure(
      AdaptationLearningTransaction.Result result,
      Skill<?> skill,
      Adaptation<?> adaptation,
      Player targetPlayer,
      PlayerData playerData,
      PlayerSkillLine skillLine,
      int knowledgeCost,
      int powerCost,
      CommandSender sender
  ) {
    if (result == AdaptationLearningTransaction.Result.INSUFFICIENT_POWER) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.POWER_INSUFFICIENT,
          trusted("needed", powerCost),
          trusted("available", playerData.getAvailablePower())
      )));
      return;
    }
    if (result == AdaptationLearningTransaction.Result.INSUFFICIENT_KNOWLEDGE) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.KNOWLEDGE_INSUFFICIENT,
          untrusted("skill", skill.getName()),
          trusted("needed", knowledgeCost),
          trusted("available", skillLine.getKnowledge())
      )));
      return;
    }
    if (result == AdaptationLearningTransaction.Result.INSUFFICIENT_FUNDS) {
      CommandTargetExecutor.send(
          sender,
          FConst.error(AdaptLanguage.text(CommandRuntimeMessages.VAULT_INSUFFICIENT_FUNDS))
      );
      return;
    }
    if (result == AdaptationLearningTransaction.Result.ECONOMY_UNAVAILABLE
        || result == AdaptationLearningTransaction.Result.ECONOMY_FAILED) {
      CommandTargetExecutor.send(
          sender,
          FConst.error(AdaptLanguage.text(CommandRuntimeMessages.VAULT_TRANSACTION_FAILED))
      );
      return;
    }
    if (result == AdaptationLearningTransaction.Result.PERMANENT) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.PERMANENT_ADAPTATION_LOWER,
          untrusted("adaptation", adaptation.getName())
      )));
      return;
    }
    CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
        CommandRuntimeMessages.NO_ADAPTATION_CHANGE,
        untrusted("adaptation", adaptation.getName()),
        untrusted("player", targetPlayer.getName()),
        trusted("level", skillLine.getAdaptationLevel(adaptation.getName()))
    )));
  }

  static boolean openSkillGui(Skill<?> skill, Player player, boolean force) {
    return skill.openGui(player, !force);
  }

  static boolean openAdaptationGui(Adaptation<?> adaptation, Player player, boolean force) {
    return adaptation.openGui(player, !force);
  }

  private List<Skill<?>> allSkillSnapshot() {
    if (Adapt.instance != null
        && Adapt.instance.getAdaptServer() != null
        && Adapt.instance.getAdaptServer().getSkillRegistry() != null) {
      return Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills();
    }

    return SkillRegistry.skills.sortV();
  }

  private Player resolveTargetPlayer(Player player, CommandSender sender) {
    return resolveTargetPlayer(player, sender, CommandRuntimeMessages.PLAYER_REQUIRED_FROM_CONSOLE);
  }

  private Player resolveTargetPlayer(Player player, CommandSender sender, TextKey consoleMessage) {
    Player targetPlayer = player;
    if (targetPlayer == null && !(sender instanceof Player)) {
      FConst.error(AdaptLanguage.text(consoleMessage)).send(sender);
      return null;
    }
    if (targetPlayer == null) {
      targetPlayer = (Player) sender;
    }
    return targetPlayer;
  }

  private AdaptPlayer resolveReadyTarget(Player targetPlayer, CommandSender sender) {
    AdaptServer adaptServer = Adapt.instance == null ? null : Adapt.instance.getAdaptServer();
    AdaptPlayer adaptPlayer = adaptServer == null
        ? null
        : adaptServer.getOnlineAdaptPlayer(targetPlayer.getUniqueId());
    if (adaptPlayer != null && adaptPlayer.isRuntimeReady()
        && adaptPlayer.getPlayer() == targetPlayer) {
      return adaptPlayer;
    }
    CommandTargetExecutor.send(
        sender,
        FConst.error(AdaptLanguage.text(CommandRuntimeMessages.ADAPT_SERVER_NOT_READY))
    );
    return null;
  }
}
