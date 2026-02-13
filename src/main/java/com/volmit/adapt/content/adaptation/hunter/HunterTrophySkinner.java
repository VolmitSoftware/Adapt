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

package com.volmit.adapt.content.adaptation.hunter;

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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public class HunterTrophySkinner extends SimpleAdaptation<HunterTrophySkinner.Config> {
    public HunterTrophySkinner() {
        super("hunter-trophy-skinner");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("hunter.trophy_skinner.description"));
        setDisplayName(Localizer.dLocalize("hunter.trophy_skinner.name"));
        setIcon(Material.ZOMBIE_HEAD);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(2000);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SKELETON_SKULL)
                .key("challenge_hunter_trophy_50")
                .title(Localizer.dLocalize("advancement.challenge_hunter_trophy_50.title"))
                .description(Localizer.dLocalize("advancement.challenge_hunter_trophy_50.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.ZOMBIE_HEAD)
                        .key("challenge_hunter_trophy_heads_100")
                        .title(Localizer.dLocalize("advancement.challenge_hunter_trophy_heads_100.title"))
                        .description(Localizer.dLocalize("advancement.challenge_hunter_trophy_heads_100.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_hunter_trophy_50", "hunter.trophy-skinner.trophies-collected", 50, 400);
        registerMilestone("challenge_hunter_trophy_heads_100", "hunter.trophy-skinner.heads-collected", 100, 1000);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.pc(getDropChance(level), 0) + C.GRAY + " " + Localizer.dLocalize("hunter.trophy_skinner.lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getHeadChance(level), 0) + C.GRAY + " " + Localizer.dLocalize("hunter.trophy_skinner.lore2"));
        v.addLore(C.YELLOW + "* " + Form.f(getMinimumRange(level), 1) + C.GRAY + " " + Localizer.dLocalize("hunter.trophy_skinner.lore3"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null || !hasAdaptation(killer) || e.getEntity() instanceof Player || !canPVE(killer, e.getEntity().getLocation())) {
            return;
        }

        int level = getLevel(killer);
        PrecisionContext precision = readPrecisionContext(e, killer, level);
        if (!precision.precise()) {
            return;
        }

        if (ThreadLocalRandom.current().nextDouble() > getDropChance(level)) {
            return;
        }

        ItemStack trophy = buildTrophyDrop(e.getEntityType(), level, precision.projectileKill());
        if (trophy != null) {
            e.getDrops().add(trophy);
            getPlayer(killer).getData().addStat("hunter.trophy-skinner.trophies-collected", 1);
        }

        if (ThreadLocalRandom.current().nextDouble() <= getHeadChance(level)) {
            ItemStack head = buildHeadDrop(e.getEntityType());
            if (head != null) {
                e.getDrops().add(head);
                getPlayer(killer).getData().addStat("hunter.trophy-skinner.heads-collected", 1);
            }
        }

        SoundPlayer.of(killer).play(killer.getLocation(), Sound.ENTITY_WOLF_SHAKE, 0.55f, 1.35f);
        xp(killer, getConfig().xpPerTrophy);
    }

    private PrecisionContext readPrecisionContext(EntityDeathEvent e, Player killer, int level) {
        boolean projectileKill = false;
        double range = 0;
        if (e.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent damageByEntity) {
            if (damageByEntity.getDamager() instanceof Projectile projectile && projectile.getShooter() == killer) {
                projectileKill = true;
            }
        }

        if (killer.getWorld() == e.getEntity().getWorld()) {
            range = killer.getLocation().distance(e.getEntity().getLocation());
        }

        boolean rangedPrecision = projectileKill && range >= getMinimumRange(level);
        boolean stealthPrecision = killer.isSneaking();
        return new PrecisionContext(projectileKill, rangedPrecision || stealthPrecision);
    }

    private ItemStack buildTrophyDrop(EntityType type, int level, boolean projectileKill) {
        Material material = switch (type) {
            case CREEPER -> Material.GUNPOWDER;
            case SKELETON, BOGGED, WITHER_SKELETON, STRAY -> Material.BONE;
            case ZOMBIE, ZOMBIFIED_PIGLIN, HUSK, DROWNED -> Material.ROTTEN_FLESH;
            case SPIDER, CAVE_SPIDER -> Material.STRING;
            case BLAZE -> Material.BLAZE_POWDER;
            case ENDERMAN -> Material.ENDER_PEARL;
            case WITCH -> Material.REDSTONE;
            case PIGLIN, PIGLIN_BRUTE, HOGLIN, ZOGLIN -> Material.PORKCHOP;
            default -> Material.LEATHER;
        };

        int amount = Math.max(1, (int) Math.round(getConfig().trophyAmountBase + (getLevelPercent(level) * getConfig().trophyAmountFactor)));
        if (projectileKill) {
            amount += 1;
        }

        return new ItemStack(material, Math.min(8, amount));
    }

    private ItemStack buildHeadDrop(EntityType type) {
        Material material = switch (type) {
            case CREEPER -> Material.CREEPER_HEAD;
            case SKELETON, STRAY, BOGGED -> Material.SKELETON_SKULL;
            case WITHER_SKELETON -> Material.WITHER_SKELETON_SKULL;
            case ZOMBIE, HUSK, DROWNED, ZOMBIFIED_PIGLIN -> Material.ZOMBIE_HEAD;
            case PIGLIN, PIGLIN_BRUTE -> Material.PIGLIN_HEAD;
            default -> null;
        };

        return material == null ? null : new ItemStack(material);
    }

    private double getDropChance(int level) {
        return Math.min(getConfig().maxDropChance, getConfig().dropChanceBase + (getLevelPercent(level) * getConfig().dropChanceFactor));
    }

    private double getHeadChance(int level) {
        return Math.min(getConfig().maxHeadChance, getConfig().headChanceBase + (getLevelPercent(level) * getConfig().headChanceFactor));
    }

    private double getMinimumRange(int level) {
        return Math.max(4, getConfig().minimumRangeBase - (getLevelPercent(level) * getConfig().minimumRangeFactor));
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
    @ConfigDescription("Precision kills can grant bonus trophy drops and occasional heads from elite targets.")
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
        double costFactor = 0.8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Drop Chance Base for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double dropChanceBase = 0.14;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Drop Chance Factor for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double dropChanceFactor = 0.3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Maximum Drop Chance for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxDropChance = 0.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Head Chance Base for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double headChanceBase = 0.015;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Head Chance Factor for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double headChanceFactor = 0.08;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Maximum Head Chance for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxHeadChance = 0.12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Trophy Amount Base for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double trophyAmountBase = 1;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Trophy Amount Factor for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double trophyAmountFactor = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Minimum Range Base for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double minimumRangeBase = 18;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Minimum Range Factor for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double minimumRangeFactor = 10;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls XP Per Trophy for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerTrophy = 16;
    }

    private record PrecisionContext(boolean projectileKill, boolean precise) {
    }
}
