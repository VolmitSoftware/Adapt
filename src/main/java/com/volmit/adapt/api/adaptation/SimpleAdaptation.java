package com.volmit.adapt.api.adaptation;

import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.util.KList;
import eu.endercentral.crazy_advancements.AdvancementVisibility;
import lombok.Data;
import org.bukkit.Material;

import java.util.UUID;

@Data
public abstract class SimpleAdaptation extends TickedObject implements Adaptation {
    private int maxLevel;
    private int initialCost;
    private int baseCost;
    private double costFactor;
    private Skill skill;
    private String description;
    private Material icon;
    private String name;

    public SimpleAdaptation(String name) {
        super("adaptations", UUID.randomUUID() + "-" + name, 1000);
        setMaxLevel(5);
        setCostFactor(0.35);
        setBaseCost(3);
        setIcon(Material.PAPER);
        setInitialCost(1);
        setDescription("No Description Provided");
        this.name = name;
    }

    public AdaptAdvancement buildAdvancements()
    {
        KList<AdaptAdvancement> a = new KList<>();
        onRegisterAdvancements(a);

        return AdaptAdvancement.builder()
            .key("adaptation_" + getName())
            .title(getDisplayName())
            .description(getDescription() + ". Unlock this Adaptation by right clicking a bookshelf.")
            .icon(getIcon())
            .toast(true)
            .children(a)
            .visibility(AdvancementVisibility.ALWAYS)
            .build();
    }
}
