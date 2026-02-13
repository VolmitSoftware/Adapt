package com.volmit.adapt.command;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.util.command.FConst;
import com.volmit.adapt.util.decree.DecreeExecutor;
import com.volmit.adapt.util.decree.DecreeOrigin;
import com.volmit.adapt.util.decree.annotations.Decree;
import com.volmit.adapt.util.decree.annotations.Param;
import com.volmit.adapt.util.decree.context.AdaptationListingHandler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

@Decree(name = "default", origin = DecreeOrigin.BOTH, description = "Reset configs to defaults")
public class CommandDefault implements DecreeExecutor {

    @Decree(description = "Reset a skill config to defaults")
    public void skill(
        @Param(description = "skill to reset")
        AdaptationListingHandler.SkillProvider skillTarget
    ) {
        if (!sender().isOp()) {
            FConst.error("This command can only be run by server operators.").send(sender());
            return;
        }

        Skill<?> skill = Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(skillTarget.name());
        if (skill == null) {
            FConst.error("Unknown skill: " + skillTarget.name()).send(sender());
            return;
        }

        if (!(skill instanceof SimpleSkill<?> simpleSkill)) {
            FConst.error("Skill " + skill.getName() + " does not support config reset.").send(sender());
            return;
        }

        File configFile = Adapt.instance.getDataFile("adapt", "skills", skill.getName() + ".toml");
        if (configFile.exists() && !configFile.delete()) {
            FConst.error("Failed to delete config file for " + skill.getName()).send(sender());
            return;
        }

        simpleSkill.reloadConfigFromDisk(false);
        FConst.success("Reset config for skill " + skill.getName() + " to defaults.").send(sender());
    }

    @Decree(description = "Reset an adaptation config to defaults")
    public void adaptation(
        @Param(description = "adaptation to reset (skill:adaptation)")
        AdaptationListingHandler.AdaptationProvider adaptationTarget
    ) {
        if (!sender().isOp()) {
            FConst.error("This command can only be run by server operators.").send(sender());
            return;
        }

        String[] split = adaptationTarget.name().split(":");
        if (split.length != 2) {
            FConst.error("Invalid format. Use skill:adaptation").send(sender());
            return;
        }

        Skill<?> skill = Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(split[0]);
        if (skill == null) {
            FConst.error("Unknown skill: " + split[0]).send(sender());
            return;
        }

        Adaptation<?> adaptation = null;
        for (Adaptation<?> a : skill.getAdaptations()) {
            if (a.getName().equalsIgnoreCase(split[1])) {
                adaptation = a;
                break;
            }
        }

        if (adaptation == null) {
            FConst.error("Unknown adaptation: " + split[1] + " in skill " + skill.getName()).send(sender());
            return;
        }

        if (!(adaptation instanceof SimpleAdaptation<?> simpleAdaptation)) {
            FConst.error("Adaptation " + adaptation.getName() + " does not support config reset.").send(sender());
            return;
        }

        File configFile = Adapt.instance.getDataFile("adapt", "adaptations", adaptation.getName() + ".toml");
        if (configFile.exists() && !configFile.delete()) {
            FConst.error("Failed to delete config file for " + adaptation.getName()).send(sender());
            return;
        }

        simpleAdaptation.reloadConfigFromDisk(false);
        FConst.success("Reset config for adaptation " + adaptation.getName() + " to defaults.").send(sender());
    }

    @Decree(description = "Reset ALL configs to defaults and archive the old settings")
    public void all() {
        if (!sender().isOp()) {
            FConst.error("This command can only be run by server operators.").send(sender());
            return;
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date());
        File archiveDir = Adapt.instance.getDataFolder("config-archive", timestamp);

        int archived = 0;
        int reset = 0;

        // Archive and reset main config
        File mainConfig = Adapt.instance.getDataFile("adapt", "adapt.toml");
        if (mainConfig.exists()) {
            if (archiveFile(mainConfig, new File(archiveDir, "adapt.toml"))) {
                archived++;
            }
            mainConfig.delete();
        }

        // Archive and reset skill configs
        File skillsDir = Adapt.instance.getDataFolder("adapt", "skills");
        if (skillsDir.exists()) {
            File archiveSkillsDir = new File(archiveDir, "skills");
            archiveSkillsDir.mkdirs();
            File[] skillFiles = skillsDir.listFiles((dir, name) -> name.endsWith(".toml"));
            if (skillFiles != null) {
                for (File f : skillFiles) {
                    if (archiveFile(f, new File(archiveSkillsDir, f.getName()))) {
                        archived++;
                    }
                    f.delete();
                }
            }
        }

        // Archive and reset adaptation configs
        File adaptationsDir = Adapt.instance.getDataFolder("adapt", "adaptations");
        if (adaptationsDir.exists()) {
            File archiveAdaptationsDir = new File(archiveDir, "adaptations");
            archiveAdaptationsDir.mkdirs();
            File[] adaptationFiles = adaptationsDir.listFiles((dir, name) -> name.endsWith(".toml"));
            if (adaptationFiles != null) {
                for (File f : adaptationFiles) {
                    if (archiveFile(f, new File(archiveAdaptationsDir, f.getName()))) {
                        archived++;
                    }
                    f.delete();
                }
            }
        }

        // Reload main config from defaults
        AdaptConfig.reload();

        // Reload all skill and adaptation configs from defaults
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getSkills()) {
            if (skill instanceof SimpleSkill<?> simpleSkill) {
                if (simpleSkill.reloadConfigFromDisk(false)) {
                    reset++;
                }
            }

            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (adaptation instanceof SimpleAdaptation<?> simpleAdaptation) {
                    if (simpleAdaptation.reloadConfigFromDisk(false)) {
                        reset++;
                    }
                }
            }
        }

        FConst.success("Archived " + archived + " config files to config-archive/" + timestamp + "/").send(sender());
        FConst.success("Reset " + reset + " configs to defaults.").send(sender());
    }

    private boolean archiveFile(File source, File destination) {
        try {
            destination.getParentFile().mkdirs();
            Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            Adapt.warn("Failed to archive " + source.getPath() + ": " + e.getMessage());
            return false;
        }
    }
}
