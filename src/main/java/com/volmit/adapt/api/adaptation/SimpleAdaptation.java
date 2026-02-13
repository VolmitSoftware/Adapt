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

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdvancementSpec;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.potion.BrewingRecipe;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.config.ConfigFileSupport;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Material;

import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.collection.KList;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
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
    private KList<AdaptStatTracker> statTrackers;
    private Class<T> configType;
    private volatile T config;

    public SimpleAdaptation(String name) {
        super("adaptations", UUID.randomUUID() + "-" + name, 1000);
        cachedAdvancements = new ArrayList<>();
        recipes = new ArrayList<>();
        brewingRecipes = new ArrayList<>();
        statTrackers = new KList<>();
        setMaxLevel(5);
        setCostFactor(0.45);
        setBaseCost(4);
        setIcon(Material.PAPER);
        setInitialCost(2);
        setDescription("No Description Provided");
        this.name = name;
    }

    @Override
    public Class<T> getConfigurationClass() {
        return configType;
    }

    @Override
    public void registerConfiguration(Class<T> type) {
        this.configType = type;
    }

    protected File getConfigFile() {
        return Adapt.instance.getDataFile("adapt", "adaptations", getName() + ".toml");
    }

    protected File getLegacyConfigFile() {
        return Adapt.instance.getDataFile("adapt", "adaptations", getName() + ".json");
    }

    protected T createDefaultConfig() {
        try {
            return getConfigurationClass().getConstructor().newInstance();
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to create default config for adaptation " + getName(), e);
        }
    }

    public synchronized boolean reloadConfigFromDisk(boolean announce) {
        if (getConfigurationClass() == null) {
            return false;
        }

        T previous = config;
        File file = getConfigFile();
        try {
            T loaded = loadConfig(file, previous == null ? createDefaultConfig() : previous, previous == null);
            config = loaded;
            applySharedConfigValues(loaded);
            onConfigReload(previous, loaded);
            if (announce) {
                Adapt.info("Hotloaded " + file.getPath());
            }
            return true;
        } catch (Throwable e) {
            Adapt.warn("Skipped hotload for " + file.getPath() + " due to invalid config: " + e.getMessage());
            return false;
        }
    }

    private T loadConfig(File file, T fallback, boolean overwriteOnReadFailure) throws IOException {
        return ConfigFileSupport.load(
                file,
                getLegacyConfigFile(),
                getConfigurationClass(),
                fallback,
                overwriteOnReadFailure,
                "adaptation:" + getName(),
                "Created missing adaptation config [adapt/adaptations/" + getName() + ".toml] from defaults."
        );
    }

    private void applySharedConfigValues(T currentConfig) {
        applyIntField(currentConfig, "baseCost", this::setBaseCost);
        applyIntField(currentConfig, "initialCost", this::setInitialCost);
        applyIntField(currentConfig, "maxLevel", this::setMaxLevel);
        applyLongField(currentConfig, "setInterval", this::setInterval);
    }

    protected void onConfigReload(T previousConfig, T newConfig) {
        applyDoubleField(newConfig, "costFactor", this::setCostFactor);
    }

    private void applyIntField(T source, String fieldName, java.util.function.IntConsumer consumer) {
        Number number = getNumericField(source, fieldName);
        if (number != null) {
            consumer.accept(number.intValue());
        }
    }

    private void applyLongField(T source, String fieldName, java.util.function.LongConsumer consumer) {
        Number number = getNumericField(source, fieldName);
        if (number != null) {
            consumer.accept(number.longValue());
        }
    }

    private void applyDoubleField(T source, String fieldName, java.util.function.DoubleConsumer consumer) {
        Number number = getNumericField(source, fieldName);
        if (number != null) {
            consumer.accept(number.doubleValue());
        }
    }

    private Number getNumericField(T source, String fieldName) {
        Field f = getField(source.getClass(), fieldName);
        if (f == null) {
            return null;
        }

        try {
            f.setAccessible(true);
            Object value = f.get(source);
            if (value instanceof Number number) {
                return number;
            }
        } catch (Throwable ignored) {
            Adapt.verbose("Failed reading config field '" + fieldName + "' for adaptation " + getName());
        }

        return null;
    }

    private Field getField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }

        return null;
    }

    @Override
    public T getConfig() {
        T local = config;
        if (local != null) {
            return local;
        }

        synchronized (this) {
            local = config;
            if (local != null) {
                return local;
            }

            boolean loaded = reloadConfigFromDisk(false);
            local = config;
            if (!loaded || local == null) {
                local = createDefaultConfig();
                applySharedConfigValues(local);
                onConfigReload(null, local);
                config = local;
                Adapt.warn("Falling back to in-memory defaults for adaptation config " + getName() + ".");
            }
        }

        return local;
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

    public void registerStatTracker(AdaptStatTracker tracker) {
        statTrackers.add(tracker);
    }

    public KList<AdaptStatTracker> getStatTrackers() {
        return statTrackers;
    }

    public void registerAdvancement(AdaptAdvancement a) {
        cachedAdvancements.add(a);
    }

    protected void registerAdvancementSpec(AdvancementSpec spec) {
        if (spec == null) {
            return;
        }

        registerAdvancement(spec.toAdvancement());
    }

    protected void registerMilestone(AdvancementSpec spec, String stat, double goal, double reward) {
        if (spec == null) {
            return;
        }

        registerAdvancementSpec(spec);
        registerStatTracker(spec.statTracker(stat, goal, reward));
    }

    protected void registerMilestone(String advancementKey, String stat, double goal, double reward) {
        registerStatTracker(AdaptStatTracker.builder()
                .advancement(advancementKey)
                .stat(stat)
                .goal(goal)
                .reward(reward)
                .build());
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
                .description(getDescription() + ". " + Localizer.dLocalize("snippets.gui.unlock_this_by_clicking") + " " + AdaptConfig.get().adaptActivatorBlockName)
                .icon(getIcon())
                .children(a)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build();
    }
}
