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
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.potion.BrewingRecipe;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.util.ConfigRewriteReporter;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.util.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Material;

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
        if (!file.exists()) {
            IO.writeAll(file, Json.toJson(fallback, true));
            Adapt.info("Created missing adaptation config [adapt/adaptations/" + getName() + ".json] from defaults.");
            return fallback;
        }

        try {
            String raw = IO.readAll(file);
            T loaded = Json.fromJson(raw, getConfigurationClass());
            if (loaded == null) {
                throw new IOException("Config parser returned null.");
            }

            String canonical = Json.toJson(loaded, true);
            if (!normalizeJson(canonical).equals(normalizeJson(raw))) {
                ConfigRewriteReporter.reportRewrite(file, "adaptation:" + getName(), raw, canonical);
                IO.writeAll(file, canonical);
            }

            return loaded;
        } catch (Throwable e) {
            if (overwriteOnReadFailure) {
                ConfigRewriteReporter.reportFallbackRewrite(file, "adaptation:" + getName(), "invalid json");
                IO.writeAll(file, Json.toJson(fallback, true));
                return fallback;
            }

            throw new IOException("Invalid json", e);
        }
    }

    private String normalizeJson(String json) {
        return json.replace("\r\n", "\n").stripTrailing();
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
    public synchronized T getConfig() {
        if (config == null) {
            reloadConfigFromDisk(false);
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
