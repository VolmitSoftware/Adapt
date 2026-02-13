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

package com.volmit.adapt.content.adaptation.ranged;

import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RangedPiercing extends SimpleAdaptation<RangedPiercing.Config> {
    private final Map<UUID, Integer> arrowHitCounts = new HashMap<>();

    public RangedPiercing() {
        super("ranged-piercing");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("ranged.arrow_piercing.description"));
        setDisplayName(Localizer.dLocalize("ranged.arrow_piercing.name"));
        setIcon(Material.FLETCHING_TABLE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInterval(4791);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SPECTRAL_ARROW)
                .key("challenge_ranged_piercing_500")
                .title(Localizer.dLocalize("advancement.challenge_ranged_piercing_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_ranged_piercing_500.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SPECTRAL_ARROW)
                .key("challenge_ranged_piercing_4")
                .title(Localizer.dLocalize("advancement.challenge_ranged_piercing_4.title"))
                .description(Localizer.dLocalize("advancement.challenge_ranged_piercing_4.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_ranged_piercing_500", "ranged.piercing.extra-hits", 500, 400);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + level + C.GRAY + " " + Localizer.dLocalize("ranged.arrow_piercing.lore1"));
    }

    @EventHandler
    public void on(ProjectileLaunchEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getEntity().getShooter() instanceof Player p) {
            if (e.getEntity() instanceof AbstractArrow a) {
                xp(p, 5);
                if (hasAdaptation(p)) {
                    a.setPierceLevel(((AbstractArrow) e.getEntity()).getPierceLevel() + getLevel(p));
                }
            }
        }
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getDamager() instanceof Projectile projectile && projectile instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player p && hasAdaptation(p)) {
            if (arrow.getPierceLevel() > 0) {
                UUID arrowId = arrow.getUniqueId();
                int hits = arrowHitCounts.getOrDefault(arrowId, 0) + 1;
                arrowHitCounts.put(arrowId, hits);
                if (hits > 1) {
                    getPlayer(p).getData().addStat("ranged.piercing.extra-hits", 1);
                }
                if (hits >= 4 && AdaptConfig.get().isAdvancements() && !getPlayer(p).getData().isGranted("challenge_ranged_piercing_4")) {
                    getPlayer(p).getAdvancementHandler().grant("challenge_ranged_piercing_4");
                }
            }
        }
    }

    @Override
    public void onTick() {

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
    @ConfigDescription("Projectiles pierce through multiple targets.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.5;
    }
}
