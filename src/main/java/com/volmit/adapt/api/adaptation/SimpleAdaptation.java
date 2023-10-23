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

package com.volmit.adapt.api.adaptation;

import art.arcane.amulet.io.FileWatcher;
import com.google.gson.Gson;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.potion.BrewingRecipe;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.util.*;
import com.volmit.adapt.nms.advancements.advancement.AdvancementVisibility;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Material;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
    private List<AdaptAdvancement> cachedAdvancements;
    private List<AdaptRecipe> recipes;
    private List<BrewingRecipe> brewingRecipes;
    private Class<T> configType;
    private T config;

    public SimpleAdaptation(String name) {
        super("adaptations", UUID.randomUUID() + "-" + name, 1000);
        cachedAdvancements = new ArrayList<>();
        recipes = new ArrayList<>();
        brewingRecipes = new ArrayList<>();
        setMaxLevel(5);
        setCostFactor(0.35);
        setBaseCost(3);
        setIcon(Material.PAPER);
        setInitialCost(1);
        setDescription("No Description Provided");
        this.name = name;

        J.a(() -> {
            if (!isEnabled()) {
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
                    try {
                        if (!AdaptConfig.get().isHotReload()) {
                            return;
                        }
                        if (fw.checkModified() && file.exists()) {
                            config = null;
                            getConfig();
                            Adapt.info("Hotloaded " + file.getPath());
                            Adapt.hotloaded();
                            fw.checkModified();
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            });
        }, 20);
    }

    @Override
    public T getConfig() {
        try {
            if (config == null) {
                T dummy = getConfigurationClass().getConstructor().newInstance();
                File l = Adapt.instance.getDataFile("adapt", "adaptations", getName() + ".json");

                if (!l.exists()) {
                    try {
                        IO.writeAll(l, new JSONObject(new Gson().toJson(dummy)).toString(4));
                    } catch (IOException e) {
                        e.printStackTrace();
                        config = dummy;
                        return config;
                    }
                }

                try {
                    config = new Gson().fromJson(IO.readAll(l), getConfigurationClass());
                    IO.writeAll(l, new JSONObject(new Gson().toJson(config)).toString(4));
                } catch (IOException e) {
                    e.printStackTrace();
                    config = dummy;
                    return config;
                }
            }
        } catch (Throwable e) {
            Adapt.verbose("Failed to load config for " + getName());
        }

        return config;
    }

    public void registerRecipe(AdaptRecipe r) {
        recipes.add(r);
    }

    public void registerBrewingRecipe(BrewingRecipe r) {
        brewingRecipes.add(r);
    }

    @Override
    public String getDisplayName() {
        try {
            return displayName == null ? Adaptation.super.getDisplayName() : (C.RESET + "" + C.BOLD + getSkill().getColor().toString() + displayName);
        } catch (Exception ignored) {
            Adapt.verbose("Failed to get display name for " + getName());
            return null;
        }
    }

    public void registerAdvancement(AdaptAdvancement a) {
        cachedAdvancements.add(a);
    }

    @Override
    public void onRegisterAdvancements(List<AdaptAdvancement> advancements) {
        advancements.addAll(cachedAdvancements);
    }

    public AdaptAdvancement buildAdvancements() {
        List<AdaptAdvancement> a = new ArrayList<>();
        onRegisterAdvancements(a);

        return AdaptAdvancement.builder()
                .key("adaptation_" + getName())
                .title(C.WHITE + "[     " + getDisplayName() + C.WHITE + "     ]")
                .description(getDescription() + ". " + Localizer.dLocalize("snippets", "gui", "unlockthisbyclicking") + " " + AdaptConfig.get().adaptActivatorBlockName)
                .icon(getIcon())
                .children(a)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build();
    }
}
