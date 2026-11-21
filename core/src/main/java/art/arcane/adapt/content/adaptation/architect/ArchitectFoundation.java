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

package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ArchitectMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockExplodeEvent;
import art.arcane.adapt.platform.event.block.BlockPistonEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.event.world.ChunkEvent;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.math.M;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ArchitectFoundation extends SimpleAdaptation<ArchitectFoundation.Config> {
  public static final PlayerPreference<Control> CONTROL = new PlayerPreference<>(Control.class,
      new PlayerPreference.Definition<>("control", ArchitectMessages.ARCHITECTFOUNDATION_PREFERENCE_CONTROL, Control.HOLD, List.of(
          new PlayerPreference.Choice<>(Control.HOLD, ArchitectMessages.ARCHITECTFOUNDATION_PREFERENCE_CONTROL_HOLD, Id.minecraft("leather_boots"), 1),
          new PlayerPreference.Choice<>(Control.LATCH, ArchitectMessages.ARCHITECTFOUNDATION_PREFERENCE_CONTROL_LATCH, Id.minecraft("lever"), 1))));
  public static final PlayerPreference<CommonPreferences.Toggle> EMPTY_HAND = CommonPreferences.toggle("empty-hand", ArchitectMessages.ARCHITECTFOUNDATION_PREFERENCE_EMPTY_HAND, CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Scale> DURATION = CommonPreferences.scale("duration", ArchitectMessages.ARCHITECTFOUNDATION_PREFERENCE_DURATION);

  private static final DataKey<int[]> JOURNAL_KEY = DataKey.adapt("architect-foundation", DataCodecs.INT_ARRAY);
  private static final Id TINTED_GLASS = Id.minecraft("tinted_glass");
  private static final Id AIR = Id.minecraft("air");
  private static final int MAX_JOURNALED_BLOCKS_PER_CHUNK = 4096;
  private static final int MAX_RECOVERY_CHUNKS_PER_TICK = 32;
  private final Map<UUID, Integer> blockPower;
  private final Map<UUID, Long> cooldowns;
  private final Map<UUID, Boolean> active;
  private final Set<BlockKey> activeBlocks;
  private final Queue<ChunkRecovery> recoveryQueue;
  private final Set<ChunkRecovery> queuedRecoveries;
  private final AtomicBoolean recoveryScheduled;

  public ArchitectFoundation() {
    super("architect-foundation");
    registerConfiguration(ArchitectFoundation.Config.class);
    setIcon(TINTED_GLASS);
    setInterval(988);
    blockPower = playerState();
    cooldowns = playerState();
    active = playerState();
    activeBlocks = ConcurrentHashMap.newKeySet();
    recoveryQueue = new ConcurrentLinkedQueue<>();
    queuedRecoveries = ConcurrentHashMap.newKeySet();
    recoveryScheduled = new AtomicBoolean();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("scaffolding"))
        .key("challenge_architect_foundation_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("scaffolding"))
            .key("challenge_architect_foundation_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_architect_foundation_1k", "architect.foundation.blocks-placed", 1000, 300);
    registerMilestone("challenge_architect_foundation_10k", "architect.foundation.blocks-placed", 10000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, EMPTY_HAND, DURATION, CONTROL);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    deactivateFoundation(player.getPlayer(), player.getPlayer().uuid());
  }

  @Override
  protected void onRuntimeActivated() {
    queueLoadedChunkRecovery();
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(ArchitectMessages.FOUNDATION_LORE1)
        + (getBlockPower(getLevelPercent(level))) + C.GRAY + " "
        + AdaptLanguage.text(ArchitectMessages.FOUNDATION_LORE2));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerMoveEvent e) {
    Loc to = e.to();
    if (to == null || active.isEmpty()) {
      return;
    }

    PlayerRef p = e.player();
    if (!p.isSneaking() && preference(p, CONTROL) == Control.HOLD) {
      return;
    }

    UUID id = p.uuid();
    if (!active.containsKey(id) || (e.from().sameWorld(to) && e.from().blockPos().equals(to.blockPos()))) {
      return;
    }

    withPlayerThread(p, e, () -> {
      if ((!p.isSneaking() && preference(p, CONTROL) == Control.HOLD) || !active.containsKey(id) || !isPlayerEnabled(p)) {
        return;
      }
      placeFoundation(p, to);
    });
  }

  private void placeFoundation(PlayerRef p, Loc location) {
    if (location.world() == null
        || (Tasks.isFoliaThreading()
        && (!Tasks.isOwnedByCurrentRegion(p) || !Tasks.isOwnedByCurrentRegion(location)))) {
      return;
    }

    UUID id = p.uuid();
    if (getActiveBlockPlaceLevel(p, location) <= 0) {
      return;
    }
    int power = blockPower.getOrDefault(id, 0);

    if (power <= 0) {
      return;
    }

    WorldRef world = location.world();
    Set<BlockRef> locations = new LinkedHashSet<>(4);
    int y = location.blockY() - 1;
    int minX = (int) Math.floor(location.x() - 0.3D);
    int maxX = (int) Math.floor(location.x() + 0.3D);
    int minZ = (int) Math.floor(location.z() - 0.3D);
    int maxZ = (int) Math.floor(location.z() + 0.3D);
    addOwnedFoundationTarget(locations, world, minX, y, minZ);
    addOwnedFoundationTarget(locations, world, minX, y, maxZ);
    addOwnedFoundationTarget(locations, world, maxX, y, minZ);
    addOwnedFoundationTarget(locations, world, maxX, y, maxZ);

    int startPower = power;
    for (BlockRef b : locations) {
      if (addFoundation(p, b)) {
        power--;
        addStat(p, "architect.foundation.blocks-placed", 1);
      }

      if (power <= 0) {
        break;
      }
    }

    if (power != startPower) {
      float pitch = (float) (0.8D + (0.8D * (power / (double) Math.max(1, getConfig().maxBlocks))));
      fx(location, FxPriority.TRAIL)
          .dustRing(new Rgb(120, 255, 200), 0.6D, 12, 0.8F)
          .sound(Sounds.BLOCK_DEEPSLATE_PLACE, 0.7f, pitch);
    }

    blockPower.put(id, power);
    if (power != startPower) {
      AbilityGlyphs.charges(id, this, power, getBlockPower(getLevelPercent(p)));
    }
  }

  private void addOwnedFoundationTarget(Set<BlockRef> targets, WorldRef world, int x, int y, int z) {
    Loc target = new Loc(world, x, y, z);
    if (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(target)) {
      return;
    }
    targets.add(world.blockAt(x, y, z));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockPistonEvent e) {
    e.blocks().forEach(b -> {
      if (activeBlocks.contains(BlockKey.of(b))) {
        AdaptLog.verbose(e.extending()
            ? "Cancelled Piston Extend on Adaptation Foundation Block"
            : "Cancelled Piston Retract on Adaptation Foundation Block");
        e.setCancelled(true);
      }
    });
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockExplodeEvent e) {
    e.blocks().removeIf(block -> activeBlocks.contains(BlockKey.of(block)));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    if (activeBlocks.contains(BlockKey.of(e.block()))) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityExplodeEvent e) {
    e.blocks().removeIf(block -> activeBlocks.contains(BlockKey.of(block)));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    UUID id = p.uuid();
    if (active.containsKey(id) && (preference(p, CONTROL) == Control.LATCH ? e.isSneaking() : !e.isSneaking())) {
      withPlayerThread(p, e, () -> deactivateFoundation(p, id));
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (p.gameMode() == GameMode.CREATIVE
          || p.gameMode() == GameMode.SPECTATOR) {
        return;
      }

      boolean ready = !hasCooldown(id);
      boolean activeNow = active.containsKey(id);

      if (e.isSneaking() && ready && !activeNow
          && (!preferenceEnabled(p, EMPTY_HAND) || p.inventory().mainHand().type().isAir())) {
        active.put(id, Boolean.TRUE);
        int power = getBlockPower(getLevelPercent(p));
        blockPower.put(id, power);
        AbilityGlyphs.charges(id, this, power, power);
        FxPresets.chargeRing(this, p.location(), 6);
        placeFoundation(p, p.location());
      }
    });
  }

  public boolean addFoundation(PlayerRef player, BlockRef block) {
    Loc location = block.location();
    if (!Tasks.isOwnedByCurrentRegion(location)
        || (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(player))) {
      return false;
    }
    if (!block.type().isAir()) {
      return false;
    }

    Loc center = location.add(0.5D, 0.5D, 0.5D);
    if ((Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(center, 0.5D, 0.5D))
        || hasHangingDecoration(block.world().nearbyEntities(center.position(), .5, .5, .5))) {
      return false;
    }

    if (!canBlockPlace(player, location)
        || !ProtectionProbes.attemptBlockPlaceProbe(player, block)) {
      return false;
    }
    if (!Tasks.isOwnedByCurrentRegion(location)
        || (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(player))
        || !block.type().isAir()
        || !journalFoundation(block)) {
      return false;
    }
    BlockKey key = BlockKey.of(block);
    boolean cleanupScheduled = false;
    try {
      block.setType(TINTED_GLASS, false);
      activeBlocks.add(key);
      int durationTicks = (int) Math.max(1L,
          Math.min(Integer.MAX_VALUE, (long) Math.ceil(getConfig().duration * preference(player, DURATION).multiplier() / 50D)));
      cleanupScheduled = Tasks.runAt(location, () -> removeFoundation(block), durationTicks);
      return cleanupScheduled;
    } finally {
      if (!cleanupScheduled) {
        activeBlocks.remove(key);
        if (TINTED_GLASS.equals(block.type())) {
          block.setType(AIR, false);
        }
        unjournalFoundation(block);
      }
    }
  }

  private static boolean hasHangingDecoration(List<EntityRef> entities) {
    for (EntityRef entity : entities) {
      if (entity.has(EntityTrait.ITEM_FRAME) || entity.has(EntityTrait.PAINTING)) {
        return true;
      }
    }
    return false;
  }

  public void removeFoundation(BlockRef block) {
    removeFoundation(block, true);
  }

  private void removeFoundation(BlockRef block, boolean renderEffects) {
    activeBlocks.remove(BlockKey.of(block));
    WorldRef world = block.world();
    int chunkX = block.x() >> 4;
    int chunkZ = block.z() >> 4;
    if (!world.isChunkLoaded(chunkX, chunkZ)) {
      return;
    }
    boolean removed = TINTED_GLASS.equals(block.type());
    if (removed) {
      block.setType(AIR, false);
    }
    unjournalFoundation(block);
    if (removed && renderEffects) {
      fx(block.location().add(0.5, 0.5, 0.5), FxPriority.TRAIL)
          .burst(Particles.ASH, 4, 0.2D)
          .sound(Sounds.BLOCK_DEEPSLATE_BREAK, 0.6f, 1.0f);
    }
  }

  public int getBlockPower(double factor) {
    return (int) Math.floor(M.lerp(getConfig().minBlocks, getConfig().maxBlocks, factor));
  }

  private void deactivateFoundation(PlayerRef player, UUID playerId) {
    if (active.remove(playerId) == null) {
      return;
    }
    blockPower.remove(playerId);
    AbilityGlyphs.clear(playerId, this, HudGlyph.CHARGES);
    long cooldownMillis = Math.max(0L, getConfig().cooldown);
    long now = M.ms();
    long deadline = now + cooldownMillis;
    if (cooldownMillis > 0L) {
      cooldowns.put(playerId, deadline);
      AbilityGlyphs.cooldown(playerId, this, now, cooldownMillis);
      scheduleCooldownReady(player, playerId, deadline);
    } else {
      cooldowns.remove(playerId);
    }
    fx(player.location(), FxPriority.TRANSITION)
        .dustRing(new Rgb(128, 128, 128), 1.0D, 16, 1.0F)
        .chord(Sounds.BLOCK_BEACON_DEACTIVATE, 1.0f, 2.0f, Sounds.BLOCK_SCULK_CATALYST_BREAK, 1.0f, 0.81f);
  }

  private void scheduleCooldownReady(PlayerRef player, UUID playerId, long deadline) {
    long remaining = Math.max(0L, deadline - M.ms());
    int delayTicks = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, (remaining + 49L) / 50L));
    Tasks.runEntity(player, () -> completeCooldown(player, playerId, deadline), delayTicks);
  }

  private void completeCooldown(PlayerRef player, UUID playerId, long deadline) {
    if (!isRuntimeRegistered()) {
      return;
    }
    Long current = cooldowns.get(playerId);
    if (current == null || current != deadline) {
      return;
    }
    if (M.ms() < deadline) {
      scheduleCooldownReady(player, playerId, deadline);
      return;
    }

    cooldowns.remove(playerId, deadline);
    if (player.isOnline() && hasActiveAdaptation(player)) {
      fx(player.location(), FxPriority.TRANSITION)
          .dustRing(new Rgb(120, 255, 200), 1.2D, 16, 1.0F)
          .chord(Sounds.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f, Sounds.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.81f);
    }
  }

  private boolean journalFoundation(BlockRef block) {
    DataHolder data = block.world().chunkData(block.x() >> 4, block.z() >> 4);
    int encoded = encodeBlock(block.x() & 15, block.y(), block.z() & 15, block.world().minHeight());
    int[] journal = data.get(JOURNAL_KEY);
    if (journal == null) {
      data.set(JOURNAL_KEY, new int[]{encoded});
      return true;
    }
    for (int existing : journal) {
      if (existing == encoded) {
        return true;
      }
    }
    if (journal.length >= MAX_JOURNALED_BLOCKS_PER_CHUNK) {
      return false;
    }
    int[] expanded = Arrays.copyOf(journal, journal.length + 1);
    expanded[journal.length] = encoded;
    data.set(JOURNAL_KEY, expanded);
    return true;
  }

  private void unjournalFoundation(BlockRef block) {
    DataHolder data = block.world().chunkData(block.x() >> 4, block.z() >> 4);
    int[] journal = data.get(JOURNAL_KEY);
    if (journal == null || journal.length == 0) {
      return;
    }

    int encoded = encodeBlock(block.x() & 15, block.y(), block.z() & 15, block.world().minHeight());
    int found = -1;
    for (int index = 0; index < journal.length; index++) {
      if (journal[index] == encoded) {
        found = index;
        break;
      }
    }
    if (found < 0) {
      return;
    }
    if (journal.length == 1) {
      data.remove(JOURNAL_KEY);
      return;
    }

    int[] compacted = new int[journal.length - 1];
    System.arraycopy(journal, 0, compacted, 0, found);
    System.arraycopy(journal, found + 1, compacted, found, journal.length - found - 1);
    data.set(JOURNAL_KEY, compacted);
  }

  private void queueLoadedChunkRecovery() {
    for (WorldRef world : AdaptCore.platform().host().worlds()) {
      for (ChunkPos chunk : world.loadedChunks()) {
        queueChunkRecovery(world, chunk.x(), chunk.z());
      }
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ChunkEvent event) {
    if (event.phase() != ChunkEvent.Phase.LOAD) {
      return;
    }
    queueChunkRecovery(event.world(), event.chunk().x(), event.chunk().z());
  }

  private void queueChunkRecovery(WorldRef world, int chunkX, int chunkZ) {
    if (!isRuntimeRegistered()) {
      return;
    }
    ChunkRecovery recovery = new ChunkRecovery(world, chunkX, chunkZ);
    if (!queuedRecoveries.add(recovery)) {
      return;
    }
    recoveryQueue.add(recovery);
    if (recoveryScheduled.compareAndSet(false, true)) {
      Tasks.s(this::drainRecoveryQueue, 1);
    }
  }

  private void drainRecoveryQueue() {
    if (!isRuntimeRegistered()) {
      recoveryQueue.clear();
      recoveryScheduled.set(false);
      return;
    }

    int dispatched = 0;
    ChunkRecovery recovery;
    while (dispatched < MAX_RECOVERY_CHUNKS_PER_TICK && (recovery = recoveryQueue.poll()) != null) {
      ChunkRecovery current = recovery;
      queuedRecoveries.remove(current);
      Loc anchor = new Loc(current.world(), (current.chunkX() << 4) + 8, current.world().minHeight(),
          (current.chunkZ() << 4) + 8);
      Tasks.runAt(anchor, () -> recoverChunk(current));
      dispatched++;
    }

    recoveryScheduled.set(false);
    if (!recoveryQueue.isEmpty() && recoveryScheduled.compareAndSet(false, true)) {
      Tasks.s(this::drainRecoveryQueue, 1);
    }
  }

  private void recoverChunk(ChunkRecovery recovery) {
    WorldRef world = recovery.world();
    if (!world.isChunkLoaded(recovery.chunkX(), recovery.chunkZ())) {
      return;
    }

    DataHolder data = world.chunkData(recovery.chunkX(), recovery.chunkZ());
    int[] journal = data.get(JOURNAL_KEY);
    if (journal == null || journal.length == 0) {
      return;
    }

    int limit = Math.min(journal.length, MAX_JOURNALED_BLOCKS_PER_CHUNK);
    for (int index = 0; index < limit; index++) {
      int encoded = journal[index];
      int y = decodeY(encoded, world.minHeight());
      if (y < world.minHeight() || y >= world.maxHeight()) {
        continue;
      }
      BlockRef block = world.blockAt((recovery.chunkX() << 4) + decodeX(encoded), y,
          (recovery.chunkZ() << 4) + decodeZ(encoded));
      activeBlocks.remove(BlockKey.of(block));
      if (TINTED_GLASS.equals(block.type())) {
        block.setType(AIR, false);
      }
    }
    if (journal.length > limit) {
      data.set(JOURNAL_KEY, Arrays.copyOfRange(journal, limit, journal.length));
      queueChunkRecovery(world, recovery.chunkX(), recovery.chunkZ());
    } else {
      data.remove(JOURNAL_KEY);
    }
  }

  static int encodeBlock(int localX, int y, int localZ, int minHeight) {
    return ((y - minHeight) << 8) | ((localZ & 15) << 4) | (localX & 15);
  }

  static int decodeX(int encoded) {
    return encoded & 15;
  }

  static int decodeZ(int encoded) {
    return (encoded >>> 4) & 15;
  }

  static int decodeY(int encoded, int minHeight) {
    return minHeight + (encoded >>> 8);
  }

  @Override
  public void unregister() {
    super.unregister();
    recoveryQueue.clear();
    queuedRecoveries.clear();
    recoveryScheduled.set(false);
    for (BlockKey key : Set.copyOf(activeBlocks)) {
      WorldRef world = AdaptCore.platform().host().world(key.worldId());
      if (world == null) {
        continue;
      }
      BlockRef block = world.blockAt(key.pos());
      Tasks.runAt(block.location(), () -> removeFoundation(block, false));
    }
    activeBlocks.clear();
    active.clear();
    blockPower.clear();
    cooldowns.clear();
  }

  private boolean hasCooldown(UUID id) {
    Long cooldown = cooldowns.get(id);
    if (cooldown != null && M.ms() >= cooldown) {
      cooldowns.remove(id);
      cooldown = null;
    }

    return cooldown != null;
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    UUID id = e.player().uuid();
    blockPower.remove(id);
    cooldowns.remove(id);
    active.remove(id);
  }

  private record ChunkRecovery(WorldRef world, int chunkX, int chunkZ) {
  }

  private record BlockKey(UUID worldId, BlockPos pos) {
    private static BlockKey of(BlockRef block) {
      return new BlockKey(block.world().uid(), block.pos());
    }
  }

  @ConfigDescription("Sneak to place a temporary foundation beneath you.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Duration for the Architect Foundation adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public long duration = 3000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Blocks for the Architect Foundation adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public int minBlocks = 9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Blocks for the Architect Foundation adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public int maxBlocks = 35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown for the Architect Foundation adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public int cooldown = 5000;

    public Config() {
      baseCost = 5;
      costFactor = 0.40;
      initialCost = 1;
    }
  }

  public enum Control { HOLD, LATCH }
}
