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

package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.catalog.StealthMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class StealthSnatch extends SimpleAdaptation<StealthSnatch.Config> {
  public static final PlayerPreference<Control> CONTROL = new PlayerPreference<>(Control.class,
      new PlayerPreference.Definition<>("control", StealthMessages.STEALTHSNATCH_PREFERENCE_CONTROL, Control.SNEAK, List.of(
          new PlayerPreference.Choice<>(Control.SNEAK, StealthMessages.STEALTHSNATCH_PREFERENCE_CONTROL_SNEAK, Id.minecraft("leather_boots"), 1),
          new PlayerPreference.Choice<>(Control.AUTOMATIC, StealthMessages.STEALTHSNATCH_PREFERENCE_CONTROL_AUTOMATIC, Id.minecraft("hopper"), 1))));
  public static final PlayerPreference<Items> ITEMS = new PlayerPreference<>(Items.class,
      new PlayerPreference.Definition<>("items", StealthMessages.STEALTHSNATCH_PREFERENCE_ITEMS, Items.ALL, List.of(
          new PlayerPreference.Choice<>(Items.ALL, StealthMessages.STEALTHSNATCH_PREFERENCE_ITEMS_ALL, Id.minecraft("chest"), 1),
          new PlayerPreference.Choice<>(Items.BLOCKS, StealthMessages.STEALTHSNATCH_PREFERENCE_ITEMS_BLOCKS, Id.minecraft("stone"), 1),
          new PlayerPreference.Choice<>(Items.FOOD, StealthMessages.STEALTHSNATCH_PREFERENCE_ITEMS_FOOD, Id.minecraft("bread"), 1))));

  private static final int MAX_ITEMS_PER_PULSE = 32;
  private static final int MAX_CANDIDATES_PER_PULSE = 128;
  private static final int MAX_SESSION_VISITS_PER_TICK = 32;
  private static final long ACTIVE_TICK_MILLIS = 50L;
  private static final long IDLE_TICK_MILLIS = 1000L;
  private static final long HOLD_EXPIRY_MILLIS = 5000L;
  private final Map<Integer, Long> holds;
  private final Map<UUID, SnatchSession> activeSessions;
  private final ConcurrentLinkedQueue<SnatchSession> sessionQueue;

  public StealthSnatch() {
    super("stealth-snatch");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("chest_minecart"));
    holds = new ConcurrentHashMap<>();
    activeSessions = playerState();
    sessionQueue = new ConcurrentLinkedQueue<>();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("chest"))
        .key("challenge_stealth_snatch_2500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("hopper"))
            .key("challenge_stealth_snatch_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_stealth_snatch_2500", "stealth.snatch.items-snatched", 2500, 400);
    registerMilestone("challenge_stealth_snatch_25k", "stealth.snatch.items-snatched", 25000, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONTROL, ITEMS);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    PlayerRef p = player.getPlayer();
    removePlayerSession(p.uuid());
    if (hasActiveAdaptation(p) && (p.isSneaking() || preference(p, CONTROL) == Control.AUTOMATIC)) {
      startCollection(p);
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getRange(getLevelPercent(level)), 1), 1);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    UUID playerId = p.uuid();
    if (!e.isSneaking() && preference(p, CONTROL) == Control.SNEAK) {
      removePlayerSession(playerId);
      return;
    }
    if (!hasActiveAdaptation(p)) {
      return;
    }

    startCollection(p);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    if (preference(p, CONTROL) == Control.AUTOMATIC && hasActiveAdaptation(p) && !activeSessions.containsKey(p.uuid())) {
      startCollection(p);
    }
  }

  private void startCollection(PlayerRef p) {
    UUID playerId = p.uuid();
    if (activeSessions.containsKey(playerId)) {
      return;
    }
    double range = getRange(getLevelPercent(p));
    timeline(p).duration(6).priority(FxPriority.TRANSITION).cullRadius(range + 8)
        .frame((fx, tick, progress) -> {
          fx.ring(Particles.ENCHANT, 0.3D + ((range - 0.3D) * progress), 10, 0.1D);
          if (tick == 0) {
            fx.sound(Sounds.BLOCK_AMETHYST_BLOCK_RESONATE, 0.4F, 1.5F);
          }
        }).start();
    snatch(p);
    SnatchSession session = new SnatchSession(p, playerId);
    if (activeSessions.putIfAbsent(playerId, session) == null) {
      session.nextSnatchAt = System.currentTimeMillis() + getSnatchRateMillis();
      sessionQueue.add(session);
      setInterval(ACTIVE_TICK_MILLIS);
      if (!isBursting()) {
        retick();
      }
    }
  }

  private boolean acceptsItem(PlayerRef player, Id type) {
    return switch (preference(player, ITEMS)) {
      case ALL -> true;
      case BLOCKS -> AdaptCore.platform().host().items().isBlock(type);
      case FOOD -> AdaptCore.platform().host().items().isEdible(type);
    };
  }

  private void snatch(PlayerRef player) {
    double factor = getLevelPercent(player);

    if (factor == 0) {
      return;
    }

    if (!canAccessChest(player, player.location())) {
      return;
    }

    double range = getRange(factor);
    Loc playerLocation = player.location();
    if (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(playerLocation, range, range)) {
      return;
    }
    List<ItemEntityRef> items = new ArrayList<>(MAX_ITEMS_PER_PULSE);
    int inspected = 0;
    Box area = Box.around(playerLocation.position(), range, range / 1.5, range);
    for (EntityRef droppedItemEntity : player.world().entitiesIn(area, entity -> true)) {
      if (++inspected > MAX_CANDIDATES_PER_PULSE || items.size() >= MAX_ITEMS_PER_PULSE) {
        break;
      }
      if (droppedItemEntity instanceof ItemEntityRef droppedItem
          && (!Tasks.isFoliaThreading() || Tasks.isOwnedByCurrentRegion(droppedItem))
          && acceptsItem(player, droppedItem.item().type())
          && canSnatchItem(player, droppedItem)) {
        items.add(droppedItem);
      }
    }

    int fxBudget = 3;
    int snatched = 0;
    for (ItemEntityRef droppedItemEntity : items) {
      if (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(droppedItemEntity)) {
        continue;
      }
      if (holds.containsKey(droppedItemEntity.entityId())) {
        continue;
      }

      double dist = droppedItemEntity.location().distanceSquared(playerLocation);
      if (dist >= range * range) {
        continue;
      }

      ItemRef is = droppedItemEntity.item().copy();
      if (!hasSpace(player.inventory(), is)) {
        continue;
      }

      holds.put(droppedItemEntity.entityId(), System.currentTimeMillis());
      int id = droppedItemEntity.entityId();
      Loc itemLoc = fxBudget > 0 ? droppedItemEntity.location() : null;
      if (safeGiveItem(player, droppedItemEntity, is)) {
        snatched++;
        if (itemLoc != null && fxBudget-- > 0) {
          Loc target = player.location().add(0, 1.0D, 0);
          float pitch = (float) (1.0D + (ThreadLocalRandom.current().nextDouble() / 3D));
          float pickupPitch = (float) (1.4D + (ThreadLocalRandom.current().nextDouble() * 0.4D));
          fx(itemLoc, FxPriority.TRAIL)
              .line(Particles.ENCHANT, target.x(), target.y(), target.z(), 4)
              .chord(Sounds.BLOCK_LAVA_POP, 0.6F, pitch, Sounds.ENTITY_ITEM_PICKUP, 0.5F, pickupPitch);
        }
        addStat(player, "stealth.snatch.items-snatched", 1);
      }
      Tasks.runEntity(player, () -> holds.remove(Integer.valueOf(id)), 1);
    }

    if (snatched > 3) {
      fx(player.location().add(0, 1.0D, 0), FxPriority.TRAIL).burst(Particles.ENCHANT, 5, 0.3D);
    }
  }

  /**
   * Enough empty slots for the whole stacks in {@code item}. Merging into partial stacks is not counted.
   */
  static boolean hasSpace(InventoryRef inventory, ItemRef item) {
    int needed = item.amount() / Math.max(1, item.maxStackSize());
    int empty = 0;
    for (ItemRef slot : inventory.contents()) {
      if (slot == null || slot.isEmpty() || slot.type().isAir()) {
        empty++;
      }
    }
    return empty >= needed;
  }

  private double getRange(double factor) {
    double range = (factor * getConfig().radiusFactor) + 1D;
    return Double.isFinite(range) ? Math.max(1D, Math.min(8D, range)) : 1D;
  }

  @Override
  public void onTick() {
    if (!holds.isEmpty()) {
      long cutoff = System.currentTimeMillis() - HOLD_EXPIRY_MILLIS;
      holds.values().removeIf(at -> at <= cutoff);
    }

    if (activeSessions.isEmpty()) {
      setInterval(IDLE_TICK_MILLIS);
      return;
    }

    long now = System.currentTimeMillis();
    int visitLimit = Math.min(MAX_SESSION_VISITS_PER_TICK, activeSessions.size());
    for (int visited = 0; visited < visitLimit; visited++) {
      SnatchSession session = sessionQueue.poll();
      if (session == null) {
        break;
      }
      if (activeSessions.get(session.playerId) != session) {
        continue;
      }
      sessionQueue.add(session);
      if (now < session.nextSnatchAt || !session.pending.compareAndSet(false, true)) {
        continue;
      }
      if (!Tasks.runEntity(session.player, () -> runSnatchSession(session))) {
        session.pending.set(false);
        removeSession(session);
      }
    }
  }

  private void runSnatchSession(SnatchSession session) {
    try {
      PlayerRef player = session.player;
      if (activeSessions.get(session.playerId) != session
          || !player.isOnline() || player.isDead() || (!player.isSneaking() && preference(player, CONTROL) == Control.SNEAK)
          || !hasActiveAdaptation(player)) {
        removeSession(session);
        return;
      }

      snatch(player);
      session.nextSnatchAt = System.currentTimeMillis() + getSnatchRateMillis();
    } finally {
      session.pending.set(false);
    }
  }

  private long getSnatchRateMillis() {
    return Math.max(ACTIVE_TICK_MILLIS, getConfig().snatchRate);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    removePlayerSession(event.player().uuid());
  }

  private void removePlayerSession(UUID playerId) {
    SnatchSession removed = activeSessions.remove(playerId);
    if (removed != null) {
      sessionQueue.remove(removed);
      return;
    }
    sessionQueue.removeIf(session -> session.playerId.equals(playerId));
  }

  private void removeSession(SnatchSession session) {
    activeSessions.remove(session.playerId, session);
    sessionQueue.remove(session);
  }

  @Override
  public void unregister() {
    activeSessions.clear();
    sessionQueue.clear();
    holds.clear();
    super.unregister();
  }

  private static final class SnatchSession {
    private final PlayerRef player;
    private final UUID playerId;
    private final AtomicBoolean pending = new AtomicBoolean();
    private volatile long nextSnatchAt;

    private SnatchSession(PlayerRef player, UUID playerId) {
      this.player = player;
      this.playerId = playerId;
    }
  }

  @ConfigDescription("Snatch dropped items instantly while sneaking.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Snatch Rate for the Stealth Snatch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int snatchRate = 250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Stealth Snatch adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 5.55;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 3;
      initialCost = 1;
    }
  }

  public enum Control { SNEAK, AUTOMATIC }

  public enum Items { ALL, BLOCKS, FOOD }
}
