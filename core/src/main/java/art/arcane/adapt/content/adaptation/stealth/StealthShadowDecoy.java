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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.api.minion.MinionKind;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.localization.catalog.StealthMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.ArmorStandStyle;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerAnimationEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.viewer.ViewerHost;
import art.arcane.adapt.platform.viewer.VirtualPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.data.StackExclusion;
import art.arcane.volmlib.util.format.Form;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class StealthShadowDecoy extends SimpleAdaptation<StealthShadowDecoy.Config> {
  public static final PlayerPreference<Gesture> GESTURE = new PlayerPreference<>(Gesture.class,
      new PlayerPreference.Definition<>("gesture", StealthMessages.STEALTHSHADOWDECOY_PREFERENCE_GESTURE, Gesture.RELEASE, List.of(
          new PlayerPreference.Choice<>(Gesture.RELEASE, StealthMessages.STEALTHSHADOWDECOY_PREFERENCE_GESTURE_RELEASE, Id.minecraft("leather_boots"), 1),
          new PlayerPreference.Choice<>(Gesture.DOUBLE, StealthMessages.STEALTHSHADOWDECOY_PREFERENCE_GESTURE_DOUBLE, Id.minecraft("rabbit_foot"), 1),
          new PlayerPreference.Choice<>(Gesture.ARMED_RELEASE, StealthMessages.STEALTHSHADOWDECOY_PREFERENCE_GESTURE_ARMED_RELEASE, Id.minecraft("shield"), 1))));
  private static final MinionKind MINION_KIND = new DecoyMinionKind();

  private final Map<UUID, Long> lastGesturePress = playerState();
  private final Map<UUID, Boolean> armedRelease = playerState();
  private final Map<UUID, Long> gestureSpawnEvents = playerState();
  private static final DataKey<String> DECOY_OWNER_KEY = DataKey.adapt("shadow_decoy_owner", DataCodecs.STRING);
  private static final Id INVISIBILITY = Id.minecraft("invisibility");
  private static final EquipmentSlot[] BODY_SLOTS = {
      EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAIN_HAND, EquipmentSlot.OFF_HAND
  };

  private final Cooldowns decoyCooldowns = cooldowns();
  private final Map<UUID, DecoySession> activeDecoys = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> anchorOwners = new ConcurrentHashMap<>();

  public StealthShadowDecoy() {
    super("stealth-shadow-decoy");
    MinionIdentity.registerKind(MINION_KIND);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("player_head"));
    setInterval(5);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("armor_stand"))
        .key("challenge_stealth_decoy_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("armor_stand"))
        .key("challenge_stealth_decoy_distract_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_stealth_decoy_100", "stealth.shadow-decoy.decoys-spawned", 100, 300);
    registerMilestone("challenge_stealth_decoy_distract_500", "stealth.shadow-decoy.mobs-distracted", 500, 1000);
  }

  public static boolean isDecoy(EntityRef target) {
    return target != null
        && target.has(EntityTrait.ARMOR_STAND)
        && target.data().has(DECOY_OWNER_KEY);
  }

  public EntityRef activeDecoyAnchor(UUID ownerId) {
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
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, GESTURE);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    PlayerRef p = player.getPlayer();
    lastGesturePress.remove(p.uuid());
    armedRelease.remove(p.uuid());
    gestureSpawnEvents.remove(p.uuid());
    if (!isPlayerEnabled(p)) {
      DecoySession state = activeDecoys.get(p.uuid());
      if (state != null) {
        terminateDecoy(state, true);
      }
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.duration(getDecoyTicks(level) * 50D, 1), 1);
    statLore(v, Form.f(getDecoyRadius(level)), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    UUID id = e.player().uuid();
    decoyCooldowns.clear(id);
    DecoySession state = activeDecoys.get(id);
    if (state != null) {
      terminateDecoy(state, false);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    UUID ownerId = anchorOwners.get(e.entity().uuid());
    if (ownerId == null) {
      return;
    }

    e.setCancelled(true);
    if (!(e instanceof EntityDamageByEntityEvent hit)
        || !e.entity().has(EntityTrait.ARMOR_STAND)
        || !(hit.damager() instanceof LivingRef attacker)) {
      return;
    }

    DecoySession state = activeDecoys.get(ownerId);
    if (state == null) {
      return;
    }

    reactToDecoyHit(state, e.entity(), attacker.location());
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerAnimationEvent e) {
    PlayerRef attacker = e.player();
    if (activeDecoys.isEmpty()) {
      return;
    }

    Loc eye = attacker.eyeLocation();
    EntityRef hit = attacker.world().rayTraceEntity(
        eye.position(),
        eye.direction(),
        Math.max(1.0, getConfig().decoySwingDetectionReach),
        0.0D,
        entity -> anchorOwners.containsKey(entity.uuid())
    );

    if (hit == null || !hit.has(EntityTrait.ARMOR_STAND)) {
      return;
    }

    UUID ownerId = anchorOwners.get(hit.uuid());
    if (ownerId == null) {
      return;
    }

    DecoySession state = activeDecoys.get(ownerId);
    if (state == null) {
      return;
    }

    Loc source = attacker.location();
    Tasks.runEntity(hit, () -> reactToDecoyHit(state, hit, source));
  }

  private void reactToDecoyHit(DecoySession state, EntityRef stand, Loc source) {
    if (!state.active.get()) {
      return;
    }
    if (state.packetDecoy != null) {
      state.packetDecoy.hitFrom(source);
    }

    Vec3 push = stand.location().position().subtract(source.position()).withY(0);
    if (push.lengthSquared() < 1.0E-6) {
      Vec3 sourceHorizontal = source.direction().withY(0);
      push = sourceHorizontal.lengthSquared() < 1.0E-6 ? new Vec3(0, 0, 1) : sourceHorizontal.multiply(-1);
    }

    push = push.normalize().multiply(Math.max(0, getConfig().decoyHitKnockback)).withY(Math.max(0, getConfig().decoyHitLift));
    stand.setVelocity(push);
    fx(stand.location().add(0, 1.2D, 0), FxPriority.COMBAT)
        .burst(Particles.SMOKE, 6, 0.25D)
        .burst(Particles.CRIT, 3, 0.2D)
        .chord(Sounds.ENTITY_PLAYER_HURT, 0.6F, 1.2F, Sounds.BLOCK_SCULK_SENSOR_CLICKING, 0.3F, 1.4F);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    Gesture gesture = preference(p, GESTURE);
    if (gesture == Gesture.DOUBLE) {
      if (!e.isSneaking() || swapReservesGesture(p)) {
        return;
      }
      long now = System.currentTimeMillis();
      Long last = lastGesturePress.put(p.uuid(), now);
      if (last == null || now - last > 350L) {
        return;
      }
      lastGesturePress.remove(p.uuid());
    } else {
      if (e.isSneaking() || swapReservesGesture(p)) {
        return;
      }
      if (gesture == Gesture.ARMED_RELEASE && armedRelease.remove(p.uuid()) == null) {
        return;
      }
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    UUID id = p.uuid();
    long cooldownMillis = getCooldownMillis(level);
    if (!decoyCooldowns.isReady(id, cooldownMillis)) {
      return;
    }

    if (gesture == Gesture.DOUBLE) {
      gestureSpawnEvents.put(id, e.dispatchId());
    }
    spawnDecoy(p, level);
    decoyCooldowns.mark(id, cooldownMillis);
    xp(p, getConfig().xpOnDecoy);
    addStat(p, "stealth.shadow-decoy.decoys-spawned", 1);
  }

  public boolean spawnedOn(PlayerToggleSneakEvent event) {
    Long spawnedOn = gestureSpawnEvents.get(event.player().uuid());
    return spawnedOn != null && spawnedOn == event.dispatchId();
  }

  private boolean swapReservesGesture(PlayerRef player) {
    for (Adaptation<?> candidate : getSkill().getAdaptations()) {
      if (candidate instanceof StealthDecoySwap swap && swap.reservesDoubleSneak(player)) {
        return true;
      }
    }
    return false;
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    PlayerRef p = e.player();
    if (e.hand() == Hand.MAIN_HAND && e.action() == InteractAction.RIGHT_CLICK_AIR
        && p.isSneaking() && p.inventory().mainHand().type().isAir()
        && preference(p, GESTURE) == Gesture.ARMED_RELEASE && hasActiveAdaptation(p)) {
      armedRelease.put(p.uuid(), true);
    }
  }

  private void spawnDecoy(PlayerRef owner, int level) {
    DecoySession previous = activeDecoys.get(owner.uuid());
    if (previous != null) {
      terminateDecoy(previous, false);
    }

    Loc location = owner.location();
    VirtualPlayer nativePlayer = AdaptCore.platform().viewers().createVirtualPlayer(owner, location, getConfig().decoySkinLayerMask);
    PacketPlayerDecoy packetDecoy = nativePlayer == null ? null : new PacketPlayerDecoy(nativePlayer, getConfig().tabListRemoveDelayTicks);
    boolean legacyVisual = packetDecoy == null && getConfig().legacyFallbackEnabled;
    EntityRef anchor = spawnAnchor(owner, location, legacyVisual);
    if (anchor == null) {
      return;
    }
    anchorOwners.put(anchor.uuid(), owner.uuid());

    if (legacyVisual) {
      configureLegacyVisual(anchor, owner);
    }

    long now = System.currentTimeMillis();
    long expiresAt = now + (getDecoyTicks(level) * 50L);
    UUID ownerId = owner.uuid();
    DecoySession state = new DecoySession(owner, anchor, packetDecoy, expiresAt, level);
    activeDecoys.put(ownerId, state);
    AbilityGlyphs.duration(ownerId, this, now, expiresAt - now);

    refreshOwner(state);
    fx(owner.location().add(0, 1.0D, 0), FxPriority.TRANSITION)
        .burst(Particles.REVERSE_PORTAL, 12, 0.3D)
        .chord(Sounds.ENTITY_ENDERMAN_TELEPORT, 0.6F, 1.7F, Sounds.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.5F, 1.0F);
    timeline(anchor).duration(8).priority(FxPriority.TRANSITION).cullRadius(32)
        .frame((fx, tick, progress) -> {
          fx.column(Particles.SMOKE, 2, 1.8D);
          fx.particle(Particles.SOUL, 1, 0, 0.2D + (progress * 1.5D), 0, 0.08D, 0.02D);
        }).start();
  }

  EntityRef spawnAnchor(PlayerRef owner, Loc location, boolean legacyVisual) {
    return location.world().spawnArmorStand(location, new ArmorStandStyle(false, false, false, true, legacyVisual), stand -> {
      stand.data().set(DECOY_OWNER_KEY, owner.uuid().toString());
      StackExclusion.exclude(stand);
      stand.setPersistent(false);
      AdaptCore.platform().viewers().setVisibleByDefault(stand, false);
      if (stand instanceof LivingRef living) {
        living.setInvisible(false);
      }
      stand.setGravity(true);
      stand.setInvulnerable(false);
      stand.setSilent(true);
    });
  }

  void configureLegacyVisual(EntityRef stand, PlayerRef owner) {
    AdaptCore.platform().viewers().setVisibleByDefault(stand, true);
    if (!(stand instanceof LivingRef living)) {
      return;
    }
    living.setInvisible(false);
    living.setCustomName(C.GRAY + owner.name(), true);

    PlayerInventoryRef inventory = owner.inventory();
    living.setEquipment(EquipmentSlot.HEAD, AdaptCore.platform().host().items().playerHead(owner));
    living.setEquipment(EquipmentSlot.CHEST, inventory.armor(EquipmentSlot.CHEST));
    living.setEquipment(EquipmentSlot.LEGS, inventory.armor(EquipmentSlot.LEGS));
    living.setEquipment(EquipmentSlot.FEET, inventory.armor(EquipmentSlot.FEET));
    living.setEquipment(EquipmentSlot.MAIN_HAND, inventory.mainHand());
    living.setEquipment(EquipmentSlot.OFF_HAND, inventory.offHand());
  }

  private void redirectAggro(DecoySession state, Loc ownerEyeLocation) {
    if (!state.active.get()) {
      return;
    }

    PlayerRef owner = state.owner;
    UUID ownerId = state.ownerId;
    EntityRef target = state.anchor;
    if (!(target instanceof LivingRef decoy)) {
      return;
    }
    double radius = getDecoyRadius(state.level);
    Loc decoyLocation = target.location();
    int remaining = Math.max(1, getConfig().maxAggroEntitiesPerScan);
    for (EntityRef entity : target.nearbyEntities(radius, radius, radius)) {
      if (!(entity instanceof MobRef mob)) {
        continue;
      }

      Tasks.runEntity(mob, () -> {
        if (!state.active.get() || isProtectedFriendlyToOwner(ownerId, mob)) {
          return;
        }

        LivingRef current = mob.target();
        if ((current != null && current.uuid().equals(ownerId)) || hasClearPath(mob, ownerEyeLocation)) {
          boolean newlyDistracted = current == null || !current.uuid().equals(decoy.uuid());
          mob.setTarget(decoy);
          if (newlyDistracted) {
            Tasks.runEntity(owner, () -> addStat(owner, "stealth.shadow-decoy.mobs-distracted", 1));
            fx(mob.eyeLocation(), FxPriority.TRAIL)
                .line(Particles.SMOKE, decoyLocation.x(), decoyLocation.y() + 1.0D, decoyLocation.z(), 4);
          }
        }
      });
      remaining--;
      if (remaining <= 0) {
        return;
      }
    }
  }

  private static boolean hasClearPath(LivingRef entity, Loc target) {
    Loc eye = entity.eyeLocation();
    if (!eye.sameWorld(target)) {
      return false;
    }
    Vec3 direction = target.position().subtract(eye.position());
    double distance = direction.length();
    if (distance < 1.0e-6) {
      return true;
    }
    return eye.world().rayTraceBlockHit(eye.position(), direction, distance, false) == null;
  }

  private void refreshOwner(DecoySession state) {
    PlayerRef owner = state.owner;
    long now = System.currentTimeMillis();
    if (!state.active.get()
        || activeDecoys.get(owner.uuid()) != state
        || !owner.isOnline() || !isPlayerEnabled(owner)
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

    Loc ownerEyeLocation = owner.eyeLocation();
    if (!Tasks.runEntity(state.anchor, () -> refreshAnchor(state, now, ownerEyeLocation))) {
      terminateDecoy(state, true);
      return;
    }
    if (!Tasks.runEntity(owner, () -> refreshOwner(state), 1)) {
      terminateDecoy(state, false);
    }
  }

  private void refreshAnchor(DecoySession state, long now, Loc ownerEyeLocation) {
    EntityRef anchor = state.anchor;
    if (!state.active.get() || !anchor.isValid()) {
      terminateDecoy(state, true);
      return;
    }

    PacketPlayerDecoy packetDecoy = state.packetDecoy;
    if (packetDecoy != null) {
      Loc location = anchor.location();
      packetDecoy.refresh(
          location,
          anchor.isOnGround(),
          AdaptCore.platform().viewers().chunkViewers(location.world(), location.chunkX(), location.chunkZ()),
          Math.max(1, getConfig().maxPacketViewers),
          Math.max(1, getConfig().maxViewerAddsPerRefresh),
          Math.max(1, getConfig().maxViewerLookUpdatesPerRefresh),
          getConfig().decoyEyeHeight
      );
    }

    if (now >= state.ownerAggroNextAt) {
      redirectAggro(state, ownerEyeLocation);
      state.ownerAggroNextAt = now + Math.max(25L, getConfig().aggroRedirectIntervalMillis);
    }
  }

  private boolean isProtectedFriendlyToOwner(UUID ownerId, MobRef mob) {
    if (isProtectedFriendly(null, mob)) {
      return true;
    }
    if (!AdaptCore.platform().host().animals().isTamed(mob)) {
      return false;
    }
    return ownerId.equals(mob.tameOwner());
  }

  private void applyOwnerInvisibility(DecoySession state) {
    PlayerRef owner = state.owner;
    int duration = Math.max(20, getConfig().ownerInvisibilityRefreshTicks);
    EffectInstance current = owner.effect(INVISIBILITY);
    if (current != null && current.durationTicks() > duration + 5) {
      state.ownerInvisibilityApplied = false;
      return;
    }

    owner.addEffect(new EffectInstance(INVISIBILITY, duration, getConfig().ownerInvisibilityAmplifier, false, false, false));
    state.ownerInvisibilityApplied = true;
  }

  private void spawnOwnerTrail(PlayerRef owner) {
    fx(owner.location().add(0, getConfig().ownerTrailYOffset, 0), FxPriority.TRAIL)
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

    sendOwnerEquipment(state.owner, true, Math.max(1, getConfig().maxPacketViewers));
    state.ownerEquipmentNextAt = now + Math.max(100L, getConfig().ownerEquipmentHideResendMillis);
  }

  private void restoreOwnerState(DecoySession state, boolean feedback) {
    PlayerRef owner = state.owner;
    if (!owner.isOnline()) {
      return;
    }

    if (state.ownerInvisibilityApplied) {
      EffectInstance current = owner.effect(INVISIBILITY);
      int duration = Math.max(20, getConfig().ownerInvisibilityRefreshTicks);
      if (current != null
          && current.amplifier() == getConfig().ownerInvisibilityAmplifier
          && current.durationTicks() <= duration + 5) {
        owner.removeEffect(INVISIBILITY);
      }
    }
    state.ownerInvisibilityApplied = false;
    sendOwnerEquipment(owner, false, Math.max(1, getConfig().maxPacketViewers));
    if (feedback) {
      fx(owner.location().add(0, 1.0D, 0), FxPriority.TRANSITION)
          .ring(Particles.END_ROD, 0.6D, 6, 1.0D)
          .sound(Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.3F, 1.0F);
    }
  }

  private void terminateDecoy(DecoySession state, boolean feedback) {
    if (!state.active.compareAndSet(true, false)) {
      return;
    }

    activeDecoys.remove(state.ownerId, state);
    AbilityGlyphs.clear(state.ownerId, this, HudGlyph.DURATION);
    anchorOwners.remove(state.anchor.uuid(), state.ownerId);
    if (state.packetDecoy != null) {
      state.packetDecoy.destroy();
    }

    Tasks.runEntity(state.owner, () -> restoreOwnerState(state, feedback));
    Tasks.runEntity(state.anchor, () -> {
      Loc decoyLocation = state.anchor.location();
      if (state.anchor.isValid()) {
        state.anchor.remove();
      }

      if (feedback) {
        fx(decoyLocation.add(0, 1.0D, 0), FxPriority.TRANSITION)
            .burst(Particles.SMOKE, 10, 0.3D)
            .sound(Sounds.ENTITY_ENDERMAN_TELEPORT, 0.4F, 0.8F);
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
    private final PlayerRef owner;
    private final UUID ownerId;
    private final EntityRef anchor;
    private final PacketPlayerDecoy packetDecoy;
    private final long expiresAt;
    private final int level;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private long ownerEquipmentNextAt;
    private long ownerTrailNextAt;
    private long ownerAggroNextAt;
    private boolean ownerInvisibilityApplied;

    private DecoySession(PlayerRef owner, EntityRef anchor, PacketPlayerDecoy packetDecoy, long expiresAt, int level) {
      this.owner = owner;
      ownerId = owner.uuid();
      this.anchor = anchor;
      this.packetDecoy = packetDecoy;
      this.expiresAt = expiresAt;
      this.level = level;
    }
  }

  private void sendOwnerEquipment(PlayerRef owner, boolean hidden, int maxViewers) {
    if (!owner.isOnline()) {
      return;
    }
    Map<EquipmentSlot, ItemRef> items = ownerEquipment(owner, hidden);
    ViewerHost viewers = AdaptCore.platform().viewers();
    int remaining = Math.max(1, maxViewers);
    for (PlayerRef viewer : viewers.trackingPlayers(owner)) {
      if (viewer.uuid().equals(owner.uuid())) {
        continue;
      }
      Tasks.runEntity(viewer, () -> {
        if (viewer.isOnline()) {
          viewers.sendEquipment(viewer, owner, items);
        }
      });
      if (--remaining == 0) {
        return;
      }
    }
  }

  private static Map<EquipmentSlot, ItemRef> ownerEquipment(PlayerRef owner, boolean hidden) {
    Map<EquipmentSlot, ItemRef> items = new EnumMap<>(EquipmentSlot.class);
    PlayerInventoryRef inventory = owner.inventory();
    for (EquipmentSlot slot : BODY_SLOTS) {
      if (hidden) {
        items.put(slot, AdaptCore.platform().host().items().empty());
      } else if (slot == EquipmentSlot.MAIN_HAND) {
        items.put(slot, inventory.mainHand());
      } else if (slot == EquipmentSlot.OFF_HAND) {
        items.put(slot, inventory.offHand());
      } else {
        items.put(slot, inventory.armor(slot));
      }
    }
    return items;
  }

  private static final class DecoyMinionKind implements MinionKind {
    @Override
    public boolean matches(EntityRef entity) {
      return isDecoy(entity);
    }

    @Override
    public UUID ownerOf(EntityRef entity) {
      String raw = entity.data().get(DECOY_OWNER_KEY);
      if (raw == null) {
        return null;
      }
      try {
        return UUID.fromString(raw);
      } catch (IllegalArgumentException ignored) {
        return null;
      }
    }
  }

  public enum Gesture { RELEASE, DOUBLE, ARMED_RELEASE }
}
