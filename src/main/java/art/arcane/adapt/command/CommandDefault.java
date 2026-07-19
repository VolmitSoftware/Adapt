package art.arcane.adapt.command;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.service.HotloadSVC;
import art.arcane.adapt.service.MutationSVC;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.director.context.AdaptationListingHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.compat.BukkitDirectorContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

@Director(name = "default", origin = DirectorOrigin.BOTH, description = "Reset configs to defaults")
public class CommandDefault {

  @Director(description = "Reset a skill config to defaults")
  public void skill(
      @Param(description = "skill to reset")
      AdaptationListingHandler.SkillProvider skillTarget
  ) {
    if (!BukkitDirectorContext.sender().isOp()) {
      FConst.error("This command can only be run by server operators.").send(BukkitDirectorContext.sender());
      return;
    }

    SkillRegistry registry = Adapt.instance.getAdaptServer().getSkillRegistry();
    Skill<?> skill = registry.getSkill(skillTarget.name());
    if (skill == null) {
      FConst.error("Unknown skill: " + skillTarget.name()).send(BukkitDirectorContext.sender());
      return;
    }

    if (!(skill instanceof SimpleSkill<?>)) {
      FConst.error("Skill " + skill.getName() + " does not support config reset.").send(BukkitDirectorContext.sender());
      return;
    }

    File configFile = Adapt.instance.getDataFile("adapt", "skills", skill.getName() + ".toml");
    if (configFile.exists() && !configFile.delete()) {
      FConst.error("Failed to delete config file for " + skill.getName()).send(BukkitDirectorContext.sender());
      return;
    }

    if (!registry.hotReloadSkillConfig(skill.getName())) {
      FConst.error("Failed to reload the default config for " + skill.getName()).send(BukkitDirectorContext.sender());
      return;
    }
    reconcileMutations();
    FConst.success("Reset config for skill " + skill.getName() + " to defaults.").send(BukkitDirectorContext.sender());
  }

  @Director(description = "Reset an adaptation config to defaults")
  public void adaptation(
      @Param(description = "adaptation to reset (skill:adaptation)")
      AdaptationListingHandler.AdaptationProvider adaptationTarget
  ) {
    if (!BukkitDirectorContext.sender().isOp()) {
      FConst.error("This command can only be run by server operators.").send(BukkitDirectorContext.sender());
      return;
    }

    String[] split = adaptationTarget.name().split(":", 2);
    if (split.length != 2) {
      FConst.error("Invalid format. Use skill:adaptation").send(BukkitDirectorContext.sender());
      return;
    }

    Skill<?> skill = Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(split[0]);
    if (skill == null) {
      FConst.error("Unknown skill: " + split[0]).send(BukkitDirectorContext.sender());
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
      FConst.error("Unknown adaptation: " + split[1] + " in skill " + skill.getName()).send(BukkitDirectorContext.sender());
      return;
    }

    if (!(adaptation instanceof SimpleAdaptation<?>)) {
      FConst.error("Adaptation " + adaptation.getName() + " does not support config reset.").send(BukkitDirectorContext.sender());
      return;
    }

    File configFile = Adapt.instance.getDataFile("adapt", "adaptations", adaptation.getName() + ".toml");
    if (configFile.exists() && !configFile.delete()) {
      FConst.error("Failed to delete config file for " + adaptation.getName()).send(BukkitDirectorContext.sender());
      return;
    }

    SkillRegistry registry = Adapt.instance.getAdaptServer().getSkillRegistry();
    if (!registry.hotReloadAdaptationConfig(adaptation.getName())) {
      FConst.error("Failed to reload the default config for " + adaptation.getName()).send(BukkitDirectorContext.sender());
      return;
    }
    reconcileMutations();
    FConst.success("Reset config for adaptation " + adaptation.getName() + " to defaults.").send(BukkitDirectorContext.sender());
  }

  @Director(description = "Reset ALL configs to defaults and archive the old settings")
  public void all() {
    if (!BukkitDirectorContext.sender().isOp()) {
      FConst.error("This command can only be run by server operators.").send(BukkitDirectorContext.sender());
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
    SkillRegistry registry = Adapt.instance.getAdaptServer().getSkillRegistry();
    for (Skill<?> skill : registry.getAllSkills()) {
      int configCount = skill instanceof SimpleSkill<?> ? 1 : 0;
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        if (adaptation instanceof SimpleAdaptation<?>) {
          configCount++;
        }
      }
      if (registry.hotReloadSkillConfig(skill.getName())) {
        reset += configCount;
      }
    }
    reconcileMutations();

    FConst.success("Archived " + archived + " config files to config-archive/" + timestamp + "/").send(BukkitDirectorContext.sender());
    FConst.success("Reset " + reset + " configs to defaults.").send(BukkitDirectorContext.sender());
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

  private void reconcileMutations() {
    MutationSVC service = MutationSVC.get();
    if (service != null) {
      HotloadSVC.reconcileOnlineMutations(service.getManager());
    }
  }
}
