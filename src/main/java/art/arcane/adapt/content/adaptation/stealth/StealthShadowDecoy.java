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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.entity.StackExclusion;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class StealthShadowDecoy extends SimpleAdaptation<StealthShadowDecoy.Config> {
  private static final PacketDecoyBridge PACKET_DECOY = PacketDecoyBridge.create();

  private final Cooldowns decoyCooldowns = cooldowns();
  private final Map<UUID, DecoySession> activeDecoys = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> anchorOwners = new ConcurrentHashMap<>();

  public StealthShadowDecoy() {
    super("stealth-shadow-decoy");
    registerConfiguration(Config.class);
    setIcon(Material.PLAYER_HEAD);
    setInterval(5);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ARMOR_STAND)
        .key("challenge_stealth_decoy_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ARMOR_STAND)
        .key("challenge_stealth_decoy_distract_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_stealth_decoy_100", "stealth.shadow-decoy.decoys-spawned", 100, 300);
    registerMilestone("challenge_stealth_decoy_distract_500", "stealth.shadow-decoy.mobs-distracted", 500, 1000);
  }

  public Entity activeDecoyAnchor(UUID ownerId) {
    if (ownerId == null) {
      return null;
    }

    DecoySession state = activeDecoys.get(ownerId);
    if (!isActive(state, System.currentTimeMillis())) {
      return null;
    }

    return state.anchor;
  }

  public boolean hasActiveDecoy(UUID ownerId) {
    return ownerId != null && isActive(activeDecoys.get(ownerId), System.currentTimeMillis());
  }

  static boolean isActive(DecoySession state, long now) {
    return state != null
        && state.active.get()
        && state.anchor != null
        && state.expiresAt > now;
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.duration(getDecoyTicks(level) * 50D, 1), 1);
    statLore(v, Form.f(getDecoyRadius(level)), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 3);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    UUID id = e.getPlayer().getUniqueId();
    decoyCooldowns.clear(id);
    DecoySession state = activeDecoys.get(id);
    if (state != null) {
      terminateDecoy(state, false);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    UUID ownerId = anchorOwners.get(e.getEntity().getUniqueId());
    if (ownerId == null) {
      return;
    }

    e.setCancelled(true);
    if (!(e instanceof EntityDamageByEntityEvent hit)
        || !(e.getEntity() instanceof ArmorStand stand)
        || !(hit.getDamager() instanceof LivingEntity attacker)) {
      return;
    }

    DecoySession state = activeDecoys.get(ownerId);
    if (state == null) {
      return;
    }

    reactToDecoyHit(state, stand, attacker.getLocation());
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

    DecoySession state = activeDecoys.get(ownerId);
    if (state == null) {
      return;
    }

    Location source = attacker.getLocation().clone();
    J.runEntity(stand, () -> reactToDecoyHit(state, stand, source));
  }

  private void reactToDecoyHit(DecoySession state, ArmorStand stand, Location source) {
    if (!state.active.get()) {
      return;
    }
    if (state.packetDecoy != null) {
      state.packetDecoy.hitFrom(source);
    }

    Vector push = stand.getLocation().toVector().subtract(source.toVector());
    push.setY(0);
    if (push.lengthSquared() < 1.0E-6) {
      Vector sourceHorizontal = source.getDirection().setY(0);
      push = sourceHorizontal.lengthSquared() < 1.0E-6 ? new Vector(0, 0, 1) : sourceHorizontal.multiply(-1);
    }

    push.normalize().multiply(Math.max(0, getConfig().decoyHitKnockback));
    push.setY(Math.max(0, getConfig().decoyHitLift));
    stand.setVelocity(push);
    fx(stand.getLocation().add(0, 1.2D, 0), FxPriority.COMBAT)
        .burst(Particles.SMOKE, 6, 0.25D)
        .burst(Particle.CRIT, 3, 0.2D)
        .chord(Sound.ENTITY_PLAYER_HURT, 0.6F, 1.2F, Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.3F, 1.4F);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    if (e.isSneaking() || p.hasMetadata("adapt-mutation-exposed")) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    UUID id = p.getUniqueId();
    if (!decoyCooldowns.isReady(id, getCooldownMillis(level))) {
      return;
    }

    spawnDecoy(p, level);
    decoyCooldowns.mark(id);
    xp(p, getConfig().xpOnDecoy);
    addStat(p, "stealth.shadow-decoy.decoys-spawned", 1);
  }

  private void spawnDecoy(Player owner, int level) {
    DecoySession previous = activeDecoys.get(owner.getUniqueId());
    if (previous != null) {
      terminateDecoy(previous, false);
    }

    ArmorStand anchor = spawnAnchor(owner.getLocation());
    anchorOwners.put(anchor.getUniqueId(), owner.getUniqueId());
    PacketPlayerDecoy packetDecoy = PACKET_DECOY.spawnDecoy(owner, anchor, getConfig().tabListRemoveDelayTicks, getConfig().decoySkinLayerMask);

    if (packetDecoy == null && getConfig().legacyFallbackEnabled) {
      configureLegacyVisual(anchor, owner);
    }

    long expiresAt = System.currentTimeMillis() + (getDecoyTicks(level) * 50L);
    UUID ownerId = owner.getUniqueId();
    DecoySession state = new DecoySession(owner, anchor, packetDecoy, expiresAt, level);
    activeDecoys.put(ownerId, state);

    refreshOwner(state);
    fx(owner.getLocation().add(0, 1.0D, 0), FxPriority.TRANSITION)
        .burst(Particle.REVERSE_PORTAL, 12, 0.3D)
        .chord(Sound.ENTITY_ENDERMAN_TELEPORT, 0.6F, 1.7F, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.5F, 1.0F);
    timeline(anchor).duration(8).priority(FxPriority.TRANSITION).cullRadius(32)
        .frame((fx, tick, progress) -> {
          fx.column(Particles.SMOKE, 2, 1.8D);
          fx.particle(Particle.SOUL, 1, 0, 0.2D + (progress * 1.5D), 0, 0.08D, 0.02D);
        }).start();
  }

  private ArmorStand spawnAnchor(Location location) {
    ArmorStand spawned = location.getWorld().spawn(location, ArmorStand.class, stand -> {
      StackExclusion.exclude(stand);
      stand.setPersistent(false);
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
    return spawned;
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

    ItemStack head = new ItemStack(Material.PLAYER_HEAD);
    if (head.getItemMeta() instanceof SkullMeta skullMeta) {
      skullMeta.setPlayerProfile(owner.getPlayerProfile());
      head.setItemMeta(skullMeta);
    }
    equipment.setHelmet(head);
    equipment.setChestplate(owner.getInventory().getChestplate());
    equipment.setLeggings(owner.getInventory().getLeggings());
    equipment.setBoots(owner.getInventory().getBoots());
    equipment.setItemInMainHand(owner.getInventory().getItemInMainHand());
    equipment.setItemInOffHand(owner.getInventory().getItemInOffHand());
  }

  private void redirectAggro(DecoySession state) {
    if (!state.active.get()) {
      return;
    }

    Player owner = state.owner;
    ArmorStand target = state.anchor;
    double radius = getDecoyRadius(state.level);
    Location decoyLocation = target.getLocation().clone();
    int remaining = Math.max(1, getConfig().maxAggroEntitiesPerScan);
    for (Entity entity : target.getNearbyEntities(radius, radius, radius)) {
      if (!(entity instanceof Mob mob)) {
        continue;
      }

      J.runEntity(mob, () -> {
        if (!state.active.get() || isProtectedFriendly(owner, mob)) {
          return;
        }

        if (mob.getTarget() == owner || mob.hasLineOfSight(owner)) {
          boolean newlyDistracted = mob.getTarget() != target;
          mob.setTarget(target);
          if (newlyDistracted) {
            J.runEntity(owner, () -> addStat(owner, "stealth.shadow-decoy.mobs-distracted", 1));
            fx(mob.getEyeLocation(), FxPriority.TRAIL)
                .line(Particles.SMOKE, decoyLocation.getX(), decoyLocation.getY() + 1.0D, decoyLocation.getZ(), 4);
          }
        }
      });
      remaining--;
      if (remaining <= 0) {
        return;
      }
    }
  }

  private void refreshOwner(DecoySession state) {
    Player owner = state.owner;
    long now = System.currentTimeMillis();
    if (!state.active.get()
        || activeDecoys.get(owner.getUniqueId()) != state
        || !owner.isOnline()
        || owner.hasMetadata("adapt-mutation-exposed")
        || state.expiresAt <= now) {
      terminateDecoy(state, true);
      return;
    }

    applyOwnerInvisibility(state);
    syncOwnerEquipmentHidden(state, now);
    if (now >= state.ownerTrailNextAt) {
      spawnOwnerTrail(owner);
      state.ownerTrailNextAt = now + Math.max(25L, getConfig().ownerTrailIntervalMillis);
    }

    if (!J.runEntity(state.anchor, () -> refreshAnchor(state, now))) {
      terminateDecoy(state, true);
      return;
    }
    if (!J.runEntity(owner, () -> refreshOwner(state), 1)) {
      terminateDecoy(state, false);
    }
  }

  private void refreshAnchor(DecoySession state, long now) {
    ArmorStand anchor = state.anchor;
    if (!state.active.get() || !anchor.isValid()) {
      terminateDecoy(state, true);
      return;
    }

    PacketPlayerDecoy packetDecoy = state.packetDecoy;
    if (packetDecoy != null) {
      packetDecoy.refresh(
          anchor.getLocation(),
          anchor.isOnGround(),
          anchor.getTrackedPlayers(),
          Math.max(1, getConfig().maxPacketViewers),
          Math.max(1, getConfig().maxViewerAddsPerRefresh),
          Math.max(1, getConfig().maxViewerLookUpdatesPerRefresh),
          getConfig().decoyEyeHeight
      );
    }

    if (now >= state.ownerAggroNextAt) {
      redirectAggro(state);
      state.ownerAggroNextAt = now + Math.max(25L, getConfig().aggroRedirectIntervalMillis);
    }
  }

  private void applyOwnerInvisibility(DecoySession state) {
    Player owner = state.owner;
    int duration = Math.max(20, getConfig().ownerInvisibilityRefreshTicks);
    PotionEffect current = owner.getPotionEffect(PotionEffectType.INVISIBILITY);
    if (current != null && current.getDuration() > duration + 5) {
      state.ownerInvisibilityApplied = false;
      return;
    }

    owner.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, duration, getConfig().ownerInvisibilityAmplifier, false, false, false), true);
    state.ownerInvisibilityApplied = true;
  }

  private void spawnOwnerTrail(Player owner) {
    fx(owner.getLocation().add(0, getConfig().ownerTrailYOffset, 0), FxPriority.TRAIL)
        .particle(
            Particles.SMOKE,
            Math.max(1, getConfig().ownerTrailParticles),
            0, 0, 0,
            Math.max(0, getConfig().ownerTrailHorizontalSpread),
            Math.max(0, getConfig().ownerTrailSpeed));
  }

  private void syncOwnerEquipmentHidden(DecoySession state, long now) {
    if (now < state.ownerEquipmentNextAt) {
      return;
    }

    PACKET_DECOY.sendOwnerEquipment(state.owner, true, Math.max(1, getConfig().maxPacketViewers));
    state.ownerEquipmentNextAt = now + Math.max(100L, getConfig().ownerEquipmentHideResendMillis);
  }

  private void restoreOwnerState(DecoySession state, boolean feedback) {
    Player owner = state.owner;
    if (!owner.isOnline()) {
      return;
    }

    if (state.ownerInvisibilityApplied) {
      PotionEffect current = owner.getPotionEffect(PotionEffectType.INVISIBILITY);
      int duration = Math.max(20, getConfig().ownerInvisibilityRefreshTicks);
      if (current != null
          && current.getAmplifier() == getConfig().ownerInvisibilityAmplifier
          && current.getDuration() <= duration + 5) {
        owner.removePotionEffect(PotionEffectType.INVISIBILITY);
      }
    }
    state.ownerInvisibilityApplied = false;
    PACKET_DECOY.sendOwnerEquipment(owner, false, Math.max(1, getConfig().maxPacketViewers));
    if (feedback) {
      fx(owner.getLocation().add(0, 1.0D, 0), FxPriority.TRANSITION)
          .ring(Particles.END_ROD, 0.6D, 6, 1.0D)
          .sound(Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.3F, 1.0F);
    }
  }

  private void terminateDecoy(DecoySession state, boolean feedback) {
    if (!state.active.compareAndSet(true, false)) {
      return;
    }

    activeDecoys.remove(state.owner.getUniqueId(), state);
    anchorOwners.remove(state.anchor.getUniqueId(), state.owner.getUniqueId());
    if (state.packetDecoy != null) {
      state.packetDecoy.destroy();
    }

    J.runEntity(state.owner, () -> restoreOwnerState(state, feedback));
    J.runEntity(state.anchor, () -> {
      Location decoyLocation = state.anchor.getLocation();
      if (state.anchor.isValid()) {
        state.anchor.remove();
      }

      if (feedback) {
        fx(decoyLocation.add(0, 1.0D, 0), FxPriority.TRANSITION)
            .burst(Particles.SMOKE, 10, 0.3D)
            .sound(Sound.ENTITY_ENDERMAN_TELEPORT, 0.4F, 0.8F);
      }
    });
  }

  @Override
  public void unregister() {
    super.unregister();
    for (DecoySession state : activeDecoys.values()) {
      terminateDecoy(state, false);
    }
    activeDecoys.clear();
    anchorOwners.clear();
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

  @ConfigDescription("Stopping sneak spawns a short-lived shadow decoy that pulls your current aggro.")
  protected static class Config extends AdaptationConfig {
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
    @art.arcane.adapt.util.config.ConfigDoc(value = "Delay before removing the skinned fake player from tab list, in ticks.", impact = "The default two seconds lets clients resolve the owner's skin before the entry is hidden; negative values keep it listed.")
    int tabListRemoveDelayTicks = 40;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Allows armor stand visual fallback if packet NPC creation fails.", impact = "Turn off to disable fallback visuals on incompatible server builds; disabling leaves an invisible decoy when the packet bridge fails.")
    boolean legacyFallbackEnabled = true;
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
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between owner trail particle bursts while decoy is active.", impact = "Lower values make the owner trail denser; higher values reduce particle cost.")
    long ownerTrailIntervalMillis = 75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "How often owner equipment-hide packets are resent while invisible, in milliseconds.", impact = "Lower values keep visuals tighter for joining viewers, higher values reduce packet traffic.")
    long ownerEquipmentHideResendMillis = 250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between aggro redirect scans while a decoy is active.", impact = "Lower values pull mobs more aggressively; higher values reduce nearby-entity scan cost.")
    long aggroRedirectIntervalMillis = 150;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum mobs dispatched by each decoy aggro scan.", impact = "Caps scheduler pressure in dense farms while preserving frequent nearby redirects.")
    int maxAggroEntitiesPerScan = 32;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum players sent fake-decoy and equipment packets for one decoy.", impact = "Bounds packet fanout in very dense hubs; tracked viewers beyond the cap rely on the armor-stand fallback state.")
    int maxPacketViewers = 64;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum newly tracked viewers initialized during each decoy refresh.", impact = "Spreads join and tracking bursts across ticks instead of producing one large packet spike.")
    int maxViewerAddsPerRefresh = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum viewer-facing rotations sent during each decoy refresh.", impact = "Bounds look packet work while keeping the fake player responsive to nearby viewers.")
    int maxViewerLookUpdatesPerRefresh = 16;
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

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }

  private static class DecoySession {
    private final Player owner;
    private final ArmorStand anchor;
    private final PacketPlayerDecoy packetDecoy;
    private final long expiresAt;
    private final int level;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private long ownerEquipmentNextAt;
    private long ownerTrailNextAt;
    private long ownerAggroNextAt;
    private boolean ownerInvisibilityApplied;

    private DecoySession(Player owner, ArmorStand anchor, PacketPlayerDecoy packetDecoy, long expiresAt, int level) {
      this.owner = owner;
      this.anchor = anchor;
      this.packetDecoy = packetDecoy;
      this.expiresAt = expiresAt;
      this.level = level;
    }
  }

}
