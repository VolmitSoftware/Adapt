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

package art.arcane.adapt.content.adaptation.unarmed;
import art.arcane.volmlib.util.format.Form;

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.math.M;
import art.arcane.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.reflect.registries.Particles;

public class UnarmedSuckerPunch extends SimpleAdaptation<UnarmedSuckerPunch.Config> {
    public UnarmedSuckerPunch() {
        super("unarmed-sucker-punch");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("unarmed.sucker_punch.description"));
        setDisplayName(Localizer.dLocalize("unarmed.sucker_punch.name"));
        setIcon(Material.OBSIDIAN);
        setBaseCost(getConfig().baseCost);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(4944);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.IRON_INGOT)
                .key("challenge_unarmed_sucker_500")
                .title(Localizer.dLocalize("advancement.challenge_unarmed_sucker_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_unarmed_sucker_500.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_unarmed_sucker_500", "unarmed.sucker-punch.sucker-punches", 500, 400);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.DIAMOND)
                .key("challenge_unarmed_knockout")
                .title(Localizer.dLocalize("advancement.challenge_unarmed_knockout.title"))
                .description(Localizer.dLocalize("advancement.challenge_unarmed_knockout.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_unarmed_knockout", "unarmed.sucker-punch.one-punch-kills", 50, 1000);
    }


    @Override
    public void addStats(int level, Element v) {
        double f = getLevelPercent(level);
        double d = getDamage(f);
        v.addLore(C.GREEN + "+ " + Form.pc(d, 0) + C.GRAY + " " + Localizer.dLocalize("unarmed.sucker_punch.lore1"));
        v.addLore(C.GRAY + Localizer.dLocalize("unarmed.sucker_punch.lore2"));
    }

    private double getDamage(double f) {
        return getConfig().baseDamage + (f * getConfig().damageFactor);
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) {
            return;
        }

        if (e.getDamager() instanceof Player p) {
            if (!hasAdaptation(p)) {
                return;
            }
            if (p.getInventory().getItemInMainHand().getType() != Material.AIR && p.getInventory().getItemInOffHand().getType() != Material.AIR) {
                return;
            }
            double factor = getLevelPercent(p);

            if (!p.isSprinting()) {
                return;
            }

            if (factor <= 0) {
                return;
            }

            if (isTool(p.getInventory().getItemInMainHand()) || isTool(p.getInventory().getItemInOffHand())) {
                return;
            }

            e.setDamage(e.getDamage() * getDamage(factor));
            SoundPlayer spw = SoundPlayer.of(e.getEntity().getWorld());
            spw.play(e.getEntity().getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 1.8f);
            spw.play(e.getEntity().getLocation(), Sound.BLOCK_BASALT_BREAK, 1f, 0.6f);
            xp(p, 6.221 * e.getDamage(), "sucker-punch");
            getPlayer(p).getData().addStat("unarmed.sucker-punch.sucker-punches", 1);
            if (e.getDamage() > 5) {
                xp(p, 0.42 * e.getDamage(), "bonus-damage");
                if (areParticlesEnabled()) {
                    e.getEntity().getWorld().spawnParticle(Particle.FLASH, e.getEntity().getLocation(), 1);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (victim.getLastDamageCause() instanceof EntityDamageByEntityEvent dmg
                && dmg.getDamager() instanceof Player p
                && hasAdaptation(p)
                && p.isSprinting()
                && !isTool(p.getInventory().getItemInMainHand())
                && !isTool(p.getInventory().getItemInOffHand())) {
            // One-punch kill: entity was at full health before the killing blow
            if (victim.getMaxHealth() <= dmg.getFinalDamage()) {
                getPlayer(p).getData().addStat("unarmed.sucker-punch.one-punch-kills", 1);
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
    @ConfigDescription("Sprint punches deal extra damage based on your speed.")
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Show Particles for the Unarmed Sucker Punch adaptation.", impact = "True enables this behavior and false disables it.")
        boolean showParticles = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 2;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.225;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Damage for the Unarmed Sucker Punch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double baseDamage = 0.2;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Factor for the Unarmed Sucker Punch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damageFactor = 0.55;
    }
}
