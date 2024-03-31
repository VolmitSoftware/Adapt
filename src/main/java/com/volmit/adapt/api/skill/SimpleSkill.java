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

import art.arcane.amulet.io.FileWatcher;
import com.google.gson.Gson;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.api.tick.TickedObject;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.item.ItemListings;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.IO;
import com.volmit.adapt.util.J;
import com.volmit.adapt.util.JSONObject;
import eu.endercentral.crazy_advancements.advancement.AdvancementVisibility;
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
import java.util.ArrayList;
import java.util.List;
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
    private List<Adaptation<?>> adaptations;
    private List<AdaptStatTracker> statTrackers;
    private List<AdaptAdvancement> cachedAdvancements;
    private String advancementBackground;
    private List<AdaptRecipe> recipes;
    private Class<T> configType;
    private T config;

    public SimpleSkill(String name, String emojiName) {
        super("skill", UUID.randomUUID() + "-skill-" + name, 50);
        statTrackers = new ArrayList<>();
        recipes = new ArrayList<>();
        cachedAdvancements = new ArrayList<>();
        this.emojiName = emojiName;
        adaptations = new ArrayList<>();
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
        File file = Adapt.instance.getDataFile("adapt", "skills", getName() + ".json");
        FileWatcher fw = new FileWatcher(file);
        fw.checkModified();
        J.a(() -> {
            fw.checkModified();
            Adapt.instance.getTicker().register(new TickedObject("config", "config-" + getName(), 1000) {
                @Override
                public void onTick() {
                    if (fw.checkModified() && file.exists()) {
                        config = null;
                        getConfig();
                        Adapt.info("Hotloaded " + file.getPath());
                        Adapt.hotloaded();
                        fw.checkModified();
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
                File l = Adapt.instance.getDataFile("adapt", "skills", getName() + ".json");

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
            return !this.isEnabled() || hasBlacklistPermission(p, this) || isWorldBlacklisted(p) || isInCreativeOrSpectator(p);
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

    protected boolean shouldReturnForWorld(World world, Skill skill) {
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
    public void onRegisterAdvancements(List<AdaptAdvancement> advancements) {
        advancements.addAll(cachedAdvancements);
    }

    public AdaptAdvancement buildAdvancements() {
        List<AdaptAdvancement> a = new ArrayList<>();
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
                .children(a)
                .visibility(AdvancementVisibility.HIDDEN)
                .build();
    }

    @Override
    public void registerStatTracker(AdaptStatTracker tracker) {
        getStatTrackers().add(tracker);
    }

    @Override
    public List<AdaptStatTracker> getStatTrackers() {
        return statTrackers;
    }

    @Override
    public void registerAdaptation(Adaptation<?> a) {
        if (!a.isEnabled()) {
            return;
        }

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
