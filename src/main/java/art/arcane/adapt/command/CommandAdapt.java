package art.arcane.adapt.command;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.content.gui.ConfigGui;
import art.arcane.adapt.content.gui.SkillsGui;
import art.arcane.adapt.content.item.ExperienceOrb;
import art.arcane.adapt.content.item.KnowledgeOrb;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.config.ConfigMigrationManager;
import art.arcane.adapt.util.decree.DecreeExecutor;
import art.arcane.volmlib.util.decree.DecreeOrigin;
import art.arcane.volmlib.util.decree.annotations.Decree;
import art.arcane.volmlib.util.decree.annotations.Param;
import art.arcane.adapt.util.decree.context.AdaptationListingHandler;
import art.arcane.adapt.util.decree.specialhandlers.NullablePlayerHandler;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

import art.arcane.adapt.util.common.plugin.Permission;
import art.arcane.adapt.util.project.command.Command;

@Decree(name = "adapt", description = "Basic Command")
public class CommandAdapt implements DecreeExecutor {
    private CommandDebug debug;
    private CommandClear clear;
    private CommandReset reset;
    private CommandDefault defaults;

    @Decree(description = "Boost Target player Experience gain.")
    public void boost(
        @Param(aliases = "seconds", description = "Amount of seconds", defaultValue = "10")
        int seconds,
        @Param(aliases = "multiplier", description = "Strength of the boost ", defaultValue = "10")
        double multiplier,
        @Param(description = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
        Player player
    ) {
        if (!sender().hasPermission("adapt.boost")) {
            FConst.error("You lack the Permission 'adapt.boost'").send(sender());
            return;
        }

        Player targetPlayer = player;
        if (targetPlayer == null && sender().isConsole()) {
            FConst.error("You must specify a player when using this command from console.").send(sender());
            return;
        } else if (targetPlayer == null) {
            targetPlayer = player();
        }

        AdaptServer adaptServer = Adapt.instance.getAdaptServer();
        PlayerData playerData = adaptServer.getPlayer(targetPlayer).getData();
        playerData.globalXPMultiplier(multiplier, seconds * 1000);

        FConst.success("Boosted XP by " + multiplier + " for " + seconds + " seconds").send(sender());
    }

    @Decree(description = "Boost Global Experience gain.", name = "global-boost")
    public void globalBoost(
            @Param(aliases = "seconds", description = "Amount of seconds", defaultValue = "10")
            int seconds,
            @Param(aliases = "multiplier", description = "Strength of the boost ", defaultValue = "10")
            double multiplier
    ) {
        if (!sender().hasPermission("adapt.boost.global")) {
            FConst.error("You lack the Permission 'adapt.boost.global'").send(sender());
            return;
        }

        AdaptServer adaptServer = Adapt.instance.getAdaptServer();
        adaptServer.boostXP(multiplier, seconds * 1000);

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
            return;
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
                    if (!adaptation.isEnabled()) {
                        continue;
                    }
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

    @Decree(name = "configure", aliases = {"config", "cfg"}, origin = DecreeOrigin.PLAYER, description = "Open the in-game Adapt config editor")
    public void configure() {
        if (!ConfigGui.canConfigure(player())) {
            FConst.error("You need operator status or the permission 'adapt.configurator'").send(sender());
            return;
        }

        ConfigGui.open(player());
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

        Skill<?> skill = Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(skillName.name());
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

        Skill<?> skill = Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(skillName.name());
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

    @Decree(name = "migrate-configs", description = "Force migrate and rewrite all skill/adaptation configs to canonical TOML with comments.")
    public void migrateConfigs() {
        if (!sender().hasPermission("adapt.debug")) {
            FConst.error("You lack the Permission 'adapt.debug'").send(sender());
            return;
        }

        if (Adapt.instance.getAdaptServer() == null || Adapt.instance.getAdaptServer().getSkillRegistry() == null) {
            FConst.error("Adapt server is not ready yet. Try again in a few seconds.").send(sender());
            return;
        }

        int migratedSkills = 0;
        int migratedAdaptations = 0;
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getSkills()) {
            if (skill instanceof SimpleSkill<?> simpleSkill) {
                if (simpleSkill.reloadConfigFromDisk(false)) {
                    migratedSkills++;
                }
            }

            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (adaptation instanceof SimpleAdaptation<?> simpleAdaptation) {
                    if (simpleAdaptation.reloadConfigFromDisk(false)) {
                        migratedAdaptations++;
                    }
                }
            }
        }

        int deletedLegacyJson = ConfigMigrationManager.deleteMigratedLegacyJsonFiles();
        FConst.success("Canonicalized TOML configs. skills=" + migratedSkills + ", adaptations=" + migratedAdaptations + ", deletedLegacyJson=" + deletedLegacyJson).send(sender());
    }

}
