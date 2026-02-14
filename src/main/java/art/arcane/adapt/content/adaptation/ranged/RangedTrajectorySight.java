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

package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.volmlib.util.format.Form;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import fr.skytasul.glowingentities.GlowingEntities;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RangedTrajectorySight extends SimpleAdaptation<RangedTrajectorySight.Config> {
    private static final double EPSILON = 0.0000001D;
    private final Map<UUID, Long> drawStartedMillis = new HashMap<>();
    private final Map<UUID, UUID> previewGlowTargets = new HashMap<>();
    private volatile RangedForce cachedRangedForce;
    private volatile RangedRicochetBolt cachedRicochetBolt;

    public RangedTrajectorySight() {
        super("ranged-trajectory-sight");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("ranged.trajectory_sight.description"));
        setDisplayName(Localizer.dLocalize("ranged.trajectory_sight.name"));
        setIcon(Material.SPYGLASS);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(2);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.SPYGLASS)
                .key("challenge_ranged_trajectory_100")
                .title(Localizer.dLocalize("advancement.challenge_ranged_trajectory_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_ranged_trajectory_100.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerMilestone("challenge_ranged_trajectory_100", "ranged.trajectory-sight.kills-while-aiming", 100, 400);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getVelocityMultiplier(level)) + C.GRAY + " " + Localizer.dLocalize("ranged.trajectory_sight.lore1"));
        v.addLore(C.GREEN + "+ " + Form.f(getSegments(level)) + C.GRAY + " " + Localizer.dLocalize("ranged.trajectory_sight.lore2"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        drawStartedMillis.remove(p.getUniqueId());
        clearPreviewGlow(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(PlayerInteractEvent e) {
        if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player p = e.getPlayer();
        if (!hasAdaptation(p)) {
            return;
        }

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!isItem(hand)) {
            return;
        }

        Material type = hand.getType();
        if (type != Material.BOW && type != Material.CROSSBOW) {
            return;
        }

        switch (e.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> drawStartedMillis.put(p.getUniqueId(), System.currentTimeMillis());
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityShootBowEvent e) {
        if (e.getEntity() instanceof Player p) {
            drawStartedMillis.remove(p.getUniqueId());
        }
    }

    @EventHandler
    public void on(EntityDeathEvent e) {
        if (e.getEntity().getKiller() instanceof Player p && hasAdaptation(p)) {
            if (e.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent dmg
                    && dmg.getDamager() instanceof Projectile projectile
                    && projectile.getShooter() instanceof Player) {
                getPlayer(p).getData().addStat("ranged.trajectory-sight.kills-while-aiming", 1);
            }
        }
    }

    @Override
    public void onTick() {
        for (art.arcane.adapt.api.world.AdaptPlayer adaptPlayer : getServer().getOnlineAdaptPlayerSnapshot()) {
            Player p = adaptPlayer.getPlayer();
            if (!hasAdaptation(p)) {
                drawStartedMillis.remove(p.getUniqueId());
                clearPreviewGlow(p);
                continue;
            }

            PreviewContext context = resolvePreviewContext(p);
            if (context == null) {
                drawStartedMillis.remove(p.getUniqueId());
                clearPreviewGlow(p);
                continue;
            }

            if (context.trigger() != PreviewTrigger.DRAWING_BOW) {
                drawStartedMillis.remove(p.getUniqueId());
            }

            ShotPreview shot = getShotPreview(p, context);
            if (shot == null) {
                clearPreviewGlow(p);
                continue;
            }

            UUID predictedHit = renderTrajectory(p, getSegments(getLevel(p)), shot);
            updatePreviewGlow(p, predictedHit);
        }
    }

    private PreviewContext resolvePreviewContext(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();

        if (isDrawingBow(p, main)) {
            return new PreviewContext(main, PreviewTrigger.DRAWING_BOW);
        }

        if (!p.isSneaking()) {
            return null;
        }

        if (isSneakProjectile(main)) {
            return new PreviewContext(main, PreviewTrigger.SNEAK_PROJECTILE);
        }

        if (isSneakProjectile(off)) {
            return new PreviewContext(off, PreviewTrigger.SNEAK_PROJECTILE);
        }

        return null;
    }

    private ShotPreview getShotPreview(Player p, PreviewContext context) {
        Material launchType = context.item().getType();
        double launchVelocity = getLaunchVelocity(p, launchType);
        if (launchVelocity <= 0.01) {
            return null;
        }

        BallisticsProfile profile = resolveBallisticsProfile(launchType);
        Vector direction = applyLaunchDirectionTuning(launchType, p.getEyeLocation().getDirection().clone());
        if (direction.lengthSquared() <= EPSILON) {
            return null;
        }

        Vector velocity = direction.normalize().multiply(launchVelocity);
        velocity.multiply(getRangedForceLaunchMultiplier(p));
        RicochetPreview ricochet = getRicochetPreview(p);
        if (!supportsRicochet(launchType, ricochet)) {
            ricochet = RicochetPreview.disabled();
        }
        return new ShotPreview(velocity, ricochet, profile, context.trigger());
    }

    private double getLaunchVelocity(Player p, Material type) {
        if (type == Material.SNOWBALL || type == Material.EGG || type == Material.ENDER_PEARL) {
            return getConfig().thrownProjectileVelocity;
        }

        if (type == Material.SPLASH_POTION || type == Material.LINGERING_POTION || type == Material.EXPERIENCE_BOTTLE) {
            return getConfig().thrownPotionVelocity;
        }

        if (type == Material.TRIDENT) {
            return getConfig().tridentVelocity;
        }

        if (type == Material.BOW) {
            double force = getBowForce(p);
            if (force <= 0) {
                return 0;
            }

            return force * 3.0;
        }

        if (type == Material.CROSSBOW) {
            return getConfig().crossbowVelocity;
        }

        return getConfig().fallbackVelocity;
    }

    private double getBowForce(Player p) {
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        long start = drawStartedMillis.computeIfAbsent(id, k -> now);
        double chargeTicks = Math.max(0, (now - start) / 50.0);

        if (!p.isHandRaised() && p.isSneaking()) {
            chargeTicks = getConfig().sneakPreviewChargeTicks;
        }

        double force = chargeTicks / 20.0;
        force = (force * force + force * 2.0) / 3.0;
        return Math.min(1.0, force);
    }

    private UUID renderTrajectory(Player p, int segments, ShotPreview shot) {
        Location eye = p.getEyeLocation().clone();
        Location current = eye.clone().add(p.getEyeLocation().getDirection().normalize().multiply(getConfig().previewStartOffset));
        Vector velocity = shot.initialVelocity().clone();
        Color trailColor = shot.trigger() == PreviewTrigger.DRAWING_BOW
                ? Color.fromRGB(120, 225, 255)
                : Color.fromRGB(255, 190, 125);
        int every = Math.max(1, getConfig().trailParticleEvery);
        double minDistanceSq = Math.max(0, getConfig().minPreviewDistanceFromEye);
        minDistanceSq *= minDistanceSq;
        int ricochets = 0;
        Location lastVisiblePoint = null;
        UUID hitEntityId = null;

        for (int i = 0; i < segments; i++) {
            Vector step = velocity.clone();
            double stepLength = step.length();
            if (stepLength <= EPSILON) {
                return hitEntityId;
            }

            Vector stepDirection = step.clone().normalize();
            Location from = current.clone();
            RayTraceResult entityHit = p.getWorld().rayTraceEntities(current, stepDirection, stepLength, entity -> isValidPreviewTarget(p, entity));
            RayTraceResult hit = p.getWorld().rayTraceBlocks(current, stepDirection, stepLength, FluidCollisionMode.NEVER, true);
            if (isEntityFirstHit(current, hit, entityHit)) {
                Entity target = entityHit.getHitEntity();
                if (target != null) {
                    hitEntityId = target.getUniqueId();
                }

                if (entityHit.getHitPosition() != null) {
                    current = entityHit.getHitPosition().toLocation(p.getWorld());
                } else if (target != null) {
                    current = target.getLocation().clone();
                }

                if (areParticlesEnabled() && eye.distanceSquared(current) >= minDistanceSq) {
                    float impactSize = getScaledParticleSize(eye.distance(current), 1.15D);
                    Particle.DustOptions impact = new Particle.DustOptions(Color.fromRGB(255, 236, 128), impactSize);
                    p.spawnParticle(Particle.DUST, current, Math.max(1, getConfig().impactParticleCount + 1), 0.02, 0.02, 0.02, 0.0, impact);
                }
                break;
            }

            if (hit != null && hit.getHitBlock() != null) {
                current = hit.getHitPosition().toLocation(p.getWorld());
                if (areParticlesEnabled()) {
                    if (eye.distanceSquared(current) >= minDistanceSq) {
                        float impactSize = getScaledParticleSize(eye.distance(current), 1.1D);
                        Particle.DustOptions impact = new Particle.DustOptions(Color.fromRGB(255, 236, 128), impactSize);
                        p.spawnParticle(Particle.DUST, current, Math.max(1, getConfig().impactParticleCount), 0.02, 0.02, 0.02, 0.0, impact);
                    }
                }

                RicochetPreview ricochet = shot.ricochetPreview();
                if (canRicochet(ricochet, ricochets, velocity)) {
                    BlockFace hitFace = hit.getHitBlockFace() == null ? resolveHitFace(velocity) : hit.getHitBlockFace();
                    if (hitFace == null) {
                        break;
                    }

                    Vector reflectedDir = reflect(velocity.clone().normalize(), hitFace);
                    if (reflectedDir.lengthSquared() <= EPSILON) {
                        break;
                    }

                    reflectedDir.normalize();
                    double nextSpeed = Math.max(ricochet.minimumPostBounceSpeed(), velocity.length()) * (1D + ricochet.speedBonusPerRicochet());
                    velocity = reflectedDir.clone().multiply(nextSpeed);
                    current.add(hitFace.getDirection().normalize().multiply(ricochet.spawnOffsetFromSurface()))
                            .add(reflectedDir.clone().multiply(ricochet.spawnOffsetAlongDirection()));
                    ricochets++;
                    if (areParticlesEnabled()) {
                        if (eye.distanceSquared(current) >= minDistanceSq) {
                            float bounceSize = getScaledParticleSize(eye.distance(current), 1.15D);
                            Particle.DustOptions bounce = new Particle.DustOptions(Color.fromRGB(170, 200, 255), bounceSize);
                            p.spawnParticle(Particle.DUST, current, Math.max(1, getConfig().impactParticleCount), 0.02, 0.02, 0.02, 0.0, bounce);
                        }
                    }
                    continue;
                }

                break;
            }

            current.add(step);

            if (i % every == 0) {
                if (areParticlesEnabled()) {
                    if (eye.distanceSquared(current) >= minDistanceSq) {
                        Location delta = current.clone().subtract(from);
                        int subSteps = Math.max(1, getConfig().trailSubSteps);
                        for (int s = 1; s <= subSteps; s++) {
                            double f = (double) s / (double) subSteps;
                            Location point = from.clone().add(delta.clone().multiply(f));
                            if (eye.distanceSquared(point) < minDistanceSq) {
                                continue;
                            }

                            float trailSize = getScaledParticleSize(eye.distance(point), 1D);
                            Particle.DustOptions trail = new Particle.DustOptions(trailColor, trailSize);
                            p.spawnParticle(Particle.DUST, point, Math.max(1, getConfig().trailParticleCount), 0.0, 0.0, 0.0, 0.0, trail);
                            lastVisiblePoint = point;
                        }
                    }
                }
            }

            velocity.multiply(shot.profile().dragFactor());
            velocity.setY(velocity.getY() - shot.profile().gravityStep());
        }

        if (areParticlesEnabled() && lastVisiblePoint != null) {
            float tipSize = getScaledParticleSize(eye.distance(lastVisiblePoint), 1.2D);
            Particle.DustOptions impact = new Particle.DustOptions(Color.fromRGB(255, 236, 128), tipSize);
            p.spawnParticle(Particle.DUST, lastVisiblePoint, 1, 0.0, 0.0, 0.0, 0.0, impact);
        }
        return hitEntityId;
    }

    private BallisticsProfile resolveBallisticsProfile(Material type) {
        if (type == Material.SNOWBALL || type == Material.EGG || type == Material.ENDER_PEARL) {
            return new BallisticsProfile(getConfig().lightProjectileDragFactor, getConfig().lightProjectileGravityStep);
        }

        if (type == Material.SPLASH_POTION || type == Material.LINGERING_POTION || type == Material.EXPERIENCE_BOTTLE) {
            return new BallisticsProfile(getConfig().heavyProjectileDragFactor, getConfig().heavyProjectileGravityStep);
        }

        return new BallisticsProfile(getConfig().dragFactor, getConfig().gravityStep);
    }

    private Vector applyLaunchDirectionTuning(Material type, Vector direction) {
        if (type == Material.SPLASH_POTION || type == Material.LINGERING_POTION || type == Material.EXPERIENCE_BOTTLE) {
            direction.setY(direction.getY() - getConfig().heavyProjectilePitchDrop);
        }

        return direction;
    }

    private double getRangedForceLaunchMultiplier(Player p) {
        RangedForce force = getRangedForceAdaptation();
        if (force == null || !force.isEnabled() || !force.getSkill().isEnabled()) {
            return 1D;
        }

        int level = getAdaptationLevel(p, force.getName());
        if (level <= 0) {
            return 1D;
        }

        double levelPercent = Math.min(1D, Math.max(0D, (double) level / (double) Math.max(1, force.getMaxLevel())));
        double speedBonus = levelPercent * force.getConfig().speedFactor;
        return Math.max(0.1D, 1D + speedBonus);
    }

    private RicochetPreview getRicochetPreview(Player p) {
        RangedRicochetBolt ricochet = getRicochetAdaptation();
        if (ricochet == null || !ricochet.isEnabled() || !ricochet.getSkill().isEnabled()) {
            return RicochetPreview.disabled();
        }

        int level = getAdaptationLevel(p, ricochet.getName());
        if (level <= 0) {
            return RicochetPreview.disabled();
        }

        RangedRicochetBolt.Config cfg = ricochet.getConfig();
        double levelPercent = Math.min(1D, Math.max(0D, (double) level / (double) Math.max(1, ricochet.getMaxLevel())));
        int maxRicochets = Math.max(1, (int) Math.round(cfg.maxRicochetsBase + (levelPercent * cfg.maxRicochetsFactor)));
        double speedBonus = Math.min(cfg.maxSpeedBonusPerRicochet,
                cfg.speedBonusPerRicochetBase + (levelPercent * cfg.speedBonusPerRicochetFactor));
        return new RicochetPreview(
                true,
                maxRicochets,
                speedBonus,
                cfg.minRicochetVelocitySquared,
                cfg.minimumPostBounceSpeed,
                cfg.spawnOffsetFromSurface,
                cfg.spawnOffsetAlongDirection,
                cfg.applyToAllProjectiles
        );
    }

    private int getAdaptationLevel(Player p, String adaptationId) {
        PlayerSkillLine line = getPlayer(p).getData().getSkillLineNullable("ranged");
        return line == null ? 0 : line.getAdaptationLevel(adaptationId);
    }

    private RangedForce getRangedForceAdaptation() {
        RangedForce cached = cachedRangedForce;
        if (cached != null) {
            return cached;
        }

        RangedForce found = resolveRangedAdaptation(RangedForce.class);
        if (found != null) {
            cachedRangedForce = found;
        }
        return found;
    }

    private RangedRicochetBolt getRicochetAdaptation() {
        RangedRicochetBolt cached = cachedRicochetBolt;
        if (cached != null) {
            return cached;
        }

        RangedRicochetBolt found = resolveRangedAdaptation(RangedRicochetBolt.class);
        if (found != null) {
            cachedRicochetBolt = found;
        }
        return found;
    }

    private <T> T resolveRangedAdaptation(Class<T> type) {
        Skill<?> ranged = Adapt.instance.getAdaptServer().getSkillRegistry().getSkill("ranged");
        if (ranged == null) {
            return null;
        }

        for (Adaptation<?> adaptation : ranged.getAdaptations()) {
            if (type.isInstance(adaptation)) {
                return type.cast(adaptation);
            }
        }

        return null;
    }

    private boolean canRicochet(RicochetPreview ricochet, int ricochetCount, Vector incomingVelocity) {
        return ricochet.enabled()
                && ricochetCount < ricochet.maxRicochets()
                && incomingVelocity.lengthSquared() >= ricochet.minRicochetVelocitySquared();
    }

    private float getScaledParticleSize(double distance, double multiplier) {
        double size = getConfig().particleSize + (Math.max(0D, distance) * getConfig().particleSizePerBlock);
        size *= multiplier;
        size = Math.max(0.05D, Math.min(getConfig().maxParticleSize, size));
        return (float) size;
    }

    private boolean isEntityFirstHit(Location from, RayTraceResult blockHit, RayTraceResult entityHit) {
        if (entityHit == null || entityHit.getHitEntity() == null) {
            return false;
        }

        if (blockHit == null || blockHit.getHitPosition() == null) {
            return true;
        }

        if (entityHit.getHitPosition() == null) {
            return false;
        }

        double blockDistSq = blockHit.getHitPosition().distanceSquared(from.toVector());
        double entityDistSq = entityHit.getHitPosition().distanceSquared(from.toVector());
        return entityDistSq <= blockDistSq;
    }

    private boolean isValidPreviewTarget(Player shooter, Entity entity) {
        return entity instanceof LivingEntity
                && entity.isValid()
                && !entity.isDead()
                && entity.getUniqueId() != shooter.getUniqueId();
    }

    private void updatePreviewGlow(Player p, UUID targetId) {
        if (!getConfig().glowPredictedTarget) {
            clearPreviewGlow(p);
            return;
        }

        UUID viewerId = p.getUniqueId();
        UUID current = previewGlowTargets.get(viewerId);
        if (current != null && current.equals(targetId)) {
            return;
        }

        GlowingEntities glowingEntities = Adapt.instance.getGlowingEntities();
        if (glowingEntities == null) {
            return;
        }

        if (current != null) {
            Entity stale = Bukkit.getEntity(current);
            if (stale != null) {
                try {
                    glowingEntities.unsetGlowing(stale, p);
                } catch (ReflectiveOperationException ignored) {
                    // Ignore and continue; preview should never hard-fail from packet glow.
                }
            }
            previewGlowTargets.remove(viewerId);
        }

        if (targetId == null) {
            return;
        }

        Entity target = Bukkit.getEntity(targetId);
        if (target == null || !target.isValid()) {
            return;
        }

        try {
            glowingEntities.setGlowing(target, p, ChatColor.GOLD);
            previewGlowTargets.put(viewerId, targetId);
        } catch (ReflectiveOperationException ignored) {
            // Ignore and continue; preview should never hard-fail from packet glow.
        }
    }

    private void clearPreviewGlow(Player p) {
        UUID viewerId = p.getUniqueId();
        UUID targetId = previewGlowTargets.remove(viewerId);
        if (targetId == null) {
            return;
        }

        GlowingEntities glowingEntities = Adapt.instance.getGlowingEntities();
        if (glowingEntities == null) {
            return;
        }

        Entity entity = Bukkit.getEntity(targetId);
        if (entity == null) {
            return;
        }

        try {
            glowingEntities.unsetGlowing(entity, p);
        } catch (ReflectiveOperationException ignored) {
            // Ignore and continue; preview should never hard-fail from packet glow.
        }
    }

    private boolean supportsRicochet(Material launchType, RicochetPreview ricochet) {
        if (!ricochet.enabled()) {
            return false;
        }

        if (launchType == Material.BOW || launchType == Material.CROSSBOW || launchType == Material.TRIDENT) {
            return true;
        }

        return ricochet.applyToAllProjectiles()
                && (launchType == Material.SNOWBALL
                || launchType == Material.EGG
                || launchType == Material.ENDER_PEARL
                || launchType == Material.SPLASH_POTION
                || launchType == Material.LINGERING_POTION
                || launchType == Material.EXPERIENCE_BOTTLE);
    }

    private boolean isDrawingBow(Player p, ItemStack main) {
        if (!isItem(main)) {
            return false;
        }

        Material type = main.getType();
        if (type != Material.BOW && type != Material.CROSSBOW) {
            return false;
        }

        if (!p.isHandRaised()) {
            return false;
        }

        ItemStack active = p.getItemInUse();
        return isItem(active) && active.getType() == type;
    }

    private boolean isSneakProjectile(ItemStack item) {
        if (!isItem(item)) {
            return false;
        }

        Material type = item.getType();
        return type == Material.BOW
                || type == Material.CROSSBOW
                || type == Material.TRIDENT
                || type == Material.SNOWBALL
                || type == Material.EGG
                || type == Material.ENDER_PEARL
                || type == Material.SPLASH_POTION
                || type == Material.LINGERING_POTION
                || type == Material.EXPERIENCE_BOTTLE;
    }

    private Vector reflect(Vector incoming, BlockFace face) {
        Vector normal = face.getDirection().normalize();
        double dot = incoming.dot(normal);
        return incoming.clone().subtract(normal.multiply(2D * dot));
    }

    private BlockFace resolveHitFace(Vector incoming) {
        double ax = Math.abs(incoming.getX());
        double ay = Math.abs(incoming.getY());
        double az = Math.abs(incoming.getZ());

        if (ay >= ax && ay >= az) {
            return incoming.getY() > 0 ? BlockFace.DOWN : BlockFace.UP;
        }

        if (ax >= az) {
            return incoming.getX() > 0 ? BlockFace.WEST : BlockFace.EAST;
        }

        return incoming.getZ() > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
    }

    private record PreviewContext(ItemStack item, PreviewTrigger trigger) {
    }

    private enum PreviewTrigger {
        SNEAK_PROJECTILE,
        DRAWING_BOW
    }

    private record BallisticsProfile(double dragFactor, double gravityStep) {
    }

    private record ShotPreview(Vector initialVelocity, RicochetPreview ricochetPreview, BallisticsProfile profile, PreviewTrigger trigger) {
    }

    private record RicochetPreview(boolean enabled, int maxRicochets, double speedBonusPerRicochet,
                                   double minRicochetVelocitySquared, double minimumPostBounceSpeed,
                                   double spawnOffsetFromSurface, double spawnOffsetAlongDirection,
                                   boolean applyToAllProjectiles) {
        private static RicochetPreview disabled() {
            return new RicochetPreview(false, 0, 0D, Double.MAX_VALUE, 0D, 0D, 0D, false);
        }
    }

    private int getSegments(int level) {
        return Math.max(10, (int) Math.round(getConfig().segmentsBase + (getLevelPercent(level) * getConfig().segmentsFactor)));
    }

    private double getVelocityMultiplier(int level) {
        return Math.max(0.1, getConfig().velocityBase + (getLevelPercent(level) * getConfig().velocityFactor));
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
    @ConfigDescription("Preview ranged projectile flight while sneaking or drawing your shot.")
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
        double costFactor = 0.75;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Segments Base for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double segmentsBase = 18;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Segments Factor for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double segmentsFactor = 26;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Velocity Base for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double velocityBase = 1.0;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Velocity Factor for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double velocityFactor = 0.18;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Gravity Step for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double gravityStep = 0.05;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Step Scale for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double stepScale = 0.45;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Drag Factor for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double dragFactor = 0.99;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Drag factor used for lighter thrown projectiles (snowballs, eggs, pearls).", impact = "Higher values keep thrown arcs flatter and longer; lower values make them lose speed faster.")
        double lightProjectileDragFactor = 0.99;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Drag factor used for heavier thrown projectiles (potions, experience bottles).", impact = "Higher values keep heavier throws moving faster; lower values shorten their travel.")
        double heavyProjectileDragFactor = 0.99;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Gravity step used for lighter thrown projectiles (snowballs, eggs, pearls).", impact = "Higher values produce steeper arcs for light projectiles.")
        double lightProjectileGravityStep = 0.03;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Gravity step used for heavier thrown projectiles (potions, experience bottles).", impact = "Higher values cause potions and bottles to drop faster.")
        double heavyProjectileGravityStep = 0.05;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Crossbow Velocity for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double crossbowVelocity = 3.15;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Launch velocity used for trident previews while sneaking.", impact = "Higher values extend trident prediction distance.")
        double tridentVelocity = 2.5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Launch velocity used for light thrown projectile previews.", impact = "Higher values extend snowball, egg, and pearl prediction distance.")
        double thrownProjectileVelocity = 1.5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Launch velocity used for potion and experience bottle previews.", impact = "Higher values extend heavy throw prediction distance.")
        double thrownPotionVelocity = 0.5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Additional downward launch offset for heavy thrown projectile previews.", impact = "Higher values tilt potion and bottle trajectories downward more strongly.")
        double heavyProjectilePitchDrop = 0.12;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fallback Velocity for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double fallbackVelocity = 1.6;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Sneak Preview Charge Ticks for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double sneakPreviewChargeTicks = 16;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Particle Size for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double particleSize = 0.13;
        @art.arcane.adapt.util.config.ConfigDoc(value = "How much particle size grows per block of distance from the viewer.", impact = "Higher values make far trajectory points easier to see.")
        double particleSizePerBlock = 0.008;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum particle size used for the trajectory preview.", impact = "Caps distance scaling to prevent oversized particles.")
        double maxParticleSize = 0.45;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Trail Particle Count for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int trailParticleCount = 1;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Impact Particle Count for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int impactParticleCount = 2;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Trail Particle Every for the Ranged Trajectory Sight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        int trailParticleEvery = 3;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum distance from the player's eye before preview particles are shown.", impact = "Higher values keep particles out of your sightline and reduce visual obstruction.")
        double minPreviewDistanceFromEye = 1.6;
        @art.arcane.adapt.util.config.ConfigDoc(value = "How many interpolated points are drawn between each simulated physics step.", impact = "Higher values smooth the line while increasing particle density.")
        int trailSubSteps = 1;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Offset forward from the eye where trajectory simulation begins.", impact = "Higher values start the preview further from your face.")
        double previewStartOffset = 0.55;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Highlights the predicted hit target entity with per-player glow.", impact = "Enable to glow whichever entity the preview would hit first.")
        boolean glowPredictedTarget = true;
    }
}
