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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.content.item.BoundEnderPearl;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import us.lynuxcraft.deadsilenceiv.advancedchests.AdvancedChestsAPI;
import us.lynuxcraft.deadsilenceiv.advancedchests.chest.AdvancedChest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static art.arcane.adapt.api.adaptation.chunk.ChunkLoading.loadChunkAsync;

public class RiftAccess extends SimpleAdaptation<RiftAccess.Config> {
  private static final int PENDING_OPEN_TIMEOUT_TICKS = 40;
  private static final Object ADVANCED_CHEST_LOOKUP_FAILED = new Object();
  private static final AtomicLong TICKET_OWNER_SEQUENCE = new AtomicLong();
  private static final ConcurrentMap<RiftAccessViewRegistry.ChunkKey, Long> TICKET_OWNERS = new ConcurrentHashMap<>();

  private final RiftAccessViewRegistry activeViews = new RiftAccessViewRegistry();
  private final ConcurrentMap<RiftAccessViewRegistry.ChunkKey, Location> ticketAnchors = new ConcurrentHashMap<>();
  private final AtomicBoolean acceptingViews = new AtomicBoolean(true);
  private final long ticketOwner = TICKET_OWNER_SEQUENCE.incrementAndGet();

  public RiftAccess() {
    super("rift-access");
    registerConfiguration(Config.class);
    setLocalizationKey("rift.remote_access");
    setIcon(Material.NETHER_STAR);
    registerRecipe(AdaptRecipe.shapeless()
        .key("rift-remote-access")
        .ingredient(Material.ENDER_PEARL)
        .ingredient(Material.COMPASS)
        .result(BoundEnderPearl.io.withData(new BoundEnderPearl.Data(null)))
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CHEST)
        .key("challenge_rift_access_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENDER_CHEST)
            .key("challenge_rift_access_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_rift_access_100", "rift.access.remote-opens", 100, 300);
    registerMilestone("challenge_rift_access_2500", "rift.access.remote-opens", 2500, 1000);
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
  public void addStats(int level, Element v) {
    v.addLore(C.ITALIC + Localizer.dLocalize("rift.remote_access.lore1"));
    v.addLore(C.ITALIC + Localizer.dLocalize("rift.remote_access.lore2"));
    v.addLore(C.ITALIC + Localizer.dLocalize("rift.remote_access.lore3"));
  }


  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent event) {
    Player player = event.getPlayer();
    ItemStack mainHand = player.getInventory().getItemInMainHand();
    ItemStack offHand = player.getInventory().getItemInOffHand();
    Block block = event.getClickedBlock();

    boolean mainHandBound = BoundEnderPearl.isBindableItem(mainHand);
    boolean offHandBound = BoundEnderPearl.isBindableItem(offHand);

    // Cancel event if the enderpearl is in the offhand
    if (offHandBound && event.getHand() != null && event.getHand().equals(EquipmentSlot.OFF_HAND)) {
      event.setCancelled(true);
      return;
    }

    // If the main hand is holding a bound enderpearl
    if (mainHandBound) {
      event.setCancelled(true);
      if (event.getHand() == EquipmentSlot.HAND && hasActiveAdaptation(player)) {
        Adapt.verbose("Player using bound enderpearl.");
        handleEnderPearlInteraction(event, player, block);
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBurnEvent event) {
    closeBlock(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPistonRetractEvent event) {
    for (Block block : event.getBlocks()) {
      closeBlock(block);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPistonExtendEvent event) {
    for (Block block : event.getBlocks()) {
      closeBlock(block);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockExplodeEvent event) {
    for (Block block : event.blockList()) {
      closeBlock(block);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityExplodeEvent event) {
    for (Block block : event.blockList()) {
      closeBlock(block);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent event) {
    closeBlock(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(InventoryCloseEvent event) {
    RiftAccessViewRegistry.Session session = activeViews.closeView(
        event.getPlayer().getUniqueId(), event.getView().getTopInventory());
    releaseSession(session, false);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    RiftAccessViewRegistry.Session session = activeViews.closePlayer(event.getPlayer().getUniqueId());
    releaseSession(session, false);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ChunkUnloadEvent event) {
    RiftAccessViewRegistry.ChunkKey chunkKey = new RiftAccessViewRegistry.ChunkKey(
        event.getWorld().getUID(), event.getChunk().getX(), event.getChunk().getZ());
    releaseSessions(activeViews.closeChunk(chunkKey), true);
    releaseTicketOwned(chunkKey, false);
  }

  private void handleEnderPearlInteraction(PlayerInteractEvent event, Player player, Block block) {
    boolean canUseInCreative = AdaptConfig.get().allowAdaptationsInCreative;
    boolean isCreative = player.getGameMode() == GameMode.CREATIVE;
    boolean sneaking = player.isSneaking();
    boolean allowed = canUseInCreative || !isCreative;


    // Check if the player is allowed to use the bound item in creative
    if (!allowed) {
      Adapt.info("Player " + player.getName() + " tried to use the bound item in creative mode.");
      return;
    }

    Action action = event.getAction();
    if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
      if (!sneaking) {
        return;
      }

      Block target = action == Action.LEFT_CLICK_BLOCK ? block : player.getTargetBlockExact(5);
      if (target == null || !isStorage(target.getBlockData())) {
        return;
      }

      if (canAccessChest(player, target.getLocation())) {
        linkPearl(player, target, event);
      } else {
        Adapt.verbose("Player " + player.getName() + " doesn't have permission.");
      }
    } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
      openPearl(player);
    }
  }

  private void linkPearl(Player player, Block block, PlayerInteractEvent event) {
    event.setCancelled(true);
    Location center = block.getLocation().add(0.5, 0.5, 0.5);
    timeline(center)
        .duration(10)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          fx.ring(Particle.REVERSE_PORTAL, 0.7, 10, 0.0);
          fx.particle(Particles.END_ROD, 1, 0, 1.0 - progress, 0, 0.02, 0);
          if (tick == 0) {
            fx.sound(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 1.6f);
          }
          if (tick == 9) {
            fx.sound(Sound.BLOCK_ENDER_CHEST_CLOSE, 0.5f, 0.8f);
          }
        })
        .start();
    ItemStack hand = player.getInventory().getItemInMainHand();

    if (hand.getAmount() == 1) {
      BoundEnderPearl.setData(hand, block);
    } else {
      hand.setAmount(hand.getAmount() - 1);
      ItemStack pearl = BoundEnderPearl.withData(block);
      player.getInventory().addItem(pearl).values()
          .forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }
  }

  private void openPearl(Player player) {
    Block block = BoundEnderPearl.getBlock(player.getInventory().getItemInMainHand());
    if (block == null) {
      showOpenFailure(player);
      return;
    }

    Location target = blockLocation(block.getLocation());
    if (!canAccessChest(player, target)) {
      showOpenFailure(player);
      return;
    }

    UUID playerId = player.getUniqueId();
    loadChunkAsync(target, chunk -> {
      if (!acceptingViews.get()) {
        return;
      }
      if (!J.runAt(target, () -> prepareRemoteOpen(player, playerId, target))) {
        scheduleOpenFailure(player);
      }
    });
  }

  private void prepareRemoteOpen(Player player, UUID playerId, Location target) {
    if (!acceptingViews.get()) {
      return;
    }

    Block block = target.getBlock();
    Object advancedChest = findAdvancedChest(target);
    if (advancedChest == ADVANCED_CHEST_LOOKUP_FAILED) {
      scheduleOpenFailure(player);
      return;
    }
    Inventory inventory = null;
    if (advancedChest == null) {
      if (!isStorage(block.getBlockData()) || !(block.getState() instanceof InventoryHolder holder)) {
        scheduleOpenFailure(player);
        return;
      }
      inventory = holder.getInventory();
    }

    RiftAccessViewRegistry.BlockKey blockKey = blockKey(target);
    RiftAccessViewRegistry.ChunkKey chunkKey = chunkKey(target);
    RiftAccessViewRegistry.Registration registration = activeViews.register(playerId, blockKey, chunkKey);
    if (registration == null) {
      return;
    }
    RiftAccessViewRegistry.Session retired = registration.retired();
    if (retired != null) {
      closeSessionView(retired);
      releaseTicketIfUnused(retired.chunkKey());
    }

    RiftAccessViewRegistry.Session session = registration.session();
    if (!ensureTicket(target, chunkKey)) {
      cancelSession(session);
      scheduleOpenFailure(player);
      return;
    }
    if (!acceptingViews.get() || !activeViews.isCurrent(session)) {
      cancelSession(session);
      releaseTicketIfUnused(chunkKey);
      return;
    }

    if (!J.runAt(target, () -> expirePendingSession(session), PENDING_OPEN_TIMEOUT_TICKS)) {
      cancelSession(session);
      scheduleOpenFailure(player);
      return;
    }

    Inventory targetInventory = inventory;
    Object targetAdvancedChest = advancedChest;
    if (!J.runEntity(player, () -> completeRemoteOpen(player, session, target, targetInventory, targetAdvancedChest))) {
      cancelSession(session);
    }
  }

  private Object findAdvancedChest(Location target) {
    if (!Bukkit.getPluginManager().isPluginEnabled("AdvancedChests")) {
      return null;
    }
    try {
      return AdvancedChestsAPI.getChestManager().getAdvancedChest(target);
    } catch (Throwable error) {
      Adapt.error("Failed to resolve AdvancedChests container for Rift Access at " + target + ".");
      error.printStackTrace();
      return ADVANCED_CHEST_LOOKUP_FAILED;
    }
  }

  private void completeRemoteOpen(Player player, RiftAccessViewRegistry.Session session, Location target,
                                  Inventory inventory, Object advancedChest) {
    if (!acceptingViews.get() || !activeViews.isCurrent(session)) {
      cancelSession(session);
      return;
    }
    if (!player.isOnline() || !hasActiveAdaptation(player) || !canAccessChest(player, target)) {
      cancelSession(session);
      if (player.isOnline()) {
        showOpenFailure(player);
      }
      return;
    }

    if (J.isFoliaThreading() && !FoliaScheduler.isOwnedByCurrentRegion(target)) {
      cancelSession(session);
      showOpenFailure(player);
      return;
    }

    InventoryView view = null;
    try {
      if (advancedChest != null) {
        ((AdvancedChest<?, ?>) advancedChest).openPage(player, 1);
        view = player.getOpenInventory();
        Adapt.verbose("Opening AdvancedChests GUI");
      } else {
        view = player.openInventory(inventory);
      }

      if (view == null || !activeViews.activate(session, view.getTopInventory())) {
        closeViewIfCurrent(player, view);
        cancelSession(session);
        return;
      }

      timeline(player)
          .duration(6)
          .priority(FxPriority.TRANSITION)
          .frame((fx, tick, progress) -> {
            fx.helix(Particle.PORTAL, 0.5, 1.4, 8, progress * Math.PI * 2.0);
            if (tick == 0) {
              fx.chord(Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.8f, Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1.0f);
            }
          })
          .start();
      addStat(player, "rift.access.remote-opens", 1);
    } catch (Throwable error) {
      Adapt.error("Failed to open Rift Access container for " + player.getName() + " at " + target + ".");
      error.printStackTrace();
      closeViewIfCurrent(player, view);
      cancelSession(session);
      showOpenFailure(player);
    }
  }

  private void closeViewIfCurrent(Player player, InventoryView view) {
    if (view != null && player.getOpenInventory().getTopInventory() == view.getTopInventory()) {
      player.closeInventory();
    }
  }

  private void showOpenFailure(Player player) {
    timeline(player)
        .duration(3)
        .priority(FxPriority.TRANSITION)
        .frame((fx, tick, progress) -> {
          if (tick == 0) {
            fx.burst(Particles.SMOKE, 3, 0.2).sound(Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
          }
          if (tick == 2) {
            fx.sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
          }
        })
        .start();
  }

  private void scheduleOpenFailure(Player player) {
    J.runEntity(player, () -> {
      if (player.isOnline()) {
        showOpenFailure(player);
      }
    });
  }

  private void closeBlock(Block block) {
    releaseSessions(activeViews.closeBlock(blockKey(block.getLocation())), true);
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
      chunks.add(session.chunkKey());
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
    releaseTicketIfUnused(session.chunkKey());
  }

  private void cancelSession(RiftAccessViewRegistry.Session session) {
    releaseSession(activeViews.cancel(session), false);
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
    Player player = Bukkit.getPlayer(session.playerId());
    if (player == null) {
      return;
    }
    J.runEntity(player, () -> {
      InventoryView current = player.getOpenInventory();
      if (!activeViews.tracksView(session.playerId(), viewIdentity)
          && current.getTopInventory() == viewIdentity) {
        player.closeInventory();
      }
    });
  }

  private boolean ensureTicket(Location target, RiftAccessViewRegistry.ChunkKey chunkKey) {
    Location anchor = blockLocation(target);
    if (ticketAnchors.putIfAbsent(chunkKey, anchor) != null) {
      return true;
    }

    Long previousOwner = TICKET_OWNERS.put(chunkKey, ticketOwner);
    try {
      target.getWorld().addPluginChunkTicket(chunkKey.x(), chunkKey.z(), Adapt.instance);
      return true;
    } catch (Throwable error) {
      ticketAnchors.remove(chunkKey, anchor);
      if (TICKET_OWNERS.remove(chunkKey, ticketOwner) && previousOwner != null) {
        TICKET_OWNERS.putIfAbsent(chunkKey, previousOwner);
      }
      Adapt.error("Failed to retain the Rift Access chunk ticket at " + target + ".");
      error.printStackTrace();
      return false;
    }
  }

  private void releaseTicketIfUnused(RiftAccessViewRegistry.ChunkKey chunkKey) {
    if (activeViews.requiresTicket(chunkKey)) {
      return;
    }
    Location anchor = ticketAnchors.get(chunkKey);
    if (anchor == null) {
      return;
    }
    if (!J.runAt(anchor, () -> releaseTicketOwned(chunkKey, true))) {
      Adapt.error("Failed to schedule Rift Access chunk-ticket release at " + anchor + ".");
    }
  }

  private void releaseTicketOwned(RiftAccessViewRegistry.ChunkKey chunkKey, boolean requestUnload) {
    if (activeViews.requiresTicket(chunkKey)) {
      return;
    }

    Location anchor = ticketAnchors.get(chunkKey);
    if (anchor == null) {
      return;
    }
    Long owner = TICKET_OWNERS.get(chunkKey);
    if (owner == null || owner.longValue() != ticketOwner) {
      ticketAnchors.remove(chunkKey, anchor);
      return;
    }

    try {
      World world = anchor.getWorld();
      world.removePluginChunkTicket(chunkKey.x(), chunkKey.z(), Adapt.instance);
      if (requestUnload) {
        world.unloadChunkRequest(chunkKey.x(), chunkKey.z());
      }
      ticketAnchors.remove(chunkKey, anchor);
      TICKET_OWNERS.remove(chunkKey, ticketOwner);
    } catch (Throwable error) {
      Adapt.error("Failed to release the Rift Access chunk ticket at " + anchor + ".");
      error.printStackTrace();
    }
  }

  private static Location blockLocation(Location location) {
    return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
  }

  private static RiftAccessViewRegistry.BlockKey blockKey(Location location) {
    return new RiftAccessViewRegistry.BlockKey(location.getWorld().getUID(),
        location.getBlockX(), location.getBlockY(), location.getBlockZ());
  }

  private static RiftAccessViewRegistry.ChunkKey chunkKey(Location location) {
    return new RiftAccessViewRegistry.ChunkKey(location.getWorld().getUID(),
        location.getBlockX() >> 4, location.getBlockZ() >> 4);
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
