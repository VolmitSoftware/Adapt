package art.arcane.adapt.command;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.telemetry.AbilityCheckTelemetry;
import art.arcane.adapt.api.world.AdaptDebugMode;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.fx.ParticleSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.director.handlers.ParticleHandler;
import art.arcane.adapt.util.director.specialhandlers.NullablePlayerHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

@Director(name = "debug", origin = DirectorOrigin.BOTH, description = "Adapt Debug Command", aliases = {"dev"}, descriptionKey = "command.help.adapt_debug_command")
public class CommandDebug {

  @Director(name = "version", description = "Show the installed plugin version", descriptionKey = "command.help.version")
  public void version(@Param(name = "sender", contextual = true) CommandSource sender) {
    sender.sendMarkup(AdaptPorts.get(CommandHelpPort.class, CommandHelpPort.NONE)
        .version("Adapt", AdaptCore.platform().host().adaptVersion()));
  }

  @Director(name = "dump", sync = true, description = "Create and optionally upload a diagnostic report", descriptionKey = "command.help.debug_dump")
  public void dump(
    @Param(name = "upload", defaultValue = "true", description = "Upload the report to mclo.gs", descriptionKey = "command.help.debug_upload") boolean upload,
    @Param(name = "sender", contextual = true) CommandSource sender
  ) {
    AdaptPorts.get(DebugDumpPort.class, DebugDumpPort.NONE).request(sender, upload);
  }


  @Director(name = "mode", description = "Toggle debug mode: reveals every skill and adaptation and makes learning free and uncapped", descriptionKey = "command.help.toggle_debug_mode_reveals_every_skill_and_adaptation_and_makes_learning_free_and_uncapped")
  public void mode(
      @Param(aliases = "enabled", description = "Explicit on/off state, omit to toggle", defaultValue = "toggle", descriptionKey = "command.help.explicit_on_off_state_omit_to_toggle")
      String enabled,
      @Param(aliases = "player", description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      PlayerRef player
  ) {
    CommandSource sender = CommandContext.source();
    if (!sender.hasPermission("adapt.debug")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.debug")))
          .send(sender);
      return;
    }

    PlayerRef targetPlayer = player != null ? player : sender.player().orElse(null);
    if (targetPlayer == null) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.PLAYER_REQUIRED_FROM_CONSOLE))
          .send(sender);
      return;
    }

    String normalized = enabled == null ? "toggle" : enabled.trim().toLowerCase(Locale.ROOT);
    if (!isToggleValue(normalized)) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.UNKNOWN_TOGGLE_STATE, untrusted("state", enabled)))
          .send(sender);
      return;
    }

    PlayerRef target = targetPlayer;
    CommandTargetExecutor.run(target, () -> setDebugMode(target, normalized, sender), sender);
  }

  @Director(description = "Toggle verbose mode", descriptionKey = "command.help.toggle_verbose_mode")
  public void verbose() {
    if (!CommandContext.hasPermission("adapt.idontknowwhatimdoingiswear")) {
      FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.MISSING_PERMISSION,
          trusted("permission", "adapt.idontknowwhatimdoingiswear")
      )).send(CommandContext.source());
      return;
    }

    AdaptConfig.get().setVerbose(!AdaptConfig.get().isVerbose());
    FConst.success(AdaptLanguage.text(AdaptConfig.get().isVerbose()
        ? CommandRuntimeMessages.VERBOSE_ENABLED
        : CommandRuntimeMessages.VERBOSE_DISABLED)).send(CommandContext.source());
  }

  @Director(name = "pap", description = "Generate Perms for Adaptations!", descriptionKey = "command.help.generate_perms_for_adaptations")
  public void pap() {
    if (!CommandContext.hasPermission("adapt.idontknowwhatimdoingiswear")) {
      FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.MISSING_PERMISSION,
          trusted("permission", "adapt.idontknowwhatimdoingiswear")
      )).send(CommandContext.source());
      return;
    }

    StringBuilder builder = new StringBuilder();
    AdaptCore.get().getAdaptServer().getSkillRegistry().getSkills().forEach(skill -> skill.getAdaptations().forEach(adaptation -> builder
        .append("adapt.use.")
        .append(adaptation.getName()
            .replaceAll("-", ""))
        .append("\n")));
    AdaptLog.info("Permissions: \n" + builder);
    FConst.success(AdaptLanguage.text(CommandRuntimeMessages.PERMISSIONS_PRINTED)).send(CommandContext.source());
  }

  @Director(name = "psp", description = "Generate Perms for Skills!", descriptionKey = "command.help.generate_perms_for_skills")
  public void psp() {
    if (!CommandContext.hasPermission("adapt.idontknowwhatimdoingiswear")) {
      FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.MISSING_PERMISSION,
          trusted("permission", "adapt.idontknowwhatimdoingiswear")
      )).send(CommandContext.source());
      return;
    }

    StringBuilder builder = new StringBuilder();
    AdaptCore.get().getAdaptServer().getSkillRegistry().getSkills().forEach(skill -> builder
        .append("adapt.use.")
        .append(skill.getName()
            .replaceAll("-", ""))
        .append("\n"));
    AdaptLog.info("Permissions: \n" + builder);
    FConst.success(AdaptLanguage.text(CommandRuntimeMessages.PERMISSIONS_PRINTED)).send(CommandContext.source());
  }

  @Director(name = "particle", origin = DirectorOrigin.PLAYER, description = "Summon a particle at your location for testing!", descriptionKey = "command.help.summon_a_particle_at_your_location_for_testing")
  public void particle(@Param(description = "Particle type to spawn", descriptionKey = "command.help.particle_type_to_spawn") ParticleSpec particle) {
    if (!CommandContext.hasPermission("adapt.idontknowwhatimdoingiswear")) {
      FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.MISSING_PERMISSION,
          trusted("permission", "adapt.idontknowwhatimdoingiswear")
      )).send(CommandContext.source());
      return;
    }

    PlayerRef player = CommandContext.player();
    String dataType = AdaptCore.platform().host().registries().particleDataType(particle.type());
    if (dataType != null) {
      FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.PARTICLE_DATA_REQUIRED,
          untrusted("particle", new ParticleHandler().toString(particle)),
          untrusted("type", dataType)
      )).send(CommandContext.source());
      return;
    }
    AdaptCore.platform().fx().particle(player, particle, player.location().x(), player.location().y(), player.location().z(),
        10, 0.5D, 0.5D, 0.5D, 1D);
  }

  @Director(name = "sound", origin = DirectorOrigin.PLAYER, description = "Play a sound at your location for testing!", descriptionKey = "command.help.play_a_sound_at_your_location_for_testing")
  public void sound(@Param(description = "Sound to play", descriptionKey = "command.help.sound_to_play") Id sound) {
    if (!CommandContext.hasPermission("adapt.idontknowwhatimdoingiswear")) {
      FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.MISSING_PERMISSION,
          trusted("permission", "adapt.idontknowwhatimdoingiswear")
      )).send(CommandContext.source());
      return;
    }

    SoundPlayer sp = SoundPlayer.of(CommandContext.player());
    sp.play(CommandContext.player().location(), sound, 1, 1);
  }

  @Director(description = "Show Adapt ticker hotspots", descriptionKey = "command.help.show_adapt_ticker_hotspots")
  public void perf(
      @Param(description = "Top results to print", defaultValue = "12", descriptionKey = "command.help.top_results_to_print")
      int top,
      @Param(description = "Reset metrics after printing", defaultValue = "false", descriptionKey = "command.help.reset_metrics_after_printing")
      boolean reset
  ) {
    if (!CommandContext.hasPermission("adapt.idontknowwhatimdoingiswear")) {
      FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.MISSING_PERMISSION,
          trusted("permission", "adapt.idontknowwhatimdoingiswear")
      )).send(CommandContext.source());
      return;
    }

    long now = System.currentTimeMillis();
    long checksPerSecond = AbilityCheckTelemetry.checksPerSecond(now);
    long successfulPerSecond = AbilityCheckTelemetry.successfulChecksPerSecond(now);
    long checksPerMinute = AbilityCheckTelemetry.checksPerMinute(now);
    long successfulPerMinute = AbilityCheckTelemetry.successfulChecksPerMinute(now);
    long cacheHits = AbilityCheckTelemetry.cacheHitsPerMinute(now);
    long cacheMisses = AbilityCheckTelemetry.cacheMissesPerMinute(now);
    double cacheHitRatio = AbilityCheckTelemetry.cacheHitRatio(now) * 100D;
    double averageMicros = AbilityCheckTelemetry.averageCheckMicros(now);
    double timingMillisPerSecond = AbilityCheckTelemetry.estimatedTimingMillisPerSecond(now);
    double timingBudgetPercent = AbilityCheckTelemetry.timingBudgetPercent(now);

    FConst.info(AdaptLanguage.text(
        CommandRuntimeMessages.PERF_ABILITY_CHECKS,
        trusted("perSecond", checksPerSecond),
        trusted("perMinute", checksPerMinute)
    )).send(CommandContext.source());
    FConst.info(AdaptLanguage.text(
        CommandRuntimeMessages.PERF_SUCCESSFUL_CHECKS,
        trusted("perSecond", successfulPerSecond),
        trusted("perMinute", successfulPerMinute)
    )).send(CommandContext.source());
    FConst.info(AdaptLanguage.text(
        CommandRuntimeMessages.PERF_CACHE_HIT_RATIO,
        trusted("ratio", String.format(Locale.US, "%.1f%%", cacheHitRatio)),
        trusted("hits", cacheHits),
        trusted("misses", cacheMisses)
    )).send(CommandContext.source());
    FConst.info(AdaptLanguage.text(
        CommandRuntimeMessages.PERF_TIMING_BUDGET,
        trusted("percent", String.format(Locale.US, "%.2f%%", timingBudgetPercent)),
        trusted("millisPerSecond", String.format(Locale.US, "%.2fms/s", timingMillisPerSecond)),
        trusted("microsPerCheck", String.format(Locale.US, "%.1fus/check", averageMicros))
    )).send(CommandContext.source());

    List<String> lines = AdaptCore.get().getTicker().topMetrics(top);
    long windowMs = AdaptCore.get().getTicker().getMetricsWindowMs();
    FConst.success(AdaptLanguage.text(
        CommandRuntimeMessages.PERF_TICKER_WINDOW,
        trusted("window", windowMs)
    )).send(CommandContext.source());
    if (lines.isEmpty()) {
      FConst.success(AdaptLanguage.text(CommandRuntimeMessages.PERF_NO_METRICS))
          .send(CommandContext.source());
    } else {
      lines.forEach(line -> FConst.info(line).send(CommandContext.source()));
    }

    if (reset) {
      AdaptCore.get().getTicker().resetMetrics();
      AbilityCheckTelemetry.clear();
      FConst.success(AdaptLanguage.text(CommandRuntimeMessages.PERF_RESET))
          .send(CommandContext.source());
    }
  }

  private boolean isToggleValue(String normalized) {
    return switch (normalized) {
      case "toggle", "true", "on", "yes", "enabled", "false", "off", "no", "disabled" -> true;
      default -> false;
    };
  }

  private void setDebugMode(PlayerRef targetPlayer, String normalized, CommandSource sender) {
    UUID targetId = targetPlayer.uuid();
    boolean enabled = switch (normalized) {
      case "toggle" -> !AdaptDebugMode.isActive(targetId);
      case "true", "on", "yes", "enabled" -> true;
      default -> false;
    };
    AdaptDebugMode.setActive(targetId, enabled);
    CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(
        enabled ? CommandRuntimeMessages.DEBUG_ENABLED : CommandRuntimeMessages.DEBUG_DISABLED,
        untrusted("player", targetPlayer.name())
    )));
  }
}
