package com.volmit.adapt.api.world;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.notification.Notifier;
import com.volmit.adapt.api.notification.SoundNotification;
import com.volmit.adapt.api.notification.TitleNotification;
import com.volmit.adapt.api.xp.XP;
import com.volmit.adapt.api.xp.XPMultiplier;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.KMap;
import com.volmit.adapt.util.M;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Sound;

import java.util.concurrent.TimeUnit;

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
    private KMap<String, PlayerAdaptation> adaptations = new KMap<>();
    private KList<XPMultiplier> multipliers = new KList<>();

    public void giveXP(Notifier p, double xp) {
        freshness -= xp * 0.001;
        this.xp += multiplier * xp;

        if(p != null)
        {
            last = M.ms();
            p.notifyXP(line, xp);
        }
    }

    public boolean hasEarnedWithin(long ms)
    {
        return M.ms() - last < ms;
    }

    public PlayerAdaptation getAdaptation(String id)
    {
        return adaptations.get(id);
    }

    public int getAdaptationLevel(String id)
    {
        PlayerAdaptation a = getAdaptation(id);

        if(a == null)
        {
            return 0;
        }

        return a.getLevel();
    }

    public void setAdaptation(Adaptation a, int level)
    {
        if(level <= 1)
        {
            adaptations.remove(a.getName());
        }

        PlayerAdaptation v = new PlayerAdaptation();
        v.setId(a.getName());
        v.setLevel(Math.min(level, a.getMaxLevel()));
        adaptations.put(a.getName(), v);
    }

    public void update(AdaptPlayer p, String line)
    {
        if(!p.isBusy() && getXp() > XP.getXpForLevel(100))
        {
            xp = getXp() - XP.getXpForLevel(100);
            lastXP = xp;
            lastLevel = (int) Math.floor(XP.getLevelForXp(getXp()));
            p.getData().addWisdom();
            boost(0.25, (int) TimeUnit.HOURS.toMillis(1));
        }

        double max = 1D + (getLevel() * 0.004);

        freshness+=(0.1 * freshness) + 0.00124;
        if(freshness > max)
        {
            freshness = max;
        }

        if(freshness < 0.01)
        {
            freshness = 0.01;
        }

        if(freshness < rfreshness)
        {
            rfreshness -= ((rfreshness - freshness) * 0.003);
        }

        if(freshness > rfreshness)
        {
            rfreshness += (freshness - rfreshness) * 0.265;
        }

        double m = rfreshness;

        for(XPMultiplier i : multipliers.copy())
        {
            if(i.isExpired())
            {
                multipliers.remove(i);
                continue;
            }

            m += i.getMultiplier();
        }

        if(m <=0)
        {
            m = 0.01;
        }

        if(m > 1000)
        {
            m = 1000;
        }

        multiplier = m;

        double earned = xp - lastXP;

        if(earned > p.getServer().getSkillRegistry().getSkill(line).getMinXp())
        {
            lastXP = xp;
        }

        if(lastLevel < getLevel())
        {
            for(int i = lastLevel; i < getLevel(); i++)
            {
                giveKnowledge((i / 13) + 1);
            }

            lastLevel = getLevel();

            if(getLevel() >= 5)
            {
                p.getNot().queue(SoundNotification.builder()
                            .sound(Sound.BLOCK_BEACON_POWER_SELECT)
                            .volume(0.7f)
                            .pitch(1.35f)
                        .build(),
                        SoundNotification.builder()
                            .sound(Sound.BLOCK_BAMBOO_HIT)
                            .volume(1f)
                            .pitch(0.65f)
                        .build(),
                        TitleNotification.builder()
                            .in(250)
                            .stay(250)
                            .out(750)
                            .title("")
                            .subtitle(p.getServer().getSkillRegistry().getSkill(getLine()).getDisplayName(getLevel()))
                        .build());
            }
        }
    }

    public void giveKnowledge(long points)
    {
        this.knowledge +=points;
    }

    public double getMinimumXPForLevel()
    {
        return XP.getXpForLevel(getLevel());
    }

    public double getXPForLevelUpAbsolute()
    {
        return getMaximumXPForLevel()-getXp();
    }

    public double getXPForLevelUp()
    {
        return getMaximumXPForLevel()-getMinimumXPForLevel();
    }

    public double getMaximumXPForLevel()
    {
        return XP.getXpForLevel(getLevel());
    }

    public double getAbsoluteLevel()
    {
        return XP.getLevelForXp(xp);
    }

    public double getLevelProgress()
    {
        return getAbsoluteLevel() - getLevel();
    }

    public double getLevelProgressRemaining()
    {
        return 1D - getLevelProgress();
    }

    public int getLevel()
    {
        return (int) Math.floor(getAbsoluteLevel());
    }

    public void boost(double v, int i) {
        multipliers.add(new XPMultiplier(v, i));
    }

    public boolean spendKnowledge(int c) {
        if(getKnowledge() >= c)
        {
            setKnowledge(getKnowledge() - c);
            return true;
        }

        return false;
    }
}
