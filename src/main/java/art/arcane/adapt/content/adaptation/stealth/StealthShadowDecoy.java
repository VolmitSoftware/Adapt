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

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class StealthShadowDecoy extends SimpleAdaptation<StealthShadowDecoy.Config> {
  private static final PacketDecoyBridge PACKET_DECOY = PacketDecoyBridge.create();

  private final Map<UUID, Long> cooldowns = new java.util.concurrent.ConcurrentHashMap<>();
  private final Map<UUID, DecoyState> activeDecoys = new java.util.concurrent.ConcurrentHashMap<>();
  private final Map<UUID, UUID> anchorOwners = new java.util.concurrent.ConcurrentHashMap<>();
  private final Map<UUID, Long> ownerEquipmentMaskSync = new java.util.concurrent.ConcurrentHashMap<>();
  private final Map<UUID, Long> ownerTrailNextAt = new java.util.concurrent.ConcurrentHashMap<>();
  private final Map<UUID, Long> ownerAggroNextAt = new java.util.concurrent.ConcurrentHashMap<>();

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
    ownerTrailNextAt.remove(id);
    ownerAggroNextAt.remove(id);
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
    if (e.isSneaking()) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

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
    UUID ownerId = owner.getUniqueId();
    activeDecoys.put(ownerId, new DecoyState(ownerId, anchor.getUniqueId(), packetDecoy, expiresAt, level));
    ownerTrailNextAt.put(ownerId, 0L);
    ownerAggroNextAt.put(ownerId, 0L);

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
      if (now >= ownerTrailNextAt.getOrDefault(ownerId, 0L)) {
        spawnOwnerTrail(owner);
        ownerTrailNextAt.put(ownerId, now + Math.max(25L, getConfig().ownerTrailIntervalMillis));
      }
      if (now >= ownerAggroNextAt.getOrDefault(ownerId, 0L)) {
        redirectAggro(owner, anchor, state.level());
        ownerAggroNextAt.put(ownerId, now + Math.max(25L, getConfig().aggroRedirectIntervalMillis));
      }
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
    ownerTrailNextAt.remove(state.ownerId());
    ownerAggroNextAt.remove(state.ownerId());
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
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between owner trail particle bursts while decoy is active.", impact = "Lower values make the owner trail denser; higher values reduce particle cost.")
    long ownerTrailIntervalMillis = 75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "How often owner equipment-hide packets are resent while invisible, in milliseconds.", impact = "Lower values keep visuals tighter for joining viewers, higher values reduce packet traffic.")
    long ownerEquipmentHideResendMillis = 250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between aggro redirect scans while a decoy is active.", impact = "Lower values pull mobs more aggressively; higher values reduce nearby-entity scan cost.")
    long aggroRedirectIntervalMillis = 150;
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

}
