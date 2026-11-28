package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.ContentIds;
import art.arcane.adapt.api.item.ToolRules;
import art.arcane.adapt.api.item.Tools;
import art.arcane.adapt.platform.item.ToolKind;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;

/** Hands a tool skill's operator lists to {@link Tools}, once per loaded config. */
final class ToolSkillRules {
  private final ToolKind kind;
  private final Id skill;
  private Object applied;

  ToolSkillRules(ToolKind kind, Id skill) {
    this.kind = kind;
    this.skill = skill;
  }

  synchronized void apply(Object config, List<String> extraTools, List<String> excludedTools,
      Map<String, Double> extraBlocks, List<String> excludedBlocks, boolean tagRule, boolean capabilityRule,
      boolean behaviourRule) {
    if (config == applied || !AdaptApi.isRunning()) {
      return;
    }
    applied = config;
    Tools.rules(kind, ToolRules.parse("skills/" + ContentIds.configPath(skill), extraTools, excludedTools, extraBlocks,
        excludedBlocks, tagRule, capabilityRule, behaviourRule, AdaptApi.platform().host().registries(),
        AdaptApi.log()::warn));
  }
}
