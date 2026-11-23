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

import art.arcane.adapt.AdaptCore;
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
import art.arcane.adapt.content.item.BoundSnowBall;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RangedMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockExplodeEvent;
import art.arcane.adapt.platform.event.block.BlockPistonEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.event.entity.EntityRemoveEvent;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.world.ChunkEvent;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;

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
  private static final DataKey<long[]> JOURNAL_KEY = DataKey.adapt("ranged-web-bomb", DataCodecs.LONG_ARRAY);
  private static final int MAX_JOURNALED_WEBS_PER_CHUNK = 4096;
  private static final int MAX_RECOVERY_CHUNKS_PER_TICK = 32;
  private static final Id COBWEB = Id.minecraft("cobweb");
  private static final Id AIR = Id.minecraft("air");
  private final Map<UUID, UUID> activeSnowballs;
  private final Map<WebKey, ActiveWeb> activeBlocks;
  private final Queue<ChunkRecovery> recoveryQueue;
  private final Set<ChunkRecovery> queuedRecoveries;
  private final AtomicBoolean recoveryScheduled;

  public RangedWebBomb() {
    super("ranged-webshot");
    registerConfiguration(Config.class);
    setLocalizationKey("ranged.web_shot");
    setIcon(COBWEB);
    setInterval(4900);
    registerRecipe(new RecipeSpec.Shaped(Id.adapt("ranged-web-bomb"),
        List.of(
            "III",
            "ISI",
            "III"),
        Map.of('I', Ingredient.of(COBWEB), 'S', Ingredient.of(Id.minecraft("snowball"))),
        BoundSnowBall.io.withData(new BoundSnowBall.Data(null))));
    activeBlocks = new ConcurrentHashMap<>();
    activeSnowballs = new ConcurrentHashMap<>();
    recoveryQueue = new ConcurrentLinkedQueue<>();
    queuedRecoveries = ConcurrentHashMap.newKeySet();
    recoveryScheduled = new AtomicBoolean();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("cobweb"))
        .key("challenge_ranged_web_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_ranged_web_200", "ranged.web-bomb.mobs-trapped", 200, 300);
  }

  @Override
  protected void onRuntimeActivated() {
    queueLoadedChunkRecovery();
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerInteractEvent event) {
    if (event.action().isRightClick()
        && event.item() != null && BoundSnowBall.isBindableItem(event.item())
        && preferenceEnabled(event.player(), RangedPreferences.SNEAK) && !event.player().isSneaking()) {
      event.setCancelled(true);
    }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, RangedPreferences.SNEAK);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(RangedMessages.WEB_SHOT_LORE1));
    statLore(v, C.YELLOW, "+ ", level, 2);
  }


  @Handle(priority = EventPriority.NORMAL, ignoreCancelled = false)
  public void on(ProjectileHitEvent e) {
    ProjectileRef snowball = e.projectile();
    if (!snowball.type().equals(RangedProjectiles.SNOWBALL)) {
      return;
    }
    UUID shooterId = activeSnowballs.remove(snowball.uuid());
    if (shooterId == null) {
      return;
    }
    WebImpact impact = captureImpact(e);
    snowball.remove();
    if (impact == null) {
      return;
    }
    PlayerRef p = AdaptCore.platform().host().player(shooterId);
    if (p == null) {
      return;
    }
    Tasks.runEntity(p, () -> authorizeImpact(p, impact));
  }

  private void authorizeImpact(PlayerRef p, WebImpact impact) {
    if (!p.isOnline()) {
      return;
    }
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    List<WebPlacement> placements = placementCandidates(impact.world(), impact.x(), impact.y(), impact.z());
    if (Tasks.isFoliaThreading()) {
      for (WebPlacement placement : placements) {
        if (!Tasks.isOwnedByCurrentRegion(placement.location())) {
          return;
        }
      }
    }

    Loc center = impact.center();
    Tasks.runAt(center, () -> showImpactFx(center, impact.hitEntity()));

    long durationTicks = Math.max(1L, (long) level * 20L);
    long expiresAt = System.currentTimeMillis() + (durationTicks * 50L);
    AtomicBoolean trapCredited = new AtomicBoolean();
    for (WebPlacement placement : placements) {
      scheduleAuthorizedWeb(p, placement, expiresAt, impact.hitEntity(), trapCredited);
    }

    Tasks.runAt(center, () -> fx(center, FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 6, 0.3D)
        .sound(Sounds.BLOCK_ROOTED_DIRT_BREAK, 0.7F, 1.0F),
        delayTicksUntil(expiresAt, System.currentTimeMillis()));
  }

  private WebImpact captureImpact(ProjectileHitEvent event) {
    Loc location;
    boolean hitEntity = event.hitEntity() != null;
    if (hitEntity) {
      location = event.hitEntity().location();
    } else if (event.hitBlock() != null) {
      location = event.hitBlock().location();
    } else {
      location = event.projectile().location();
    }
    WorldRef world = location.world();
    return world == null
        ? null
        : new WebImpact(world, location.blockX(), location.blockY() + 1, location.blockZ(), hitEntity);
  }

  private void showImpactFx(Loc center, boolean hitEntity) {
    FxPresets.chargeRing(this, center, 8);
    fx(center, FxPriority.GAMEPLAY)
        .burst(Particles.WHITE_ASH, 12, 0.4D)
        .burst(Particles.CLOUD, 6, 0.3D)
        .chord(Sounds.BLOCK_WOOL_PLACE, 0.9F, 0.7F, Sounds.BLOCK_ROOTED_DIRT_PLACE, 0.7F, 0.9F);
    if (hitEntity) {
      fx(center, FxPriority.COMBAT)
          .particle(Particles.CRIT_MAGIC, 6, 0, 0.5D, 0, 0.3D, 0.0D)
          .particle(Particles.ENCHANTMENT_TABLE, 8, 0, 0.6D, 0, 0.35D, 0.0D)
          .sound(Sounds.BLOCK_WOOL_PLACE, 0.5F, 1.3F);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ProjectileLaunchEvent e) {
    ProjectileRef snowball = e.projectile();
    if (snowball.shooter() instanceof PlayerRef p && snowball.type().equals(RangedProjectiles.SNOWBALL) && hasActiveAdaptation(p)) {
      if (preferenceEnabled(p, RangedPreferences.SNEAK) && !p.isSneaking()) {
        return;
      }
      if (BoundSnowBall.isBindableItem(snowball.item())) {
        activeSnowballs.put(snowball.uuid(), p.uuid());
      }
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityRemoveEvent e) {
    if (e.entity().type().equals(RangedProjectiles.SNOWBALL)) {
      activeSnowballs.remove(e.entity().uuid());
    }
  }

  private void scheduleAuthorizedWeb(PlayerRef player, WebPlacement placement, long expiresAt,
                                     boolean hitEntity, AtomicBoolean trapCredited) {
    Loc location = placement.location();
    Tasks.runAt(location, () -> {
      if (!isRuntimeRegistered()
          || (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(player))
          || !player.isOnline()
          || !canBlockPlace(player, location)) {
        return;
      }
      BlockRef block = location.world().blockAt(location);
      if (!block.isAir()
          || !ProtectionProbes.attemptBlockPlaceProbe(player, block)
          || !block.isAir()) {
        return;
      }
      if (!journalWeb(block, expiresAt)) {
        return;
      }
      block.setType(COBWEB, false);
      WebKey key = webKey(block);
      activeBlocks.put(key, new ActiveWeb(block, expiresAt));
      scheduleWebRemoval(block, expiresAt);
      if (hitEntity && trapCredited.compareAndSet(false, true)
          && !Tasks.runEntity(player, () -> addStat(player, "ranged.web-bomb.mobs-trapped", 1))) {
        trapCredited.set(false);
      }
    });
  }

  private void scheduleWebRemoval(BlockRef block, long expiresAt) {
    Tasks.runAt(block.location(), () -> removeFoundation(block, expiresAt), delayTicksUntil(expiresAt, System.currentTimeMillis()));
  }

  private void removeFoundation(BlockRef block, long expiresAt) {
    WorldRef world = block.world();
    int chunkX = block.x() >> 4;
    int chunkZ = block.z() >> 4;
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
    if (block.type().equals(COBWEB)) {
      block.setType(AIR, false);
    }
    unjournalWeb(block, expiresAt);
  }

  private static DataHolder chunkData(BlockRef block) {
    return block.world().chunkData(block.x() >> 4, block.z() >> 4);
  }

  private boolean journalWeb(BlockRef block, long expiresAt) {
    DataHolder data = chunkData(block);
    long encoded = encodeBlock(block.x() & 15, block.y(), block.z() & 15,
        block.world().minHeight());
    long[] journal = data.get(JOURNAL_KEY);
    long[] updated = upsertJournalEntry(journal, encoded, expiresAt, MAX_JOURNALED_WEBS_PER_CHUNK);
    if (updated == null) {
      return false;
    }
    data.set(JOURNAL_KEY, updated);
    return true;
  }

  private void unjournalWeb(BlockRef block, long expiresAt) {
    DataHolder data = chunkData(block);
    long[] journal = data.get(JOURNAL_KEY);
    long encoded = encodeBlock(block.x() & 15, block.y(), block.z() & 15,
        block.world().minHeight());
    long[] updated = removeJournalEntry(journal, encoded, expiresAt);
    if (updated.length == 0) {
      data.remove(JOURNAL_KEY);
    } else if (!Arrays.equals(journal, updated)) {
      data.set(JOURNAL_KEY, updated);
    }
  }

  private boolean journalMatches(BlockRef block, long expiresAt) {
    Long journalExpiry = journalExpiry(block, chunkData(block));
    return journalExpiry != null && journalExpiry == expiresAt;
  }

  private boolean isActiveWeb(BlockRef block) {
    WebKey key = webKey(block);
    if (activeBlocks.containsKey(key)) {
      return true;
    }
    return block.type().equals(COBWEB)
        && journalExpiry(block, chunkData(block)) != null;
  }

  private Long journalExpiry(BlockRef block, DataHolder data) {
    long[] journal = data.get(JOURNAL_KEY);
    long encoded = encodeBlock(block.x() & 15, block.y(), block.z() & 15,
        block.world().minHeight());
    return journalExpiry(journal, encoded);
  }

  private WebKey webKey(BlockRef block) {
    return new WebKey(block.world().uid(), block.x(), block.y(), block.z());
  }

  private void queueLoadedChunkRecovery() {
    for (WorldRef world : AdaptCore.platform().host().worlds()) {
      for (ChunkPos chunk : world.loadedChunks()) {
        queueChunkRecovery(world, chunk.x(), chunk.z());
      }
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
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
      queuedRecoveries.clear();
      recoveryScheduled.set(false);
      return;
    }

    int dispatched = 0;
    ChunkRecovery recovery;
    while (dispatched < MAX_RECOVERY_CHUNKS_PER_TICK && (recovery = recoveryQueue.poll()) != null) {
      ChunkRecovery current = recovery;
      queuedRecoveries.remove(current);
      Loc anchor = new Loc(current.world(), (current.chunkX() << 4) + 8,
          current.world().minHeight(), (current.chunkZ() << 4) + 8);
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
    long[] journal = data.get(JOURNAL_KEY);
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
      int y = decodeY(encoded, world.minHeight());
      if (y < world.minHeight() || y >= world.maxHeight()) {
        continue;
      }
      BlockRef block = world.blockAt((recovery.chunkX() << 4) + decodeX(encoded), y,
          (recovery.chunkZ() << 4) + decodeZ(encoded));
      if (!block.type().equals(COBWEB)) {
        activeBlocks.remove(webKey(block));
        continue;
      }
      if (expiresAt <= now) {
        activeBlocks.remove(webKey(block));
        block.setType(AIR, false);
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
      data.set(JOURNAL_KEY, Arrays.copyOf(retained, retainedLength));
    }
  }

  //prevent piston from moving blocks // Dupe fix
  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(BlockPistonEvent e) {
    e.blocks().forEach(b -> {
      if (isActiveWeb(b)) {
        e.setCancelled(true);
      }
    });
  }

  //prevent TNT from destroying blocks // Dupe fix
  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(BlockExplodeEvent e) {
    protectActiveWebs(e.blocks(), this::isActiveWeb);
  }

  //prevent block from being destroyed // Dupe fix
  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(BlockBreakEvent e) {
    if (isActiveWeb(e.block())) {
      e.setCancelled(true);
    }
  }

  //prevent Entities from destroying blocks // Dupe fix
  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(EntityExplodeEvent e) {
    protectActiveWebs(e.blocks(), this::isActiveWeb);
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
      Tasks.runAt(activeWeb.block().location(),
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

  static void protectActiveWebs(List<BlockRef> blocks, Predicate<BlockRef> activeWeb) {
    blocks.removeIf(activeWeb);
  }

  static List<WebPlacement> placementCandidates(WorldRef world, int x, int y, int z) {
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

  private record ActiveWeb(BlockRef block, long expiresAt) {
  }

  private record ChunkRecovery(WorldRef world, int chunkX, int chunkZ) {
  }

  private record WebImpact(WorldRef world, int x, int y, int z, boolean hitEntity) {
    private Loc center() {
      return new Loc(world, x + 0.5D, y + 0.5D, z + 0.5D);
    }
  }

  record WebPlacement(WorldRef world, int x, int y, int z) {
    Loc location() {
      return new Loc(world, x, y, z);
    }
  }


  @ConfigDescription("Throw a crafted web snare to trap targets in cobwebs.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
