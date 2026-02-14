/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.api.world;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.notification.ActionBarNotification;
import art.arcane.adapt.api.notification.Notifier;
import art.arcane.adapt.api.notification.SoundNotification;
import art.arcane.adapt.api.notification.TitleNotification;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.api.xp.XPMultiplier;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Sound;

@Data
@NoArgsConstructor
public class PlayerSkillLine {
    private String line = "";
    private double xp = 0;
    private double lastXP = 0;
    private long knowledge = 0;
    private double multiplier = 1D;
    private double freshness = 1D;
    private double rfreshness = 1D;
    private int lastLevel = 0;
    private long last = M.ms();
    private int monotonyCounter = 0;
    private long lastXpTimestamp = 0;
    private double monotonyMultiplier = 1.0;
    private RewardStalenessState skillStaleness = new RewardStalenessState();
    private long lastStalenessCleanup = 0;
    private final KMap<String, Object> storage = new KMap<>();
    private final KMap<String, PlayerAdaptation> adaptations = new KMap<>();
    private final KList<XPMultiplier> multipliers = new KList<>();
    private final KMap<String, RewardStalenessState> activityStaleness = new KMap<>();

    private static double diff(long a, long b) {
        return Math.abs(a - b / (double) (a == 0 ? 1 : a));
    }

    public void update(AdaptPlayer p, String line, PlayerData data) {
        grantSkillsAndAdaptations(p, line);
        checkMaxLevel(p, line);
        updateFreshness();
        updateMultiplier(data);
        updateEarnedXP(p, line);
        updateLevel(p, line, data);
    }

    private void grantSkillsAndAdaptations(AdaptPlayer p, String line) {
        if (!p.getData().isGranted("skill_" + line) && AdaptConfig.get().isAdvancements()) {
            p.getAdvancementHandler().grant("skill_" + line);
        }

        for (String i : getAdaptations().keySet()) {
            if (!p.getData().isGranted("adaptation_" + i) && AdaptConfig.get().isAdvancements()) {
                p.getAdvancementHandler().grant("adaptation_" + i);
            }
        }
    }

    private void checkMaxLevel(AdaptPlayer p, String line) {
        if (!p.isBusy() && getXp() > XP.getXpForLevel(AdaptConfig.get().experienceMaxLevel)) {
            p.getData().addWisdom();
            Adapt.warn("A Player has reached the maximum level of " + AdaptConfig.get().experienceMaxLevel + " and has been granted 1 wisdom, Dropping Level to " + lastLevel);
            setXp(XP.getXpForLevel(AdaptConfig.get().experienceMaxLevel - 1));
        }
    }

    private void updateFreshness() {
        double max = 1D + (getLevel() * 0.004);

        freshness += (0.08 * freshness) + 0.003;
        if (freshness > max) freshness = max;
        if (freshness < 0.01) freshness = 0.01;
        if (freshness < rfreshness) rfreshness -= ((rfreshness - freshness) * 0.003);
        if (freshness > rfreshness) rfreshness += (freshness - rfreshness) * 0.265;
    }

    private void updateMultiplier(PlayerData data) {
        double m = rfreshness;
        for (int i = multipliers.size() - 1; i >= 0; i--) {
            XPMultiplier active = multipliers.get(i);
            if (active == null || active.isExpired()) {
                multipliers.remove(i);
                continue;
            }
            m += active.getMultiplier();
        }

        m = Math.max(0.01, Math.min(m, 1000));
        multiplier = m * data.getMultiplier();
    }

    private void updateEarnedXP(AdaptPlayer p, String line) {
        double earned = xp - lastXP;
        if (earned > p.getServer().getSkillRegistry().getSkill(line).getMinXp()) lastXP = xp;
    }

    private void updateLevel(AdaptPlayer p, String line, PlayerData data) {
        if (lastLevel < getLevel()) {
            long kb = getKnowledge();
            for (int i = lastLevel; i < getLevel(); i++) {
                giveKnowledge((i / 13) + 1);
                p.getData().giveMasterXp((i * AdaptConfig.get().getPlayerXpPerSkillLevelUpLevelMultiplier()) + AdaptConfig.get().getPlayerXpPerSkillLevelUpBase());
            }

            if (AdaptConfig.get().isActionbarNotifyLevel()) notifyLevel(p, getLevel(), getKnowledge());
            lastLevel = getLevel();
        }
    }

    public void giveXP(Notifier p, double xp) {
        giveXP(p, xp, null);
    }

    public void giveXP(Notifier p, double xp, String rewardKey) {
        freshness -= 0.012 + (xp * 0.00025);

        long now = System.currentTimeMillis();
        lastXpTimestamp = now;
        monotonyCounter++;
        monotonyMultiplier = computeStalenessMultiplier(xp, rewardKey, now);

        xp = multiplier * monotonyMultiplier * xp;
        this.xp += xp;

        if (p != null) {
            last = M.ms();
            if (AdaptConfig.get().isActionbarNotifyXp()) {
                p.notifyXP(line, xp);
            }
        }
    }

    public void relaxStalenessForActivitySwitch() {
        AdaptConfig.FarmPrevention prevention = AdaptConfig.get().getFarmPrevention();
        if (prevention == null || !prevention.isEnabled()) {
            monotonyCounter = 0;
            monotonyMultiplier = 1.0;
            return;
        }

        double factor = clamp(prevention.getCrossSkillRecoveryFactor(), 0.0, 1.0);
        applyRecoveryFactor(skillStaleness, factor);
        for (RewardStalenessState state : activityStaleness.values()) {
            applyRecoveryFactor(state, factor);
        }
        monotonyCounter = 0;
    }

    private double computeStalenessMultiplier(double awardXp, String rewardKey, long now) {
        if (awardXp <= 0) {
            return 1.0;
        }

        AdaptConfig.FarmPrevention prevention = AdaptConfig.get().getFarmPrevention();
        if (prevention == null || !prevention.isEnabled()) {
            return 1.0;
        }

        double skillGain = prevention.getSkillBasePressure() + (awardXp * prevention.getSkillXpPressure());
        double skillMultiplier = applyStaleness(
                ensureSkillStaleness(),
                now,
                skillGain,
                prevention.getSkillRecoveryMillis(),
                prevention.getSkillDecayCurve(),
                prevention.getSkillFloorMultiplier()
        );

        double activityMultiplier = 1.0;
        if (prevention.isPerActivityTracking()) {
            String normalizedRewardKey = normalizeRewardKey(rewardKey);
            if (normalizedRewardKey != null) {
                cleanupActivityStaleness(now, prevention.getActivityStateTtlMillis());
                RewardStalenessState activityState = activityStaleness.computeIfAbsent(normalizedRewardKey, k -> new RewardStalenessState());
                double activityGain = prevention.getActivityBasePressure() + (awardXp * prevention.getActivityXpPressure());
                activityMultiplier = applyStaleness(
                        activityState,
                        now,
                        activityGain,
                        prevention.getActivityRecoveryMillis(),
                        prevention.getActivityDecayCurve(),
                        prevention.getActivityFloorMultiplier()
                );
            }
        }

        double floor = clamp(prevention.getSkillFloorMultiplier(), 0.0, 1.0);
        if (prevention.isPerActivityTracking()) {
            floor = clamp(floor * prevention.getActivityFloorMultiplier(), 0.0, 1.0);
        }
        return clamp(skillMultiplier * activityMultiplier, floor, 1.0);
    }

    private RewardStalenessState ensureSkillStaleness() {
        if (skillStaleness == null) {
            skillStaleness = new RewardStalenessState();
        }
        return skillStaleness;
    }

    private double applyStaleness(RewardStalenessState state, long now, double gain, long recoveryMillis, double curve, double floor) {
        if (state == null) {
            return 1.0;
        }

        decayState(state, now, recoveryMillis);
        state.setPressure(clamp(state.getPressure() + Math.max(0.0, gain), 0.0, 100000.0));
        state.setLastAwardAt(now);

        double clampedFloor = clamp(floor, 0.0, 1.0);
        if (curve <= 0) {
            return 1.0;
        }

        double scaled = Math.exp(-state.getPressure() / curve);
        return clamp(clampedFloor + ((1.0 - clampedFloor) * scaled), clampedFloor, 1.0);
    }

    private void decayState(RewardStalenessState state, long now, long recoveryMillis) {
        if (state == null || recoveryMillis <= 0) {
            return;
        }

        long lastAward = state.getLastAwardAt();
        if (lastAward <= 0) {
            state.setLastAwardAt(now);
            return;
        }

        long elapsed = Math.max(0, now - lastAward);
        if (elapsed == 0) {
            return;
        }

        double decay = Math.exp(-(double) elapsed / (double) recoveryMillis);
        state.setPressure(Math.max(0.0, state.getPressure() * decay));
    }

    private void cleanupActivityStaleness(long now, long ttl) {
        if (ttl <= 0) {
            return;
        }
        if (now - lastStalenessCleanup < 15000) {
            return;
        }

        activityStaleness.entrySet().removeIf(entry -> {
            RewardStalenessState state = entry.getValue();
            return state == null || (state.getLastAwardAt() > 0 && now - state.getLastAwardAt() > ttl);
        });
        lastStalenessCleanup = now;
    }

    private void applyRecoveryFactor(RewardStalenessState state, double factor) {
        if (state == null) {
            return;
        }
        state.setPressure(Math.max(0.0, state.getPressure() * factor));
    }

    private String normalizeRewardKey(String rewardKey) {
        if (rewardKey == null) {
            return null;
        }
        String normalized = rewardKey.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public void giveXPFresh(Notifier p, double xp) {
        xp = multiplier * xp;
        this.xp += xp;

        if (p != null) {
            last = M.ms();
            if (AdaptConfig.get().isActionbarNotifyXp()) {
                p.notifyXP(line, xp);
            }
        }
    }

    public boolean hasEarnedWithin(long ms) {
        return M.ms() - last < ms;
    }

    public PlayerAdaptation getAdaptation(String id) {
        return adaptations.get(id);
    }

    public int getAdaptationLevel(String id) {
        PlayerAdaptation a = getAdaptation(id);

        if (a == null) {
            return 0;
        }

        return a.getLevel();
    }

    public void setAdaptation(Adaptation a, int level) {
        if (level <= 1) {
            adaptations.remove(a.getName());
        }

        PlayerAdaptation v = new PlayerAdaptation();
        v.setId(a.getName());
        v.setLevel(Math.min(level, a.getMaxLevel()));
        adaptations.put(a.getName(), v);
    }

    public Skill<?> getRawSkill(AdaptPlayer p) {
        return p.getServer().getSkillRegistry().getSkill(line);
    }

    private void notifyLevel(AdaptPlayer p, double lvl, long kn) {
//        Skill s = p.getServer().getSkillRegistry().getSkill(getLine());
        if (lvl % 10 == 0) {
            p.getNot().queue(SoundNotification.builder()
                    .sound(Sound.UI_TOAST_CHALLENGE_COMPLETE)
                    .volume(1f)
                    .pitch(1.35f)
                    .group("lvl" + getLine())
                    .build(), SoundNotification.builder()
                    .sound(Sound.UI_TOAST_CHALLENGE_COMPLETE)
                    .volume(1f)
                    .pitch(0.75f)
                    .group("lvl" + getLine())
                    .build(), TitleNotification.builder()
                    .in(250)
                    .stay(1450)
                    .out(2250)
                    .group("lvl" + getLine())
                    .title("")
                    .subtitle(p.getServer().getSkillRegistry().getSkill(getLine()).getDisplayName(getLevel()))
                    .build());
            p.getActionBarNotifier().queue(
                    ActionBarNotification.builder()
                            .duration(450)
                            .group("know" + getLine())
                            .title(kn + " " + p.getServer().getSkillRegistry().getSkill(getLine()).getShortName() + " Knowledge")
                            .build());

        } else {
            p.getActionBarNotifier().queue(
                    SoundNotification.builder()
                            .sound(Sound.BLOCK_AMETHYST_BLOCK_BREAK)
                            .volume(1f)
                            .pitch(1.74f)
                            .group("lvl" + getLine())
                            .build(),
                    SoundNotification.builder()
                            .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME)
                            .volume(1f)
                            .pitch(0.74f)
                            .group("lvl" + getLine())
                            .build(),
                    ActionBarNotification.builder()
                            .duration(450)
                            .group("lvl" + getLine())
                            .title(p.getServer().getSkillRegistry().getSkill(getLine()).getDisplayName(getLevel()))
                            .build());
        }

        lastLevel = (int) Math.floor(XP.getLevelForXp(getXp()));
    }

    public void giveKnowledge(long points) {
        this.knowledge += points;
    }

    public double getMinimumXPForLevel() {
        return XP.getXpForLevel(getLevel());
    }

    public double getXPForLevelUpAbsolute() {
        return getMaximumXPForLevel() - getXp();
    }

    public double getXPForLevelUp() {
        return getMaximumXPForLevel() - getMinimumXPForLevel();
    }

    public double getMaximumXPForLevel() {
        return XP.getXpForLevel(getLevel());
    }

    public double getAbsoluteLevel() {
        return XP.getLevelForXp(xp);
    }

    public double getLevelProgress() {
        return getAbsoluteLevel() - getLevel();
    }

    public double getLevelProgressRemaining() {
        return 1D - getLevelProgress();
    }

    public int getLevel() {
        return (int) Math.floor(getAbsoluteLevel());
    }

    public void boost(double v, int i) {
        multipliers.add(new XPMultiplier(v, i));
    }

    public boolean spendKnowledge(int c) {
        if (getKnowledge() >= c) {
            setKnowledge(getKnowledge() - c);
            return true;
        }

        return false;
    }

    @Data
    @NoArgsConstructor
    public static class RewardStalenessState {
        private double pressure = 0;
        private long lastAwardAt = 0;
    }
}
