package com.volmit.adapt.api.skill;

import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.MaterialBlock;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

import java.util.UUID;
import java.util.regex.MatchResult;

@Data
public abstract class SimpleSkill extends TickedObject implements Skill{
    private final String name;
    private C color;
    private BarColor barColor;
    private double minXp;
    private BarStyle barStyle;
    private String description;
    private Material icon;
    private KList<Adaptation> adaptations;

    public SimpleSkill(String name)
    {
        super("skill", UUID.randomUUID() + "-skill-" + name, 50);
        adaptations = new KList<>();
        setColor(C.WHITE);
        setBarColor(BarColor.WHITE);
        setBarStyle(BarStyle.SOLID);
        this.name = name;
        setIcon(Material.BOOK);
        setDescription("No Description Provided");
        setMinXp(100);
    }

    @Override
    public void registerAdaptation(Adaptation a) {
        a.setSkill(this);
        adaptations.add(a);
    }

    @Override
    public void unregister()
    {
        adaptations.forEach(Adaptation::unregister);
    }

    @Override
    public abstract void onTick();
}
