package com.volmit.adapt.api.skill;

import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.KList;
import eu.endercentral.crazy_advancements.AdvancementVisibility;
import io.papermc.lib.PaperLib;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

import java.util.List;
import java.util.UUID;

@Data
public abstract class SimpleSkill extends TickedObject implements Skill {
    private final String name;
    private final String emojiName;
    private C color;
    private double minXp;
    private String description;
    private Material icon;
    @EqualsAndHashCode.Exclude
    private KList<Adaptation> adaptations;
    private KList<AdaptStatTracker> statTrackers;
    private KList<AdaptAdvancement> cachedAdvancements;
    private String advancementBackground;

    public SimpleSkill(String name, String emojiName) {
        super("skill", UUID.randomUUID() + "-skill-" + name, 50);
        statTrackers = new KList<>();
        cachedAdvancements = new KList<>();
        this.emojiName = emojiName;
        adaptations = new KList<>();
        setColor(C.WHITE);
        this.name = name;
        setIcon(Material.BOOK);
        setDescription("No Description Provided");
        setMinXp(100);
        setAdvancementBackground("minecraft:textures/block/deepslate_tiles.png");
    }

    public void registerAdvancement(AdaptAdvancement a)
    {
        cachedAdvancements.add(a);
    }

    @Override
    public void onRegisterAdvancements(KList<AdaptAdvancement> advancements) {
        advancements.addAll(cachedAdvancements);
    }

    public AdaptAdvancement buildAdvancements()
    {
        KList<AdaptAdvancement> a = new KList<>();
        onRegisterAdvancements(a);

        for(Adaptation i : getAdaptations())
        {
            a.add(i.buildAdvancements());
        }

        return AdaptAdvancement.builder()
            .background(getAdvancementBackground())
            .key("skill_" + getName())
            .title(getDisplayName())
            .description(getDescription())
            .icon(getIcon())
            .children(a)
            .visibility(AdvancementVisibility.HIDDEN)
            .build();
    }

    @Override
    public void registerStatTracker(AdaptStatTracker tracker) {
        getStatTrackers().add(tracker);
    }

    @Override
    public KList<AdaptStatTracker> getStatTrackers() {
        return statTrackers;
    }

    @Override
    public void registerAdaptation(Adaptation a) {
        a.setSkill(this);
        adaptations.add(a);
    }

    @Override
    public void unregister() {
        adaptations.forEach(Adaptation::unregister);
    }

    @Override
    public abstract void onTick();
}
