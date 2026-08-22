package art.arcane.adapt.papi;

import java.util.Map;

public record AdaptPlayerSnapshot(
    double masterXp,
    String masterXpText,
    double masterLevelExact,
    int level,
    String levelText,
    String maxLevelText,
    double multiplier,
    String multiplierText,
    long wisdom,
    String wisdomText,
    int maxPower,
    int usedPower,
    int availablePower,
    String powerText,
    String powerMaxText,
    String powerUsedText,
    int knownSkills,
    String knownSkillsText,
    int learnedAdaptations,
    String learnedAdaptationsText,
    Map<String, AdaptSkillLineSnapshot> skills,
    Map<String, Integer> adaptationLevels,
    AdaptSkillLineSnapshot zeroLine,
    AdaptMutationView mutations
) {
  public AdaptSkillLineSnapshot skill(String id) {
    return skills.get(id);
  }

  public int adaptationLevel(String id) {
    Integer level = adaptationLevels.get(id);
    return level == null ? 0 : level;
  }

  public long knowledgeForSkill(String skillId) {
    AdaptSkillLineSnapshot line = skills.get(skillId);
    return line == null ? 0L : line.knowledge();
  }
}
