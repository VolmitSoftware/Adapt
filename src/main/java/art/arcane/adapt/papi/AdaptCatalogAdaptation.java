package art.arcane.adapt.papi;

public final class AdaptCatalogAdaptation {
  private final String id;
  private final String name;
  private final String skillId;
  private final String skillName;
  private final boolean enabled;
  private final boolean skillEnabled;
  private final boolean permanent;
  private final int maxLevel;
  private final int[] cumulativeKnowledgeCost;
  private final String nameText;
  private final String maxLevelText;

  AdaptCatalogAdaptation(
      String id,
      String name,
      String skillId,
      String skillName,
      boolean enabled,
      boolean skillEnabled,
      boolean permanent,
      int maxLevel,
      int[] cumulativeKnowledgeCost,
      String nameText,
      String maxLevelText
  ) {
    this.id = id;
    this.name = name;
    this.skillId = skillId;
    this.skillName = skillName;
    this.enabled = enabled;
    this.skillEnabled = skillEnabled;
    this.permanent = permanent;
    this.maxLevel = maxLevel;
    this.cumulativeKnowledgeCost = cumulativeKnowledgeCost;
    this.nameText = nameText;
    this.maxLevelText = maxLevelText;
  }

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String skillId() {
    return skillId;
  }

  public String skillName() {
    return skillName;
  }

  public boolean enabled() {
    return enabled;
  }

  public boolean skillEnabled() {
    return skillEnabled;
  }

  public boolean permanent() {
    return permanent;
  }

  public int maxLevel() {
    return maxLevel;
  }

  public String nameText() {
    return nameText;
  }

  public String maxLevelText() {
    return maxLevelText;
  }

  public int clampLevel(int level) {
    if (level < 0) {
      return 0;
    }

    return Math.min(level, maxLevel);
  }

  public int knowledgeCostFor(int targetLevel, int currentLevel) {
    int target = clampLevel(targetLevel);
    int current = clampLevel(currentLevel);

    if (target <= current) {
      return 0;
    }

    return cumulativeKnowledgeCost[target] - cumulativeKnowledgeCost[current];
  }

  public int powerCostFor(int targetLevel, int currentLevel) {
    int target = clampLevel(targetLevel);
    int current = clampLevel(currentLevel);

    if (target <= current) {
      return 0;
    }

    return target - current;
  }

  public int nextLevel(int currentLevel) {
    return Math.min(maxLevel, clampLevel(currentLevel) + 1);
  }
}
