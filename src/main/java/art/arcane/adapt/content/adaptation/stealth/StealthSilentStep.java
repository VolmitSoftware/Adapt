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

package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.volmlib.util.format.Form;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import fr.skytasul.glowingentities.GlowingEntities;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class StealthSilentStep extends SimpleAdaptation<StealthSilentStep.Config> {
    private final Map<UUID, Boolean> dimmed = new HashMap<>();
    private final Map<UUID, List<Long>> recentBackstabs = new HashMap<>();
    private final Map<UUID, Map<UUID, ThreatLevel>> threatGlows = new HashMap<>();

    public StealthSilentStep() {
        super("stealth-silent-step");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("stealth.silent_step.description"));
        setDisplayName(Localizer.dLocalize("stealth.silent_step.name"));
        setIcon(Material.WHITE_WOOL);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(10);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.IRON_SWORD)
                .key("challenge_stealth_silent_200")
                .title(Localizer.dLocalize("advancement.challenge_stealth_silent_200.title"))
                .description(Localizer.dLocalize("advancement.challenge_stealth_silent_200.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.DIAMOND_SWORD)
                .key("challenge_stealth_silent_5in10")
                .title(Localizer.dLocalize("advancement.challenge_stealth_silent_5in10.title"))
                .description(Localizer.dLocalize("advancement.challenge_stealth_silent_5in10.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_stealth_silent_200", "stealth.silent-step.backstabs", 200, 400);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getStealthRadius(level)) + C.GRAY + " " + Localizer.dLocalize("stealth.silent_step.lore1"));
        v.addLore(C.GREEN + "+ " + Form.pc(getMobBackstabMultiplier(level) - 1D, 0) + C.GRAY + " " + Localizer.dLocalize("stealth.silent_step.lore2"));
        v.addLore(C.GREEN + "+ " + Form.pc(getPlayerBackstabMultiplier(level) - 1D, 0) + C.GRAY + " " + Localizer.dLocalize("stealth.silent_step.lore3"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
        clearDimming(e.getPlayer());
        clearThreatGlows(e.getPlayer());
        recentBackstabs.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player p)) {
            return;
        }

        if (!hasAdaptation(p) || !p.isSneaking()) {
            return;
        }

        if (isTargetBlacklistType(e.getEntity().getType())) {
            return;
        }

        e.setCancelled(true);
        if (e.getEntity() instanceof Mob mob && mob.getTarget() == p) {
            mob.setTarget(null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerMoveEvent e) {
        if (e.isCancelled()) {
            return;
        }

        Player p = e.getPlayer();
        if (!hasAdaptation(p) || !p.isSneaking()) {
            return;
        }

        double radius = getStealthRadius(getLevel(p));
        for (Entity entity : p.getWorld().getNearbyEntities(p.getLocation(), radius, radius, radius)) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }

            if (isTargetBlacklistType(mob.getType())) {
                continue;
            }

            if (mob.getTarget() == p) {
                mob.setTarget(null);
                xp(p, getConfig().xpPerTargetDrop);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageByEntityEvent e) {
        if (e.isCancelled() || !(e.getDamager() instanceof Player attacker) || !hasAdaptation(attacker)) {
            return;
        }

        if (!(e.getEntity() instanceof LivingEntity target)) {
            return;
        }

        if (target instanceof Player victim) {
            if (!canPVP(attacker, victim.getLocation())) {
                return;
            }
        } else if (!canPVE(attacker, target.getLocation())) {
            return;
        }

        boolean unseen = attacker.hasPotionEffect(PotionEffectType.INVISIBILITY) || !isLookingAt(target, attacker);
        if (target == attacker || !unseen) {
            return;
        }

        int level = getLevel(attacker);
        double multiplier = (target instanceof Player) ? getPlayerBackstabMultiplier(level) : getMobBackstabMultiplier(level);
        e.setDamage(e.getDamage() * multiplier);
        xp(attacker, e.getDamage() * getConfig().xpPerBonusDamage);
        getPlayer(attacker).getData().addStat("stealth.silent-step.backstabs", 1);

        long now = System.currentTimeMillis();
        UUID uid = attacker.getUniqueId();
        recentBackstabs.computeIfAbsent(uid, k -> new ArrayList<>()).add(now);
        recentBackstabs.get(uid).removeIf(t -> now - t > 10000);
        if (recentBackstabs.get(uid).size() >= 5
                && AdaptConfig.get().isAdvancements()
                && !getPlayer(attacker).getData().isGranted("challenge_stealth_silent_5in10")) {
            getPlayer(attacker).getAdvancementHandler().grant("challenge_stealth_silent_5in10");
        }
    }

    @Override
    public void onTick() {
        for (art.arcane.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
            Player p = adaptPlayer.getPlayer();
            if (!hasAdaptation(p) || !p.isSneaking()) {
                clearDimming(p);
                clearThreatGlows(p);
                continue;
            }

            int level = getLevel(p);
            p.setFallDistance(Math.min(p.getFallDistance(), getConfig().maxSilentFallDistance));
            ThreatSnapshot threatSnapshot = collectThreatSnapshot(p, level);
            if (threatSnapshot.canDetect.isEmpty()) {
                applyDimming(p, level);
            } else {
                clearDimming(p);
            }
            updateThreatGlows(p, threatSnapshot);
        }
    }

    private ThreatSnapshot collectThreatSnapshot(Player p, int level) {
        ThreatSnapshot snapshot = new ThreatSnapshot();
        double detectionLookDotThreshold = getDetectionLookDotThreshold();
        double mobRadius = getStealthRadius(level);
        for (Entity entity : p.getWorld().getNearbyEntities(p.getLocation(), mobRadius, mobRadius, mobRadius)) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }

            if (!getConfig().allMobsAffectStealthVisibility && !isTargetBlacklistType(mob.getType())) {
                continue;
            }

            snapshot.add(mob, getThreatLevel(mob, p, detectionLookDotThreshold));
        }

        double playerRadius = getPlayerDetectionRadius(level);
        for (Entity nearby : p.getWorld().getNearbyEntities(p.getLocation(), playerRadius, playerRadius, playerRadius)) {
            if (!(nearby instanceof Player other)) {
                continue;
            }
            if (other == p || other.isDead()) {
                continue;
            }

            snapshot.add(other, getThreatLevel(other, p, detectionLookDotThreshold));
        }

        return snapshot;
    }

    private void applyDimming(Player p, int level) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, getDimDurationTicks(level), getConfig().dimAmplifier, false, false, false), true);
        dimmed.put(p.getUniqueId(), true);
    }

    private void clearDimming(Player p) {
        if (dimmed.remove(p.getUniqueId()) != null) {
            p.removePotionEffect(PotionEffectType.DARKNESS);
        }
    }

    private void updateThreatGlows(Player p, ThreatSnapshot snapshot) {
        if (!getConfig().showThreatGlows) {
            clearThreatGlows(p);
            return;
        }

        GlowingEntities glowingEntities = Adapt.instance.getGlowingEntities();
        if (glowingEntities == null) {
            clearThreatGlows(p);
            return;
        }

        UUID viewerId = p.getUniqueId();
        Map<UUID, ThreatLevel> active = threatGlows.computeIfAbsent(viewerId, k -> new HashMap<>());

        List<UUID> stale = new ArrayList<>();
        for (UUID entityId : active.keySet()) {
            if (!snapshot.threats.containsKey(entityId)) {
                stale.add(entityId);
            }
        }

        for (UUID entityId : stale) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null) {
                try {
                    glowingEntities.unsetGlowing(entity, p);
                } catch (ReflectiveOperationException ignored) {
                    // Ignore reflective failures and continue clearing other entities.
                }
            }
            active.remove(entityId);
        }

        for (Map.Entry<UUID, ThreatLevel> entry : snapshot.threats.entrySet()) {
            UUID entityId = entry.getKey();
            ThreatLevel desired = entry.getValue();
            ThreatLevel current = active.get(entityId);
            if (desired == current) {
                continue;
            }

            Entity entity = snapshot.entities.get(entityId);
            if (entity == null) {
                entity = Bukkit.getEntity(entityId);
            }
            if (entity == null || !entity.isValid()) {
                continue;
            }

            try {
                glowingEntities.setGlowing(entity, p, getThreatColor(desired));
                active.put(entityId, desired);
            } catch (ReflectiveOperationException ignored) {
                // Ignore reflective failures and keep runtime behavior intact.
            }
        }

        if (active.isEmpty()) {
            threatGlows.remove(viewerId);
        }
    }

    private void clearThreatGlows(Player p) {
        Map<UUID, ThreatLevel> active = threatGlows.remove(p.getUniqueId());
        if (active == null || active.isEmpty()) {
            return;
        }

        GlowingEntities glowingEntities = Adapt.instance.getGlowingEntities();
        if (glowingEntities == null) {
            return;
        }

        for (UUID entityId : active.keySet()) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity == null) {
                continue;
            }

            try {
                glowingEntities.unsetGlowing(entity, p);
            } catch (ReflectiveOperationException ignored) {
                // Ignore reflective failures and continue clearing other entities.
            }
        }
    }

    private ThreatLevel getThreatLevel(LivingEntity observer, LivingEntity target, double detectThreshold) {
        if (!observer.hasLineOfSight(target)) {
            return ThreatLevel.NONE;
        }

        double lookDot = getLookDot(observer, target);
        if (lookDot >= detectThreshold) {
            return ThreatLevel.CAN_DETECT;
        }

        double almostThreshold = Math.max(-1, detectThreshold - Math.max(0, getConfig().almostLookDotMargin));
        if (lookDot >= almostThreshold) {
            return ThreatLevel.ALMOST_DETECT;
        }

        return ThreatLevel.NONE;
    }

    private double getDetectionLookDotThreshold() {
        return Math.max(-1, Math.min(1, getConfig().detectionLookDotThreshold));
    }

    private ChatColor getThreatColor(ThreatLevel level) {
        return switch (level) {
            case CAN_DETECT -> ChatColor.RED;
            case ALMOST_DETECT -> ChatColor.GRAY;
            default -> ChatColor.WHITE;
        };
    }

    private boolean isLookingAt(LivingEntity observer, LivingEntity target) {
        return getLookDot(observer, target) >= getConfig().lookDotThreshold;
    }

    private double getLookDot(LivingEntity observer, LivingEntity target) {
        Vector look = observer.getEyeLocation().getDirection().normalize();
        Vector toTarget = target.getEyeLocation().toVector().subtract(observer.getEyeLocation().toVector());
        if (toTarget.lengthSquared() <= 0.0001) {
            return 1;
        }

        toTarget.normalize();
        return look.dot(toTarget);
    }

    private boolean isTargetBlacklistType(EntityType type) {
        if (type == null) {
            return false;
        }

        for (String raw : getConfig().targetingBlacklistTypes) {
            if (raw == null || raw.isBlank()) {
                continue;
            }

            try {
                EntityType configured = EntityType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
                if (configured == type) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid enum names in config.
            }
        }

        return false;
    }

    private double getStealthRadius(int level) {
        return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
    }

    private double getPlayerDetectionRadius(int level) {
        return getConfig().playerDetectionRadiusBase + (getLevelPercent(level) * getConfig().playerDetectionRadiusFactor);
    }

    private int getDimDurationTicks(int level) {
        return Math.max(10, (int) Math.round(getConfig().dimDurationTicksBase + (getLevelPercent(level) * getConfig().dimDurationTicksFactor)));
    }

    private double getMobBackstabMultiplier(int level) {
        return getConfig().mobBackstabBase + (getLevelPercent(level) * getConfig().mobBackstabFactor);
    }

    private double getPlayerBackstabMultiplier(int level) {
        return getConfig().playerBackstabBase + (getLevelPercent(level) * getConfig().playerBackstabFactor);
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
    @ConfigDescription("Sneaking prevents hostile mob detection, and unseen hits deal backstab damage.")
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 3;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 2;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 3;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.65;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double radiusBase = 6;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double radiusFactor = 8;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Player Detection Radius Base for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double playerDetectionRadiusBase = 10;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Player Detection Radius Factor for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double playerDetectionRadiusFactor = 14;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Dim Duration Ticks Base for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double dimDurationTicksBase = 20;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Dim Duration Ticks Factor for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double dimDurationTicksFactor = 20;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Dim Amplifier for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int dimAmplifier = 0;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Mob Backstab Base for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double mobBackstabBase = 1.5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Mob Backstab Factor for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double mobBackstabFactor = 0.5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Player Backstab Base for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double playerBackstabBase = 1.25;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Player Backstab Factor for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double playerBackstabFactor = 0.35;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Look Dot Threshold for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double lookDotThreshold = 0.45;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Target Drop for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerTargetDrop = 2;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Bonus Damage for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerBonusDamage = 3.0;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Silent Fall Distance for the Stealth Silent Step adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        float maxSilentFallDistance = 1.6f;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Shows nearby threats with per-player glowing while sneaking (red = can detect, gray = almost).", impact = "Enable to get visual awareness of entities that can or almost can spot you.")
        boolean showThreatGlows = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Look-dot margin below the full detection threshold used for gray 'almost detect' glow.", impact = "Higher values make gray warnings appear earlier; lower values make warnings stricter.")
        double almostLookDotMargin = 0.2;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Look-dot threshold for stealth visibility checks while sneaking.", impact = "Lower values make crossing an entity's view count as seen more easily; higher values require a more direct look.")
        double detectionLookDotThreshold = 0.2;
        @art.arcane.adapt.util.config.ConfigDoc(value = "If true, all nearby mobs (including passive) can break hidden state when they have line-of-sight.", impact = "Enable to prevent stealth from feeling hidden in front of passive mobs like pigs.")
        boolean allMobsAffectStealthVisibility = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Entity types that are NOT ignored by stealth targeting suppression.", impact = "Mobs listed here can still detect/target sneaking players with Silent Step.")
        List<String> targetingBlacklistTypes = new ArrayList<>(List.of("WARDEN", "WITHER", "PHANTOM", "ENDER_DRAGON"));
    }

    private enum ThreatLevel {
        NONE,
        ALMOST_DETECT,
        CAN_DETECT
    }

    private static class ThreatSnapshot {
        private final Map<UUID, ThreatLevel> threats = new HashMap<>();
        private final Map<UUID, Entity> entities = new HashMap<>();
        private final Map<UUID, ThreatLevel> canDetect = new HashMap<>();

        private void add(Entity entity, ThreatLevel level) {
            if (entity == null || level == ThreatLevel.NONE) {
                return;
            }

            UUID id = entity.getUniqueId();
            entities.put(id, entity);
            ThreatLevel existing = threats.get(id);
            if (existing == null || level.ordinal() > existing.ordinal()) {
                threats.put(id, level);
            }

            if (level == ThreatLevel.CAN_DETECT) {
                canDetect.put(id, level);
            }
        }
    }
}
