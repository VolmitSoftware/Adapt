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

package art.arcane.adapt.api.skill;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.runtime.AdaptationGate;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.content.item.ItemListings;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.math.M;
import art.arcane.adapt.util.config.ConfigFileSupport;
import art.arcane.volmlib.util.collection.KList;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.UUID;

import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;

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
    private volatile T config;

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
        return Adapt.instance.getDataFile("adapt", "skills", getName() + ".toml");
    }

    protected File getLegacyConfigFile() {
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
        return ConfigFileSupport.load(
                file,
                getLegacyConfigFile(),
                getConfigurationClass(),
                fallback,
                overwriteOnReadFailure,
                "skill:" + getName(),
                "Created missing skill config [adapt/skills/" + getName() + ".toml] from defaults."
        );
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
                onConfigReload(null, local);
                config = local;
                Adapt.warn("Falling back to in-memory defaults for skill config " + getName() + ".");
            }
        }

        return local;
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
            Adapt.verbose("Checking " + p.getName() + " for " + getName());
            return AdaptationGate.shouldSkipPlayer(p, this, getPlayer(p) != null);
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
            return AdaptationGate.shouldSkipWorld(world, skill);
        } catch (Exception ignored) {
            return true;
        }
    }

    protected boolean isWorldBlacklisted(Player p) {
        return AdaptationGate.isWorldBlacklisted(p);
    }

    protected boolean isInCreativeOrSpectator(Player p) {
        return AdaptationGate.isInCreativeOrSpectator(p);
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

    protected void registerMilestone(String advancementKey, String stat, double goal, double reward) {
        registerStatTracker(AdaptStatTracker.builder()
                .advancement(advancementKey)
                .stat(stat)
                .goal(goal)
                .reward(reward)
                .build());
    }

    protected void checkStatTrackersForOnlinePlayers() {
        for (AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
            Player player = adaptPlayer.getPlayer();
            if (shouldReturnForPlayer(player)) {
                continue;
            }
            checkStatTrackers(adaptPlayer);
        }
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
