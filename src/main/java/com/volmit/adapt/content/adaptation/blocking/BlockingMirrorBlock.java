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

package com.volmit.adapt.content.adaptation.blocking;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class BlockingMirrorBlock extends SimpleAdaptation<BlockingMirrorBlock.Config> {
    private static final String REFLECTED_META = "adapt-mirror-reflected";
    private static final String DAMAGE_FACTOR_META = "adapt-mirror-damage-factor";

    public BlockingMirrorBlock() {
        super("blocking-mirror-block");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("blocking.mirror_block.description"));
        setDisplayName(Localizer.dLocalize("blocking.mirror_block.name"));
        setIcon(Material.LIGHT_WEIGHTED_PRESSURE_PLATE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(1200);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SHIELD)
                .key("challenge_blocking_mirror_100")
                .title(Localizer.dLocalize("advancement.challenge_blocking_mirror_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_blocking_mirror_100.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_blocking_mirror_100", "blocking.mirror-block.projectiles-reflected", 100, 500);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SHIELD)
                .key("challenge_blocking_mirror_3in5")
                .title(Localizer.dLocalize("advancement.challenge_blocking_mirror_3in5.title"))
                .description(Localizer.dLocalize("advancement.challenge_blocking_mirror_3in5.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getReflectChance(level), 0) + C.GRAY + " " + Localizer.dLocalize("blocking.mirror_block.lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getReflectedDamageFactor(level), 0) + C.GRAY + " " + Localizer.dLocalize("blocking.mirror_block.lore2"));
        v.addLore(C.YELLOW + "* " + Form.duration(getReflectCooldownMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("blocking.mirror_block.lore3"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled() || !(e.getDamager() instanceof Projectile projectile)) {
            return;
        }

        applyReflectedDamageModifier(e, projectile);

        if (!(e.getEntity() instanceof Player defender) || !isMirrorReady(defender) || projectile.hasMetadata(REFLECTED_META)) {
            return;
        }

        int level = getLevel(defender);
        long now = System.currentTimeMillis();
        long next = getStorageLong(defender, "mirrorBlockNext", 0L);
        if (next > now) {
            return;
        }

        if (ThreadLocalRandom.current().nextDouble() > getReflectChance(level)) {
            return;
        }

        e.setCancelled(true);
        reflectProjectile(defender, projectile, level);
        setStorage(defender, "mirrorBlockNext", now + getReflectCooldownMillis(level));

        SoundPlayer sp = SoundPlayer.of(defender.getWorld());
        sp.play(defender.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1.35f);
        sp.play(defender.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.8f, 0.8f);
        if (areParticlesEnabled()) {
            defender.spawnParticle(Particle.CRIT, defender.getLocation().add(0, 1, 0), 20, 0.35, 0.3, 0.35, 0.08);
        }
        xp(defender, getConfig().xpOnReflect);
        getPlayer(defender).getData().addStat("blocking.mirror-block.projectiles-reflected", 1);

        // Special achievement: reflect 3 projectiles within 5 seconds
        long windowStart = getStorageLong(defender, "mirrorWindowStart", 0L);
        int windowCount = getStorageInt(defender, "mirrorWindowCount", 0);
        if (now - windowStart > 5000L) {
            windowStart = now;
            windowCount = 1;
        } else {
            windowCount++;
        }
        setStorage(defender, "mirrorWindowStart", windowStart);
        setStorage(defender, "mirrorWindowCount", windowCount);
        if (windowCount >= 3 && AdaptConfig.get().isAdvancements() && !getPlayer(defender).getData().isGranted("challenge_blocking_mirror_3in5")) {
            getPlayer(defender).getAdvancementHandler().grant("challenge_blocking_mirror_3in5");
        }
    }

    private void applyReflectedDamageModifier(EntityDamageByEntityEvent e, Projectile projectile) {
        if (!(projectile.getShooter() instanceof Player shooter) || !projectile.hasMetadata(DAMAGE_FACTOR_META)) {
            return;
        }

        if (e.getEntity() instanceof Player victim) {
            if (!canPVP(shooter, victim.getLocation())) {
                return;
            }
        } else if (!canPVE(shooter, e.getEntity().getLocation())) {
            return;
        }

        double factor = getMetadataDouble(projectile, DAMAGE_FACTOR_META, 1D);
        e.setDamage(e.getDamage() * factor);
    }

    private void reflectProjectile(Player defender, Projectile projectile, int level) {
        Vector incoming = projectile.getVelocity().clone();
        Vector reflected = incoming.multiply(-Math.max(0.01, getReflectVelocityFactor(level)));
        if (reflected.lengthSquared() < getConfig().minReflectedVelocitySquared) {
            reflected = defender.getEyeLocation().getDirection().normalize().multiply(getConfig().fallbackReflectedSpeed);
        }

        projectile.teleport(defender.getEyeLocation().add(defender.getEyeLocation().getDirection().multiply(0.55)));
        projectile.setShooter(defender);
        projectile.setVelocity(reflected);
        projectile.setMetadata(REFLECTED_META, new FixedMetadataValue(Adapt.instance, true));
        projectile.setMetadata(DAMAGE_FACTOR_META, new FixedMetadataValue(Adapt.instance, getReflectedDamageFactor(level)));
    }

    private boolean isMirrorReady(Player p) {
        return hasAdaptation(p) && p.isBlocking() && hasShield(p);
    }

    private boolean hasShield(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();
        return (isItem(main) && main.getType() == Material.SHIELD) || (isItem(off) && off.getType() == Material.SHIELD);
    }

    private double getMetadataDouble(Projectile projectile, String key, double fallback) {
        for (MetadataValue value : projectile.getMetadata(key)) {
            if (value.getOwningPlugin() == Adapt.instance) {
                return value.asDouble();
            }
        }

        return fallback;
    }

    private double getReflectChance(int level) {
        return Math.min(getConfig().maxReflectChance, getConfig().reflectChanceBase + (getLevelPercent(level) * getConfig().reflectChanceFactor));
    }

    private double getReflectedDamageFactor(int level) {
        return Math.min(getConfig().maxReflectedDamageFactor,
                getConfig().reflectedDamageFactorBase + (getLevelPercent(level) * getConfig().reflectedDamageFactorIncrease));
    }

    private double getReflectVelocityFactor(int level) {
        return Math.min(getConfig().maxReflectVelocityFactor, getConfig().reflectVelocityFactorBase + (getLevelPercent(level) * getConfig().reflectVelocityFactor));
    }

    private long getReflectCooldownMillis(int level) {
        return Math.max(100L, Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
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
    @ConfigDescription("Blocking with a shield can reflect incoming projectiles at reduced force and damage.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Reflect Chance Base for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double reflectChanceBase = 0.1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Reflect Chance Factor for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double reflectChanceFactor = 0.35;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Reflect Chance for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxReflectChance = 0.7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Reflected Damage Factor Base for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double reflectedDamageFactorBase = 0.45;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Reflected Damage Factor Increase for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double reflectedDamageFactorIncrease = 0.35;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Reflected Damage Factor for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxReflectedDamageFactor = 0.95;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Reflect Velocity Factor Base for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double reflectVelocityFactorBase = 0.42;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Reflect Velocity Factor for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double reflectVelocityFactor = 0.45;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Reflect Velocity Factor for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxReflectVelocityFactor = 1.1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownMillisBase = 2000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownMillisFactor = 1200;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Min Reflected Velocity Squared for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double minReflectedVelocitySquared = 0.08;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Fallback Reflected Speed for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double fallbackReflectedSpeed = 0.95;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp On Reflect for the Blocking Mirror Block adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpOnReflect = 8;
    }
}
