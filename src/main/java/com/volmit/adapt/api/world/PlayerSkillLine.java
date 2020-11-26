package com.volmit.adapt.api.world;

import com.volmit.adapt.api.skill.SkillLine;
import com.volmit.adapt.api.xp.XP;
import com.volmit.adapt.api.xp.XPMultiplier;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.M;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.TimeUnit;

@Data
@NoArgsConstructor
public class PlayerSkillLine {
    private transient String line = "";
    private double xp = 0;
    private double lastXP = 0;
    private long knowledge = 0;
    private double multiplier = 1D;
    private double freshness = 1D;
    private double rfreshness = 1D;
    private int lastLevel = 0;
    private long last = M.ms();
    private KList<XPMultiplier> multipliers = new KList<>();

    public void giveXP(double xp) {
        freshness -= xp * 0.001;
        this.xp += multiplier * xp;
        last = M.ms();
    }

    public boolean hasEarnedWithin(long ms)
    {
        return M.ms() - last < ms;
    }

    public void update(AdaptPlayer p, String line)
    {
        if(!p.isBusy() && getXp() > XP.getXpForLevel(100))
        {
            xp = getXp() - XP.getXpForLevel(100);
            lastXP = xp;
            lastLevel = (int) Math.floor(XP.getLevelForXp(getXp()));
            p.getData().addWisdom();
            p.notifyWisdom(line);
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

        double m = 1D * rfreshness;

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

        if(!p.isBusy())
        {
            double earned = xp - lastXP;

            if(earned > SkillLine.valueOf(line.toUpperCase()).getMinxp())
            {
                p.notifyEarn(earned, line);
                lastXP = xp;
            }
        }

        if(!p.isBusy())
        {
            if(lastLevel < getLevel())
            {
                p.notifyLevelUp(getLevel(), getLevel() - lastLevel, line);
                lastLevel = getLevel();
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
}
