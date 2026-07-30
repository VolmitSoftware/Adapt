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

package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RangedMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.recipe.MaterialChar;
import art.arcane.adapt.content.item.BoundSnowBall;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public class RangedWebBomb extends SimpleAdaptation<RangedWebBomb.Config> {
  private static final NamespacedKey JOURNAL_KEY = new NamespacedKey("adapt", "ranged-web-bomb");
  private static final int MAX_JOURNALED_WEBS_PER_CHUNK = 4096;
  private static final int MAX_RECOVERY_CHUNKS_PER_TICK = 32;
  private final Map<UUID, UUID> activeSnowballs;
  private final Map<WebKey, ActiveWeb> activeBlocks;
  private final Queue<ChunkRecovery> recoveryQueue;
  private final Set<ChunkRecovery> queuedRecoveries;
  private final AtomicBoolean recoveryScheduled;

  public RangedWebBomb() {
    super("ranged-webshot");
    registerConfiguration(Config.class);
    setLocalizationKey("ranged.web_shot");
    setIcon(Material.COBWEB);
    setInterval(4900);
    registerRecipe(AdaptRecipe.shaped()
        .key("ranged-web-bomb")
        .ingredient(new MaterialChar('I', Material.COBWEB))
        .ingredient(new MaterialChar('S', Material.SNOWBALL))
        .shapes(List.of(
            "III",
            "ISI",
            "III"))
        .result(BoundSnowBall.io.withData(new BoundSnowBall.Data(null)))
        .build());
    activeBlocks = new ConcurrentHashMap<>();
    activeSnowballs = new ConcurrentHashMap<>();
    recoveryQueue = new ConcurrentLinkedQueue<>();
    queuedRecoveries = ConcurrentHashMap.newKeySet();
    recoveryScheduled = new AtomicBoolean();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.COBWEB)
        .key("challenge_ranged_web_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_ranged_web_200", "ranged.web-bomb.mobs-trapped", 200, 300);
  }

  @Override
  protected void onRuntimeActivated() {
    queueLoadedChunkRecovery();
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(RangedMessages.WEB_SHOT_LORE1));
    statLore(v, C.YELLOW, "+ ", level, 2);
  }


  @EventHandler
  public void on(ProjectileHitEvent e) {
    if (!(e.getEntity() instanceof Snowball snowball)) {
      return;
    }
    UUID shooterId = activeSnowballs.remove(snowball.getUniqueId());
    if (shooterId == null) {
      return;
    }
    WebImpact impact = captureImpact(e);
    snowball.remove();
    if (impact == null) {
      return;
    }
    Player p = Bukkit.getPlayer(shooterId);
    if (p == null) {
      return;
    }
    J.runEntity(p, () -> authorizeImpact(p, impact));
  }

  private void authorizeImpact(Player p, WebImpact impact) {
    if (!p.isOnline()) {
      return;
    }
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    List<WebPlacement> authorized = new ArrayList<>(7);
    for (WebPlacement placement : placementCandidates(impact.world(), impact.x(), impact.y(), impact.z())) {
      if (canBlockPlace(p, placement.location())) {
        authorized.add(placement);
      }
    }

    Location center = impact.center();
    J.runAt(center, () -> showImpactFx(center, impact.hitEntity()));
    if (impact.hitEntity()) {
      addStat(p, "ranged.web-bomb.mobs-trapped", 1);
    }

    long durationTicks = Math.max(1L, (long) level * 20L);
    long expiresAt = System.currentTimeMillis() + (durationTicks * 50L);
    for (WebPlacement placement : authorized) {
      scheduleAuthorizedWeb(placement, expiresAt);
    }

    J.runAt(center, () -> fx(center, FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 6, 0.3D)
        .sound(Sound.BLOCK_ROOTED_DIRT_BREAK, 0.7F, 1.0F),
        delayTicksUntil(expiresAt, System.currentTimeMillis()));
  }

  private WebImpact captureImpact(ProjectileHitEvent event) {
    Location location;
    boolean hitEntity = event.getHitEntity() != null;
    if (hitEntity) {
      location = event.getHitEntity().getLocation();
    } else if (event.getHitBlock() != null) {
      location = event.getHitBlock().getLocation();
    } else {
      location = event.getEntity().getLocation();
    }
    World world = location.getWorld();
    return world == null
        ? null
        : new WebImpact(world, location.getBlockX(), location.getBlockY() + 1, location.getBlockZ(), hitEntity);
  }

  private void showImpactFx(Location center, boolean hitEntity) {
    FxPresets.chargeRing(this, center, 8);
    fx(center, FxPriority.GAMEPLAY)
        .burst(Particle.WHITE_ASH, 12, 0.4D)
        .burst(Particle.CLOUD, 6, 0.3D)
        .chord(Sound.BLOCK_WOOL_PLACE, 0.9F, 0.7F, Sound.BLOCK_ROOTED_DIRT_PLACE, 0.7F, 0.9F);
    if (hitEntity) {
      fx(center, FxPriority.COMBAT)
          .particle(Particles.CRIT_MAGIC, 6, 0, 0.5D, 0, 0.3D, 0.0D)
          .particle(Particles.ENCHANTMENT_TABLE, 8, 0, 0.6D, 0, 0.35D, 0.0D)
          .sound(Sound.BLOCK_WOOL_PLACE, 0.5F, 1.3F);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(ProjectileLaunchEvent e) {
    if (e.getEntity().getShooter() instanceof Player p && e.getEntity() instanceof Snowball snowball && hasActiveAdaptation(p)) {
      if (BoundSnowBall.isBindableItem(snowball.getItem())) {
        activeSnowballs.put(snowball.getUniqueId(), p.getUniqueId());
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityRemoveEvent e) {
    if (e.getEntity() instanceof Snowball) {
      activeSnowballs.remove(e.getEntity().getUniqueId());
    }
  }

  private void scheduleAuthorizedWeb(WebPlacement placement, long expiresAt) {
    Location location = placement.location();
    J.runAt(location, () -> {
      if (!isRuntimeRegistered()) {
        return;
      }
      Block block = location.getBlock();
      if (!block.getType().isAir()) {
        return;
      }
      if (!journalWeb(block, expiresAt)) {
        return;
      }
      block.setType(Material.COBWEB, false);
      WebKey key = webKey(block);
      activeBlocks.put(key, new ActiveWeb(block, expiresAt));
      scheduleWebRemoval(block, expiresAt);
    });
  }

  private void scheduleWebRemoval(Block block, long expiresAt) {
    J.runAt(block.getLocation(), () -> removeFoundation(block, expiresAt), delayTicksUntil(expiresAt, System.currentTimeMillis()));
  }

  private void removeFoundation(Block block, long expiresAt) {
    World world = block.getWorld();
    int chunkX = block.getX() >> 4;
    int chunkZ = block.getZ() >> 4;
    if (!world.isChunkLoaded(chunkX, chunkZ)) {
      WebKey unloadedKey = webKey(block);
      activeBlocks.computeIfPresent(unloadedKey,
          (key, activeWeb) -> activeWeb.expiresAt() == expiresAt ? null : activeWeb);
      return;
    }

    WebKey key = webKey(block);
    ActiveWeb current = activeBlocks.get(key);
    if (current != null && current.expiresAt() != expiresAt) {
      return;
    }
    if (current == null && !journalMatches(block, expiresAt)) {
      return;
    }

    if (current != null) {
      activeBlocks.remove(key, current);
    }
    if (block.getType() == Material.COBWEB) {
      block.setType(Material.AIR, false);
    }
    unjournalWeb(block, expiresAt);
  }

  private boolean journalWeb(Block block, long expiresAt) {
    Chunk chunk = block.getChunk();
    PersistentDataContainer data = chunk.getPersistentDataContainer();
    long encoded = encodeBlock(block.getX() & 15, block.getY(), block.getZ() & 15,
        block.getWorld().getMinHeight());
    long[] journal = data.get(JOURNAL_KEY, PersistentDataType.LONG_ARRAY);
    long[] updated = upsertJournalEntry(journal, encoded, expiresAt, MAX_JOURNALED_WEBS_PER_CHUNK);
    if (updated == null) {
      return false;
    }
    data.set(JOURNAL_KEY, PersistentDataType.LONG_ARRAY, updated);
    return true;
  }

  private void unjournalWeb(Block block, long expiresAt) {
    PersistentDataContainer data = block.getChunk().getPersistentDataContainer();
    long[] journal = data.get(JOURNAL_KEY, PersistentDataType.LONG_ARRAY);
    long encoded = encodeBlock(block.getX() & 15, block.getY(), block.getZ() & 15,
        block.getWorld().getMinHeight());
    long[] updated = removeJournalEntry(journal, encoded, expiresAt);
    if (updated.length == 0) {
      data.remove(JOURNAL_KEY);
    } else if (!Arrays.equals(journal, updated)) {
      data.set(JOURNAL_KEY, PersistentDataType.LONG_ARRAY, updated);
    }
  }

  private boolean journalMatches(Block block, long expiresAt) {
    Long journalExpiry = journalExpiry(block, block.getChunk().getPersistentDataContainer());
    return journalExpiry != null && journalExpiry == expiresAt;
  }

  private boolean isActiveWeb(Block block) {
    WebKey key = webKey(block);
    if (activeBlocks.containsKey(key)) {
      return true;
    }
    return block.getType() == Material.COBWEB
        && journalExpiry(block, block.getChunk().getPersistentDataContainer()) != null;
  }

  private Long journalExpiry(Block block, PersistentDataContainer data) {
    long[] journal = data.get(JOURNAL_KEY, PersistentDataType.LONG_ARRAY);
    long encoded = encodeBlock(block.getX() & 15, block.getY(), block.getZ() & 15,
        block.getWorld().getMinHeight());
    return journalExpiry(journal, encoded);
  }

  private WebKey webKey(Block block) {
    return new WebKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
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
      queuedRecoveries.clear();
      recoveryScheduled.set(false);
      return;
    }

    int dispatched = 0;
    ChunkRecovery recovery;
    while (dispatched < MAX_RECOVERY_CHUNKS_PER_TICK && (recovery = recoveryQueue.poll()) != null) {
      ChunkRecovery current = recovery;
      queuedRecoveries.remove(current);
      Location anchor = new Location(current.world(), (current.chunkX() << 4) + 8,
          current.world().getMinHeight(), (current.chunkZ() << 4) + 8);
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
    long[] journal = data.get(JOURNAL_KEY, PersistentDataType.LONG_ARRAY);
    if (journal == null || journal.length < 2) {
      data.remove(JOURNAL_KEY);
      return;
    }

    int pairCount = journal.length / 2;
    long[] retained = new long[journal.length];
    int retainedLength = 0;
    long now = System.currentTimeMillis();
    for (int index = 0; index < pairCount; index++) {
      long encoded = journal[index * 2];
      long expiresAt = journal[(index * 2) + 1];
      int y = decodeY(encoded, world.getMinHeight());
      if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
        continue;
      }
      Block block = world.getBlockAt((recovery.chunkX() << 4) + decodeX(encoded), y,
          (recovery.chunkZ() << 4) + decodeZ(encoded));
      if (block.getType() != Material.COBWEB) {
        activeBlocks.remove(webKey(block));
        continue;
      }
      if (expiresAt <= now) {
        activeBlocks.remove(webKey(block));
        block.setType(Material.AIR, false);
        continue;
      }
      retained[retainedLength++] = encoded;
      retained[retainedLength++] = expiresAt;
      activeBlocks.put(webKey(block), new ActiveWeb(block, expiresAt));
      scheduleWebRemoval(block, expiresAt);
    }
    if (retainedLength == 0) {
      data.remove(JOURNAL_KEY);
    } else {
      data.set(JOURNAL_KEY, PersistentDataType.LONG_ARRAY, Arrays.copyOf(retained, retainedLength));
    }
  }

  //prevent piston from moving blocks // Dupe fix
  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockPistonExtendEvent e) {
    e.getBlocks().forEach(b -> {
      if (isActiveWeb(b)) {
        e.setCancelled(true);
      }
    });
  }

  //prevent piston from pulling blocks // Dupe fix
  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockPistonRetractEvent e) {
    e.getBlocks().forEach(b -> {
      if (isActiveWeb(b)) {
        e.setCancelled(true);
      }
    });
  }

  //prevent TNT from destroying blocks // Dupe fix
  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockExplodeEvent e) {
    protectActiveWebs(e.blockList(), this::isActiveWeb);
  }

  //prevent block from being destroyed // Dupe fix
  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    if (isActiveWeb(e.getBlock())) {
      e.setCancelled(true);
    }
  }

  //prevent Entities from destroying blocks // Dupe fix
  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityExplodeEvent e) {
    protectActiveWebs(e.blockList(), this::isActiveWeb);
  }

  @Override
  public void unregister() {
    super.unregister();
    recoveryQueue.clear();
    queuedRecoveries.clear();
    recoveryScheduled.set(false);
    activeSnowballs.clear();
    List<ActiveWeb> activeWebs = List.copyOf(activeBlocks.values());
    activeBlocks.clear();
    for (ActiveWeb activeWeb : activeWebs) {
      J.runAt(activeWeb.block().getLocation(),
          () -> removeFoundation(activeWeb.block(), activeWeb.expiresAt()));
    }
  }

  static int delayTicksUntil(long expiresAt, long now) {
    long remainingMillis = Math.max(1L, expiresAt - now);
    return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, (remainingMillis + 49L) / 50L));
  }

  static long encodeBlock(int localX, int y, int localZ, int minHeight) {
    return ((long) (y - minHeight) << 8) | ((long) (localZ & 15) << 4) | (localX & 15);
  }

  static int decodeX(long encoded) {
    return (int) encoded & 15;
  }

  static int decodeZ(long encoded) {
    return ((int) encoded >>> 4) & 15;
  }

  static int decodeY(long encoded, int minHeight) {
    return minHeight + (int) (encoded >>> 8);
  }

  static long[] upsertJournalEntry(long[] journal, long encoded, long expiresAt, int maxEntries) {
    long[] normalized = journal == null ? new long[0] : Arrays.copyOf(journal, journal.length & ~1);
    for (int index = 0; index < normalized.length; index += 2) {
      if (normalized[index] == encoded) {
        normalized[index + 1] = expiresAt;
        return normalized;
      }
    }
    if (normalized.length / 2 >= maxEntries) {
      return null;
    }
    long[] expanded = Arrays.copyOf(normalized, normalized.length + 2);
    expanded[normalized.length] = encoded;
    expanded[normalized.length + 1] = expiresAt;
    return expanded;
  }

  static long[] removeJournalEntry(long[] journal, long encoded, long expiresAt) {
    if (journal == null || journal.length < 2) {
      return new long[0];
    }
    int normalizedLength = journal.length & ~1;
    for (int index = 0; index < normalizedLength; index += 2) {
      if (journal[index] != encoded || journal[index + 1] != expiresAt) {
        continue;
      }
      long[] compacted = new long[normalizedLength - 2];
      System.arraycopy(journal, 0, compacted, 0, index);
      System.arraycopy(journal, index + 2, compacted, index, normalizedLength - index - 2);
      return compacted;
    }
    return Arrays.copyOf(journal, normalizedLength);
  }

  static Long journalExpiry(long[] journal, long encoded) {
    if (journal == null) {
      return null;
    }
    for (int index = 0; index + 1 < journal.length; index += 2) {
      if (journal[index] == encoded) {
        return journal[index + 1];
      }
    }
    return null;
  }

  static void protectActiveWebs(List<Block> blocks, Predicate<Block> activeWeb) {
    blocks.removeIf(activeWeb);
  }

  static List<WebPlacement> placementCandidates(World world, int x, int y, int z) {
    return List.of(
        new WebPlacement(world, x, y, z),
        new WebPlacement(world, x, y + 1, z),
        new WebPlacement(world, x, y - 1, z),
        new WebPlacement(world, x, y, z + 1),
        new WebPlacement(world, x, y, z - 1),
        new WebPlacement(world, x + 1, y, z),
        new WebPlacement(world, x - 1, y, z)
    );
  }

  private record WebKey(UUID worldId, int x, int y, int z) {
  }

  private record ActiveWeb(Block block, long expiresAt) {
  }

  private record ChunkRecovery(World world, int chunkX, int chunkZ) {
  }

  private record WebImpact(World world, int x, int y, int z, boolean hitEntity) {
    private Location center() {
      return new Location(world, x + 0.5D, y + 0.5D, z + 0.5D);
    }
  }

  record WebPlacement(World world, int x, int y, int z) {
    Location location() {
      return new Location(world, x, y, z);
    }
  }


  @ConfigDescription("Throw a crafted web snare to trap targets in cobwebs.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 5;
      costFactor = 0.9;
      initialCost = 1;
    }
  }
}
