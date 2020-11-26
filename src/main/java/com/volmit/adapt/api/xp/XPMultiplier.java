package com.volmit.adapt.api.xp;

import com.volmit.adapt.util.M;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class XPMultiplier
{
    private double multiplier = 0D;
    private long goodFor = M.ms() + 10000;

    public XPMultiplier(double percentChange, long duration)
    {
        this.multiplier = percentChange;
        this.goodFor = M.ms() + duration;
    }

    public boolean isExpired() {
        return M.ms() > goodFor;
    }
}
