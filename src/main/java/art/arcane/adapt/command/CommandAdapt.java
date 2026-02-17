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
import art.arcane.adapt.util.config.ConfigMigrationManager;
import art.arcane.volmlib.util.director.compat.BukkitDirectorContext;
import art.arcane.adapt.util.director.context.AdaptationListingHandler;
import art.arcane.adapt.util.director.specialhandlers.NullablePlayerHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Director(name = "adapt", description = "Basic Command")
public class CommandAdapt {
    private CommandDebug debug;
    private CommandClear clear;
    private CommandReset reset;
    private CommandDefault defaults;

    @Director(description = "Boost Target player Experience gain.")
    public void boost(
        @Param(aliases = "seconds", description = "Amount of seconds", defaultValue = "10")
        int seconds,
        @Param(aliases = "multiplier", description = "Strength of the boost ", defaultValue = "10")
        double multiplier,
        @Param(description = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
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
            @Param(aliases = "multiplier", description = "Strength of the boost ", defaultValue = "10")
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
        @Param(aliases = "target", defaultValue = "[Main]")
        AdaptationListingHandler.AdaptationList guiTarget,
        @Param(aliases = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
        Player player,
        @Param(aliases = "force", defaultValue = "false")
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

        if (guiTarget.equals("[Main]")) {
            SkillsGui.open(targetPlayer);
            return;
        }

        if (guiTarget.startsWith("[Skill]-")) {
            for (Skill<?> skill : SkillRegistry.skills.sortV()) {
                if (guiTarget.equals("[Skill]-" + skill.getName())) {
                    if (force || skill.openGui(targetPlayer, true)) {
                        FConst.success("Opened GUI for " + skill.getName() + " for " + targetPlayer.getName()).send(BukkitDirectorContext.sender());
                    } else {
                        FConst.error("Failed to open GUI for " + skill.getName() + " for " + targetPlayer.getName() + " - No Permission, remove from blacklist!").send(BukkitDirectorContext.sender());
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
                            FConst.success("Opened GUI for " + adaptation.getName() + " for " + targetPlayer.getName()).send(BukkitDirectorContext.sender());
                        } else {
                            FConst.error("Failed to open GUI for " + adaptation.getName() + " for " + targetPlayer.getName() + " - No Permission, remove from blacklist!").send(BukkitDirectorContext.sender());
                        }
                        return;
                    }
                }
            }
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

    @Director(description = "Give yourself an experience orb")
    public void experience(
        @Param(aliases = "skill")
        AdaptationListingHandler.AdaptationSkillList skillName,
        @Param(aliases = "amount", defaultValue = "10")
        int amount,
        @Param(aliases = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
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

        if (skillName.equals("[all]")) {
            Map<String, Double> experienceMap = new HashMap<>();
            for (Skill<?> skill : allSkillSnapshot()) {
                experienceMap.put(skill.getName(), (double) amount);
            }
            targetPlayer.getInventory().addItem(ExperienceOrb.with(experienceMap));
            FConst.success("Giving all orbs").send(BukkitDirectorContext.sender());
            return;
        }

        if (skillName.equals("[random]")) {
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
        }
    }

    @Director(description = "Give yourself a knowledge orb")
    public void knowledge(
        @Param(aliases = "skill")
        AdaptationListingHandler.AdaptationSkillList skillName,
        @Param(aliases = "amount", defaultValue = "10")
        int amount,
        @Param(aliases = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
        Player player
    ) {
        if (!BukkitDirectorContext.hasPermission("adapt.cheatitem")) {
            FConst.error("You lack the Permission 'adapt.cheatitem'").send(BukkitDirectorContext.sender());
            return;
        }
        Player targetPlayer = player;

        if(targetPlayer == null){
            if (BukkitDirectorContext.isPlayer()) {
                targetPlayer = BukkitDirectorContext.player();
            } else {
                FConst.error("You must be a player to use this command").send(BukkitDirectorContext.sender());
                return;
            }
        }

        if (skillName.equals("[all]")) {
            Map<String, Integer> knowledgeMap = new HashMap<>();
            for (Skill<?> skill : allSkillSnapshot()) {
                knowledgeMap.put(skill.getName(), amount);
            }
            targetPlayer.getInventory().addItem(KnowledgeOrb.with(knowledgeMap));
            FConst.success("Giving all orbs").send(BukkitDirectorContext.sender());
            return;
        }

        if (skillName.equals("[random]")){
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
        if(skill != null){
            targetPlayer.getInventory().addItem(KnowledgeOrb.with(skill.getName(), amount));
            FConst.success("Giving " + skill.getName() + " orb").send(BukkitDirectorContext.sender());
        }
    }

    @Director(description = "Assign a skill, or UnAssign a skill as if you are learning / unlearning a skill.")
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
                        if (targetPlayer != null) {
                            if (assign) {
                                adaptation.learn(targetPlayer, level, force);
                            } else {
                                adaptation.unlearn(targetPlayer, level, force);
                            }
                        } else {
                            FConst.error("You must specify a player when using this command from console.").send(BukkitDirectorContext.sender());
                        }
                        return;
                    }
                }
                return;
            }
        }
    }

    @Director(name = "claim-skill", description = "Set a player's skill line level between 0 and 100 for custom UI integration.")
    public void claimSkill(
            @Param(aliases = "skill")
            AdaptationListingHandler.SkillProvider skillTarget,
            @Param(aliases = "level")
            int level,
            @Param(aliases = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
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
            @Param(aliases = "adaptationTarget")
            AdaptationListingHandler.AdaptationProvider adaptationTarget,
            @Param(aliases = "level")
            int level,
            @Param(aliases = "force", defaultValue = "false")
            boolean force,
            @Param(aliases = "player", defaultValue = "---", customHandler = NullablePlayerHandler.class)
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
