package art.arcane.adapt.papi;

public record AdaptSkillLevelBand(
    double xp,
    int level,
    double absoluteLevel,
    String levelText,
    String xpText,
    String progressText,
    String progressPercentText,
    String xpToNextText,
    String currentLevelXpText,
    String nextLevelXpText
) {
}
