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

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ArchitectMessages;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ArchitectFoundation extends SimpleAdaptation<ArchitectFoundation.Config> {
  private static final NamespacedKey JOURNAL_KEY = new NamespacedKey("adapt", "architect-foundation");
  private static final int MAX_JOURNALED_BLOCKS_PER_CHUNK = 4096;
  private static final int MAX_RECOVERY_CHUNKS_PER_TICK = 32;
  private final Map<UUID, Integer> blockPower;
  private final Map<UUID, Long> cooldowns;
  private final Map<UUID, Boolean> active;
  private final Set<Block> activeBlocks;
  private final Queue<ChunkRecovery> recoveryQueue;
  private final Set<ChunkRecovery> queuedRecoveries;
  private final AtomicBoolean recoveryScheduled;

  public ArchitectFoundation() {
    super("architect-foundation");
    registerConfiguration(ArchitectFoundation.Config.class);
    setIcon(Material.TINTED_GLASS);
    setInterval(988);
    blockPower = playerState();
    cooldowns = playerState();
    active = playerState();
    activeBlocks = ConcurrentHashMap.newKeySet();
    recoveryQueue = new ConcurrentLinkedQueue<>();
    queuedRecoveries = ConcurrentHashMap.newKeySet();
    recoveryScheduled = new AtomicBoolean();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SCAFFOLDING)
        .key("challenge_architect_foundation_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SCAFFOLDING)
            .key("challenge_architect_foundation_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_architect_foundation_1k", "architect.foundation.blocks-placed", 1000, 300);
    registerMilestone("challenge_architect_foundation_10k", "architect.foundation.blocks-placed", 10000, 1000);
  }

  @Override
  protected void onRuntimeActivated() {
    queueLoadedChunkRecovery();
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(ArchitectMessages.FOUNDATION_LORE1)
        + (getBlockPower(getLevelPercent(level))) + C.GRAY + " "
        + AdaptLanguage.text(ArchitectMessages.FOUNDATION_LORE2));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerMoveEvent e) {
    Location to = e.getTo();
    if (to == null || active.isEmpty()) {
      return;
    }

    Player p = e.getPlayer();
    if (!p.isSneaking()) {
      return;
    }

    UUID id = p.getUniqueId();
    if (!active.containsKey(id) || e.getFrom().getBlock().equals(to.getBlock())) {
      return;
    }

    withPlayerThread(p, e, () -> {
      if (!p.isSneaking() || !active.containsKey(id)) {
        return;
      }
      placeFoundation(p, to);
    });
  }

  private void placeFoundation(Player p, Location location) {
    UUID id = p.getUniqueId();
    if (getActiveBlockPlaceLevel(p, location) <= 0) {
      return;
    }
    int power = blockPower.getOrDefault(id, 0);

    if (power <= 0) {
      return;
    }

    World world = location.getWorld();
    Set<Block> locations = new HashSet<>(4);
    int y = location.getBlockY() - 1;
    int minX = (int) Math.floor(location.getX() - 0.3D);
    int maxX = (int) Math.floor(location.getX() + 0.3D);
    int minZ = (int) Math.floor(location.getZ() - 0.3D);
    int maxZ = (int) Math.floor(location.getZ() + 0.3D);
    locations.add(world.getBlockAt(minX, y, minZ));
    locations.add(world.getBlockAt(minX, y, maxZ));
    locations.add(world.getBlockAt(maxX, y, minZ));
    locations.add(world.getBlockAt(maxX, y, maxZ));

    int startPower = power;
    for (Block b : locations) {
      if (addFoundation(b)) {
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
          .dustRing(Color.fromRGB(120, 255, 200), 0.6D, 12, 0.8F)
          .sound(Sound.BLOCK_DEEPSLATE_PLACE, 0.7f, pitch);
    }

    blockPower.put(id, power);
  }

  // prevent piston from moving blocks // Dupe fix
  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockPistonExtendEvent e) {
    e.getBlocks().forEach(b -> {
      if (activeBlocks.contains(b)) {
        Adapt.verbose("Cancelled Piston Extend on Adaptation Foundation Block");
        e.setCancelled(true);
      }
    });
  }

  // prevent piston from pulling blocks // Dupe fix
  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockPistonRetractEvent e) {
    e.getBlocks().forEach(b -> {
      if (activeBlocks.contains(b)) {
        Adapt.verbose("Cancelled Piston Retract on Adaptation Foundation Block");
        e.setCancelled(true);
      }
    });
  }

  // prevent TNT from destroying blocks // Dupe fix
  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockExplodeEvent e) {
    e.blockList().removeIf(activeBlocks::contains);
  }

  // prevent block from being destroyed // Dupe fix
  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    if (activeBlocks.contains(e.getBlock())) {
      e.setCancelled(true);
    }
  }

  // prevent Entities from destroying blocks // Dupe fix
  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityExplodeEvent e) {
    e.blockList().removeIf(activeBlocks::contains);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    UUID id = p.getUniqueId();
    if (!e.isSneaking() && active.containsKey(id)) {
      withPlayerThread(p, e, () -> deactivateFoundation(p, id));
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (p.getGameMode().equals(GameMode.CREATIVE)
          || p.getGameMode().equals(GameMode.SPECTATOR)) {
        return;
      }

      boolean ready = !hasCooldown(id);
      boolean activeNow = active.containsKey(id);

      if (e.isSneaking() && ready && !activeNow) {
        active.put(id, Boolean.TRUE);
        blockPower.put(id, getBlockPower(getLevelPercent(p)));
        FxPresets.chargeRing(this, p.getLocation(), 6);
        placeFoundation(p, p.getLocation());
      }
    });
  }

  public boolean addFoundation(Block block) {
    if (!block.getType().isAir()) {
      return false;
    }

    if (!block.getWorld()
        .getNearbyEntities(block.getLocation()
            .add(.5, .5, .5), .5, .5, .5, entity ->
            entity instanceof ItemFrame || entity instanceof Painting).isEmpty()) {
      return false;
    }

    Location location = block.getLocation();
    if (!FoliaScheduler.isOwnedByCurrentRegion(location) || !journalFoundation(block)) {
      return false;
    }
    block.setType(Material.TINTED_GLASS, false);
    activeBlocks.add(block);
    int durationTicks = (int) Math.max(1L,
        Math.min(Integer.MAX_VALUE, (long) Math.ceil(getConfig().duration / 50D)));
    J.runAt(location, () -> removeFoundation(block), durationTicks);
    return true;
  }

  public void removeFoundation(Block block) {
    removeFoundation(block, true);
  }

  private void removeFoundation(Block block, boolean renderEffects) {
    activeBlocks.remove(block);
    World world = block.getWorld();
    int chunkX = block.getX() >> 4;
    int chunkZ = block.getZ() >> 4;
    if (!world.isChunkLoaded(chunkX, chunkZ)) {
      return;
    }
    boolean removed = block.getType() == Material.TINTED_GLASS;
    if (removed) {
      block.setType(Material.AIR, false);
    }
    unjournalFoundation(block);
    if (removed && renderEffects) {
      fx(block.getLocation().add(0.5, 0.5, 0.5), FxPriority.TRAIL)
          .burst(Particle.ASH, 4, 0.2D)
          .sound(Sound.BLOCK_DEEPSLATE_BREAK, 0.6f, 1.0f);
    }
  }

  public int getBlockPower(double factor) {
    return (int) Math.floor(M.lerp(getConfig().minBlocks, getConfig().maxBlocks, factor));
  }

  private void deactivateFoundation(Player player, UUID playerId) {
    if (active.remove(playerId) == null) {
      return;
    }
    blockPower.remove(playerId);
    long cooldownMillis = Math.max(0L, getConfig().cooldown);
    long deadline = M.ms() + cooldownMillis;
    if (cooldownMillis > 0L) {
      cooldowns.put(playerId, deadline);
      scheduleCooldownReady(player, playerId, deadline);
    } else {
      cooldowns.remove(playerId);
    }
    fx(player.getLocation(), FxPriority.TRANSITION)
        .dustRing(Color.GRAY, 1.0D, 16, 1.0F)
        .chord(Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 2.0f, Sound.BLOCK_SCULK_CATALYST_BREAK, 1.0f, 0.81f);
  }

  private void scheduleCooldownReady(Player player, UUID playerId, long deadline) {
    long remaining = Math.max(0L, deadline - M.ms());
    int delayTicks = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, (remaining + 49L) / 50L));
    J.runEntity(player, () -> completeCooldown(player, playerId, deadline), delayTicks);
  }

  private void completeCooldown(Player player, UUID playerId, long deadline) {
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
      fx(player.getLocation(), FxPriority.TRANSITION)
          .dustRing(Color.fromRGB(120, 255, 200), 1.2D, 16, 1.0F)
          .chord(Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.81f);
    }
  }

  private boolean journalFoundation(Block block) {
    Chunk chunk = block.getChunk();
    PersistentDataContainer data = chunk.getPersistentDataContainer();
    int encoded = encodeBlock(block.getX() & 15, block.getY(), block.getZ() & 15, block.getWorld().getMinHeight());
    int[] journal = data.get(JOURNAL_KEY, PersistentDataType.INTEGER_ARRAY);
    if (journal == null) {
      data.set(JOURNAL_KEY, PersistentDataType.INTEGER_ARRAY, new int[]{encoded});
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
    data.set(JOURNAL_KEY, PersistentDataType.INTEGER_ARRAY, expanded);
    return true;
  }

  private void unjournalFoundation(Block block) {
    Chunk chunk = block.getChunk();
    PersistentDataContainer data = chunk.getPersistentDataContainer();
    int[] journal = data.get(JOURNAL_KEY, PersistentDataType.INTEGER_ARRAY);
    if (journal == null || journal.length == 0) {
      return;
    }

    int encoded = encodeBlock(block.getX() & 15, block.getY(), block.getZ() & 15, block.getWorld().getMinHeight());
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
    data.set(JOURNAL_KEY, PersistentDataType.INTEGER_ARRAY, compacted);
  }

  private void queueLoadedChunkRecovery() {
    for (World world : Bukkit.getWorlds()) {
      for (Chunk chunk : world.getLoadedChunks()) {
        queueChunkRecovery(world, chunk.getX(), chunk.getZ());
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(ChunkLoadEvent event) {
    Chunk chunk = event.getChunk();
    queueChunkRecovery(chunk.getWorld(), chunk.getX(), chunk.getZ());
  }

  private void queueChunkRecovery(World world, int chunkX, int chunkZ) {
    if (!isRuntimeRegistered()) {
      return;
    }
    ChunkRecovery recovery = new ChunkRecovery(world, chunkX, chunkZ);
    if (!queuedRecoveries.add(recovery)) {
      return;
    }
    recoveryQueue.add(recovery);
    if (recoveryScheduled.compareAndSet(false, true)) {
      J.s(this::drainRecoveryQueue, 1);
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
      Location anchor = new Location(current.world(), (current.chunkX() << 4) + 8, current.world().getMinHeight(),
          (current.chunkZ() << 4) + 8);
      J.runAt(anchor, () -> recoverChunk(current));
      dispatched++;
    }

    recoveryScheduled.set(false);
    if (!recoveryQueue.isEmpty() && recoveryScheduled.compareAndSet(false, true)) {
      J.s(this::drainRecoveryQueue, 1);
    }
  }

  private void recoverChunk(ChunkRecovery recovery) {
    World world = recovery.world();
    if (!world.isChunkLoaded(recovery.chunkX(), recovery.chunkZ())) {
      return;
    }

    Chunk chunk = world.getChunkAt(recovery.chunkX(), recovery.chunkZ());
    PersistentDataContainer data = chunk.getPersistentDataContainer();
    int[] journal = data.get(JOURNAL_KEY, PersistentDataType.INTEGER_ARRAY);
    if (journal == null || journal.length == 0) {
      return;
    }

    int limit = Math.min(journal.length, MAX_JOURNALED_BLOCKS_PER_CHUNK);
    for (int index = 0; index < limit; index++) {
      int encoded = journal[index];
      int y = decodeY(encoded, world.getMinHeight());
      if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
        continue;
      }
      Block block = world.getBlockAt((recovery.chunkX() << 4) + decodeX(encoded), y,
          (recovery.chunkZ() << 4) + decodeZ(encoded));
      activeBlocks.remove(block);
      if (block.getType() == Material.TINTED_GLASS) {
        block.setType(Material.AIR, false);
      }
    }
    if (journal.length > limit) {
      data.set(JOURNAL_KEY, PersistentDataType.INTEGER_ARRAY, Arrays.copyOfRange(journal, limit, journal.length));
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
    for (Block block : Set.copyOf(activeBlocks)) {
      Location location = block.getLocation();
      J.runAt(location, () -> removeFoundation(block, false));
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

  @EventHandler
  public void on(PlayerQuitEvent e) {
    UUID id = e.getPlayer().getUniqueId();
    blockPower.remove(id);
    cooldowns.remove(id);
    active.remove(id);
  }

  private record ChunkRecovery(World world, int chunkX, int chunkZ) {
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
}
