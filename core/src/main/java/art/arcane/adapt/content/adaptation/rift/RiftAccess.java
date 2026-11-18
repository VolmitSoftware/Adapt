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

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
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
import art.arcane.adapt.api.preference.PreferenceConfirmation;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.item.BoundEnderPearl;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RiftMessages;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockBurnEvent;
import art.arcane.adapt.platform.event.block.BlockExplodeEvent;
import art.arcane.adapt.platform.event.block.BlockPistonEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.event.inventory.InventoryCloseEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.event.world.ChunkEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class RiftAccess extends SimpleAdaptation<RiftAccess.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> CONFIRM_UNBIND = CommonPreferences.toggle("confirm-unbind", RiftMessages.RIFTACCESS_PREFERENCE_CONFIRM_UNBIND, CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> CONFIRM_BIND = CommonPreferences.toggle("confirm-bind", RiftMessages.RIFTACCESS_PREFERENCE_CONFIRM_BIND, CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK_OPEN = CommonPreferences.toggle("sneak-open", RiftMessages.RIFTACCESS_PREFERENCE_SNEAK_OPEN, CommonPreferences.Toggle.OFF);

  private static final int PENDING_OPEN_TIMEOUT_TICKS = 40;
  private static final AtomicLong TICKET_OWNER_SEQUENCE = new AtomicLong();
  private static final ConcurrentMap<RiftAccessViewRegistry.ChunkKey, Long> TICKET_OWNERS = new ConcurrentHashMap<>();

  private final RiftAccessViewRegistry activeViews = new RiftAccessViewRegistry();
  private final ConcurrentMap<RiftAccessViewRegistry.ChunkKey, Loc> ticketAnchors = new ConcurrentHashMap<>();
  private final AtomicBoolean acceptingViews = new AtomicBoolean(true);
  private final long ticketOwner = TICKET_OWNER_SEQUENCE.incrementAndGet();

  public RiftAccess() {
    super("rift-access");
    registerConfiguration(Config.class);
    setLocalizationKey("rift.remote_access");
    setIcon(Id.minecraft("nether_star"));
    registerRecipe(new RecipeSpec.Shapeless(Id.adapt("rift-remote-access"),
        List.of(Ingredient.of(Id.minecraft("ender_pearl")), Ingredient.of(Id.minecraft("compass"))),
        BoundEnderPearl.io.withData(new BoundEnderPearl.Data(null))));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("chest"))
        .key("challenge_rift_access_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("ender_chest"))
            .key("challenge_rift_access_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_access_100", "rift.access.remote-opens", 100, 300);
    registerMilestone("challenge_rift_access_2500", "rift.access.remote-opens", 2500, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONFIRM_BIND, CONFIRM_UNBIND, SNEAK_OPEN);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    RiftAccessViewRegistry.Session session = activeViews.closePlayer(player.getPlayer().uuid());
    if (session != null) {
      releaseSession(session, true);
    }
  }

  @Override
  public void unregister() {
    if (!acceptingViews.compareAndSet(true, false)) {
      super.unregister();
      return;
    }

    super.unregister();
    List<RiftAccessViewRegistry.Session> sessions = activeViews.drain();
    for (RiftAccessViewRegistry.Session session : sessions) {
      closeSessionView(session);
    }
    Set<RiftAccessViewRegistry.ChunkKey> chunks = new HashSet<>(ticketAnchors.keySet());
    for (RiftAccessViewRegistry.ChunkKey chunkKey : chunks) {
      releaseTicketIfUnused(chunkKey);
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.ITALIC + AdaptLanguage.text(RiftMessages.REMOTE_ACCESS_LORE1));
    v.addLore(C.ITALIC + AdaptLanguage.text(RiftMessages.REMOTE_ACCESS_LORE2));
    v.addLore(C.ITALIC + AdaptLanguage.text(RiftMessages.REMOTE_ACCESS_LORE3));
    v.addLore(C.ITALIC + AdaptLanguage.text(RiftMessages.REMOTE_ACCESS_UNBIND));
  }


  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent event) {
    PlayerRef player = event.player();
    ItemRef mainHand = player.inventory().mainHand();
    ItemRef offHand = player.inventory().offHand();
    BlockRef block = event.clickedBlock();

    boolean mainHandBound = BoundEnderPearl.isBindableItem(mainHand);
    boolean offHandBound = BoundEnderPearl.isBindableItem(offHand);

    // Cancel event if the enderpearl is in the offhand
    if (offHandBound && event.hand() != null && event.hand() == Hand.OFF_HAND) {
      event.setCancelled(true);
      return;
    }

    // If the main hand is holding a bound enderpearl
    if (mainHandBound) {
      boolean blockUseDenied = block != null && event.useBlock() == UseResult.DENY;
      boolean itemUseDenied = event.useItem() == UseResult.DENY;
      event.setCancelled(true);
      if (event.hand() == Hand.MAIN_HAND && hasActiveAdaptation(player) && !itemUseDenied) {
        handleEnderPearlInteraction(event, player, block, blockUseDenied);
      }
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBurnEvent event) {
    closeBlock(event.block());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockPistonEvent event) {
    for (BlockRef block : event.blocks()) {
      closeBlock(block);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockExplodeEvent event) {
    for (BlockRef block : event.blocks()) {
      closeBlock(block);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityExplodeEvent event) {
    for (BlockRef block : event.blocks()) {
      closeBlock(block);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent event) {
    closeBlock(event.block());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(InventoryCloseEvent event) {
    RiftAccessViewRegistry.Session session = activeViews.closeView(
        event.player().uuid(), event.player().openMenuInventory());
    releaseSession(session, false);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    RiftAccessViewRegistry.Session session = activeViews.closePlayer(event.player().uuid());
    releaseSession(session, false);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ChunkEvent event) {
    if (event.phase() != ChunkEvent.Phase.UNLOAD) {
      return;
    }
    RiftAccessViewRegistry.ChunkKey chunkKey = new RiftAccessViewRegistry.ChunkKey(
        event.world().uid(), event.chunk().x(), event.chunk().z());
    releaseSessions(activeViews.closeChunk(chunkKey), true);
    releaseTicketOwned(chunkKey, false);
  }

  private void handleEnderPearlInteraction(PlayerInteractEvent event, PlayerRef player, BlockRef block, boolean blockUseDenied) {
    boolean canUseInCreative = AdaptConfig.get().allowAdaptationsInCreative;
    boolean isCreative = player.gameMode() == GameMode.CREATIVE;
    boolean sneaking = player.isSneaking();
    boolean allowed = canUseInCreative || !isCreative;


    // Check if the player is allowed to use the bound item in creative
    if (!allowed) {
      AdaptLog.verbose(() -> "Blocked Rift Access use in Creative mode for " + player.name() + ".");
      return;
    }

    InteractAction action = event.action();
    if (action == InteractAction.LEFT_CLICK_BLOCK || action == InteractAction.LEFT_CLICK_AIR) {
      if (!sneaking) {
        return;
      }

      if (action == InteractAction.LEFT_CLICK_BLOCK && blockUseDenied) {
        return;
      }
      if (action == InteractAction.LEFT_CLICK_AIR && Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(player.location(), 6D, 6D)) {
        return;
      }

      BlockRef target = action == InteractAction.LEFT_CLICK_BLOCK ? block : player.targetBlock(5);
      if (target == null || !isStorage(target.state())) {
        if (action == InteractAction.LEFT_CLICK_AIR) {
          ItemRef hand = player.inventory().mainHand();
          if ((!preferenceEnabled(player, CONFIRM_UNBIND) || PreferenceConfirmation.confirm(this, player, "unbind", hand)) && BoundEnderPearl.clearBinding(hand)) {
            player.inventory().setMainHand(hand);
          }
        }
        return;
      }
      if (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(target.location())) {
        return;
      }
      if (!isStorage(target.state())) {
        return;
      }

      List<BlockRef> physicalBlocks = ProtectionProbes.containerBlocks(target, false);
      if (Tasks.isFoliaThreading() && !areContainerRegionsOwned(physicalBlocks)) {
        return;
      }
      List<BlockRef> containerBlocks = ProtectionProbes.containerBlocks(target, true);
      if (canOpenContainers(player, containerBlocks, target.location())) {
        linkPearl(player, target, event);
      } else {
        AdaptLog.verbose(() -> "Blocked Rift Access use because " + player.name()
            + " lacks permission.");
      }
    } else if (action == InteractAction.RIGHT_CLICK_AIR || action == InteractAction.RIGHT_CLICK_BLOCK) {
      if (blockUseDenied) {
        return;
      }
      openPearl(player);
    }
  }

  private void linkPearl(PlayerRef player, BlockRef block, PlayerInteractEvent event) {
    event.setCancelled(true);
    ItemRef hand = player.inventory().mainHand();
    if (preferenceEnabled(player, CONFIRM_BIND) && !PreferenceConfirmation.confirm(this, player, "bind:" + block.world().uid() + ":" + block.x() + ":" + block.y() + ":" + block.z(), hand)) {
      return;
    }
    ItemRef costUnit = hand.copyWithAmount(1);
    AtomicBoolean defaultConsumed = new AtomicBoolean();
    if (!payItemCost(player, "bind", costUnit, 1, () -> {
      ItemRef current = player.inventory().mainHand();
      if (!BoundEnderPearl.isBindableItem(current)) {
        return false;
      }
      decrementItemstack(current, player);
      defaultConsumed.set(true);
      return true;
    })) {
      return;
    }

    ItemRef pearl = BoundEnderPearl.withData(block);
    ItemRef current = player.inventory().mainHand();
    if (shouldReplaceBoundPearl(defaultConsumed.get(), current == null || current.type().isAir())) {
      player.inventory().setMainHand(pearl);
    } else {
      for (ItemRef item : player.inventory().addItem(pearl)) {
        player.world().dropItemNaturally(player.location().position(), item);
      }
    }

    Loc center = block.location().add(0.5, 0.5, 0.5);
    timeline(center)
        .duration(10)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          fx.ring(Particles.REVERSE_PORTAL, 0.7, 10, 0.0);
          fx.particle(Particles.END_ROD, 1, 0, 1.0 - progress, 0, 0.02, 0);
          if (tick == 0) {
            fx.sound(Sounds.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 1.6f);
          }
          if (tick == 9) {
            fx.sound(Sounds.BLOCK_ENDER_CHEST_CLOSE, 0.5f, 0.8f);
          }
        })
        .start();
  }

  static boolean shouldReplaceBoundPearl(boolean defaultConsumed, boolean handEmpty) {
    return defaultConsumed && handEmpty;
  }

  private void openPearl(PlayerRef player) {
    if (preferenceEnabled(player, SNEAK_OPEN) && !player.isSneaking()) {
      return;
    }
    BlockRef block = BoundEnderPearl.getBlock(player.inventory().mainHand());
    if (block == null) {
      showOpenFailure(player);
      return;
    }

    Loc target = blockLocation(block.location());
    UUID playerId = player.uuid();
    ChunkLoading.loadChunkAsync(target, () -> {
      if (!acceptingViews.get()) {
        return;
      }
      if (!Tasks.runAt(target, () -> prepareRemoteOpen(player, playerId, target))) {
        scheduleOpenFailure(player);
      }
    });
  }

  private void prepareRemoteOpen(PlayerRef player, UUID playerId, Loc target) {
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
      if (!isStorage(block.state()) || block.container() == null) {
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

    if (!Tasks.runEntity(player, () -> completeRemoteOpen(
        player, session, target, containerBlocks))) {
      cancelSession(session);
    }
  }

  private static AdvancedChestsPort advancedChests() {
    return AdaptPorts.get(AdvancedChestsPort.class, AdvancedChestsPort.NONE);
  }

  private void completeRemoteOpen(PlayerRef player, RiftAccessViewRegistry.Session session, Loc target,
                                  List<BlockRef> expectedContainerBlocks) {
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
      if (!isStorage(currentBlock.state()) || currentBlock.container() == null) {
        cancelSession(session);
        showOpenFailure(player);
        return;
      }
      currentContainerBlocks = ProtectionProbes.containerBlocks(currentBlock, true);
    } else {
      if (!isStorage(currentBlock.state())) {
        cancelSession(session);
        showOpenFailure(player);
        return;
      }
      currentContainerBlocks = physicalBlocks;
    }

    if (!containerTargets(currentContainerBlocks).blockKeys().equals(session.blockKeys())
        || !hasActiveAdaptation(player)
        || !canOpenContainers(player, currentContainerBlocks, target)) {
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

      timeline(player)
          .duration(6)
          .priority(FxPriority.TRANSITION)
          .frame((fx, tick, progress) -> {
            fx.helix(Particles.PORTAL, 0.5, 1.4, 8, progress * Math.PI * 2.0);
            if (tick == 0) {
              fx.chord(Sounds.PARTICLE_SOUL_ESCAPE, 1f, 0.8f, Sounds.BLOCK_ENDER_CHEST_OPEN, 1f, 1.0f);
            }
          })
          .start();
      addStat(player, "rift.access.remote-opens", 1);
    } catch (Throwable error) {
      AdaptLog.error("Failed to open Rift Access container for " + player.name() + " at " + target + ".");
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

  private boolean canAccessContainers(PlayerRef player, List<BlockRef> blocks) {
    for (BlockRef block : blocks) {
      if (!canAccessChest(player, block.location())) {
        return false;
      }
    }
    return true;
  }

  private boolean canOpenContainers(PlayerRef player, List<BlockRef> blocks, Loc target) {
    if (Tasks.isFoliaThreading() && !areContainerRegionsOwned(blocks)) {
      return false;
    }
    if (!canAccessContainers(player, blocks)) {
      return false;
    }
    try {
      return ProtectionProbes.attemptContainerOpen(player, blocks);
    } catch (Throwable error) {
      AdaptLog.error("Failed to check container access for " + player.name() + " at " + target + ".");
      AdaptLog.error(error);
      return false;
    }
  }

  private void showOpenFailure(PlayerRef player) {
    timeline(player)
        .duration(3)
        .priority(FxPriority.TRANSITION)
        .frame((fx, tick, progress) -> {
          if (tick == 0) {
            fx.burst(Particles.SMOKE, 3, 0.2).sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
          }
          if (tick == 2) {
            fx.sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
          }
        })
        .start();
  }

  private void scheduleOpenFailure(PlayerRef player) {
    Tasks.runEntity(player, () -> {
      if (player.isOnline()) {
        showOpenFailure(player);
      }
    });
  }

  private void closeBlock(BlockRef block) {
    releaseSessions(activeViews.closeBlock(blockKey(block.location())), true);
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
      AdaptLog.error("Failed to retain the Rift Access chunk ticket at " + target + ".");
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
      AdaptLog.error("Failed to schedule Rift Access chunk-ticket release at " + anchor + ".");
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
    Long owner = TICKET_OWNERS.get(chunkKey);
    if (owner == null || owner.longValue() != ticketOwner) {
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
      AdaptLog.error("Failed to release the Rift Access chunk ticket at " + anchor + ".");
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


  @ConfigDescription("Craft a Reliquary Portkey to access marked containers remotely.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 3;
      costFactor = 0.2;
      initialCost = 15;
      maxLevel = 1;
    }
  }
}
