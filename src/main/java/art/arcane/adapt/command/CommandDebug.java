package art.arcane.adapt.command;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.telemetry.AbilityCheckTelemetry;
import art.arcane.adapt.api.world.AdaptDebugMode;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.director.specialhandlers.NullablePlayerHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.compat.BukkitDirectorContext;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Director(name = "debug", origin = DirectorOrigin.BOTH, description = "Adapt Debug Command", aliases = {"dev"})
public class CommandDebug {

  @Director(name = "mode", description = "Toggle debug mode: reveals every skill and adaptation and makes learning free and uncapped")
  public void mode(
      @Param(aliases = "enabled", description = "Explicit on/off state, omit to toggle", defaultValue = "toggle")
      String enabled,
      @Param(aliases = "player", description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class)
      Player player
  ) {
    if (!BukkitDirectorContext.hasPermission("adapt.debug")) {
      FConst.error("You lack the Permission 'adapt.debug'").send(BukkitDirectorContext.sender());
      return;
    }

    Player targetPlayer = player;
    if (targetPlayer == null && BukkitDirectorContext.isConsole()) {
      FConst.error("You must specify a player when using this command from console.").send(BukkitDirectorContext.sender());
      return;
    } else if (targetPlayer == null) {
      targetPlayer = BukkitDirectorContext.player();
    }

    String normalized = enabled == null ? "toggle" : enabled.trim().toLowerCase(Locale.ROOT);
    UUID targetId = targetPlayer.getUniqueId();
    Boolean target = switch (normalized) {
      case "toggle" -> !AdaptDebugMode.isActive(targetId);
      case "true", "on", "yes", "enabled" -> Boolean.TRUE;
      case "false", "off", "no", "disabled" -> Boolean.FALSE;
      default -> null;
    };

    if (target == null) {
      FConst.error("Unknown state '" + enabled + "'. Use on, off, or omit to toggle.").send(BukkitDirectorContext.sender());
      return;
    }

    AdaptDebugMode.setActive(targetId, target);
    if (target) {
      FConst.success("Debug mode enabled for " + targetPlayer.getName() + ": all skills and adaptations are visible and learning is free and uncapped. The toggle resets on logout; adaptations learned while it is on persist.").send(BukkitDirectorContext.sender());
    } else {
      FConst.success("Debug mode disabled for " + targetPlayer.getName() + ". Adaptations learned in debug mode persist; use /adapt clear adaptations or /adapt determine to remove them.").send(BukkitDirectorContext.sender());
    }
  }

  @Director(description = "Toggle verbose mode")
  public void verbose() {
    if (!BukkitDirectorContext.hasPermission("adapt.idontknowwhatimdoingiswear")) {
      FConst.error("You lack the Permission 'adapt.idontknowwhatimdoingiswear'").send(BukkitDirectorContext.sender());
      return;
    }

    AdaptConfig.get().setVerbose(!AdaptConfig.get().isVerbose());
    FConst.success("Verbose is now " + (AdaptConfig.get().isVerbose() ? "enabled" : "disabled")).send(BukkitDirectorContext.sender());
  }

  @Director(name = "pap", description = "Generate Perms for Adaptations!")
  public void pap() {
    if (!BukkitDirectorContext.hasPermission("adapt.idontknowwhatimdoingiswear")) {
      FConst.error("You lack the Permission 'adapt.idontknowwhatimdoingiswear'").send(BukkitDirectorContext.sender());
      return;
    }

    StringBuilder builder = new StringBuilder();
    Adapt.instance.getAdaptServer().getSkillRegistry().getSkills().forEach(skill -> skill.getAdaptations().forEach(adaptation -> builder
        .append("adapt.use.")
        .append(adaptation.getName()
            .replaceAll("-", ""))
        .append("\n")));
    Adapt.info("Permissions: \n" + builder);
    FConst.success("Permissions have been printed to console.").send(BukkitDirectorContext.sender());
  }

  @Director(name = "psp", description = "Generate Perms for Skills!")
  public void psp() {
    if (!BukkitDirectorContext.hasPermission("adapt.idontknowwhatimdoingiswear")) {
      FConst.error("You lack the Permission 'adapt.idontknowwhatimdoingiswear'").send(BukkitDirectorContext.sender());
      return;
    }

    StringBuilder builder = new StringBuilder();
    Adapt.instance.getAdaptServer().getSkillRegistry().getSkills().forEach(skill -> builder
        .append("adapt.use.")
        .append(skill.getName()
            .replaceAll("-", ""))
        .append("\n"));
    Adapt.info("Permissions: \n" + builder);
    FConst.success("Permissions have been printed to console.").send(BukkitDirectorContext.sender());
  }

  @Director(name = "particle", origin = DirectorOrigin.PLAYER, description = "Summon a particle at your location for testing!")
  public void particle(@Param(description = "Particle type to spawn") Particle particle) {
    if (!BukkitDirectorContext.hasPermission("adapt.idontknowwhatimdoingiswear")) {
      FConst.error("You lack the Permission 'adapt.idontknowwhatimdoingiswear'").send(BukkitDirectorContext.sender());
      return;
    }

    Player player = BukkitDirectorContext.player();
    if (particle.getDataType() != Void.class) {
      FConst.error("Particle " + particle.name() + " requires data of type " + particle.getDataType().getSimpleName() + " and cannot be spawned by this command.").send(BukkitDirectorContext.sender());
      return;
    }
    player.spawnParticle(particle, player.getLocation(), 10, 0.5D, 0.5D, 0.5D);
  }

  @Director(name = "sound", origin = DirectorOrigin.PLAYER, description = "Play a sound at your location for testing!")
  public void sound(@Param(description = "Sound to play") Sound sound) {
    if (!BukkitDirectorContext.hasPermission("adapt.idontknowwhatimdoingiswear")) {
      FConst.error("You lack the Permission 'adapt.idontknowwhatimdoingiswear'").send(BukkitDirectorContext.sender());
      return;
    }

    SoundPlayer sp = SoundPlayer.of(BukkitDirectorContext.player());
    sp.play(BukkitDirectorContext.player().getLocation(), sound, 1, 1);
  }

  @Director(description = "Show Adapt ticker hotspots")
  public void perf(
      @Param(description = "Top results to print", defaultValue = "12")
      int top,
      @Param(description = "Reset metrics after printing", defaultValue = "false")
      boolean reset
  ) {
    if (!BukkitDirectorContext.hasPermission("adapt.idontknowwhatimdoingiswear")) {
      FConst.error("You lack the Permission 'adapt.idontknowwhatimdoingiswear'").send(BukkitDirectorContext.sender());
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

    FConst.info("Ability checks: " + checksPerSecond + "/s (" + checksPerMinute + "/m)").send(BukkitDirectorContext.sender());
    FConst.info("Successful checks: " + successfulPerSecond + "/s (" + successfulPerMinute + "/m)").send(BukkitDirectorContext.sender());
    FConst.info("Active-level cache hit ratio: "
        + String.format(Locale.US, "%.1f%%", cacheHitRatio)
        + " (" + cacheHits + " hit, " + cacheMisses + " miss)")
        .send(BukkitDirectorContext.sender());
    FConst.info("Ability check timing budget: "
        + String.format(Locale.US, "%.2f%%", timingBudgetPercent)
        + " (" + String.format(Locale.US, "%.2fms/s", timingMillisPerSecond)
        + ", " + String.format(Locale.US, "%.1fus/check", averageMicros) + ")")
        .send(BukkitDirectorContext.sender());

    List<String> lines = Adapt.instance.getTicker().topMetrics(top);
    long windowMs = Adapt.instance.getTicker().getMetricsWindowMs();
    FConst.success("Ticker window: " + windowMs + "ms").send(BukkitDirectorContext.sender());
    if (lines.isEmpty()) {
      FConst.success("No tick metrics collected yet.").send(BukkitDirectorContext.sender());
    } else {
      lines.forEach(line -> FConst.info(line).send(BukkitDirectorContext.sender()));
    }

    if (reset) {
      Adapt.instance.getTicker().resetMetrics();
      AbilityCheckTelemetry.clear();
      FConst.success("Ticker and ability telemetry reset.").send(BukkitDirectorContext.sender());
    }
  }
}
