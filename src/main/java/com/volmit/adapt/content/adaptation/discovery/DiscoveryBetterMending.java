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

package com.volmit.adapt.content.adaptation.discovery;

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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

public class DiscoveryBetterMending extends SimpleAdaptation<DiscoveryBetterMending.Config> {
    public DiscoveryBetterMending() {
        super("discovery-better-mending");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("discovery.better_mending.description"));
        setDisplayName(Localizer.dLocalize("discovery.better_mending.name"));
        setIcon(Material.EXPERIENCE_BOTTLE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(2400);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ANVIL)
                .key("challenge_discovery_mending_10k")
                .title(Localizer.dLocalize("advancement.challenge_discovery_mending_10k.title"))
                .description(Localizer.dLocalize("advancement.challenge_discovery_mending_10k.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.ENCHANTED_GOLDEN_APPLE)
                        .key("challenge_discovery_mending_100k")
                        .title(Localizer.dLocalize("advancement.challenge_discovery_mending_100k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_discovery_mending_100k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder()
                .advancement("challenge_discovery_mending_10k")
                .goal(10000)
                .stat("discovery.better-mending.durability-restored")
                .reward(400)
                .build());
        registerStatTracker(AdaptStatTracker.builder()
                .advancement("challenge_discovery_mending_100k")
                .goal(100000)
                .stat("discovery.better-mending.durability-restored")
                .reward(1500)
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getRepairPerXp(level)) + C.GRAY + " " + Localizer.dLocalize("discovery.better_mending.lore1"));
        v.addLore(C.GREEN + "+ " + getMaxXpSpend(level) + C.GRAY + " " + Localizer.dLocalize("discovery.better_mending.lore2"));
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("discovery.better_mending.lore3"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerInteractEvent e) {
        if (e.isCancelled() || e.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = e.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player p = e.getPlayer();
        if (!hasAdaptation(p) || !p.isSneaking()) {
            return;
        }

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!canMend(hand) || p.hasCooldown(hand.getType())) {
            return;
        }

        Damageable damageable = (Damageable) hand.getItemMeta();
        if (damageable == null || damageable.getDamage() <= 0) {
            return;
        }

        int level = getLevel(p);
        int availableXp = getTotalExp(p);
        if (availableXp <= 0) {
            return;
        }

        double repairPerXp = getRepairPerXp(level);
        int maxXpSpend = Math.min(getMaxXpSpend(level), availableXp);
        int currentDamage = damageable.getDamage();
        int xpNeeded = (int) Math.ceil(currentDamage / repairPerXp);
        int xpSpent = Math.min(maxXpSpend, xpNeeded);
        if (xpSpent <= 0) {
            return;
        }

        int repaired = Math.max(1, (int) Math.round(xpSpent * repairPerXp));
        int newDamage = Math.max(0, currentDamage - repaired);

        takeExp(p, xpSpent, true);
        damageable.setDamage(newDamage);
        hand.setItemMeta(damageable);
        p.getInventory().setItemInMainHand(hand);
        p.setCooldown(hand.getType(), getCooldownTicks(level));
        e.setCancelled(true);

        SoundPlayer sp = SoundPlayer.of(p.getWorld());
        sp.play(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.45f);
        if (newDamage <= 0) {
            sp.play(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.65f);
        }

        xp(p, Math.max(1D, (currentDamage - newDamage) * getConfig().skillXpPerDurability));
        getPlayer(p).getData().addStat("discovery.better-mending.durability-restored", repaired);
    }

    private boolean canMend(ItemStack hand) {
        if (!isItem(hand) || hand.getType().getMaxDurability() <= 0) {
            return false;
        }

        if (!hand.containsEnchantment(Enchantment.MENDING)) {
            return false;
        }

        if (!(hand.getItemMeta() instanceof Damageable damageable)) {
            return false;
        }

        return damageable.getDamage() > 0;
    }

    private double getRepairPerXp(int level) {
        return Math.max(0.1, getConfig().repairPerXpBase + (getLevelPercent(level) * getConfig().repairPerXpFactor));
    }

    private int getMaxXpSpend(int level) {
        return Math.max(1, (int) Math.round(getConfig().maxXpSpendBase + (getLevelPercent(level) * getConfig().maxXpSpendFactor)));
    }

    private int getCooldownTicks(int level) {
        return Math.max(6, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksReduction)));
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
    @ConfigDescription("Sneak-left-click to spend XP and directly mend the Mending item in your hand.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Repair Per Xp Base for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double repairPerXpBase = 2.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Repair Per Xp Factor for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double repairPerXpFactor = 4.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Xp Spend Base for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxXpSpendBase = 14.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Xp Spend Factor for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxXpSpendFactor = 130.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksBase = 38.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Reduction for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksReduction = 26.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Skill Xp Per Durability for the Discovery Better Mending adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double skillXpPerDurability = 0.35;
    }
}
