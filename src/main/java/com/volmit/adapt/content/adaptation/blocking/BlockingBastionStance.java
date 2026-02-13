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
import org.bukkit.Sound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class BlockingBastionStance extends SimpleAdaptation<BlockingBastionStance.Config> {
    public BlockingBastionStance() {
        super("blocking-bastion-stance");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("blocking.bastion_stance.description"));
        setDisplayName(Localizer.dLocalize("blocking.bastion_stance.name"));
        setIcon(Material.SHIELD);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(2000);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SHIELD)
                .key("challenge_blocking_bastion_500")
                .title(Localizer.dLocalize("advancement.challenge_blocking_bastion_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_blocking_bastion_500.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_blocking_bastion_500", "blocking.bastion-stance.projectiles-softened", 500, 500);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SHIELD)
                .key("challenge_blocking_bastion_10")
                .title(Localizer.dLocalize("advancement.challenge_blocking_bastion_10.title"))
                .description(Localizer.dLocalize("advancement.challenge_blocking_bastion_10.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getKnockbackReduction(level), 0) + C.GRAY + " " + Localizer.dLocalize("blocking.bastion_stance.lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getProjectileReduction(level), 0) + C.GRAY + " " + Localizer.dLocalize("blocking.bastion_stance.lore2"));
        v.addLore(C.GREEN + "+ " + Form.pc(getProjectileNegateChance(level), 0) + C.GRAY + " " + Localizer.dLocalize("blocking.bastion_stance.lore3"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled() || !(e.getEntity() instanceof Player defender) || !isBastionStance(defender)) {
            return;
        }

        if (!(e.getDamager() instanceof Projectile)) {
            return;
        }

        int level = getLevel(defender);

        // Track session counter for special achievement
        int sessionCount = getStorageInt(defender, "bastionSessionCount", 0) + 1;
        setStorage(defender, "bastionSessionCount", sessionCount);
        if (sessionCount >= 10 && AdaptConfig.get().isAdvancements() && !getPlayer(defender).getData().isGranted("challenge_blocking_bastion_10")) {
            getPlayer(defender).getAdvancementHandler().grant("challenge_blocking_bastion_10");
        }

        if (ThreadLocalRandom.current().nextDouble() <= getProjectileNegateChance(level)) {
            e.setCancelled(true);
            SoundPlayer.of(defender.getWorld()).play(defender.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.9f);
            xp(defender, getConfig().xpOnNegate);
            getPlayer(defender).getData().addStat("blocking.bastion-stance.projectiles-softened", 1);
            return;
        }

        e.setDamage(Math.max(0, e.getDamage() * (1D - getProjectileReduction(level))));
        SoundPlayer.of(defender.getWorld()).play(defender.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.75f, 0.75f);
        xp(defender, e.getDamage() * getConfig().xpPerMitigatedDamage);
        getPlayer(defender).getData().addStat("blocking.bastion-stance.projectiles-softened", 1);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void on(PlayerVelocityEvent e) {
        Player p = e.getPlayer();
        if (!isBastionStance(p)) {
            return;
        }

        double factor = 1D - getKnockbackReduction(getLevel(p));
        Vector v = e.getVelocity();
        e.setVelocity(new Vector(v.getX() * factor, v.getY(), v.getZ() * factor));
    }

    private boolean isBastionStance(Player p) {
        return hasAdaptation(p) && p.isBlocking() && p.isSneaking() && hasShield(p);
    }

    private boolean hasShield(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();
        return (isItem(main) && main.getType() == Material.SHIELD) || (isItem(off) && off.getType() == Material.SHIELD);
    }

    private double getKnockbackReduction(int level) {
        return Math.min(getConfig().maxKnockbackReduction, getConfig().knockbackReductionBase + (getLevelPercent(level) * getConfig().knockbackReductionFactor));
    }

    private double getProjectileReduction(int level) {
        return Math.min(getConfig().maxProjectileReduction, getConfig().projectileReductionBase + (getLevelPercent(level) * getConfig().projectileReductionFactor));
    }

    private double getProjectileNegateChance(int level) {
        return Math.min(getConfig().maxProjectileNegateChance, getConfig().projectileNegateChanceBase + (getLevelPercent(level) * getConfig().projectileNegateChanceFactor));
    }

    @Override
    public void onTick() {
        for (com.volmit.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
            Player p = adaptPlayer.getPlayer();
            if (hasAdaptation(p) && !isBastionStance(p)) {
                setStorage(p, "bastionSessionCount", 0);
            }
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
    @ConfigDescription("Sneak-block with a shield to brace against knockback and soften projectiles.")
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
        double costFactor = 0.68;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Knockback Reduction Base for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double knockbackReductionBase = 0.18;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Knockback Reduction Factor for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double knockbackReductionFactor = 0.52;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Knockback Reduction for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxKnockbackReduction = 0.75;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Projectile Reduction Base for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double projectileReductionBase = 0.12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Projectile Reduction Factor for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double projectileReductionFactor = 0.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Projectile Reduction for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxProjectileReduction = 0.7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Projectile Negate Chance Base for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double projectileNegateChanceBase = 0.05;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Projectile Negate Chance Factor for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double projectileNegateChanceFactor = 0.22;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Projectile Negate Chance for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxProjectileNegateChance = 0.35;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Mitigated Damage for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerMitigatedDamage = 2.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp On Negate for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpOnNegate = 8.0;
    }
}
