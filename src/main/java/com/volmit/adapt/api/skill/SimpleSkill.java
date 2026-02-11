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

package com.volmit.adapt.api.skill;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.item.ItemListings;
import com.volmit.adapt.util.ConfigRewriteReporter;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.collection.KList;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.UUID;

@EqualsAndHashCode(callSuper = false)
@Data
public abstract class SimpleSkill<T> extends TickedObject implements Skill<T> {
    private final String name;
    private final String emojiName;
    private C color;
    private double minXp;
    private String description;
    private String displayName;
    private Material icon;
    @EqualsAndHashCode.Exclude
    private KList<Adaptation<?>> adaptations;
    private KList<AdaptStatTracker> statTrackers;
    private KList<AdaptAdvancement> cachedAdvancements;
    private String advancementBackground;
    private KList<AdaptRecipe> recipes;
    private Class<T> configType;
    private T config;

    public SimpleSkill(String name, String emojiName) {
        super("skill", UUID.randomUUID() + "-skill-" + name, 50);
        statTrackers = new KList<>();
        recipes = new KList<>();
        cachedAdvancements = new KList<>();
        this.emojiName = emojiName;
        adaptations = new KList<>();
        setColor(C.WHITE);
        this.name = name;
        setIcon(Material.BOOK);
        setDescription("No Description Provided");
        setMinXp(100);
        setAdvancementBackground("minecraft:textures/block/deepslate_tiles.png");

        J.a(() -> {
            J.attempt(this::getConfig);
            getAdaptations().forEach(i -> J.attempt(i::getConfig));
        }, 1);
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
        return Adapt.instance.getDataFile("adapt", "skills", getName() + ".json");
    }

    protected T createDefaultConfig() {
        try {
            return getConfigurationClass().getConstructor().newInstance();
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to create default config for skill " + getName(), e);
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
            Adapt.info("Created missing skill config [adapt/skills/" + getName() + ".json] from defaults.");
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
                ConfigRewriteReporter.reportRewrite(file, "skill:" + getName(), raw, canonical);
                IO.writeAll(file, canonical);
            }

            return loaded;
        } catch (Throwable e) {
            if (overwriteOnReadFailure) {
                ConfigRewriteReporter.reportFallbackRewrite(file, "skill:" + getName(), "invalid json");
                IO.writeAll(file, Json.toJson(fallback, true));
                return fallback;
            }

            throw new IOException("Invalid json", e);
        }
    }

    private String normalizeJson(String json) {
        return json.replace("\r\n", "\n").stripTrailing();
    }

    protected void onConfigReload(T previousConfig, T newConfig) {
        applyDoubleField(newConfig, "minXp", this::setMinXp);
        Number interval = getNumericField(newConfig, "setInterval");
        if (interval != null) {
            setInterval(interval.longValue());
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
            Adapt.verbose("Failed reading config field '" + fieldName + "' for skill " + getName());
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

    public void registerAdvancement(AdaptAdvancement a) {
        cachedAdvancements.add(a);
    }

    public boolean checkValidEntity(EntityType e) {
        if (!e.isAlive() || e.equals(EntityType.PARROT)) {
            return false;
        }
        return !ItemListings.getInvalidDamageableEntities().contains(e);
    }

    protected boolean shouldReturnForPlayer(Player p) {
        try {
            if (p == null) {
                return true;
            }
            if (!p.getClass().getSimpleName().equals("CraftPlayer")) {
                return true;
            }
            Adapt.verbose("Checking " + p.getName() + " for " + getName());
            return !this.isEnabled() || hasBlacklistPermission(p, this) || isWorldBlacklisted(p) || isInCreativeOrSpectator(p) || getPlayer(p) == null;
        } catch (Exception ignored) {
            return true;
        }
    }
    protected void shouldReturnForPlayer(Player p, Runnable r) {
        try {
            if (shouldReturnForPlayer(p)) {
                return;
            }
            r.run();
        } catch (Exception ignored) {
        }
    }

    protected void shouldReturnForPlayer(Player p, Cancellable c, Runnable r) {
        try {
            if (c.isCancelled()) {
                return;
            }
            if (shouldReturnForPlayer(p)) {
                return;
            }
            r.run();
        } catch (Exception ignored) {
        }
    }

    protected boolean shouldReturnForWorld(World world, Skill<?> skill) {
        try {
            return !skill.isEnabled() || AdaptConfig.get().blacklistedWorlds.contains(world.getName());
        } catch (Exception ignored) {
            return true;
        }
    }

    protected boolean isWorldBlacklisted(Player p) {
        return AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName());
    }

    protected boolean isInCreativeOrSpectator(Player p) {
        return !AdaptConfig.get().isXpInCreative() && (p.getGameMode().equals(GameMode.CREATIVE) || p.getGameMode().equals(GameMode.SPECTATOR));
    }

    @Override
    public String getDisplayName() {
        return displayName == null ? Skill.super.getDisplayName() : (C.RESET + "" + C.BOLD + getColor().toString() + getEmojiName() + " " + displayName);
    }

    @Override
    public void onRegisterAdvancements(KList<AdaptAdvancement> advancements) {
        advancements.addAll(cachedAdvancements);
    }

    public AdaptAdvancement buildAdvancements() {
        KList<AdaptAdvancement> a = new KList<>();
        onRegisterAdvancements(a);

        for (Adaptation<?> i : getAdaptations()) {
            a.add(i.buildAdvancements());
        }

        return AdaptAdvancement.builder()
                .background(getAdvancementBackground())
                .key("skill_" + getName())
                .title(displayName)
                .description(getDescription())
                .icon(getIcon())
                .model(getModel())
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
    public void registerAdaptation(Adaptation<?> a) {
        a.setSkill(this);
        adaptations.add(a);
    }

    @Override
    public void unregister() {
        super.unregister();
        adaptations.forEach(Adaptation::unregister);
    }

    @Override
    public abstract void onTick();
}
