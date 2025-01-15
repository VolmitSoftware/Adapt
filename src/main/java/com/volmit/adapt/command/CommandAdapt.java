package com.volmit.adapt.command;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.skill.SkillRegistry;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.AdaptServer;
import com.volmit.adapt.content.gui.SkillsGui;
import com.volmit.adapt.content.item.KnowledgeOrb;
import com.volmit.adapt.util.command.FConst;
import com.volmit.adapt.util.decree.DecreeExecutor;
import com.volmit.adapt.util.decree.annotations.Decree;
import com.volmit.adapt.util.decree.annotations.Param;
import com.volmit.adapt.util.decree.context.AdaptationListingHandler;
import com.volmit.adapt.util.decree.specialhandlers.NullablePlayerHandler;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Decree(name = "adapt", description = "Basic Command")
public class CommandAdapt implements DecreeExecutor {
    @Decree(description = "Boost Target player, or Global Experience gain.")
    public void boost(
            @Param(aliases = "seconds", description = "Amount of seconds", defaultValue = "10")
            int seconds,
            @Param(aliases = "multiplier", description = "Strength of the boost ", defaultValue = "10")
            int multiplier,
            @Param(description = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
            Player player

    ) {
        if (!sender().hasPermission("adapt.boost")) {
            FConst.error("You lack the Permission 'adapt.boost'").send(sender());
            return;
        }

        AdaptServer adaptServer = Adapt.instance.getAdaptServer();
        if (player() == null) {
            adaptServer.boostXP(multiplier, seconds * 1000);
        } else {
            AdaptPlayer adaptPlayer = adaptServer.getPlayer(player);
            adaptPlayer.boostXPToRecents(multiplier, seconds * 1000);
        }
        FConst.success("Boosted XP by " + multiplier + " for " + seconds + " seconds").send(sender());
    }

    @Decree(description = "Open the Adapt GUI")
    public void gui(
            @Param(aliases = "target", defaultValue = "[Main]")
            AdaptationListingHandler.AdaptationList guiTarget,
            @Param(aliases = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
            Player player,
            @Param(aliases = "force", defaultValue = "false")
            boolean force
    ) {
        if (!sender().hasPermission("adapt.gui")) {
            FConst.error("You lack the Permission 'adapt.gui'").send(sender());
            return;
        }

        Player targetPlayer = player;
        if (targetPlayer == null && sender().isConsole()) {
            FConst.error("You must specify a player when using this command from console.").send(sender());
        } else if (targetPlayer == null) {
            targetPlayer = player();
        }

        if (guiTarget.equals("[Main]")) {
            SkillsGui.open(targetPlayer);
            return;
        }

        if (guiTarget.startsWith("[Skill]-")) {
            for (Skill<?> skill : SkillRegistry.skills.sortV()) {
                if (guiTarget.equals("[Skill]-" + skill.getName())) {
                    openGuiWithForceCheck(force, targetPlayer, skill.openGui(targetPlayer), skill.getName());
                    return;
                }
            }
        }

        if (guiTarget.startsWith("[Adaptation]-")) {
            for (Skill<?> skill : SkillRegistry.skills.sortV()) {
                for (Adaptation<?> adaptation : skill.getAdaptations()) {
                    if (guiTarget.equals("[Adaptation]-" + adaptation.getName())) {
                        openGuiWithForceCheck(force, targetPlayer, adaptation.openGui(targetPlayer, true), adaptation.getName());
                    }
                }
            }
        }
    }

    private void openGuiWithForceCheck(@Param(aliases = "force", defaultValue = "false") boolean force, Player targetPlayer, boolean b, String name) {
        if (force || b) {
            FConst.success("Opened GUI for " + name + " for " + Objects.requireNonNull(targetPlayer).getName()).send(sender());
        } else {
            FConst.error("Failed to open GUI for " + name + " for " + Objects.requireNonNull(targetPlayer).getName() + " - No Permission, remove from blacklist!").send(sender());
        }
    }

    @Decree(description = "Give yourself a knowledge orb")
    public void knowledge(
            @Param(aliases = "skill")
            AdaptationListingHandler.AdaptationSkillList skillName,
            @Param(aliases = "amount", defaultValue = "10")
            int amount,
            @Param(aliases = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
            Player player
    ) {
        if (!sender().hasPermission("adapt.cheatitem")) {
            FConst.error("You lack the Permission 'adapt.cheatitem'").send(sender());
            return;
        }
        Player targetPlayer = player;

        if (targetPlayer == null) {
            if (sender().isPlayer()) {
                targetPlayer = player();
            } else {
                FConst.error("You must be a player to use this command").send(sender());
                return;
            }
        }

        if (skillName.equals("[all]")) {
            Map<String, Integer> knowledgeMap = new HashMap<>();
            for (Skill<?> skill : SkillRegistry.skills.sortV()) {
                knowledgeMap.put(skill.getName(), amount);
            }
            targetPlayer.getInventory().addItem(KnowledgeOrb.with(knowledgeMap));
            FConst.success("Giving all orbs").send(sender());
            return;
        }

        if (skillName.equals("[random]")) {
            targetPlayer.getInventory().addItem(KnowledgeOrb.with(SkillRegistry.skills.sortV().getRandom().getName(), amount));
            FConst.success("Giving random orb").send(sender());
            return;
        }

        Skill<?> skill = SkillRegistry.skills.get(skillName.toString());
        if (skill != null) {
            targetPlayer.getInventory().addItem(KnowledgeOrb.with(skill.getName(), amount));
            FConst.success("Giving " + skill.getName() + " orb").send(sender());
        }
    }
}
