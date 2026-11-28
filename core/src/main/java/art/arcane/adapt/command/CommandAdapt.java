package art.arcane.adapt.command;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.player.LearnOptions;
import art.arcane.adapt.api.player.LearnResult;
import art.arcane.adapt.api.player.MutationResult;
import art.arcane.adapt.api.player.SkillProgress;
import art.arcane.adapt.client.protocol.ClientCapability;
import art.arcane.adapt.client.server.AdaptScreens;
import art.arcane.adapt.client.server.ClientUiService;
import art.arcane.adapt.engine.gui.ConfigGui;
import art.arcane.adapt.engine.gui.MutationGui;
import art.arcane.adapt.engine.item.ExperienceOrb;
import art.arcane.adapt.engine.item.KnowledgeOrb;
import art.arcane.adapt.engine.adaptation.AdaptationRequirements;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.player.EnginePlayerProfile;
import art.arcane.adapt.engine.player.PlayerApi;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptDebugMode;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.LanguageSwitcherPort;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.localization.catalog.SnippetsMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.director.context.AdaptationListingHandler;
import art.arcane.adapt.util.director.specialhandlers.NullablePlayerHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

@Director(name = "adapt", description = "Adapt skills, adaptations, and admin tools", descriptionKey = "adapt.command.help.adapt_skills_adaptations_and_admin_tools")
public class CommandAdapt {
  private static final String NO_SKILL = "---";

  static final double MINIMUM_XP_BOOST = -0.99D;
  static final double MAXIMUM_XP_BOOST = 999D;
  static final long MAXIMUM_XP_BOOST_SECONDS = Long.MAX_VALUE / 1000L;

  private CommandDebug debug;
  private CommandClear clear;
  private CommandReset reset;
  private CommandDefault defaults;
  private CommandPack pack;
  private CommandOrphans orphans;

  @Director(name = "version", hidden = true, description = "Show the installed plugin version", descriptionKey = "adapt.command.help.version")
  @CommandPermission(AdaptCommands.ROOT_PERMISSION)
  public void version(@Param(name = "sender", contextual = true) CommandSource sender) {
    debug.version(sender);
  }

  @Director(description = "Choose your language or the server default")
  @CommandPermission("adapt.language.self")
  public void language() {
    AdaptPorts.get(LanguageSwitcherPort.class, LanguageSwitcherPort.NONE).command(CommandContext.source(), new String[0]);
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

  @Director(description = "Boost Target player Experience gain.", descriptionKey = "adapt.command.help.boost_target_player_experience_gain")
  @CommandPermission("adapt.boost")
  public void boost(
      @Param(aliases = "seconds", description = "Positive duration in seconds", defaultValue = "10", descriptionKey = "adapt.command.help.amount_of_seconds")
      long seconds,
      @Param(aliases = "multiplier", description = "Additive XP multiplier from -0.99 to 999", defaultValue = "10", descriptionKey = "adapt.command.help.strength_of_the_boost")
      double multiplier,
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "adapt.command.help.target_player_defaults_to_you")
      PlayerRef player
  ) {
    CommandSource sender = CommandContext.source();
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

    PlayerRef targetPlayer = resolveTargetPlayer(player, sender);
    if (targetPlayer == null) {
      return;
    }

    CommandTargetExecutor.run(
        targetPlayer,
        () -> boostTarget(targetPlayer, seconds, multiplier, sender),
        sender
    );
  }

  private void boostTarget(PlayerRef targetPlayer, long seconds, double multiplier, CommandSource sender) {
    EnginePlayerProfile profile = resolveReadyTarget(targetPlayer, sender);
    if (profile == null) {
      return;
    }
    profile.boost(multiplier, xpBoostDurationMillis(seconds));

    CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
        CommandRuntimeMessages.BOOSTED_XP,
        trusted("multiplier", multiplier),
        trusted("seconds", seconds)
    )));
  }

  @Director(description = "Boost Global Experience gain.", name = "global-boost", descriptionKey = "adapt.command.help.boost_global_experience_gain")
  @CommandPermission("adapt.boost.global")
  public void globalBoost(
      @Param(aliases = "seconds", description = "Positive duration in seconds", defaultValue = "10", descriptionKey = "adapt.command.help.amount_of_seconds")
      long seconds,
      @Param(aliases = "multiplier", description = "Additive XP multiplier from -0.99 to 999", defaultValue = "10", descriptionKey = "adapt.command.help.strength_of_the_boost")
      double multiplier
  ) {
    if (!CommandContext.hasPermission("adapt.boost.global")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.boost.global")))
          .send(CommandContext.source());
      return;
    }

    if (!isValidXpBoost(seconds, multiplier)) {
      FConst.error(AdaptLanguage.text(
              CommandRuntimeMessages.INVALID_XP_BOOST,
              trusted("seconds", MAXIMUM_XP_BOOST_SECONDS),
              trusted("minimum", MINIMUM_XP_BOOST),
              trusted("maximum", MAXIMUM_XP_BOOST)
          ))
          .send(CommandContext.source());
      return;
    }

    PlayerApi.directory().boost(multiplier, xpBoostDurationMillis(seconds));

    FConst.success(AdaptLanguage.text(
        CommandRuntimeMessages.BOOSTED_XP,
        trusted("multiplier", multiplier),
        trusted("seconds", seconds)
    )).send(CommandContext.source());
  }

  @Director(description = "Show a player's Adapt client UI status and power use",descriptionKey = "adapt.command.help.show_client_ui_status")
  @CommandPermission("adapt.debug")
  public void client(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "adapt.command.help.target_player_defaults_to_you")
      PlayerRef player
  ) {
    CommandSource sender = CommandContext.source();
    if (!sender.hasPermission("adapt.debug")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.debug")))
          .send(sender);
      return;
    }
    PlayerRef targetPlayer = resolveTargetPlayer(player, sender);
    if (targetPlayer == null) {
      return;
    }
    ClientUiService service = ClientUiService.current();
    Optional<ClientCapability> capability = service == null
        ? Optional.empty()
        : service.capability(targetPlayer.uuid());
    if (capability.isEmpty()) {
      FConst.info(AdaptLanguage.text(CommandRuntimeMessages.CLIENT_UI_ABSENT, untrusted("player", targetPlayer.name())))
          .send(sender);
    } else {
      FConst.success(AdaptLanguage.text(
          CommandRuntimeMessages.CLIENT_UI_NEGOTIATED,
          untrusted("player", targetPlayer.name()),
          trusted("version", capability.get().version()),
          trusted("capabilities", String.join(", ", ClientUiService.capabilityNames(capability.get().mask())))
      )).send(sender);
    }
    EnginePlayerProfile profile = resolveReadyTarget(targetPlayer, sender);
    if (profile == null) {
      return;
    }
    for (String line : powerReport(profile.runtime().getData(), targetPlayer.name())) {
      FConst.info(line).send(sender);
    }
  }

  /** The player's power use, then every stored adaptation with a level and whether it takes a power slot. */
  static List<String> powerReport(PlayerData data, String playerName) {
    Set<String> held = new HashSet<>();
    for (PlayerData.HeldAdaptation adaptation : data.heldAdaptations()) {
      held.add(adaptation.line() + ":" + adaptation.adaptation().getId().toString());
    }
    List<String> report = new ArrayList<>();
    report.add(AdaptLanguage.text(
        CommandRuntimeMessages.POWER_USAGE,
        untrusted("player", playerName),
        trusted("used", held.size()),
        trusted("max", data.getMaxPower())
    ));
    for (String lineId : new TreeSet<>(data.getSkillLines().keySet())) {
      PlayerSkillLine line = data.getSkillLines().get(lineId);
      if (line == null) {
        continue;
      }
      for (String adaptationId : new TreeSet<>(line.getAdaptations().keySet())) {
        int level = line.getAdaptationLevel(adaptationId);
        if (level <= 0) {
          continue;
        }
        TextKey status = held.contains(lineId + ":" + adaptationId)
            ? CommandRuntimeMessages.POWER_HELD
            : CommandRuntimeMessages.POWER_FREE;
        report.add(AdaptLanguage.text(status, untrusted("skill", lineId), untrusted("adaptation", adaptationId),
            trusted("level", level)));
      }
    }
    return report;
  }

  @Director(description = "Open the Adapt GUI", descriptionKey = "adapt.command.help.open_the_adapt_gui")
  @CommandPermission("adapt.gui")
  public void gui(
      @Param(description = "GUI to open: main, skill:<name>, or adaptation:<name>", defaultValue = "main", descriptionKey = "adapt.command.help.gui_to_open_main_skill_name_or_adaptation_name")
      AdaptationListingHandler.AdaptationList target,
      @Param(aliases = "player", description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "adapt.command.help.target_player_defaults_to_you")
      PlayerRef player,
      @Param(aliases = "force", description = "Bypass adapt.use permission checks when opening", defaultValue = "false", descriptionKey = "adapt.command.help.bypass_adapt_use_permission_checks_when_opening")
      boolean force
  ) {
    CommandSource sender = CommandContext.source();
    if (!sender.hasPermission("adapt.gui")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.gui")))
          .send(sender);
      return;
    }

    PlayerRef targetPlayer = resolveTargetPlayer(player, sender);
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
      PlayerRef targetPlayer,
      boolean force,
      CommandSource sender
  ) {
    if (resolveReadyTarget(targetPlayer, sender) == null) {
      return;
    }

    if (target.equals("main")) {
      AdaptScreens.openSkills(targetPlayer);
      return;
    }

    if (target.startsWith("skill:")) {
      Id targetId = CommandTargets.id(target.name().substring("skill:".length()));
      for (Skill<?> skill : registry().getSkills()) {
        if (skill.getId().equals(targetId)) {
          if (!skill.isEnabled()) {
            CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
                CommandRuntimeMessages.SKILL_DISABLED,
                untrusted("skill", SkillHost.lineKey(skill))
            )));
            return;
          }
          if (openSkillGui(skill, targetPlayer, force)) {
            CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
                CommandRuntimeMessages.GUI_OPENED,
                untrusted("target", SkillHost.lineKey(skill)),
                untrusted("player", targetPlayer.name())
            )));
          } else {
            CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
                CommandRuntimeMessages.GUI_OPEN_DENIED,
                untrusted("target", SkillHost.lineKey(skill)),
                untrusted("player", targetPlayer.name())
            )));
          }
          return;
        }
      }
    }

    if (target.startsWith("adaptation:")) {
      Id targetId = CommandTargets.id(target.name().substring("adaptation:".length()));
      for (Skill<?> skill : registry().getSkills()) {
        for (Adaptation<?> adaptation : skill.getAdaptations()) {
          if (adaptation.getId().equals(targetId)) {
            if (!skill.isEnabled() || !adaptation.isEnabled()) {
              CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
                  CommandRuntimeMessages.ADAPTATION_DISABLED,
                  untrusted("adaptation", adaptation.getId().toString())
              )));
              return;
            }
            if (openAdaptationGui(adaptation, targetPlayer, force)) {
              CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
                  CommandRuntimeMessages.GUI_OPENED,
                  untrusted("target", adaptation.getId().toString()),
                  untrusted("player", targetPlayer.name())
              )));
            } else {
              CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
                  CommandRuntimeMessages.GUI_OPEN_DENIED,
                  untrusted("target", adaptation.getId().toString()),
                  untrusted("player", targetPlayer.name())
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

  @Director(name = "effects", origin = DirectorOrigin.PLAYER, description = "Toggle Adapt effect visibility for yourself", descriptionKey = "adapt.command.help.toggle_adapt_effect_visibility_for_yourself")
  @CommandPermission("adapt.effects")
  public void effects(
      @Param(aliases = "enabled", description = "Explicit on/off state, omit to toggle", defaultValue = "toggle", descriptionKey = "adapt.command.help.explicit_on_off_state_omit_to_toggle")
      String enabled
  ) {
    if (!CommandContext.hasPermission("adapt.effects")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.effects")))
          .send(CommandContext.source());
      return;
    }

    CommandSource sender = CommandContext.source();
    PlayerRef player = CommandContext.player();
    EnginePlayerProfile profile = resolveReadyTarget(player, sender);
    if (profile == null) {
      return;
    }
    PlayerData playerData = profile.runtime().getData();
    String normalized = enabled == null ? "toggle" : enabled.trim().toLowerCase(Locale.ROOT);
    Boolean target = switch (normalized) {
      case "toggle" -> !playerData.isEffectsEnabled();
      case "true", "on", "yes", "enabled" -> Boolean.TRUE;
      case "false", "off", "no", "disabled" -> Boolean.FALSE;
      default -> null;
    };

    if (target == null) {
      FConst.error(AdaptLanguage.text(SnippetsMessages.EFFECTS_INVALID_STATE)).send(CommandContext.source());
      return;
    }

    playerData.setEffectsEnabled(target);
    if (target) {
      FConst.success(AdaptLanguage.text(SnippetsMessages.EFFECTS_ENABLED)).send(CommandContext.source());
    } else {
      FConst.success(AdaptLanguage.text(SnippetsMessages.EFFECTS_DISABLED)).send(CommandContext.source());
    }
  }

  @Director(name = "mutate", origin = DirectorOrigin.PLAYER, description = "Merge two of your skills into one mutated skill. This cannot be undone.", descriptionKey = "adapt.command.help.merge_two_of_your_skills")
  @CommandPermission("adapt.mutate")
  public void mutate(
      @Param(aliases = "lead", description = "Skill that leads the mutation and names it", defaultValue = NO_SKILL, descriptionKey = "adapt.command.help.lead_skill")
      AdaptationListingHandler.SkillProvider lead,
      @Param(aliases = "partner", description = "Skill merged into the lead", defaultValue = NO_SKILL, descriptionKey = "adapt.command.help.partner_skill")
      AdaptationListingHandler.SkillProvider partner
  ) {
    CommandSource sender = CommandContext.source();
    if (!sender.hasPermission("adapt.mutate")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.mutate")))
          .send(sender);
      return;
    }
    PlayerRef player = CommandContext.player();
    CommandTargetExecutor.run(player, () -> mutateTarget(lead.name(), partner.name(), player, sender), sender);
  }

  private void mutateTarget(String leadName, String partnerName, PlayerRef player, CommandSource sender) {
    if (resolveReadyTarget(player, sender) == null) {
      return;
    }
    if (NO_SKILL.equals(leadName)) {
      MutationGui.openLeads(player);
      return;
    }
    SkillRegistry registry = AdaptCore.get().getAdaptServer().getSkillRegistry();
    Skill<?> lead = registry.getSkill(leadName);
    Skill<?> partner = NO_SKILL.equals(partnerName) ? null : registry.getSkill(partnerName);
    if (lead != null && NO_SKILL.equals(partnerName)) {
      AdaptScreens.openMutation(player, lead);
      return;
    }
    if (lead == null || partner == null) {
      MutationResult.Status refused = (lead == null && knownElsewhere(registry, leadName))
          || (partner == null && knownElsewhere(registry, partnerName))
          ? MutationResult.Status.UNAVAILABLE_SKILL : MutationResult.Status.UNKNOWN_SKILL;
      CommandTargetExecutor.send(sender, FConst.error(MutationGui.resultText(refused, lead, partner)));
      return;
    }
    MutationGui.complete(player, lead, partner);
  }

  private static boolean knownElsewhere(SkillRegistry registry, String skill) {
    Id id = Id.tryParseAdapt(skill.toLowerCase(Locale.ROOT));
    return id != null && registry.getAnySkill(id) != null;
  }

  @Director(name = "mutation-reset", description = "Remove all of a player's skill mutations; both parent skills of each come back", descriptionKey = "adapt.command.help.remove_a_player_s_mutations")
  @CommandPermission(MutationGui.UNMUTATE_PERMISSION)
  public void mutationReset(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "adapt.command.help.target_player_defaults_to_you")
      PlayerRef player
  ) {
    CommandSource sender = CommandContext.source();
    PlayerRef targetPlayer = unmutateTarget(player, sender);
    if (targetPlayer == null) {
      return;
    }
    CommandTargetExecutor.run(targetPlayer, () -> {
      EnginePlayerProfile profile = resolveReadyTarget(targetPlayer, sender);
      if (profile == null) {
        return;
      }
      int removed = profile.unmutateAll().size();
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(CommandRuntimeMessages.MUTATIONS_RESET,
          trusted("count", removed), untrusted("player", targetPlayer.name()))));
    }, sender);
  }

  @Director(name = "unmutate", description = "Remove one of a player's skill mutations; both parent skills come back", descriptionKey = "adapt.command.help.unmutate")
  @CommandPermission(MutationGui.UNMUTATE_PERMISSION)
  public void unmutate(
      @Param(aliases = "player", description = "The player whose mutation to remove", descriptionKey = "adapt.command.help.unmutate_player")
      PlayerRef player,
      @Param(aliases = "skill", description = "The mutated skill, its lead or its partner", descriptionKey = "adapt.command.help.mutated_skill")
      String skill
  ) {
    CommandSource sender = CommandContext.source();
    PlayerRef targetPlayer = unmutateTarget(player, sender);
    if (targetPlayer == null) {
      return;
    }
    CommandTargetExecutor.run(targetPlayer, () -> {
      EnginePlayerProfile profile = resolveReadyTarget(targetPlayer, sender);
      if (profile == null) {
        return;
      }
      SkillPair mutation = SkillMutations.mutationNamed(profile.runtime().getData(), skill);
      MutationResult.Status result = mutation == null ? MutationResult.Status.NOT_MUTATED
          : profile.unmutate(mutation.lead()).status();
      if (result == MutationResult.Status.CANCELLED) {
        CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MUTATION_CANCELLED)));
        return;
      }
      if (result != MutationResult.Status.UNMUTATED) {
        CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MUTATION_NOT_HELD,
            untrusted("player", targetPlayer.name()), untrusted("skill", skill))));
        return;
      }
      CommandTargetExecutor.send(sender, FConst.success(MutationGui.unmutatedText(mutation, targetPlayer.name())));
    }, sender);
  }

  /** The player an unmutate command acts on, or null when the sender lacks the permission or names no one. */
  private PlayerRef unmutateTarget(PlayerRef player, CommandSource sender) {
    if (!sender.hasPermission(MutationGui.UNMUTATE_PERMISSION)) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", MutationGui.UNMUTATE_PERMISSION)))
          .send(sender);
      return null;
    }
    return resolveTargetPlayer(player, sender);
  }

  /**
   * What {@code /adapt unmutate <player> <skill>} can name for the player {@code playerName}: their mutated skills and
   * the skills in them, starting with {@code typed}. Empty without the permission.
   */
  static List<String> mutationSuggestions(CommandSource source, String playerName, String typed) {
    AdaptCore core = AdaptCore.current();
    AdaptServer server = core == null ? null : core.getAdaptServer();
    if (server == null || !source.hasPermission(MutationGui.UNMUTATE_PERMISSION)) {
      return List.of();
    }
    String name = playerName.regionMatches(true, 0, "player=", 0, 7) ? playerName.substring(7) : playerName;
    String key = typed.regionMatches(true, 0, "skill=", 0, 6) ? "skill=" : "";
    String prefix = typed.substring(key.length()).toLowerCase(Locale.ROOT);
    List<String> suggestions = new ArrayList<>();
    for (PlayerRef online : server.getOnlinePlayerSnapshot()) {
      AdaptPlayer adaptPlayer = online.name().equalsIgnoreCase(name) ? server.getOnlineAdaptPlayer(online.uuid()) : null;
      if (adaptPlayer == null) {
        continue;
      }
      for (SkillPair mutation : adaptPlayer.getData().getMutations()) {
        for (String option : List.of(mutation.key(), mutation.lead().toString(), mutation.partner().toString())) {
          if (option.startsWith(prefix)) {
            suggestions.add(key + option);
          }
        }
      }
    }
    return suggestions;
  }

  @Director(name = "configure", aliases = {"config", "cfg"}, origin = DirectorOrigin.PLAYER, description = "Open the in-game Adapt config editor", descriptionKey = "adapt.command.help.open_the_in_game_adapt_config_editor")
  @CommandPermission("adapt.configurator")
  public void configure() {
    PlayerRef player = CommandContext.player();
    if (!ConfigGui.canConfigure(player)) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.CONFIGURATOR_REQUIRED))
          .send(CommandContext.source());
      return;
    }

    ConfigGui.open(player);
  }

  @Director(description = "Give an experience orb to yourself or a target player", descriptionKey = "adapt.command.help.give_an_experience_orb_to_yourself_or_a_target_player")
  @CommandPermission("adapt.cheatitem")
  public void experience(
      @Param(aliases = "skill", description = "Skill name, or all / random", descriptionKey = "adapt.command.help.skill_name_or_all_random")
      AdaptationListingHandler.AdaptationSkillList skillName,
      @Param(aliases = "amount", description = "Experience per orb", defaultValue = "10", descriptionKey = "adapt.command.help.experience_per_orb")
      int amount,
      @Param(aliases = "player", description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "adapt.command.help.target_player_defaults_to_you")
      PlayerRef player

  ) {
    CommandSource sender = CommandContext.source();
    if (!sender.hasPermission("adapt.cheatitem")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.cheatitem")))
          .send(sender);
      return;
    }

    PlayerRef targetPlayer = resolveTargetPlayer(player, sender, CommandRuntimeMessages.PLAYER_ONLY_OR_REFERENCE);
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
      PlayerRef targetPlayer,
      CommandSource sender
  ) {
    if (skillName.equals("all")) {
      Map<String, Double> experienceMap = new HashMap<>();
      for (Skill<?> skill : allSkillSnapshot()) {
        experienceMap.put(SkillHost.lineKey(skill), (double) amount);
      }
      targetPlayer.inventory().addItem(ExperienceOrb.with(experienceMap));
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(CommandRuntimeMessages.GIVING_ALL_ORBS)));
      return;
    }

    if (skillName.equals("random")) {
      List<Skill<?>> skills = allSkillSnapshot();
      if (skills.isEmpty()) {
        CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(CommandRuntimeMessages.NO_SKILLS_REGISTERED)));
        return;
      }

      targetPlayer.inventory().addItem(ExperienceOrb.with(Map.of(SkillHost.lineKey(skills.get(ThreadLocalRandom.current().nextInt(skills.size()))), (double) amount)));
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(CommandRuntimeMessages.GIVING_RANDOM_ORB)));
      return;
    }

    Id skillId = CommandTargets.id(skillName.name());
    Skill<?> skill = CommandTargets.findSkill(skillId);
    if (skill != null) {
      targetPlayer.inventory().addItem(ExperienceOrb.with(Map.of(SkillHost.lineKey(skill), (double) amount)));
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
          CommandRuntimeMessages.GIVING_SKILL_ORB,
          untrusted("skill", SkillHost.lineKey(skill))
      )));
    } else {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.UNKNOWN_SKILL_ORB,
          untrusted("skill", CommandTargets.shown(skillName.name(), skillId))
      )));
    }
  }

  @Director(description = "Give a knowledge orb to yourself or a target player", descriptionKey = "adapt.command.help.give_a_knowledge_orb_to_yourself_or_a_target_player")
  @CommandPermission("adapt.cheatitem")
  public void knowledge(
      @Param(aliases = "skill", description = "Skill name, or all / random", descriptionKey = "adapt.command.help.skill_name_or_all_random")
      AdaptationListingHandler.AdaptationSkillList skillName,
      @Param(aliases = "amount", description = "Knowledge per orb", defaultValue = "10", descriptionKey = "adapt.command.help.knowledge_per_orb")
      int amount,
      @Param(aliases = "player", description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "adapt.command.help.target_player_defaults_to_you")
      PlayerRef player
  ) {
    CommandSource sender = CommandContext.source();
    if (!sender.hasPermission("adapt.cheatitem")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.cheatitem")))
          .send(sender);
      return;
    }

    PlayerRef targetPlayer = resolveTargetPlayer(player, sender, CommandRuntimeMessages.PLAYER_ONLY);
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
      PlayerRef targetPlayer,
      CommandSource sender
  ) {
    if (skillName.equals("all")) {
      Map<String, Integer> knowledgeMap = new HashMap<>();
      for (Skill<?> skill : allSkillSnapshot()) {
        knowledgeMap.put(SkillHost.lineKey(skill), amount);
      }
      targetPlayer.inventory().addItem(KnowledgeOrb.with(knowledgeMap));
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(CommandRuntimeMessages.GIVING_ALL_ORBS)));
      return;
    }

    if (skillName.equals("random")) {
      List<Skill<?>> skills = allSkillSnapshot();
      if (skills.isEmpty()) {
        CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(CommandRuntimeMessages.NO_SKILLS_REGISTERED)));
        return;
      }

      targetPlayer.inventory().addItem(KnowledgeOrb.with(Map.of(SkillHost.lineKey(skills.get(ThreadLocalRandom.current().nextInt(skills.size()))), amount)));
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(CommandRuntimeMessages.GIVING_RANDOM_ORB)));
      return;
    }

    Id skillId = CommandTargets.id(skillName.name());
    Skill<?> skill = CommandTargets.findSkill(skillId);
    if (skill != null) {
      targetPlayer.inventory().addItem(KnowledgeOrb.with(Map.of(SkillHost.lineKey(skill), amount)));
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
          CommandRuntimeMessages.GIVING_SKILL_ORB,
          untrusted("skill", SkillHost.lineKey(skill))
      )));
    } else {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.UNKNOWN_SKILL_ORB,
          untrusted("skill", CommandTargets.shown(skillName.name(), skillId))
      )));
    }
  }

  @Director(description = "Assign or unassign an adaptation for a player, as if learning / unlearning it.", descriptionKey = "adapt.command.help.assign_or_unassign_an_adaptation_for_a_player_as_if_learning_unlearning_it")
  @CommandPermission("adapt.determine")
  public void determine(
      @Param(aliases = "adaptationTarget", description = "Adaptation to modify, by id such as herbalism/luck", descriptionKey = "adapt.command.help.adaptation_to_modify_as_skill_adaptation")
      AdaptationListingHandler.AdaptationProvider adaptationTarget,
      @Param(aliases = "assign", description = "true to learn, false to unlearn", descriptionKey = "adapt.command.help.true_to_learn_false_to_unlearn")
      boolean assign,
      @Param(aliases = "force", description = "Bypass costs and permanence; prerequisites still apply", descriptionKey = "adapt.command.help.bypass_costs_and_permanence_prerequisites_still_apply")
      boolean force,
      @Param(aliases = "level", description = "Adaptation level to apply", descriptionKey = "adapt.command.help.adaptation_level_to_apply")
      int level,
      @Param(aliases = "player", description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "adapt.command.help.target_player_defaults_to_you")
      PlayerRef player

  ) {
    CommandSource sender = CommandContext.source();
    if (!sender.hasPermission("adapt.determine")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.determine")))
          .send(sender);
      return;
    }

    PlayerRef targetPlayer = resolveTargetPlayer(player, sender);
    if (targetPlayer == null) {
      return;
    }

    Adaptation<?> adaptation = CommandTargets.adaptation(adaptationTarget.name(), sender);
    if (adaptation == null) {
      return;
    }

    CommandTargetExecutor.run(
        targetPlayer,
        () -> determineTarget(adaptation, assign, force, level, targetPlayer, sender),
        sender
    );
  }

  private void determineTarget(
      Adaptation<?> adaptation,
      boolean assign,
      boolean force,
      int level,
      PlayerRef targetPlayer,
      CommandSource sender
  ) {
    EnginePlayerProfile profile = resolveReadyTarget(targetPlayer, sender);
    if (profile == null) {
      return;
    }
    if (!adaptation.isEnabled() || adaptation.getSkill() == null || !adaptation.getSkill().isEnabled()) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.ADAPTATION_DISABLED,
          untrusted("adaptation", adaptation.getId().toString())
      )));
      return;
    }

    Id adaptationId = adaptation.getId();
    LearnResult result = assign
        ? profile.learn(adaptationId, level, (force ? LearnOptions.forced() : LearnOptions.paying()).quietly())
        : profile.unlearn(adaptationId, level - 1, (force || AdaptDebugMode.isActive(targetPlayer) ? LearnOptions.forced() : LearnOptions.paying()).quietly());

    if (sendRequirementFailure(result.status(), adaptation, targetPlayer, profile, level - 1, sender)) {
      return;
    }
    if (result.toLevel() == result.fromLevel()) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.NO_ADAPTATION_CHANGE,
          untrusted("adaptation", adaptationId.toString()),
          untrusted("player", targetPlayer.name()),
          trusted("level", result.toLevel())
      )));
    } else {
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
          assign ? CommandRuntimeMessages.LEARNED_ADAPTATION : CommandRuntimeMessages.UNLEARNED_ADAPTATION,
          untrusted("adaptation", adaptationId.toString()),
          untrusted("player", targetPlayer.name()),
          trusted("level", result.toLevel())
      )));
    }
  }

  @Director(name = "claim-skill", description = "Set a player's skill line level between 0 and 100 for custom UI integration.", descriptionKey = "adapt.command.help.set_a_player_s_skill_line_level_between_0_and_100_for_custom_ui_integration")
  @CommandPermission("adapt.determine")
  public void claimSkill(
      @Param(aliases = "skill", description = "Skill line to set", descriptionKey = "adapt.command.help.skill_line_to_set")
      AdaptationListingHandler.SkillProvider skillTarget,
      @Param(aliases = "level", description = "Level between 0 and 100", descriptionKey = "adapt.command.help.level_between_0_and_100")
      int level,
      @Param(aliases = "player", description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "adapt.command.help.target_player_defaults_to_you")
      PlayerRef player
  ) {
    CommandSource sender = CommandContext.source();
    if (!sender.hasPermission("adapt.determine")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.determine")))
          .send(sender);
      return;
    }

    PlayerRef targetPlayer = resolveTargetPlayer(player, sender);
    if (targetPlayer == null) {
      return;
    }

    if (level < 0 || level > 100) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.SKILL_LEVEL_RANGE)).send(sender);
      return;
    }

    Skill<?> skill = CommandTargets.skill(skillTarget.name(), sender);
    if (skill == null) {
      return;
    }

    CommandTargetExecutor.run(
        targetPlayer,
        () -> claimSkillTarget(skill, level, targetPlayer, sender),
        sender
    );
  }

  private void claimSkillTarget(Skill<?> skill, int level, PlayerRef targetPlayer, CommandSource sender) {
    EnginePlayerProfile profile = resolveReadyTarget(targetPlayer, sender);
    if (profile == null) {
      return;
    }
    SkillProgress progress = profile.skill(skill.getId());
    if (!progress.isAvailable()) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.SKILL_LINE_UNAVAILABLE,
          untrusted("skill", SkillHost.lineKey(skill))
      )));
      return;
    }

    progress.setLevel(level);

    CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
        CommandRuntimeMessages.SET_SKILL_LEVEL,
        untrusted("player", targetPlayer.name()),
        untrusted("skill", SkillHost.lineKey(skill)),
        trusted("level", level)
    )));
  }

  @Director(name = "claim-adaptation", description = "Set an adaptation level between 0 and 100 if the player can afford it.", descriptionKey = "adapt.command.help.set_an_adaptation_level_between_0_and_100_if_the_player_can_afford_it")
  @CommandPermission("adapt.determine")
  public void claimAdaptation(
      @Param(aliases = "adaptationTarget", description = "Adaptation to set, by id such as herbalism/luck", descriptionKey = "adapt.command.help.adaptation_to_set_as_skill_adaptation")
      AdaptationListingHandler.AdaptationProvider adaptationTarget,
      @Param(aliases = "level", description = "Level between 0 and 100, clamped to the adaptation max", descriptionKey = "adapt.command.help.level_between_0_and_100_clamped_to_the_adaptation_max")
      int level,
      @Param(aliases = "force", description = "Bypass power, knowledge, and permanence checks", defaultValue = "false", descriptionKey = "adapt.command.help.bypass_power_knowledge_and_permanence_checks")
      boolean force,
      @Param(aliases = "player", description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "adapt.command.help.target_player_defaults_to_you")
      PlayerRef player
  ) {
    CommandSource sender = CommandContext.source();
    if (!sender.hasPermission("adapt.determine")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.determine")))
          .send(sender);
      return;
    }

    PlayerRef targetPlayer = resolveTargetPlayer(player, sender);
    if (targetPlayer == null) {
      return;
    }

    if (level < 0 || level > 100) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.ADAPTATION_LEVEL_RANGE))
          .send(sender);
      return;
    }

    Adaptation<?> adaptation = CommandTargets.adaptation(adaptationTarget.name(), sender);
    if (adaptation == null) {
      return;
    }

    Skill<?> skill = adaptation.getSkill();
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
      PlayerRef targetPlayer,
      CommandSource sender
  ) {
    EnginePlayerProfile profile = resolveReadyTarget(targetPlayer, sender);
    if (profile == null) {
      return;
    }
    SkillProgress progress = profile.skill(skill.getId());
    if (!progress.isAvailable()) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.SKILL_LINE_UNAVAILABLE,
          untrusted("skill", SkillHost.lineKey(skill))
      )));
      return;
    }

    int currentLevel = progress.adaptations().getOrDefault(adaptation.getId(), 0);
    int targetLevel = Math.max(0, Math.min(level, adaptation.getMaxLevel()));
    if (targetLevel == currentLevel) {
      CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
          CommandRuntimeMessages.ADAPTATION_ALREADY_LEVEL,
          untrusted("adaptation", adaptation.getId().toString()),
          trusted("level", currentLevel)
      )));
      return;
    }

    LearnOptions options = (force ? LearnOptions.forced() : LearnOptions.paying()).quietly();
    boolean raising = targetLevel > currentLevel;
    LearnResult result = raising
        ? profile.learn(adaptation.getId(), targetLevel, options)
        : profile.unlearn(adaptation.getId(), targetLevel, options);
    if (!result.succeeded()) {
      if (sendRequirementFailure(result.status(), adaptation, targetPlayer, profile, targetLevel, sender)) {
        return;
      }
      sendLearningFailure(
          result.status(),
          skill,
          adaptation,
          targetPlayer,
          profile,
          progress,
          raising ? adaptation.getCostFor(targetLevel, currentLevel) : 0,
          raising ? adaptation.getPowerCostFor(targetLevel, currentLevel) : 0,
          sender
      );
      return;
    }
    CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
        CommandRuntimeMessages.SET_ADAPTATION_LEVEL,
        untrusted("player", targetPlayer.name()),
        untrusted("adaptation", adaptation.getId().toString()),
        trusted("level", targetLevel)
    )));
  }

  private boolean sendRequirementFailure(
      LearnResult.Status result,
      Adaptation<?> adaptation,
      PlayerRef targetPlayer,
      EnginePlayerProfile profile,
      int targetLevel,
      CommandSource sender
  ) {
    PlayerData playerData = profile.runtime().getData();
    if (result == LearnResult.Status.REQUIREMENTS_MISSING) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.REQUIREMENTS_MISSING,
          untrusted("player", targetPlayer.name()),
          untrusted("requirements", AdaptationRequirements.describe(
              AdaptationRequirements.missing(adaptation, playerData)
          )),
          untrusted("adaptation", adaptation.getId().toString())
      )));
      return true;
    }
    if (result == LearnResult.Status.REQUIRED_BY_DEPENDENTS) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.REQUIRED_BY_DEPENDENTS,
          untrusted("adaptation", adaptation.getId().toString()),
          untrusted("dependents", AdaptationRequirements.names(
              AdaptationRequirements.dependents(adaptation, playerData, targetLevel)
          )),
          untrusted("player", targetPlayer.name())
      )));
      return true;
    }
    return false;
  }

  private void sendLearningFailure(
      LearnResult.Status result,
      Skill<?> skill,
      Adaptation<?> adaptation,
      PlayerRef targetPlayer,
      EnginePlayerProfile profile,
      SkillProgress progress,
      int knowledgeCost,
      int powerCost,
      CommandSource sender
  ) {
    if (result == LearnResult.Status.INSUFFICIENT_POWER) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.POWER_INSUFFICIENT,
          trusted("needed", powerCost),
          trusted("available", profile.maxPower() - profile.power())
      )));
      return;
    }
    if (result == LearnResult.Status.INSUFFICIENT_KNOWLEDGE) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.KNOWLEDGE_INSUFFICIENT,
          untrusted("skill", SkillHost.lineKey(skill)),
          trusted("needed", knowledgeCost),
          trusted("available", progress.knowledge())
      )));
      return;
    }
    if (result == LearnResult.Status.INSUFFICIENT_FUNDS) {
      CommandTargetExecutor.send(
          sender,
          FConst.error(AdaptLanguage.text(CommandRuntimeMessages.VAULT_INSUFFICIENT_FUNDS))
      );
      return;
    }
    if (result == LearnResult.Status.ECONOMY_UNAVAILABLE || result == LearnResult.Status.ECONOMY_FAILED) {
      CommandTargetExecutor.send(
          sender,
          FConst.error(AdaptLanguage.text(CommandRuntimeMessages.VAULT_TRANSACTION_FAILED))
      );
      return;
    }
    if (result == LearnResult.Status.PERMANENT) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.PERMANENT_ADAPTATION_LOWER,
          untrusted("adaptation", adaptation.getId().toString())
      )));
      return;
    }
    if (result == LearnResult.Status.CANCELLED) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.ADAPTATION_CHANGE_CANCELLED,
          untrusted("adaptation", adaptation.getId().toString())
      )));
      return;
    }
    CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
        CommandRuntimeMessages.NO_ADAPTATION_CHANGE,
        untrusted("adaptation", adaptation.getId().toString()),
        untrusted("player", targetPlayer.name()),
        trusted("level", progress.adaptations().getOrDefault(adaptation.getId(), 0))
    )));
  }

  static boolean openSkillGui(Skill<?> skill, PlayerRef player, boolean force) {
    return AdaptScreens.openSkill(player, skill, !force);
  }

  static boolean openAdaptationGui(Adaptation<?> adaptation, PlayerRef player, boolean force) {
    return AdaptScreens.openAdaptation(player, adaptation, !force);
  }

  private List<Skill<?>> allSkillSnapshot() {
    return registry().getAllSkills();
  }

  private static SkillRegistry registry() {
    return AdaptCore.get().getAdaptServer().getSkillRegistry();
  }

  private PlayerRef resolveTargetPlayer(PlayerRef player, CommandSource sender) {
    return resolveTargetPlayer(player, sender, CommandRuntimeMessages.PLAYER_REQUIRED_FROM_CONSOLE);
  }

  private PlayerRef resolveTargetPlayer(PlayerRef player, CommandSource sender, TextKey consoleMessage) {
    if (player != null) {
      return player;
    }
    PlayerRef senderPlayer = sender.player().orElse(null);
    if (senderPlayer == null) {
      FConst.error(AdaptLanguage.text(consoleMessage)).send(sender);
    }
    return senderPlayer;
  }

  private EnginePlayerProfile resolveReadyTarget(PlayerRef targetPlayer, CommandSource sender) {
    EnginePlayerProfile profile = AdaptCore.current() == null ? null : PlayerApi.directory().profile(targetPlayer);
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
