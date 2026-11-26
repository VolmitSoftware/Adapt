package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.chunk.ChunkLoading;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Opens a container far away for a player: loads and holds its chunk while the view is open, checks the container is
 * still there and that the player may open it, and closes the view when the container breaks, burns, moves or
 * unloads. Rift Access opens its bound pearl's container through it, Pocket Realm the containers beside its anchors.
 */
public final class RiftRemoteContainers {
  private static final int PENDING_OPEN_TIMEOUT_TICKS = 40;
  private static final AtomicLong TICKET_OWNER_SEQUENCE = new AtomicLong();
  private static final ConcurrentMap<RiftAccessViewRegistry.ChunkKey, Long> TICKET_OWNERS = new ConcurrentHashMap<>();

  private final Adaptation<?> owner;
  private final Consumer<PlayerRef> opened;
  private final Consumer<PlayerRef> failed;
  private final RiftAccessViewRegistry activeViews = new RiftAccessViewRegistry();
  private final ConcurrentMap<RiftAccessViewRegistry.ChunkKey, Loc> ticketAnchors = new ConcurrentHashMap<>();
  private final AtomicBoolean acceptingViews = new AtomicBoolean(true);
  private final long ticketOwner = TICKET_OWNER_SEQUENCE.incrementAndGet();

  /** {@code opened} and {@code failed} run on the player's thread after a view opens or would not open. */
  public RiftRemoteContainers(Adaptation<?> owner, Consumer<PlayerRef> opened, Consumer<PlayerRef> failed) {
    this.owner = Objects.requireNonNull(owner);
    this.opened = Objects.requireNonNull(opened);
    this.failed = Objects.requireNonNull(failed);
  }

  /** Opens the container at {@code target} for {@code player} while {@code allowed} still holds for them. */
  public void open(PlayerRef player, Loc target, Predicate<PlayerRef> allowed) {
    Loc block = blockLocation(target);
    UUID playerId = player.uuid();
    ChunkLoading.loadChunkAsync(block, () -> {
      if (!acceptingViews.get()) {
        return;
      }
      if (!Tasks.runAt(block, () -> prepareRemoteOpen(player, playerId, block, allowed))) {
        scheduleOpenFailure(player);
      }
    });
  }

  /** Whether the player may open every part of a container ({@code blocks}, as {@link ProtectionProbes#containerBlocks}). */
  public boolean canOpen(PlayerRef player, List<BlockRef> blocks, Loc target) {
    if (Tasks.isFoliaThreading() && !areContainerRegionsOwned(blocks)) {
      return false;
    }
    for (BlockRef block : blocks) {
      if (!owner.canAccessChest(player, block.location())) {
        return false;
      }
    }
    try {
      return ProtectionProbes.attemptContainerOpen(player, blocks);
    } catch (Throwable error) {
      AdaptLog.error("Failed to check container access for " + player.name() + " at " + target + ".");
      AdaptLog.error(error);
      return false;
    }
  }

  public void blockChanged(BlockRef block) {
    releaseSessions(activeViews.closeBlock(blockKey(block.location())), true);
  }

  public void viewClosed(PlayerRef player) {
    releaseSession(activeViews.closeView(player.uuid(), player.openMenuInventory()), false);
  }

  public void playerLeft(UUID player) {
    releaseSession(activeViews.closePlayer(player), false);
  }

  public void closeFor(UUID player) {
    releaseSession(activeViews.closePlayer(player), true);
  }

  public void chunkUnloaded(WorldRef world, int chunkX, int chunkZ) {
    RiftAccessViewRegistry.ChunkKey chunkKey = new RiftAccessViewRegistry.ChunkKey(world.uid(), chunkX, chunkZ);
    releaseSessions(activeViews.closeChunk(chunkKey), true);
    releaseTicketOwned(chunkKey, false);
  }

  /** Closes every view and releases every chunk; nothing opens afterwards. */
  public void shutdown() {
    if (!acceptingViews.compareAndSet(true, false)) {
      return;
    }
    for (RiftAccessViewRegistry.Session session : activeViews.drain()) {
      closeSessionView(session);
    }
    for (RiftAccessViewRegistry.ChunkKey chunkKey : new HashSet<>(ticketAnchors.keySet())) {
      releaseTicketIfUnused(chunkKey);
    }
  }

  private void prepareRemoteOpen(PlayerRef player, UUID playerId, Loc target, Predicate<PlayerRef> allowed) {
    if (!acceptingViews.get()) {
      return;
    }

    BlockRef block = target.world().blockAt(target);
    List<BlockRef> physicalBlocks = ProtectionProbes.containerBlocks(block, false);
    if (Tasks.isFoliaThreading() && !areContainerRegionsOwned(physicalBlocks)) {
      scheduleOpenFailure(player);
      return;
    }
    AdvancedChestsPort.Lookup advancedChest = advancedChests().find(target);
    if (advancedChest == AdvancedChestsPort.Lookup.FAILED) {
      scheduleOpenFailure(player);
      return;
    }
    boolean hasInventory = false;
    if (advancedChest == AdvancedChestsPort.Lookup.ABSENT) {
      if (!owner.isStorage(block.state()) || block.container() == null) {
        scheduleOpenFailure(player);
        return;
      }
      hasInventory = true;
    }
    List<BlockRef> containerBlocks = ProtectionProbes.containerBlocks(block, hasInventory);
    ContainerTargets containerTargets = containerTargets(containerBlocks);

    RiftAccessViewRegistry.Registration registration = activeViews.register(
        playerId,
        containerTargets.blockKeys(),
        containerTargets.chunkAnchors().keySet()
    );
    if (registration == null) {
      return;
    }
    RiftAccessViewRegistry.Session retired = registration.retired();
    if (retired != null) {
      closeSessionView(retired);
      releaseTicketsIfUnused(retired.chunkKeys());
    }

    RiftAccessViewRegistry.Session session = registration.session();
    if (!ensureTickets(containerTargets.chunkAnchors())) {
      cancelSession(session);
      scheduleOpenFailure(player);
      return;
    }
    if (!acceptingViews.get() || !activeViews.isCurrent(session)) {
      cancelSession(session);
      return;
    }

    if (!Tasks.runAt(target, () -> expirePendingSession(session), PENDING_OPEN_TIMEOUT_TICKS)) {
      cancelSession(session);
      scheduleOpenFailure(player);
      return;
    }

    if (!Tasks.runEntity(player, () -> completeRemoteOpen(player, session, target, containerBlocks, allowed))) {
      cancelSession(session);
    }
  }

  private static AdvancedChestsPort advancedChests() {
    return AdaptPorts.get(AdvancedChestsPort.class, AdvancedChestsPort.NONE);
  }

  private void completeRemoteOpen(PlayerRef player, RiftAccessViewRegistry.Session session, Loc target,
                                  List<BlockRef> expectedContainerBlocks, Predicate<PlayerRef> allowed) {
    if (!acceptingViews.get() || !activeViews.isCurrent(session)) {
      cancelSession(session);
      return;
    }
    if (!player.isOnline()) {
      cancelSession(session);
      return;
    }
    if (Tasks.isFoliaThreading() && !areContainerRegionsOwned(expectedContainerBlocks)) {
      cancelSession(session);
      showOpenFailure(player);
      return;
    }

    BlockRef currentBlock = target.world().blockAt(target);
    List<BlockRef> physicalBlocks = ProtectionProbes.containerBlocks(currentBlock, false);
    if (Tasks.isFoliaThreading() && !areContainerRegionsOwned(physicalBlocks)) {
      cancelSession(session);
      showOpenFailure(player);
      return;
    }
    List<BlockRef> currentContainerBlocks;
    AdvancedChestsPort.Lookup currentAdvancedChest = advancedChests().find(target);
    if (currentAdvancedChest == AdvancedChestsPort.Lookup.FAILED) {
      cancelSession(session);
      showOpenFailure(player);
      return;
    }
    if (currentAdvancedChest == AdvancedChestsPort.Lookup.ABSENT) {
      if (!owner.isStorage(currentBlock.state()) || currentBlock.container() == null) {
        cancelSession(session);
        showOpenFailure(player);
        return;
      }
      currentContainerBlocks = ProtectionProbes.containerBlocks(currentBlock, true);
    } else {
      if (!owner.isStorage(currentBlock.state())) {
        cancelSession(session);
        showOpenFailure(player);
        return;
      }
      currentContainerBlocks = physicalBlocks;
    }

    if (!containerTargets(currentContainerBlocks).blockKeys().equals(session.blockKeys())
        || !allowed.test(player)
        || !canOpen(player, currentContainerBlocks, target)) {
      cancelSession(session);
      showOpenFailure(player);
      return;
    }

    InventoryRef view = null;
    try {
      if (currentAdvancedChest == AdvancedChestsPort.Lookup.PRESENT) {
        InventoryRef openedView = advancedChests().open(player, target);
        if (openedView == null) {
          cancelSession(session);
          return;
        }
        view = openedView;
      } else {
        view = player.openBlockInventory(currentBlock);
      }

      if (view == null || !activeViews.activate(session, view)) {
        closeViewIfCurrent(player, view);
        cancelSession(session);
        return;
      }

      opened.accept(player);
    } catch (Throwable error) {
      AdaptLog.error("Failed to open a remote container for " + player.name() + " at " + target + ".");
      AdaptLog.error(error);
      closeViewIfCurrent(player, view);
      cancelSession(session);
      showOpenFailure(player);
    }
  }

  private void closeViewIfCurrent(PlayerRef player, InventoryRef view) {
    if (view != null && view.equals(player.openMenuInventory())) {
      player.closeInventory();
    }
  }

  private void showOpenFailure(PlayerRef player) {
    failed.accept(player);
  }

  private void scheduleOpenFailure(PlayerRef player) {
    Tasks.runEntity(player, () -> {
      if (player.isOnline()) {
        showOpenFailure(player);
      }
    });
  }

  private void releaseSessions(List<RiftAccessViewRegistry.Session> sessions, boolean closeViews) {
    if (sessions.isEmpty()) {
      return;
    }

    Set<RiftAccessViewRegistry.ChunkKey> chunks = new HashSet<>();
    for (RiftAccessViewRegistry.Session session : sessions) {
      if (closeViews) {
        closeSessionView(session);
      }
      chunks.addAll(session.chunkKeys());
    }
    for (RiftAccessViewRegistry.ChunkKey chunkKey : chunks) {
      releaseTicketIfUnused(chunkKey);
    }
  }

  private void releaseSession(RiftAccessViewRegistry.Session session, boolean closeView) {
    if (session == null) {
      return;
    }
    if (closeView) {
      closeSessionView(session);
    }
    releaseTicketsIfUnused(session.chunkKeys());
  }

  private void cancelSession(RiftAccessViewRegistry.Session session) {
    RiftAccessViewRegistry.Session cancelled = activeViews.cancel(session);
    if (cancelled != null) {
      releaseSession(cancelled, false);
      return;
    }
    releaseTicketsIfUnused(session.chunkKeys());
  }

  private void expirePendingSession(RiftAccessViewRegistry.Session session) {
    if (activeViews.isPending(session)) {
      cancelSession(session);
    }
  }

  private void closeSessionView(RiftAccessViewRegistry.Session session) {
    Object viewIdentity = session.viewIdentity();
    if (viewIdentity == null) {
      return;
    }
    PlayerRef player = AdaptCore.platform().host().player(session.playerId());
    if (player == null) {
      return;
    }
    Tasks.runEntity(player, () -> {
      InventoryRef current = player.openMenuInventory();
      if (!activeViews.tracksView(session.playerId(), viewIdentity)
          && viewIdentity.equals(current)) {
        player.closeInventory();
      }
    });
  }

  private boolean ensureTickets(Map<RiftAccessViewRegistry.ChunkKey, Loc> chunkAnchors) {
    for (Map.Entry<RiftAccessViewRegistry.ChunkKey, Loc> entry : chunkAnchors.entrySet()) {
      if (!ensureTicket(entry.getValue(), entry.getKey())) {
        return false;
      }
    }
    return true;
  }

  private boolean ensureTicket(Loc target, RiftAccessViewRegistry.ChunkKey chunkKey) {
    Loc anchor = blockLocation(target);
    if (ticketAnchors.putIfAbsent(chunkKey, anchor) != null) {
      return true;
    }

    Long previousOwner = TICKET_OWNERS.put(chunkKey, ticketOwner);
    try {
      target.world().addChunkTicket(chunkKey.x(), chunkKey.z());
      return true;
    } catch (Throwable error) {
      ticketAnchors.remove(chunkKey, anchor);
      if (TICKET_OWNERS.remove(chunkKey, ticketOwner) && previousOwner != null) {
        TICKET_OWNERS.putIfAbsent(chunkKey, previousOwner);
      }
      AdaptLog.error("Failed to retain the remote container chunk ticket at " + target + ".");
      AdaptLog.error(error);
      return false;
    }
  }

  private void releaseTicketIfUnused(RiftAccessViewRegistry.ChunkKey chunkKey) {
    if (activeViews.requiresTicket(chunkKey)) {
      return;
    }
    Loc anchor = ticketAnchors.get(chunkKey);
    if (anchor == null) {
      return;
    }
    if (!Tasks.runAt(anchor, () -> releaseTicketOwned(chunkKey, true))) {
      AdaptLog.error("Failed to schedule remote container chunk-ticket release at " + anchor + ".");
    }
  }

  private void releaseTicketsIfUnused(Set<RiftAccessViewRegistry.ChunkKey> chunkKeys) {
    for (RiftAccessViewRegistry.ChunkKey chunkKey : chunkKeys) {
      releaseTicketIfUnused(chunkKey);
    }
  }

  private void releaseTicketOwned(RiftAccessViewRegistry.ChunkKey chunkKey, boolean requestUnload) {
    if (activeViews.requiresTicket(chunkKey)) {
      return;
    }

    Loc anchor = ticketAnchors.get(chunkKey);
    if (anchor == null) {
      return;
    }
    Long ticketHolder = TICKET_OWNERS.get(chunkKey);
    if (ticketHolder == null || ticketHolder.longValue() != ticketOwner) {
      ticketAnchors.remove(chunkKey, anchor);
      return;
    }

    try {
      anchor.world().removeChunkTicket(chunkKey.x(), chunkKey.z());
      if (requestUnload) {
        anchor.world().requestChunkUnload(chunkKey.x(), chunkKey.z());
      }
      ticketAnchors.remove(chunkKey, anchor);
      TICKET_OWNERS.remove(chunkKey, ticketOwner);
    } catch (Throwable error) {
      AdaptLog.error("Failed to release the remote container chunk ticket at " + anchor + ".");
      AdaptLog.error(error);
    }
  }

  private static Loc blockLocation(Loc location) {
    return new Loc(location.world(), location.blockX(), location.blockY(), location.blockZ());
  }

  private static RiftAccessViewRegistry.BlockKey blockKey(Loc location) {
    return new RiftAccessViewRegistry.BlockKey(location.world().uid(),
        location.blockX(), location.blockY(), location.blockZ());
  }

  private static RiftAccessViewRegistry.ChunkKey chunkKey(Loc location) {
    return new RiftAccessViewRegistry.ChunkKey(location.world().uid(),
        location.blockX() >> 4, location.blockZ() >> 4);
  }

  static boolean areContainerRegionsOwned(List<BlockRef> blocks) {
    for (BlockRef block : blocks) {
      if (!Tasks.isOwnedByCurrentRegion(block.location())) {
        return false;
      }
    }
    return true;
  }

  static ContainerTargets containerTargets(List<BlockRef> blocks) {
    Set<RiftAccessViewRegistry.BlockKey> blockKeys = new LinkedHashSet<>();
    Map<RiftAccessViewRegistry.ChunkKey, Loc> chunkAnchors = new LinkedHashMap<>();
    for (BlockRef block : blocks) {
      Loc location = blockLocation(block.location());
      blockKeys.add(blockKey(location));
      chunkAnchors.putIfAbsent(chunkKey(location), location);
    }
    return new ContainerTargets(Set.copyOf(blockKeys), Map.copyOf(chunkAnchors));
  }

  record ContainerTargets(
      Set<RiftAccessViewRegistry.BlockKey> blockKeys,
      Map<RiftAccessViewRegistry.ChunkKey, Loc> chunkAnchors
  ) {
  }
}
