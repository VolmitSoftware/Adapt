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
import com.volmit.adapt.util.M;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class BlockingCounterGuard extends SimpleAdaptation<BlockingCounterGuard.Config> {
    public BlockingCounterGuard() {
        super("blocking-counter-guard");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("blocking.counter_guard.description"));
        setDisplayName(Localizer.dLocalize("blocking.counter_guard.name"));
        setIcon(Material.IRON_BARS);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(1000);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SHIELD)
                .key("challenge_blocking_counter_500")
                .title(Localizer.dLocalize("advancement.challenge_blocking_counter_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_blocking_counter_500.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_blocking_counter_500", "blocking.counter-guard.damage-reflected", 500, 500);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SHIELD)
                .key("challenge_blocking_counter_max")
                .title(Localizer.dLocalize("advancement.challenge_blocking_counter_max.title"))
                .description(Localizer.dLocalize("advancement.challenge_blocking_counter_max.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + getMaxStacks(level) + C.GRAY + " " + Localizer.dLocalize("blocking.counter_guard.lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getReflectChance(level), 0) + C.GRAY + " " + Localizer.dLocalize("blocking.counter_guard.lore2"));
        v.addLore(C.GREEN + "+ " + Form.f(getReflectDamage(level)) + C.GRAY + " " + Localizer.dLocalize("blocking.counter_guard.lore3"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled() || !(e.getEntity() instanceof Player defender)) {
            return;
        }

        if (!hasAdaptation(defender) || !hasShield(defender)) {
            return;
        }

        int level = getLevel(defender);
        int stacks = getStorageInt(defender, "counterStacks", 0);

        if (defender.isBlocking()) {
            stacks = Math.min(getMaxStacks(level), stacks + 1);
            setStorage(defender, "counterStacks", stacks);
        }

        if (stacks <= 0 || !M.r(getReflectChance(level))) {
            return;
        }

        Entity source = e.getDamager();
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            source = shooter;
        }

        if (!(source instanceof LivingEntity attacker)) {
            return;
        }

        if (attacker instanceof Player p && !canPVP(defender, p.getLocation())) {
            return;
        }

        if (!(attacker instanceof Player) && !canPVE(defender, attacker.getLocation())) {
            return;
        }

        // Special achievement: reach max stacks and release
        if (stacks >= getMaxStacks(level) && AdaptConfig.get().isAdvancements() && !getPlayer(defender).getData().isGranted("challenge_blocking_counter_max")) {
            getPlayer(defender).getAdvancementHandler().grant("challenge_blocking_counter_max");
        }

        double reflected = getReflectDamage(level) + (stacks * getConfig().damagePerStack);
        attacker.damage(reflected, defender);
        setStorage(defender, "counterStacks", Math.max(0, stacks - getConfig().stackCostOnReflect));
        xp(defender, reflected * getConfig().xpPerReflectedDamage);
        getPlayer(defender).getData().addStat("blocking.counter-guard.damage-reflected", reflected);
    }

    private boolean hasShield(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();
        return (isItem(main) && main.getType() == Material.SHIELD) || (isItem(off) && off.getType() == Material.SHIELD);
    }

    private double getReflectChance(int level) {
        return Math.min(getConfig().maxReflectChance, getConfig().reflectChanceBase + (getLevelPercent(level) * getConfig().reflectChanceFactor));
    }

    private int getMaxStacks(int level) {
        return Math.max(1, (int) Math.round(getConfig().baseStacks + (getLevelPercent(level) * getConfig().stackFactor)));
    }

    private double getReflectDamage(int level) {
        return getConfig().baseReflectDamage + (getLevelPercent(level) * getConfig().reflectDamageFactor);
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
    @ConfigDescription("Blocking builds retaliation stacks that reflect damage back to attackers.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.75;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Base Stacks for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double baseStacks = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Stack Factor for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double stackFactor = 8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Reflect Chance Base for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double reflectChanceBase = 0.08;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Reflect Chance Factor for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double reflectChanceFactor = 0.27;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Reflect Chance for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxReflectChance = 0.6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Base Reflect Damage for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double baseReflectDamage = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Reflect Damage Factor for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double reflectDamageFactor = 3.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Damage Per Stack for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double damagePerStack = 0.28;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Stack Cost On Reflect for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int stackCostOnReflect = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Reflected Damage for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerReflectedDamage = 5.0;
    }
}
