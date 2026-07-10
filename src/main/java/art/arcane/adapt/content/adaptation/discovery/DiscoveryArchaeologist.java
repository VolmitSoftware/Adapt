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

package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class DiscoveryArchaeologist extends SimpleAdaptation<DiscoveryArchaeologist.Config> {
  private static final String BLOCK_BRUSH_EVENT_CLASS = "org.bukkit.event.block.BlockBrushEvent";
  private static final long BRUSH_FALLBACK_WINDOW_MILLIS = 25000L;
  private static final long FALLBACK_CHECK_INTERVAL_MILLIS = 2300L;
  private static final int PENDING_CHECKS_PER_TICK = 16;
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, PendingBrush> pendingBrushes = playerState();
  private final Queue<UUID> pendingQueue = new ConcurrentLinkedQueue<>();
  private final Set<UUID> queuedPending = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean brushEventFailureWarned = new AtomicBoolean(false);
  private final BrushEventBridge brushEventBridge;

  public DiscoveryArchaeologist() {
    super("discovery-archaeologist");
    registerConfiguration(Config.class);
    setIcon(Material.BRUSH);
    setInterval(10);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BRUSH)
        .key("challenge_discovery_archaeologist_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DECORATED_POT)
            .key("challenge_discovery_archaeologist_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_discovery_archaeologist_50", "discovery.archaeologist.bonus-finds", 50, 300);
    registerMilestone("challenge_discovery_archaeologist_500", "discovery.archaeologist.bonus-finds", 500, 1000);
    brushEventBridge = BrushEventBridge.create();
    registerBrushEventBridge(brushEventBridge);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getBonusRollChance(level), 0), 1);
    statLore(v, Form.pc(getRareRewardChance(level), 0), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    ItemStack hand = e.getItem();
    if (hand == null || hand.getType() != Material.BRUSH) {
      return;
    }

    if (e.getHand() == EquipmentSlot.OFF_HAND && e.getPlayer().getInventory().getItemInMainHand().getType() == Material.BRUSH) {
      return;
    }

    Block block = e.getClickedBlock();
    if (block == null || !isSuspiciousBlock(block.getType())) {
      return;
    }

    Player p = e.getPlayer();
    if (getActiveBlockBreakLevel(p, block.getLocation()) <= 0) {
      return;
    }

    UUID id = p.getUniqueId();
    PendingBrush prior = pendingBrushes.get(id);
    boolean freshBrush = prior == null || !prior.sameBlock(block);
    pendingBrushes.put(id, PendingBrush.from(block, System.currentTimeMillis() + BRUSH_FALLBACK_WINDOW_MILLIS));
    queuePending(id);

    if (freshBrush) {
      fx(block.getLocation().add(0.5, 0.9, 0.5), FxPriority.TRAIL)
          .particle(Particles.ENCHANTMENT_TABLE, 3, 0, 0, 0, 0.1, 0.01);
    }
  }

  private void registerBrushEventBridge(BrushEventBridge bridge) {
    if (bridge == null) {
      Adapt.verbose("BlockBrushEvent not available; discovery-archaeologist will use interaction fallback tracking.");
      return;
    }

    EventExecutor executor = (listener, event) -> onBrush(event, bridge);
    Bukkit.getPluginManager().registerEvent(bridge.eventClass, this, EventPriority.HIGHEST, executor, Adapt.instance, true);
  }

  private void onBrush(Event event, BrushEventBridge bridge) {
    try {
      Player p = bridge.player(event);
      Block block = bridge.block(event);
      Material originalType = block == null ? null : block.getType();
      if (p != null) {
        PendingBrush pending = pendingBrushes.get(p.getUniqueId());
        if (pending != null && isSuspiciousBlock(pending.originalType)) {
          originalType = pending.originalType;
        }
      }

      Material newStateType = bridge.newStateType(event);
      if (p != null && block != null) {
        Location blockLocation = block.getLocation();
        Material completedOriginalType = originalType;
        withPlayerThread(p, () -> handleBrush(p, blockLocation, completedOriginalType, newStateType));
      }
      if (p != null && newStateType != null && !isSuspiciousBlock(newStateType)) {
        removePending(p.getUniqueId());
      }
    } catch (Throwable t) {
      if (brushEventFailureWarned.compareAndSet(false, true)) {
        Adapt.warn("DiscoveryArchaeologist brush event bridge failed once (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ").");
      }
    }
  }

  private void handleBrush(Player p, Location blockLocation, Material originalType, Material newStateType) {
    if (p == null || blockLocation == null) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!isSuspiciousBlock(originalType)) {
      return;
    }

    // Only award when brushing actually completes and the suspicious block resolves.
    if (newStateType == null || isSuspiciousBlock(newStateType)) {
      return;
    }

    if (!canBlockBreak(p, blockLocation)) {
      return;
    }

    if (!cooldowns.isReady(p.getUniqueId(), getCooldownMillis(level))) {
      return;
    }

    cooldowns.mark(p.getUniqueId());
    Location surface = blockLocation.clone().add(0.5, 0.65, 0.5);
    if (ThreadLocalRandom.current().nextDouble() > getBonusRollChance(level)) {
      fx(surface, FxPriority.TRAIL)
          .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05, 0.01)
          .sound(Sound.BLOCK_SAND_BREAK, 0.4F, 0.8F);
      return;
    }

    boolean rare = ThreadLocalRandom.current().nextDouble() <= getRareRewardChance(level);
    ItemStack reward = rollReward(rare);
    Map<Integer, ItemStack> overflow = p.getInventory().addItem(reward);
    overflow.values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(), item));

    if (rare) {
      timeline(surface)
          .duration(10)
          .priority(FxPriority.GAMEPLAY)
          .cullRadius(32)
          .frame((f, tick, progress) -> {
            f.ring(Particle.WAX_ON, 1.2D - (1.2D * progress), 12, 0.2D);
            if (tick == 0) {
              f.chord(Sound.ITEM_BRUSH_BRUSHING_SAND_COMPLETE, 1F, 1.15F, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.7F, 0.9F, Sound.ENTITY_PLAYER_LEVELUP, 0.4F, 1.6F);
            }
          })
          .onComplete(() -> fx(surface, FxPriority.GAMEPLAY).particle(Particles.TOTEM, 14, 0, 0, 0, 0.15, 0.1))
          .start();
    } else {
      fx(surface, FxPriority.COMBAT)
          .particle(Particles.ENCHANTMENT_TABLE, 9, 0, 0, 0, 0.25, 0.2)
          .particle(Particles.CRIT_MAGIC, 6, 0, 0, 0, 0.22, 0.02)
          .chord(Sound.ITEM_BRUSH_BRUSHING_SAND_COMPLETE, 1F, 1.15F, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.55F);
    }

    xp(p, getConfig().xpPerReward + (getValue(reward.getType()) * getConfig().rewardValueXpMultiplier));
    addStat(p, "discovery.archaeologist.bonus-finds", 1);
  }

  private ItemStack rollReward(boolean rare) {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    if (rare) {
      return switch (random.nextInt(4)) {
        case 0 -> new ItemStack(Material.DIAMOND, 1);
        case 1 -> new ItemStack(Material.EMERALD, 1);
        case 2 -> new ItemStack(Material.GOLD_INGOT, 1 + random.nextInt(2));
        default ->
            new ItemStack(Material.AMETHYST_SHARD, 2 + random.nextInt(3));
      };
    }

    return switch (random.nextInt(6)) {
      case 0 -> new ItemStack(Material.BRICK, 1 + random.nextInt(2));
      case 1 -> new ItemStack(Material.CLAY_BALL, 2 + random.nextInt(3));
      case 2 -> new ItemStack(Material.BONE, 1 + random.nextInt(2));
      case 3 -> new ItemStack(Material.FLINT, 1 + random.nextInt(2));
      case 4 -> new ItemStack(Material.STRING, 1 + random.nextInt(2));
      default -> new ItemStack(Material.COAL, 1 + random.nextInt(2));
    };
  }

  private boolean isSuspiciousBlock(Material type) {
    return type == Material.SUSPICIOUS_SAND || type == Material.SUSPICIOUS_GRAVEL;
  }

  private double getBonusRollChance(int level) {
    return Math.min(getConfig().maxBonusRollChance,
        getConfig().bonusRollChanceBase + (getLevelPercent(level) * getConfig().bonusRollChanceFactor));
  }

  private double getRareRewardChance(int level) {
    return Math.min(getConfig().maxRareRewardChance,
        getConfig().rareRewardChanceBase + (getLevelPercent(level) * getConfig().rareRewardChanceFactor));
  }

  private long getCooldownMillis(int level) {
    return Math.max(250L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    int attempts = workFor(queuedPending.size(), PENDING_CHECKS_PER_TICK);
    for (int i = 0; i < attempts; i++) {
      UUID id = pendingQueue.poll();
      if (id == null) {
        break;
      }
      queuedPending.remove(id);
      PendingBrush pending = pendingBrushes.get(id);
      if (pending == null) {
        continue;
      }
      if (pending.expiresAt <= now) {
        pendingBrushes.remove(id, pending);
        continue;
      }
      if (pending.nextCheckAt > now) {
        queuePending(id);
        continue;
      }
      pending.nextCheckAt = now + FALLBACK_CHECK_INTERVAL_MILLIS;
      Location location = pending.location();
      if (location == null) {
        pendingBrushes.remove(id, pending);
        continue;
      }
      if (!J.runAt(location, () -> inspectPendingBrush(id, pending))) {
        queuePending(id);
      }
    }
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    removePending(e.getPlayer().getUniqueId());
  }

  private void inspectPendingBrush(UUID id, PendingBrush pending) {
    if (pendingBrushes.get(id) != pending) {
      return;
    }
    Block current = pending.resolveBlock();
    if (current == null) {
      pendingBrushes.remove(id, pending);
      return;
    }
    Material currentType = current.getType();
    if (isSuspiciousBlock(currentType)) {
      queuePending(id);
      return;
    }

    pendingBrushes.remove(id, pending);
    Player p = Bukkit.getPlayer(id);
    if (p == null) {
      return;
    }
    Location location = current.getLocation();
    if (!J.runEntity(p, () -> {
      if (p.isOnline() && hasActiveAdaptation(p)) {
        handleBrush(p, location, pending.originalType, currentType);
      }
    })) {
      pendingBrushes.remove(id, pending);
    }
  }

  private void queuePending(UUID id) {
    if (pendingBrushes.containsKey(id) && queuedPending.add(id)) {
      pendingQueue.add(id);
    }
  }

  private void removePending(UUID id) {
    pendingBrushes.remove(id);
    if (queuedPending.remove(id)) {
      pendingQueue.remove(id);
    }
  }

  static int workFor(int queued, int limit) {
    return Math.min(Math.max(0, queued), Math.max(0, limit));
  }

  private static final class BrushEventBridge {
    private final Class<? extends Event> eventClass;
    private final Method getPlayer;
    private final Method getBlock;
    private final Method getNewState;
    private final Method getBlockStateType;

    private BrushEventBridge(Class<? extends Event> eventClass, Method getPlayer, Method getBlock, Method getNewState, Method getBlockStateType) {
      this.eventClass = eventClass;
      this.getPlayer = getPlayer;
      this.getBlock = getBlock;
      this.getNewState = getNewState;
      this.getBlockStateType = getBlockStateType;
    }

    private static BrushEventBridge create() {
      try {
        Class<?> eventType = Class.forName(BLOCK_BRUSH_EVENT_CLASS);
        if (!Event.class.isAssignableFrom(eventType)) {
          return null;
        }

        Method getPlayer = eventType.getMethod("getPlayer");
        Method getBlock = eventType.getMethod("getBlock");
        Method getNewState = eventType.getMethod("getNewState");
        Class<?> blockStateType = Class.forName("org.bukkit.block.BlockState");
        Method getBlockStateType = blockStateType.getMethod("getType");

        @SuppressWarnings("unchecked")
        Class<? extends Event> typedEvent = (Class<? extends Event>) eventType;
        return new BrushEventBridge(typedEvent, getPlayer, getBlock, getNewState, getBlockStateType);
      } catch (Throwable ignored) {
        return null;
      }
    }

    private Player player(Event event) throws ReflectiveOperationException {
      Object value = getPlayer.invoke(event);
      return value instanceof Player p ? p : null;
    }

    private Block block(Event event) throws ReflectiveOperationException {
      Object value = getBlock.invoke(event);
      return value instanceof Block b ? b : null;
    }

    private Material newStateType(Event event) throws ReflectiveOperationException {
      Object newState = getNewState.invoke(event);
      if (newState == null) {
        return null;
      }

      Object material = getBlockStateType.invoke(newState);
      return material instanceof Material m ? m : null;
    }
  }

  private static final class PendingBrush {
    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;
    private final Material originalType;
    private final long expiresAt;
    private volatile long nextCheckAt;

    private PendingBrush(UUID worldId, int x, int y, int z, Material originalType, long expiresAt) {
      this.worldId = worldId;
      this.x = x;
      this.y = y;
      this.z = z;
      this.originalType = originalType;
      this.expiresAt = expiresAt;
      this.nextCheckAt = 0L;
    }

    private static PendingBrush from(Block block, long expiresAt) {
      return new PendingBrush(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(), block.getType(), expiresAt);
    }

    private boolean sameBlock(Block block) {
      return block.getX() == x && block.getY() == y && block.getZ() == z && block.getWorld().getUID().equals(worldId);
    }

    private Block resolveBlock() {
      World world = Bukkit.getWorld(worldId);
      return world == null ? null : world.getBlockAt(x, y, z);
    }

    private Location location() {
      World world = Bukkit.getWorld(worldId);
      return world == null ? null : new Location(world, x, y, z);
    }
  }

  @ConfigDescription("Brushing suspicious blocks has a chance to grant bonus archaeology loot.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Roll Chance Base for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusRollChanceBase = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Roll Chance Factor for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusRollChanceFactor = 0.43;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Bonus Roll Chance for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxBonusRollChance = 0.72;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Rare Reward Chance Base for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rareRewardChanceBase = 0.04;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Rare Reward Chance Factor for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rareRewardChanceFactor = 0.24;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Rare Reward Chance for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxRareRewardChance = 0.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 1600;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 1250;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Reward for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerReward = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reward Value Xp Multiplier for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rewardValueXpMultiplier = 0.45;

    public Config() {
      costFactor = 0.8;
      maxLevel = 6;
      initialCost = 4;
    }
  }
}
