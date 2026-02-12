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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class EnchantingGrindstoneRecovery extends SimpleAdaptation<EnchantingGrindstoneRecovery.Config> {
    public EnchantingGrindstoneRecovery() {
        super("enchanting-grindstone-recovery");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("enchanting.grindstone_recovery.description"));
        setDisplayName(Localizer.dLocalize("enchanting.grindstone_recovery.name"));
        setIcon(Material.GRINDSTONE);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(1700);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.GRINDSTONE)
                .key("challenge_enchanting_grindstone_50")
                .title(Localizer.dLocalize("advancement.challenge_enchanting_grindstone_50.title"))
                .description(Localizer.dLocalize("advancement.challenge_enchanting_grindstone_50.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.GRINDSTONE)
                        .key("challenge_enchanting_grindstone_500")
                        .title(Localizer.dLocalize("advancement.challenge_enchanting_grindstone_500.title"))
                        .description(Localizer.dLocalize("advancement.challenge_enchanting_grindstone_500.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_enchanting_grindstone_50").goal(50).stat("enchanting.grindstone-recovery.enchants-recovered").reward(300).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_enchanting_grindstone_500").goal(500).stat("enchanting.grindstone-recovery.enchants-recovered").reward(1000).build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getRecoverChance(level), 0) + C.GRAY + " " + Localizer.dLocalize("enchanting.grindstone_recovery.lore1"));
        v.addLore(C.GREEN + "+ " + Form.f(getBonusXp(level), 1) + C.GRAY + " " + Localizer.dLocalize("enchanting.grindstone_recovery.lore2"));
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("enchanting.grindstone_recovery.lore3"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !hasAdaptation(p)) {
            return;
        }

        if (e.getView().getTopInventory().getType() != InventoryType.GRINDSTONE || e.getRawSlot() != 2 || p.hasCooldown(Material.GRINDSTONE)) {
            return;
        }

        ItemStack source = getEnchantedSource(e.getView().getTopInventory().getItem(0), e.getView().getTopInventory().getItem(1));
        if (source == null) {
            return;
        }

        int level = getLevel(p);
        if (ThreadLocalRandom.current().nextDouble() > getRecoverChance(level)) {
            return;
        }

        ItemStack recovered = makeBook(source.getEnchantments());
        if (recovered == null) {
            return;
        }

        Map<Integer, ItemStack> overflow = p.getInventory().addItem(recovered);
        overflow.values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(), item));
        int xp = Math.max(0, (int) Math.round(getBonusXp(level)));
        if (xp > 0) {
            p.giveExp(xp);
        }

        p.setCooldown(Material.GRINDSTONE, getCooldownTicks(level));
        SoundPlayer sp = SoundPlayer.of(p.getWorld());
        sp.play(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.95f, 1.15f);
        sp.play(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.45f);
        xp(p, getConfig().skillXpOnRecovery);
        getPlayer(p).getData().addStat("enchanting.grindstone-recovery.enchants-recovered", 1);
    }

    private ItemStack getEnchantedSource(ItemStack a, ItemStack b) {
        if (isEnchanted(a)) {
            return a;
        }

        if (isEnchanted(b)) {
            return b;
        }

        return null;
    }

    private boolean isEnchanted(ItemStack item) {
        return isItem(item) && !item.getEnchantments().isEmpty();
    }

    private ItemStack makeBook(Map<Enchantment, Integer> source) {
        if (source.isEmpty()) {
            return null;
        }

        List<Map.Entry<Enchantment, Integer>> entries = new ArrayList<>(source.entrySet());
        Map.Entry<Enchantment, Integer> picked = entries.get(ThreadLocalRandom.current().nextInt(entries.size()));
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        if (meta == null) {
            return null;
        }

        int safeLevel = Math.max(1, Math.min(picked.getValue(), picked.getKey().getMaxLevel()));
        meta.addStoredEnchant(picked.getKey(), safeLevel, true);
        book.setItemMeta(meta);
        return book;
    }

    private double getRecoverChance(int level) {
        return Math.min(getConfig().maxRecoverChance, getConfig().recoverChanceBase + (getLevelPercent(level) * getConfig().recoverChanceFactor));
    }

    private double getBonusXp(int level) {
        return getConfig().bonusXpBase + (getLevelPercent(level) * getConfig().bonusXpFactor);
    }

    private int getCooldownTicks(int level) {
        return Math.max(10, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
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
    @ConfigDescription("Using a grindstone can recover one removed enchant on a book with bonus XP.")
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
        double costFactor = 0.74;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Recover Chance Base for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double recoverChanceBase = 0.15;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Recover Chance Factor for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double recoverChanceFactor = 0.45;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Recover Chance for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxRecoverChance = 0.7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bonus Xp Base for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bonusXpBase = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bonus Xp Factor for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bonusXpFactor = 8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksBase = 120;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksFactor = 70;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Skill Xp On Recovery for the Enchanting Grindstone Recovery adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double skillXpOnRecovery = 13;
    }
}
