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
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.volmlib.util.format.Form;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class StealthShadowDecoy extends SimpleAdaptation<StealthShadowDecoy.Config> {
    private static final PacketDecoyBridge PACKET_DECOY = PacketDecoyBridge.create();

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, DecoyState> activeDecoys = new HashMap<>();
    private final Map<UUID, UUID> anchorOwners = new HashMap<>();
    private final Map<UUID, Long> ownerEquipmentMaskSync = new HashMap<>();

    public StealthShadowDecoy() {
        super("stealth-shadow-decoy");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("stealth.shadow_decoy.description"));
        setDisplayName(Localizer.dLocalize("stealth.shadow_decoy.name"));
        setIcon(Material.PLAYER_HEAD);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(5);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ARMOR_STAND)
                .key("challenge_stealth_decoy_100")
                .title(Localizer.dLocalize("advancement.challenge_stealth_decoy_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_stealth_decoy_100.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ARMOR_STAND)
                .key("challenge_stealth_decoy_distract_500")
                .title(Localizer.dLocalize("advancement.challenge_stealth_decoy_distract_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_stealth_decoy_distract_500.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_stealth_decoy_100", "stealth.shadow-decoy.decoys-spawned", 100, 300);
        registerMilestone("challenge_stealth_decoy_distract_500", "stealth.shadow-decoy.mobs-distracted", 500, 1000);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.duration(getDecoyTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("stealth.shadow_decoy.lore1"));
        v.addLore(C.GREEN + "+ " + Form.f(getDecoyRadius(level)) + C.GRAY + " " + Localizer.dLocalize("stealth.shadow_decoy.lore2"));
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("stealth.shadow_decoy.lore3"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        cooldowns.remove(id);
        ownerEquipmentMaskSync.remove(id);
        DecoyState state = activeDecoys.remove(id);
        if (state != null) {
            removeDecoy(state, null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(EntityDamageEvent e) {
        if (anchorOwners.containsKey(e.getEntity().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(EntityDamageByEntityEvent e) {
        UUID ownerId = anchorOwners.get(e.getEntity().getUniqueId());
        if (ownerId == null) {
            return;
        }

        e.setCancelled(true);
        DecoyState state = activeDecoys.get(ownerId);
        if (state == null) {
            return;
        }

        if (!(e.getEntity() instanceof ArmorStand stand) || !(e.getDamager() instanceof LivingEntity attacker)) {
            return;
        }

        reactToDecoyHit(state, stand, attacker);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerAnimationEvent e) {
        Player attacker = e.getPlayer();
        if (activeDecoys.isEmpty()) {
            return;
        }

        RayTraceResult hit = attacker.getWorld().rayTraceEntities(
                attacker.getEyeLocation(),
                attacker.getEyeLocation().getDirection(),
                Math.max(1.0, getConfig().decoySwingDetectionReach),
                entity -> anchorOwners.containsKey(entity.getUniqueId())
        );

        if (hit == null || !(hit.getHitEntity() instanceof ArmorStand stand)) {
            return;
        }

        UUID ownerId = anchorOwners.get(stand.getUniqueId());
        if (ownerId == null) {
            return;
        }

        DecoyState state = activeDecoys.get(ownerId);
        if (state == null) {
            return;
        }

        reactToDecoyHit(state, stand, attacker);
    }

    private void reactToDecoyHit(DecoyState state, ArmorStand stand, LivingEntity attacker) {
        if (state.packetDecoy() != null) {
            state.packetDecoy().hitFrom(attacker.getLocation());
        }

        Vector push = stand.getLocation().toVector().subtract(attacker.getLocation().toVector());
        if (push.lengthSquared() < 0.0001) {
            push = attacker.getLocation().getDirection().multiply(-1);
        }

        push.setY(0);
        push.normalize().multiply(Math.max(0, getConfig().decoyHitKnockback));
        push.setY(Math.max(0, getConfig().decoyHitLift));
        stand.setVelocity(push);
        SoundPlayer.of(stand.getWorld()).play(stand.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (e.isSneaking() || !hasAdaptation(p)) {
            return;
        }

        int level = getLevel(p);
        long now = System.currentTimeMillis();
        if (now < cooldowns.getOrDefault(p.getUniqueId(), 0L)) {
            return;
        }

        spawnDecoy(p, level);
        cooldowns.put(p.getUniqueId(), now + getCooldownMillis(level));
        xp(p, getConfig().xpOnDecoy);
        getPlayer(p).getData().addStat("stealth.shadow-decoy.decoys-spawned", 1);
    }

    private void spawnDecoy(Player owner, int level) {
        DecoyState previous = activeDecoys.remove(owner.getUniqueId());
        if (previous != null) {
            removeDecoy(previous, owner);
        }

        ArmorStand anchor = spawnAnchor(owner.getLocation());
        anchorOwners.put(anchor.getUniqueId(), owner.getUniqueId());
        PacketPlayerDecoy packetDecoy = PACKET_DECOY.spawnDecoy(owner, anchor, getConfig().tabListRemoveDelayTicks, getConfig().decoySkinLayerMask);

        if (packetDecoy == null && getConfig().legacyFallbackEnabled) {
            configureLegacyVisual(anchor, owner);
        }

        long expiresAt = System.currentTimeMillis() + (getDecoyTicks(level) * 50L);
        activeDecoys.put(owner.getUniqueId(), new DecoyState(anchor.getUniqueId(), packetDecoy, expiresAt, level));

        redirectAggro(owner, anchor, level);
        if (areParticlesEnabled()) {
            anchor.getWorld().spawnParticle(Particle.SMOKE, anchor.getLocation().add(0, 1, 0), 18, 0.2, 0.4, 0.2, 0.03);
        }
        SoundPlayer.of(owner.getWorld()).play(owner.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.7f);
    }

    private ArmorStand spawnAnchor(Location location) {
        return location.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setMarker(false);
            stand.setVisible(false);
            stand.setInvisible(true);
            stand.setGravity(true);
            stand.setInvulnerable(false);
            stand.setSilent(true);
            stand.setBasePlate(false);
            stand.setSmall(false);
            stand.setArms(false);
            stand.setCollidable(true);
        });
    }

    private void configureLegacyVisual(ArmorStand stand, Player owner) {
        stand.setMarker(false);
        stand.setVisible(true);
        stand.setInvisible(false);
        stand.setSmall(false);
        stand.setArms(true);
        stand.setCustomNameVisible(true);
        stand.setCustomName(C.GRAY + owner.getName());

        EntityEquipment equipment = stand.getEquipment();
        if (equipment == null) {
            return;
        }

        equipment.setHelmet(owner.getInventory().getHelmet());
        equipment.setChestplate(owner.getInventory().getChestplate());
        equipment.setLeggings(owner.getInventory().getLeggings());
        equipment.setBoots(owner.getInventory().getBoots());
        equipment.setItemInMainHand(owner.getInventory().getItemInMainHand());
        equipment.setItemInOffHand(owner.getInventory().getItemInOffHand());
    }

    private void redirectAggro(Player owner, LivingEntity target, int level) {
        double radius = getDecoyRadius(level);
        Location center = target.getLocation();
        for (Entity entity : owner.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }

            if (mob.getTarget() == owner || mob.hasLineOfSight(owner)) {
                mob.setTarget(target);
                getPlayer(owner).getData().addStat("stealth.shadow-decoy.mobs-distracted", 1);
            }
        }
    }

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, DecoyState>> it = activeDecoys.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, DecoyState> entry = it.next();
            UUID ownerId = entry.getKey();
            DecoyState state = entry.getValue();
            Player owner = Bukkit.getPlayer(ownerId);

            if (owner == null || !owner.isOnline() || state.expiresAt() <= now) {
                removeDecoy(state, owner);
                it.remove();
                continue;
            }

            Entity entity = Bukkit.getEntity(state.anchorId());
            if (!(entity instanceof ArmorStand anchor) || !anchor.isValid()) {
                removeDecoy(state, owner);
                it.remove();
                continue;
            }

            PacketPlayerDecoy packetDecoy = state.packetDecoy();
            if (packetDecoy != null) {
                packetDecoy.tick();
                packetDecoy.syncToAnchor(anchor.getLocation(), anchor.isOnGround());
                packetDecoy.lookAtViewers(anchor.getLocation().add(0, getConfig().decoyEyeHeight, 0));
            }

            applyOwnerInvisibility(owner);
            syncOwnerEquipmentHidden(owner);
            spawnOwnerTrail(owner);
            redirectAggro(owner, anchor, state.level());
        }
    }

    private void applyOwnerInvisibility(Player owner) {
        int duration = Math.max(20, getConfig().ownerInvisibilityRefreshTicks);
        PotionEffect current = owner.getPotionEffect(PotionEffectType.INVISIBILITY);
        if (current != null && current.getDuration() > duration + 5) {
            return;
        }

        owner.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, duration, getConfig().ownerInvisibilityAmplifier, false, false, false), true);
    }

    private void spawnOwnerTrail(Player owner) {
        owner.getWorld().spawnParticle(
                Particle.SMOKE,
                owner.getLocation().add(0, getConfig().ownerTrailYOffset, 0),
                Math.max(1, getConfig().ownerTrailParticles),
                Math.max(0, getConfig().ownerTrailHorizontalSpread),
                Math.max(0, getConfig().ownerTrailVerticalSpread),
                Math.max(0, getConfig().ownerTrailHorizontalSpread),
                Math.max(0, getConfig().ownerTrailSpeed)
        );
    }

    private void syncOwnerEquipmentHidden(Player owner) {
        long now = System.currentTimeMillis();
        long nextAt = ownerEquipmentMaskSync.getOrDefault(owner.getUniqueId(), 0L);
        if (now < nextAt) {
            return;
        }

        PACKET_DECOY.sendOwnerEquipment(owner, true);
        ownerEquipmentMaskSync.put(owner.getUniqueId(), now + Math.max(100L, getConfig().ownerEquipmentHideResendMillis));
    }

    private void restoreOwnerEquipment(Player owner) {
        if (owner == null || !owner.isOnline()) {
            return;
        }

        ownerEquipmentMaskSync.remove(owner.getUniqueId());
        PACKET_DECOY.sendOwnerEquipment(owner, false);
    }

    private void removeDecoy(DecoyState state, Player owner) {
        if (state.packetDecoy() != null) {
            state.packetDecoy().destroy();
        }

        Entity entity = Bukkit.getEntity(state.anchorId());
        anchorOwners.remove(state.anchorId());
        if (entity instanceof ArmorStand stand && stand.isValid()) {
            stand.remove();
        }

        restoreOwnerEquipment(owner);
    }

    private long getCooldownMillis(int level) {
        return Math.max(1000L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
    }

    private int getDecoyTicks(int level) {
        return Math.max(20, (int) Math.round(getConfig().decoyTicksBase + (getLevelPercent(level) * getConfig().decoyTicksFactor)));
    }

    private double getDecoyRadius(int level) {
        return getConfig().decoyRadiusBase + (getLevelPercent(level) * getConfig().decoyRadiusFactor);
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
    @ConfigDescription("Stopping sneak spawns a short-lived shadow decoy that pulls your current aggro.")
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.72;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base cooldown after creating a decoy, in milliseconds.", impact = "Higher values mean longer time between activations.")
        double cooldownMillisBase = 18000;
        @art.arcane.adapt.util.config.ConfigDoc(value = "How much cooldown is reduced by leveling.", impact = "Higher values reduce cooldown more at higher levels.")
        double cooldownMillisFactor = 12000;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base active duration in ticks.", impact = "Higher values keep decoys active longer.")
        double decoyTicksBase = 60;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Duration scaling from level, in ticks.", impact = "Higher values extend duration more per level.")
        double decoyTicksFactor = 80;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base aggro redirect radius.", impact = "Higher values pull aggro from farther away.")
        double decoyRadiusBase = 8;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Aggro radius scaling from level.", impact = "Higher values expand pull range more per level.")
        double decoyRadiusFactor = 10;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Visual eye height used for fake player facing.", impact = "Adjust if head rotation appears too high or too low.")
        double decoyEyeHeight = 1.62;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Delay before removing the fake player from tab list, in ticks.", impact = "Small values hide tab entries faster; larger values help skins load.")
        int tabListRemoveDelayTicks = -1;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Allows armor stand visual fallback if packet NPC creation fails.", impact = "Turn off to disable fallback visuals on incompatible server builds.")
        boolean legacyFallbackEnabled = false;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Refresh duration for owner invisibility while a decoy is active.", impact = "Higher values keep invisibility active longer between refreshes.")
        int ownerInvisibilityRefreshTicks = 30;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Amplifier for the temporary invisibility effect.", impact = "Most servers should leave this at 0.")
        int ownerInvisibilityAmplifier = 0;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Smoke particles emitted around the invisible owner each tick while decoy is active.", impact = "Higher values create a stronger visible trail.")
        int ownerTrailParticles = 5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Horizontal spread for owner smoke trail.", impact = "Higher values make the trail wider.")
        double ownerTrailHorizontalSpread = 0.18;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Vertical spread for owner smoke trail.", impact = "Higher values make the trail taller.")
        double ownerTrailVerticalSpread = 0.05;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Vertical offset for smoke trail spawn location.", impact = "Adjust to move trail closer to feet or torso.")
        double ownerTrailYOffset = 0.1;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Particle speed for owner smoke trail.", impact = "Higher values make trail movement more turbulent.")
        double ownerTrailSpeed = 0.01;
        @art.arcane.adapt.util.config.ConfigDoc(value = "How often owner equipment-hide packets are resent while invisible, in milliseconds.", impact = "Lower values keep visuals tighter for joining viewers, higher values reduce packet traffic.")
        long ownerEquipmentHideResendMillis = 250;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Horizontal knockback applied when the decoy is hit.", impact = "Higher values make the decoy react more dramatically when struck.")
        double decoyHitKnockback = 0.28;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Vertical lift applied when the decoy is hit.", impact = "Higher values make impacts pop the decoy upward more.")
        double decoyHitLift = 0.08;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Swing ray distance used to detect decoy hits.", impact = "Higher values make swings connect from farther away.")
        double decoySwingDetectionReach = 4.5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Bitmask for visible skin layers on the fake player decoy.", impact = "127 enables all standard skin layers (hat, jacket, sleeves, pants).")
        int decoySkinLayerMask = 127;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Experience granted on each decoy spawn.", impact = "Higher values level the adaptation faster.")
        double xpOnDecoy = 18;
    }

    private record DecoyState(UUID anchorId, PacketPlayerDecoy packetDecoy, long expiresAt, int level) {
    }

    private static final class PacketPlayerDecoy {
        private final PacketDecoyBridge bridge;
        private final World world;
        private final UUID profileId;
        private final int entityId;
        private final Object nmsEntity;
        private final long removeTabAt;
        private final Set<UUID> knownViewers;
        private boolean removedFromTab;
        private Object spawnPlayerInfoPacket;
        private Object spawnAddEntityPacket;
        private Object spawnMetadataPacket;
        private Object spawnEquipmentPacket;
        private long lastPositionSyncAt;
        private double lastX;
        private double lastY;
        private double lastZ;
        private float lastYaw;
        private float lastPitch;

        private PacketPlayerDecoy(PacketDecoyBridge bridge, World world, UUID profileId, int entityId, Object nmsEntity, int tabListRemoveDelayTicks) {
            this.bridge = bridge;
            this.world = world;
            this.profileId = profileId;
            this.entityId = entityId;
            this.nmsEntity = nmsEntity;
            this.removeTabAt = System.currentTimeMillis() + Math.max(0, tabListRemoveDelayTicks) * 50L;
            this.knownViewers = new HashSet<>();
            this.removedFromTab = false;
            this.spawnPlayerInfoPacket = null;
            this.spawnAddEntityPacket = null;
            this.spawnMetadataPacket = null;
            this.spawnEquipmentPacket = null;
            this.lastPositionSyncAt = 0L;
            this.lastX = Double.NaN;
            this.lastY = Double.NaN;
            this.lastZ = Double.NaN;
            this.lastYaw = Float.NaN;
            this.lastPitch = Float.NaN;
        }

        public void spawn(Object playerInfoPacket, Object addEntityPacket, Object metadataPacket, Object equipmentPacket) {
            this.spawnPlayerInfoPacket = playerInfoPacket;
            this.spawnAddEntityPacket = addEntityPacket;
            this.spawnMetadataPacket = metadataPacket;
            this.spawnEquipmentPacket = equipmentPacket;
            ensureViewerState();
        }

        public void tick() {
            ensureViewerState();
            if (removeTabAt < 0 || removedFromTab || System.currentTimeMillis() < removeTabAt) {
                return;
            }

            Object removePacket = bridge.createPlayerInfoRemovePacket(profileId);
            if (removePacket != null) {
                for (Player viewer : spawnedViewerPlayers()) {
                    bridge.sendPacket(viewer, removePacket);
                }
            }

            removedFromTab = true;
        }

        public void lookAtViewers(Location origin) {
            ensureViewerState();
            for (Player viewer : spawnedViewerPlayers()) {
                Location to = viewer.getEyeLocation();
                if (origin.getWorld() != to.getWorld()) {
                    continue;
                }

                double dx = to.getX() - origin.getX();
                double dy = to.getY() - origin.getY();
                double dz = to.getZ() - origin.getZ();
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontal));
                bridge.applyLook(this, yaw, pitch, viewer);
            }
        }

        public void syncToAnchor(Location anchor, boolean onGround) {
            ensureViewerState();
            long now = System.currentTimeMillis();
            double dx = Double.isFinite(lastX) ? anchor.getX() - lastX : 1;
            double dy = Double.isFinite(lastY) ? anchor.getY() - lastY : 1;
            double dz = Double.isFinite(lastZ) ? anchor.getZ() - lastZ : 1;
            double distanceSq = (dx * dx) + (dy * dy) + (dz * dz);
            float yawDiff = Float.isFinite(lastYaw) ? Math.abs(anchor.getYaw() - lastYaw) : 360f;
            float pitchDiff = Float.isFinite(lastPitch) ? Math.abs(anchor.getPitch() - lastPitch) : 360f;

            if (distanceSq < 0.0004 && yawDiff < 0.8f && pitchDiff < 0.8f && now - lastPositionSyncAt < 45L) {
                return;
            }

            if (bridge.syncPosition(this, anchor, onGround, spawnedViewerPlayers())) {
                lastPositionSyncAt = now;
                lastX = anchor.getX();
                lastY = anchor.getY();
                lastZ = anchor.getZ();
                lastYaw = anchor.getYaw();
                lastPitch = anchor.getPitch();
            }
        }

        public void hitFrom(Location source) {
            ensureViewerState();
            if (!Double.isFinite(lastX) || !Double.isFinite(lastZ)) {
                bridge.sendHurtAnimation(this, 0f, spawnedViewerPlayers());
                return;
            }

            double dx = source.getX() - lastX;
            double dz = source.getZ() - lastZ;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            bridge.sendHurtAnimation(this, yaw, spawnedViewerPlayers());
        }

        public void destroy() {
            Object removeEntityPacket = bridge.createRemoveEntityPacket(entityId);
            Object removePlayerInfoPacket = bridge.createPlayerInfoRemovePacket(profileId);

            for (Player viewer : spawnedViewerPlayers()) {
                if (removeEntityPacket != null) {
                    bridge.sendPacket(viewer, removeEntityPacket);
                }
                if (removePlayerInfoPacket != null) {
                    bridge.sendPacket(viewer, removePlayerInfoPacket);
                }
            }

            knownViewers.clear();
        }

        private void ensureViewerState() {
            Set<UUID> online = new HashSet<>();
            for (Player viewer : world.getPlayers()) {
                if (!viewer.isOnline()) {
                    continue;
                }

                UUID id = viewer.getUniqueId();
                online.add(id);
                if (!knownViewers.contains(id)) {
                    spawnFor(viewer);
                    knownViewers.add(id);
                }
            }

            knownViewers.retainAll(online);
        }

        private void spawnFor(Player viewer) {
            if (spawnPlayerInfoPacket != null) {
                bridge.sendPacket(viewer, spawnPlayerInfoPacket);
            }

            if (spawnAddEntityPacket != null) {
                bridge.sendPacket(viewer, spawnAddEntityPacket);
            }

            if (spawnMetadataPacket != null) {
                bridge.sendPacket(viewer, spawnMetadataPacket);
            }

            if (spawnEquipmentPacket != null) {
                bridge.sendPacket(viewer, spawnEquipmentPacket);
            }

            if (removedFromTab) {
                Object removePacket = bridge.createPlayerInfoRemovePacket(profileId);
                if (removePacket != null) {
                    bridge.sendPacket(viewer, removePacket);
                }
            }
        }

        private List<Player> spawnedViewerPlayers() {
            List<Player> viewers = new ArrayList<>();
            for (Player viewer : world.getPlayers()) {
                if (viewer.isOnline() && knownViewers.contains(viewer.getUniqueId())) {
                    viewers.add(viewer);
                }
            }

            return viewers;
        }
    }

    private static final class PacketDecoyBridge {
        private final boolean supported;

        private final Method craftServerGetServer;
        private final Method craftWorldGetHandle;
        private final Method craftPlayerGetHandle;

        private final Constructor<?> serverPlayerConstructor;
        private final Method clientInformationCreateDefault;

        private final Constructor<?> gameProfileBasicConstructor;
        private final Constructor<?> gameProfileWithPropertiesConstructor;
        private final Method gameProfilePropertiesAccessor;
        private final Method playerGetGameProfile;

        private final Method entitySetPos;
        private final Method entitySetRot;
        private final Method entitySetOnGround;
        private final Method livingSetYHeadRot;
        private final Method livingSetYBodyRot;
        private final Method entityGetId;
        private final Method entityGetType;
        private final Method entityGetEntityData;
        private final Method synchedEntityDataGetNonDefaultValues;
        private final Method synchedEntityDataPackAll;
        private final Method synchedEntityDataSet;

        private final Method playerInfoCreateSingleInitializing;
        private final Constructor<?> playerInfoActionConstructor;
        private final Constructor<?> playerInfoFromEntriesConstructor;
        private final Constructor<?> playerInfoEntryExplicitConstructor;
        private final Class<?> playerInfoActionClass;
        private final Object defaultGameType;

        private final Constructor<?> addEntityConstructor;
        private final Constructor<?> addEntityExplicitConstructor;
        private final Field blockPosZero;
        private final Field vec3Zero;
        private final Constructor<?> setEntityDataConstructor;
        private final Constructor<?> moveEntityRotConstructor;
        private final Constructor<?> rotateHeadConstructor;
        private final Constructor<?> removeEntitiesConstructor;
        private final Constructor<?> playerInfoRemoveConstructor;
        private final Method entityPositionSyncOf;
        private final Constructor<?> hurtAnimationConstructor;
        private final Constructor<?> setEquipmentConstructor;
        private final Method pairOfMethod;
        private final Method craftItemStackAsNmsCopy;
        private final Class<?> equipmentSlotClass;
        private final Field avatarModelCustomizationAccessor;

        private final Field serverPlayerConnectionField;
        private final Method connectionSendPacket;

        private PacketDecoyBridge() throws ReflectiveOperationException {
            String craftPackage = Bukkit.getServer().getClass().getPackage().getName();
            if (!craftPackage.startsWith("org.bukkit.craftbukkit")) {
                throw new ClassNotFoundException("CraftBukkit package not detected: " + craftPackage);
            }

            Class<?> craftServerClass = Class.forName(craftPackage + ".CraftServer");
            Class<?> craftWorldClass = Class.forName(craftPackage + ".CraftWorld");
            Class<?> craftPlayerClass = Class.forName(craftPackage + ".entity.CraftPlayer");

            Class<?> minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer");
            Class<?> serverLevelClass = Class.forName("net.minecraft.server.level.ServerLevel");
            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            Class<?> clientInformationClass = Class.forName("net.minecraft.server.level.ClientInformation");
            Class<?> nmsPlayerClass = Class.forName("net.minecraft.world.entity.player.Player");
            Class<?> livingEntityClass = Class.forName("net.minecraft.world.entity.LivingEntity");
            Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
            Class<?> entityTypeClass = Class.forName("net.minecraft.world.entity.EntityType");
            Class<?> avatarClass = Class.forName("net.minecraft.world.entity.Avatar");
            Class<?> equipmentSlotClass = Class.forName("net.minecraft.world.entity.EquipmentSlot");
            Class<?> entityDataAccessorClass = Class.forName("net.minecraft.network.syncher.EntityDataAccessor");
            Class<?> synchedEntityDataClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData");
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Class<?> vec3Class = Class.forName("net.minecraft.world.phys.Vec3");
            Class<?> pairClass = Class.forName("com.mojang.datafixers.util.Pair");
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
            Class<?> connectionClass = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl");
            Class<?> playerInfoPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
            Class<?> playerInfoEntryClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry");
            Class<?> playerInfoActionEnumClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
            Class<?> gameTypeClass = Class.forName("net.minecraft.world.level.GameType");
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            Class<?> remoteChatSessionDataClass = Class.forName("net.minecraft.network.chat.RemoteChatSession$Data");
            Class<?> addEntityPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundAddEntityPacket");
            Class<?> entityPositionSyncPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket");
            Class<?> hurtAnimationPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket");
            Class<?> blockPosClass = Class.forName("net.minecraft.core.BlockPos");
            Class<?> setEntityDataPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");
            Class<?> moveEntityRotClass = Class.forName("net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$Rot");
            Class<?> rotateHeadPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundRotateHeadPacket");
            Class<?> removeEntitiesPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket");
            Class<?> playerInfoRemovePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
            Class<?> setEquipmentPacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket");
            Class<?> craftItemStackClass = Class.forName(craftPackage + ".inventory.CraftItemStack");

            this.craftServerGetServer = craftServerClass.getMethod("getServer");
            this.craftWorldGetHandle = craftWorldClass.getMethod("getHandle");
            this.craftPlayerGetHandle = craftPlayerClass.getMethod("getHandle");

            this.serverPlayerConstructor = serverPlayerClass.getConstructor(minecraftServerClass, serverLevelClass, gameProfileClass, clientInformationClass);
            this.clientInformationCreateDefault = clientInformationClass.getMethod("createDefault");

            this.gameProfileBasicConstructor = findConstructor(gameProfileClass, UUID.class, String.class);
            this.gameProfileWithPropertiesConstructor = findOptionalConstructor(gameProfileClass, UUID.class, String.class, findPropertyMapClass(gameProfileClass));
            this.gameProfilePropertiesAccessor = findOptionalMethod(gameProfileClass, "properties", "getProperties");
            this.playerGetGameProfile = nmsPlayerClass.getMethod("getGameProfile");

            this.entitySetPos = entityClass.getMethod("setPos", double.class, double.class, double.class);
            this.entitySetRot = entityClass.getMethod("setRot", float.class, float.class);
            this.entitySetOnGround = entityClass.getMethod("setOnGround", boolean.class);
            this.livingSetYHeadRot = livingEntityClass.getMethod("setYHeadRot", float.class);
            this.livingSetYBodyRot = livingEntityClass.getMethod("setYBodyRot", float.class);
            this.entityGetId = entityClass.getMethod("getId");
            this.entityGetType = entityClass.getMethod("getType");
            this.entityGetEntityData = entityClass.getMethod("getEntityData");
            this.synchedEntityDataGetNonDefaultValues = synchedEntityDataClass.getMethod("getNonDefaultValues");
            this.synchedEntityDataPackAll = findOptionalMethod(synchedEntityDataClass, "packAll", new Class<?>[0]);
            this.synchedEntityDataSet = synchedEntityDataClass.getMethod("set", entityDataAccessorClass, Object.class);

            this.playerInfoCreateSingleInitializing = findOptionalMethod(playerInfoPacketClass, "createSinglePlayerInitializing", serverPlayerClass, boolean.class);
            this.playerInfoActionConstructor = findOptionalConstructor(playerInfoPacketClass, playerInfoActionEnumClass, serverPlayerClass);
            this.playerInfoFromEntriesConstructor = findOptionalConstructor(playerInfoPacketClass, EnumSet.class, List.class);
            this.playerInfoEntryExplicitConstructor = findOptionalConstructor(playerInfoEntryClass, UUID.class, gameProfileClass, boolean.class, int.class, gameTypeClass, componentClass, boolean.class, int.class, remoteChatSessionDataClass);
            this.playerInfoActionClass = playerInfoActionEnumClass;
            this.defaultGameType = resolveDefaultGameType(gameTypeClass);

            this.addEntityConstructor = addEntityPacketClass.getConstructor(entityClass, int.class, blockPosClass);
            this.addEntityExplicitConstructor = findOptionalConstructor(addEntityPacketClass, int.class, UUID.class, double.class, double.class, double.class, float.class, float.class, entityTypeClass, int.class, vec3Class, double.class);
            this.blockPosZero = blockPosClass.getField("ZERO");
            this.vec3Zero = vec3Class.getField("ZERO");
            this.setEntityDataConstructor = setEntityDataPacketClass.getConstructor(int.class, List.class);
            this.moveEntityRotConstructor = moveEntityRotClass.getConstructor(int.class, byte.class, byte.class, boolean.class);
            this.rotateHeadConstructor = rotateHeadPacketClass.getConstructor(entityClass, byte.class);
            this.removeEntitiesConstructor = removeEntitiesPacketClass.getConstructor(int[].class);
            this.playerInfoRemoveConstructor = playerInfoRemovePacketClass.getConstructor(List.class);
            this.entityPositionSyncOf = entityPositionSyncPacketClass.getMethod("of", entityClass);
            this.hurtAnimationConstructor = findOptionalConstructor(hurtAnimationPacketClass, int.class, float.class);
            this.setEquipmentConstructor = findOptionalConstructor(setEquipmentPacketClass, int.class, List.class);
            this.pairOfMethod = pairClass.getMethod("of", Object.class, Object.class);
            this.craftItemStackAsNmsCopy = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            this.equipmentSlotClass = equipmentSlotClass;
            this.avatarModelCustomizationAccessor = avatarClass.getField("DATA_PLAYER_MODE_CUSTOMISATION");

            this.serverPlayerConnectionField = serverPlayerClass.getField("connection");
            this.connectionSendPacket = connectionClass.getMethod("send", packetClass);
            this.supported = true;
        }

        private PacketDecoyBridge(boolean supported) {
            this.supported = supported;
            this.craftServerGetServer = null;
            this.craftWorldGetHandle = null;
            this.craftPlayerGetHandle = null;
            this.serverPlayerConstructor = null;
            this.clientInformationCreateDefault = null;
            this.gameProfileBasicConstructor = null;
            this.gameProfileWithPropertiesConstructor = null;
            this.gameProfilePropertiesAccessor = null;
            this.playerGetGameProfile = null;
            this.entitySetPos = null;
            this.entitySetRot = null;
            this.entitySetOnGround = null;
            this.livingSetYHeadRot = null;
            this.livingSetYBodyRot = null;
            this.entityGetId = null;
            this.entityGetType = null;
            this.entityGetEntityData = null;
            this.synchedEntityDataGetNonDefaultValues = null;
            this.synchedEntityDataPackAll = null;
            this.synchedEntityDataSet = null;
            this.playerInfoCreateSingleInitializing = null;
            this.playerInfoActionConstructor = null;
            this.playerInfoFromEntriesConstructor = null;
            this.playerInfoEntryExplicitConstructor = null;
            this.playerInfoActionClass = null;
            this.defaultGameType = null;
            this.addEntityConstructor = null;
            this.addEntityExplicitConstructor = null;
            this.blockPosZero = null;
            this.vec3Zero = null;
            this.setEntityDataConstructor = null;
            this.moveEntityRotConstructor = null;
            this.rotateHeadConstructor = null;
            this.removeEntitiesConstructor = null;
            this.playerInfoRemoveConstructor = null;
            this.entityPositionSyncOf = null;
            this.hurtAnimationConstructor = null;
            this.setEquipmentConstructor = null;
            this.pairOfMethod = null;
            this.craftItemStackAsNmsCopy = null;
            this.equipmentSlotClass = null;
            this.avatarModelCustomizationAccessor = null;
            this.serverPlayerConnectionField = null;
            this.connectionSendPacket = null;
        }

        public static PacketDecoyBridge create() {
            try {
                return new PacketDecoyBridge();
            } catch (Throwable e) {
                Adapt.warn("Shadow decoy fake-player bridge unavailable: " + e.getClass().getSimpleName() + " " + e.getMessage());
                return new PacketDecoyBridge(false);
            }
        }

        public PacketPlayerDecoy spawnDecoy(Player owner, ArmorStand anchor, int tabListRemoveDelayTicks, int skinLayerMask) {
            Location location = anchor.getLocation();
            if (!supported || location.getWorld() == null) {
                return null;
            }

            try {
                Object ownerHandle = craftPlayerGetHandle.invoke(owner);
                Object ownerProfile = playerGetGameProfile.invoke(ownerHandle);
                UUID profileId = UUID.randomUUID();
                Object profile = createProfile(profileId, owner.getName(), ownerProfile);

                Object minecraftServer = craftServerGetServer.invoke(Bukkit.getServer());
                Object worldHandle = craftWorldGetHandle.invoke(location.getWorld());
                Object clientInfo = clientInformationCreateDefault.invoke(null);
                Object nmsDecoy = serverPlayerConstructor.newInstance(minecraftServer, worldHandle, profile, clientInfo);

                entitySetPos.invoke(nmsDecoy, location.getX(), location.getY(), location.getZ());
                entitySetRot.invoke(nmsDecoy, location.getYaw(), location.getPitch());
                livingSetYHeadRot.invoke(nmsDecoy, location.getYaw());
                livingSetYBodyRot.invoke(nmsDecoy, location.getYaw());
                applySkinLayers(nmsDecoy, skinLayerMask);

                int entityId = (int) entityGetId.invoke(nmsDecoy);
                Object playerInfoPacket = createPlayerInfoAddPacket(nmsDecoy, profileId, profile);
                Object addEntityPacket = createAddEntityPacket(nmsDecoy, entityId, profileId, location);
                Object metadataPacket = createMetadataPacket(nmsDecoy, entityId);
                Object equipmentPacket = createEquipmentPacket(entityId, owner, false, true);

                PacketPlayerDecoy decoy = new PacketPlayerDecoy(this, location.getWorld(), profileId, entityId, nmsDecoy, tabListRemoveDelayTicks);
                decoy.spawn(playerInfoPacket, addEntityPacket, metadataPacket, equipmentPacket);
                return decoy;
            } catch (Throwable e) {
                Throwable root = unwrapRootCause(e);
                Adapt.warn("Failed to spawn fake-player shadow decoy: " + root.getClass().getSimpleName() + " " + String.valueOf(root.getMessage()));
                return null;
            }
        }

        private Object createProfile(UUID id, String ownerName, Object ownerProfile) throws ReflectiveOperationException {
            String profileName = ownerName;
            if (profileName.length() > 16) {
                profileName = profileName.substring(0, 16);
            }

            if (gameProfileWithPropertiesConstructor != null && gameProfilePropertiesAccessor != null && ownerProfile != null) {
                Object properties = gameProfilePropertiesAccessor.invoke(ownerProfile);
                if (properties != null) {
                    return gameProfileWithPropertiesConstructor.newInstance(id, profileName, properties);
                }
            }

            return gameProfileBasicConstructor.newInstance(id, profileName);
        }

        private Object createPlayerInfoAddPacket(Object nmsDecoy, UUID profileId, Object profile) throws ReflectiveOperationException {
            ReflectiveOperationException last = null;

            if (playerInfoFromEntriesConstructor != null && playerInfoEntryExplicitConstructor != null && playerInfoActionClass != null && defaultGameType != null) {
                try {
                    Enum addAction = Enum.valueOf((Class<Enum>) playerInfoActionClass, "ADD_PLAYER");
                    EnumSet actions = buildInitializationActions(addAction);
                    Object entry = playerInfoEntryExplicitConstructor.newInstance(profileId, profile, true, 0, defaultGameType, null, true, 0, null);
                    return playerInfoFromEntriesConstructor.newInstance(actions, List.of(entry));
                } catch (ReflectiveOperationException e) {
                    last = e;
                }
            }

            if (playerInfoCreateSingleInitializing != null) {
                try {
                    return playerInfoCreateSingleInitializing.invoke(null, nmsDecoy, true);
                } catch (ReflectiveOperationException e) {
                    last = e;
                }
            }

            if (playerInfoActionConstructor != null && playerInfoActionClass != null) {
                try {
                    Object addAction = Enum.valueOf((Class<Enum>) playerInfoActionClass, "ADD_PLAYER");
                    return playerInfoActionConstructor.newInstance(addAction, nmsDecoy);
                } catch (ReflectiveOperationException e) {
                    last = e;
                }
            }

            if (last != null) {
                throw last;
            }

            throw new ReflectiveOperationException("No supported player-info add packet constructor found.");
        }

        private Object createAddEntityPacket(Object nmsDecoy, int entityId, UUID profileId, Location location) throws ReflectiveOperationException {
            if (addEntityExplicitConstructor != null && entityGetType != null && vec3Zero != null) {
                Object entityType = entityGetType.invoke(nmsDecoy);
                Object velocity = vec3Zero.get(null);
                return addEntityExplicitConstructor.newInstance(
                        entityId,
                        profileId,
                        location.getX(),
                        location.getY(),
                        location.getZ(),
                        location.getPitch(),
                        location.getYaw(),
                        entityType,
                        0,
                        velocity,
                        (double) location.getYaw()
                );
            }

            return addEntityConstructor.newInstance(nmsDecoy, 0, blockPosZero.get(null));
        }

        private void applySkinLayers(Object nmsDecoy, int mask) throws ReflectiveOperationException {
            if (avatarModelCustomizationAccessor == null || synchedEntityDataSet == null) {
                return;
            }

            Object accessor = avatarModelCustomizationAccessor.get(null);
            Object synchedData = entityGetEntityData.invoke(nmsDecoy);
            synchedEntityDataSet.invoke(synchedData, accessor, (byte) (mask & 0xFF));
        }

        private Object createEquipmentPacket(int entityId, Player owner, boolean hide, boolean includeEmptySlots) throws ReflectiveOperationException {
            if (setEquipmentConstructor == null || pairOfMethod == null || craftItemStackAsNmsCopy == null || equipmentSlotClass == null) {
                return null;
            }

            List<Object> slots = new ArrayList<>();
            ItemStack air = new ItemStack(Material.AIR);
            appendEquipment(slots, "HEAD", hide ? air : owner.getInventory().getHelmet(), includeEmptySlots);
            appendEquipment(slots, "CHEST", hide ? air : owner.getInventory().getChestplate(), includeEmptySlots);
            appendEquipment(slots, "LEGS", hide ? air : owner.getInventory().getLeggings(), includeEmptySlots);
            appendEquipment(slots, "FEET", hide ? air : owner.getInventory().getBoots(), includeEmptySlots);
            appendEquipment(slots, "MAINHAND", hide ? air : owner.getInventory().getItemInMainHand(), includeEmptySlots);
            appendEquipment(slots, "OFFHAND", hide ? air : owner.getInventory().getItemInOffHand(), includeEmptySlots);

            if (slots.isEmpty()) {
                return null;
            }

            return setEquipmentConstructor.newInstance(entityId, slots);
        }

        private void appendEquipment(List<Object> slots, String slotName, ItemStack stack, boolean includeEmptySlots) throws ReflectiveOperationException {
            ItemStack resolved = stack == null ? new ItemStack(Material.AIR) : stack;
            if (!includeEmptySlots && resolved.getType().isAir()) {
                return;
            }

            Object slot = Enum.valueOf((Class<Enum>) equipmentSlotClass, slotName);
            Object nmsStack = craftItemStackAsNmsCopy.invoke(null, resolved.clone());
            Object pair = pairOfMethod.invoke(null, slot, nmsStack);
            slots.add(pair);
        }

        private Object createMetadataPacket(Object nmsDecoy, int entityId) throws ReflectiveOperationException {
            Object synchedData = entityGetEntityData.invoke(nmsDecoy);
            Object packed = synchedEntityDataPackAll != null
                    ? synchedEntityDataPackAll.invoke(synchedData)
                    : synchedEntityDataGetNonDefaultValues.invoke(synchedData);
            if (!(packed instanceof List<?> values) || values.isEmpty()) {
                return null;
            }

            return setEntityDataConstructor.newInstance(entityId, values);
        }

        public Object createPlayerInfoRemovePacket(UUID profileId) {
            if (!supported) {
                return null;
            }

            try {
                return playerInfoRemoveConstructor.newInstance(List.of(profileId));
            } catch (Throwable e) {
                return null;
            }
        }

        public Object createRemoveEntityPacket(int entityId) {
            if (!supported) {
                return null;
            }

            try {
                return removeEntitiesConstructor.newInstance((Object) new int[]{entityId});
            } catch (Throwable e) {
                return null;
            }
        }

        public boolean applyLook(PacketPlayerDecoy decoy, float yaw, float pitch, Player viewer) {
            if (!supported) {
                return false;
            }

            try {
                entitySetRot.invoke(decoy.nmsEntity, yaw, pitch);
                livingSetYHeadRot.invoke(decoy.nmsEntity, yaw);
                livingSetYBodyRot.invoke(decoy.nmsEntity, yaw);

                byte yawByte = toAngle(yaw);
                byte pitchByte = toAngle(pitch);
                Object rotatePacket = moveEntityRotConstructor.newInstance(decoy.entityId, yawByte, pitchByte, true);
                Object headPacket = rotateHeadConstructor.newInstance(decoy.nmsEntity, yawByte);

                sendPacket(viewer, rotatePacket);
                sendPacket(viewer, headPacket);

                return true;
            } catch (Throwable e) {
                return false;
            }
        }

        public boolean syncPosition(PacketPlayerDecoy decoy, Location location, boolean onGround, List<Player> viewers) {
            if (!supported || entityPositionSyncOf == null) {
                return false;
            }

            try {
                entitySetPos.invoke(decoy.nmsEntity, location.getX(), location.getY(), location.getZ());
                entitySetRot.invoke(decoy.nmsEntity, location.getYaw(), location.getPitch());
                if (entitySetOnGround != null) {
                    entitySetOnGround.invoke(decoy.nmsEntity, onGround);
                }

                Object packet = entityPositionSyncOf.invoke(null, decoy.nmsEntity);
                if (packet == null) {
                    return false;
                }

                for (Player viewer : viewers) {
                    sendPacket(viewer, packet);
                }

                return true;
            } catch (Throwable e) {
                return false;
            }
        }

        public void sendHurtAnimation(PacketPlayerDecoy decoy, float yaw, List<Player> viewers) {
            if (!supported || hurtAnimationConstructor == null) {
                return;
            }

            try {
                Object packet = hurtAnimationConstructor.newInstance(decoy.entityId, yaw);
                for (Player viewer : viewers) {
                    sendPacket(viewer, packet);
                }
            } catch (Throwable ignored) {
            }
        }

        public void sendOwnerEquipment(Player owner, boolean hide) {
            if (!supported || owner == null || !owner.isOnline()) {
                return;
            }

            try {
                Object packet = createEquipmentPacket(owner.getEntityId(), owner, hide, true);
                if (packet == null) {
                    return;
                }

                for (Player viewer : new ArrayList<>(owner.getWorld().getPlayers())) {
                    if (viewer.getUniqueId().equals(owner.getUniqueId())) {
                        continue;
                    }
                    sendPacket(viewer, packet);
                }
            } catch (Throwable ignored) {
            }
        }

        public void sendPacket(Player viewer, Object packet) {
            if (!supported || packet == null || viewer == null || !viewer.isOnline()) {
                return;
            }

            try {
                Object handle = craftPlayerGetHandle.invoke(viewer);
                Object connection = serverPlayerConnectionField.get(handle);
                if (connection == null) {
                    return;
                }

                connectionSendPacket.invoke(connection, packet);
            } catch (Throwable ignored) {
            }
        }

        private static byte toAngle(float degrees) {
            return (byte) (degrees * 256.0F / 360.0F);
        }

        private static Constructor<?> findConstructor(Class<?> type, Class<?>... parameterTypes) throws NoSuchMethodException {
            Constructor<?> constructor = type.getConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor;
        }

        private static Constructor<?> findOptionalConstructor(Class<?> type, Class<?>... parameterTypes) {
            if (Arrays.stream(parameterTypes).anyMatch(c -> c == null)) {
                return null;
            }

            try {
                Constructor<?> constructor = type.getConstructor(parameterTypes);
                constructor.setAccessible(true);
                return constructor;
            } catch (NoSuchMethodException e) {
                return null;
            }
        }

        private static Method findOptionalMethod(Class<?> type, String name, Class<?>... parameterTypes) {
            try {
                Method method = type.getMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException e) {
                return null;
            }
        }

        private static Method findOptionalMethod(Class<?> type, String... methodNames) {
            for (String methodName : methodNames) {
                try {
                    Method method = type.getMethod(methodName);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
            }

            return null;
        }

        private static Class<?> findPropertyMapClass(Class<?> gameProfileClass) {
            for (Constructor<?> constructor : gameProfileClass.getConstructors()) {
                Class<?>[] params = constructor.getParameterTypes();
                if (params.length == 3 && params[0] == UUID.class && params[1] == String.class) {
                    return params[2];
                }
            }

            return null;
        }

        private static Object resolveDefaultGameType(Class<?> gameTypeClass) throws ReflectiveOperationException {
            try {
                Field defaultMode = gameTypeClass.getField("DEFAULT_MODE");
                Object value = defaultMode.get(null);
                if (value != null) {
                    return value;
                }
            } catch (NoSuchFieldException ignored) {
            }

            if (gameTypeClass.isEnum()) {
                try {
                    return Enum.valueOf((Class<Enum>) gameTypeClass, "SURVIVAL");
                } catch (IllegalArgumentException ignored) {
                }

                Object[] values = gameTypeClass.getEnumConstants();
                if (values != null && values.length > 0) {
                    return values[0];
                }
            }

            throw new ReflectiveOperationException("No default game mode could be resolved.");
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private EnumSet buildInitializationActions(Enum addAction) {
            EnumSet actions = EnumSet.noneOf((Class<Enum>) playerInfoActionClass);
            actions.add(addAction);

            String[] optionalActions = new String[]{
                    "INITIALIZE_CHAT",
                    "UPDATE_GAME_MODE",
                    "UPDATE_LISTED",
                    "UPDATE_LATENCY",
                    "UPDATE_DISPLAY_NAME",
                    "UPDATE_HAT",
                    "UPDATE_LIST_ORDER"
            };

            for (String name : optionalActions) {
                try {
                    actions.add(Enum.valueOf((Class<Enum>) playerInfoActionClass, name));
                } catch (IllegalArgumentException ignored) {
                }
            }

            return actions;
        }

        private static Throwable unwrapRootCause(Throwable throwable) {
            Throwable cursor = throwable;
            while (true) {
                if (cursor instanceof InvocationTargetException invocation && invocation.getCause() != null) {
                    cursor = invocation.getCause();
                    continue;
                }

                Throwable cause = cursor.getCause();
                if (cause == null || cause == cursor) {
                    return cursor;
                }

                cursor = cause;
            }
        }
    }
}
