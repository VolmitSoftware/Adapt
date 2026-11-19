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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.AdaptationTeleportPort;
import art.arcane.adapt.api.adaptation.ItemCooldowns;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PreferenceConfirmation;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.item.BoundEyeOfEnder;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RiftMessages;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;

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
  public static final PlayerPreference<CommonPreferences.Toggle> CONFIRM_BIND = CommonPreferences.toggle("confirm-bind", RiftMessages.RIFTGATE_PREFERENCE_CONFIRM_BIND, CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> CONFIRM_UNBIND = CommonPreferences.toggle("confirm-unbind", RiftMessages.RIFTGATE_PREFERENCE_CONFIRM_UNBIND, CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> COUNTDOWN = CommonPreferences.toggle("countdown", RiftMessages.RIFTGATE_PREFERENCE_COUNTDOWN, CommonPreferences.Toggle.OFF);

  private static final int CHANNEL_TICKS = 85;
  private static final int COOLDOWN_TICKS = 150;
  private static final long COOLDOWN_MILLIS = COOLDOWN_TICKS * 50L;

  /**
   * The gate rides on an ender eye, so its cooldown lives in the bound eye's
   * own group. A gate on cooldown must never gray out or block a plain ender
   * eye being used to locate a stronghold.
   */
  private static final Id ENDER_EYE = Id.minecraft("ender_eye");
  private static final Id BLINDNESS = Id.minecraft("blindness");
  private static final Id LEVITATION = Id.minecraft("levitation");

  /**
   * The gate rides on an ender eye, so its cooldown lives in the bound eye's
   * own group. A gate on cooldown must never gray out or block a plain ender
   * eye being used to locate a stronghold.
   */
  private final ItemCooldowns gateCooldown = ItemCooldowns.forGroup(BoundEyeOfEnder.COOLDOWN_GROUP);
  private final DataKey<byte[]> reservationKey;
  private final Map<UUID, GateChannel> pendingChannels = new ConcurrentHashMap<>();

  public RiftGate() {
    super("rift-gate");
    needs(PlatformNeed.partialFeature(EngineFeature.ITEM_COOLDOWN_GROUPS, "no cooldown sweep on the item"));
    registerConfiguration(Config.class);
    reservationKey = DataKey.adapt("rift_gate_eye_reservation", DataCodecs.BYTE_ARRAY);
    setIcon(Id.minecraft("respawn_anchor"));
    setInterval(1322);
    if (getConfig().requireCraftedEye) {
      registerRecipe(new RecipeSpec.Shapeless(Id.adapt("rift-recall-gate"),
          List.of(Ingredient.of(Id.minecraft("ender_pearl")), Ingredient.of(Id.minecraft("amethyst_shard")),
              Ingredient.of(Id.minecraft("emerald"))),
          BoundEyeOfEnder.io.withData(new BoundEyeOfEnder.Data(null))));
    }
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_pearl"))
        .key("challenge_rift_gate_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("ender_eye"))
            .key("challenge_rift_gate_50k_dist")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_gate_100", "rift.gate.teleports", 100, 400);
    registerMilestone("challenge_rift_gate_50k_dist", "rift.gate.total-distance", 50000, 1500);
  }

  private static ItemOps items() {
    return AdaptCore.platform().host().items();
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    AdaptHud.clearAmbientStatus(player.getPlayer(), getName());
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONFIRM_BIND, CONFIRM_UNBIND, COUNTDOWN);
  }

  @Override
  protected void onRuntimeActivated() {
    for (PlayerRef player : AdaptCore.platform().host().onlinePlayers()) {
      Tasks.runEntity(player, () -> restoreReservedEye(player));
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
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
  @Handle
  public void on(PlayerInteractEvent e) {
    PlayerRef p = e.player();
    ItemRef hand = p.inventory().mainHand();
    ItemRef offHand = p.inventory().offHand();
    Loc location = e.clickedBlock() == null ? p.location() : e.clickedBlock().location();

    // Deny usage if the offhand contains a bindable item
    if (isProtectedEye(offHand) && e.hand() != null && e.hand() == Hand.OFF_HAND) {
      e.setCancelled(true);
      return;
    }

    if (!hand.type().equals(ENDER_EYE) || !isGateEye(hand)) {
      return;
    }

    if (!hasActiveAdaptation(p) || !gateCooldown.isReady(p, COOLDOWN_MILLIS)) {
      if (isProtectedEye(hand)) {
        e.setCancelled(true);
      }
      return;
    }

    if (BoundEyeOfEnder.io.ensureCooldownGroup(hand)) {
      p.inventory().setMainHand(hand);
    }

    if (e.clickedBlock() != null && e.useBlock() == UseResult.DENY) {
      if (isProtectedEye(hand)) {
        e.setCancelled(true);
      }
      return;
    }

    if (e.useItem() == UseResult.DENY) {
      return;
    }

    InteractAction action = e.action();
    if (action == InteractAction.LEFT_CLICK_BLOCK || action == InteractAction.LEFT_CLICK_AIR) {
      if (p.isSneaking()) {
        e.setCancelled(true);
        if (action == InteractAction.LEFT_CLICK_AIR && isBound(hand)) {
          unlinkEye(p);
        } else {
          linkEye(p, location);
        }
      } else if (getConfig().requireCraftedEye) {
        e.setCancelled(true);
      }
    } else if (action == InteractAction.RIGHT_CLICK_AIR || action == InteractAction.RIGHT_CLICK_BLOCK) {
      if (isBound(hand)) {
        e.setCancelled(true);
        openEye(p);
      } else if (getConfig().requireCraftedEye) {
        e.setCancelled(true);
      }
    }
  }

  private boolean isGateEye(ItemRef stack) {
    if (getConfig().requireCraftedEye) {
      return BoundEyeOfEnder.isBindableItem(stack);
    }
    return stack.type().equals(ENDER_EYE);
  }

  private boolean isProtectedEye(ItemRef stack) {
    if (getConfig().requireCraftedEye) {
      return BoundEyeOfEnder.isBindableItem(stack);
    }
    return isBound(stack);
  }


  private boolean isBound(ItemRef stack) {
    return stack.type().equals(ENDER_EYE) && BoundEyeOfEnder.isBound(stack);
  }

  private void unlinkEye(PlayerRef p) {
    if (preferenceEnabled(p, CONFIRM_UNBIND) && !PreferenceConfirmation.confirm(this, p, "unbind", p.inventory().mainHand())) {
      return;
    }
    ItemRef hand = p.inventory().mainHand();
    ItemRef costUnit = hand.copyWithAmount(1);
    AtomicBoolean defaultConsumed = new AtomicBoolean();
    if (!payItemCost(p, "unlink", costUnit, 1, () -> {
      ItemRef current = p.inventory().mainHand();
      if (!isBound(current)) {
        return false;
      }
      decrementItemstack(current, p);
      defaultConsumed.set(true);
      return true;
    })) {
      return;
    }

    ItemRef eye = getConfig().requireCraftedEye
        ? BoundEyeOfEnder.io.withData(new BoundEyeOfEnder.Data(null))
        : items().create(ENDER_EYE, 1);
    deliverTransformedEye(p, eye, defaultConsumed.get());
    fx(p.eyeLocation(), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 4, 0.2)
        .chord(Sounds.ENTITY_ENDER_EYE_DEATH, 0.5f, 1.4f, Sounds.BLOCK_BEACON_DEACTIVATE, 0.4f, 1.2f);
  }

  private void linkEye(PlayerRef p, Loc location) {
    if (preferenceEnabled(p, CONFIRM_BIND) && !PreferenceConfirmation.confirm(this, p, "bind:" + location.world().uid() + ":" + location.blockX() + ":" + location.blockY() + ":" + location.blockZ(), p.inventory().mainHand())) {
      return;
    }
    ItemRef hand = p.inventory().mainHand();
    ItemRef costUnit = hand.copyWithAmount(1);
    AtomicBoolean defaultConsumed = new AtomicBoolean();
    if (!payItemCost(p, "bind", costUnit, 1, () -> {
      ItemRef current = p.inventory().mainHand();
      if (!isGateEye(current)) {
        return false;
      }
      decrementItemstack(current, p);
      defaultConsumed.set(true);
      return true;
    })) {
      return;
    }

    ItemRef eye = costUnit.copy();
    BoundEyeOfEnder.setData(eye, location);
    deliverTransformedEye(p, eye, defaultConsumed.get());

    Loc center = new Loc(location.world(), location.blockX(), location.blockY(), location.blockZ()).add(0.5, 0.5, 0.5);
    timeline(center)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          fx.ring(Particles.REVERSE_PORTAL, 0.7, 10, 0.0);
          fx.particle(Particles.END_ROD, 1, 0, 2.0 - (2.0 * progress), 0, 0.02, 0);
          if (tick == 0) {
            fx.chord(Sounds.ENTITY_ENDER_EYE_DEATH, 0.5f, 0.6f, Sounds.BLOCK_BEACON_POWER_SELECT, 0.4f, 1.2f);
          }
        })
        .start();
  }


  private void openEye(PlayerRef p) {
    Loc destination = BoundEyeOfEnder.getLocation(p.inventory().mainHand());
    if (destination == null) {
      return;
    }

    if (!getConfig().consumeOnUse && !gateCooldown.isReady(p, COOLDOWN_MILLIS)) {
      timeline(p)
          .duration(3)
          .priority(FxPriority.TRANSITION)
          .frame((fx, tick, progress) -> {
            if (tick == 0) {
              fx.burst(Particles.SMOKE, 3, 0.2).sound(Sounds.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.6f, 0.8f);
            }
            if (tick == 2) {
              fx.sound(Sounds.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.5f, 0.5f);
            }
          })
          .start();
      return;
    }
    GateChannel channel = new GateChannel(
        p.uuid(),
        destination,
        new AtomicReference<>()
    );
    if (pendingChannels.putIfAbsent(p.uuid(), channel) != null) {
      return;
    }

    // The channel commits its full cost up front: the eye leaves the inventory and the cooldown
    // burns before any levitation is granted. Stowing or dropping the eye mid-channel used to
    // abort the end-of-channel check for free, turning the channel into an infinite float loop.
    if (getConfig().consumeOnUse) {
      GateReservation reservation = reserveGateEye(p, channel.destination());
      if (reservation == null) {
        pendingChannels.remove(p.uuid(), channel);
        return;
      }
      channel.reservation().set(reservation);
    }
    gateCooldown.mark(p, COOLDOWN_MILLIS);
    showCountdown(p, channel, CHANNEL_TICKS);

    p.addEffect(new EffectInstance(BLINDNESS, 100, 10, true, false, false));
    p.addEffect(new EffectInstance(LEVITATION, 85, 0, true, false, false));
    fx(destination, FxPriority.TRANSITION)
        .chord(Sounds.BLOCK_LODESTONE_PLACE, 1f, 0.8f, Sounds.BLOCK_BELL_RESONATE, 0.7f, 0.9f);

    timeline(p)
        .duration(80)
        .priority(FxPriority.TRANSITION)
        .cullRadius(32)
        .frame((fx, tick, progress) -> {
          fx.dustRing(2.4 - (2.25 * progress), 16, 1.0f);
          fx.helix(Particles.END_ROD, 0.6, 1.6 * progress, 8, -progress * Math.PI * 2.0);
          if (tick % 16 == 0) {
            fx.sound(Sounds.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, (float) (0.6 + (progress * 0.8)));
          }
        })
        .start();

    if (!Tasks.runEntity(p, () -> authorizeAndTeleport(p, channel), CHANNEL_TICKS)) {
      abortGateChannel(p, channel, AbilityRefundReason.ACTIVATION_FAILED, false);
    }
  }

  private void showCountdown(PlayerRef player, GateChannel channel, int ticksLeft) {
    if (ticksLeft <= 0 || pendingChannels.get(player.uuid()) != channel || !player.isOnline()) {
      AdaptHud.clearAmbientStatus(player, getName());
      return;
    }
    if (preferenceEnabled(player, COUNTDOWN)) {
      AdaptHud.ambientStatus(player, getName(), AdaptLanguage.text(RiftMessages.RIFTGATE_PREFERENCE_COUNTDOWN_TEXT, trusted("seconds", String.valueOf((ticksLeft + 19) / 20))));
    }
    Tasks.runEntity(player, () -> showCountdown(player, channel, ticksLeft - 20), 20);
  }

  private void authorizeAndTeleport(PlayerRef p, GateChannel channel) {
    if (!isAuthorizedAfterChannel(p, channel)) {
      abortGateChannel(p, channel, AbilityRefundReason.ACTIVATION_ABORTED, true);
      return;
    }

    Loc origin = p.location();
    Loc destination = AdaptPorts.get(AdaptationTeleportPort.class, AdaptationTeleportPort.NONE)
        .announce(p, this, origin, channel.destination());
    if (destination == null) {
      abortGateChannel(p, channel, AbilityRefundReason.ACTIVATION_ABORTED, false);
      return;
    }

    CompletableFuture<Boolean> teleport;
    try {
      teleport = p.teleport(channel.destination(), TeleportCause.PLUGIN);
    } catch (RuntimeException error) {
      AdaptLog.error(error);
      AdaptLog.error("Rift Gate could not start a teleport for " + channel.playerId() + ".");
      abortGateChannel(p, channel, AbilityRefundReason.ACTIVATION_FAILED, true);
      return;
    }
    if (teleport == null) {
      abortGateChannel(p, channel, AbilityRefundReason.ACTIVATION_FAILED, true);
      return;
    }
    teleport.whenComplete((Boolean success, Throwable failure) -> completeGateTeleport(p, channel, origin, success, failure));
  }

  /**
   * The eye was reserved and the cooldown marked when the channel started, so the end of the
   * channel only re-checks what can genuinely change mid-channel. Deliberately no inventory or
   * cooldown checks: the hand no longer holds the reserved eye, and consulting the cooldown the
   * channel itself just marked would abort every teleport.
   */
  private boolean isAuthorizedAfterChannel(PlayerRef p, GateChannel channel) {
    return pendingChannels.get(channel.playerId()) == channel
        && p.isOnline()
        && getActiveLevel(p) > 0;
  }

  private GateReservation reserveGateEye(PlayerRef p, Loc destination) {
    ItemRef hand = p.inventory().mainHand();
    if (!isBound(hand) || !isSameBlock(BoundEyeOfEnder.getLocation(hand), destination)) {
      return null;
    }
    ItemRef unit = hand.copyWithAmount(1);
    AtomicReference<ItemRef> defaultItem = new AtomicReference<>();
    AbilityCharge charge = payItemCostDeferred(p, "teleport", unit, 1, () -> {
      ItemRef current = p.inventory().mainHand();
      if (!isBound(current) || !isSameBlock(BoundEyeOfEnder.getLocation(current), destination)) {
        return false;
      }
      ItemRef reserved = current.copyWithAmount(1);
      try {
        decrementItemstack(current, p);
        p.data().set(reservationKey, items().serialize(reserved));
        defaultItem.set(reserved);
        return true;
      } catch (RuntimeException error) {
        deliverExactItem(p, reserved);
        p.data().remove(reservationKey);
        AdaptLog.error(error);
        return false;
      }
    });
    if (!charge.allowed()) {
      ItemRef consumed = defaultItem.get();
      if (consumed != null) {
        deliverExactItem(p, consumed);
        p.data().remove(reservationKey);
      }
      return null;
    }
    return new GateReservation(charge, defaultItem.get(), new AtomicBoolean());
  }

  private void completeGateTeleport(PlayerRef p, GateChannel channel, Loc origin,
                                    Boolean success, Throwable failure) {
    if (failure != null) {
      AdaptLog.error(failure);
      AdaptLog.error("Rift Gate teleport failed for " + channel.playerId() + ".");
    }
    if (!Tasks.runEntity(p, () -> finishGateTeleportOwned(p, channel, origin, success, failure))) {
      if (pendingChannels.remove(channel.playerId(), channel)) {
        releaseProviderReservation(channel.reservation().get(), AbilityRefundReason.ACTIVATION_FAILED);
      }
    }
  }

  private void finishGateTeleportOwned(PlayerRef p, GateChannel channel, Loc origin,
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
    Loc actualDestination = p.location();
    addStat(p, "rift.teleports", 1);
    addStat(p, "rift.gate.teleports", 1);
    if (origin.sameWorld(actualDestination)) {
      addStat(p, "rift.gate.total-distance", (int) origin.distance(actualDestination));
    }
    fx(origin, FxPriority.TRANSITION)
        .particle(Particles.REVERSE_PORTAL, 16, 0, 1.0, 0, 0.25, 0.05)
        .sound(Sounds.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.8f);
    timeline(actualDestination)
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .frame((fx, tick, progress) -> {
          fx.ring(Particles.END_ROD, 0.3 + (1.3 * progress), 16, 0.1);
          if (tick == 0) {
            fx.sound(Sounds.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.3f);
          }
        })
        .start();
  }

  private boolean settleGateReservation(PlayerRef p, GateReservation reservation) {
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
      AdaptLog.warn("Rift Gate could not settle the deferred provider cost for " + p.uuid() + ".");
      return false;
    }
    if (reservation.defaultItem() != null) {
      p.data().remove(reservationKey);
    }
    return true;
  }

  private void abortGateChannel(PlayerRef p, GateChannel channel, AbilityRefundReason reason, boolean feedback) {
    if (!pendingChannels.remove(channel.playerId(), channel)) {
      return;
    }
    refundGateReservation(p, channel.reservation().get(), reason);
    clearChannelEffects(p);
    if (feedback) {
      showGateFailure(p);
    }
  }

  private void refundGateReservation(PlayerRef p, GateReservation reservation, AbilityRefundReason reason) {
    ItemRef item = releaseProviderReservation(reservation, reason);
    if (item == null) {
      return;
    }
    try {
      deliverExactItem(p, item);
      p.data().remove(reservationKey);
    } catch (RuntimeException error) {
      AdaptLog.error("Rift Gate could not return a reserved gate eye to " + p.uuid() + ".");
      AdaptLog.error(error);
    }
  }

  private ItemRef releaseProviderReservation(GateReservation reservation, AbilityRefundReason reason) {
    if (reservation == null || !reservation.resolved().compareAndSet(false, true)) {
      return null;
    }
    boolean refunded = refundCost(reservation.charge().activationId(), reason);
    if (reservation.charge().defaultCostSuppressed() && !refunded) {
      AdaptLog.warn("Rift Gate could not resolve the deferred provider cost for "
          + reservation.charge().activationId() + ".");
    }
    return reservation.defaultItem();
  }

  private void clearChannelEffects(PlayerRef p) {
    EffectInstance blindness = p.effect(BLINDNESS);
    if (isOwnedChannelEffect(blindness, 10, 100)) {
      p.removeEffect(BLINDNESS);
    }
    EffectInstance levitation = p.effect(LEVITATION);
    if (isOwnedChannelEffect(levitation, 0, 85)) {
      p.removeEffect(LEVITATION);
    }
  }

  private void showGateFailure(PlayerRef p) {
    if (!p.isOnline()) {
      return;
    }
    fx(p.eyeLocation(), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 3, 0.2)
        .sound(Sounds.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.6f, 0.8f);
  }

  @Handle
  public void on(PlayerQuitEvent event) {
    GateChannel channel = pendingChannels.remove(event.player().uuid());
    if (channel != null) {
      refundGateReservation(event.player(), channel.reservation().get(), AbilityRefundReason.PLAYER_LEFT);
      clearChannelEffects(event.player());
    }
  }

  @Handle
  public void on(PlayerJoinEvent event) {
    restoreReservedEye(event.player());
  }

  private void restoreReservedEye(PlayerRef p) {
    byte[] serialized = p.data().get(reservationKey);
    if (serialized == null || serialized.length == 0) {
      return;
    }
    try {
      deliverExactItem(p, items().deserialize(serialized));
      p.data().remove(reservationKey);
    } catch (RuntimeException error) {
      AdaptLog.error("Rift Gate could not recover a reserved gate eye for " + p.uuid() + ".");
      AdaptLog.error(error);
    }
  }

  @Override
  public void unregister() {
    List<GateChannel> channels = new ArrayList<>(pendingChannels.values());
    pendingChannels.clear();
    for (GateChannel channel : channels) {
      PlayerRef player = AdaptCore.platform().host().player(channel.playerId());
      if (player != null && player.isOnline()) {
        if (!Tasks.runEntity(player, () -> {
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

  static boolean isOwnedChannelEffect(EffectInstance effect, int amplifier, int maximumDuration) {
    return effect != null
        && effect.amplifier() == amplifier
        && effect.durationTicks() <= maximumDuration
        && effect.ambient()
        && !effect.particles()
        && !effect.icon();
  }

  private void deliverTransformedEye(PlayerRef p, ItemRef eye, boolean defaultConsumed) {
    ItemRef hand = p.inventory().mainHand();
    if (shouldReplaceTransformedEye(defaultConsumed, hand == null || hand.type().isAir())) {
      p.inventory().setMainHand(eye);
      return;
    }
    deliverExactItem(p, eye);
  }

  static boolean shouldReplaceTransformedEye(boolean defaultConsumed, boolean handEmpty) {
    return defaultConsumed && handEmpty;
  }

  static void deliverExactItem(PlayerRef p, ItemRef item) {
    for (ItemRef leftover : p.inventory().addItem(item)) {
      p.world().dropItemNaturally(p.location().position(), leftover);
    }
  }

  private boolean isSameBlock(Loc a, Loc b) {
    return a != null
        && b != null
        && a.sameWorld(b)
        && a.blockX() == b.blockX()
        && a.blockY() == b.blockY()
        && a.blockZ() == b.blockZ();
  }

  private record GateChannel(
      UUID playerId,
      Loc destination,
      AtomicReference<GateReservation> reservation
  ) {
  }

  private record GateReservation(
      AbilityCharge charge,
      ItemRef defaultItem,
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
