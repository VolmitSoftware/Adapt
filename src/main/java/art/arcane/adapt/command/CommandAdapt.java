package art.arcane.adapt.command;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.content.gui.ConfigGui;
import art.arcane.adapt.content.gui.SkillsGui;
import art.arcane.adapt.content.item.ExperienceOrb;
import art.arcane.adapt.content.item.KnowledgeOrb;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigMigrationManager;
import art.arcane.adapt.util.director.context.AdaptationListingHandler;
import art.arcane.adapt.util.director.specialhandlers.NullablePlayerHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.compat.BukkitDirectorContext;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Director(name = "adapt", description = "Adapt skills, adaptations, and admin tools")
public class CommandAdapt {
  private CommandDebug debug;
  private CommandClear clear;
  private CommandReset reset;
  private CommandDefault defaults;
  private CommandMutation mutations;

  @Director(description = "Boost Target player Experience gain.")
  public void boost(
      @Param(aliases = "seconds", description = "Amount of seconds", defaultValue = "10")
      int seconds,
      @Param(aliases = "multiplier", description = "Strength of the boost", defaultValue = "10")
      double multiplier,
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class)
      Player player
  ) {
    if (!BukkitDirectorContext.hasPermission("adapt.boost")) {
      FConst.error("You lack the Permission 'adapt.boost'").send(BukkitDirectorContext.sender());
      return;
    }

    Player targetPlayer = player;
    if (targetPlayer == null && BukkitDirectorContext.isConsole()) {
      FConst.error("You must specify a player when using this command from console.").send(BukkitDirectorContext.sender());
      return;
    } else if (targetPlayer == null) {
      targetPlayer = BukkitDirectorContext.player();
    }

    AdaptServer adaptServer = Adapt.instance.getAdaptServer();
    PlayerData playerData = adaptServer.getPlayer(targetPlayer).getData();
    playerData.globalXPMultiplier(multiplier, seconds * 1000);

    FConst.success("Boosted XP by " + multiplier + " for " + seconds + " seconds").send(BukkitDirectorContext.sender());
  }

  @Director(description = "Boost Global Experience gain.", name = "global-boost")
  public void globalBoost(
      @Param(aliases = "seconds", description = "Amount of seconds", defaultValue = "10")
      int seconds,
      @Param(aliases = "multiplier", description = "Strength of the boost", defaultValue = "10")
      double multiplier
  ) {
    if (!BukkitDirectorContext.hasPermission("adapt.boost.global")) {
      FConst.error("You lack the Permission 'adapt.boost.global'").send(BukkitDirectorContext.sender());
      return;
    }

    AdaptServer adaptServer = Adapt.instance.getAdaptServer();
    adaptServer.boostXP(multiplier, seconds * 1000);

    FConst.success("Boosted XP by " + multiplier + " for " + seconds + " seconds").send(BukkitDirectorContext.sender());
  }

  @Director(description = "Open the Adapt GUI")
  public void gui(
      @Param(description = "GUI to open: main, skill:<name>, or adaptation:<name>", defaultValue = "main")
      AdaptationListingHandler.AdaptationList target,
      @Param(aliases = "player", description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class)
      Player player,
      @Param(aliases = "force", description = "Bypass adapt.use permission checks when opening", defaultValue = "false")
      boolean force
  ) {
    if (!BukkitDirectorContext.hasPermission("adapt.gui")) {
      FConst.error("You lack the Permission 'adapt.gui'").send(BukkitDirectorContext.sender());
      return;
    }

    Player targetPlayer = player;
    if (targetPlayer == null && BukkitDirectorContext.isConsole()) {
      FConst.error("You must specify a player when using this command from console.").send(BukkitDirectorContext.sender());
      return;
    } else if (targetPlayer == null) {
      targetPlayer = BukkitDirectorContext.player();
    }

    if (target.equals("main")) {
      SkillsGui.open(targetPlayer);
      return;
    }

    if (target.startsWith("skill:")) {
      for (Skill<?> skill : SkillRegistry.skills.sortV()) {
        if (target.equals("skill:" + skill.getName())) {
          if (!skill.isEnabled()) {
            FConst.error("Skill " + skill.getName() + " is disabled on this server.").send(BukkitDirectorContext.sender());
            return;
          }
          if (force || skill.openGui(targetPlayer, true)) {
            FConst.success("Opened GUI for " + skill.getName() + " for " + targetPlayer.getName()).send(BukkitDirectorContext.sender());
          } else {
            FConst.error("Failed to open GUI for " + skill.getName() + " for " + targetPlayer.getName() + " - Permission denied by adapt.use node.").send(BukkitDirectorContext.sender());
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
              FConst.error("Adaptation " + adaptation.getName() + " is disabled on this server.").send(BukkitDirectorContext.sender());
              return;
            }
            if (force || adaptation.openGui(targetPlayer, true)) {
              FConst.success("Opened GUI for " + adaptation.getName() + " for " + targetPlayer.getName()).send(BukkitDirectorContext.sender());
            } else {
              FConst.error("Failed to open GUI for " + adaptation.getName() + " for " + targetPlayer.getName() + " - Permission denied by adapt.use node.").send(BukkitDirectorContext.sender());
            }
            return;
          }
        }
      }
    }

    FConst.error("Unknown GUI target '" + target.name() + "'. Use main, skill:<name>, or adaptation:<name>.").send(BukkitDirectorContext.sender());
  }

  @Director(name = "effects", origin = DirectorOrigin.PLAYER, description = "Toggle Adapt effect visibility for yourself")
  public void effects(
      @Param(aliases = "enabled", description = "Explicit on/off state, omit to toggle", defaultValue = "toggle")
      String enabled
  ) {
    if (!BukkitDirectorContext.hasPermission("adapt.effects")) {
      FConst.error("You lack the Permission 'adapt.effects'").send(BukkitDirectorContext.sender());
      return;
    }

    Player player = BukkitDirectorContext.player();
    PlayerData playerData = Adapt.instance.getAdaptServer().getPlayer(player).getData();
    String normalized = enabled == null ? "toggle" : enabled.trim().toLowerCase(Locale.ROOT);
    Boolean target = switch (normalized) {
      case "toggle" -> !playerData.isEffectsEnabled();
      case "true", "on", "yes", "enabled" -> Boolean.TRUE;
      case "false", "off", "no", "disabled" -> Boolean.FALSE;
      default -> null;
    };

    if (target == null) {
      FConst.error(Localizer.dLocalize("snippets.effects.invalid_state")).send(BukkitDirectorContext.sender());
      return;
    }

    playerData.setEffectsEnabled(target);
    if (target) {
      FConst.success(Localizer.dLocalize("snippets.effects.enabled")).send(BukkitDirectorContext.sender());
    } else {
      FConst.success(Localizer.dLocalize("snippets.effects.disabled")).send(BukkitDirectorContext.sender());
    }
  }

  @Director(name = "configure", aliases = {"config", "cfg"}, origin = DirectorOrigin.PLAYER, description = "Open the in-game Adapt config editor")
  public void configure() {
    if (!ConfigGui.canConfigure(BukkitDirectorContext.player())) {
      FConst.error("You need operator status or the permission 'adapt.configurator'").send(BukkitDirectorContext.sender());
      return;
    }

    ConfigGui.open(BukkitDirectorContext.player());
  }

  @Director(description = "Give an experience orb to yourself or a target player")
  public void experience(
      @Param(aliases = "skill", description = "Skill name, or all / random")
      AdaptationListingHandler.AdaptationSkillList skillName,
      @Param(aliases = "amount", description = "Experience per orb", defaultValue = "10")
      int amount,
      @Param(aliases = "player", description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class)
      Player player

  ) {
    if (!BukkitDirectorContext.hasPermission("adapt.cheatitem")) {
      FConst.error("You lack the Permission 'adapt.cheatitem'").send(BukkitDirectorContext.sender());
      return;
    }

    Player targetPlayer = player;

    if (targetPlayer == null) {
      if (BukkitDirectorContext.isPlayer()) {
        targetPlayer = BukkitDirectorContext.player();
      } else {
        FConst.error("You must be a player to use this command, or Reference a player").send(BukkitDirectorContext.sender());
        return;
      }
    }

    if (skillName.equals("all")) {
      Map<String, Double> experienceMap = new HashMap<>();
      for (Skill<?> skill : allSkillSnapshot()) {
        experienceMap.put(skill.getName(), (double) amount);
      }
      targetPlayer.getInventory().addItem(ExperienceOrb.with(experienceMap));
      FConst.success("Giving all orbs").send(BukkitDirectorContext.sender());
      return;
    }

    if (skillName.equals("random")) {
      List<Skill<?>> skills = allSkillSnapshot();
      if (skills.isEmpty()) {
        FConst.error("No skills are registered.").send(BukkitDirectorContext.sender());
        return;
      }

      targetPlayer.getInventory().addItem(ExperienceOrb.with(skills.get(ThreadLocalRandom.current().nextInt(skills.size())).getName(), amount));
      FConst.success("Giving random orb").send(BukkitDirectorContext.sender());
      return;
    }

    Skill<?> skill = Adapt.instance.getAdaptServer().getSkillRegistry().getAnySkill(skillName.name());
    if (skill != null) {
      targetPlayer.getInventory().addItem(ExperienceOrb.with(skill.getName(), amount));
      FConst.success("Giving " + skill.getName() + " orb").send(BukkitDirectorContext.sender());
    } else {
      FConst.error("Unknown skill '" + skillName.name() + "'. Use a skill name, all, or random.").send(BukkitDirectorContext.sender());
    }
  }

  @Director(description = "Give a knowledge orb to yourself or a target player")
  public void knowledge(
      @Param(aliases = "skill", description = "Skill name, or all / random")
      AdaptationListingHandler.AdaptationSkillList skillName,
      @Param(aliases = "amount", description = "Knowledge per orb", defaultValue = "10")
      int amount,
      @Param(aliases = "player", description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class)
      Player player
  ) {
    if (!BukkitDirectorContext.hasPermission("adapt.cheatitem")) {
      FConst.error("You lack the Permission 'adapt.cheatitem'").send(BukkitDirectorContext.sender());
      return;
    }
    Player targetPlayer = player;

    if (targetPlayer == null) {
      if (BukkitDirectorContext.isPlayer()) {
        targetPlayer = BukkitDirectorContext.player();
      } else {
        FConst.error("You must be a player to use this command").send(BukkitDirectorContext.sender());
        return;
      }
    }

    if (skillName.equals("all")) {
      Map<String, Integer> knowledgeMap = new HashMap<>();
      for (Skill<?> skill : allSkillSnapshot()) {
        knowledgeMap.put(skill.getName(), amount);
      }
      targetPlayer.getInventory().addItem(KnowledgeOrb.with(knowledgeMap));
      FConst.success("Giving all orbs").send(BukkitDirectorContext.sender());
      return;
    }

    if (skillName.equals("random")) {
      List<Skill<?>> skills = allSkillSnapshot();
      if (skills.isEmpty()) {
        FConst.error("No skills are registered.").send(BukkitDirectorContext.sender());
        return;
      }

      targetPlayer.getInventory().addItem(KnowledgeOrb.with(skills.get(ThreadLocalRandom.current().nextInt(skills.size())).getName(), amount));
      FConst.success("Giving random orb").send(BukkitDirectorContext.sender());
      return;
    }

    Skill<?> skill = Adapt.instance.getAdaptServer().getSkillRegistry().getAnySkill(skillName.name());
    if (skill != null) {
      targetPlayer.getInventory().addItem(KnowledgeOrb.with(skill.getName(), amount));
      FConst.success("Giving " + skill.getName() + " orb").send(BukkitDirectorContext.sender());
    } else {
      FConst.error("Unknown skill '" + skillName.name() + "'. Use a skill name, all, or random.").send(BukkitDirectorContext.sender());
    }
  }

  @Director(description = "Assign or unassign an adaptation for a player, as if learning / unlearning it.")
  public void determine(
      @Param(aliases = "adaptationTarget", description = "Adaptation to modify, as skill:adaptation")
      AdaptationListingHandler.AdaptationProvider adaptationTarget,
      @Param(aliases = "assign", description = "true to learn, false to unlearn")
      boolean assign,
      @Param(aliases = "force", description = "Bypass costs and restrictions")
      boolean force,
      @Param(aliases = "level", description = "Adaptation level to apply")
      int level,
      @Param(aliases = "player", description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class)
      Player player

  ) {
    if (!BukkitDirectorContext.hasPermission("adapt.determine")) {
      FConst.error("You lack the Permission 'adapt.determine'").send(BukkitDirectorContext.sender());
      return;
    }

    Player targetPlayer = player;
    if (targetPlayer == null && BukkitDirectorContext.isConsole()) {
      FConst.error("You must specify a player when using this command from console.").send(BukkitDirectorContext.sender());
      return;
    } else if (targetPlayer == null) {
      targetPlayer = BukkitDirectorContext.player();
    }

    //the format is skillname:adaptationname
    String[] split = adaptationTarget.name().split(":", 2);
    if (split.length != 2) {
      FConst.error("Invalid adaptation target format. Use skill:adaptation").send(BukkitDirectorContext.sender());
      return;
    }
    String skillname = split[0];
    String adaptationname = split[1];

    for (Skill<?> skill : SkillRegistry.skills.sortV()) {
      if (skill.getName().equalsIgnoreCase(skillname)) {
        for (Adaptation<?> adaptation : skill.getAdaptations()) {
          if (adaptation.getName().equalsIgnoreCase(adaptationname)) {
            PlayerSkillLine skillLine = Adapt.instance.getAdaptServer().getPlayer(targetPlayer).getData().getSkillLine(skill.getName());
            int previousLevel = skillLine == null ? 0 : skillLine.getAdaptationLevel(adaptation.getName());

            if (assign) {
              adaptation.learn(targetPlayer, level, force);
            } else {
              adaptation.unlearn(targetPlayer, level, force);
            }

            int resultingLevel = skillLine == null ? 0 : skillLine.getAdaptationLevel(adaptation.getName());
            if (resultingLevel == previousLevel) {
              FConst.error("No change: " + adaptation.getName() + " for " + targetPlayer.getName() + " remains at level " + resultingLevel + ". The request was refused (power, knowledge, or permanence) or was already applied; use force=true to override.").send(BukkitDirectorContext.sender());
            } else {
              FConst.success((assign ? "Learned " : "Unlearned ") + adaptation.getName() + " for " + targetPlayer.getName() + ", now at level " + resultingLevel).send(BukkitDirectorContext.sender());
            }
            return;
          }
        }
        FConst.error("Unknown adaptation '" + adaptationname + "' in skill " + skill.getName()).send(BukkitDirectorContext.sender());
        return;
      }
    }

    FConst.error("Unknown skill '" + skillname + "'").send(BukkitDirectorContext.sender());
  }

  @Director(name = "claim-skill", description = "Set a player's skill line level between 0 and 100 for custom UI integration.")
  public void claimSkill(
      @Param(aliases = "skill", description = "Skill line to set")
      AdaptationListingHandler.SkillProvider skillTarget,
      @Param(aliases = "level", description = "Level between 0 and 100")
      int level,
      @Param(aliases = "player", description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class)
      Player player
  ) {
    if (!BukkitDirectorContext.hasPermission("adapt.determine")) {
      FConst.error("You lack the Permission 'adapt.determine'").send(BukkitDirectorContext.sender());
      return;
    }

    Player targetPlayer = resolveTargetPlayer(player);
    if (targetPlayer == null) {
      return;
    }

    if (level < 0 || level > 100) {
      FConst.error("Skill claim level must be between 0 and 100.").send(BukkitDirectorContext.sender());
      return;
    }

    Skill<?> skill = Adapt.instance.getAdaptServer().getSkillRegistry().getAnySkill(skillTarget.name());
    if (skill == null) {
      FConst.error("Unknown skill: " + skillTarget.name()).send(BukkitDirectorContext.sender());
      return;
    }

    PlayerData playerData = Adapt.instance.getAdaptServer().getPlayer(targetPlayer).getData();
    PlayerSkillLine skillLine = playerData.getSkillLine(skill.getName());
    if (skillLine == null) {
      FConst.error("Failed to resolve skill line for " + skill.getName() + ".").send(BukkitDirectorContext.sender());
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

    FConst.success("Set " + targetPlayer.getName() + " " + skill.getName() + " level to " + level + ".").send(BukkitDirectorContext.sender());
  }

  @Director(name = "claim-adaptation", description = "Set an adaptation level between 0 and 100 if the player can afford it.")
  public void claimAdaptation(
      @Param(aliases = "adaptationTarget", description = "Adaptation to set, as skill:adaptation")
      AdaptationListingHandler.AdaptationProvider adaptationTarget,
      @Param(aliases = "level", description = "Level between 0 and 100, clamped to the adaptation max")
      int level,
      @Param(aliases = "force", description = "Bypass power, knowledge, and permanence checks", defaultValue = "false")
      boolean force,
      @Param(aliases = "player", description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class)
      Player player
  ) {
    if (!BukkitDirectorContext.hasPermission("adapt.determine")) {
      FConst.error("You lack the Permission 'adapt.determine'").send(BukkitDirectorContext.sender());
      return;
    }

    Player targetPlayer = resolveTargetPlayer(player);
    if (targetPlayer == null) {
      return;
    }

    if (level < 0 || level > 100) {
      FConst.error("Adaptation claim level must be between 0 and 100.").send(BukkitDirectorContext.sender());
      return;
    }

    String[] split = adaptationTarget.name().split(":", 2);
    if (split.length != 2) {
      FConst.error("Invalid adaptation target format. Use skill:adaptation").send(BukkitDirectorContext.sender());
      return;
    }

    Skill<?> skill = Adapt.instance.getAdaptServer().getSkillRegistry().getAnySkill(split[0]);
    if (skill == null) {
      FConst.error("Unknown skill: " + split[0]).send(BukkitDirectorContext.sender());
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
      FConst.error("Unknown adaptation: " + split[1] + " in skill " + skill.getName()).send(BukkitDirectorContext.sender());
      return;
    }

    PlayerData playerData = Adapt.instance.getAdaptServer().getPlayer(targetPlayer).getData();
    PlayerSkillLine skillLine = playerData.getSkillLine(skill.getName());
    if (skillLine == null) {
      FConst.error("Failed to resolve skill line for " + skill.getName() + ".").send(BukkitDirectorContext.sender());
      return;
    }

    int currentLevel = skillLine.getAdaptationLevel(adaptation.getName());
    int targetLevel = Math.max(0, Math.min(level, adaptation.getMaxLevel()));
    if (targetLevel == currentLevel) {
      FConst.success("No change: " + adaptation.getName() + " is already at level " + currentLevel + ".").send(BukkitDirectorContext.sender());
      return;
    }

    if (targetLevel > currentLevel) {
      int knowledgeCost = adaptation.getCostFor(targetLevel, currentLevel);
      int powerCost = adaptation.getPowerCostFor(targetLevel, currentLevel);

      if (!force) {
        if (!playerData.hasPowerAvailable(powerCost)) {
          FConst.error("Not enough available power. Need " + powerCost + ", have " + playerData.getAvailablePower() + ".").send(BukkitDirectorContext.sender());
          return;
        }

        if (skillLine.getKnowledge() < knowledgeCost) {
          FConst.error("Not enough knowledge in " + skill.getName() + ". Need " + knowledgeCost + ", have " + skillLine.getKnowledge() + ".").send(BukkitDirectorContext.sender());
          return;
        }

        if (!skillLine.spendKnowledge(knowledgeCost)) {
          FConst.error("Failed to spend required knowledge (" + knowledgeCost + ").").send(BukkitDirectorContext.sender());
          return;
        }
      }

      skillLine.setAdaptation(adaptation, targetLevel);
      FConst.success("Set " + targetPlayer.getName() + " " + adaptation.getName() + " to level " + targetLevel + ".").send(BukkitDirectorContext.sender());
      return;
    }

    if (adaptation.isPermanent() && !force) {
      FConst.error(adaptation.getName() + " is permanent and cannot be lowered without force=true.").send(BukkitDirectorContext.sender());
      return;
    }

    int refund = AdaptConfig.get().isHardcoreNoRefunds() ? 0 : adaptation.getRefundCostFor(targetLevel, currentLevel);
    skillLine.setAdaptation(adaptation, targetLevel);
    if (refund > 0) {
      skillLine.giveKnowledge(refund);
    }

    FConst.success("Set " + targetPlayer.getName() + " " + adaptation.getName() + " to level " + targetLevel + ".").send(BukkitDirectorContext.sender());
  }

  @Director(name = "migrate-configs", description = "Force migrate and rewrite all skill/adaptation configs to canonical TOML with comments.")
  public void migrateConfigs() {
    if (!BukkitDirectorContext.hasPermission("adapt.debug")) {
      FConst.error("You lack the Permission 'adapt.debug'").send(BukkitDirectorContext.sender());
      return;
    }

    if (Adapt.instance.getAdaptServer() == null || Adapt.instance.getAdaptServer().getSkillRegistry() == null) {
      FConst.error("Adapt server is not ready yet. Try again in a few seconds.").send(BukkitDirectorContext.sender());
      return;
    }

    int migratedSkills = 0;
    int migratedAdaptations = 0;
    SkillRegistry registry = Adapt.instance.getAdaptServer().getSkillRegistry();
    for (Skill<?> skill : registry.getAllSkills()) {
      int adaptationConfigs = 0;
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        if (adaptation instanceof SimpleAdaptation<?>) {
          adaptationConfigs++;
        }
      }
      if (registry.hotReloadSkillConfig(skill.getName())) {
        if (skill instanceof SimpleSkill<?>) {
          migratedSkills++;
        }
        migratedAdaptations += adaptationConfigs;
      }
    }

    int deletedLegacyJson = ConfigMigrationManager.deleteMigratedLegacyJsonFiles();
    FConst.success("Canonicalized TOML configs. skills=" + migratedSkills + ", adaptations=" + migratedAdaptations + ", deletedLegacyJson=" + deletedLegacyJson).send(BukkitDirectorContext.sender());
  }

  private List<Skill<?>> allSkillSnapshot() {
    if (Adapt.instance != null
        && Adapt.instance.getAdaptServer() != null
        && Adapt.instance.getAdaptServer().getSkillRegistry() != null) {
      return Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills();
    }

    return SkillRegistry.skills.sortV();
  }

  private Player resolveTargetPlayer(Player player) {
    Player targetPlayer = player;
    if (targetPlayer == null && BukkitDirectorContext.isConsole()) {
      FConst.error("You must specify a player when using this command from console.").send(BukkitDirectorContext.sender());
      return null;
    }
    if (targetPlayer == null) {
      targetPlayer = BukkitDirectorContext.player();
    }
    return targetPlayer;
  }
}
