package com.volmit.adapt.api.adaptation;

import com.google.gson.Gson;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.FileWatcher;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.IO;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.JSONObject;
import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.advancements.advancement.AdvancementVisibility;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Material;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@EqualsAndHashCode(callSuper = false)
@Data
public abstract class SimpleAdaptation<T> extends TickedObject implements Adaptation<T> {
    private int maxLevel;
    private int initialCost;
    private int baseCost;
    private double costFactor;
    private String displayName;
    private Skill<?> skill;
    private String description;
    private Material icon;
    private String name;
    private KList<AdaptAdvancement> cachedAdvancements;
    private KList<AdaptRecipe> recipes;
    private Class<T> configType;
    private T config;

    public SimpleAdaptation(String name) {
        super("adaptations", UUID.randomUUID() + "-" + name, 1000);
        cachedAdvancements = new KList<>();
        recipes = new KList<>();
        setMaxLevel(5);
        setCostFactor(0.35);
        setBaseCost(3);
        setIcon(Material.PAPER);
        setInitialCost(1);
        setDescription("No Description Provided");
        this.name = name;

        J.a(() -> {
            if(!isEnabled()) {
                unregister();
            }
        }, 1);
    }

    @Override
    public Class<T> getConfigurationClass() {
        return configType;
    }

    @Override
    public void registerConfiguration(Class<T> type) {
        this.configType = type;
        File file = Adapt.instance.getDataFile("adapt", "adaptations", getName() + ".json");
        FileWatcher fw = new FileWatcher(file);
        fw.checkModified();
        J.a(() -> {
            fw.checkModified();
            Adapt.instance.getTicker().register(new TickedObject("config", "config-adaptation-" + getName(), 1000) {
                @Override
                public void onTick() {
                    if(fw.checkModified() && file.exists())
                    {
                        config = null;
                        getConfig();
                        Adapt.info("Hotloaded " + file.getPath());
                        fw.checkModified();
                    }
                }
            });
        }, 20);
    }

    @Override
    public T getConfig() {
        try {
            if(config == null) {
                T dummy = getConfigurationClass().getConstructor().newInstance();
                File l = Adapt.instance.getDataFile("adapt", "adaptations", getName() + ".json");

                if(!l.exists()) {
                    try {
                        IO.writeAll(l, new JSONObject(new Gson().toJson(dummy)).toString(4));
                    } catch(IOException e) {
                        e.printStackTrace();
                        config = dummy;
                        return config;
                    }
                }

                try {
                    config = new Gson().fromJson(IO.readAll(l), getConfigurationClass());
                    IO.writeAll(l, new JSONObject(new Gson().toJson(config)).toString(4));
                } catch(IOException e) {
                    e.printStackTrace();
                    config = dummy;
                    return config;
                }
            }
        } catch(Throwable e) {

        }

        return config;
    }

    public void registerRecipe(AdaptRecipe r) {
        recipes.add(r);
    }

    @Override
    public String getDisplayName() {
        return displayName == null ? Adaptation.super.getDisplayName() : (C.RESET + "" + C.BOLD + getSkill().getColor().toString() + displayName);
    }

    public void registerAdvancement(AdaptAdvancement a) {
        cachedAdvancements.add(a);
    }

    @Override
    public void onRegisterAdvancements(KList<AdaptAdvancement> advancements) {
        advancements.addAll(cachedAdvancements);
    }

    public AdaptAdvancement buildAdvancements() {
        KList<AdaptAdvancement> a = new KList<>();
        onRegisterAdvancements(a);

        return AdaptAdvancement.builder()
            .key("adaptation_" + getName())
            .title(getDisplayName())
            .description(getDescription() + ". Unlock this Adaptation by right clicking a bookshelf.")
            .icon(getIcon())
            .children(a)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build();
    }
}
