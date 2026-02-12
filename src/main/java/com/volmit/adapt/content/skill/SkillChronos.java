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

package com.volmit.adapt.content.skill;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.skill.SimpleSkill;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.content.adaptation.chronos.ChronosAberrantTouch;
import com.volmit.adapt.content.adaptation.chronos.ChronosInstantRecall;
import com.volmit.adapt.content.adaptation.chronos.ChronosTemporalEcho;
import com.volmit.adapt.content.adaptation.chronos.ChronosTimeBomb;
import com.volmit.adapt.content.adaptation.chronos.ChronosTimeInABottle;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.CustomModel;
import com.volmit.adapt.util.Localizer;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SkillChronos extends SimpleSkill<SkillChronos.Config> {
    private final Map<UUID, Location> lastPositions;
    private final Map<UUID, Deque<Location>> positionHistory;
    private final Map<UUID, Set<String>> recentActionTypes;
    private final Map<UUID, Long> actionTypeResetTimestamps;
    private final Map<UUID, Long> lastActivityTimestamps;
    private final Map<UUID, Long> sleepCooldowns;
    private final Map<UUID, Long> sleepEntryWorldTime;
    private final Map<UUID, SpeedPotionTracker> speedPotionTrackers;
    private final Map<UUID, Long> enderPearlCooldowns;
    private final Map<UUID, Long> survivalStreakStart;
    private final Map<UUID, Long> lastSurvivalCheck;

    public SkillChronos() {
        super("chronos", Localizer.dLocalize("skill.chronos.icon"));
        registerConfiguration(Config.class);
        setColor(C.AQUA);
        setInterval(600000);
        setDescription(Localizer.dLocalize("skill.chronos.description"));
        setDisplayName(Localizer.dLocalize("skill.chronos.name"));
        setInterval(getConfig().setInterval);
        setIcon(Material.CLOCK);
        registerAdaptation(new ChronosTimeInABottle());
        registerAdaptation(new ChronosAberrantTouch());
        registerAdaptation(new ChronosInstantRecall());
        registerAdaptation(new ChronosTimeBomb());
        registerAdaptation(new ChronosTemporalEcho());
        lastPositions = new HashMap<>();
        positionHistory = new HashMap<>();
        recentActionTypes = new HashMap<>();
        actionTypeResetTimestamps = new HashMap<>();
        lastActivityTimestamps = new HashMap<>();
        sleepCooldowns = new HashMap<>();
        sleepEntryWorldTime = new HashMap<>();
        speedPotionTrackers = new HashMap<>();
        enderPearlCooldowns = new HashMap<>();
        survivalStreakStart = new HashMap<>();
        lastSurvivalCheck = new HashMap<>();
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.CLOCK)
                .key("challenge_chronos_1h")
                .title(Localizer.dLocalize("advancement.challenge_chronos_1h.title"))
                .description(Localizer.dLocalize("advancement.challenge_chronos_1h.description"))
                .model(CustomModel.get(Material.CLOCK, "advancement", "chronos", "challenge_chronos_1h"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.COMPASS)
                        .key("challenge_chronos_24h")
                        .title(Localizer.dLocalize("advancement.challenge_chronos_24h.title"))
                        .description(Localizer.dLocalize("advancement.challenge_chronos_24h.description"))
                        .model(CustomModel.get(Material.COMPASS, "advancement", "chronos", "challenge_chronos_24h"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .child(AdaptAdvancement.builder()
                                .icon(Material.RECOVERY_COMPASS)
                                .key("challenge_chronos_168h")
                                .title(Localizer.dLocalize("advancement.challenge_chronos_168h.title"))
                                .description(Localizer.dLocalize("advancement.challenge_chronos_168h.description"))
                                .model(CustomModel.get(Material.RECOVERY_COMPASS, "advancement", "chronos", "challenge_chronos_168h"))
                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                .build())
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_chronos_1h").goal(60).stat("minutes.online").reward(getConfig().challengeChronosReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_chronos_24h").goal(1440).stat("minutes.online").reward(getConfig().challengeChronosReward * 2).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_chronos_168h").goal(10080).stat("minutes.online").reward(getConfig().challengeChronosReward * 5).build());

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.COMPASS).key("challenge_active_dist_1k")
                .title(Localizer.dLocalize("advancement.challenge_active_dist_1k.title"))
                .description(Localizer.dLocalize("advancement.challenge_active_dist_1k.description"))
                .model(CustomModel.get(Material.COMPASS, "advancement", "chronos", "challenge_active_dist_1k"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.RECOVERY_COMPASS)
                        .key("challenge_active_dist_10k")
                        .title(Localizer.dLocalize("advancement.challenge_active_dist_10k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_active_dist_10k.description"))
                        .model(CustomModel.get(Material.RECOVERY_COMPASS, "advancement", "chronos", "challenge_active_dist_10k"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .child(AdaptAdvancement.builder()
                                .icon(Material.LODESTONE)
                                .key("challenge_active_dist_100k")
                                .title(Localizer.dLocalize("advancement.challenge_active_dist_100k.title"))
                                .description(Localizer.dLocalize("advancement.challenge_active_dist_100k.description"))
                                .model(CustomModel.get(Material.LODESTONE, "advancement", "chronos", "challenge_active_dist_100k"))
                                .frame(AdaptAdvancementFrame.CHALLENGE)
                                .visibility(AdvancementVisibility.PARENT_GRANTED)
                                .build())
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_active_dist_1k").goal(1000).stat("chronos.active.distance").reward(getConfig().challengeChronosReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_active_dist_10k").goal(10000).stat("chronos.active.distance").reward(getConfig().challengeChronosReward * 2).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_active_dist_100k").goal(100000).stat("chronos.active.distance").reward(getConfig().challengeChronosReward * 5).build());

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.WHITE_BED).key("challenge_beds_10")
                .title(Localizer.dLocalize("advancement.challenge_beds_10.title"))
                .description(Localizer.dLocalize("advancement.challenge_beds_10.description"))
                .model(CustomModel.get(Material.WHITE_BED, "advancement", "chronos", "challenge_beds_10"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.RED_BED)
                        .key("challenge_beds_100")
                        .title(Localizer.dLocalize("advancement.challenge_beds_100.title"))
                        .description(Localizer.dLocalize("advancement.challenge_beds_100.description"))
                        .model(CustomModel.get(Material.RED_BED, "advancement", "chronos", "challenge_beds_100"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_beds_10").goal(10).stat("chronos.beds.used").reward(getConfig().challengeChronosReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_beds_100").goal(100).stat("chronos.beds.used").reward(getConfig().challengeChronosReward * 2).build());

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ENDER_PEARL).key("challenge_chronos_tp_50")
                .title(Localizer.dLocalize("advancement.challenge_chronos_tp_50.title"))
                .description(Localizer.dLocalize("advancement.challenge_chronos_tp_50.description"))
                .model(CustomModel.get(Material.ENDER_PEARL, "advancement", "chronos", "challenge_chronos_tp_50"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.CHORUS_FRUIT)
                        .key("challenge_chronos_tp_500")
                        .title(Localizer.dLocalize("advancement.challenge_chronos_tp_500.title"))
                        .description(Localizer.dLocalize("advancement.challenge_chronos_tp_500.description"))
                        .model(CustomModel.get(Material.CHORUS_FRUIT, "advancement", "chronos", "challenge_chronos_tp_500"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_chronos_tp_50").goal(50).stat("chronos.teleports").reward(getConfig().challengeChronosReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_chronos_tp_500").goal(500).stat("chronos.teleports").reward(getConfig().challengeChronosReward * 2).build());

        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SKELETON_SKULL).key("challenge_chronos_deaths_10")
                .title(Localizer.dLocalize("advancement.challenge_chronos_deaths_10.title"))
                .description(Localizer.dLocalize("advancement.challenge_chronos_deaths_10.description"))
                .model(CustomModel.get(Material.SKELETON_SKULL, "advancement", "chronos", "challenge_chronos_deaths_10"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.WITHER_SKELETON_SKULL)
                        .key("challenge_chronos_deaths_100")
                        .title(Localizer.dLocalize("advancement.challenge_chronos_deaths_100.title"))
                        .description(Localizer.dLocalize("advancement.challenge_chronos_deaths_100.description"))
                        .model(CustomModel.get(Material.WITHER_SKELETON_SKULL, "advancement", "chronos", "challenge_chronos_deaths_100"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_chronos_deaths_10").goal(10).stat("chronos.deaths").reward(getConfig().challengeChronosReward).build());
        registerStatTracker(AdaptStatTracker.builder().advancement("challenge_chronos_deaths_100").goal(100).stat("chronos.deaths").reward(getConfig().challengeChronosReward * 2).build());
    }

    private void trackAction(UUID uuid, String actionType) {
        long now = System.currentTimeMillis();
        lastActivityTimestamps.put(uuid, now);

        Long resetTime = actionTypeResetTimestamps.get(uuid);
        if (resetTime == null || now - resetTime > getConfig().activityWindow) {
            recentActionTypes.put(uuid, new HashSet<>());
            actionTypeResetTimestamps.put(uuid, now);
        }
        recentActionTypes.computeIfAbsent(uuid, k -> new HashSet<>()).add(actionType);
    }

    private boolean isAfk(UUID uuid) {
        Deque<Location> history = positionHistory.get(uuid);
        if (history == null || history.size() < 3) {
            return false;
        }

        double avgX = 0;
        double avgZ = 0;
        int count = 0;
        for (Location loc : history) {
            avgX += loc.getX();
            avgZ += loc.getZ();
            count++;
        }
        avgX /= count;
        avgZ /= count;

        double variance = 0;
        for (Location loc : history) {
            double dx = loc.getX() - avgX;
            double dz = loc.getZ() - avgZ;
            variance += Math.sqrt(dx * dx + dz * dz);
        }
        variance /= count;

        Set<String> actions = recentActionTypes.getOrDefault(uuid, new HashSet<>());
        return variance < getConfig().afkVarianceThreshold && actions.size() < getConfig().afkMinActionTypes;
    }

    private double getAfkMultiplier(UUID uuid) {
        return isAfk(uuid) ? getConfig().afkPenaltyMultiplier : 1.0;
    }

    private boolean isNight(Player p) {
        long time = p.getWorld().getTime();
        return time >= 12542 && time <= 23460;
    }

    @Override
    public void onTick() {
        if (!this.isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (shouldReturnForPlayer(p)) {
                continue;
            }

            UUID uuid = p.getUniqueId();
            Location current = p.getLocation();
            Location last = lastPositions.get(uuid);

            // Update position history
            Deque<Location> history = positionHistory.computeIfAbsent(uuid, k -> new ArrayDeque<>());
            history.addLast(current.clone());
            while (history.size() > getConfig().positionHistorySize) {
                history.removeFirst();
            }

            double moved = (last != null && last.getWorld() != null && last.getWorld().equals(current.getWorld()))
                    ? last.distance(current)
                    : 0;

            // Track movement as an action type
            if (moved >= getConfig().minimumMovementForActiveCheck) {
                trackAction(uuid, "movement");
            }

            double afkMult = getAfkMultiplier(uuid);

            // Movement XP (existing behavior, now with AFK penalty)
            if (moved >= getConfig().minimumMovementForActiveCheck) {
                getPlayer(p).getData().addStat("minutes.online", 10);
                getPlayer(p).getData().addStat("chronos.active.distance", moved);
                double bonus = (moved / getConfig().distancePerBonusXP) * getConfig().activeMovementXP;
                xpSilent(p, Math.min(getConfig().activeMovementXPCapPerTick, bonus) * afkMult);
            }

            // Passive active-play XP
            Long lastActivity = lastActivityTimestamps.get(uuid);
            if (lastActivity != null && now - lastActivity < getConfig().activityWindow) {
                double passiveXP = getConfig().passiveActiveXP;

                // Night activity multiplier
                if (isNight(p)) {
                    passiveXP *= getConfig().nightActivityMultiplier;
                }

                // Activity variety bonus
                Set<String> actions = recentActionTypes.getOrDefault(uuid, new HashSet<>());
                if (actions.size() >= getConfig().activityTypesForBonus) {
                    passiveXP *= getConfig().activityBonusMultiplier;
                }

                xpSilent(p, passiveXP * afkMult);
            }

            // Survival streak XP
            survivalStreakStart.putIfAbsent(uuid, now);
            Long lastCheck = lastSurvivalCheck.get(uuid);
            if (lastCheck == null || now - lastCheck >= 60000) {
                lastSurvivalCheck.put(uuid, now);
                long aliveMs = now - survivalStreakStart.getOrDefault(uuid, now);
                double aliveHours = aliveMs / 3600000.0;
                double streakBonus = 1.0 + Math.min(
                        aliveHours * getConfig().survivalStreakBonusPerHour,
                        getConfig().survivalStreakHourCap * getConfig().survivalStreakBonusPerHour
                );
                xpSilent(p, getConfig().survivalXPPerMinute * streakBonus * afkMult);
            }

            checkStatTrackers(getPlayer(p));
            lastPositions.put(uuid, current.clone());
        }
    }

    // --- Sleep XP ---

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerBedEnterEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        shouldReturnForPlayer(p, () -> {
            UUID uuid = p.getUniqueId();
            long now = System.currentTimeMillis();

            Long lastSleep = sleepCooldowns.get(uuid);
            if (lastSleep != null && now - lastSleep < getConfig().sleepCooldown) {
                return;
            }

            trackAction(uuid, "sleep");
            long worldTime = p.getWorld().getTime();
            sleepEntryWorldTime.put(uuid, worldTime);
            sleepCooldowns.put(uuid, now);
            getPlayer(p).getData().addStat("chronos.beds.used", 1);

            Bukkit.getScheduler().runTaskLater(Adapt.instance, () -> {
                if (!p.isOnline()) {
                    return;
                }
                long currentWorldTime = p.getWorld().getTime();
                boolean nightSkipped = currentWorldTime < 1000 || currentWorldTime < worldTime - 100;
                if (nightSkipped) {
                    xp(p, p.getLocation(), getConfig().sleepSkipXP);
                } else {
                    xp(p, p.getLocation(), getConfig().sleepAttemptXP);
                }
            }, 40L);
        });
    }

    // --- Speed Potion XP ---

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerItemConsumeEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Player p = e.getPlayer();
        shouldReturnForPlayer(p, () -> {
            ItemStack item = e.getItem();
            if (item.getType() != Material.POTION) {
                return;
            }
            if (!(item.getItemMeta() instanceof PotionMeta meta)) {
                return;
            }

            boolean isSpeedPotion = false;
            boolean isSpeedII = false;

            PotionType baseType = meta.getBasePotionType();
            if (baseType == PotionType.SWIFTNESS) {
                isSpeedPotion = true;
            }
            if (baseType == PotionType.STRONG_SWIFTNESS) {
                isSpeedPotion = true;
                isSpeedII = true;
            }

            if (!isSpeedPotion && meta.hasCustomEffects()) {
                isSpeedPotion = meta.getCustomEffects().stream()
                        .anyMatch(effect -> effect.getType().equals(PotionEffectType.SPEED));
                isSpeedII = meta.getCustomEffects().stream()
                        .anyMatch(effect -> effect.getType().equals(PotionEffectType.SPEED) && effect.getAmplifier() >= 1);
            }

            if (!isSpeedPotion) {
                return;
            }

            UUID uuid = p.getUniqueId();
            trackAction(uuid, "potion");
            long now = System.currentTimeMillis();

            SpeedPotionTracker tracker = speedPotionTrackers.computeIfAbsent(uuid, k -> new SpeedPotionTracker());

            if (now - tracker.lastUseTime > getConfig().speedPotionResetWindow) {
                tracker.consecutiveUses = 0;
            }

            double decay = getConfig().speedPotionDiminishingDecay;
            double floor = getConfig().speedPotionDiminishingFloor;
            double multiplier = Math.max(floor, Math.pow(1.0 - decay, tracker.consecutiveUses));

            double xpAmount = getConfig().speedPotionBaseXP * multiplier;
            if (isSpeedII) {
                xpAmount *= getConfig().speedPotionLevelMultiplier;
            }

            tracker.consecutiveUses++;
            tracker.lastUseTime = now;

            xp(p, p.getLocation(), xpAmount);
        });
    }

    // --- Ender Pearl XP ---

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(ProjectileLaunchEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (!(e.getEntity() instanceof EnderPearl pearl)) {
            return;
        }
        if (!(pearl.getShooter() instanceof Player p)) {
            return;
        }
        shouldReturnForPlayer(p, () -> {
            UUID uuid = p.getUniqueId();
            long now = System.currentTimeMillis();

            Long lastThrow = enderPearlCooldowns.get(uuid);
            if (lastThrow != null && now - lastThrow < getConfig().enderPearlCooldown) {
                return;
            }

            trackAction(uuid, "teleport");
            enderPearlCooldowns.put(uuid, now);
            xp(p, p.getLocation(), getConfig().enderPearlThrowXP);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerTeleportEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }
        Player p = e.getPlayer();
        shouldReturnForPlayer(p, () -> {
            trackAction(p.getUniqueId(), "teleport");
            getPlayer(p).getData().addStat("chronos.teleports", 1);
            xp(p, e.getTo(), getConfig().enderPearlTeleportXP);
        });
    }

    // --- Death / Survival Streak Reset ---

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerDeathEvent e) {
        Player p = e.getEntity();
        UUID uuid = p.getUniqueId();
        survivalStreakStart.put(uuid, System.currentTimeMillis());
        lastSurvivalCheck.remove(uuid);
        trackAction(uuid, "combat");
        shouldReturnForPlayer(p, () -> getPlayer(p).getData().addStat("chronos.deaths", 1));
    }

    // --- Player Quit Cleanup ---

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        lastPositions.remove(uuid);
        positionHistory.remove(uuid);
        recentActionTypes.remove(uuid);
        actionTypeResetTimestamps.remove(uuid);
        lastActivityTimestamps.remove(uuid);
        sleepCooldowns.remove(uuid);
        sleepEntryWorldTime.remove(uuid);
        speedPotionTrackers.remove(uuid);
        enderPearlCooldowns.remove(uuid);
        survivalStreakStart.remove(uuid);
        lastSurvivalCheck.remove(uuid);
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    protected void onConfigReload(Config previousConfig, Config newConfig) {
        super.onConfigReload(previousConfig, newConfig);
        setInterval(newConfig.setInterval);
    }

    private static class SpeedPotionTracker {
        int consecutiveUses;
        long lastUseTime;
    }

    @NoArgsConstructor
    protected static class Config {
        // Existing
        @com.volmit.adapt.util.config.ConfigDoc(value = "Tick interval used by this logic.", impact = "Lower values run logic more often; higher values run it less often.")
        long setInterval = 5050;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Minimum Movement For Active Check for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double minimumMovementForActiveCheck = 0.35;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Distance Per Bonus XP for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double distancePerBonusXP = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Active Movement XP for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double activeMovementXP = 3.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Active Movement XPCap Per Tick for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double activeMovementXPCapPerTick = 22;

        // Anti-AFK
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Position History Size for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int positionHistorySize = 12;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Afk Variance Threshold for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double afkVarianceThreshold = 2.0;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Afk Min Action Types for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int afkMinActionTypes = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Afk Penalty Multiplier for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double afkPenaltyMultiplier = 0.1;

        // Passive active XP
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Passive Active XP for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double passiveActiveXP = 1.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Activity Window for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long activityWindow = 15000;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Activity Types For Bonus for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int activityTypesForBonus = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Activity Bonus Multiplier for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double activityBonusMultiplier = 1.5;

        // Night bonus
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Night Activity Multiplier for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double nightActivityMultiplier = 1.3;

        // Sleep
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Sleep Skip XP for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double sleepSkipXP = 150;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Sleep Attempt XP for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double sleepAttemptXP = 25;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Sleep Cooldown for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long sleepCooldown = 30000;

        // Speed potion
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Speed Potion Base XP for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double speedPotionBaseXP = 45;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Speed Potion Level Multiplier for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double speedPotionLevelMultiplier = 1.5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Speed Potion Diminishing Decay for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double speedPotionDiminishingDecay = 0.15;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Speed Potion Diminishing Floor for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double speedPotionDiminishingFloor = 0.25;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Speed Potion Reset Window for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long speedPotionResetWindow = 300000;

        // Ender pearl
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Ender Pearl Throw XP for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double enderPearlThrowXP = 35;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Ender Pearl Teleport XP for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double enderPearlTeleportXP = 15;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Ender Pearl Cooldown for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        long enderPearlCooldown = 10000;

        // Survival streak
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Survival XPPer Minute for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double survivalXPPerMinute = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Survival Streak Bonus Per Hour for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double survivalStreakBonusPerHour = 0.2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Survival Streak Hour Cap for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int survivalStreakHourCap = 5;

        // Challenge rewards
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Challenge Chronos Reward for the Chronos skill.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double challengeChronosReward = 500;
    }
}
