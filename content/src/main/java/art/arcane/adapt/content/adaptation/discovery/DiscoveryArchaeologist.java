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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBrushEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

public class DiscoveryArchaeologist extends SimpleAdaptation<DiscoveryArchaeologist.Config> {
  public static final Id ID = Id.adapt("discovery/archaeologist");
  private static final Id BRUSH = Id.minecraft("brush");
  private static final Id SUSPICIOUS_SAND = Id.minecraft("suspicious_sand");
  private static final Id SUSPICIOUS_GRAVEL = Id.minecraft("suspicious_gravel");
  private static final long BRUSH_FALLBACK_WINDOW_MILLIS = 25000L;
  private static final long FALLBACK_CHECK_INTERVAL_MILLIS = 2300L;
  private static final int PENDING_CHECKS_PER_TICK = 16;
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, PendingBrush> pendingBrushes = playerState();
  private final Queue<UUID> pendingQueue = new ConcurrentLinkedQueue<>();
  private final Set<UUID> queuedPending = ConcurrentHashMap.newKeySet();

  public DiscoveryArchaeologist() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("brush"));
    setInterval(10);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("brush"))
        .key("challenge_discovery_archaeologist_50")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("decorated_pot"))
            .key("challenge_discovery_archaeologist_500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_discovery_archaeologist_50", "discovery.archaeologist.bonus-finds", 50, 300);
    registerMilestone("challenge_discovery_archaeologist_500", "discovery.archaeologist.bonus-finds", 500, 1000);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getBonusRollChance(level), 0), 1);
    statLore(v, Format.pc(getRareRewardChance(level), 0), 2);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getCooldownMillis(level), 1), 3);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    if (e.action() != InteractAction.RIGHT_CLICK_BLOCK) {
      return;
    }

    ItemRef hand = e.item();
    if (hand == null || !BRUSH.equals(hand.type())) {
      return;
    }

    if (e.hand() == Hand.OFF_HAND && BRUSH.equals(e.player().inventory().mainHand().type())) {
      return;
    }

    BlockRef block = e.clickedBlock();
    if (block == null || !isSuspiciousBlock(block.type())) {
      return;
    }

    PlayerRef p = e.player();
    if (getActiveBlockBreakLevel(p, block.location()) <= 0) {
      return;
    }

    UUID id = p.uuid();
    PendingBrush prior = pendingBrushes.get(id);
    boolean freshBrush = prior == null || !prior.sameBlock(block);
    pendingBrushes.put(id, PendingBrush.from(block, System.currentTimeMillis() + BRUSH_FALLBACK_WINDOW_MILLIS));
    queuePending(id);

    if (freshBrush) {
      fx(block.location().add(0.5, 0.9, 0.5), FxPriority.TRAIL)
          .particle(Particles.ENCHANT, 3, 0, 0, 0, 0.1, 0.01);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBrushEvent e) {
    PlayerRef p = e.player();
    BlockRef block = e.block();
    Id originalType = block.type();
    PendingBrush pending = pendingBrushes.get(p.uuid());
    if (pending != null && isSuspiciousBlock(pending.originalType)) {
      originalType = pending.originalType;
    }
    Id newStateType = e.newState() == null ? null : e.newState().type();
    Loc blockLocation = block.location();
    Id completedOriginalType = originalType;
    withPlayerThread(p, () -> handleBrush(p, blockLocation, completedOriginalType, newStateType));
    if (newStateType != null && !isSuspiciousBlock(newStateType)) {
      removePending(p.uuid());
    }
  }

  private void handleBrush(PlayerRef p, Loc blockLocation, Id originalType, Id newStateType) {
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

    long cooldownMillis = getCooldownMillis(level);
    if (!cooldowns.isReady(p.uuid(), cooldownMillis)) {
      return;
    }

    cooldowns.mark(p.uuid(), cooldownMillis);
    Loc surface = blockLocation.add(0.5, 0.65, 0.5);
    if (ThreadLocalRandom.current().nextDouble() > getBonusRollChance(level)) {
      fx(surface, FxPriority.TRAIL)
          .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05, 0.01)
          .sound(Sounds.BLOCK_SAND_BREAK, 0.4F, 0.8F);
      return;
    }

    boolean rare = ThreadLocalRandom.current().nextDouble() <= getRareRewardChance(level);
    ItemRef reward = rollReward(rare);
    List<ItemRef> overflow = p.inventory().addItem(reward.copy());
    overflow.forEach(item -> p.world().dropItemNaturally(p.location().position(), item));

    if (rare) {
      timeline(surface)
          .duration(10)
          .priority(FxPriority.GAMEPLAY)
          .cullRadius(32)
          .frame((f, tick, progress) -> {
            f.ring(Particles.WAX_ON, 1.2D - (1.2D * progress), 12, 0.2D);
            if (tick == 0) {
              f.chord(Sounds.ITEM_BRUSH_BRUSHING_SAND_COMPLETE, 1F, 1.15F, Sounds.BLOCK_AMETHYST_BLOCK_RESONATE, 0.7F, 0.9F, Sounds.ENTITY_PLAYER_LEVELUP, 0.4F, 1.6F);
            }
          })
          .onComplete(() -> fx(surface, FxPriority.GAMEPLAY).particle(Particles.TOTEM, 14, 0, 0, 0, 0.15, 0.1))
          .start();
    } else {
      fx(surface, FxPriority.COMBAT)
          .particle(Particles.ENCHANT, 9, 0, 0, 0, 0.25, 0.2)
          .particle(Particles.CRIT, 6, 0, 0, 0, 0.22, 0.02)
          .chord(Sounds.ITEM_BRUSH_BRUSHING_SAND_COMPLETE, 1F, 1.15F, Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.55F);
    }

    xp(p, getConfig().xpPerReward + (getValue(reward.type()) * getConfig().rewardValueXpMultiplier));
    addStat(p, "discovery.archaeologist.bonus-finds", 1);
  }

  private ItemRef rollReward(boolean rare) {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    if (rare) {
      return switch (random.nextInt(4)) {
        case 0 -> item("diamond", 1);
        case 1 -> item("emerald", 1);
        case 2 -> item("gold_ingot", 1 + random.nextInt(2));
        default ->
            item("amethyst_shard", 2 + random.nextInt(3));
      };
    }

    return switch (random.nextInt(6)) {
      case 0 -> item("brick", 1 + random.nextInt(2));
      case 1 -> item("clay_ball", 2 + random.nextInt(3));
      case 2 -> item("bone", 1 + random.nextInt(2));
      case 3 -> item("flint", 1 + random.nextInt(2));
      case 4 -> item("string", 1 + random.nextInt(2));
      default -> item("coal", 1 + random.nextInt(2));
    };
  }

  private static ItemRef item(String type, int amount) {
    return AdaptApi.platform().host().items().create(Id.minecraft(type), amount);
  }

  static boolean isSuspiciousBlock(Id type) {
    return SUSPICIOUS_SAND.equals(type) || SUSPICIOUS_GRAVEL.equals(type);
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
  public boolean hasTickDemand() {
    return !pendingBrushes.isEmpty();
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
      Loc location = pending.location();
      if (location == null) {
        pendingBrushes.remove(id, pending);
        continue;
      }
      if (!AdaptApi.scheduler().runAt(location, () -> inspectPendingBrush(id, pending))) {
        queuePending(id);
      }
    }
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    removePending(e.player().uuid());
  }

  private void inspectPendingBrush(UUID id, PendingBrush pending) {
    if (pendingBrushes.get(id) != pending) {
      return;
    }
    BlockRef current = pending.resolveBlock();
    if (current == null) {
      pendingBrushes.remove(id, pending);
      return;
    }
    Id currentType = current.type();
    if (isSuspiciousBlock(currentType)) {
      queuePending(id);
      return;
    }

    pendingBrushes.remove(id, pending);
    PlayerRef p = AdaptApi.platform().host().player(id);
    if (p == null) {
      return;
    }
    Loc location = current.location();
    if (!AdaptApi.scheduler().runEntity(p, () -> {
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

  private static final class PendingBrush {
    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;
    private final Id originalType;
    private final long expiresAt;
    private volatile long nextCheckAt;

    private PendingBrush(UUID worldId, int x, int y, int z, Id originalType, long expiresAt) {
      this.worldId = worldId;
      this.x = x;
      this.y = y;
      this.z = z;
      this.originalType = originalType;
      this.expiresAt = expiresAt;
      this.nextCheckAt = 0L;
    }

    private static PendingBrush from(BlockRef block, long expiresAt) {
      return new PendingBrush(block.world().uid(), block.x(), block.y(), block.z(), block.type(), expiresAt);
    }

    private boolean sameBlock(BlockRef block) {
      return block.x() == x && block.y() == y && block.z() == z && block.world().uid().equals(worldId);
    }

    private BlockRef resolveBlock() {
      WorldRef world = host().world(worldId);
      return world == null ? null : world.blockAt(x, y, z);
    }

    private Loc location() {
      WorldRef world = host().world(worldId);
      return world == null ? null : new Loc(world, x, y, z);
    }

    private static PlatformHost host() {
      return AdaptApi.platform().host();
    }
  }

  @ConfigDescription("Brushing suspicious blocks has a chance to grant bonus archaeology loot.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Bonus Roll Chance Base for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusRollChanceBase = 0.12;
    @ConfigDoc(value = "Controls Bonus Roll Chance Factor for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusRollChanceFactor = 0.43;
    @ConfigDoc(value = "Controls Max Bonus Roll Chance for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxBonusRollChance = 0.72;
    @ConfigDoc(value = "Controls Rare Reward Chance Base for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rareRewardChanceBase = 0.04;
    @ConfigDoc(value = "Controls Rare Reward Chance Factor for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rareRewardChanceFactor = 0.24;
    @ConfigDoc(value = "Controls Max Rare Reward Chance for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxRareRewardChance = 0.3;
    @ConfigDoc(value = "Controls Cooldown Millis Base for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 1600;
    @ConfigDoc(value = "Controls Cooldown Millis Factor for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 1250;
    @ConfigDoc(value = "Controls Xp Per Reward for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerReward = 10;
    @ConfigDoc(value = "Controls Reward Value Xp Multiplier for the Discovery Archaeologist adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rewardValueXpMultiplier = 0.45;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 6;
      initialCost = 1;
    }
  }
}
