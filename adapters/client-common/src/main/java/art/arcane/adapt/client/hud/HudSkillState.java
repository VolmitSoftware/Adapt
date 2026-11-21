package art.arcane.adapt.client.hud;

/** The player's current standing in one skill, kept up to date by player views and xp awards. */
public final class HudSkillState {
    private final String skillId;
    int level;
    double progress;
    double xp;
    long knowledge;
    long levelUpAt = Long.MIN_VALUE;
    String levelText = "";
    String knowledgeText = "";

    HudSkillState(String skillId) {
        this.skillId = skillId;
    }

    public String skillId() {
        return skillId;
    }

    public int level() {
        return level;
    }

    public double progress() {
        return progress;
    }

    public double xp() {
        return xp;
    }

    public long knowledge() {
        return knowledge;
    }

    /** Monotonic time of the last level gained while connected, or {@link Long#MIN_VALUE}. */
    public long levelUpAt() {
        return levelUpAt;
    }

    public String levelText() {
        return levelText;
    }

    public String knowledgeText() {
        return knowledgeText;
    }

    void set(int newLevel, double newProgress, double newXp, long newKnowledge) {
        if (newLevel != level || levelText.isEmpty()) {
            levelText = Integer.toString(newLevel);
        }
        if (newKnowledge != knowledge || knowledgeText.isEmpty()) {
            knowledgeText = Long.toString(newKnowledge);
        }
        level = newLevel;
        progress = Math.max(0.0, Math.min(1.0, newProgress));
        xp = newXp;
        knowledge = newKnowledge;
    }
}
