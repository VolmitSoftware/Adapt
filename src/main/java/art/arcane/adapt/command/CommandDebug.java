package art.arcane.adapt.command;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.telemetry.AbilityCheckTelemetry;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.compat.BukkitDirectorContext;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

@Director(name = "debug", origin = DirectorOrigin.BOTH, description = "Adapt Debug Command", aliases = {"dev"})
public class CommandDebug {

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

  @Director(name = "particle", origin = DirectorOrigin.PLAYER, description = "Summon a particle in front of you for testing!")
  public void particle(@Param Particle particle) {
    if (!BukkitDirectorContext.hasPermission("adapt.idontknowwhatimdoingiswear")) {
      FConst.error("You lack the Permission 'adapt.idontknowwhatimdoingiswear'").send(BukkitDirectorContext.sender());
      return;
    }

    Player player = BukkitDirectorContext.player();
    player.spawnParticle(particle, player.getLocation(), 10, 10);
  }

  @Director(name = "particle", origin = DirectorOrigin.PLAYER, description = "Summon a particle in front of you for testing!")
  public void particle(@Param Sound sound) {
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
