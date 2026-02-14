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

package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.volmlib.util.format.Form;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public class NetherGhastWard extends SimpleAdaptation<NetherGhastWard.Config> {
    public NetherGhastWard() {
        super("nether-ghast-ward");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("nether.ghast_ward.description"));
        setDisplayName(Localizer.dLocalize("nether.ghast_ward.name"));
        setIcon(Material.GHAST_TEAR);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(2000);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.GHAST_TEAR)
                .key("challenge_nether_ghast_500")
                .title(Localizer.dLocalize("advancement.challenge_nether_ghast_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_nether_ghast_500.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_nether_ghast_500", "nether.ghast-ward.damage-reduced", 500, 400);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getGhastProjectileReduction(level), 0) + C.GRAY + " " + Localizer.dLocalize("nether.ghast_ward.lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getExplosionReduction(level), 0) + C.GRAY + " " + Localizer.dLocalize("nether.ghast_ward.lore2"));
        v.addLore(C.GREEN + "+ " + Form.pc(getWitherSkeletonReduction(level), 0) + C.GRAY + " " + Localizer.dLocalize("nether.ghast_ward.lore3"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled() || !(e.getEntity() instanceof Player p) || !hasAdaptation(p) || !isNether(p)) {
            return;
        }

        int level = getLevel(p);
        if (e.getDamager() instanceof Fireball fireball && fireball.getShooter() instanceof Ghast) {
            double before = e.getDamage();
            e.setDamage(Math.max(0, e.getDamage() * (1D - getGhastProjectileReduction(level))));
            p.setFireTicks(Math.min(p.getFireTicks(), getMaxFireTicks(level)));
            xp(p, e.getDamage() * getConfig().xpPerMitigatedDamage);
            int reduced = (int) Math.round(before - e.getDamage());
            if (reduced > 0) {
                getPlayer(p).getData().addStat("nether.ghast-ward.damage-reduced", reduced);
            }
            return;
        }

        if (e.getDamager() instanceof AbstractArrow arrow && arrow.getShooter() instanceof WitherSkeleton) {
            double before = e.getDamage();
            e.setDamage(Math.max(0, e.getDamage() * (1D - getWitherSkeletonReduction(level))));
            xp(p, e.getDamage() * getConfig().xpPerMitigatedDamage);
            int reduced = (int) Math.round(before - e.getDamage());
            if (reduced > 0) {
                getPlayer(p).getData().addStat("nether.ghast-ward.damage-reduced", reduced);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void on(EntityDamageEvent e) {
        if (e.isCancelled() || e instanceof EntityDamageByEntityEvent || !(e.getEntity() instanceof Player p) || !hasAdaptation(p) || !isNether(p)) {
            return;
        }

        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION && e.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return;
        }

        double before = e.getDamage();
        e.setDamage(Math.max(0, e.getDamage() * (1D - getExplosionReduction(getLevel(p)))));
        xp(p, e.getDamage() * getConfig().xpPerMitigatedDamage);
        int reduced = (int) Math.round(before - e.getDamage());
        if (reduced > 0) {
            getPlayer(p).getData().addStat("nether.ghast-ward.damage-reduced", reduced);
        }
    }

    private boolean isNether(Player p) {
        return p.getWorld().getEnvironment().name().contains("NETHER");
    }

    private double getGhastProjectileReduction(int level) {
        return Math.min(getConfig().maxGhastProjectileReduction, getConfig().ghastProjectileReductionBase + (getLevelPercent(level) * getConfig().ghastProjectileReductionFactor));
    }

    private double getExplosionReduction(int level) {
        return Math.min(getConfig().maxExplosionReduction, getConfig().explosionReductionBase + (getLevelPercent(level) * getConfig().explosionReductionFactor));
    }

    private double getWitherSkeletonReduction(int level) {
        return Math.min(getConfig().maxWitherSkeletonReduction, getConfig().witherSkeletonReductionBase + (getLevelPercent(level) * getConfig().witherSkeletonReductionFactor));
    }

    private int getMaxFireTicks(int level) {
        return Math.max(0, (int) Math.round(getConfig().maxFireTicksBase - (getLevelPercent(level) * getConfig().maxFireTicksFactor)));
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
    @ConfigDescription("Harden against ghast blasts and wither-skeleton pressure in the Nether.")
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 6;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.73;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Ghast Projectile Reduction Base for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double ghastProjectileReductionBase = 0.14;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Ghast Projectile Reduction Factor for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double ghastProjectileReductionFactor = 0.54;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Ghast Projectile Reduction for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxGhastProjectileReduction = 0.8;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Explosion Reduction Base for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double explosionReductionBase = 0.08;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Explosion Reduction Factor for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double explosionReductionFactor = 0.42;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Explosion Reduction for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxExplosionReduction = 0.65;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Wither Skeleton Reduction Base for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double witherSkeletonReductionBase = 0.1;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Wither Skeleton Reduction Factor for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double witherSkeletonReductionFactor = 0.4;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Wither Skeleton Reduction for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxWitherSkeletonReduction = 0.55;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Fire Ticks Base for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxFireTicksBase = 80;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Fire Ticks Factor for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxFireTicksFactor = 70;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Mitigated Damage for the Nether Ghast Ward adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerMitigatedDamage = 4.2;
    }
}
