package com.volmit.adapt.command;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.skill.SkillRegistry;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.AdaptServer;
import com.volmit.adapt.content.gui.SkillsGui;
import com.volmit.adapt.content.item.ExperienceOrb;
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

@Decree(name = "adapt", description = "Basic Command")
public class CommandAdapt implements DecreeExecutor {
    private CommandDebug debug;

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
        if (player == null) {
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
                    if (force || skill.openGui(targetPlayer, true)) {
                        FConst.success("Opened GUI for " + skill.getName() + " for " + targetPlayer.getName()).send(sender());
                    } else {
                        FConst.error("Failed to open GUI for " + skill.getName() + " for " + targetPlayer.getName() + " - No Permission, remove from blacklist!").send(sender());
                    }
                    return;
                }
            }
        }

        if (guiTarget.startsWith("[Adaptation]-")) {
            for (Skill<?> skill : SkillRegistry.skills.sortV()) {
                for (Adaptation<?> adaptation : skill.getAdaptations()) {
                    if (guiTarget.equals("[Adaptation]-" + adaptation.getName())) {
                        if (force || adaptation.openGui(targetPlayer, true)) {
                            FConst.success("Opened GUI for " + adaptation.getName() + " for " + targetPlayer.getName()).send(sender());
                        } else {
                            FConst.error("Failed to open GUI for " + adaptation.getName() + " for " + targetPlayer.getName() + " - No Permission, remove from blacklist!").send(sender());
                        }
                        return;
                    }
                }
            }
        }
    }

    @Decree(description = "Give yourself an experience orb")
    public void experience(
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
                FConst.error("You must be a player to use this command, or Reference a player").send(sender());
                return;
            }
        }

        if (skillName.equals("[all]")) {
            Map<String, Double> experienceMap = new HashMap<>();
            for (Skill<?> skill : SkillRegistry.skills.sortV()) {
                experienceMap.put(skill.getName(), (double) amount);
            }
            targetPlayer.getInventory().addItem(ExperienceOrb.with(experienceMap));
            FConst.success("Giving all orbs").send(sender());
            return;
        }

        if (skillName.equals("[random]")) {
            targetPlayer.getInventory().addItem(ExperienceOrb.with(SkillRegistry.skills.sortV().getRandom().getName(), amount));
            FConst.success("Giving random orb").send(sender());
            return;
        }

        Skill<?> skill = SkillRegistry.skills.get(skillName.name());
        if (skill != null) {
            targetPlayer.getInventory().addItem(ExperienceOrb.with(skill.getName(), amount));
            FConst.success("Giving " + skill.getName() + " orb").send(sender());
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

        if(targetPlayer == null){
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

        if (skillName.equals("[random]")){
            targetPlayer.getInventory().addItem(KnowledgeOrb.with(SkillRegistry.skills.sortV().getRandom().getName(), amount));
            FConst.success("Giving random orb").send(sender());
            return;
        }

        Skill<?> skill = SkillRegistry.skills.get(skillName.toString());
        if(skill != null){
            targetPlayer.getInventory().addItem(KnowledgeOrb.with(skill.getName(), amount));
            FConst.success("Giving " + skill.getName() + " orb").send(sender());
        }
    }

    @Decree(description = "Assign a skill, or UnAssign a skill as if you are learning / unlearning a skill.")
    public void determine(
        @Param(aliases = "adaptationTarget")
        AdaptationListingHandler.AdaptationProvider adaptationTarget,
        @Param(aliases = "assign")
        boolean assign,
        @Param(aliases = "force")
        boolean force,
        @Param(aliases = "level")
        int level,
        @Param(aliases = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
        Player player

    ) {
        if (!sender().hasPermission("adapt.determine")) {
            FConst.error("You lack the Permission 'adapt.determine'").send(sender());
            return;
        }

        Player targetPlayer = player;
        if (targetPlayer == null && sender().isConsole()) {
            FConst.error("You must specify a player when using this command from console.").send(sender());
        } else if (targetPlayer == null) {
            targetPlayer = player();
        }

        //the format is skillname:adaptationname
        String[] split = adaptationTarget.name().split(":");
        String skillname = split[0];
        String adaptationname = split[1];

        for (Skill<?> skill : SkillRegistry.skills.sortV()) {
            if (skill.getName().equalsIgnoreCase(skillname)) {
                for (Adaptation<?> adaptation : skill.getAdaptations()) {
                    if (adaptation.getName().equalsIgnoreCase(adaptationname)) {
                        if (targetPlayer != null) {
                            if (assign) {
                                adaptation.learn(player, level, force);
                            } else {
                                adaptation.unlearn(player, level, force);
                            }
                        } else {
                            FConst.error("You must specify a player when using this command from console.").send(sender());
                        }
                        return;
                    }
                }
                return;
            }
        }
    }

}
