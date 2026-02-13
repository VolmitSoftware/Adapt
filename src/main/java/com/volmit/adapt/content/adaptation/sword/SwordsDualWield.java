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

package com.volmit.adapt.content.adaptation.sword;

import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class SwordsDualWield extends SimpleAdaptation<SwordsDualWield.Config> {
    public SwordsDualWield() {
        super("sword-dual-wield");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("sword.dual_wield.description"));
        setDisplayName(Localizer.dLocalize("sword.dual_wield.name"));
        setIcon(Material.GOLDEN_SWORD);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(1800);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.IRON_SWORD)
                .key("challenge_swords_dual_1k")
                .title(Localizer.dLocalize("advancement.challenge_swords_dual_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_swords_dual_1k.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.DIAMOND_SWORD)
                        .key("challenge_swords_dual_25k")
                        .title(Localizer.dLocalize("advancement.challenge_swords_dual_25k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_swords_dual_25k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_swords_dual_1k", "swords.dual-wield.bonus-damage", 1000, 400);
        registerMilestone("challenge_swords_dual_25k", "swords.dual-wield.bonus-damage", 25000, 1500);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getSameMultiplier(level), 0) + C.GRAY + " " + Localizer.dLocalize("sword.dual_wield.lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getMixedMultiplier(level), 0) + C.GRAY + " " + Localizer.dLocalize("sword.dual_wield.lore2"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled() || !(e.getDamager() instanceof Player p) || !hasAdaptation(p)) {
            return;
        }

        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();
        if (!isItem(main) || !isItem(off) || main.getType() == Material.AIR || off.getType() == Material.AIR) {
            return;
        }

        boolean sameWeapon = main.getType() == off.getType();
        double multiplier = sameWeapon ? getSameMultiplier(getLevel(p)) : getMixedMultiplier(getLevel(p));
        double originalDamage = e.getDamage();
        e.setDamage(originalDamage * multiplier);
        double bonusDamage = e.getDamage() - originalDamage;
        xp(p, e.getDamage() * getConfig().xpPerDamage);
        getPlayer(p).getData().addStat("swords.dual-wield.bonus-damage", bonusDamage);
    }

    private double getSameMultiplier(int level) {
        return getConfig().sameWeaponBase + (getLevelPercent(level) * getConfig().sameWeaponFactor);
    }

    private double getMixedMultiplier(int level) {
        return getConfig().mixedWeaponBase + (getLevelPercent(level) * getConfig().mixedWeaponFactor);
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
    @ConfigDescription("Wield a sword in both hands for increased damage output.")
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
        int initialCost = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Same Weapon Base for the Swords Dual Wield adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double sameWeaponBase = 1.12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Same Weapon Factor for the Swords Dual Wield adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double sameWeaponFactor = 0.43;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Mixed Weapon Base for the Swords Dual Wield adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double mixedWeaponBase = 1.06;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Mixed Weapon Factor for the Swords Dual Wield adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double mixedWeaponFactor = 0.28;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Damage for the Swords Dual Wield adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerDamage = 2.0;
    }
}
