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

package com.volmit.adapt.content.adaptation.unarmed;

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
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UnarmedBatteringCharge extends SimpleAdaptation<UnarmedBatteringCharge.Config> {
    private final Map<UUID, Boolean> primedState = new HashMap<>();

    public UnarmedBatteringCharge() {
        super("unarmed-battering-charge");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("unarmed.battering_charge.description"));
        setDisplayName(Localizer.dLocalize("unarmed.battering_charge.name"));
        setIcon(Material.BLAZE_ROD);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(8);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.IRON_INGOT)
                .key("challenge_unarmed_charge_300")
                .title(Localizer.dLocalize("advancement.challenge_unarmed_charge_300.title"))
                .description(Localizer.dLocalize("advancement.challenge_unarmed_charge_300.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_unarmed_charge_300", "unarmed.battering-charge.charges", 300, 400);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.DIAMOND)
                .key("challenge_unarmed_charge_kills_100")
                .title(Localizer.dLocalize("advancement.challenge_unarmed_charge_kills_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_unarmed_charge_kills_100.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_unarmed_charge_kills_100", "unarmed.battering-charge.charge-kills", 100, 1000);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getDamageBonus(level)) + C.GRAY + " " + Localizer.dLocalize("unarmed.battering_charge.lore1"));
        v.addLore(C.GREEN + "+ " + Form.f(getKnockback(level)) + C.GRAY + " " + Localizer.dLocalize("unarmed.battering_charge.lore2"));
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("unarmed.battering_charge.lore3"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled() || !(e.getDamager() instanceof Player p) || !hasAdaptation(p) || p.isInsideVehicle()) {
            return;
        }

        if (!isChargeLoadout(p) || !p.isSprinting()) {
            return;
        }

        ItemStack cooldownItem = getCooldownItem(p);
        if (cooldownItem != null && p.hasCooldown(cooldownItem.getType())) {
            return;
        }

        Vector v = p.getVelocity();
        if (v.lengthSquared() < getConfig().minimumVelocitySquared) {
            return;
        }

        int level = getLevel(p);
        e.setDamage(e.getDamage() + getDamageBonus(level));
        Entity target = e.getEntity();
        target.setVelocity(target.getVelocity().add(p.getLocation().getDirection().normalize().multiply(getKnockback(level))));

        if (cooldownItem != null) {
            p.setCooldown(cooldownItem.getType(), getCooldownTicks(level));
        }

        SoundPlayer sp = SoundPlayer.of(p.getWorld());
        sp.play(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.95f);
        sp.play(target.getLocation(), Sound.ENTITY_ZOGLIN_ATTACK, 0.5f, 0.8f);
        if (areParticlesEnabled()) {
            target.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation().add(0, 0.8, 0), 1, 0.06, 0.06, 0.06, 0.02);
        }
        if (areParticlesEnabled()) {
            target.getWorld().spawnParticle(Particle.CLOUD, target.getLocation().add(0, 0.6, 0), 14, 0.25, 0.25, 0.25, 0.05);
        }
        primedState.put(p.getUniqueId(), false);
        xp(p, e.getDamage() * getConfig().xpPerDamage);
        getPlayer(p).getData().addStat("unarmed.battering-charge.charges", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (victim.getLastDamageCause() instanceof EntityDamageByEntityEvent dmg
                && dmg.getDamager() instanceof Player p
                && hasAdaptation(p)
                && isChargeLoadout(p)) {
            getPlayer(p).getData().addStat("unarmed.battering-charge.charge-kills", 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
        primedState.remove(e.getPlayer().getUniqueId());
    }

    private boolean isChargeLoadout(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();
        boolean fists = (!isItem(main) || main.getType() == Material.AIR) && (!isItem(off) || off.getType() == Material.AIR);
        boolean shieldLoadout = (isItem(main) && main.getType() == Material.SHIELD) || (isItem(off) && off.getType() == Material.SHIELD);
        return fists || shieldLoadout;
    }

    private ItemStack getCooldownItem(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();
        if (isItem(main) && main.getType() == Material.SHIELD) {
            return main;
        }

        if (isItem(off) && off.getType() == Material.SHIELD) {
            return off;
        }

        return null;
    }

    private boolean isChargeReady(Player p) {
        if (p.isInsideVehicle() || !isChargeLoadout(p) || !p.isSprinting()) {
            return false;
        }

        ItemStack cooldownItem = getCooldownItem(p);
        if (cooldownItem != null && p.hasCooldown(cooldownItem.getType())) {
            return false;
        }

        Vector v = p.getVelocity();
        return v.lengthSquared() >= getConfig().minimumVelocitySquared;
    }

    private double getDamageBonus(int level) {
        return getConfig().damageBase + (getLevelPercent(level) * getConfig().damageFactor);
    }

    private double getKnockback(int level) {
        return getConfig().knockbackBase + (getLevelPercent(level) * getConfig().knockbackFactor);
    }

    private int getCooldownTicks(int level) {
        return Math.max(10, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
    }

    @Override
    public void onTick() {
        for (com.volmit.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
            Player p = adaptPlayer.getPlayer();
            if (!hasAdaptation(p)) {
                primedState.remove(p.getUniqueId());
                continue;
            }

            boolean primed = isChargeReady(p);
            boolean wasPrimed = primedState.getOrDefault(p.getUniqueId(), false);

            if (primed) {
                if (areParticlesEnabled()) {
                    p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.2, 0), 2, 0.2, 0.05, 0.2, 0.02);
                }
                if (!wasPrimed) {
                    SoundPlayer.of(p.getWorld()).play(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.55f, 1.15f);
                    if (areParticlesEnabled()) {
                        p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1.0, 0), 8, 0.2, 0.3, 0.2, 0.1);
                    }
                }
            }

            primedState.put(p.getUniqueId(), primed);
        }
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
    @ConfigDescription("Sprint into enemies with fists or a shield to deal impact damage.")
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
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage Base for the Unarmed Battering Charge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageBase = 0.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage Factor for the Unarmed Battering Charge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageFactor = 4.2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Knockback Base for the Unarmed Battering Charge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double knockbackBase = 0.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Knockback Factor for the Unarmed Battering Charge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double knockbackFactor = 1.2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Unarmed Battering Charge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksBase = 80;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Unarmed Battering Charge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksFactor = 50;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Minimum Velocity Squared for the Unarmed Battering Charge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double minimumVelocitySquared = 0.18;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Damage for the Unarmed Battering Charge adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerDamage = 3.3;
    }
}
