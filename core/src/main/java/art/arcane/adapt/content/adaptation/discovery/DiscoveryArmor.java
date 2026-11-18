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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.DiscoveryMessages;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.math.Sphere;
import art.arcane.adapt.util.common.world.LoadedChunkAccess;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.math.M;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class DiscoveryArmor extends SimpleAdaptation<DiscoveryArmor.Config> {
  static final UUID LEGACY_MODIFIER = UUID.nameUUIDFromBytes("adapt-discovery-armor".getBytes());
  static final Id LEGACY_MODIFIER_KEY = Id.adapt("discovery-armor");
  private static final Id LEGACY_UUID_MODIFIER_KEY = Id.minecraft(LEGACY_MODIFIER.toString());
  private static final String LEGACY_MODIFIER_NAME = LEGACY_MODIFIER_KEY.namespace() + "-" + LEGACY_MODIFIER_KEY.path();
  private static final String SLOT_ARMOR = "armor";
  private static final long UPDATE_COOLDOWN = TimeUnit.SECONDS.toMillis(3);
  private static final List<BlockPos> ARMOR_OFFSETS = createArmorOffsets();

  private final Map<Id, Double> materialArmorPoints = new ConcurrentHashMap<>();
  private final Cooldowns updateThrottle = cooldowns();
  private final Map<UUID, Double> appliedArmor = playerState();
  private final Map<UUID, Long> bonusStatAt = playerState();
  private final Map<UUID, Boolean> legacyPurged = playerState();
  private Iterator<UUID> cleanupCursor = Map.<UUID, Double>of().keySet().iterator();
  private int playerCursor;

  public DiscoveryArmor() {
    super("discovery-world-armor");
    registerConfiguration(Config.class);
    setLocalizationKey("discovery.armor");
    setIcon(Id.minecraft("turtle_helmet"));
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_chestplate"))
        .key("challenge_discovery_armor_1hr")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_discovery_armor_1hr", "discovery.armor.ticks-with-bonus", 72000, 400);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    if (!hasActiveAdaptation(player.getPlayer())) {
      removeArmorModifier(player.getPlayer());
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(DiscoveryMessages.ARMOR_PASSIVE));
    v.addLore(C.YELLOW + "~ " + AdaptLanguage.text(
        DiscoveryMessages.ARMOR_STRENGTH,
        trusted("strength", C.BLUE + String.valueOf(level * 0.25))
    ));
  }

  public double getArmorPoints(Id m) {
    return materialArmorPoints.computeIfAbsent(m, (Id type) -> computeArmorPoints(type, AdaptCore.platform().host().items()));
  }

  @Override
  public void unregister() {
    bonusStatAt.clear();
    appliedArmor.clear();
    legacyPurged.clear();
    super.unregister();
  }

  public double getArmor(Loc l, int level) {
    WorldRef world = l.world();
    if (world == null) {
      return 0D;
    }

    int centerX = l.blockX();
    int centerY = l.blockY();
    int centerZ = l.blockZ();
    int minY = world.minHeight();
    int maxY = world.maxHeight() - 1;
    LoadedChunkAccess chunkAccess = new LoadedChunkAccess(world, 5);
    double armorValue = 0.0;
    int count = 0;

    for (BlockPos offset : ARMOR_OFFSETS) {
      int blockX = centerX + offset.x();
      int blockY = centerY + offset.y();
      int blockZ = centerZ + offset.z();
      if (blockY < minY || blockY > maxY || !chunkAccess.canRead(blockX, blockZ)) {
        continue;
      }

      BlockRef block = world.blockAt(blockX, blockY, blockZ);
      Id material = block.type();
      if (material.isAir() || block.isLiquid()) {
        continue;
      }

      count++;
      armorValue += getArmorPoints(material);
    }

    return scaleArmor(armorValue, count, level);
  }

  static int scanBlockCount() {
    return ARMOR_OFFSETS.size();
  }

  static double computeArmorPoints(Id material, ItemOps items) {
    if (!items.isBlock(material)) {
      return 0D;
    }
    return computeArmorPoints(items.blastResistance(material), items.blockHardness(material));
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

  static double lerpSeed(Double previous) {
    return previous == null || !Double.isFinite(previous) ? 0D : previous;
  }

  @Override
  public boolean hasTickDemand() {
    return super.hasTickDemand() || !appliedArmor.isEmpty();
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
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

  @Handle
  public void on(PlayerJoinEvent event) {
    PlayerRef player = event.player();
    withPlayerThread(player, () -> purgeLegacyModifier(player));
  }

  private void queueArmorUpdate(AdaptPlayer adaptPlayer) {
    PlayerRef player = adaptPlayer.getPlayer();
    if (player == null || !player.isOnline()) {
      return;
    }

    UUID playerId = player.uuid();
    if (!updateThrottle.isReady(playerId, UPDATE_COOLDOWN)) {
      return;
    }
    updateThrottle.mark(playerId);
    withPlayerThread(player, () -> updateArmor(adaptPlayer, player));
  }

  private void updateArmor(AdaptPlayer adaptPlayer, PlayerRef player) {
    purgeLegacyModifier(player);
    if (!hasActiveAdaptation(player)) {
      removeArmorModifier(player);
      return;
    }

    UUID playerId = player.uuid();
    double oldArmor = lerpSeed(appliedArmor.get(playerId));
    double armor = getArmor(player.location(), getLevel(player));
    double lerpedArmor = M.lerp(oldArmor, armor, 0.3D);
    lerpedArmor = Double.isFinite(lerpedArmor) ? lerpedArmor : 0D;
    AdaptAttributeService.get().apply(player, getName(), SLOT_ARMOR, Attributes.ARMOR, lerpedArmor, AttributeOperation.ADD_VALUE);
    appliedArmor.put(playerId, lerpedArmor);
    if (lerpedArmor > 0D) {
      double activeTicks = activeTicksSinceLastUpdate(playerId);
      if (activeTicks > 0D) {
        adaptPlayer.getData().addStat("discovery.armor.ticks-with-bonus", activeTicks);
      }
    } else {
      bonusStatAt.remove(playerId);
    }

    if (Math.round(lerpedArmor) != Math.round(oldArmor) && Math.round(lerpedArmor) > 0) {
      fx(player.location(), FxPriority.TRANSITION)
          .dustRing(new Rgb(150, 170, 190), 0.8D, 16, 1.1F)
          .chord(Sounds.BLOCK_STONE_PLACE, 0.5F, 0.7F, Sounds.BLOCK_AMETHYST_BLOCK_STEP, 0.3F, 1.2F);
    } else if (lerpedArmor > 6D && ThreadLocalRandom.current().nextInt(20) == 0) {
      fx(player.location(), FxPriority.AMBIENT)
          .particle(Particles.CRIT_MAGIC, 2, 0, 0, 0, 0.05D, 0.01D);
    }
  }

  private void cleanupInactivePlayers() {
    if (!cleanupCursor.hasNext()) {
      cleanupCursor = appliedArmor.keySet().iterator();
    }

    int limit = Math.max(1, getConfig().maxPlayersPerPass);
    int examined = 0;
    while (examined < limit && cleanupCursor.hasNext()) {
      UUID playerId = cleanupCursor.next();
      examined++;
      PlayerRef player = AdaptCore.platform().host().player(playerId);
      if (player == null || !player.isOnline()) {
        appliedArmor.remove(playerId);
        continue;
      }

      AdaptPlayer adaptPlayer = getPlayer(player);
      if (getLevel(adaptPlayer) > 0) {
        continue;
      }
      withPlayerThread(player, () -> removeInactiveModifier(player));
    }
  }

  private void removeInactiveModifier(PlayerRef player) {
    if (hasActiveAdaptation(player)) {
      return;
    }

    removeArmorModifier(player);
  }

  private void removeArmorModifier(PlayerRef player) {
    AdaptAttributeService.get().remove(player, getName(), SLOT_ARMOR, Attributes.ARMOR);
    appliedArmor.remove(player.uuid());
    bonusStatAt.remove(player.uuid());
  }

  private void purgeLegacyModifier(PlayerRef player) {
    if (legacyPurged.putIfAbsent(player.uuid(), Boolean.TRUE) != null) {
      return;
    }

    if (!player.hasAttribute(Attributes.ARMOR)) {
      return;
    }
    for (AttributeMod modifier : List.copyOf(player.modifiers(Attributes.ARMOR))) {
      if (isLegacyModifier(modifier.key())) {
        player.removeModifier(Attributes.ARMOR, modifier.key());
      }
    }
  }

  static boolean isLegacyModifier(Id key) {
    return LEGACY_MODIFIER_KEY.equals(key) || LEGACY_UUID_MODIFIER_KEY.equals(key) || LEGACY_MODIFIER_NAME.equals(key.path());
  }

  private double activeTicksSinceLastUpdate(UUID playerId) {
    long now = System.currentTimeMillis();
    Long previous = bonusStatAt.put(playerId, now);
    return elapsedActiveTicks(previous, now);
  }

  private static List<BlockPos> createArmorOffsets() {
    Sphere sphere = new Sphere(5);
    List<BlockPos> offsets = new ArrayList<>(1331);
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
