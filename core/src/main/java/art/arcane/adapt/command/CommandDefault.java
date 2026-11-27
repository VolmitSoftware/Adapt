package art.arcane.adapt.command;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.skill.SkillHost;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.director.context.AdaptationListingHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

@Director(name = "default", origin = DirectorOrigin.BOTH, description = "Reset configs to defaults", descriptionKey = "adapt.command.help.reset_configs_to_defaults")
public class CommandDefault {

  @Director(description = "Reset a skill config to defaults", descriptionKey = "adapt.command.help.reset_a_skill_config_to_defaults")
  public void skill(
      @Param(description = "skill to reset", descriptionKey = "adapt.command.help.skill_to_reset")
      AdaptationListingHandler.SkillProvider skillTarget
  ) {
    if (!CommandContext.hasPermission("adapt.configurator")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.configurator")))
          .send(CommandContext.source());
      return;
    }

    SkillRegistry registry = AdaptCore.get().getAdaptServer().getSkillRegistry();
    Skill<?> skill = CommandTargets.skill(skillTarget.name(), CommandContext.source());
    if (skill == null) {
      return;
    }

    if (!(skill instanceof SimpleSkill<?> simpleSkill)) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.SKILL_RESET_UNSUPPORTED, untrusted("skill", SkillHost.lineKey(skill))))
          .send(CommandContext.source());
      return;
    }

    if (!ComponentHost.of(simpleSkill).writeDefaultConfig() || !registry.hotReloadSkillConfig(skill.getId())) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.DEFAULT_CONFIG_RELOAD_FAILED, untrusted("target", SkillHost.lineKey(skill))))
          .send(CommandContext.source());
      return;
    }
    FConst.success(AdaptLanguage.text(CommandRuntimeMessages.SKILL_RESET, untrusted("skill", SkillHost.lineKey(skill))))
        .send(CommandContext.source());
  }

  @Director(description = "Reset an adaptation config to defaults", descriptionKey = "adapt.command.help.reset_an_adaptation_config_to_defaults")
  public void adaptation(
      @Param(description = "Adaptation to reset, by id such as herbalism/luck", descriptionKey = "adapt.command.help.adaptation_to_reset_skill_adaptation")
      AdaptationListingHandler.AdaptationProvider adaptationTarget
  ) {
    if (!CommandContext.hasPermission("adapt.configurator")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.configurator")))
          .send(CommandContext.source());
      return;
    }

    Adaptation<?> adaptation = CommandTargets.adaptation(adaptationTarget.name(), CommandContext.source());
    if (adaptation == null) {
      return;
    }

    if (!(adaptation instanceof SimpleAdaptation<?> simpleAdaptation)) {
      FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.ADAPTATION_RESET_UNSUPPORTED,
          untrusted("adaptation", adaptation.getId().toString())
      )).send(CommandContext.source());
      return;
    }

    SkillRegistry registry = AdaptCore.get().getAdaptServer().getSkillRegistry();
    if (!ComponentHost.of(simpleAdaptation).writeDefaultConfig() || !registry.hotReloadAdaptationConfig(adaptation.getId())) {
      FConst.error(AdaptLanguage.text(
          CommandRuntimeMessages.DEFAULT_CONFIG_RELOAD_FAILED,
          untrusted("target", adaptation.getId().toString())
      )).send(CommandContext.source());
      return;
    }
    FConst.success(AdaptLanguage.text(
        CommandRuntimeMessages.ADAPTATION_RESET,
        untrusted("adaptation", adaptation.getId().toString())
    )).send(CommandContext.source());
  }

  @Director(description = "Reset ALL configs to defaults and archive the old settings", descriptionKey = "adapt.command.help.reset_all_configs_to_defaults_and_archive_the_old_settings")
  public void all() {
    if (!CommandContext.hasPermission("adapt.configurator")) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", "adapt.configurator")))
          .send(CommandContext.source());
      return;
    }

    String timestamp = new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date());
    File archiveDir = dataFolder("config-archive", timestamp);

    int archived = 0;
    File mainConfig = AdaptEnvironment.dataFile("adapt.toml");
    if (mainConfig.exists() && archiveFile(mainConfig, new File(archiveDir, "adapt.toml"))) {
      archived++;
    }
    archived += archiveFolder(dataFolder("skills"), new File(archiveDir, "skills"));
    archived += archiveFolder(dataFolder("adaptations"), new File(archiveDir, "adaptations"));

    // Defaults overwrite each file in place: the reload path keeps the live config when its file is missing.
    int reset = 0;
    Set<String> failed = new LinkedHashSet<>();
    if (AdaptConfig.resetToDefaults()) {
      reset++;
    } else {
      failed.add("adapt.toml");
    }

    SkillRegistry registry = AdaptCore.get().getAdaptServer().getSkillRegistry();
    for (Skill<?> skill : registry.getAllSkills()) {
      int written = 0;
      if (skill instanceof SimpleSkill<?> simpleSkill) {
        if (ComponentHost.of(simpleSkill).writeDefaultConfig()) {
          written++;
        } else {
          failed.add(SkillHost.lineKey(skill));
        }
      }
      for (Adaptation<?> adaptation : SkillHost.ownedAdaptations(skill)) {
        if (!(adaptation instanceof SimpleAdaptation<?> simpleAdaptation)) {
          continue;
        }
        if (ComponentHost.of(simpleAdaptation).writeDefaultConfig()) {
          written++;
        } else {
          failed.add(adaptation.getId().toString());
        }
      }
      if (registry.hotReloadSkillConfig(skill.getId())) {
        reset += written;
      } else {
        failed.add(SkillHost.lineKey(skill));
      }
    }

    FConst.success(AdaptLanguage.text(
        CommandRuntimeMessages.CONFIGS_ARCHIVED,
        trusted("count", archived),
        trusted("timestamp", timestamp)
    )).send(CommandContext.source());
    FConst.success(AdaptLanguage.text(CommandRuntimeMessages.CONFIGS_RESET, trusted("count", reset)))
        .send(CommandContext.source());
    for (String target : failed) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.DEFAULT_CONFIG_RELOAD_FAILED, untrusted("target", target)))
          .send(CommandContext.source());
    }
  }

  private static File dataFolder(String... path) {
    File folder = AdaptEnvironment.dataFile(path);
    folder.mkdirs();
    return folder;
  }

  private int archiveFolder(File source, File destination) {
    File[] files = source.listFiles((File file) -> file.isDirectory() || file.getName().endsWith(".toml"));
    if (files == null) {
      return 0;
    }

    int archived = 0;
    for (File file : files) {
      if (file.isDirectory()) {
        archived += archiveFolder(file, new File(destination, file.getName()));
      } else if (archiveFile(file, new File(destination, file.getName()))) {
        archived++;
      }
    }
    return archived;
  }

  private boolean archiveFile(File source, File destination) {
    try {
      destination.getParentFile().mkdirs();
      Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
      return true;
    } catch (IOException e) {
      AdaptLog.warn("Failed to archive " + source.getPath() + ": " + e.getMessage());
      return false;
    }
  }
}
