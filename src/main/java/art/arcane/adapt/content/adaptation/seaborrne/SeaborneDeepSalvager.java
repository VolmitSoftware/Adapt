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

package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.ViewerDisplayDirector;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.common.world.WorldBlockScanScheduler;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class SeaborneDeepSalvager extends SimpleAdaptation<SeaborneDeepSalvager.Config> {
  private static final int HARD_MAX_RANGE = 9;
  private static final int VERTICAL_RANGE = 3;
  private static final int HARD_MAX_BLOCKS = 3200;
  private static final int MAX_SHIMMER = 6;
  private static final Set<Material> CONTAINERS = EnumSet.of(
      Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL);
  private static final BlockFace[] WATER_FACES = {
      BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.DOWN
  };
  private static final Material[] TREASURE = {
      Material.NAUTILUS_SHELL, Material.NAUTILUS_SHELL,
      Material.PRISMARINE_SHARD, Material.PRISMARINE_SHARD, Material.PRISMARINE_SHARD,
      Material.PRISMARINE_CRYSTALS, Material.PRISMARINE_CRYSTALS,
      Material.GOLD_INGOT, Material.IRON_INGOT, Material.LAPIS_LAZULI,
      Material.EMERALD, Material.INK_SAC, Material.GLOW_INK_SAC, Material.TROPICAL_FISH,
      Material.HEART_OF_THE_SEA
  };

  private final Cooldowns shimmerCooldowns = cooldowns();
  private final Map<UUID, UUID> activeScans = new ConcurrentHashMap<>();
  private volatile NamespacedKey salvagedKey;

  public SeaborneDeepSalvager() {
    super("seaborne-deep-salvager");
    registerConfiguration(Config.class);
    setLocalizationKey("seaborn.deep_salvager");
    setIcon(Material.CHEST);
    setInterval(3000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CHEST)
        .key("challenge_seaborne_salvage_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.HEART_OF_THE_SEA)
            .key("challenge_seaborne_salvage_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_seaborne_salvage_100", "seaborne.deep-salvager.containers-salvaged", 100, 400);
    registerMilestone("challenge_seaborne_salvage_1k", "seaborne.deep-salvager.containers-salvaged", 1000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getDetectionRange(level), 1), 1);
    statLore(v, getBonusRolls(level), 2);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    if (!getConfig().enableShimmer) {
      return;
    }

    long now = System.currentTimeMillis();
    for (AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      Player player = adaptPlayer.getPlayer();
      withPlayerThread(player, () -> {
        if (!player.isOnline() || !player.isInWater()) {
          return;
        }

        int level = getActiveLevel(player);
        if (level <= 0) {
          return;
        }

        if (!shimmerCooldowns.isReady(player.getUniqueId(), getConfig().shimmerScanCooldownMillis)) {
          return;
        }
        if (activeScans.containsKey(player.getUniqueId())) {
          return;
        }

        shimmerCooldowns.mark(player.getUniqueId());
        scanShimmer(player, level);
      });
    }
  }

  private void scanShimmer(Player player, int level) {
    int range = Math.min(HARD_MAX_RANGE, (int) Math.round(getDetectionRange(level)));
    Location base = player.getLocation().clone();
    if (base.getWorld() == null) {
      return;
    }
    int baseY = base.getBlockY();
    WorldBlockScanScheduler.ScanRequest request = WorldBlockScanScheduler.ScanRequest.builder(base)
        .radius(range)
        .denseRadius(range)
        .maxSamples(HARD_MAX_BLOCKS)
        .maxResults(MAX_SHIMMER)
        .blockMatcher(block -> Math.abs(block.getY() - baseY) <= VERTICAL_RANGE
            && CONTAINERS.contains(block.getType()))
        .completion(result -> completeShimmerScan(player, result))
        .build();
    UUID scanId = WorldBlockScanScheduler.submit(this, player.getUniqueId(), request);
    activeScans.put(player.getUniqueId(), scanId);
  }

  private void completeShimmerScan(Player player, WorldBlockScanScheduler.ScanResult result) {
    UUID playerId = player.getUniqueId();
    if (!result.scanId().equals(activeScans.get(playerId))) {
      return;
    }
    if (!J.runEntity(player, () -> showShimmersOwned(player, playerId, result))) {
      activeScans.remove(playerId, result.scanId());
    }
  }

  private void showShimmersOwned(Player player, UUID playerId, WorldBlockScanScheduler.ScanResult result) {
    if (!activeScans.remove(playerId, result.scanId()) || !player.isOnline() || !player.isInWater()) {
      return;
    }
    List<WorldBlockScanScheduler.Match> matches = result.matches();
    int durationTicks = Math.max(20, (int) Math.min(200L, (getConfig().shimmerScanCooldownMillis / 50L) + 20L));
    for (WorldBlockScanScheduler.Match match : matches) {
      Location location = new Location(result.world(), match.x(), match.y(), match.z());
      ViewerDisplayDirector.showBlock(
          getName(),
          "container-" + match.x() + ":" + match.y() + ":" + match.z(),
          player,
          location,
          match.material().createBlockData(),
          Color.fromRGB(70, 230, 235),
          durationTicks
      );
    }
    if (!matches.isEmpty()) {
      fx(player, FxPriority.AMBIENT).sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.25F, 1.8F);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    UUID playerId = event.getPlayer().getUniqueId();
    shimmerCooldowns.clear(playerId);
    activeScans.remove(playerId);
    WorldBlockScanScheduler.cancel(this, playerId);
    ViewerDisplayDirector.clearViewer(getName(), playerId);
  }

  @Override
  public void unregister() {
    activeScans.clear();
    WorldBlockScanScheduler.cancelOwner(this);
    ViewerDisplayDirector.clearChannel(getName());
    super.unregister();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(InventoryOpenEvent e) {
    if (!(e.getPlayer() instanceof Player p)) {
      return;
    }

    InventoryHolder holder = e.getInventory().getHolder();
    Block block = resolveContainerBlock(holder);
    if (block == null) {
      return;
    }

    Inventory inventory = e.getInventory();
    withAdaptedPlayer(p, e, () -> trySalvage(p, inventory, block));
  }

  private Block resolveContainerBlock(InventoryHolder holder) {
    if (holder instanceof Container container) {
      return container.getBlock();
    }

    if (holder instanceof DoubleChest doubleChest && doubleChest.getLeftSide() instanceof Container left) {
      return left.getBlock();
    }

    return null;
  }

  private void trySalvage(Player p, Inventory inventory, Block block) {
    int level = getActiveLevel(p);
    if (level <= 0 || !p.isInWater() || !isSubmerged(block)) {
      return;
    }

    BlockState state = block.getState();
    if (!(state instanceof TileState tile)) {
      return;
    }

    PersistentDataContainer pdc = tile.getPersistentDataContainer();
    NamespacedKey key = salvagedKey();
    if (pdc.has(key, PersistentDataType.BYTE)) {
      return;
    }

    int rolls = getBonusRolls(level);
    ThreadLocalRandom random = ThreadLocalRandom.current();
    int added = 0;
    for (int i = 0; i < rolls; i++) {
      ItemStack loot = rollTreasure(random);
      if (inventory.addItem(loot).isEmpty()) {
        added++;
      }
    }

    if (added <= 0) {
      return;
    }

    pdc.set(key, PersistentDataType.BYTE, (byte) 1);
    tile.update();
    addStat(p, "seaborne.deep-salvager.containers-salvaged", 1);
    xp(p, getConfig().salvageXp * added);
    fx(block.getLocation().add(0.5D, 1.0D, 0.5D), FxPriority.GAMEPLAY)
        .ring(Particle.GLOW, 0.6D, 12, 0.3D)
        .particle(Particle.END_ROD, 6, 0D, 0.3D, 0D, 0.35D, 0.02D)
        .chord(Sound.BLOCK_CONDUIT_ACTIVATE, 0.5F, 1.3F, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4F, 1.6F);
  }

  private static boolean isSubmerged(Block block) {
    for (BlockFace face : WATER_FACES) {
      if (block.getRelative(face).getType() == Material.WATER) {
        return true;
      }
    }

    return false;
  }

  private ItemStack rollTreasure(ThreadLocalRandom random) {
    Material material = TREASURE[random.nextInt(TREASURE.length)];
    int amount = material == Material.HEART_OF_THE_SEA ? 1 : 1 + random.nextInt(3);
    return new ItemStack(material, amount);
  }

  private NamespacedKey salvagedKey() {
    NamespacedKey local = salvagedKey;
    if (local == null) {
      local = new NamespacedKey(Adapt.instance, "seaborne_salvaged");
      salvagedKey = local;
    }

    return local;
  }

  private double getDetectionRange(int level) {
    return detectionRange(getConfig().detectionRangeBase, getConfig().detectionRangeFactor, getLevelPercent(level));
  }

  private int getBonusRolls(int level) {
    return bonusRolls(getConfig().bonusRollsBase, getConfig().bonusRollsFactor, getLevelPercent(level));
  }

  static double detectionRange(double base, double factor, double levelPercent) {
    return base + (levelPercent * factor);
  }

  static int bonusRolls(int base, double factor, double levelPercent) {
    return Math.max(1, base + (int) Math.round(levelPercent * factor));
  }

  @ConfigDescription("Underwater containers privately glow within range and reward bonus treasure the first time you open them submerged.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base block range that underwater containers shimmer within.", impact = "Higher values reveal containers from farther away at low levels.")
    double detectionRangeBase = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional shimmer detection range gained across levels.", impact = "Higher values greatly extend detection range at higher levels.")
    double detectionRangeFactor = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base number of bonus treasure rolls added when salvaging a container.", impact = "Higher values grant more bonus loot at low levels.")
    int bonusRollsBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional bonus treasure rolls gained across levels.", impact = "Higher values grant far more bonus loot at higher levels.")
    double bonusRollsFactor = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bonus XP granted per bonus treasure item salvaged.", impact = "Higher values reward salvaging with more skill XP.")
    double salvageXp = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown between underwater container shimmer scans per player.", impact = "Higher values reduce scan frequency and scanning cost.")
    long shimmerScanCooldownMillis = 3000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables the underwater container shimmer locator scan.", impact = "Set to false to disable shimmer scanning entirely while keeping bonus loot.")
    boolean enableShimmer = true;

    public Config() {
      baseCost = 4;
      costFactor = 0.6;
      maxLevel = 4;
      initialCost = 4;
    }
  }
}
