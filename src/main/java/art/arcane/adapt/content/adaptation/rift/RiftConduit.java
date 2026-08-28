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
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static art.arcane.adapt.api.adaptation.chunk.ChunkLoading.loadChunkAsync;

public class RiftConduit extends SimpleAdaptation<RiftConduit.Config> {
  static final int HARD_MAX_FLOW_ITEMS = 1152;
  static final double HARD_MAX_RANGE = 512D;
  static final int FLOW_DELIVERY_TIMEOUT_TICKS = 100;
  static final int BIND_TIMEOUT_TICKS = 400;
  static final long CAPTURE_FOLLOWUP_WINDOW_MILLIS = 150L;
  static final String TAGLOCK_LOC_KEY_NAME = "rift_conduit_taglock_loc";
  static final String TAGLOCK_ID_KEY_NAME = "rift_conduit_taglock_id";

  private final NamespacedKey partnerKey;
  private final NamespacedKey linkKey;
  private final NamespacedKey taglockLocKey;
  private final NamespacedKey taglockIdKey;
  private final NamespacedKey reservationKey;
  private final Map<UUID, Long> captureFollowUpUntil = playerState();
  private final Map<UUID, BindOperation> pendingBinds = new ConcurrentHashMap<>();
  private final Set<EndpointKey> bindingEndpoints = ConcurrentHashMap.newKeySet();

  public RiftConduit() {
    super("rift-conduit");
    registerConfiguration(Config.class);
    setIcon(Material.CONDUIT);
    setInterval(1000);
    partnerKey = new NamespacedKey(Adapt.instance, "rift_conduit_partner");
    linkKey = new NamespacedKey(Adapt.instance, "rift_conduit_link");
    taglockLocKey = new NamespacedKey(Adapt.instance, TAGLOCK_LOC_KEY_NAME);
    taglockIdKey = new NamespacedKey(Adapt.instance, TAGLOCK_ID_KEY_NAME);
    reservationKey = new NamespacedKey(Adapt.instance, "rift_conduit_taglock_reservation");
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_PEARL)
        .key("challenge_rift_conduit_10")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.CONDUIT)
            .key("challenge_rift_conduit_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_conduit_10", "rift.conduit.links-formed", 10, 500);
    registerMilestone("challenge_rift_conduit_10k", "rift.conduit.items-flowed", 10000, 1500);
  }

  @Override
  protected void onRuntimeActivated() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      J.runEntity(player, () -> restoreReservedTaglock(player));
    }
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getThroughput(level), 1);
    statLore(v, Form.f(getRange(level), 0), 2);
    if (isCrossDimension(level)) {
      v.addLore(C.LIGHT_PURPLE + "+ " + AdaptLanguage.text(RiftMessages.CONDUIT_LORE3));
    }
  }

  @ReceiveCancelledEvents
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
      return;
    }

    Player p = e.getPlayer();
    if (e.getHand() != EquipmentSlot.HAND) {
      if (e.getHand() == EquipmentSlot.OFF_HAND && isConduitTaglock(p.getInventory().getItemInOffHand())) {
        e.setCancelled(true);
      }
      return;
    }

    ItemStack hand = p.getInventory().getItemInMainHand();
    if (hand.getType() != Material.ENDER_PEARL) {
      return;
    }

    boolean taglock = isConduitTaglock(hand);
    if (taglock && isWithinCaptureFollowUp(p)) {
      e.setCancelled(true);
      return;
    }

    int level = getActiveLevel(p);
    boolean learned = level > 0;
    Block clicked = e.getClickedBlock();
    boolean container = learned && clicked != null && isConduitContainer(clicked);
    boolean blockUseDenied = clicked != null && e.useInteractedBlock() == Event.Result.DENY;

    switch (resolveGesture(taglock, learned, p.isSneaking(), container,
        !taglock && RiftPearls.isPlainPearl(hand))) {
      case IGNORE -> {
      }
      case CANCEL_ONLY -> e.setCancelled(true);
      case NEED_CONTAINER -> {
        e.setCancelled(true);
        ComponentMessenger.sendSection(p, C.GRAY + AdaptLanguage.text(RiftMessages.CONDUIT_MSG_NEED_CONTAINER));
        fx(p.getEyeLocation(), FxPriority.TRANSITION)
            .burst(Particles.SMOKE, 2, 0.15)
            .sound(Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.5f, 0.7f);
      }
      case BIND -> {
        e.setCancelled(true);
        if (blockUseDenied
            || !canInteract(p, clicked.getLocation())
            || !canAccessContainer(p, clicked)) {
          failBind(p, RiftMessages.CONDUIT_MSG_STALE);
          return;
        }
        // Consuming the taglock swaps the held item inside the gesture, which
        // breaks the client-prediction dedup and re-fires this interact. Hold
        // the follow-up window open so the refire cannot double-act on a refund.
        markCaptureFollowUp(p);
        completeBind(p, level, hand, clicked.getLocation());
      }
      case CAPTURE -> {
        e.setCancelled(true);
        if (!blockUseDenied
            && canInteract(p, clicked.getLocation())
            && canAccessContainer(p, clicked)) {
          beginCapture(p, hand, clicked.getLocation());
        }
      }
    }
  }

  /**
   * Gesture table for an ender pearl right-click in the main hand. Taglocks stay
   * inert for players without the adaptation, which means cancelled rather than
   * thrown. The caller separately honors block-use denial and container access.
   */
  static ConduitGesture resolveGesture(boolean taglock, boolean learned, boolean sneaking,
                                       boolean container, boolean plainPearl) {
    if (!learned) {
      return taglock ? ConduitGesture.CANCEL_ONLY : ConduitGesture.IGNORE;
    }
    if (taglock) {
      return container ? ConduitGesture.BIND : ConduitGesture.NEED_CONTAINER;
    }
    return sneaking && container && plainPearl ? ConduitGesture.CAPTURE : ConduitGesture.IGNORE;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(InventoryCloseEvent e) {
    if (!(e.getPlayer() instanceof Player p)) {
      return;
    }

    List<Location> sources = resolveFlowSources(e.getInventory());
    if (sources.isEmpty()) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    int throughput = getThroughput(level);
    double xpPerFlow = getConfig().xpPerFlow;
    for (Location sourceLoc : sources) {
      J.runAt(sourceLoc, () -> flowFromSource(p, sourceLoc, throughput, xpPerFlow), 1);
    }
  }

  /**
   * A double chest reports a midpoint location, so flooring it only ever hits
   * one half and links written on the other half never flow. Resolve both
   * halves from the holder instead.
   */
  private List<Location> resolveFlowSources(Inventory inventory) {
    InventoryHolder holder = inventory.getHolder();
    if (holder instanceof DoubleChest doubleChest) {
      List<Location> sides = new ArrayList<>(2);
      addContainerLocation(sides, doubleChest.getLeftSide());
      addContainerLocation(sides, doubleChest.getRightSide());
      return sides;
    }
    if (holder instanceof Container container) {
      return List.of(container.getBlock().getLocation());
    }

    Location loc = inventory.getLocation();
    return loc == null ? List.of() : List.of(loc.getBlock().getLocation());
  }

  private void addContainerLocation(List<Location> sides, InventoryHolder side) {
    if (side instanceof Container container) {
      sides.add(container.getBlock().getLocation());
    }
  }

  private void beginCapture(Player p, ItemStack hand, Location clicked) {
    Location loc = clicked.getBlock().getLocation();
    String linkId = UUID.randomUUID().toString();
    ItemStack taglock = makeTaglock(loc, linkId);
    ItemStack costUnit = hand.clone();
    costUnit.setAmount(1);
    AtomicBoolean defaultConsumed = new AtomicBoolean();
    if (!payItemCost(p, "capture", costUnit, 1, () -> {
      ItemStack current = p.getInventory().getItemInMainHand();
      if (!RiftPearls.isPlainPearl(current)) {
        return false;
      }
      decrementItemstack(current, p);
      defaultConsumed.set(true);
      return true;
    })) {
      Adapt.verbose("Rift Conduit capture cost was refused for " + p.getUniqueId() + ".");
      return;
    }

    deliverCreatedTaglock(p, taglock, defaultConsumed.get());
    markCaptureFollowUp(p);

    fx(loc.clone().add(0.5, 0.5, 0.5), FxPriority.TRANSITION)
        .ring(Particle.REVERSE_PORTAL, 0.7, 10, 0.0)
        .particle(Particles.END_ROD, 6, 0, 0.6, 0, 0.05, 0.02)
        .chord(Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.2f, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.5f);
    ComponentMessenger.sendSection(p, C.LIGHT_PURPLE + AdaptLanguage.text(RiftMessages.CONDUIT_MSG_CAPTURED));
  }

  private void markCaptureFollowUp(Player p) {
    captureFollowUpUntil.put(p.getUniqueId(), followUpDeadline(System.currentTimeMillis()));
  }

  private boolean isWithinCaptureFollowUp(Player p) {
    return isWithinFollowUpWindow(System.currentTimeMillis(), captureFollowUpUntil.get(p.getUniqueId()));
  }

  private void completeBind(Player p, int level, ItemStack taglock, Location clicked) {
    ConduitLocation stored = decodeLocation(getTaglockLocation(taglock));
    String linkId = getTaglockLinkId(taglock);
    if (stored == null || linkId == null) {
      failBind(p, RiftMessages.CONDUIT_MSG_STALE);
      return;
    }

    World aWorld = WorldIdentity.resolve(stored.worldKey()).orElse(null);
    if (aWorld == null) {
      failBind(p, RiftMessages.CONDUIT_MSG_STALE);
      return;
    }

    Location aLoc = new Location(aWorld, stored.x(), stored.y(), stored.z());
    Location bLoc = clicked.getBlock().getLocation();
    if (sameBlock(aLoc, bLoc)) {
      failBind(p, RiftMessages.CONDUIT_MSG_SAME);
      return;
    }

    if (!isCrossDimension(level)) {
      if (aWorld != bLoc.getWorld()) {
        failBind(p, RiftMessages.CONDUIT_MSG_RANGE);
        return;
      }
      double range = getRange(level);
      if (aLoc.distanceSquared(bLoc) > range * range) {
        failBind(p, RiftMessages.CONDUIT_MSG_RANGE);
        return;
      }
    }

    if (pendingBinds.containsKey(p.getUniqueId())) {
      Adapt.verbose("Rift Conduit ignored a bind for " + p.getUniqueId() + ": one is already in flight.");
      return;
    }
    if (!reserveEndpoints(aLoc, bLoc)) {
      failBind(p, RiftMessages.CONDUIT_MSG_STALE);
      return;
    }
    BindReservation reservation = reserveTaglock(p, linkId);
    if (reservation == null) {
      releaseEndpoints(aLoc, bLoc);
      Adapt.verbose("Rift Conduit could not reserve the taglock for " + p.getUniqueId() + ".");
      failBind(p, RiftMessages.CONDUIT_MSG_STALE);
      return;
    }
    BindOperation operation = new BindOperation(
        p,
        p.getUniqueId(),
        aLoc,
        bLoc,
        linkId,
        reservation,
        new AtomicReference<>(),
        new AtomicReference<>(),
        new AtomicBoolean()
    );
    if (pendingBinds.putIfAbsent(operation.playerId(), operation) != null) {
      refundBindReservation(p, reservation, AbilityRefundReason.ACTIVATION_ABORTED);
      releaseEndpoints(aLoc, bLoc);
      return;
    }
    loadBindRegion(
        aLoc,
        () -> writeSourceThenPartner(operation),
        () -> failBindOperation(operation, RiftMessages.CONDUIT_MSG_STALE,
            AbilityRefundReason.ACTIVATION_FAILED, true)
    );
    if (!J.runEntity(p, () -> failBindOperation(
        operation,
        RiftMessages.CONDUIT_MSG_STALE,
        AbilityRefundReason.EXPIRED,
        true
    ), BIND_TIMEOUT_TICKS)) {
      failBindOperation(
          operation,
          RiftMessages.CONDUIT_MSG_STALE,
          AbilityRefundReason.ACTIVATION_FAILED,
          true
      );
    }
  }

  private void writeSourceThenPartner(BindOperation operation) {
    synchronized (operation) {
      if (!isCurrentBind(operation)) {
        return;
      }
      BlockState st = operation.source().getBlock().getState();
      if (!(st instanceof Container aContainer) || !canUseContainer(operation.player(), aContainer)) {
        failBindOperation(operation, RiftMessages.CONDUIT_MSG_STALE,
            AbilityRefundReason.TARGET_LOST, true);
        return;
      }

      operation.sourceSnapshot().set(snapshotLink(aContainer));
      if (!writeLink(aContainer, operation.partner(), operation.linkId())) {
        failBindOperation(operation, RiftMessages.CONDUIT_MSG_STALE,
            AbilityRefundReason.ACTIVATION_FAILED, true);
        return;
      }
      loadBindRegion(
          operation.partner(),
          () -> finishBind(operation),
          () -> failBindOperation(operation, RiftMessages.CONDUIT_MSG_STALE,
              AbilityRefundReason.ACTIVATION_FAILED, true)
      );
    }
  }

  private void finishBind(BindOperation operation) {
    synchronized (operation) {
      if (!isCurrentBind(operation)) {
        return;
      }
      BlockState st = operation.partner().getBlock().getState();
      if (!(st instanceof Container bContainer) || !canUseContainer(operation.player(), bContainer)) {
        failBindOperation(operation, RiftMessages.CONDUIT_MSG_STALE,
            AbilityRefundReason.TARGET_LOST, true);
        return;
      }

      operation.partnerSnapshot().set(snapshotLink(bContainer));
      if (!writeLink(bContainer, operation.source(), operation.linkId())) {
        failBindOperation(operation, RiftMessages.CONDUIT_MSG_STALE,
            AbilityRefundReason.ACTIVATION_FAILED, true);
        return;
      }
      loadBindRegion(
          operation.source(),
          () -> confirmSourceAndFinalize(operation),
          () -> failBindOperation(operation, RiftMessages.CONDUIT_MSG_STALE,
              AbilityRefundReason.ACTIVATION_FAILED, true)
      );
    }
  }

  private void confirmSourceAndFinalize(BindOperation operation) {
    synchronized (operation) {
      if (!isCurrentBind(operation)) {
        return;
      }
      BlockState state = operation.source().getBlock().getState();
      if (!(state instanceof Container source)
          || !canUseContainer(operation.player(), source)
          || !linkMatches(source, operation.linkId(), operation.partner())) {
        failBindOperation(operation, RiftMessages.CONDUIT_MSG_STALE,
            AbilityRefundReason.TARGET_LOST, true);
        return;
      }
      if (!J.runEntity(operation.player(), () -> finalizeBindOwned(operation))) {
        failBindOperation(operation, RiftMessages.CONDUIT_MSG_STALE,
            AbilityRefundReason.ACTIVATION_FAILED, true);
      }
    }
  }

  private void finalizeBindOwned(BindOperation operation) {
    Player p = operation.player();
    synchronized (operation) {
      if (!canFinalizeBind(isCurrentBind(operation), p.isOnline(), getActiveLevel(p) > 0)) {
        failBindOperation(operation, RiftMessages.CONDUIT_MSG_STALE,
            AbilityRefundReason.ACTIVATION_ABORTED, true);
        return;
      }
      if (!operation.completed().compareAndSet(false, true)) {
        return;
      }
      pendingBinds.remove(operation.playerId(), operation);
    }
    if (!settleBindReservation(p, operation.reservation())) {
      rollbackEndpoints(operation);
      failBind(p, RiftMessages.CONDUIT_MSG_STALE);
      return;
    }
    releaseEndpoints(operation.source(), operation.partner());

    addStat(p, "rift.conduit.links-formed", 1);
    xp(p, getConfig().xpOnLink, "rift:conduit:link");
    ComponentMessenger.sendSection(p, C.LIGHT_PURPLE + AdaptLanguage.text(RiftMessages.CONDUIT_MSG_LINKED));
    J.runAt(operation.partner(), () -> fx(
        operation.partner().clone().add(0.5, 0.5, 0.5),
        FxPriority.TRANSITION
    )
        .ring(Particles.END_ROD, 0.8, 12, 0.1)
        .particle(Particle.REVERSE_PORTAL, 8, 0, 0.6, 0, 0.2, 0.03)
        .chord(Sound.BLOCK_CONDUIT_ACTIVATE, 0.6f, 1.1f,
            Sound.BLOCK_BEACON_ACTIVATE, 0.4f, 1.4f));
  }

  private void failBind(Player p, TextKey messageKey) {
    if (!p.isOnline()) {
      return;
    }
    ComponentMessenger.sendSection(p, C.RED + AdaptLanguage.text(messageKey));
    fx(p.getEyeLocation(), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 3, 0.2)
        .sound(Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.6f, 0.6f);
  }

  private void deliverCreatedTaglock(Player p, ItemStack taglock, boolean defaultConsumed) {
    ItemStack hand = p.getInventory().getItemInMainHand();
    if (defaultConsumed && (hand == null || hand.getType().isAir())) {
      p.getInventory().setItemInMainHand(taglock);
      return;
    }
    deliverExactTaglock(p, taglock);
  }

  private BindReservation reserveTaglock(Player p, String linkId) {
    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!isConduitTaglock(hand) || !linkId.equals(getTaglockLinkId(hand))) {
      return null;
    }
    ItemStack unit = hand.clone();
    unit.setAmount(1);
    AtomicReference<ItemStack> defaultItem = new AtomicReference<>();
    AbilityCharge charge = payItemCostDeferred(p, "bind", unit, 1, () -> {
      ItemStack current = p.getInventory().getItemInMainHand();
      if (!isConduitTaglock(current) || !linkId.equals(getTaglockLinkId(current))) {
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
        deliverExactTaglock(p, reserved);
        p.getPersistentDataContainer().remove(reservationKey);
        Adapt.error(error);
        return false;
      }
    });
    if (!charge.allowed()) {
      ItemStack consumed = defaultItem.get();
      if (consumed != null) {
        deliverExactTaglock(p, consumed);
        p.getPersistentDataContainer().remove(reservationKey);
      }
      return null;
    }
    return new BindReservation(charge, defaultItem.get(), new AtomicBoolean());
  }

  private boolean settleBindReservation(Player p, BindReservation reservation) {
    if (!reservation.resolved().compareAndSet(false, true)) {
      return false;
    }
    boolean settled = settleCost(reservation.charge().activationId());
    if (!acceptSettlement(
        reservation.defaultItem() != null,
        reservation.charge().defaultCostSuppressed(),
        settled
    )) {
      Adapt.warn("Rift Conduit could not settle the deferred provider cost for " + p.getUniqueId() + ".");
      return false;
    }
    if (reservation.defaultItem() != null) {
      p.getPersistentDataContainer().remove(reservationKey);
    }
    return true;
  }

  private void refundBindReservation(Player p, BindReservation reservation, AbilityRefundReason reason) {
    ItemStack item = releaseProviderReservation(reservation, reason);
    if (item == null) {
      return;
    }
    try {
      deliverExactTaglock(p, item);
      p.getPersistentDataContainer().remove(reservationKey);
    } catch (Throwable error) {
      Adapt.error("Rift Conduit could not return a reserved taglock to " + p.getUniqueId() + ".");
      Adapt.error(error);
    }
  }

  private ItemStack releaseProviderReservation(BindReservation reservation, AbilityRefundReason reason) {
    if (reservation == null || !reservation.resolved().compareAndSet(false, true)) {
      return null;
    }
    boolean refunded = refundCost(reservation.charge().activationId(), reason);
    if (reservation.charge().defaultCostSuppressed() && !refunded) {
      Adapt.warn("Rift Conduit could not resolve the deferred provider cost for "
          + reservation.charge().activationId() + ".");
    }
    return reservation.defaultItem();
  }

  private void failBindOperation(BindOperation operation, TextKey messageKey,
                                 AbilityRefundReason reason, boolean feedback) {
    synchronized (operation) {
      if (!operation.completed().compareAndSet(false, true)) {
        return;
      }
      pendingBinds.remove(operation.playerId(), operation);
    }
    rollbackEndpoints(operation);
    Runnable finishFailure = () -> {
      refundBindReservation(operation.player(), operation.reservation(), reason);
      if (feedback) {
        failBind(operation.player(), messageKey);
      }
    };
    if (!J.runEntity(operation.player(), finishFailure)) {
      releaseProviderReservation(operation.reservation(), reason);
    }
  }

  private boolean isCurrentBind(BindOperation operation) {
    return !operation.completed().get()
        && pendingBinds.get(operation.playerId()) == operation
        && isRuntimeRegistered();
  }

  private boolean reserveEndpoints(Location source, Location partner) {
    EndpointKey sourceKey = endpointKey(source);
    EndpointKey partnerKey = endpointKey(partner);
    synchronized (bindingEndpoints) {
      if (bindingEndpoints.contains(sourceKey) || bindingEndpoints.contains(partnerKey)) {
        return false;
      }
      bindingEndpoints.add(sourceKey);
      bindingEndpoints.add(partnerKey);
      return true;
    }
  }

  private void releaseEndpoints(Location source, Location partner) {
    synchronized (bindingEndpoints) {
      bindingEndpoints.remove(endpointKey(source));
      bindingEndpoints.remove(endpointKey(partner));
    }
  }

  private EndpointKey endpointKey(Location location) {
    World world = location.getWorld();
    return new EndpointKey(
        world == null ? new UUID(0L, 0L) : world.getUID(),
        location.getBlockX(),
        location.getBlockY(),
        location.getBlockZ()
    );
  }

  private EndpointSnapshot snapshotLink(Container container) {
    PersistentDataContainer data = container.getPersistentDataContainer();
    return new EndpointSnapshot(
        data.get(partnerKey, PersistentDataType.STRING),
        data.get(linkKey, PersistentDataType.STRING)
    );
  }

  private void rollbackEndpoints(BindOperation operation) {
    EndpointSnapshot sourceSnapshot = operation.sourceSnapshot().get();
    EndpointSnapshot partnerSnapshot = operation.partnerSnapshot().get();
    int restoreCount = (sourceSnapshot == null ? 0 : 1) + (partnerSnapshot == null ? 0 : 1);
    if (restoreCount == 0) {
      releaseEndpoints(operation.source(), operation.partner());
      return;
    }
    AtomicInteger remaining = new AtomicInteger(restoreCount);
    Runnable completed = () -> {
      if (remaining.decrementAndGet() == 0) {
        releaseEndpoints(operation.source(), operation.partner());
      }
    };
    if (sourceSnapshot != null) {
      loadBindRegion(
          operation.source(),
          () -> {
            restoreEndpoint(operation.source(), operation.linkId(), sourceSnapshot);
            completed.run();
          },
          () -> {
            reportRollbackFailure(operation.source(), operation.linkId());
            completed.run();
          }
      );
    }
    if (partnerSnapshot != null) {
      loadBindRegion(
          operation.partner(),
          () -> {
            restoreEndpoint(operation.partner(), operation.linkId(), partnerSnapshot);
            completed.run();
          },
          () -> {
            reportRollbackFailure(operation.partner(), operation.linkId());
            completed.run();
          }
      );
    }
  }

  private void restoreEndpoint(Location location, String operationLinkId, EndpointSnapshot snapshot) {
    BlockState state = location.getBlock().getState();
    if (!(state instanceof Container container)) {
      return;
    }
    PersistentDataContainer data = container.getPersistentDataContainer();
    if (!shouldRestoreEndpoint(data.get(linkKey, PersistentDataType.STRING), operationLinkId)) {
      return;
    }
    restoreValue(data, partnerKey, snapshot.partner());
    restoreValue(data, linkKey, snapshot.linkId());
    if (!container.update(true)) {
      reportRollbackFailure(location, operationLinkId);
    }
  }

  private void restoreValue(PersistentDataContainer data, NamespacedKey key, String value) {
    if (value == null) {
      data.remove(key);
    } else {
      data.set(key, PersistentDataType.STRING, value);
    }
  }

  private void reportRollbackFailure(Location location, String linkId) {
    Adapt.error("Rift Conduit could not roll back link " + linkId + " at " + location + ".");
  }

  private void loadBindRegion(Location location, Runnable task, Runnable failure) {
    World world = location.getWorld();
    if (world == null) {
      failure.run();
      return;
    }
    Runnable guardedTask = () -> {
      try {
        task.run();
      } catch (Throwable error) {
        Adapt.error(error);
        failure.run();
      }
    };
    if (world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
      if (!J.runAt(location, guardedTask)) {
        failure.run();
      }
      return;
    }

    CompletableFuture<Chunk> load;
    try {
      load = Adapt.platform.getChunkAtAsync(location);
    } catch (Throwable error) {
      Adapt.error(error);
      failure.run();
      return;
    }
    if (load == null) {
      failure.run();
      return;
    }
    load.whenComplete((chunk, error) -> {
      if (error != null) {
        Adapt.error(error);
        failure.run();
        return;
      }
      if (chunk == null || !J.runAt(location, guardedTask)) {
        failure.run();
      }
    });
  }

  @EventHandler
  public void on(PlayerQuitEvent event) {
    UUID playerId = event.getPlayer().getUniqueId();
    captureFollowUpUntil.remove(playerId);
    BindOperation operation = pendingBinds.get(playerId);
    if (operation != null) {
      failBindOperation(
          operation,
          RiftMessages.CONDUIT_MSG_STALE,
          AbilityRefundReason.PLAYER_LEFT,
          false
      );
    }
  }

  @EventHandler
  public void on(PlayerJoinEvent event) {
    restoreReservedTaglock(event.getPlayer());
  }

  private void restoreReservedTaglock(Player p) {
    byte[] serialized = p.getPersistentDataContainer().get(reservationKey, PersistentDataType.BYTE_ARRAY);
    if (serialized == null || serialized.length == 0) {
      return;
    }
    try {
      deliverExactTaglock(p, ItemStack.deserializeBytes(serialized));
      p.getPersistentDataContainer().remove(reservationKey);
    } catch (RuntimeException error) {
      Adapt.error("Rift Conduit could not recover a reserved taglock for " + p.getUniqueId() + ".");
      Adapt.error(error);
    }
  }

  @Override
  public void unregister() {
    for (BindOperation operation : List.copyOf(pendingBinds.values())) {
      failBindOperation(
          operation,
          RiftMessages.CONDUIT_MSG_STALE,
          AbilityRefundReason.ADAPTATION_DISABLED,
          false
      );
    }
    pendingBinds.clear();
    captureFollowUpUntil.clear();
    super.unregister();
  }

  static boolean acceptSettlement(boolean defaultConsumed, boolean defaultCostSuppressed, boolean settled) {
    return defaultConsumed || !defaultCostSuppressed || settled;
  }

  static boolean canFinalizeBind(boolean current, boolean online, boolean learned) {
    return current && online && learned;
  }

  static boolean shouldRestoreEndpoint(String currentLinkId, String operationLinkId) {
    return operationLinkId != null && operationLinkId.equals(currentLinkId);
  }

  static void deliverExactTaglock(Player p, ItemStack item) {
    p.getInventory().addItem(item).values()
        .forEach(leftover -> p.getWorld().dropItemNaturally(p.getLocation(), leftover));
  }

  private void flowFromSource(Player p, Location sourceLoc, int throughput, double xpPerFlow) {
    BlockState st = sourceLoc.getBlock().getState();
    if (!(st instanceof Container source) || !canUseContainer(p, source)) {
      return;
    }

    LinkRef link = readLink(source);
    if (link == null) {
      return;
    }

    List<ItemStack> moving = extractItems(source.getInventory(), throughput);
    if (moving.isEmpty()) {
      return;
    }

    Location partnerLoc = link.location();
    String linkId = link.linkId();
    AtomicBoolean settled = new AtomicBoolean(false);
    boolean recoveryScheduled = J.runAt(sourceLoc, () -> {
      if (settled.compareAndSet(false, true)) {
        restoreToSource(sourceLoc, moving);
      }
    }, FLOW_DELIVERY_TIMEOUT_TICKS);
    if (!recoveryScheduled) {
      settled.set(true);
      dropAt(sourceLoc, addItems(source.getInventory(), moving));
      return;
    }

    try {
      loadChunkAsync(partnerLoc, chunk -> {
        boolean scheduled = J.runAt(partnerLoc, () -> {
          if (settled.compareAndSet(false, true)) {
            depositToPartnerSafely(p, sourceLoc, partnerLoc, linkId, moving, xpPerFlow);
          }
        });
        if (!scheduled && settled.compareAndSet(false, true)) {
          restoreToSource(sourceLoc, moving);
        }
      });
    } catch (Throwable error) {
      Adapt.error("Rift Conduit transfer " + linkId + " could not start partner loading at "
          + partnerLoc + "; returning " + totalAmount(moving) + " items to " + sourceLoc + ".");
      Adapt.error(error);
      if (settled.compareAndSet(false, true)) {
        dropAt(sourceLoc, addItems(source.getInventory(), moving));
      }
    }
  }

  private void depositToPartnerSafely(Player p, Location sourceLoc, Location partnerLoc, String linkId,
                                      List<ItemStack> moving, double xpPerFlow) {
    try {
      depositToPartner(p, sourceLoc, partnerLoc, linkId, moving, xpPerFlow);
    } catch (Throwable error) {
      Adapt.error("Rift Conduit transfer " + linkId + " failed while depositing at " + partnerLoc
          + "; returning " + totalAmount(moving) + " items to " + sourceLoc + ".");
      Adapt.error(error);
      restoreToSource(sourceLoc, moving);
    }
  }

  private void depositToPartner(Player p, Location sourceLoc, Location partnerLoc, String linkId,
                                List<ItemStack> moving, double xpPerFlow) {
    BlockState st = partnerLoc.getBlock().getState();
    if (!(st instanceof Container partner)
        || !linkMatches(partner, linkId, sourceLoc)
        || !canUseContainer(p, partner)) {
      restoreToSource(sourceLoc, moving);
      return;
    }

    List<ItemStack> leftovers = addItems(partner.getInventory(), moving);
    int moved = totalAmount(moving) - totalAmount(leftovers);
    if (!leftovers.isEmpty()) {
      restoreToSource(sourceLoc, leftovers);
    }

    if (moved <= 0) {
      return;
    }

    fx(partnerLoc.clone().add(0.5, 0.9, 0.5), FxPriority.AMBIENT)
        .particle(Particle.REVERSE_PORTAL, 6, 0, 0.3, 0, 0.2, 0.03)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.6f);
    int delivered = moved;
    J.runEntity(p, () -> {
      if (!p.isOnline()) {
        return;
      }
      addStat(p, "rift.conduit.items-flowed", delivered);
      xp(p, delivered * xpPerFlow, "rift:conduit:flow");
    });
  }

  private void restoreToSource(Location sourceLoc, List<ItemStack> items) {
    if (items.isEmpty()) {
      return;
    }
    loadBindRegion(
        sourceLoc,
        () -> {
          BlockState st = sourceLoc.getBlock().getState();
          if (st instanceof Container container) {
            dropAt(sourceLoc, addItems(container.getInventory(), items));
          } else {
            dropAt(sourceLoc, items);
          }
        },
        () -> Adapt.error("Rift Conduit stranded " + totalAmount(items)
            + " items because their source transfer recovery could not run at " + sourceLoc + ".")
    );
  }

  private void dropAt(Location loc, List<ItemStack> items) {
    World world = loc.getWorld();
    if (world == null) {
      return;
    }
    Location drop = loc.clone().add(0.5, 0.5, 0.5);
    for (ItemStack item : items) {
      if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
        world.dropItem(drop, item);
      }
    }
  }

  private List<ItemStack> extractItems(Inventory inventory, int budget) {
    List<ItemStack> removed = new ArrayList<>();
    int remaining = Math.max(0, budget);
    ItemStack[] contents = inventory.getContents();
    for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
      ItemStack stack = contents[slot];
      if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
        continue;
      }
      int take = Math.min(remaining, stack.getAmount());
      ItemStack piece = stack.clone();
      piece.setAmount(take);
      removed.add(piece);
      remaining -= take;
      if (take >= stack.getAmount()) {
        inventory.setItem(slot, null);
      } else {
        ItemStack reduced = stack.clone();
        reduced.setAmount(stack.getAmount() - take);
        inventory.setItem(slot, reduced);
      }
    }
    return removed;
  }

  private List<ItemStack> addItems(Inventory inventory, List<ItemStack> items) {
    List<ItemStack> leftovers = new ArrayList<>();
    for (ItemStack item : items) {
      if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
        continue;
      }
      Map<Integer, ItemStack> overflow = inventory.addItem(item.clone());
      for (ItemStack leftover : overflow.values()) {
        if (leftover != null && !leftover.getType().isAir() && leftover.getAmount() > 0) {
          leftovers.add(leftover);
        }
      }
    }
    return leftovers;
  }

  private int totalAmount(List<ItemStack> items) {
    int sum = 0;
    for (ItemStack item : items) {
      if (item != null && !item.getType().isAir()) {
        sum += item.getAmount();
      }
    }
    return sum;
  }

  private boolean isConduitContainer(Block block) {
    return isStorage(block.getBlockData()) && block.getState() instanceof Container;
  }

  private boolean canAccessContainer(Player player, Block block) {
    if (!(block.getState() instanceof Container container)) {
      return false;
    }
    List<Block> physicalBlocks = ProtectionEventProbe.containerBlocks(block, null);
    if (J.isFoliaThreading()) {
      for (Block physicalBlock : physicalBlocks) {
        if (!J.isOwnedByCurrentRegion(physicalBlock.getLocation())) {
          return false;
        }
      }
    }
    List<Block> blocks = ProtectionEventProbe.containerBlocks(block, container.getInventory());
    for (Block physicalBlock : blocks) {
      if (!canAccessChest(player, physicalBlock.getLocation())) {
        return false;
      }
    }
    try {
      for (Block physicalBlock : blocks) {
        if (!physicalBlock.equals(block) && !ProtectionEventProbe.attemptBlockUse(player, physicalBlock)) {
          return false;
        }
      }
      return true;
    } catch (RuntimeException error) {
      Adapt.error("Rift Conduit could not verify container access for " + player.getUniqueId() + ".");
      Adapt.error(error);
      return false;
    }
  }

  private boolean canUseContainer(Player player, Container container) {
    if (!J.isOwnedByCurrentRegion(player) || !player.isOnline()) {
      return false;
    }

    Block containerBlock = container.getBlock();
    List<Block> blocks = ProtectionEventProbe.containerBlocks(containerBlock, null);
    for (Block block : blocks) {
      if (!J.isOwnedByCurrentRegion(block.getLocation())
          || !canAccessChest(player, block.getLocation())) {
        return false;
      }
    }

    try {
      return ProtectionEventProbe.attemptContainerOpen(player, blocks);
    } catch (RuntimeException error) {
      Adapt.error("Rift Conduit could not verify container access for " + player.getUniqueId() + ".");
      Adapt.error(error);
      return false;
    }
  }

  private LinkRef readLink(Container container) {
    PersistentDataContainer pdc = container.getPersistentDataContainer();
    String partnerRaw = pdc.get(partnerKey, PersistentDataType.STRING);
    String linkId = pdc.get(linkKey, PersistentDataType.STRING);
    if (partnerRaw == null || linkId == null) {
      return null;
    }
    ConduitLocation decoded = decodeLocation(partnerRaw);
    if (decoded == null) {
      return null;
    }
    World world = WorldIdentity.resolve(decoded.worldKey()).orElse(null);
    if (world == null) {
      return null;
    }
    return new LinkRef(new Location(world, decoded.x(), decoded.y(), decoded.z()), linkId);
  }

  private boolean writeLink(Container container, Location partner, String linkId) {
    World world = partner.getWorld();
    if (world == null) {
      return false;
    }
    PersistentDataContainer pdc = container.getPersistentDataContainer();
    pdc.set(partnerKey, PersistentDataType.STRING,
        encodeLocation(WorldIdentity.serialize(world), partner.getBlockX(), partner.getBlockY(), partner.getBlockZ()));
    pdc.set(linkKey, PersistentDataType.STRING, linkId);
    return container.update();
  }

  private boolean linkMatches(Container partner, String linkId, Location sourceLoc) {
    PersistentDataContainer pdc = partner.getPersistentDataContainer();
    String partnerLinkId = pdc.get(linkKey, PersistentDataType.STRING);
    if (partnerLinkId == null || !partnerLinkId.equals(linkId)) {
      return false;
    }
    ConduitLocation decoded = decodeLocation(pdc.get(partnerKey, PersistentDataType.STRING));
    World world = sourceLoc.getWorld();
    return decoded != null
        && world != null
        && decoded.worldKey().equals(WorldIdentity.serialize(world))
        && decoded.x() == sourceLoc.getBlockX()
        && decoded.y() == sourceLoc.getBlockY()
        && decoded.z() == sourceLoc.getBlockZ();
  }

  private ItemStack makeTaglock(Location loc, String linkId) {
    ItemStack item = new ItemStack(Material.ENDER_PEARL, 1);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    meta.setDisplayName(C.LIGHT_PURPLE + AdaptLanguage.text(RiftMessages.CONDUIT_TAGLOCK_NAME));
    List<String> lore = new ArrayList<>();
    lore.add(C.GRAY + AdaptLanguage.text(RiftMessages.CONDUIT_TAGLOCK_LORE1));
    lore.add(C.DARK_GRAY.toString() + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
    lore.add(C.DARK_GRAY + AdaptLanguage.text(RiftMessages.CONDUIT_TAGLOCK_LORE2));
    meta.setLore(lore);
    World world = loc.getWorld();
    if (world != null) {
      meta.getPersistentDataContainer().set(taglockLocKey, PersistentDataType.STRING,
          encodeLocation(WorldIdentity.serialize(world), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }
    meta.getPersistentDataContainer().set(taglockIdKey, PersistentDataType.STRING, linkId);
    item.setItemMeta(meta);
    return item;
  }

  private boolean isConduitTaglock(ItemStack stack) {
    if (stack == null || stack.getType() != Material.ENDER_PEARL || !stack.hasItemMeta()) {
      return false;
    }
    PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
    return pdc.has(taglockLocKey, PersistentDataType.STRING) && pdc.has(taglockIdKey, PersistentDataType.STRING);
  }

  private String getTaglockLocation(ItemStack stack) {
    if (!isConduitTaglock(stack)) {
      return null;
    }
    return stack.getItemMeta().getPersistentDataContainer().get(taglockLocKey, PersistentDataType.STRING);
  }

  private String getTaglockLinkId(ItemStack stack) {
    if (!isConduitTaglock(stack)) {
      return null;
    }
    return stack.getItemMeta().getPersistentDataContainer().get(taglockIdKey, PersistentDataType.STRING);
  }

  private boolean sameBlock(Location a, Location b) {
    return a.getWorld() == b.getWorld()
        && a.getBlockX() == b.getBlockX()
        && a.getBlockY() == b.getBlockY()
        && a.getBlockZ() == b.getBlockZ();
  }

  private boolean isCrossDimension(int level) {
    return getConfig().crossDimensionAtMax && level >= getMaxLevel();
  }

  private int getThroughput(int level) {
    return boundedThroughput(getConfig().throughputBase + (getLevelPercent(level) * getConfig().throughputFactor));
  }

  private double getRange(int level) {
    return boundedRange(getConfig().rangeBase + (getLevelPercent(level) * getConfig().rangeFactor));
  }

  static int boundedThroughput(double value) {
    if (!Double.isFinite(value)) {
      return 1;
    }
    return Math.max(1, Math.min(HARD_MAX_FLOW_ITEMS, (int) Math.round(value)));
  }

  static double boundedRange(double value) {
    if (!Double.isFinite(value)) {
      return 1D;
    }
    return Math.max(1D, Math.min(HARD_MAX_RANGE, value));
  }

  static long followUpDeadline(long nowMillis) {
    return nowMillis + CAPTURE_FOLLOWUP_WINDOW_MILLIS;
  }

  static boolean isWithinFollowUpWindow(long nowMillis, Long untilMillis) {
    return untilMillis != null && nowMillis <= untilMillis;
  }

  static String encodeLocation(String worldKey, int x, int y, int z) {
    return WorldIdentity.parse(worldKey) + "|" + x + "|" + y + "|" + z;
  }

  static ConduitLocation decodeLocation(String raw) {
    if (raw == null) {
      return null;
    }
    int p3 = raw.lastIndexOf('|');
    if (p3 <= 0) {
      return null;
    }
    int p2 = raw.lastIndexOf('|', p3 - 1);
    if (p2 <= 0) {
      return null;
    }
    int p1 = raw.lastIndexOf('|', p2 - 1);
    if (p1 <= 0) {
      return null;
    }
    try {
      String worldKey = WorldIdentity.parse(raw.substring(0, p1)).toString();
      int x = Integer.parseInt(raw.substring(p1 + 1, p2));
      int y = Integer.parseInt(raw.substring(p2 + 1, p3));
      int z = Integer.parseInt(raw.substring(p3 + 1));
      return new ConduitLocation(worldKey, x, y, z);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  @ConfigDescription("Bind two containers with paired taglocks; items placed in one flow to the other, and cross dimensions at max level.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base number of items moved per flow before level scaling.", impact = "Higher values move more items each time a linked container is closed, up to a hard 1152-item limit.")
    double throughputBase = 48;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional items moved per flow granted at max level, scaling with level.", impact = "Higher values widen the gap between low-level and max-level throughput.")
    double throughputFactor = 336;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base binding range in blocks before level scaling.", impact = "Higher values allow linking containers that are further apart, up to a hard 512-block limit.")
    double rangeBase = 24;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional binding range in blocks granted at max level, scaling with level.", impact = "Higher values let higher levels link containers much further apart.")
    double rangeFactor = 200;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Allows binding across different worlds once the adaptation reaches max level.", impact = "True lets a max-level link ignore world and distance limits; false keeps every link same-world.")
    boolean crossDimensionAtMax = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Skill XP granted when a new link is formed.", impact = "Higher values grant more skill XP per link created.")
    double xpOnLink = 30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Skill XP granted per item flowed between linked containers.", impact = "Higher values grant more skill XP as items move through the conduit.")
    double xpPerFlow = 0.4;

    public Config() {
      baseCost = 8;
      costFactor = 0.6;
      maxLevel = 4;
      initialCost = 8;
    }
  }

  enum ConduitGesture {
    IGNORE,
    CANCEL_ONLY,
    NEED_CONTAINER,
    BIND,
    CAPTURE
  }

  record ConduitLocation(String worldKey, int x, int y, int z) {
  }

  private record LinkRef(Location location, String linkId) {
  }

  record EndpointSnapshot(String partner, String linkId) {
  }

  private record EndpointKey(UUID worldId, int x, int y, int z) {
  }

  private record BindReservation(
      AbilityCharge charge,
      ItemStack defaultItem,
      AtomicBoolean resolved
  ) {
  }

  private record BindOperation(
      Player player,
      UUID playerId,
      Location source,
      Location partner,
      String linkId,
      BindReservation reservation,
      AtomicReference<EndpointSnapshot> sourceSnapshot,
      AtomicReference<EndpointSnapshot> partnerSnapshot,
      AtomicBoolean completed
  ) {
  }
}
