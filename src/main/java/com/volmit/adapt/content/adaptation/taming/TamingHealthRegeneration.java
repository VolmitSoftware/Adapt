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

package com.volmit.adapt.content.adaptation.taming;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.version.Version;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.*;
import com.volmit.adapt.util.config.ConfigDescription;
import com.volmit.adapt.util.reflect.registries.Attributes;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.bukkit.Particle.HEART;

public class TamingHealthRegeneration extends SimpleAdaptation<TamingHealthRegeneration.Config> {
    private final Map<UUID, Long> lastDamage = new HashMap<>();

    public TamingHealthRegeneration() {
        super("tame-health-regeneration");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("taming.regeneration.description"));
        setDisplayName(Localizer.dLocalize("taming.regeneration.name"));
        setIcon(Material.GOLDEN_APPLE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setInterval(1033);
        setCostFactor(getConfig().costFactor);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.GLISTERING_MELON_SLICE)
                .key("challenge_taming_regen_1k")
                .title(Localizer.dLocalize("advancement.challenge_taming_regen_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_taming_regen_1k.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_taming_regen_1k").goal(1000).stat("taming.health-regen.health-regened").reward(400).build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getRegenSpeed(level), 0) + C.GRAY + " " + Localizer.dLocalize("taming.regeneration.lore1"));
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getEntity() instanceof Tameable tam
                && tam.getOwner() instanceof Player p
                && hasAdaptation(p)) {
            if (lastDamage.containsKey(tam.getUniqueId())) {
                Adapt.verbose("Tamed Entity " + tam.getUniqueId() + " last damaged " + (M.ms() - lastDamage.get(tam.getUniqueId())) + "ms ago");
                return;
            }
            var attribute = Version.get().getAttribute(tam, Attributes.GENERIC_MAX_HEALTH);
            double mh = attribute == null ? tam.getHealth() : attribute.getValue();
            if (tam.isTamed() && tam.getOwner() instanceof Player && tam.getHealth() < mh) {
                Adapt.verbose("Successfully healed tamed entity " + tam.getUniqueId());
                int level = getLevel(p);
                if (level > 0) {
                    Adapt.verbose("[PRE] Current Health: " + tam.getHealth() + " Max Health: " + mh);
                    tam.addPotionEffect(PotionEffectType.REGENERATION.createEffect(25 * getLevel(p), 3));
                    getPlayer(p).getData().addStat("taming.health-regen.health-regened", 1);

                    if (getConfig().showParticles) {
                        Adapt.verbose("Healing tamed entity " + tam.getUniqueId() + " with particles");
                        tam.getWorld().spawnParticle(HEART, tam.getLocation().add(0, 1, 0), 2 * p.getLevel());
                    } else {
                        Adapt.verbose("Healing tamed entity " + tam.getUniqueId() + " without particles");
                    }
                }
            }
            lastDamage.put(e.getEntity().getUniqueId(), M.ms());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDeathEvent e) {
        lastDamage.remove(e.getEntity().getUniqueId());
    }


    private double getRegenSpeed(int level) {
        return ((getLevelPercent(level) * (getLevelPercent(level)) * getConfig().regenFactor) + getConfig().regenBase);
    }

    @Override
    public void onTick() {
        lastDamage.entrySet().removeIf(i -> M.ms() - i.getValue() > 8000);
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    @ConfigDescription("Increase your tamed animal regeneration rate.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Taming Health Regeneration adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Regen Factor for the Taming Health Regeneration adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double regenFactor = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Regen Base for the Taming Health Regeneration adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double regenBase = 1;
    }
}
