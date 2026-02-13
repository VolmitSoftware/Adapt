package com.volmit.adapt.command;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.command.FConst;
import com.volmit.adapt.util.decree.DecreeExecutor;
import com.volmit.adapt.util.decree.DecreeOrigin;
import com.volmit.adapt.util.decree.annotations.Decree;
import com.volmit.adapt.util.decree.annotations.Param;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;

@Decree(name = "debug", origin = DecreeOrigin.BOTH, description = "Adapt Debug Command", aliases = {"dev"})
public class CommandDebug implements DecreeExecutor {

    @Decree(description = "Toggle verbose mode")
    public void verbose() {
        if (!sender().hasPermission("adapt.idontknowwhatimdoingiswear")) {
            FConst.error("You lack the Permission 'adapt.idontknowwhatimdoingiswear'").send(sender());
            return;
        }

        AdaptConfig.get().setVerbose(!AdaptConfig.get().isVerbose());
        FConst.success("Verbose is now " + (AdaptConfig.get().isVerbose() ? "enabled" : "disabled")).send(sender());
    }

    @Decree(name = "pap", description = "Generate Perms for Adaptations!")
    public void pap() {
        if (!sender().hasPermission("adapt.idontknowwhatimdoingiswear")) {
            FConst.error("You lack the Permission 'adapt.idontknowwhatimdoingiswear'").send(sender());
            return;
        }

        StringBuilder builder = new StringBuilder();
        Adapt.instance.getAdaptServer().getSkillRegistry().getSkills().forEach(skill -> skill.getAdaptations().forEach(adaptation -> builder
                .append("adapt.blacklist.")
                .append(adaptation.getName()
                        .replaceAll("-", ""))
                .append("\n")));
        Adapt.info("Permissions: \n" + builder);
        FConst.success("Permissions have been printed to console.").send(sender());
    }

    @Decree(name = "psp", description = "Generate Perms for Skills!")
    public void psp() {
        if (!sender().hasPermission("adapt.idontknowwhatimdoingiswear")) {
            FConst.error("You lack the Permission 'adapt.idontknowwhatimdoingiswear'").send(sender());
            return;
        }

        StringBuilder builder = new StringBuilder();
        Adapt.instance.getAdaptServer().getSkillRegistry().getSkills().forEach(skill -> builder
                .append("adapt.blacklist.")
                .append(skill.getName()
                        .replaceAll("-", ""))
                .append("\n"));
        Adapt.info("Permissions: \n" + builder);
        FConst.success("Permissions have been printed to console.").send(sender());
    }

    @Decree(name = "particle", origin = DecreeOrigin.PLAYER, description = "Summon a particle in front of you for testing!")
    public void particle(@Param Particle particle) {
        if (!sender().hasPermission("adapt.idontknowwhatimdoingiswear")) {
            FConst.error("You lack the Permission 'adapt.idontknowwhatimdoingiswear'").send(sender());
            return;
        }

        Player player = player();
        player.spawnParticle(particle, player.getLocation(), 10, 10);
    }

    @Decree(name = "particle", origin = DecreeOrigin.PLAYER, description = "Summon a particle in front of you for testing!")
    public void particle(@Param Sound sound) {
        if (!sender().hasPermission("adapt.idontknowwhatimdoingiswear")) {
            FConst.error("You lack the Permission 'adapt.idontknowwhatimdoingiswear'").send(sender());
            return;
        }

        SoundPlayer sp = SoundPlayer.of(player());
        sp.play(player().getLocation(), sound, 1, 1);
    }

    @Decree(description = "Show Adapt ticker hotspots")
    public void perf(
            @Param(description = "Top results to print", defaultValue = "12")
            int top,
            @Param(description = "Reset metrics after printing", defaultValue = "false")
            boolean reset
    ) {
        if (!sender().hasPermission("adapt.idontknowwhatimdoingiswear")) {
            FConst.error("You lack the Permission 'adapt.idontknowwhatimdoingiswear'").send(sender());
            return;
        }

        List<String> lines = Adapt.instance.getTicker().topMetrics(top);
        long windowMs = Adapt.instance.getTicker().getMetricsWindowMs();
        FConst.success("Ticker window: " + windowMs + "ms").send(sender());
        if (lines.isEmpty()) {
            FConst.success("No tick metrics collected yet.").send(sender());
        } else {
            lines.forEach(line -> FConst.info(line).send(sender()));
        }

        if (reset) {
            Adapt.instance.getTicker().resetMetrics();
            FConst.success("Ticker metrics reset.").send(sender());
        }
    }
}
