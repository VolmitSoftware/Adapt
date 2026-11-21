package art.arcane.adapt.client.hud;

/** One skill row of the activity tracker: the xp a skill earned recently, merged while the row is still showing. */
public final class HudXpLine {
    String skillId;
    double amount;
    int level;
    double progress;
    double shownProgress;
    long createdAt;
    long updatedAt;
    long levelUpAt;
    long animatedAt;
    String label = "";
    String levelText = "";

    public String skillId() {
        return skillId;
    }

    public double amount() {
        return amount;
    }

    public int level() {
        return level;
    }

    public double progress() {
        return progress;
    }

    /** Progress as the bar currently shows it, easing toward {@link #progress()}. */
    public double shownProgress() {
        return shownProgress;
    }

    public long createdAt() {
        return createdAt;
    }

    public long updatedAt() {
        return updatedAt;
    }

    /** Monotonic time this line's skill gained a level, or {@link Long#MIN_VALUE}. */
    public long levelUpAt() {
        return levelUpAt;
    }

    /** The merged gain, "+12". */
    public String label() {
        return label;
    }

    public String levelText() {
        return levelText;
    }

    void reset(String newSkillId, long now) {
        skillId = newSkillId;
        amount = 0.0;
        level = 0;
        progress = 0.0;
        shownProgress = -1.0;
        createdAt = now;
        updatedAt = now;
        levelUpAt = Long.MIN_VALUE;
        animatedAt = now;
        label = "";
        levelText = "";
    }

    void animate(long now, boolean instant) {
        long elapsed = Math.max(0L, now - animatedAt);
        animatedAt = now;
        if (shownProgress < 0.0 || instant) {
            shownProgress = progress;
            return;
        }
        double target = progress;
        if (levelUpAt != Long.MIN_VALUE && now - levelUpAt < 250L && target < shownProgress) {
            target = 1.0;
        }
        shownProgress = HudMotion.approach(shownProgress, target, elapsed, 120.0);
    }
}
