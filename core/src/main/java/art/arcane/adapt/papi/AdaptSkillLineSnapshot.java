package art.arcane.adapt.papi;

public record AdaptSkillLineSnapshot(
    AdaptSkillLevelBand band,
    long knowledge,
    String knowledgeText,
    double multiplier,
    String multiplierText,
    int learnedAdaptations,
    String learnedAdaptationsText
) {
}
