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
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.adaptation.chunk.ChunkLoading;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RiftMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.InventoryCloseEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class RiftConduit extends SimpleAdaptation<RiftConduit.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> LINKING = CommonPreferences.toggle("linking", RiftMessages.RIFTCONDUIT_PREFERENCE_LINKING, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> FLOW = CommonPreferences.toggle("flow", RiftMessages.RIFTCONDUIT_PREFERENCE_FLOW, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<Items> ITEMS = new PlayerPreference<>(Items.class,
      new PlayerPreference.Definition<>("items", RiftMessages.RIFTCONDUIT_PREFERENCE_ITEMS, Items.ALL, List.of(
          new PlayerPreference.Choice<>(Items.ALL, RiftMessages.RIFTCONDUIT_PREFERENCE_ITEMS_ALL, Id.minecraft("hopper"), 1),
          new PlayerPreference.Choice<>(Items.BLOCKS, RiftMessages.RIFTCONDUIT_PREFERENCE_ITEMS_BLOCKS, Id.minecraft("stone"), 1),
          new PlayerPreference.Choice<>(Items.FOOD, RiftMessages.RIFTCONDUIT_PREFERENCE_ITEMS_FOOD, Id.minecraft("bread"), 1))));

  static final int HARD_MAX_FLOW_ITEMS = 1152;
  static final double HARD_MAX_RANGE = 512D;
  static final int FLOW_DELIVERY_TIMEOUT_TICKS = 100;
  static final int BIND_TIMEOUT_TICKS = 400;
  static final long CAPTURE_FOLLOWUP_WINDOW_MILLIS = 150L;
  static final String TAGLOCK_LOC_KEY_NAME = "rift_conduit_taglock_loc";
  static final String TAGLOCK_ID_KEY_NAME = "rift_conduit_taglock_id";

  private static final Id ENDER_PEARL = Id.minecraft("ender_pearl");
  private static final Pattern WORLD_NAMESPACE = Pattern.compile("[a-z0-9._-]+");
  private static final Pattern WORLD_PATH = Pattern.compile("[a-z0-9/._-]+");

  private final DataKey<String> ownerKey;
  private final DataKey<String> partnerKey;
  private final DataKey<String> linkKey;
  private final DataKey<String> taglockLocKey;
  private final DataKey<String> taglockIdKey;
  private final DataKey<byte[]> reservationKey;
  private final Map<UUID, Long> captureFollowUpUntil = playerState();
  private final Map<UUID, BindOperation> pendingBinds = new ConcurrentHashMap<>();
  private final Set<EndpointKey> bindingEndpoints = ConcurrentHashMap.newKeySet();

  public RiftConduit() {
    super("rift-conduit");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("conduit"));
    setInterval(1000);
    ownerKey = DataKey.adapt("rift_conduit_owner", DataCodecs.STRING);
    partnerKey = DataKey.adapt("rift_conduit_partner", DataCodecs.STRING);
    linkKey = DataKey.adapt("rift_conduit_link", DataCodecs.STRING);
    taglockLocKey = DataKey.adapt(TAGLOCK_LOC_KEY_NAME, DataCodecs.STRING);
    taglockIdKey = DataKey.adapt(TAGLOCK_ID_KEY_NAME, DataCodecs.STRING);
    reservationKey = DataKey.adapt("rift_conduit_taglock_reservation", DataCodecs.BYTE_ARRAY);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_pearl"))
        .key("challenge_rift_conduit_10")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("conduit"))
            .key("challenge_rift_conduit_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_conduit_10", "rift.conduit.links-formed", 10, 500);
    registerMilestone("challenge_rift_conduit_10k", "rift.conduit.items-flowed", 10000, 1500);
  }

  private static ItemOps items() {
    return AdaptCore.platform().host().items();
  }

  private static boolean acceptsItem(Items selection, Id type) {
    return switch (selection) {
      case ALL -> true;
      case BLOCKS -> items().isBlock(type);
      case FOOD -> items().isEdible(type);
    };
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, LINKING, FLOW, ITEMS);
  }

  @Override
  protected void onRuntimeActivated() {
    for (PlayerRef player : AdaptCore.platform().host().onlinePlayers()) {
      Tasks.runEntity(player, () -> restoreReservedTaglock(player));
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getThroughput(level), 1);
    statLore(v, Form.f(getRange(level), 0), 2);
    if (isCrossDimension(level)) {
      v.addLore(C.LIGHT_PURPLE + "+ " + AdaptLanguage.text(RiftMessages.CONDUIT_LORE3));
    }
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if (action != InteractAction.RIGHT_CLICK_BLOCK && action != InteractAction.RIGHT_CLICK_AIR) {
      return;
    }

    PlayerRef p = e.player();
    if (e.hand() != Hand.MAIN_HAND) {
      if (e.hand() == Hand.OFF_HAND && isConduitTaglock(p.inventory().offHand())) {
        e.setCancelled(true);
      }
      return;
    }

    ItemRef hand = p.inventory().mainHand();
    if (!hand.type().equals(ENDER_PEARL)) {
      return;
    }

    boolean taglock = isConduitTaglock(hand);
    if (taglock && isWithinCaptureFollowUp(p)) {
      e.setCancelled(true);
      return;
    }

    int level = getActiveLevel(p);
    boolean learned = level > 0 && preferenceEnabled(p, LINKING);
    BlockRef clicked = e.clickedBlock();
    boolean container = learned && clicked != null && isConduitContainer(clicked);
    boolean blockUseDenied = clicked != null && e.useBlock() == UseResult.DENY;

    switch (resolveGesture(taglock, learned, p.isSneaking(), container,
        !taglock && RiftPearls.isPlainPearl(hand))) {
      case IGNORE -> {
      }
      case CANCEL_ONLY -> e.setCancelled(true);
      case NEED_CONTAINER -> {
        e.setCancelled(true);
        p.sendMessage(C.GRAY + AdaptLanguage.text(RiftMessages.CONDUIT_MSG_NEED_CONTAINER));
        fx(p.eyeLocation(), FxPriority.TRANSITION)
            .burst(Particles.SMOKE, 2, 0.15)
            .sound(Sounds.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.5f, 0.7f);
      }
      case BIND -> {
        e.setCancelled(true);
        if (blockUseDenied
            || !canInteract(p, clicked.location())
            || !canAccessContainer(p, clicked)) {
          failBind(p, RiftMessages.CONDUIT_MSG_STALE);
          return;
        }
        // Consuming the taglock swaps the held item inside the gesture, which
        // breaks the client-prediction dedup and re-fires this interact. Hold
        // the follow-up window open so the refire cannot double-act on a refund.
        markCaptureFollowUp(p);
        completeBind(p, level, hand, clicked.location());
      }
      case CAPTURE -> {
        e.setCancelled(true);
        if (!blockUseDenied
            && canInteract(p, clicked.location())
            && canAccessContainer(p, clicked)) {
          beginCapture(p, hand, clicked.location());
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

  @Handle(priority = EventPriority.MONITOR)
  public void on(InventoryCloseEvent e) {
    PlayerRef p = e.player();
    List<Loc> sources = resolveFlowSources(p);
    if (sources.isEmpty()) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!preferenceEnabled(p, FLOW)) {
      return;
    }
    Items selection = preference(p, ITEMS);
    UUID closingPlayerId = p.uuid();
    int throughput = getThroughput(level);
    double xpPerFlow = getConfig().xpPerFlow;
    for (Loc sourceLoc : sources) {
      Tasks.runAt(sourceLoc, () -> flowFromSource(p, sourceLoc, throughput, xpPerFlow, closingPlayerId, selection), 1);
    }
  }

  /**
   * A double chest reports a midpoint location, so flooring it only ever hits
   * one half and links written on the other half never flow. Resolve both
   * halves from the open container instead.
   */
  private List<Loc> resolveFlowSources(PlayerRef player) {
    Optional<BlockRef> holder = player.openContainerBlock();
    if (holder.isPresent()) {
      List<BlockRef> halves = holder.get().containerHalves();
      if (!halves.isEmpty()) {
        List<Loc> sides = new ArrayList<>(halves.size());
        for (BlockRef half : halves) {
          sides.add(half.location());
        }
        return sides;
      }
    }

    Loc loc = player.openMenuLocation();
    return loc == null ? List.of() : List.of(blockLocation(loc));
  }

  private void beginCapture(PlayerRef p, ItemRef hand, Loc clicked) {
    Loc loc = blockLocation(clicked);
    String linkId = UUID.randomUUID().toString();
    ItemRef taglock = makeTaglock(loc, linkId);
    ItemRef costUnit = hand.copyWithAmount(1);
    AtomicBoolean defaultConsumed = new AtomicBoolean();
    if (!payItemCost(p, "capture", costUnit, 1, () -> {
      ItemRef current = p.inventory().mainHand();
      if (!RiftPearls.isPlainPearl(current)) {
        return false;
      }
      decrementItemstack(current, p);
      defaultConsumed.set(true);
      return true;
    })) {
      AdaptLog.verbose("Rift Conduit capture cost was refused for " + p.uuid() + ".");
      return;
    }

    deliverCreatedTaglock(p, taglock, defaultConsumed.get());
    markCaptureFollowUp(p);

    fx(loc.add(0.5, 0.5, 0.5), FxPriority.TRANSITION)
        .ring(Particles.REVERSE_PORTAL, 0.7, 10, 0.0)
        .particle(Particles.END_ROD, 6, 0, 0.6, 0, 0.05, 0.02)
        .chord(Sounds.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.2f, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.5f);
    p.sendMessage(C.LIGHT_PURPLE + AdaptLanguage.text(RiftMessages.CONDUIT_MSG_CAPTURED));
  }

  private void markCaptureFollowUp(PlayerRef p) {
    captureFollowUpUntil.put(p.uuid(), followUpDeadline(System.currentTimeMillis()));
  }

  private boolean isWithinCaptureFollowUp(PlayerRef p) {
    return isWithinFollowUpWindow(System.currentTimeMillis(), captureFollowUpUntil.get(p.uuid()));
  }

  private void completeBind(PlayerRef p, int level, ItemRef taglock, Loc clicked) {
    ConduitLocation stored = decodeLocation(getTaglockLocation(taglock));
    String linkId = getTaglockLinkId(taglock);
    if (stored == null || linkId == null) {
      failBind(p, RiftMessages.CONDUIT_MSG_STALE);
      return;
    }

    WorldRef aWorld = resolveWorld(stored.worldKey());
    if (aWorld == null) {
      failBind(p, RiftMessages.CONDUIT_MSG_STALE);
      return;
    }

    Loc aLoc = new Loc(aWorld, stored.x(), stored.y(), stored.z());
    Loc bLoc = blockLocation(clicked);
    if (sameBlock(aLoc, bLoc)) {
      failBind(p, RiftMessages.CONDUIT_MSG_SAME);
      return;
    }

    if (!isCrossDimension(level)) {
      if (!aWorld.equals(bLoc.world())) {
        failBind(p, RiftMessages.CONDUIT_MSG_RANGE);
        return;
      }
      double range = getRange(level);
      if (aLoc.distanceSquared(bLoc) > range * range) {
        failBind(p, RiftMessages.CONDUIT_MSG_RANGE);
        return;
      }
    }

    if (pendingBinds.containsKey(p.uuid())) {
      AdaptLog.verbose("Rift Conduit ignored a bind for " + p.uuid() + ": one is already in flight.");
      return;
    }
    if (!reserveEndpoints(aLoc, bLoc)) {
      failBind(p, RiftMessages.CONDUIT_MSG_STALE);
      return;
    }
    BindReservation reservation = reserveTaglock(p, linkId);
    if (reservation == null) {
      releaseEndpoints(aLoc, bLoc);
      AdaptLog.verbose("Rift Conduit could not reserve the taglock for " + p.uuid() + ".");
      failBind(p, RiftMessages.CONDUIT_MSG_STALE);
      return;
    }
    BindOperation operation = new BindOperation(
        p,
        p.uuid(),
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
    if (!Tasks.runEntity(p, () -> failBindOperation(
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
      BlockRef aContainer = blockAt(operation.source());
      if (aContainer.container() == null || !canUseContainer(operation.player(), aContainer)) {
        failBindOperation(operation, RiftMessages.CONDUIT_MSG_STALE,
            AbilityRefundReason.TARGET_LOST, true);
        return;
      }

      operation.sourceSnapshot().set(snapshotLink(aContainer));
      if (!writeLink(aContainer, operation.partner(), operation.linkId(), operation.player().uuid())) {
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
      BlockRef bContainer = blockAt(operation.partner());
      if (bContainer.container() == null || !canUseContainer(operation.player(), bContainer)) {
        failBindOperation(operation, RiftMessages.CONDUIT_MSG_STALE,
            AbilityRefundReason.TARGET_LOST, true);
        return;
      }

      operation.partnerSnapshot().set(snapshotLink(bContainer));
      if (!writeLink(bContainer, operation.source(), operation.linkId(), operation.player().uuid())) {
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
      BlockRef source = blockAt(operation.source());
      if (source.container() == null
          || !canUseContainer(operation.player(), source)
          || !linkMatches(source, operation.linkId(), operation.partner())) {
        failBindOperation(operation, RiftMessages.CONDUIT_MSG_STALE,
            AbilityRefundReason.TARGET_LOST, true);
        return;
      }
      if (!Tasks.runEntity(operation.player(), () -> finalizeBindOwned(operation))) {
        failBindOperation(operation, RiftMessages.CONDUIT_MSG_STALE,
            AbilityRefundReason.ACTIVATION_FAILED, true);
      }
    }
  }

  private void finalizeBindOwned(BindOperation operation) {
    PlayerRef p = operation.player();
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
    p.sendMessage(C.LIGHT_PURPLE + AdaptLanguage.text(RiftMessages.CONDUIT_MSG_LINKED));
    Tasks.runAt(operation.partner(), () -> fx(
        operation.partner().add(0.5, 0.5, 0.5),
        FxPriority.TRANSITION
    )
        .ring(Particles.END_ROD, 0.8, 12, 0.1)
        .particle(Particles.REVERSE_PORTAL, 8, 0, 0.6, 0, 0.2, 0.03)
        .chord(Sounds.BLOCK_CONDUIT_ACTIVATE, 0.6f, 1.1f,
            Sounds.BLOCK_BEACON_ACTIVATE, 0.4f, 1.4f));
  }

  private void failBind(PlayerRef p, TextKey messageKey) {
    if (!p.isOnline()) {
      return;
    }
    p.sendMessage(C.RED + AdaptLanguage.text(messageKey));
    fx(p.eyeLocation(), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 3, 0.2)
        .sound(Sounds.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.6f, 0.6f);
  }

  private void deliverCreatedTaglock(PlayerRef p, ItemRef taglock, boolean defaultConsumed) {
    ItemRef hand = p.inventory().mainHand();
    if (defaultConsumed && (hand == null || hand.type().isAir())) {
      p.inventory().setMainHand(taglock);
      return;
    }
    deliverExactTaglock(p, taglock);
  }

  private BindReservation reserveTaglock(PlayerRef p, String linkId) {
    ItemRef hand = p.inventory().mainHand();
    if (!isConduitTaglock(hand) || !linkId.equals(getTaglockLinkId(hand))) {
      return null;
    }
    ItemRef unit = hand.copyWithAmount(1);
    AtomicReference<ItemRef> defaultItem = new AtomicReference<>();
    AbilityCharge charge = payItemCostDeferred(p, "bind", unit, 1, () -> {
      ItemRef current = p.inventory().mainHand();
      if (!isConduitTaglock(current) || !linkId.equals(getTaglockLinkId(current))) {
        return false;
      }
      ItemRef reserved = current.copyWithAmount(1);
      try {
        decrementItemstack(current, p);
        p.data().set(reservationKey, items().serialize(reserved));
        defaultItem.set(reserved);
        return true;
      } catch (RuntimeException error) {
        deliverExactTaglock(p, reserved);
        p.data().remove(reservationKey);
        AdaptLog.error(error);
        return false;
      }
    });
    if (!charge.allowed()) {
      ItemRef consumed = defaultItem.get();
      if (consumed != null) {
        deliverExactTaglock(p, consumed);
        p.data().remove(reservationKey);
      }
      return null;
    }
    return new BindReservation(charge, defaultItem.get(), new AtomicBoolean());
  }

  private boolean settleBindReservation(PlayerRef p, BindReservation reservation) {
    if (!reservation.resolved().compareAndSet(false, true)) {
      return false;
    }
    boolean settled = settleCost(reservation.charge().activationId());
    if (!acceptSettlement(
        reservation.defaultItem() != null,
        reservation.charge().defaultCostSuppressed(),
        settled
    )) {
      AdaptLog.warn("Rift Conduit could not settle the deferred provider cost for " + p.uuid() + ".");
      return false;
    }
    if (reservation.defaultItem() != null) {
      p.data().remove(reservationKey);
    }
    return true;
  }

  private void refundBindReservation(PlayerRef p, BindReservation reservation, AbilityRefundReason reason) {
    ItemRef item = releaseProviderReservation(reservation, reason);
    if (item == null) {
      return;
    }
    try {
      deliverExactTaglock(p, item);
      p.data().remove(reservationKey);
    } catch (Throwable error) {
      AdaptLog.error("Rift Conduit could not return a reserved taglock to " + p.uuid() + ".");
      AdaptLog.error(error);
    }
  }

  private ItemRef releaseProviderReservation(BindReservation reservation, AbilityRefundReason reason) {
    if (reservation == null || !reservation.resolved().compareAndSet(false, true)) {
      return null;
    }
    boolean refunded = refundCost(reservation.charge().activationId(), reason);
    if (reservation.charge().defaultCostSuppressed() && !refunded) {
      AdaptLog.warn("Rift Conduit could not resolve the deferred provider cost for "
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
    if (!Tasks.runEntity(operation.player(), finishFailure)) {
      releaseProviderReservation(operation.reservation(), reason);
    }
  }

  private boolean isCurrentBind(BindOperation operation) {
    return !operation.completed().get()
        && pendingBinds.get(operation.playerId()) == operation
        && isRuntimeRegistered();
  }

  private boolean reserveEndpoints(Loc source, Loc partner) {
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

  private void releaseEndpoints(Loc source, Loc partner) {
    synchronized (bindingEndpoints) {
      bindingEndpoints.remove(endpointKey(source));
      bindingEndpoints.remove(endpointKey(partner));
    }
  }

  private EndpointKey endpointKey(Loc location) {
    return new EndpointKey(
        location.world().uid(),
        location.blockX(),
        location.blockY(),
        location.blockZ()
    );
  }

  private EndpointSnapshot snapshotLink(BlockRef container) {
    DataHolder data = container.blockEntityData();
    return new EndpointSnapshot(
        data == null ? null : data.get(partnerKey),
        data == null ? null : data.get(linkKey),
        data == null ? null : data.get(ownerKey)
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

  private void restoreEndpoint(Loc location, String operationLinkId, EndpointSnapshot snapshot) {
    BlockRef container = blockAt(location);
    DataHolder data = container.container() == null ? null : container.blockEntityData();
    if (data == null) {
      return;
    }
    if (!shouldRestoreEndpoint(data.get(linkKey), operationLinkId)) {
      return;
    }
    restoreValue(data, partnerKey, snapshot.partner());
    restoreValue(data, linkKey, snapshot.linkId());
    restoreValue(data, ownerKey, snapshot.owner());
  }

  private void restoreValue(DataHolder data, DataKey<String> key, String value) {
    if (value == null) {
      data.remove(key);
    } else {
      data.set(key, value);
    }
  }

  private void reportRollbackFailure(Loc location, String linkId) {
    AdaptLog.error("Rift Conduit could not roll back link " + linkId + " at " + location + ".");
  }

  private void loadBindRegion(Loc location, Runnable task, Runnable failure) {
    WorldRef world = location.world();
    Runnable guardedTask = () -> {
      try {
        task.run();
      } catch (Throwable error) {
        AdaptLog.error(error);
        failure.run();
      }
    };
    if (world.isChunkLoaded(location.chunkX(), location.chunkZ())) {
      if (!Tasks.runAt(location, guardedTask)) {
        failure.run();
      }
      return;
    }

    CompletableFuture<Void> load;
    try {
      load = world.loadChunkAsync(location.chunkX(), location.chunkZ());
    } catch (Throwable error) {
      AdaptLog.error(error);
      failure.run();
      return;
    }
    if (load == null) {
      failure.run();
      return;
    }
    load.whenComplete((Void ignored, Throwable error) -> {
      if (error != null) {
        AdaptLog.error(error);
        failure.run();
        return;
      }
      if (!Tasks.runAt(location, guardedTask)) {
        failure.run();
      }
    });
  }

  @Handle
  public void on(PlayerQuitEvent event) {
    UUID playerId = event.player().uuid();
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

  @Handle
  public void on(PlayerJoinEvent event) {
    restoreReservedTaglock(event.player());
  }

  private void restoreReservedTaglock(PlayerRef p) {
    byte[] serialized = p.data().get(reservationKey);
    if (serialized == null || serialized.length == 0) {
      return;
    }
    try {
      deliverExactTaglock(p, items().deserialize(serialized));
      p.data().remove(reservationKey);
    } catch (RuntimeException error) {
      AdaptLog.error("Rift Conduit could not recover a reserved taglock for " + p.uuid() + ".");
      AdaptLog.error(error);
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

  static void deliverExactTaglock(PlayerRef p, ItemRef item) {
    for (ItemRef leftover : p.inventory().addItem(item)) {
      p.world().dropItemNaturally(p.location().position(), leftover);
    }
  }

  private void flowFromSource(PlayerRef p, Loc sourceLoc, int throughput, double xpPerFlow, UUID closingPlayerId, Items selection) {
    BlockRef source = blockAt(sourceLoc);
    InventoryRef sourceInventory = source.container();
    if (sourceInventory == null || !canUseContainer(p, source)) {
      return;
    }

    LinkRef link = readLink(source);
    if (link == null) {
      return;
    }

    DataHolder sourceData = source.blockEntityData();
    String owner = sourceData == null ? null : sourceData.get(ownerKey);
    List<ItemRef> moving = extractItems(sourceInventory, throughput, closingPlayerId.toString().equals(owner) ? selection : Items.ALL);
    if (moving.isEmpty()) {
      return;
    }

    Loc partnerLoc = link.location();
    String linkId = link.linkId();
    AtomicBoolean settled = new AtomicBoolean(false);
    boolean recoveryScheduled = Tasks.runAt(sourceLoc, () -> {
      if (settled.compareAndSet(false, true)) {
        restoreToSource(sourceLoc, moving);
      }
    }, FLOW_DELIVERY_TIMEOUT_TICKS);
    if (!recoveryScheduled) {
      settled.set(true);
      dropAt(sourceLoc, addItems(sourceInventory, moving));
      return;
    }

    try {
      ChunkLoading.loadChunkAsync(partnerLoc, () -> {
        boolean scheduled = Tasks.runAt(partnerLoc, () -> {
          if (settled.compareAndSet(false, true)) {
            depositToPartnerSafely(p, sourceLoc, partnerLoc, linkId, moving, xpPerFlow);
          }
        });
        if (!scheduled && settled.compareAndSet(false, true)) {
          restoreToSource(sourceLoc, moving);
        }
      });
    } catch (Throwable error) {
      AdaptLog.error("Rift Conduit transfer " + linkId + " could not start partner loading at "
          + partnerLoc + "; returning " + totalAmount(moving) + " items to " + sourceLoc + ".");
      AdaptLog.error(error);
      if (settled.compareAndSet(false, true)) {
        dropAt(sourceLoc, addItems(sourceInventory, moving));
      }
    }
  }

  private void depositToPartnerSafely(PlayerRef p, Loc sourceLoc, Loc partnerLoc, String linkId,
                                      List<ItemRef> moving, double xpPerFlow) {
    try {
      depositToPartner(p, sourceLoc, partnerLoc, linkId, moving, xpPerFlow);
    } catch (Throwable error) {
      AdaptLog.error("Rift Conduit transfer " + linkId + " failed while depositing at " + partnerLoc
          + "; returning " + totalAmount(moving) + " items to " + sourceLoc + ".");
      AdaptLog.error(error);
      restoreToSource(sourceLoc, moving);
    }
  }

  private void depositToPartner(PlayerRef p, Loc sourceLoc, Loc partnerLoc, String linkId,
                                List<ItemRef> moving, double xpPerFlow) {
    BlockRef partner = blockAt(partnerLoc);
    InventoryRef partnerInventory = partner.container();
    if (partnerInventory == null
        || !linkMatches(partner, linkId, sourceLoc)
        || !canUseContainer(p, partner)) {
      restoreToSource(sourceLoc, moving);
      return;
    }

    List<ItemRef> leftovers = addItems(partnerInventory, moving);
    int moved = totalAmount(moving) - totalAmount(leftovers);
    if (!leftovers.isEmpty()) {
      restoreToSource(sourceLoc, leftovers);
    }

    if (moved <= 0) {
      return;
    }

    fx(partnerLoc.add(0.5, 0.9, 0.5), FxPriority.AMBIENT)
        .particle(Particles.REVERSE_PORTAL, 6, 0, 0.3, 0, 0.2, 0.03)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.6f);
    int delivered = moved;
    Tasks.runEntity(p, () -> {
      if (!p.isOnline()) {
        return;
      }
      addStat(p, "rift.conduit.items-flowed", delivered);
      xp(p, delivered * xpPerFlow, "rift:conduit:flow");
    });
  }

  private void restoreToSource(Loc sourceLoc, List<ItemRef> items) {
    if (items.isEmpty()) {
      return;
    }
    loadBindRegion(
        sourceLoc,
        () -> {
          InventoryRef container = blockAt(sourceLoc).container();
          if (container != null) {
            dropAt(sourceLoc, addItems(container, items));
          } else {
            dropAt(sourceLoc, items);
          }
        },
        () -> AdaptLog.error("Rift Conduit stranded " + totalAmount(items)
            + " items because their source transfer recovery could not run at " + sourceLoc + ".")
    );
  }

  private void dropAt(Loc loc, List<ItemRef> items) {
    Loc drop = loc.add(0.5, 0.5, 0.5);
    for (ItemRef item : items) {
      if (item != null && !item.type().isAir() && item.amount() > 0) {
        loc.world().dropItem(drop.position(), item);
      }
    }
  }

  private List<ItemRef> extractItems(InventoryRef inventory, int budget, Items selection) {
    List<ItemRef> removed = new ArrayList<>();
    int remaining = Math.max(0, budget);
    int size = inventory.size();
    for (int slot = 0; slot < size && remaining > 0; slot++) {
      ItemRef stack = inventory.item(slot);
      if (stack == null || stack.type().isAir() || stack.amount() <= 0 || !acceptsItem(selection, stack.type())) {
        continue;
      }
      int take = Math.min(remaining, stack.amount());
      removed.add(stack.copyWithAmount(take));
      remaining -= take;
      if (take >= stack.amount()) {
        inventory.clear(slot);
      } else {
        inventory.setItem(slot, stack.copyWithAmount(stack.amount() - take));
      }
    }
    return removed;
  }

  private List<ItemRef> addItems(InventoryRef inventory, List<ItemRef> items) {
    List<ItemRef> leftovers = new ArrayList<>();
    for (ItemRef item : items) {
      if (item == null || item.type().isAir() || item.amount() <= 0) {
        continue;
      }
      for (ItemRef leftover : inventory.addItem(item.copy())) {
        if (leftover != null && !leftover.type().isAir() && leftover.amount() > 0) {
          leftovers.add(leftover);
        }
      }
    }
    return leftovers;
  }

  private int totalAmount(List<ItemRef> items) {
    int sum = 0;
    for (ItemRef item : items) {
      if (item != null && !item.type().isAir()) {
        sum += item.amount();
      }
    }
    return sum;
  }

  private boolean isConduitContainer(BlockRef block) {
    return isStorage(block.state()) && block.container() != null;
  }

  private boolean canAccessContainer(PlayerRef player, BlockRef block) {
    if (block.container() == null) {
      return false;
    }
    List<BlockRef> physicalBlocks = ProtectionProbes.containerBlocks(block, false);
    if (Tasks.isFoliaThreading()) {
      for (BlockRef physicalBlock : physicalBlocks) {
        if (!Tasks.isOwnedByCurrentRegion(physicalBlock.location())) {
          return false;
        }
      }
    }
    List<BlockRef> blocks = ProtectionProbes.containerBlocks(block, true);
    for (BlockRef physicalBlock : blocks) {
      if (!canAccessChest(player, physicalBlock.location())) {
        return false;
      }
    }
    try {
      for (BlockRef physicalBlock : blocks) {
        if (!physicalBlock.equals(block) && !ProtectionProbes.attemptBlockUse(player, physicalBlock)) {
          return false;
        }
      }
      return true;
    } catch (RuntimeException error) {
      AdaptLog.error("Rift Conduit could not verify container access for " + player.uuid() + ".");
      AdaptLog.error(error);
      return false;
    }
  }

  private boolean canUseContainer(PlayerRef player, BlockRef containerBlock) {
    if (!Tasks.isOwnedByCurrentRegion(player) || !player.isOnline()) {
      return false;
    }

    List<BlockRef> blocks = ProtectionProbes.containerBlocks(containerBlock, false);
    for (BlockRef block : blocks) {
      if (!Tasks.isOwnedByCurrentRegion(block.location())
          || !canAccessChest(player, block.location())) {
        return false;
      }
    }

    try {
      return ProtectionProbes.attemptContainerOpen(player, blocks);
    } catch (RuntimeException error) {
      AdaptLog.error("Rift Conduit could not verify container access for " + player.uuid() + ".");
      AdaptLog.error(error);
      return false;
    }
  }

  private LinkRef readLink(BlockRef container) {
    DataHolder data = container.blockEntityData();
    if (data == null) {
      return null;
    }
    String partnerRaw = data.get(partnerKey);
    String linkId = data.get(linkKey);
    if (partnerRaw == null || linkId == null) {
      return null;
    }
    ConduitLocation decoded = decodeLocation(partnerRaw);
    if (decoded == null) {
      return null;
    }
    WorldRef world = resolveWorld(decoded.worldKey());
    if (world == null) {
      return null;
    }
    return new LinkRef(new Loc(world, decoded.x(), decoded.y(), decoded.z()), linkId);
  }

  private boolean writeLink(BlockRef container, Loc partner, String linkId, UUID owner) {
    DataHolder data = container.blockEntityData();
    if (data == null) {
      return false;
    }
    data.set(partnerKey,
        encodeLocation(partner.world().key().toString(), partner.blockX(), partner.blockY(), partner.blockZ()));
    data.set(linkKey, linkId);
    data.set(ownerKey, owner.toString());
    return true;
  }

  private boolean linkMatches(BlockRef partner, String linkId, Loc sourceLoc) {
    DataHolder data = partner.blockEntityData();
    if (data == null) {
      return false;
    }
    String partnerLinkId = data.get(linkKey);
    if (partnerLinkId == null || !partnerLinkId.equals(linkId)) {
      return false;
    }
    ConduitLocation decoded = decodeLocation(data.get(partnerKey));
    return decoded != null
        && decoded.worldKey().equals(sourceLoc.world().key().toString())
        && decoded.x() == sourceLoc.blockX()
        && decoded.y() == sourceLoc.blockY()
        && decoded.z() == sourceLoc.blockZ();
  }

  private ItemRef makeTaglock(Loc loc, String linkId) {
    ItemRef item = items().create(ENDER_PEARL, 1);
    item.setDisplayName(C.LIGHT_PURPLE + AdaptLanguage.text(RiftMessages.CONDUIT_TAGLOCK_NAME));
    List<String> lore = new ArrayList<>();
    lore.add(C.GRAY + AdaptLanguage.text(RiftMessages.CONDUIT_TAGLOCK_LORE1));
    lore.add(C.DARK_GRAY.toString() + loc.blockX() + ", " + loc.blockY() + ", " + loc.blockZ());
    lore.add(C.DARK_GRAY + AdaptLanguage.text(RiftMessages.CONDUIT_TAGLOCK_LORE2));
    item.setLore(lore);
    item.data().set(taglockLocKey,
        encodeLocation(loc.world().key().toString(), loc.blockX(), loc.blockY(), loc.blockZ()));
    item.data().set(taglockIdKey, linkId);
    return item;
  }

  private boolean isConduitTaglock(ItemRef stack) {
    if (stack == null || !stack.type().equals(ENDER_PEARL)) {
      return false;
    }
    DataHolder data = stack.data();
    return data.has(taglockLocKey) && data.has(taglockIdKey);
  }

  private String getTaglockLocation(ItemRef stack) {
    if (!isConduitTaglock(stack)) {
      return null;
    }
    return stack.data().get(taglockLocKey);
  }

  private String getTaglockLinkId(ItemRef stack) {
    if (!isConduitTaglock(stack)) {
      return null;
    }
    return stack.data().get(taglockIdKey);
  }

  private boolean sameBlock(Loc a, Loc b) {
    return a.sameWorld(b)
        && a.blockX() == b.blockX()
        && a.blockY() == b.blockY()
        && a.blockZ() == b.blockZ();
  }

  private static BlockRef blockAt(Loc location) {
    return location.world().blockAt(location);
  }

  private static Loc blockLocation(Loc location) {
    return new Loc(location.world(), location.blockX(), location.blockY(), location.blockZ());
  }

  private static WorldRef resolveWorld(String worldKey) {
    Id id = Id.tryParse(worldKey);
    return id == null ? null : AdaptCore.platform().host().world(id);
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
    return parseWorldKey(worldKey) + "|" + x + "|" + y + "|" + z;
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
      String worldKey = parseWorldKey(raw.substring(0, p1));
      int x = Integer.parseInt(raw.substring(p1 + 1, p2));
      int y = Integer.parseInt(raw.substring(p2 + 1, p3));
      int z = Integer.parseInt(raw.substring(p3 + 1));
      return new ConduitLocation(worldKey, x, y, z);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  /** A fully qualified world key such as {@code minecraft:overworld}; anything else is rejected. */
  private static String parseWorldKey(String serialized) {
    String value = serialized.trim();
    int separator = value.indexOf(':');
    if (separator <= 0 || separator == value.length() - 1) {
      throw new IllegalArgumentException("World identity must be a fully qualified namespaced key: " + value);
    }
    String namespace = value.substring(0, separator);
    String path = value.substring(separator + 1);
    if (!WORLD_NAMESPACE.matcher(namespace).matches() || !WORLD_PATH.matcher(path).matches()) {
      throw new IllegalArgumentException("Invalid world identity: " + value);
    }
    return namespace + ":" + path;
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
      baseCost = 5;
      costFactor = 0.6;
      maxLevel = 4;
      initialCost = 3;
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

  private record LinkRef(Loc location, String linkId) {
  }

  record EndpointSnapshot(String partner, String linkId, String owner) {
  }

  private record EndpointKey(UUID worldId, int x, int y, int z) {
  }

  private record BindReservation(
      AbilityCharge charge,
      ItemRef defaultItem,
      AtomicBoolean resolved
  ) {
  }

  private record BindOperation(
      PlayerRef player,
      UUID playerId,
      Loc source,
      Loc partner,
      String linkId,
      BindReservation reservation,
      AtomicReference<EndpointSnapshot> sourceSnapshot,
      AtomicReference<EndpointSnapshot> partnerSnapshot,
      AtomicBoolean completed
  ) {
  }

  public enum Items { ALL, BLOCKS, FOOD }
}
