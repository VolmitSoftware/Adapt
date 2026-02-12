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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class DiscoveryArchaeologist extends SimpleAdaptation<DiscoveryArchaeologist.Config> {
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public DiscoveryArchaeologist() {
        super("discovery-archaeologist");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("discovery.archaeologist.description"));
        setDisplayName(Localizer.dLocalize("discovery.archaeologist.name"));
        setIcon(Material.BRUSH);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(2300);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.BRUSH)
                .key("challenge_discovery_archaeologist_50")
                .title(Localizer.dLocalize("advancement.challenge_discovery_archaeologist_50.title"))
                .description(Localizer.dLocalize("advancement.challenge_discovery_archaeologist_50.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.DECORATED_POT)
                        .key("challenge_discovery_archaeologist_500")
                        .title(Localizer.dLocalize("advancement.challenge_discovery_archaeologist_500.title"))
                        .description(Localizer.dLocalize("advancement.challenge_discovery_archaeologist_500.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder()
                .advancement("challenge_discovery_archaeologist_50")
                .goal(50)
                .stat("discovery.archaeologist.bonus-finds")
                .reward(300)
                .build());
        registerStatTracker(AdaptStatTracker.builder()
                .advancement("challenge_discovery_archaeologist_500")
                .goal(500)
                .stat("discovery.archaeologist.bonus-finds")
                .reward(1000)
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getBonusRollChance(level), 0) + C.GRAY + " " + Localizer.dLocalize("discovery.archaeologist.lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getRareRewardChance(level), 0) + C.GRAY + " " + Localizer.dLocalize("discovery.archaeologist.lore2"));
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("discovery.archaeologist.lore3"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
        cooldowns.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }

        Player p = e.getPlayer();
        if (!hasAdaptation(p)) {
            return;
        }

        Block block = e.getClickedBlock();
        if (!isSuspiciousBlock(block.getType())) {
            return;
        }

        if (p.getInventory().getItemInMainHand().getType() != Material.BRUSH) {
            return;
        }

        if (!canBlockBreak(p, block.getLocation())) {
            return;
        }

        int level = getLevel(p);
        long now = System.currentTimeMillis();
        long nextReady = cooldowns.getOrDefault(p.getUniqueId(), 0L);
        if (now < nextReady) {
            return;
        }

        cooldowns.put(p.getUniqueId(), now + getCooldownMillis(level));
        if (ThreadLocalRandom.current().nextDouble() > getBonusRollChance(level)) {
            return;
        }

        ItemStack reward = rollReward(level);
        Map<Integer, ItemStack> overflow = p.getInventory().addItem(reward);
        overflow.values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(), item));

        block.getWorld().spawnParticle(Particle.ENCHANT, block.getLocation().add(0.5, 0.65, 0.5), 18, 0.25, 0.2, 0.25, 0.2);
        block.getWorld().spawnParticle(Particle.CRIT, block.getLocation().add(0.5, 0.65, 0.5), 12, 0.22, 0.22, 0.22, 0.02);

        SoundPlayer sp = SoundPlayer.of(block.getWorld());
        sp.play(block.getLocation().add(0.5, 0.5, 0.5), Sound.ITEM_BRUSH_BRUSHING_SAND_COMPLETE, 1f, 1.15f);
        sp.play(block.getLocation().add(0.5, 0.5, 0.5), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.55f);
        xp(p, getConfig().xpPerReward + (getValue(reward.getType()) * getConfig().rewardValueXpMultiplier));
        getPlayer(p).getData().addStat("discovery.archaeologist.bonus-finds", 1);
    }

    private ItemStack rollReward(int level) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextDouble() <= getRareRewardChance(level)) {
            return switch (random.nextInt(4)) {
                case 0 -> new ItemStack(Material.DIAMOND, 1);
                case 1 -> new ItemStack(Material.EMERALD, 1);
                case 2 -> new ItemStack(Material.GOLD_INGOT, 1 + random.nextInt(2));
                default -> new ItemStack(Material.AMETHYST_SHARD, 2 + random.nextInt(3));
            };
        }

        return switch (random.nextInt(6)) {
            case 0 -> new ItemStack(Material.BRICK, 1 + random.nextInt(2));
            case 1 -> new ItemStack(Material.CLAY_BALL, 2 + random.nextInt(3));
            case 2 -> new ItemStack(Material.BONE, 1 + random.nextInt(2));
            case 3 -> new ItemStack(Material.FLINT, 1 + random.nextInt(2));
            case 4 -> new ItemStack(Material.STRING, 1 + random.nextInt(2));
            default -> new ItemStack(Material.COAL, 1 + random.nextInt(2));
        };
    }

    private boolean isSuspiciousBlock(Material type) {
        return type == Material.SUSPICIOUS_SAND || type == Material.SUSPICIOUS_GRAVEL;
    }

    private double getBonusRollChance(int level) {
        return Math.min(getConfig().maxBonusRollChance,
                getConfig().bonusRollChanceBase + (getLevelPercent(level) * getConfig().bonusRollChanceFactor));
    }

    private double getRareRewardChance(int level) {
        return Math.min(getConfig().maxRareRewardChance,
                getConfig().rareRewardChanceBase + (getLevelPercent(level) * getConfig().rareRewardChanceFactor));
    }

    private long getCooldownMillis(int level) {
        return Math.max(250L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
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
    @ConfigDescription("Brushing suspicious blocks has a chance to grant bonus archaeology loot.")
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
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bonus Roll Chance Base for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bonusRollChanceBase = 0.12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bonus Roll Chance Factor for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bonusRollChanceFactor = 0.43;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Bonus Roll Chance for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxBonusRollChance = 0.72;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Rare Reward Chance Base for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double rareRewardChanceBase = 0.04;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Rare Reward Chance Factor for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double rareRewardChanceFactor = 0.24;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Rare Reward Chance for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxRareRewardChance = 0.3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownMillisBase = 1600;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownMillisFactor = 1250;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Reward for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerReward = 10;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Reward Value Xp Multiplier for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double rewardValueXpMultiplier = 0.45;
    }
}
