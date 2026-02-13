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

package com.volmit.adapt.content.adaptation.enchanting;

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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public class EnchantingOfferReroll extends SimpleAdaptation<EnchantingOfferReroll.Config> {
    public EnchantingOfferReroll() {
        super("enchanting-offer-reroll");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("enchanting.offer_reroll.description"));
        setDisplayName(Localizer.dLocalize("enchanting.offer_reroll.name"));
        setIcon(Material.ENCHANTING_TABLE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(1800);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ENCHANTING_TABLE)
                .key("challenge_enchanting_reroll_100")
                .title(Localizer.dLocalize("advancement.challenge_enchanting_reroll_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_enchanting_reroll_100.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.ENCHANTING_TABLE)
                        .key("challenge_enchanting_reroll_1k")
                        .title(Localizer.dLocalize("advancement.challenge_enchanting_reroll_1k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_enchanting_reroll_1k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_enchanting_reroll_100", "enchanting.offer-reroll.rerolls", 100, 300);
        registerMilestone("challenge_enchanting_reroll_1k", "enchanting.offer-reroll.rerolls", 1000, 1000);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.duration(getCooldownTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("enchanting.offer_reroll.lore1"));
        v.addLore(C.YELLOW + "* " + getLapisCost(level) + C.GRAY + " " + Localizer.dLocalize("enchanting.offer_reroll.lore2"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerInteractEvent e) {
        if (e.isCancelled() || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) {
            return;
        }

        Player p = e.getPlayer();
        if (!hasAdaptation(p) || !p.isSneaking() || e.getClickedBlock().getType() != Material.ENCHANTING_TABLE) {
            return;
        }

        int level = getLevel(p);
        int lapisCost = getLapisCost(level);
        if (p.getFoodLevel() < 0) {
            return;
        }

        if (!consumeLapis(p, lapisCost) || p.getLevel() < getConfig().xpLevelCost) {
            return;
        }

        if (!setSeed(p, ThreadLocalRandom.current().nextInt())) {
            return;
        }

        p.setLevel(Math.max(0, p.getLevel() - getConfig().xpLevelCost));
        p.setCooldown(Material.ENCHANTING_TABLE, getCooldownTicks(level));
        e.setCancelled(true);

        SoundPlayer sp = SoundPlayer.of(p.getWorld());
        sp.play(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        sp.play(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 0.85f);
        xp(p, getConfig().xpGainOnReroll);
        getPlayer(p).getData().addStat("enchanting.offer-reroll.rerolls", 1);
    }

    private boolean consumeLapis(Player p, int amount) {
        int need = amount;
        for (ItemStack stack : p.getInventory().getContents()) {
            if (stack == null || stack.getType() != Material.LAPIS_LAZULI || need <= 0) {
                continue;
            }

            int used = Math.min(stack.getAmount(), need);
            stack.setAmount(stack.getAmount() - used);
            need -= used;
        }
        return need <= 0;
    }

    private boolean setSeed(Player p, int seed) {
        try {
            p.getClass().getMethod("setEnchantmentSeed", int.class).invoke(p, seed);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int getLapisCost(int level) {
        return Math.max(1, (int) Math.round(getConfig().lapisCostBase - (getLevelPercent(level) * getConfig().lapisCostFactor)));
    }

    private int getCooldownTicks(int level) {
        return Math.max(20, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
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
    @ConfigDescription("Sneak-right-click an enchanting table to reroll offers for lapis and XP.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Lapis Cost Base for the Enchanting Offer Reroll adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double lapisCostBase = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Lapis Cost Factor for the Enchanting Offer Reroll adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double lapisCostFactor = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Enchanting Offer Reroll adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksBase = 320;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Enchanting Offer Reroll adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksFactor = 220;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Level Cost for the Enchanting Offer Reroll adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int xpLevelCost = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Gain On Reroll for the Enchanting Offer Reroll adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpGainOnReroll = 15;
    }
}
