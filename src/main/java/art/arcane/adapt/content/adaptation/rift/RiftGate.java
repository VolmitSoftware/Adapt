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
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ItemCooldowns;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.content.event.AdaptAdaptationTeleportEvent;
import art.arcane.adapt.content.item.BoundEyeOfEnder;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class RiftGate extends SimpleAdaptation<RiftGate.Config> {
  private static final int CHANNEL_TICKS = 85;
  private static final int COOLDOWN_TICKS = 150;
  private static final long COOLDOWN_MILLIS = COOLDOWN_TICKS * 50L;

  /**
   * The gate rides on an ender eye, so its cooldown lives in the bound eye's
   * own group. A gate on cooldown must never gray out or block a plain ender
   * eye being used to locate a stronghold.
   */
  private final ItemCooldowns gateCooldown = ItemCooldowns.forGroup(BoundEyeOfEnder.COOLDOWN_GROUP);
  private final NamespacedKey reservationKey;
  private final Map<UUID, GateChannel> pendingChannels = new ConcurrentHashMap<>();

  public RiftGate() {
    super("rift-gate");
    registerConfiguration(Config.class);
    reservationKey = new NamespacedKey(Adapt.instance, "rift_gate_eye_reservation");
    setIcon(Material.RESPAWN_ANCHOR);
    setInterval(1322);
    if (getConfig().requireCraftedEye) {
      registerRecipe(AdaptRecipe.shapeless()
          .key("rift-recall-gate")
          .ingredient(Material.ENDER_PEARL)
          .ingredient(Material.AMETHYST_SHARD)
          .ingredient(Material.EMERALD)
          .result(BoundEyeOfEnder.io.withData(new BoundEyeOfEnder.Data(null)))
          .build());
    }
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_PEARL)
        .key("challenge_rift_gate_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENDER_EYE)
            .key("challenge_rift_gate_50k_dist")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_gate_100", "rift.gate.teleports", 100, 400);
    registerMilestone("challenge_rift_gate_50k_dist", "rift.gate.total-distance", 50000, 1500);
  }

  @Override
  protected void onRuntimeActivated() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      J.runEntity(player, () -> restoreReservedEye(player));
    }
  }

  @Override
  public void addStats(int level, Element v) {
    if (getConfig().requireCraftedEye) {
      v.addLore(C.YELLOW + AdaptLanguage.text(RiftMessages.GATE_LORE1));
    }
    v.addLore(C.RED + AdaptLanguage.text(RiftMessages.GATE_LORE2));
    v.addLore(C.ITALIC + AdaptLanguage.text(
        RiftMessages.GATE_WARNING,
        trusted("danger", C.UNDERLINE + "" + C.RED + AdaptLanguage.text(RiftMessages.GATE_LORE4))
    ));
  }

  @Override
  public String getDescription() {
    return AdaptLanguage.text(
        getConfig().requireCraftedEye ? RiftMessages.GATE_DESCRIPTION : RiftMessages.GATE_DESCRIPTION_FREEHAND
    );
  }


  @ReceiveCancelledEvents
  @EventHandler
  public void on(PlayerInteractEvent e) {
    Player p = e.getPlayer();
    ItemStack hand = p.getInventory().getItemInMainHand();
    ItemStack offHand = p.getInventory().getItemInOffHand();
    Location location = e.getClickedBlock() == null ? p.getLocation() : e.getClickedBlock().getLocation();

    // Deny usage if the offhand contains a bindable item
    if (isProtectedEye(offHand) && e.getHand() != null && e.getHand().equals(EquipmentSlot.OFF_HAND)) {
      e.setCancelled(true);
      return;
    }

    if (!hand.getType().equals(Material.ENDER_EYE) || !isGateEye(hand)) {
      return;
    }

    if (!hasActiveAdaptation(p) || !gateCooldown.isReady(p, COOLDOWN_MILLIS)) {
      if (isProtectedEye(hand)) {
        e.setCancelled(true);
      }
      return;
    }

    if (BoundEyeOfEnder.io.ensureCooldownGroup(hand)) {
      p.getInventory().setItemInMainHand(hand);
    }

    if (e.getClickedBlock() != null && e.useInteractedBlock() == Event.Result.DENY) {
      if (isProtectedEye(hand)) {
        e.setCancelled(true);
      }
      return;
    }

    if (e.useItemInHand() == Event.Result.DENY) {
      return;
    }

    Action action = e.getAction();
    if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
      if (p.isSneaking()) {
        e.setCancelled(true);
        if (action == Action.LEFT_CLICK_AIR && isBound(hand)) {
          unlinkEye(p);
        } else {
          linkEye(p, location);
        }
      } else if (getConfig().requireCraftedEye) {
        e.setCancelled(true);
      }
    } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
      if (isBound(hand)) {
        e.setCancelled(true);
        openEye(p);
      } else if (getConfig().requireCraftedEye) {
        e.setCancelled(true);
      }
    }
  }

  private boolean isGateEye(ItemStack stack) {
    if (getConfig().requireCraftedEye) {
      return BoundEyeOfEnder.isBindableItem(stack);
    }
    return stack.getType().equals(Material.ENDER_EYE);
  }

  private boolean isProtectedEye(ItemStack stack) {
    if (getConfig().requireCraftedEye) {
      return BoundEyeOfEnder.isBindableItem(stack);
    }
    return isBound(stack);
  }


  private boolean isBound(ItemStack stack) {
    return stack.getType().equals(Material.ENDER_EYE) && BoundEyeOfEnder.getLocation(stack) != null;
  }

  private void unlinkEye(Player p) {
    ItemStack hand = p.getInventory().getItemInMainHand();
    ItemStack costUnit = hand.clone();
    costUnit.setAmount(1);
    AtomicBoolean defaultConsumed = new AtomicBoolean();
    if (!payItemCost(p, "unlink", costUnit, 1, () -> {
      ItemStack current = p.getInventory().getItemInMainHand();
      if (!isBound(current)) {
        return false;
      }
      decrementItemstack(current, p);
      defaultConsumed.set(true);
      return true;
    })) {
      return;
    }

    ItemStack eye = getConfig().requireCraftedEye
        ? BoundEyeOfEnder.io.withData(new BoundEyeOfEnder.Data(null))
        : new ItemStack(Material.ENDER_EYE);
    deliverTransformedEye(p, eye, defaultConsumed.get());
    fx(p.getEyeLocation(), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 4, 0.2)
        .chord(Sound.ENTITY_ENDER_EYE_DEATH, 0.5f, 1.4f, Sound.BLOCK_BEACON_DEACTIVATE, 0.4f, 1.2f);
  }

  private void linkEye(Player p, Location location) {
    ItemStack hand = p.getInventory().getItemInMainHand();
    ItemStack costUnit = hand.clone();
    costUnit.setAmount(1);
    AtomicBoolean defaultConsumed = new AtomicBoolean();
    if (!payItemCost(p, "bind", costUnit, 1, () -> {
      ItemStack current = p.getInventory().getItemInMainHand();
      if (!isGateEye(current)) {
        return false;
      }
      decrementItemstack(current, p);
      defaultConsumed.set(true);
      return true;
    })) {
      return;
    }

    ItemStack eye = costUnit.clone();
    BoundEyeOfEnder.setData(eye, location);
    deliverTransformedEye(p, eye, defaultConsumed.get());

    Location center = location.getBlock().getLocation().add(0.5, 0.5, 0.5);
    timeline(center)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          fx.ring(Particle.REVERSE_PORTAL, 0.7, 10, 0.0);
          fx.particle(Particles.END_ROD, 1, 0, 2.0 - (2.0 * progress), 0, 0.02, 0);
          if (tick == 0) {
            fx.chord(Sound.ENTITY_ENDER_EYE_DEATH, 0.5f, 0.6f, Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 1.2f);
          }
        })
        .start();
  }


  private void openEye(Player p) {
    Location destination = BoundEyeOfEnder.getLocation(p.getInventory().getItemInMainHand());
    if (destination == null || destination.getWorld() == null) {
      return;
    }

    if (!getConfig().consumeOnUse && !gateCooldown.isReady(p, COOLDOWN_MILLIS)) {
      timeline(p)
          .duration(3)
          .priority(FxPriority.TRANSITION)
          .frame((fx, tick, progress) -> {
            if (tick == 0) {
              fx.burst(Particles.SMOKE, 3, 0.2).sound(Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.6f, 0.8f);
            }
            if (tick == 2) {
              fx.sound(Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.5f, 0.5f);
            }
          })
          .start();
      return;
    }
    GateChannel channel = new GateChannel(
        p.getUniqueId(),
        destination.clone(),
        new AtomicReference<>()
    );
    if (pendingChannels.putIfAbsent(p.getUniqueId(), channel) != null) {
      return;
    }

    // The channel commits its full cost up front: the eye leaves the inventory and the cooldown
    // burns before any levitation is granted. Stowing or dropping the eye mid-channel used to
    // abort the end-of-channel check for free, turning the channel into an infinite float loop.
    if (getConfig().consumeOnUse) {
      GateReservation reservation = reserveGateEye(p, channel.destination());
      if (reservation == null) {
        pendingChannels.remove(p.getUniqueId(), channel);
        return;
      }
      channel.reservation().set(reservation);
    }
    gateCooldown.mark(p, COOLDOWN_MILLIS);

    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 10, true, false, false));
    p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 85, 0, true, false, false));
    fx(destination, FxPriority.TRANSITION)
        .chord(Sound.BLOCK_LODESTONE_PLACE, 1f, 0.8f, Sound.BLOCK_BELL_RESONATE, 0.7f, 0.9f);

    timeline(p)
        .duration(80)
        .priority(FxPriority.TRANSITION)
        .cullRadius(32)
        .frame((fx, tick, progress) -> {
          fx.dustRing(2.4 - (2.25 * progress), 16, 1.0f);
          fx.helix(Particles.END_ROD, 0.6, 1.6 * progress, 8, -progress * Math.PI * 2.0);
          if (tick % 16 == 0) {
            fx.sound(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, (float) (0.6 + (progress * 0.8)));
          }
        })
        .start();

    if (!J.runEntity(p, () -> authorizeAndTeleport(p, channel), CHANNEL_TICKS)) {
      abortGateChannel(p, channel, AbilityRefundReason.ACTIVATION_FAILED, false);
    }
  }

  private void authorizeAndTeleport(Player p, GateChannel channel) {
    if (!isAuthorizedAfterChannel(p, channel)) {
      abortGateChannel(p, channel, AbilityRefundReason.ACTIVATION_ABORTED, true);
      return;
    }

    Location origin = p.getLocation().clone();
    AdaptAdaptationTeleportEvent event = new AdaptAdaptationTeleportEvent(
        !Bukkit.isPrimaryThread(),
        getPlayer(p),
        this,
        origin,
        channel.destination().clone()
    );
    Bukkit.getPluginManager().callEvent(event);
    if (event.isCancelled()) {
      abortGateChannel(p, channel, AbilityRefundReason.ACTIVATION_ABORTED, false);
      return;
    }

    CompletableFuture<Boolean> teleport;
    try {
      teleport = PaperCompat.teleportAsync(p, channel.destination().clone(), PlayerTeleportEvent.TeleportCause.PLUGIN);
    } catch (RuntimeException error) {
      Adapt.error(error);
      Adapt.error("Rift Gate could not start a teleport for " + channel.playerId() + ".");
      abortGateChannel(p, channel, AbilityRefundReason.ACTIVATION_FAILED, true);
      return;
    }
    if (teleport == null) {
      abortGateChannel(p, channel, AbilityRefundReason.ACTIVATION_FAILED, true);
      return;
    }
    teleport.whenComplete((success, failure) -> completeGateTeleport(p, channel, origin, success, failure));
  }

  /**
   * The eye was reserved and the cooldown marked when the channel started, so the end of the
   * channel only re-checks what can genuinely change mid-channel. Deliberately no inventory or
   * cooldown checks: the hand no longer holds the reserved eye, and consulting the cooldown the
   * channel itself just marked would abort every teleport.
   */
  private boolean isAuthorizedAfterChannel(Player p, GateChannel channel) {
    return pendingChannels.get(channel.playerId()) == channel
        && p.isOnline()
        && getActiveLevel(p) > 0;
  }

  private GateReservation reserveGateEye(Player p, Location destination) {
    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!isBound(hand) || !isSameBlock(BoundEyeOfEnder.getLocation(hand), destination)) {
      return null;
    }
    ItemStack unit = hand.clone();
    unit.setAmount(1);
    AtomicReference<ItemStack> defaultItem = new AtomicReference<>();
    AbilityCharge charge = payItemCostDeferred(p, "teleport", unit, 1, () -> {
      ItemStack current = p.getInventory().getItemInMainHand();
      if (!isBound(current) || !isSameBlock(BoundEyeOfEnder.getLocation(current), destination)) {
        return false;
      }
      ItemStack reserved = current.clone();
      reserved.setAmount(1);
      try {
        decrementItemstack(current, p);
        p.getPersistentDataContainer().set(
            reservationKey,
            PersistentDataType.BYTE_ARRAY,
            reserved.serializeAsBytes()
        );
        defaultItem.set(reserved);
        return true;
      } catch (RuntimeException error) {
        deliverExactItem(p, reserved);
        p.getPersistentDataContainer().remove(reservationKey);
        Adapt.error(error);
        return false;
      }
    });
    if (!charge.allowed()) {
      ItemStack consumed = defaultItem.get();
      if (consumed != null) {
        deliverExactItem(p, consumed);
        p.getPersistentDataContainer().remove(reservationKey);
      }
      return null;
    }
    return new GateReservation(charge, defaultItem.get(), new AtomicBoolean());
  }

  private void completeGateTeleport(Player p, GateChannel channel, Location origin,
                                    Boolean success, Throwable failure) {
    if (failure != null) {
      Adapt.error(failure);
      Adapt.error("Rift Gate teleport failed for " + channel.playerId() + ".");
    }
    if (!J.runEntity(p, () -> finishGateTeleportOwned(p, channel, origin, success, failure))) {
      if (pendingChannels.remove(channel.playerId(), channel)) {
        releaseProviderReservation(channel.reservation().get(), AbilityRefundReason.ACTIVATION_FAILED);
      }
    }
  }

  private void finishGateTeleportOwned(Player p, GateChannel channel, Location origin,
                                       Boolean success, Throwable failure) {
    if (!pendingChannels.remove(channel.playerId(), channel)) {
      return;
    }
    GateReservation reservation = channel.reservation().get();
    if (!successfulTeleport(success, failure, p.isOnline())) {
      refundGateReservation(p, reservation, AbilityRefundReason.ACTIVATION_FAILED);
      clearChannelEffects(p);
      showGateFailure(p);
      return;
    }
    if (!settleGateReservation(p, reservation)) {
      clearChannelEffects(p);
      showGateFailure(p);
      return;
    }

    if (getActiveSiblingLevel(p, "rift-resist") > 0) {
      RiftResist.riftResistStackAdd(this, p, COOLDOWN_TICKS, 3);
    }
    if (getConfig().consumeOnUse) {
      xp(p, 75);
    }
    Location actualDestination = p.getLocation().clone();
    addStat(p, "rift.teleports", 1);
    addStat(p, "rift.gate.teleports", 1);
    if (origin.getWorld() != null && origin.getWorld().equals(actualDestination.getWorld())) {
      addStat(p, "rift.gate.total-distance", (int) origin.distance(actualDestination));
    }
    fx(origin, FxPriority.TRANSITION)
        .particle(Particle.REVERSE_PORTAL, 16, 0, 1.0, 0, 0.25, 0.05)
        .sound(Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.8f);
    timeline(actualDestination)
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .frame((fx, tick, progress) -> {
          fx.ring(Particles.END_ROD, 0.3 + (1.3 * progress), 16, 0.1);
          if (tick == 0) {
            fx.sound(Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.3f);
          }
        })
        .start();
  }

  private boolean settleGateReservation(Player p, GateReservation reservation) {
    if (reservation == null) {
      return true;
    }
    if (!reservation.resolved().compareAndSet(false, true)) {
      return false;
    }
    boolean settled = settleCost(reservation.charge().activationId());
    if (!acceptSettlement(
        reservation.defaultItem() != null,
        reservation.charge().defaultCostSuppressed(),
        settled
    )) {
      Adapt.warn("Rift Gate could not settle the deferred provider cost for " + p.getUniqueId() + ".");
      return false;
    }
    if (reservation.defaultItem() != null) {
      p.getPersistentDataContainer().remove(reservationKey);
    }
    return true;
  }

  private void abortGateChannel(Player p, GateChannel channel, AbilityRefundReason reason, boolean feedback) {
    if (!pendingChannels.remove(channel.playerId(), channel)) {
      return;
    }
    refundGateReservation(p, channel.reservation().get(), reason);
    clearChannelEffects(p);
    if (feedback) {
      showGateFailure(p);
    }
  }

  private void refundGateReservation(Player p, GateReservation reservation, AbilityRefundReason reason) {
    ItemStack item = releaseProviderReservation(reservation, reason);
    if (item == null) {
      return;
    }
    try {
      deliverExactItem(p, item);
      p.getPersistentDataContainer().remove(reservationKey);
    } catch (RuntimeException error) {
      Adapt.error("Rift Gate could not return a reserved gate eye to " + p.getUniqueId() + ".");
      Adapt.error(error);
    }
  }

  private ItemStack releaseProviderReservation(GateReservation reservation, AbilityRefundReason reason) {
    if (reservation == null || !reservation.resolved().compareAndSet(false, true)) {
      return null;
    }
    boolean refunded = refundCost(reservation.charge().activationId(), reason);
    if (reservation.charge().defaultCostSuppressed() && !refunded) {
      Adapt.warn("Rift Gate could not resolve the deferred provider cost for "
          + reservation.charge().activationId() + ".");
    }
    return reservation.defaultItem();
  }

  private void clearChannelEffects(Player p) {
    PotionEffect blindness = p.getPotionEffect(PotionEffectType.BLINDNESS);
    if (isOwnedChannelEffect(blindness, 10, 100)) {
      p.removePotionEffect(PotionEffectType.BLINDNESS);
    }
    PotionEffect levitation = p.getPotionEffect(PotionEffectType.LEVITATION);
    if (isOwnedChannelEffect(levitation, 0, 85)) {
      p.removePotionEffect(PotionEffectType.LEVITATION);
    }
  }

  private void showGateFailure(Player p) {
    if (!p.isOnline()) {
      return;
    }
    fx(p.getEyeLocation(), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 3, 0.2)
        .sound(Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.6f, 0.8f);
  }

  @EventHandler
  public void on(PlayerQuitEvent event) {
    GateChannel channel = pendingChannels.remove(event.getPlayer().getUniqueId());
    if (channel != null) {
      refundGateReservation(event.getPlayer(), channel.reservation().get(), AbilityRefundReason.PLAYER_LEFT);
      clearChannelEffects(event.getPlayer());
    }
  }

  @EventHandler
  public void on(PlayerJoinEvent event) {
    restoreReservedEye(event.getPlayer());
  }

  private void restoreReservedEye(Player p) {
    byte[] serialized = p.getPersistentDataContainer().get(reservationKey, PersistentDataType.BYTE_ARRAY);
    if (serialized == null || serialized.length == 0) {
      return;
    }
    try {
      deliverExactItem(p, ItemStack.deserializeBytes(serialized));
      p.getPersistentDataContainer().remove(reservationKey);
    } catch (RuntimeException error) {
      Adapt.error("Rift Gate could not recover a reserved gate eye for " + p.getUniqueId() + ".");
      Adapt.error(error);
    }
  }

  @Override
  public void unregister() {
    List<GateChannel> channels = new ArrayList<>(pendingChannels.values());
    pendingChannels.clear();
    for (GateChannel channel : channels) {
      Player player = Bukkit.getPlayer(channel.playerId());
      if (player != null && player.isOnline()) {
        if (!J.runEntity(player, () -> {
          refundGateReservation(
              player,
              channel.reservation().get(),
              AbilityRefundReason.ADAPTATION_DISABLED
          );
          clearChannelEffects(player);
        })) {
          releaseProviderReservation(
              channel.reservation().get(),
              AbilityRefundReason.ADAPTATION_DISABLED
          );
        }
      } else {
        releaseProviderReservation(
            channel.reservation().get(),
            AbilityRefundReason.ADAPTATION_DISABLED
        );
      }
    }
    super.unregister();
  }

  static boolean successfulTeleport(Boolean success, Throwable failure, boolean online) {
    return failure == null && Boolean.TRUE.equals(success) && online;
  }

  static boolean acceptSettlement(boolean defaultConsumed, boolean defaultCostSuppressed, boolean settled) {
    return defaultConsumed || !defaultCostSuppressed || settled;
  }

  static boolean isOwnedChannelEffect(PotionEffect effect, int amplifier, int maximumDuration) {
    return effect != null
        && effect.getAmplifier() == amplifier
        && effect.getDuration() <= maximumDuration
        && effect.isAmbient()
        && !effect.hasParticles()
        && !effect.hasIcon();
  }

  private void deliverTransformedEye(Player p, ItemStack eye, boolean defaultConsumed) {
    ItemStack hand = p.getInventory().getItemInMainHand();
    if (shouldReplaceTransformedEye(defaultConsumed, hand == null || hand.getType().isAir())) {
      p.getInventory().setItemInMainHand(eye);
      return;
    }
    deliverExactItem(p, eye);
  }

  static boolean shouldReplaceTransformedEye(boolean defaultConsumed, boolean handEmpty) {
    return defaultConsumed && handEmpty;
  }

  static void deliverExactItem(Player p, ItemStack item) {
    p.getInventory().addItem(item).values()
        .forEach(leftover -> p.getWorld().dropItemNaturally(p.getLocation(), leftover));
  }

  private boolean isSameBlock(Location a, Location b) {
    return a != null
        && b != null
        && a.getWorld() != null
        && a.getWorld().equals(b.getWorld())
        && a.getBlockX() == b.getBlockX()
        && a.getBlockY() == b.getBlockY()
        && a.getBlockZ() == b.getBlockZ();
  }

  private record GateChannel(
      UUID playerId,
      Location destination,
      AtomicReference<GateReservation> reservation
  ) {
  }

  private record GateReservation(
      AbilityCharge charge,
      ItemStack defaultItem,
      AtomicBoolean resolved
  ) {
  }



  @ConfigDescription("Craft a gate item to teleport to a marked location.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Consume On Use for the Rift Gate adaptation.", impact = "True enables this behavior and false disables it.")
    boolean consumeOnUse = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Requires crafting the recall gate eye before it can be bound.", impact = "False lets any plain Eye of Ender bind with sneak-left-click and removes the crafting recipe; recipe availability changes apply after a restart.")
    boolean requireCraftedEye = true;

    public Config() {
      baseCost = 0;
      costFactor = 0.0;
      maxLevel = 1;
      initialCost = 30;
    }
  }
}
