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

import art.arcane.adapt.localization.catalog.RiftMessages;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.world.AdaptPlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.event.player.PlayerMoveEvent;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

public class RiftVoidMagnet extends SimpleAdaptation<RiftVoidMagnet.Config> {
  public static final PlayerPreference<Control> CONTROL = new PlayerPreference<>(Control.class,
      new PlayerPreference.Definition<>("control", RiftMessages.RIFTVOIDMAGNET_PREFERENCE_CONTROL, Control.SNEAK, List.of(
          new PlayerPreference.Choice<>(Control.SNEAK, RiftMessages.RIFTVOIDMAGNET_PREFERENCE_CONTROL_SNEAK, Material.LEATHER_BOOTS, 1),
          new PlayerPreference.Choice<>(Control.ARMED, RiftMessages.RIFTVOIDMAGNET_PREFERENCE_CONTROL_ARMED, Material.HOPPER, 1))));
  public static final PlayerPreference<Destination> DESTINATION = new PlayerPreference<>(Destination.class,
      new PlayerPreference.Definition<>("destination", RiftMessages.RIFTVOIDMAGNET_PREFERENCE_DESTINATION, Destination.CHEST_FIRST, List.of(
          new PlayerPreference.Choice<>(Destination.CHEST_FIRST, RiftMessages.RIFTVOIDMAGNET_PREFERENCE_DESTINATION_CHEST_FIRST, Material.ENDER_CHEST, 1),
          new PlayerPreference.Choice<>(Destination.INVENTORY_FIRST, RiftMessages.RIFTVOIDMAGNET_PREFERENCE_DESTINATION_INVENTORY_FIRST, Material.CHEST, 1),
          new PlayerPreference.Choice<>(Destination.CHEST_ONLY, RiftMessages.RIFTVOIDMAGNET_PREFERENCE_DESTINATION_CHEST_ONLY, Material.ENDER_PEARL, 1),
          new PlayerPreference.Choice<>(Destination.INVENTORY_ONLY, RiftMessages.RIFTVOIDMAGNET_PREFERENCE_DESTINATION_INVENTORY_ONLY, Material.BUNDLE, 1))));
  public static final PlayerPreference<Items> ITEMS = new PlayerPreference<>(Items.class,
      new PlayerPreference.Definition<>("items", RiftMessages.RIFTVOIDMAGNET_PREFERENCE_ITEMS, Items.ALL, List.of(
          new PlayerPreference.Choice<>(Items.ALL, RiftMessages.RIFTVOIDMAGNET_PREFERENCE_ITEMS_ALL, Material.HOPPER, 1),
          new PlayerPreference.Choice<>(Items.BLOCKS, RiftMessages.RIFTVOIDMAGNET_PREFERENCE_ITEMS_BLOCKS, Material.STONE, 1),
          new PlayerPreference.Choice<>(Items.FOOD, RiftMessages.RIFTVOIDMAGNET_PREFERENCE_ITEMS_FOOD, Material.BREAD, 1))));

  static final long WORK_WINDOW_MILLIS = 50L;
  static final int HARD_MAX_ACTIVE_SESSIONS = 1024;
  static final int HARD_MAX_SESSION_VISITS_PER_WINDOW = 256;
  static final int HARD_MAX_SCAN_OWNER_HANDOFFS_PER_WINDOW = 64;
  static final int HARD_MAX_SCAN_EXECUTIONS_PER_WINDOW = 64;
  static final int HARD_MAX_CANDIDATE_INSPECTIONS_PER_WINDOW = 1024;
  static final int HARD_MAX_ITEM_HANDOFFS_PER_WINDOW = 256;
  static final int HARD_MAX_ITEM_EXECUTIONS_PER_WINDOW = 256;
  static final int HARD_MAX_CANDIDATE_INSPECTIONS_PER_SCAN = 64;
  static final int HARD_MAX_ITEMS_PER_PULSE = 32;
  static final double HARD_MAX_RADIUS = 16D;

  private final Cooldowns engageThrottle;
  private final MagnetCoordinator<Player> coordinator;
  private final MagnetWorkBudget workBudget;

  public RiftVoidMagnet() {
    super("rift-void-magnet");
    engageThrottle = cooldowns();
    coordinator = new MagnetCoordinator<>(HARD_MAX_ACTIVE_SESSIONS);
    workBudget = new MagnetWorkBudget(
        HARD_MAX_SESSION_VISITS_PER_WINDOW,
        HARD_MAX_SCAN_OWNER_HANDOFFS_PER_WINDOW,
        HARD_MAX_SCAN_EXECUTIONS_PER_WINDOW,
        HARD_MAX_CANDIDATE_INSPECTIONS_PER_WINDOW,
        HARD_MAX_ITEM_HANDOFFS_PER_WINDOW,
        HARD_MAX_ITEM_EXECUTIONS_PER_WINDOW,
        WORK_WINDOW_MILLIS,
        System::currentTimeMillis
    );
    registerConfiguration(Config.class);
    setIcon(Material.HOPPER_MINECART);
    setInterval(WORK_WINDOW_MILLIS);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_PEARL)
        .key("challenge_rift_void_magnet_5k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENDER_EYE)
            .key("challenge_rift_void_magnet_50k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_void_magnet_5k", "rift.void-magnet.items-pulled", 5000, 400);
    registerMilestone("challenge_rift_void_magnet_50k", "rift.void-magnet.items-pulled", 50000, 1500);
  }

  private static boolean acceptsItem(Items selection, Material type) {
    return switch (selection) {
      case ALL -> true;
      case BLOCKS -> type.isBlock();
      case FOOD -> type.isEdible();
    };
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONTROL, DESTINATION, ITEMS);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    Player p = player.getPlayer();
    coordinator.remove(p.getUniqueId());
    if (collectionArmed(p) && hasActiveAdaptation(p)) {
      startSession(p, getLevel(p));
    }
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRadius(level)), 1);
    statLore(v, getMaxItems(level), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getPulseTicks(level) * 50D, 1), 3);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    if (!e.isSneaking() && preference(e.getPlayer(), CONTROL) == Control.SNEAK) {
      coordinator.remove(e.getPlayer().getUniqueId());
      return;
    }
    Player p = e.getPlayer();
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!startSession(p, level)) {
      return;
    }
    if (!engageThrottle.isReady(p.getUniqueId(), 2500L)) {
      return;
    }

    engageThrottle.mark(p.getUniqueId());
    fx(p, FxPriority.TRANSITION)
        .ring(Particles.END_ROD, getRadius(level), 24, 0.1)
        .sound(Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 1.5f);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    UUID playerId = e.getPlayer().getUniqueId();
    coordinator.remove(playerId);
    engageThrottle.clear(playerId);
  }

  @Override
  public boolean hasTickDemand() {
    return coordinator.size() > 0;
  }

  @Override
  public void onTick() {
    if (coordinator.size() == 0) {
      return;
    }

    int visitLimit = workBudget.takeSessionVisits(HARD_MAX_SESSION_VISITS_PER_WINDOW);
    int dispatchLimit = workBudget.remainingScanOwnerHandoffs();
    if (visitLimit <= 0 || dispatchLimit <= 0) {
      return;
    }

    List<MagnetDispatch<Player>> dispatches = coordinator.poll(
        System.currentTimeMillis(),
        visitLimit,
        dispatchLimit
    );
    workBudget.takeScanOwnerHandoffs(dispatches.size());
    for (MagnetDispatch<Player> dispatch : dispatches) {
      if (!J.runEntity(dispatch.owner(), () -> scanOwnerOwned(dispatch))) {
        coordinator.remove(dispatch.ownerId(), dispatch.generation());
      }
    }
  }

  @Override
  public void unregister() {
    coordinator.clear();
    super.unregister();
  }

  private boolean collectionArmed(Player player) {
    return player.isSneaking() || preference(player, CONTROL) == Control.ARMED;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    if (preference(player, CONTROL) == Control.ARMED) {
      int level = getActiveLevel(player);
      if (level > 0) {
        startSession(player, level);
      }
    }
  }

  private boolean startSession(Player player, int level) {
    UUID playerId = player.getUniqueId();
    long firstPulseAt = System.currentTimeMillis() + (getPulseTicks(level) * 50L);
    return coordinator.admit(playerId, player, firstPulseAt);
  }

  private void scanOwnerOwned(MagnetDispatch<Player> dispatch) {
    Player player = dispatch.owner();
    boolean keepSession = false;
    long nextPulseAt = System.currentTimeMillis();
    try {
      if (!coordinator.isCurrent(dispatch.ownerId(), dispatch.generation())
          || !player.isOnline() || !collectionArmed(player)) {
        return;
      }

      int level = getActiveLevel(player, this::collectionArmed);
      if (level <= 0) {
        return;
      }
      if (!workBudget.tryScanExecution()) {
        keepSession = true;
        return;
      }

      double radius = getRadius(level);
      Location center = player.getLocation().clone();
      if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(center, radius, radius)) {
        keepSession = true;
        nextPulseAt = System.currentTimeMillis() + (getPulseTicks(level) * 50L);
        return;
      }
      MagnetPulse pulse = new MagnetPulse(
          dispatch.ownerId(),
          dispatch.generation(),
          player,
          center,
          radius * radius,
          getMaxItems(level)
      );
      Collection<Entity> nearby = player.getWorld().getNearbyEntities(
          center,
          radius,
          radius,
          radius,
          entity -> entity instanceof Item
      );
      int inspected = 0;
      for (Entity entity : nearby) {
        if (inspected >= HARD_MAX_CANDIDATE_INSPECTIONS_PER_SCAN
            || !workBudget.tryCandidateInspection()) {
          break;
        }
        inspected++;
        if (!workBudget.tryItemHandoff()) {
          break;
        }
        Item item = (Item) entity;
        J.runEntity(item, () -> transferItemOwned(pulse, item));
      }

      keepSession = true;
      nextPulseAt = System.currentTimeMillis() + (getPulseTicks(level) * 50L);
    } finally {
      if (keepSession) {
        coordinator.complete(dispatch.ownerId(), dispatch.generation(), nextPulseAt);
      } else {
        coordinator.remove(dispatch.ownerId(), dispatch.generation());
      }
    }
  }

  private void transferItemOwned(MagnetPulse pulse, Item item) {
    Player player = pulse.owner;
    if (!coordinator.isCurrent(pulse.ownerId, pulse.generation)
        || !J.isOwnedByCurrentRegion(item)
        || !J.isOwnedByCurrentRegion(player)) {
      return;
    }
    if (!workBudget.tryItemExecution()
        || !player.isOnline()
        || !collectionArmed(player)
        || getActiveLevel(player, this::collectionArmed) <= 0
        || item.isDead()
        || !item.isValid()
        || !canSnatchItem(player, item)) {
      return;
    }

    Location itemLocation = item.getLocation();
    if (itemLocation.getWorld() != pulse.center.getWorld()
        || itemLocation.distanceSquared(pulse.center) > pulse.radiusSquared) {
      return;
    }

    ItemStack original = item.getItemStack().clone();
    if (original == null || original.getType().isAir() || original.getAmount() <= 0
        || !acceptsItem(preference(player, ITEMS), original.getType())) {
      return;
    }

    if (!pulse.transfers.reserveDrop()) {
      return;
    }

    boolean transferred = false;
    try {
      int remaining = remainingAfterMagnetPickup(player, original);
      if (!ProtectionEventProbe.attemptItemPickup(player, item, remaining)
          || !item.isValid()
          || item.isDead()) {
        return;
      }

      ItemStack stack = item.getItemStack().clone();
      if (!stack.isSimilar(original) || stack.getAmount() <= 0) {
        return;
      }

      int moved = depositIntoInventories(player, stack, stack.getAmount());
      if (moved <= 0) {
        return;
      }
      transferred = true;

      if (moved >= stack.getAmount()) {
        item.remove();
      } else {
        stack.setAmount(stack.getAmount() - moved);
        item.setItemStack(stack);
      }

      addStat(player, "rift.void-magnet.items-pulled", moved);
      xp(player, moved * getConfig().xpPerMovedItem, "rift:void-magnet:item-pull");
      if (pulse.feedback.compareAndSet(false, true)) {
        fx(player, FxPriority.TRAIL)
            .particle(Particle.PORTAL, 8, 0, 1.0, 0, 0.3, 0.05)
            .sound(Sound.BLOCK_ENDER_CHEST_OPEN, 0.45f, Math.min(1.9f, 1.4f + (moved * 0.02f)));
      }
    } finally {
      if (!transferred) {
        pulse.transfers.releaseDrop();
      }
    }
  }

  private Destination pickupDestination(Player player) {
    return getConfig().allowEnderChestOverflow ? preference(player, DESTINATION) : Destination.CHEST_ONLY;
  }

  private Inventory primaryInventory(Player player, Destination destination) {
    return destination == Destination.INVENTORY_FIRST || destination == Destination.INVENTORY_ONLY
        ? player.getInventory() : player.getEnderChest();
  }

  private Inventory secondaryInventory(Player player, Destination destination) {
    return switch (destination) {
      case CHEST_FIRST -> player.getInventory();
      case INVENTORY_FIRST -> player.getEnderChest();
      case CHEST_ONLY, INVENTORY_ONLY -> null;
    };
  }

  private int remainingAfterMagnetPickup(Player player, ItemStack stack) {
    Destination destination = pickupDestination(player);
    int remaining = ProtectionEventProbe.remainingAfterPickup(primaryInventory(player, destination), stack);
    Inventory secondary = secondaryInventory(player, destination);
    if (remaining <= 0 || secondary == null) {
      return Math.max(0, remaining);
    }
    ItemStack remainder = stack.clone();
    remainder.setAmount(remaining);
    return ProtectionEventProbe.remainingAfterPickup(secondary, remainder);
  }

  private int depositIntoInventories(Player player, ItemStack stack, int requested) {
    Destination destination = pickupDestination(player);
    ItemStack transfer = stack.clone();
    transfer.setAmount(requested);
    int remaining = sumItemAmounts(primaryInventory(player, destination).addItem(transfer));
    Inventory secondary = secondaryInventory(player, destination);
    if (remaining > 0 && secondary != null) {
      transfer = stack.clone();
      transfer.setAmount(remaining);
      remaining = sumItemAmounts(secondary.addItem(transfer));
    }
    return Math.max(0, requested - remaining);
  }

  private int sumItemAmounts(Map<Integer, ItemStack> overflow) {
    int sum = 0;
    for (ItemStack itemStack : overflow.values()) {
      if (itemStack == null || itemStack.getType().isAir()) {
        continue;
      }
      sum += itemStack.getAmount();
    }
    return sum;
  }

  private double getRadius(int level) {
    return boundedRadius(getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor));
  }

  private int getMaxItems(int level) {
    return boundedItemLimit(getConfig().maxItemsBase + (getLevelPercent(level) * getConfig().maxItemsFactor));
  }

  private int getPulseTicks(int level) {
    return Math.max(2, (int) Math.round(getConfig().pulseTicksBase - (getLevelPercent(level) * getConfig().pulseTicksFactor)));
  }

  static double boundedRadius(double radius) {
    if (!Double.isFinite(radius)) {
      return 1D;
    }
    return Math.max(1D, Math.min(HARD_MAX_RADIUS, radius));
  }

  static int boundedItemLimit(double itemLimit) {
    if (!Double.isFinite(itemLimit)) {
      return 1;
    }
    return Math.max(1, Math.min(HARD_MAX_ITEMS_PER_PULSE, (int) Math.round(itemLimit)));
  }

  @ConfigDescription("Sneak to periodically pull nearby dropped items into your ender chest first.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Allow Ender Chest Overflow for the Rift Void Magnet adaptation.", impact = "When true, leftovers that do not fit in ender chest can spill into player inventory.")
    boolean allowEnderChestOverflow = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Rift Void Magnet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Rift Void Magnet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base number of separate item drops pulled per pulse for the Rift Void Magnet adaptation.", impact = "Higher values pull more distinct drops each pulse; stack size does not count against this limit.")
    double maxItemsBase = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional item drops pulled per pulse granted by level for the Rift Void Magnet adaptation.", impact = "Higher values let higher levels clear more distinct drops per pulse.")
    double maxItemsFactor = 22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Pulse Ticks Base for the Rift Void Magnet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pulseTicksBase = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Pulse Ticks Factor for the Rift Void Magnet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double pulseTicksFactor = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Moved Item for the Rift Void Magnet adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerMovedItem = 0.7;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }

  static final class MagnetCoordinator<T> {
    private final int capacity;
    private final Map<UUID, MagnetSession<T>> sessions = new HashMap<>();
    private final ArrayDeque<MagnetSession<T>> queue = new ArrayDeque<>();
    private long generation;

    MagnetCoordinator(int capacity) {
      if (capacity <= 0) {
        throw new IllegalArgumentException("Magnet session capacity must be positive");
      }
      this.capacity = capacity;
    }

    synchronized boolean admit(UUID ownerId, T owner, long nextPulseAt) {
      Objects.requireNonNull(ownerId);
      Objects.requireNonNull(owner);
      if (sessions.containsKey(ownerId) || sessions.size() >= capacity) {
        return false;
      }

      MagnetSession<T> session = new MagnetSession<>(ownerId, ++generation, owner, nextPulseAt);
      sessions.put(ownerId, session);
      queue.addLast(session);
      return true;
    }

    synchronized List<MagnetDispatch<T>> poll(long now, int visitLimit, int dispatchLimit) {
      int visits = Math.min(Math.max(0, visitLimit), queue.size());
      int dispatches = Math.max(0, dispatchLimit);
      ArrayList<MagnetDispatch<T>> ready = new ArrayList<>(Math.min(visits, dispatches));
      for (int visited = 0; visited < visits && ready.size() < dispatches; visited++) {
        MagnetSession<T> session = queue.pollFirst();
        if (session == null) {
          break;
        }
        if (sessions.get(session.ownerId) != session) {
          continue;
        }
        queue.addLast(session);
        if (session.inFlight || now < session.nextPulseAt) {
          continue;
        }
        session.inFlight = true;
        ready.add(new MagnetDispatch<>(session.ownerId, session.generation, session.owner));
      }
      return ready;
    }

    synchronized boolean complete(UUID ownerId, long expectedGeneration, long nextPulseAt) {
      MagnetSession<T> session = sessions.get(ownerId);
      if (session == null || session.generation != expectedGeneration || !session.inFlight) {
        return false;
      }
      session.nextPulseAt = nextPulseAt;
      session.inFlight = false;
      return true;
    }

    synchronized boolean isCurrent(UUID ownerId, long expectedGeneration) {
      MagnetSession<T> session = sessions.get(ownerId);
      return session != null && session.generation == expectedGeneration;
    }

    synchronized boolean remove(UUID ownerId, long expectedGeneration) {
      MagnetSession<T> session = sessions.get(ownerId);
      if (session == null || session.generation != expectedGeneration) {
        return false;
      }
      sessions.remove(ownerId);
      queue.remove(session);
      return true;
    }

    synchronized boolean remove(UUID ownerId) {
      MagnetSession<T> session = sessions.remove(ownerId);
      if (session == null) {
        return false;
      }
      queue.remove(session);
      return true;
    }

    synchronized int clear() {
      int removed = sessions.size();
      sessions.clear();
      queue.clear();
      return removed;
    }

    synchronized int size() {
      return sessions.size();
    }
  }

  static final class MagnetWorkBudget {
    private final int sessionVisitLimit;
    private final int scanOwnerHandoffLimit;
    private final int scanExecutionLimit;
    private final int candidateInspectionLimit;
    private final int itemHandoffLimit;
    private final int itemExecutionLimit;
    private final long windowMillis;
    private final LongSupplier clock;
    private long window = Long.MIN_VALUE;
    private int sessionVisits;
    private int scanOwnerHandoffs;
    private int scanExecutions;
    private int candidateInspections;
    private int itemHandoffs;
    private int itemExecutions;

    MagnetWorkBudget(int sessionVisitLimit, int scanOwnerHandoffLimit, int scanExecutionLimit,
                     int candidateInspectionLimit, int itemHandoffLimit, int itemExecutionLimit,
                     long windowMillis, LongSupplier clock) {
      if (sessionVisitLimit <= 0 || scanOwnerHandoffLimit <= 0 || scanExecutionLimit <= 0
          || candidateInspectionLimit <= 0 || itemHandoffLimit <= 0 || itemExecutionLimit <= 0
          || windowMillis <= 0) {
        throw new IllegalArgumentException("Magnet work limits must be positive");
      }
      this.sessionVisitLimit = sessionVisitLimit;
      this.scanOwnerHandoffLimit = scanOwnerHandoffLimit;
      this.scanExecutionLimit = scanExecutionLimit;
      this.candidateInspectionLimit = candidateInspectionLimit;
      this.itemHandoffLimit = itemHandoffLimit;
      this.itemExecutionLimit = itemExecutionLimit;
      this.windowMillis = windowMillis;
      this.clock = Objects.requireNonNull(clock);
    }

    synchronized int takeSessionVisits(int requested) {
      rotateWindow();
      int granted = Math.min(Math.max(0, requested), sessionVisitLimit - sessionVisits);
      sessionVisits += granted;
      return granted;
    }

    synchronized int remainingScanOwnerHandoffs() {
      rotateWindow();
      return Math.max(0, scanOwnerHandoffLimit - scanOwnerHandoffs);
    }

    synchronized int takeScanOwnerHandoffs(int requested) {
      rotateWindow();
      int granted = Math.min(Math.max(0, requested), scanOwnerHandoffLimit - scanOwnerHandoffs);
      scanOwnerHandoffs += granted;
      return granted;
    }

    synchronized boolean tryScanExecution() {
      rotateWindow();
      if (scanExecutions >= scanExecutionLimit) {
        return false;
      }
      scanExecutions++;
      return true;
    }

    synchronized boolean tryCandidateInspection() {
      rotateWindow();
      if (candidateInspections >= candidateInspectionLimit) {
        return false;
      }
      candidateInspections++;
      return true;
    }

    synchronized boolean tryItemHandoff() {
      rotateWindow();
      if (itemHandoffs >= itemHandoffLimit) {
        return false;
      }
      itemHandoffs++;
      return true;
    }

    synchronized boolean tryItemExecution() {
      rotateWindow();
      if (itemExecutions >= itemExecutionLimit) {
        return false;
      }
      itemExecutions++;
      return true;
    }

    private void rotateWindow() {
      long currentWindow = Math.floorDiv(clock.getAsLong(), windowMillis);
      if (currentWindow == window) {
        return;
      }
      window = currentWindow;
      sessionVisits = 0;
      scanOwnerHandoffs = 0;
      scanExecutions = 0;
      candidateInspections = 0;
      itemHandoffs = 0;
      itemExecutions = 0;
    }
  }

  /**
   * Per-pulse ceiling counted in item drops, not item units. A drop-denominated
   * budget keeps one large stack from consuming the whole pulse and starving
   * every other drop the player is standing on.
   */
  static final class MagnetTransferBudget {
    private final int limit;
    private final AtomicInteger reserved = new AtomicInteger();

    MagnetTransferBudget(int limit) {
      if (limit <= 0) {
        throw new IllegalArgumentException("Magnet transfer limit must be positive");
      }
      this.limit = limit;
    }

    boolean reserveDrop() {
      int current = reserved.get();
      while (current < limit) {
        if (reserved.compareAndSet(current, current + 1)) {
          return true;
        }
        current = reserved.get();
      }
      return false;
    }

    void releaseDrop() {
      reserved.updateAndGet(current -> Math.max(0, current - 1));
    }

    int reserved() {
      return reserved.get();
    }
  }

  private static final class MagnetPulse {
    private final UUID ownerId;
    private final long generation;
    private final Player owner;
    private final Location center;
    private final double radiusSquared;
    private final MagnetTransferBudget transfers;
    private final AtomicBoolean feedback = new AtomicBoolean();

    private MagnetPulse(UUID ownerId, long generation, Player owner, Location center,
                        double radiusSquared, int transferLimit) {
      this.ownerId = ownerId;
      this.generation = generation;
      this.owner = owner;
      this.center = center;
      this.radiusSquared = radiusSquared;
      transfers = new MagnetTransferBudget(transferLimit);
    }
  }

  private static final class MagnetSession<T> {
    private final UUID ownerId;
    private final long generation;
    private final T owner;
    private long nextPulseAt;
    private boolean inFlight;

    private MagnetSession(UUID ownerId, long generation, T owner, long nextPulseAt) {
      this.ownerId = ownerId;
      this.generation = generation;
      this.owner = owner;
      this.nextPulseAt = nextPulseAt;
    }
  }

  record MagnetDispatch<T>(UUID ownerId, long generation, T owner) {
  }

  public enum Control { SNEAK, ARMED }

  public enum Destination { CHEST_FIRST, INVENTORY_FIRST, CHEST_ONLY, INVENTORY_ONLY }

  public enum Items { ALL, BLOCKS, FOOD }
}
