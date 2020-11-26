package com.volmit.adapt.api.skill;

import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.util.C;
import lombok.Data;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

import java.util.UUID;

@Data
public abstract class SimpleSkill extends TickedObject implements Skill{
    private String name;
    private C color;
    private BarColor barColor;
    private BarStyle barStyle;

    public SimpleSkill(String name)
    {
        super("skill", UUID.randomUUID() + "-skill-" + name, 50);
        setColor(C.WHITE);
        setBarColor(BarColor.WHITE);
        setBarStyle(BarStyle.SOLID);
    }

    @Override
    public abstract void onTick();
}
