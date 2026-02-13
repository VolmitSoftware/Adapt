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

package com.volmit.adapt.content.adaptation.herbalism;

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
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class HerbalismSeedSower extends SimpleAdaptation<HerbalismSeedSower.Config> {
    public HerbalismSeedSower() {
        super("herbalism-seed-sower");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("herbalism.seed_sower.description"));
        setDisplayName(Localizer.dLocalize("herbalism.seed_sower.name"));
        setIcon(Material.WHEAT_SEEDS);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(6920);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.WHEAT_SEEDS)
                .key("challenge_herbalism_seed_1k")
                .title(Localizer.dLocalize("advancement.challenge_herbalism_seed_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_herbalism_seed_1k.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.FARMLAND)
                        .key("challenge_herbalism_seed_25k")
                        .title(Localizer.dLocalize("advancement.challenge_herbalism_seed_25k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_herbalism_seed_25k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_herbalism_seed_1k", "herbalism.seed-sower.seeds-planted", 1000, 300);
        registerMilestone("challenge_herbalism_seed_25k", "herbalism.seed-sower.seeds-planted", 25000, 1000);
    }

    @Override
    public void addStats(int level, Element v) {
        double factor = getLevelPercent(level);
        v.addLore(C.GREEN + "+ " + getRadius(level) + C.GRAY + " " + Localizer.dLocalize("herbalism.seed_sower.lore1"));
        v.addLore(C.GREEN + "+ " + getMaxCrops(level) + C.GRAY + " " + Localizer.dLocalize("herbalism.seed_sower.lore2"));
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownTicks(factor) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("herbalism.seed_sower.lore3"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerInteractEvent e) {
        if (e.isCancelled()) {
            return;
        }

        Player p = e.getPlayer();
        if (!hasAdaptation(p) || !p.isSneaking()) {
            return;
        }

        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) {
            return;
        }

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!isItem(hand)) {
            return;
        }

        Material seedType = hand.getType();
        Material cropType = getCropType(seedType);
        if (cropType == null || p.hasCooldown(seedType)) {
            return;
        }

        int planted = plantNearby(p, e.getClickedBlock(), hand, seedType, cropType, getRadius(getLevel(p)), getMaxCrops(getLevel(p)));
        if (planted <= 0) {
            return;
        }

        e.setCancelled(true);
        p.setCooldown(seedType, getCooldownTicks(getLevelPercent(p)));
        getPlayer(p).getData().addStat("harvest.planted", planted);
        getPlayer(p).getData().addStat("herbalism.seed-sower.seeds-planted", planted);
        xp(p, planted * getConfig().xpPerCrop);

        SoundPlayer sp = SoundPlayer.of(p.getWorld());
        sp.play(p.getLocation(), Sound.ITEM_CROP_PLANT, 0.6f, 1.25f);
        if (planted > 4) {
            sp.play(p.getLocation(), Sound.BLOCK_ROOTED_DIRT_PLACE, 0.5f, 1.35f);
        }
    }

    private int plantNearby(Player p, Block origin, ItemStack seeds, Material seedType, Material cropType, int radius, int maxCrops) {
        int planted = 0;
        int available = p.getGameMode() == GameMode.CREATIVE ? Integer.MAX_VALUE : seeds.getAmount();
        int y = origin.getY();

        for (int x = -radius; x <= radius && planted < maxCrops; x++) {
            for (int z = -radius; z <= radius && planted < maxCrops; z++) {
                if (available <= 0) {
                    break;
                }

                Block base = origin.getWorld().getBlockAt(origin.getX() + x, y, origin.getZ() + z);
                if (!isValidBase(seedType, base.getType())) {
                    continue;
                }

                Block crop = base.getRelative(0, 1, 0);
                if (!crop.isEmpty() || !canBlockPlace(p, crop.getLocation())) {
                    continue;
                }

                crop.setType(cropType);
                planted++;
                available--;
            }
        }

        if (p.getGameMode() != GameMode.CREATIVE && planted > 0) {
            seeds.setAmount(Math.max(0, seeds.getAmount() - planted));
            p.getInventory().setItemInMainHand(seeds.getAmount() <= 0 ? new ItemStack(Material.AIR) : seeds);
        }

        return planted;
    }

    private boolean isValidBase(Material seedType, Material base) {
        if (seedType == Material.NETHER_WART) {
            return base == Material.SOUL_SAND;
        }

        return base == Material.FARMLAND;
    }

    private Material getCropType(Material seedType) {
        return switch (seedType) {
            case WHEAT_SEEDS -> Material.WHEAT;
            case CARROT -> Material.CARROTS;
            case POTATO -> Material.POTATOES;
            case BEETROOT_SEEDS -> Material.BEETROOTS;
            case MELON_SEEDS -> Material.MELON_STEM;
            case PUMPKIN_SEEDS -> Material.PUMPKIN_STEM;
            case TORCHFLOWER_SEEDS -> Material.TORCHFLOWER_CROP;
            case NETHER_WART -> Material.NETHER_WART;
            default -> null;
        };
    }

    private int getRadius(int level) {
        return Math.max(1, (int) Math.round(getConfig().baseRadius + (getLevelPercent(level) * getConfig().radiusFactor)));
    }

    private int getMaxCrops(int level) {
        return Math.max(1, (int) Math.round(getConfig().baseCropCount + (getLevelPercent(level) * getConfig().cropCountFactor)));
    }

    private int getCooldownTicks(double factor) {
        return Math.max(2, (int) Math.round(getConfig().cooldownTicksBase - (factor * getConfig().cooldownTicksReduction)));
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
    @ConfigDescription("Sneak-right-click with seeds to plant nearby farmland and soul-sand plots.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.675;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Base Radius for the Herbalism Seed Sower adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double baseRadius = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Herbalism Seed Sower adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double radiusFactor = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Base Crop Count for the Herbalism Seed Sower adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double baseCropCount = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Crop Count Factor for the Herbalism Seed Sower adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cropCountFactor = 10;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Herbalism Seed Sower adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksBase = 60;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Reduction for the Herbalism Seed Sower adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksReduction = 42;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Crop for the Herbalism Seed Sower adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerCrop = 1.45;
    }
}
