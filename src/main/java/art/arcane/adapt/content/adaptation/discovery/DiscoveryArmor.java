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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.math.Sphere;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.common.world.LoadedChunkAccess;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class DiscoveryArmor extends SimpleAdaptation<DiscoveryArmor.Config> {
  private static final UUID MODIFIER = UUID.nameUUIDFromBytes("adapt-discovery-armor".getBytes());
  private static final NamespacedKey MODIFIER_KEY = NamespacedKey.fromString("adapt:discovery-armor");
  private static final long UPDATE_COOLDOWN = TimeUnit.SECONDS.toMillis(3);
  private static final List<BlockPosition> ARMOR_OFFSETS = createArmorOffsets();

  private final double[] materialArmorPoints = createMaterialArmorPoints();
  private final Cooldowns updateThrottle = cooldowns();
  private final Map<UUID, Boolean> appliedPlayers = new ConcurrentHashMap<>();
  private final Map<UUID, Long> bonusStatAt = playerState();
  private final AtomicBoolean lifecycleCleanupStarted = new AtomicBoolean();
  private Iterator<UUID> cleanupCursor = Map.<UUID, Boolean>of().keySet().iterator();
  private int playerCursor;

  public DiscoveryArmor() {
    super("discovery-world-armor");
    registerConfiguration(Config.class);
    setLocalizationKey("discovery.armor");
    setIcon(Material.TURTLE_HELMET);
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_CHESTPLATE)
        .key("challenge_discovery_armor_1hr")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_discovery_armor_1hr", "discovery.armor.ticks-with-bonus", 72000, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Localizer.dLocalize("discovery.armor.lore1") + C.GRAY + ", " + Localizer.dLocalize("discovery.armor.lore2"));
    v.addLore(C.YELLOW + "~ " + Localizer.dLocalize("discovery.armor.lore3") + C.BLUE + " +" + level * 0.25);
  }

  public double getArmorPoints(Material m) {
    return materialArmorPoints[m.ordinal()];
  }

  @Override
  public void unregister() {
    if (lifecycleCleanupStarted.compareAndSet(false, true)) {
      bonusStatAt.clear();
      cleanupArmorBatch(appliedPlayers.keySet().iterator());
    }
    super.unregister();
  }

  public double getArmor(Location l, int level) {
    World world = l.getWorld();
    if (world == null) {
      return 0D;
    }

    int centerX = l.getBlockX();
    int centerY = l.getBlockY();
    int centerZ = l.getBlockZ();
    int minY = world.getMinHeight();
    int maxY = world.getMaxHeight() - 1;
    LoadedChunkAccess chunkAccess = new LoadedChunkAccess(world, 5);
    double armorValue = 0.0;
    int count = 0;

    for (BlockPosition offset : ARMOR_OFFSETS) {
      int blockX = centerX + offset.getX();
      int blockY = centerY + offset.getY();
      int blockZ = centerZ + offset.getZ();
      if (blockY < minY || blockY > maxY || !chunkAccess.canRead(blockX, blockZ)) {
        continue;
      }

      Block block = world.getBlockAt(blockX, blockY, blockZ);
      Material material = block.getType();
      if (material.isAir() || block.isLiquid()) {
        continue;
      }

      count++;
      armorValue += materialArmorPoints[material.ordinal()];
    }

    return scaleArmor(armorValue, count, level);
  }

  static int scanBlockCount() {
    return ARMOR_OFFSETS.size();
  }

  static double computeArmorPoints(Material material) {
    if (!material.isBlock()) {
      return 0D;
    }
    return computeArmorPoints(material.getBlastResistance(), material.getHardness());
  }

  static double computeArmorPoints(double blastResistance, double hardness) {
    double points = Math.log(Math.min(2000D, blastResistance * blastResistance))
        + Math.log((hardness < 0D ? 50D : Math.min(50D, hardness + 25D)) * 0.33D);
    return Double.isFinite(points) && points > 0D ? points : 0D;
  }

  static double scaleArmor(double armorValue, int count, int level) {
    if (count <= 0 || armorValue <= 0D || level <= 0) {
      return 0D;
    }

    return Math.min((armorValue / count) * (level / 2D) * 0.65D, 10D);
  }

  static double elapsedActiveTicks(Long previous, long now) {
    if (previous == null || now <= previous) {
      return 0D;
    }
    return (now - previous) / 50D;
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    List<AdaptPlayer> candidates = learnedCandidates(now);
    int size = candidates.size();
    if (size == 0) {
      playerCursor = 0;
    } else {
      int limit = Math.max(1, Math.min(size, getConfig().maxPlayersPerPass));
      int start = Math.floorMod(playerCursor, size);
      for (int i = 0; i < limit; i++) {
        queueArmorUpdate(candidates.get((start + i) % size));
      }
      playerCursor = (start + limit) % size;
    }

    cleanupInactivePlayers();
  }

  @EventHandler
  public void on(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    withPlayerThread(player, () -> removeInactiveModifier(player));
  }

  @EventHandler
  public void on(PlayerQuitEvent event) {
    removeArmorModifier(event.getPlayer());
    updateThrottle.clear(event.getPlayer().getUniqueId());
  }

  private void queueArmorUpdate(AdaptPlayer adaptPlayer) {
    Player player = adaptPlayer.getPlayer();
    if (player == null || !player.isOnline()) {
      return;
    }

    UUID playerId = player.getUniqueId();
    if (!updateThrottle.isReady(playerId, UPDATE_COOLDOWN)) {
      return;
    }
    updateThrottle.mark(playerId);
    withPlayerThread(player, () -> updateArmor(adaptPlayer, player));
  }

  private void updateArmor(AdaptPlayer adaptPlayer, Player player) {
    IAttribute attribute = Version.get().getAttribute(player, Attributes.GENERIC_ARMOR);
    if (attribute == null) {
      return;
    }

    if (!hasActiveAdaptation(player)) {
      attribute.removeModifier(MODIFIER, MODIFIER_KEY);
      appliedPlayers.remove(player.getUniqueId());
      bonusStatAt.remove(player.getUniqueId());
      return;
    }

    double oldArmor = 0D;
    for (IAttribute.Modifier modifier : attribute.getModifier(MODIFIER, MODIFIER_KEY)) {
      double amount = modifier.getAmount();
      if (Double.isFinite(amount) && amount > oldArmor) {
        oldArmor = amount;
      }
    }

    double armor = getArmor(player.getLocation(), getLevel(player));
    double lerpedArmor = M.lerp(oldArmor, armor, 0.3D);
    lerpedArmor = Double.isFinite(lerpedArmor) ? lerpedArmor : 0D;
    attribute.setModifier(MODIFIER, MODIFIER_KEY, lerpedArmor, AttributeModifier.Operation.ADD_NUMBER);
    appliedPlayers.put(player.getUniqueId(), true);
    if (lerpedArmor > 0D) {
      double activeTicks = activeTicksSinceLastUpdate(player.getUniqueId());
      if (activeTicks > 0D) {
        adaptPlayer.getData().addStat("discovery.armor.ticks-with-bonus", activeTicks);
      }
    } else {
      bonusStatAt.remove(player.getUniqueId());
    }

    if (Math.round(lerpedArmor) != Math.round(oldArmor) && Math.round(lerpedArmor) > 0) {
      fx(player.getLocation(), FxPriority.TRANSITION)
          .dustRing(Color.fromRGB(150, 170, 190), 0.8D, 16, 1.1F)
          .chord(Sound.BLOCK_STONE_PLACE, 0.5F, 0.7F, Sound.BLOCK_AMETHYST_BLOCK_STEP, 0.3F, 1.2F);
    } else if (lerpedArmor > 6D && ThreadLocalRandom.current().nextInt(20) == 0) {
      fx(player.getLocation(), FxPriority.AMBIENT)
          .particle(Particles.CRIT_MAGIC, 2, 0, 0, 0, 0.05D, 0.01D);
    }
  }

  private void cleanupInactivePlayers() {
    if (!cleanupCursor.hasNext()) {
      cleanupCursor = appliedPlayers.keySet().iterator();
    }

    int limit = Math.max(1, getConfig().maxPlayersPerPass);
    int examined = 0;
    while (examined < limit && cleanupCursor.hasNext()) {
      UUID playerId = cleanupCursor.next();
      examined++;
      Player player = Bukkit.getPlayer(playerId);
      if (player == null || !player.isOnline()) {
        appliedPlayers.remove(playerId);
        continue;
      }

      AdaptPlayer adaptPlayer = getPlayer(player);
      if (getLevel(adaptPlayer) > 0) {
        continue;
      }
      withPlayerThread(player, () -> removeInactiveModifier(player));
    }
  }

  private void removeInactiveModifier(Player player) {
    if (hasActiveAdaptation(player)) {
      return;
    }

    removeArmorModifier(player);
  }

  private void removeArmorModifier(Player player) {
    IAttribute attribute = Version.get().getAttribute(player, Attributes.GENERIC_ARMOR);
    if (attribute != null) {
      attribute.removeModifier(MODIFIER, MODIFIER_KEY);
    }
    appliedPlayers.remove(player.getUniqueId());
    bonusStatAt.remove(player.getUniqueId());
  }

  private void cleanupArmorBatch(Iterator<UUID> cursor) {
    int limit = Math.max(1, getConfig().maxPlayersPerPass);
    int processed = 0;
    while (processed < limit && cursor.hasNext()) {
      UUID playerId = cursor.next();
      processed++;
      Player player = Bukkit.getPlayer(playerId);
      if (player == null || !player.isOnline()) {
        appliedPlayers.remove(playerId);
        continue;
      }
      J.runEntity(player, () -> removeArmorModifier(player));
    }

    if (cursor.hasNext()) {
      J.s(() -> cleanupArmorBatch(cursor), 1);
    }
  }

  private double activeTicksSinceLastUpdate(UUID playerId) {
    long now = System.currentTimeMillis();
    Long previous = bonusStatAt.put(playerId, now);
    return elapsedActiveTicks(previous, now);
  }

  private double[] createMaterialArmorPoints() {
    Material[] materials = Material.values();
    double[] points = new double[materials.length];
    for (Material material : materials) {
      points[material.ordinal()] = computeArmorPoints(material);
    }
    return points;
  }

  private static List<BlockPosition> createArmorOffsets() {
    Sphere sphere = new Sphere(5);
    List<BlockPosition> offsets = new ArrayList<>(1331);
    while (sphere.hasNext()) {
      offsets.add(sphere.next());
    }
    return List.copyOf(offsets);
  }

  @ConfigDescription("Gain passive armor based on nearby block hardness.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum players examined per scheduler pass.", impact = "Higher values refresh large servers faster but increase per-tick block reads.")
    public int maxPlayersPerPass = 16;

    public Config() {
      baseCost = 2;
      costFactor = 0.3;
      maxLevel = 3;
      initialCost = 3;
    }
  }
}
