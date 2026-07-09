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

package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.adapt.util.reflect.registries.PotionEffectTypes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChronosStasisField extends SimpleAdaptation<ChronosStasisField.Config> {
  private final Map<UUID, Long> cooldowns;
  private final List<StasisBubble> bubbles;
  private final Map<UUID, FrozenProjectileState> frozenProjectiles;

  public ChronosStasisField() {
    super("chronos-stasis-field");
    registerConfiguration(Config.class);
    setIcon(Material.AMETHYST_SHARD);
    setInterval(250);
    cooldowns = new ConcurrentHashMap<>();
    bubbles = new CopyOnWriteArrayList<>();
    frozenProjectiles = new ConcurrentHashMap<>();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.AMETHYST_SHARD)
        .key("challenge_chronos_stasis_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.CLOCK)
            .key("challenge_chronos_stasis_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_chronos_stasis_50", "chronos.stasis-field.casts", 50, 400);
    registerMilestone("challenge_chronos_stasis_500", "chronos.stasis-field.casts", 500, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.f(getRadius(level)) + " " + Localizer.dLocalize("chronos.stasis_field.lore1"));
    v.addLore(C.YELLOW + "+ " + Form.duration(getDurationMillis(level), 1) + " " + Localizer.dLocalize("chronos.stasis_field.lore2"));
    v.addLore(C.RED + "* " + Form.duration(getCooldownMillis(), 1) + " " + Localizer.dLocalize("chronos.stasis_field.lore3"));
    v.addLore(C.GRAY + "* " + Localizer.dLocalize("chronos.stasis_field.lore4"));
    if (getConfig().consumeShard) {
      v.addLore(C.RED + "* " + Localizer.dLocalize("chronos.stasis_field.lore_cost_shard"));
    }
  }

  private double getRadius(int level) {
    return getConfig().baseRadius + ((Math.max(1, level) - 1) * getConfig().radiusPerLevel);
  }

  private long getDurationMillis(int level) {
    return getConfig().baseDurationMillis + ((Math.max(1, level) - 1L) * getConfig().durationPerLevelMillis);
  }

  private long getCooldownMillis() {
    return Math.max(0L, getConfig().cooldownMillis);
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    cooldowns.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    Player p = e.getPlayer();
    if (!p.isSneaking()) {
      return;
    }

    EquipmentSlot hand = e.getHand();
    if (hand == null) {
      return;
    }

    ItemStack held = hand == EquipmentSlot.OFF_HAND
        ? p.getInventory().getItemInOffHand()
        : p.getInventory().getItemInMainHand();
    if (held.getType() != Material.AMETHYST_SHARD) {
      return;
    }

    Adaptation.BlockActionContext context = resolveInteractContext(p, p.getLocation());
    if (context == null) {
      return;
    }

    e.setCancelled(true);
    long now = M.ms();
    long until = cooldowns.getOrDefault(p.getUniqueId(), 0L);
    if (until > now) {
      if (getConfig().playClockSounds) {
        ChronosSoundFX.playClockReject(p);
      }
      return;
    }

    if (getConfig().consumeShard) {
      held.setAmount(held.getAmount() - 1);
    }

    cooldowns.put(p.getUniqueId(), now + getCooldownMillis());
    deployBubble(p, context.level(), now);
  }

  private void deployBubble(Player p, int level, long now) {
    double bubbleRadius = getRadius(level);
    Location center = p.getLocation().clone().add(0, getConfig().centerYOffset, 0);
    bubbles.add(new StasisBubble(p.getUniqueId(), center, bubbleRadius, now + getDurationMillis(level), 0L));

    if (getConfig().playClockSounds) {
      ChronosSoundFX.playTimeBombDetonate(center);
    }

    Location deployCenter = center.clone();
    fx(deployCenter, FxPriority.GAMEPLAY)
        .particle(Particle.FLASH, 1, 0, 0, 0, 0, 0)
        .dustRing(Color.fromRGB(120, 200, 255), bubbleRadius, 24, 1.2F)
        .chord(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6F, 1.5F, Sound.BLOCK_GLASS_PLACE, 0.5F, 0.7F);
    timeline(deployCenter)
        .duration(4)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(bubbleRadius + 16)
        .frame((f, tick, progress) -> f.dome(Particles.END_ROD, bubbleRadius * (0.4D + (0.6D * progress)), 16))
        .start();

    addStat(p, "chronos.stasis-field.casts", 1);
    xp(p, center, getConfig().xpOnCast + (level * getConfig().xpPerLevel));
  }

  @Override
  public void onTick() {
    long now = M.ms();
    cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
    if (bubbles.isEmpty() && frozenProjectiles.isEmpty()) {
      return;
    }

    for (StasisBubble bubble : bubbles) {
      if (bubble.expiresAt() <= now) {
        bubbles.remove(bubble);
        releaseBubble(bubble);
        emitExpiry(bubble);
        continue;
      }

      if (J.isFoliaThreading()) {
        J.runAt(bubble.center(), () -> pulse(bubble, now));
      } else {
        pulse(bubble, now);
      }
    }

    if (bubbles.isEmpty() && !frozenProjectiles.isEmpty()) {
      releaseOrphans();
    }
  }

  private void releaseBubble(StasisBubble bubble) {
    for (Map.Entry<UUID, FrozenProjectileState> entry : frozenProjectiles.entrySet()) {
      if (!entry.getValue().bubbleId().equals(bubble.id())) {
        continue;
      }

      frozenProjectiles.remove(entry.getKey());
      restoreProjectile(entry.getKey(), entry.getValue());
    }
  }

  private void releaseOrphans() {
    for (Map.Entry<UUID, FrozenProjectileState> entry : frozenProjectiles.entrySet()) {
      frozenProjectiles.remove(entry.getKey());
      restoreProjectile(entry.getKey(), entry.getValue());
    }
  }

  private void restoreProjectile(UUID entityId, FrozenProjectileState state) {
    Entity entity = Bukkit.getEntity(entityId);
    if (entity == null || entity.isDead() || !entity.isValid()) {
      return;
    }

    Runnable restore = () -> {
      if (entity.isDead() || !entity.isValid()) {
        return;
      }

      if (getConfig().removeProjectilesOnExpire) {
        entity.remove();
        return;
      }

      entity.setGravity(state.gravity());
      entity.setVelocity(state.velocity());
    };

    if (J.isFoliaThreading()) {
      J.runEntity(entity, restore);
    } else {
      restore.run();
    }
  }

  private void pulse(StasisBubble bubble, long now) {
    World world = bubble.center().getWorld();
    if (world == null) {
      return;
    }

    double radius = bubble.radius();
    double radiusSq = radius * radius;
    Player owner = Bukkit.getPlayer(bubble.owner());
    int sparks = 0;

    for (Entity entity : world.getNearbyEntities(bubble.center(), radius, radius, radius)) {
      if (entity.getLocation().distanceSquared(bubble.center()) > radiusSq) {
        continue;
      }

      if (entity instanceof Projectile projectile) {
        if (!frozenProjectiles.containsKey(projectile.getUniqueId())) {
          frozenProjectiles.put(projectile.getUniqueId(),
              new FrozenProjectileState(bubble.id(), projectile.getVelocity().clone(), projectile.hasGravity()));
          if (owner != null) {
            addStat(owner, "chronos.stasis-field.projectiles-frozen", 1);
          }
          if (sparks < 6) {
            fx(projectile.getLocation(), FxPriority.COMBAT).particle(Particles.END_ROD, 3, 0, 0, 0, 0.1D, 0.0D);
            sparks++;
          }
        }

        projectile.setGravity(false);
        projectile.setVelocity(new Vector());
        continue;
      }

      if (entity instanceof LivingEntity living && !(entity instanceof Player) && !entity.getUniqueId().equals(bubble.owner())) {
        if (isProtectedFriendly(owner, living)) {
          continue;
        }

        living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, getConfig().effectRefreshTicks, getConfig().slownessAmplifier, true, false, false), true);
        if (PotionEffectTypes.JUMP != null) {
          living.addPotionEffect(new PotionEffect(PotionEffectTypes.JUMP, getConfig().effectRefreshTicks, getConfig().jumpLockAmplifier, true, false, false), true);
        }
      }
    }

    if (now >= bubble.nextVisualAt()) {
      spawnOutline(bubble, now);
      bubble.setNextVisualAt(now + Math.max(100L, getConfig().outlineRefreshMillis));
    }
  }

  private void spawnOutline(StasisBubble bubble, long now) {
    double radius = bubble.radius();
    int points = Math.min(12, Math.max(6, getConfig().outlineParticleCount));
    double spin = ((now % 4000L) / 4000.0D) * Math.PI * 2D;
    double golden = Math.PI * (3.0D - Math.sqrt(5.0D));
    FxEmitter shell = fx(bubble.center(), FxPriority.AMBIENT);
    for (int i = 0; i < points; i++) {
      double vertical = points == 1 ? 0D : (((2.0D * i) / (points - 1)) - 1.0D);
      double ringRadius = Math.sqrt(Math.max(0D, 1.0D - (vertical * vertical)));
      double angle = (golden * i) + spin;
      double ox = Math.cos(angle) * ringRadius * radius;
      double oy = vertical * radius;
      double oz = Math.sin(angle) * ringRadius * radius;
      shell.particle(Particles.END_ROD, 1, ox, oy, oz, 0, 0);
    }
    shell.particle(Particle.WAX_ON, 2, 0, 0, 0, radius * 0.35D, 0.0D);
  }

  private void emitExpiry(StasisBubble bubble) {
    Location center = bubble.center();
    double radius = bubble.radius();
    Runnable task = () -> fx(center, FxPriority.TRANSITION)
        .particle(Particle.FLASH, 1, 0, 0, 0, 0, 0)
        .particle(Particle.PORTAL, 20, 0, 0, 0, radius * 0.4D, 0.5D)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.5F, 0.9F);
    if (J.isFoliaThreading()) {
      J.runAt(center, task);
    } else {
      task.run();
    }
  }

  @ConfigDescription("Sneak and right click with an amethyst shard to deploy a stasis bubble that freezes projectiles and locks down mobs inside.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Play Clock Sounds for the Chronos Stasis Field adaptation.", impact = "True enables this behavior and false disables it.")
    boolean playClockSounds = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Consumes the amethyst shard used to deploy a stasis bubble.", impact = "True makes each cast cost one shard; false keeps casts item-free.")
    boolean consumeShard = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base radius of the stasis bubble in blocks.", impact = "Higher values freeze projectiles and slow mobs in a larger area.")
    double baseRadius = 3.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra bubble radius granted per adaptation level.", impact = "Higher values make leveling expand the bubble faster.")
    double radiusPerLevel = 0.75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base bubble lifetime in milliseconds.", impact = "Higher values keep the stasis bubble active longer.")
    long baseDurationMillis = 3000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra bubble lifetime in milliseconds per adaptation level.", impact = "Higher values make leveling extend the bubble duration faster.")
    long durationPerLevelMillis = 750;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown between bubble deployments in milliseconds.", impact = "Higher values force longer waits between casts.")
    long cooldownMillis = 20000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Vertical offset of the bubble center above the caster.", impact = "Higher values raise the bubble center off the ground.")
    double centerYOffset = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Slowness amplifier applied to mobs inside the bubble.", impact = "Higher values slow trapped mobs more severely.")
    int slownessAmplifier = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Jump boost amplifier applied to mobs inside the bubble; negative values prevent jumping.", impact = "Lower negative values suppress jumping harder.")
    int jumpLockAmplifier = -6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of each refreshed potion pulse on trapped mobs.", impact = "Higher values keep effects on mobs longer between pulses.")
    int effectRefreshTicks = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Removes frozen projectiles when the bubble expires instead of restoring their motion.", impact = "True deletes trapped projectiles; false releases them with their original velocity.")
    boolean removeProjectilesOnExpire = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Number of outline particles spawned per visual refresh.", impact = "Higher values make the bubble edge denser at more visual cost.")
    int outlineParticleCount = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between bubble outline particle refreshes.", impact = "Lower values redraw the outline more often at more visual cost.")
    long outlineRefreshMillis = 400;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted when a bubble is deployed.", impact = "Higher values grant more skill XP per cast.")
    double xpOnCast = 22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra XP granted per adaptation level on cast.", impact = "Higher values scale cast XP with level faster.")
    double xpPerLevel = 3;

    public Config() {
      baseCost = 7;
      costFactor = 0.4;
      initialCost = 6;
    }
  }

  private static final class StasisBubble {
    private final UUID id;
    private final UUID owner;
    private final Location center;
    private final double radius;
    private final long expiresAt;
    private long nextVisualAt;

    private StasisBubble(UUID owner, Location center, double radius, long expiresAt, long nextVisualAt) {
      this.id = UUID.randomUUID();
      this.owner = owner;
      this.center = center;
      this.radius = radius;
      this.expiresAt = expiresAt;
      this.nextVisualAt = nextVisualAt;
    }

    private UUID id() {
      return id;
    }

    private UUID owner() {
      return owner;
    }

    private Location center() {
      return center;
    }

    private double radius() {
      return radius;
    }

    private long expiresAt() {
      return expiresAt;
    }

    private long nextVisualAt() {
      return nextVisualAt;
    }

    private void setNextVisualAt(long nextVisualAt) {
      this.nextVisualAt = nextVisualAt;
    }
  }

  private record FrozenProjectileState(UUID bubbleId, Vector velocity,
                                       boolean gravity) {
  }
}
