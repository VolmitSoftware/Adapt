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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.inventorygui.Inventories;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class StealthSnatch extends SimpleAdaptation<StealthSnatch.Config> {
  private static final int MAX_ITEMS_PER_PULSE = 32;
  private static final int MAX_CANDIDATES_PER_PULSE = 128;
  private static final int MAX_SESSION_VISITS_PER_TICK = 32;
  private static final long ACTIVE_TICK_MILLIS = 50L;
  private static final long IDLE_TICK_MILLIS = 1000L;
  private final Set<Integer> holds;
  private final Map<UUID, SnatchSession> activeSessions;
  private final ConcurrentLinkedQueue<SnatchSession> sessionQueue;

  public StealthSnatch() {
    super("stealth-snatch");
    registerConfiguration(Config.class);
    setIcon(Material.CHEST_MINECART);
    holds = ConcurrentHashMap.newKeySet();
    activeSessions = playerState();
    sessionQueue = new ConcurrentLinkedQueue<>();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CHEST)
        .key("challenge_stealth_snatch_2500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.HOPPER)
            .key("challenge_stealth_snatch_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_stealth_snatch_2500", "stealth.snatch.items-snatched", 2500, 400);
    registerMilestone("challenge_stealth_snatch_25k", "stealth.snatch.items-snatched", 25000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRange(getLevelPercent(level)), 1), 1);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    UUID playerId = p.getUniqueId();
    if (!e.isSneaking()) {
      removePlayerSession(playerId);
      return;
    }
    if (!hasActiveAdaptation(p)) {
      return;
    }

    double range = getRange(getLevelPercent(p));
    timeline(p).duration(6).priority(FxPriority.TRANSITION).cullRadius(range + 8)
        .frame((fx, tick, progress) -> {
          fx.ring(Particle.ENCHANT, 0.3D + ((range - 0.3D) * progress), 10, 0.1D);
          if (tick == 0) {
            fx.sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.4F, 1.5F);
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

  private void snatch(Player player) {
    double factor = getLevelPercent(player);

    if (factor == 0) {
      return;
    }

    if (!canAccessChest(player, player.getLocation())) {
      return;
    }

    double range = getRange(factor);
    List<Item> items = new ArrayList<>(MAX_ITEMS_PER_PULSE);
    int inspected = 0;
    for (Entity droppedItemEntity : player.getWorld().getNearbyEntities(player.getLocation(), range, range / 1.5, range)) {
      if (++inspected > MAX_CANDIDATES_PER_PULSE || items.size() >= MAX_ITEMS_PER_PULSE) {
        break;
      }
      if (droppedItemEntity instanceof Item droppedItem && canSnatchItem(player, droppedItem)) {
        items.add(droppedItem);
      }
    }

    int fxBudget = 3;
    int snatched = 0;
    for (Item droppedItemEntity : items) {
      if (holds.contains(droppedItemEntity.getEntityId())) {
        continue;
      }

      double dist = droppedItemEntity.getLocation().distanceSquared(player.getLocation());
      if (dist >= range * range) {
        continue;
      }

      ItemStack is = droppedItemEntity.getItemStack().clone();
      if (!Inventories.hasSpace(player.getInventory(), is)) {
        continue;
      }

      holds.add(droppedItemEntity.getEntityId());
      int id = droppedItemEntity.getEntityId();
      Location itemLoc = fxBudget > 0 ? droppedItemEntity.getLocation() : null;
      if (safeGiveItem(player, droppedItemEntity, is)) {
        snatched++;
        if (itemLoc != null && fxBudget-- > 0) {
          Location target = player.getLocation().add(0, 1.0D, 0);
          float pitch = (float) (1.0D + (ThreadLocalRandom.current().nextDouble() / 3D));
          float pickupPitch = (float) (1.4D + (ThreadLocalRandom.current().nextDouble() * 0.4D));
          fx(itemLoc, FxPriority.TRAIL)
              .line(Particle.ENCHANT, target.getX(), target.getY(), target.getZ(), 4)
              .chord(Sound.BLOCK_LAVA_POP, 0.6F, pitch, Sound.ENTITY_ITEM_PICKUP, 0.5F, pickupPitch);
        }
        addStat(player, "stealth.snatch.items-snatched", 1);
      }
      J.runEntity(player, () -> holds.remove(Integer.valueOf(id)), 1);
    }

    if (snatched > 3) {
      fx(player.getLocation().add(0, 1.0D, 0), FxPriority.TRAIL).burst(Particle.ENCHANT, 5, 0.3D);
    }
  }

  private double getRange(double factor) {
    double range = (factor * getConfig().radiusFactor) + 1D;
    return Double.isFinite(range) ? Math.max(1D, Math.min(8D, range)) : 1D;
  }

  @Override
  public void onTick() {
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
      if (!J.runEntity(session.player, () -> runSnatchSession(session))) {
        session.pending.set(false);
        removeSession(session);
      }
    }
  }

  private void runSnatchSession(SnatchSession session) {
    try {
      Player player = session.player;
      if (activeSessions.get(session.playerId) != session
          || !player.isOnline() || player.isDead() || !player.isSneaking()
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

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    removePlayerSession(event.getPlayer().getUniqueId());
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
    super.unregister();
  }

  private static final class SnatchSession {
    private final Player player;
    private final UUID playerId;
    private final AtomicBoolean pending = new AtomicBoolean();
    private volatile long nextSnatchAt;

    private SnatchSession(Player player, UUID playerId) {
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
      costFactor = 0.125;
      maxLevel = 3;
      initialCost = 12;
    }
  }
}
