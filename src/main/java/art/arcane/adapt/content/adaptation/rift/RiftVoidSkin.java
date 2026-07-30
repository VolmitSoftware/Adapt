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

package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RiftMessages;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityRefundReason;
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
import art.arcane.adapt.util.reflect.registries.PotionEffectTypes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class RiftVoidSkin extends SimpleAdaptation<RiftVoidSkin.Config> {
  private static final double HARD_MAX_SEARCH_RADIUS = 16D;
  private static final int SAFE_SAMPLES = 32;

  private final Cooldowns cooldown = cooldowns();
  private final Map<UUID, VoidEscape> pendingEscapes = new ConcurrentHashMap<>();
  private final NamespacedKey pearlReservationKey;
  private final AtomicBoolean acceptingEscapes = new AtomicBoolean(true);

  public RiftVoidSkin() {
    super("rift-void-skin");
    registerConfiguration(Config.class);
    setIcon(Material.ECHO_SHARD);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_PEARL)
        .key("challenge_rift_void_skin_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ECHO_SHARD)
            .key("challenge_rift_void_skin_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_rift_void_skin_50", "rift.void-skin.escapes", 50, 400);
    registerMilestone("challenge_rift_void_skin_500", "rift.void-skin.escapes", 500, 1500);
    pearlReservationKey = new NamespacedKey(Adapt.instance, "rift_void_skin_pearl_reservation");
  }

  @Override
  protected void onRuntimeActivated() {
    acceptingEscapes.set(true);
    for (Player player : Bukkit.getOnlinePlayers()) {
      J.runEntity(player, () -> restoreStrandedReservation(player));
    }
  }

  @Override
  public void unregister() {
    acceptingEscapes.set(false);
    List<VoidEscape> operations = new ArrayList<>(pendingEscapes.values());
    pendingEscapes.clear();
    for (VoidEscape operation : operations) {
      Player player = Bukkit.getPlayer(operation.playerId());
      if (player != null) {
        J.runEntity(player, () -> refundReservation(
            player,
            operation.reservation(),
            AbilityRefundReason.ADAPTATION_DISABLED
        ));
      }
    }
    super.unregister();
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(RiftMessages.VOID_SKIN_LORE1));
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 2);
    v.addLore(C.RED + "* " + AdaptLanguage.text(RiftMessages.VOID_SKIN_LORE3));
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    if (!isLethalDamage(p.getHealth(), e.getFinalDamage())) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0 || !acceptingEscapes.get()) {
      return;
    }

    UUID id = p.getUniqueId();
    if (pendingEscapes.containsKey(id)) {
      e.setCancelled(true);
      return;
    }
    if (!cooldown.isReady(id, getCooldownMillis(level))) {
      return;
    }

    if (!hasPlainPearl(p)) {
      return;
    }

    Location destination = findSafeSpot(p);
    if (destination == null) {
      return;
    }

    Location origin = p.getLocation().clone();
    destination.setYaw(origin.getYaw());
    destination.setPitch(origin.getPitch());
    PearlReservation reservation = reservePlainPearl(p);
    if (reservation == null) {
      return;
    }

    VoidEscape operation = new VoidEscape(id, origin, level, reservation);
    pendingEscapes.put(id, operation);
    CompletableFuture<Boolean> teleport;
    try {
      teleport = p.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
    } catch (RuntimeException exception) {
      pendingEscapes.remove(id, operation);
      refundReservation(p, reservation, AbilityRefundReason.ACTIVATION_FAILED);
      exception.printStackTrace();
      Adapt.error("Void Skin could not start an escape teleport for " + id + ".");
      return;
    }

    if (!cancelLethalDamage(e, teleport != null)) {
      pendingEscapes.remove(id, operation);
      refundReservation(p, reservation, AbilityRefundReason.ACTIVATION_FAILED);
      return;
    }

    teleport.whenComplete((success, failure) -> finishEscape(p, operation, success, failure));
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    UUID id = e.getPlayer().getUniqueId();
    cooldown.clear(id);
    VoidEscape operation = pendingEscapes.remove(id);
    if (operation != null) {
      refundReservation(e.getPlayer(), operation.reservation(), AbilityRefundReason.PLAYER_LEFT);
    }
  }

  @EventHandler
  public void on(PlayerJoinEvent e) {
    restoreStrandedReservation(e.getPlayer());
  }

  private long getCooldownMillis(int level) {
    long value = getConfig().cooldownBaseMillis - ((long) Math.max(0, level - 1) * getConfig().cooldownReductionPerLevelMillis);
    return Math.max(getConfig().minimumCooldownMillis, value);
  }

  private int getResistanceTicks(int level) {
    return Math.max(20, getConfig().resistanceTicksBase + (level * getConfig().resistanceTicksPerLevel));
  }

  private int getResistanceAmplifier() {
    return Math.max(0, Math.min(4, getConfig().resistanceAmplifier));
  }

  private void finishEscape(Player p, VoidEscape operation, Boolean success, Throwable failure) {
    if (failure != null) {
      failure.printStackTrace();
      Adapt.error("Void Skin escape teleport failed for " + operation.playerId() + ".");
    }

    if (!J.runEntity(p, () -> {
      if (!pendingEscapes.remove(operation.playerId(), operation)) {
        return;
      }
      if (failure != null || !Boolean.TRUE.equals(success) || !p.isOnline()) {
        refundReservation(p, operation.reservation(), AbilityRefundReason.ACTIVATION_FAILED);
        return;
      }

      if (!settleReservation(p, operation.reservation())) {
        return;
      }
      cooldown.mark(operation.playerId());
      p.setFallDistance(0f);
      p.addPotionEffect(new PotionEffect(PotionEffectTypes.DAMAGE_RESISTANCE,
          getResistanceTicks(operation.level()), getResistanceAmplifier(), true, false, false));
      Location actualDestination = p.getLocation().clone();
      fx(operation.origin(), FxPriority.TRANSITION)
          .dome(Particles.ENCHANTMENT_TABLE, 1.1, 16)
          .particle(Particle.WITCH, 10, 0, 1.0, 0, 0.4, 0.02)
          .chord(Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 0.8f, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5f, 1.2f);
      fx(actualDestination, FxPriority.TRANSITION)
          .ring(Particles.END_ROD, 0.8, 12, 0.1)
          .particle(Particle.REVERSE_PORTAL, 8, 0, 1.0, 0, 0.3, 0.04)
          .chord(Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.3f, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.5f, 1.1f);
      addStat(p, "rift.void-skin.escapes", 1);
      addStat(p, "rift.teleports", 1);
      xp(p, getConfig().xpOnEscape, "rift:void-skin:escape");
    })) {
      return;
    }
  }

  private Location findSafeSpot(Player p) {
    Location origin = p.getLocation();
    double maxRadius = Math.max(3D, Math.min(HARD_MAX_SEARCH_RADIUS, getConfig().searchRadius));
    double minRadius = Math.min(maxRadius - 1D, Math.max(2D, getConfig().minRadius));
    Location best = null;
    double bestDistance = -1D;
    for (int i = 0; i < SAFE_SAMPLES; i++) {
      double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2D);
      double distance = minRadius + ThreadLocalRandom.current().nextDouble(maxRadius - minRadius);
      double dx = Math.cos(angle) * distance;
      double dz = Math.sin(angle) * distance;
      Location feet = origin.clone().add(dx, 0, dz);
      if (!J.isOwnedByCurrentRegion(feet)) {
        continue;
      }
      Location resolved = resolveStandable(feet.getBlock(), 4);
      if (resolved == null) {
        continue;
      }
      double resolvedDistance = resolved.distanceSquared(origin);
      if (resolvedDistance > bestDistance) {
        bestDistance = resolvedDistance;
        best = resolved;
      }
    }
    return best != null ? best : worldSpawnFallback(p.getWorld());
  }

  private Location resolveStandable(Block base, int vertical) {
    for (int dy = 0; dy <= vertical; dy++) {
      Block up = base.getRelative(0, dy, 0);
      if (isStandable(up)) {
        return up.getLocation().add(0.5, 0, 0.5);
      }
      Block down = base.getRelative(0, -dy, 0);
      if (isStandable(down)) {
        return down.getLocation().add(0.5, 0, 0.5);
      }
    }
    return null;
  }

  private boolean isStandable(Block feet) {
    Block head = feet.getRelative(BlockFace.UP);
    Block ground = feet.getRelative(BlockFace.DOWN);
    return feet.isPassable()
        && head.isPassable()
        && ground.getType().isSolid()
        && !isHazard(ground.getType())
        && !isHazard(feet.getType())
        && !isHazard(head.getType());
  }

  private boolean isHazard(Material material) {
    return switch (material) {
      case LAVA, FIRE, SOUL_FIRE, CAMPFIRE, SOUL_CAMPFIRE, MAGMA_BLOCK, CACTUS, POWDER_SNOW, SWEET_BERRY_BUSH, WITHER_ROSE -> true;
      default -> false;
    };
  }

  private boolean hasPlainPearl(Player p) {
    for (ItemStack stack : p.getInventory().getContents()) {
      if (RiftPearls.isPlainPearl(stack)) {
        return true;
      }
    }
    return false;
  }

  private PearlReservation reservePlainPearl(Player p) {
    if (!hasPlainPearl(p)) {
      return null;
    }

    AtomicBoolean defaultConsumed = new AtomicBoolean();
    AbilityCharge charge = payItemCostDeferred(p, "pearl", new ItemStack(Material.ENDER_PEARL), 1, () -> {
      for (ItemStack stack : p.getInventory().getContents()) {
        if (RiftPearls.isPlainPearl(stack)) {
          stack.setAmount(stack.getAmount() - 1);
          defaultConsumed.set(true);
          p.getPersistentDataContainer().set(pearlReservationKey, PersistentDataType.BYTE, (byte) 1);
          return true;
        }
      }

      return false;
    });
    return charge.allowed()
        ? new PearlReservation(charge, defaultConsumed.get(), new AtomicBoolean())
        : null;
  }

  private void refundReservation(Player p, PearlReservation reservation, AbilityRefundReason reason) {
    if (!reservation.resolved().compareAndSet(false, true)) {
      return;
    }
    refundCost(reservation.charge().activationId(), reason);
    if (!reservation.defaultConsumed()) {
      return;
    }

    try {
      p.getInventory().addItem(new ItemStack(Material.ENDER_PEARL)).values()
          .forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(), item));
      p.getPersistentDataContainer().remove(pearlReservationKey);
    } catch (Throwable error) {
      Adapt.error("Void Skin could not return a reserved ender pearl to " + p.getUniqueId() + ".");
      error.printStackTrace();
    }
  }

  private boolean settleReservation(Player player, PearlReservation reservation) {
    if (!reservation.resolved().compareAndSet(false, true)) {
      return false;
    }
    boolean settled = settleCost(reservation.charge().activationId());
    if (!acceptSettlement(
        reservation.defaultConsumed(),
        reservation.charge().defaultCostSuppressed(),
        settled
    )) {
      return false;
    }
    if (reservation.defaultConsumed()) {
      player.getPersistentDataContainer().remove(pearlReservationKey);
    }
    return true;
  }

  static boolean acceptSettlement(boolean defaultConsumed, boolean defaultCostSuppressed, boolean settled) {
    return defaultConsumed || !defaultCostSuppressed || settled;
  }

  private void restoreStrandedReservation(Player player) {
    if (!player.getPersistentDataContainer().has(pearlReservationKey, PersistentDataType.BYTE)) {
      return;
    }
    try {
      player.getInventory().addItem(new ItemStack(Material.ENDER_PEARL)).values()
          .forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
      player.getPersistentDataContainer().remove(pearlReservationKey);
    } catch (Throwable error) {
      Adapt.error("Void Skin could not recover a reserved ender pearl for " + player.getUniqueId() + ".");
      error.printStackTrace();
    }
  }

  static boolean isLethalDamage(double health, double finalDamage) {
    return Double.isFinite(health)
        && Double.isFinite(finalDamage)
        && health > 0D
        && finalDamage >= health;
  }

  static boolean cancelLethalDamage(EntityDamageEvent event, boolean teleportScheduled) {
    if (!teleportScheduled) {
      return false;
    }
    event.setCancelled(true);
    return true;
  }

  static Location worldSpawnFallback(World currentWorld) {
    if (currentWorld == null) {
      return null;
    }
    Location spawn = currentWorld.getSpawnLocation();
    if (spawn == null || spawn.getWorld() != currentWorld) {
      return null;
    }
    return spawn.clone();
  }

  @ConfigDescription("Any lethal damage blinks you to a nearby safe spot, or the current world's spawn when none exists, with brief resistance for one ender pearl.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base cooldown in milliseconds between escapes.", impact = "Higher values force longer waits between escapes.")
    long cooldownBaseMillis = 120000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown reduction in milliseconds per adaptation level beyond the first.", impact = "Higher values make leveling shorten the cooldown faster.")
    long cooldownReductionPerLevelMillis = 18000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Lowest possible cooldown in milliseconds regardless of level.", impact = "Higher values keep a floor under cooldown reduction.")
    long minimumCooldownMillis = 45000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base resistance duration in ticks applied after an escape.", impact = "Higher values keep the resistance buff up longer.")
    int resistanceTicksBase = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra resistance ticks per adaptation level.", impact = "Higher values extend the resistance buff as you level.")
    int resistanceTicksPerLevel = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Resistance amplifier applied after an escape.", impact = "Higher values reduce incoming damage more during the buff.")
    int resistanceAmplifier = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum horizontal search radius in blocks for a safe blink spot.", impact = "Higher values let the escape reach further, up to a hard 16-block limit.")
    double searchRadius = 9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum horizontal blink distance in blocks.", impact = "Higher values push the escape further from where you were hit.")
    double minRadius = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Skill XP granted when an escape triggers.", impact = "Higher values grant more skill XP per escape.")
    double xpOnEscape = 40;

    public Config() {
      baseCost = 8;
      costFactor = 0.4;
      maxLevel = 4;
      initialCost = 6;
    }
  }

  private record PearlReservation(AbilityCharge charge, boolean defaultConsumed, AtomicBoolean resolved) {
  }

  private record VoidEscape(UUID playerId, Location origin, int level, PearlReservation reservation) {
  }
}
