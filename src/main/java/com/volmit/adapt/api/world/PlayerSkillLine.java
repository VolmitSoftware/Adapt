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

package com.volmit.adapt.api.world;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.notification.ActionBarNotification;
import com.volmit.adapt.api.notification.Notifier;
import com.volmit.adapt.api.notification.SoundNotification;
import com.volmit.adapt.api.notification.TitleNotification;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.xp.XP;
import com.volmit.adapt.api.xp.XPMultiplier;
import com.volmit.adapt.util.M;
import com.volmit.adapt.util.collection.KList;
import com.volmit.adapt.util.collection.KMap;
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
    private final KMap<String, Object> storage = new KMap<>();
    private final KMap<String, PlayerAdaptation> adaptations = new KMap<>();
    private final KList<XPMultiplier> multipliers = new KList<>();

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
        for (XPMultiplier i : multipliers.copy()) {
            if (i.isExpired()) multipliers.remove(i);
            else m += i.getMultiplier();
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
        freshness -= 0.012 + (xp * 0.00025);

        long now = System.currentTimeMillis();
        if (now - lastXpTimestamp > 300000) {
            monotonyCounter = 0;
        }
        lastXpTimestamp = now;
        monotonyCounter++;

        if (monotonyCounter > 200) {
            monotonyMultiplier = Math.max(0.25, 1.0 - ((monotonyCounter - 200) * 0.003));
        } else {
            monotonyMultiplier = 1.0;
        }

        xp = multiplier * monotonyMultiplier * xp;
        this.xp += xp;

        if (p != null) {
            last = M.ms();
            if (AdaptConfig.get().isActionbarNotifyXp()) {
                p.notifyXP(line, xp);
            }
        }
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
}
