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

package com.volmit.adapt.content.adaptation.excavation;

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
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ExcavationSeismicPing extends SimpleAdaptation<ExcavationSeismicPing.Config> {
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public ExcavationSeismicPing() {
        super("excavation-seismic-ping");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("excavation.seismic_ping.description"));
        setDisplayName(Localizer.dLocalize("excavation.seismic_ping.name"));
        setIcon(Material.ECHO_SHARD);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(2200);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.BELL)
                .key("challenge_excavation_seismic_200")
                .title(Localizer.dLocalize("advancement.challenge_excavation_seismic_200.title"))
                .description(Localizer.dLocalize("advancement.challenge_excavation_seismic_200.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_excavation_seismic_200").goal(200).stat("excavation.seismic-ping.pings-triggered").reward(400).build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + getScanRange(level) + C.GRAY + " " + Localizer.dLocalize("excavation.seismic_ping.lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getPingChance(level), 0) + C.GRAY + " " + Localizer.dLocalize("excavation.seismic_ping.lore2"));
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("excavation.seismic_ping.lore3"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
        cooldowns.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!hasAdaptation(p) || !isExcavationTool(p.getInventory().getItemInMainHand())) {
            return;
        }

        if (!canBlockBreak(p, e.getBlock().getLocation())) {
            return;
        }

        int level = getLevel(p);
        long now = System.currentTimeMillis();
        long nextReady = cooldowns.getOrDefault(p.getUniqueId(), 0L);
        if (now < nextReady) {
            return;
        }

        cooldowns.put(p.getUniqueId(), now + getCooldownMillis(level));
        if (ThreadLocalRandom.current().nextDouble() > getPingChance(level)) {
            return;
        }

        Block target = findNearestOre(e.getBlock().getLocation(), getScanRange(level));
        if (target == null) {
            return;
        }

        Location origin = p.getEyeLocation();
        Location targetCenter = target.getLocation().add(0.5, 0.5, 0.5);
        Vector direction = targetCenter.toVector().subtract(origin.toVector());
        if (direction.lengthSquared() <= 0.0000001) {
            return;
        }

        renderDirectionHint(p, origin, direction.normalize(), getHintSegments(level));
        playPingSound(p, origin.distance(targetCenter), getScanRange(level));
        getPlayer(p).getData().addStat("excavation.seismic-ping.pings-triggered", 1);
        xp(p, getConfig().xpPerPing + (getValue(target.getType()) * getConfig().targetValueXpMultiplier));
    }

    private void renderDirectionHint(Player p, Location origin, Vector direction, int segments) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(110, 230, 255), (float) getConfig().particleSize);
        Location at = origin.clone();
        for (int i = 0; i < segments; i++) {
            at = at.add(direction.clone().multiply(getConfig().segmentSpacing));
            p.spawnParticle(Particle.DUST, at, Math.max(1, getConfig().segmentParticleCount), 0.05, 0.05, 0.05, 0, dust);
        }

        p.spawnParticle(Particle.ELECTRIC_SPARK, at, Math.max(1, getConfig().tipParticleCount), 0.1, 0.1, 0.1, 0.04);
    }

    private void playPingSound(Player p, double distance, int range) {
        double normalized = Math.min(1.0, distance / Math.max(1.0, range));
        float pitch = (float) Math.max(0.45, Math.min(1.95, 1.9 - (normalized * 1.1)));
        SoundPlayer sp = SoundPlayer.of(p.getWorld());
        sp.play(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, pitch);
        sp.play(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.65f, (float) Math.min(2.0, pitch + 0.2));
    }

    private Block findNearestOre(Location origin, int range) {
        World world = origin.getWorld();
        if (world == null) {
            return null;
        }

        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        int rangeSq = range * range;

        Block best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (int x = -range; x <= range; x++) {
            int bx = ox + x;
            for (int z = -range; z <= range; z++) {
                int bz = oz + z;
                if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
                    continue;
                }

                for (int y = -range; y <= range; y++) {
                    int by = oy + y;
                    if (by < minY || by > maxY) {
                        continue;
                    }

                    int d2 = (x * x) + (y * y) + (z * z);
                    if (d2 > rangeSq || d2 >= bestDistanceSq) {
                        continue;
                    }

                    Block block = world.getBlockAt(bx, by, bz);
                    if (!isOre(block.getType())) {
                        continue;
                    }

                    best = block;
                    bestDistanceSq = d2;
                }
            }
        }

        return best;
    }

    private boolean isOre(Material type) {
        return type == Material.ANCIENT_DEBRIS || type.name().endsWith("_ORE");
    }

    private boolean isExcavationTool(ItemStack item) {
        if (!isItem(item)) {
            return false;
        }

        String name = item.getType().name();
        return name.endsWith("_SHOVEL") || name.endsWith("_PICKAXE");
    }

    private int getScanRange(int level) {
        return Math.max(6, (int) Math.round(getConfig().scanRangeBase + (getLevelPercent(level) * getConfig().scanRangeFactor)));
    }

    private double getPingChance(int level) {
        return Math.min(getConfig().maxPingChance, getConfig().pingChanceBase + (getLevelPercent(level) * getConfig().pingChanceFactor));
    }

    private long getCooldownMillis(int level) {
        return Math.max(350L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
    }

    private int getHintSegments(int level) {
        return Math.max(4, (int) Math.round(getConfig().hintSegmentsBase + (getLevelPercent(level) * getConfig().hintSegmentsFactor)));
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
    @ConfigDescription("Mining can emit seismic pings that hint toward nearby ore direction.")
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
        double costFactor = 0.78;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Scan Range Base for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double scanRangeBase = 11;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Scan Range Factor for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double scanRangeFactor = 18;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Ping Chance Base for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double pingChanceBase = 0.14;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Ping Chance Factor for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double pingChanceFactor = 0.37;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Max Ping Chance for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double maxPingChance = 0.6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownMillisBase = 2600;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownMillisFactor = 1850;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Hint Segments Base for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double hintSegmentsBase = 7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Hint Segments Factor for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double hintSegmentsFactor = 9;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Segment Spacing for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double segmentSpacing = 0.55;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Particle Size for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double particleSize = 0.65;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Segment Particle Count for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int segmentParticleCount = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Tip Particle Count for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int tipParticleCount = 12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Ping for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerPing = 8;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Target Value Xp Multiplier for the Excavation Seismic Ping adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double targetValueXpMultiplier = 0.5;
    }
}
