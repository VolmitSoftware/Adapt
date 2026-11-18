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

package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
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
import art.arcane.adapt.localization.catalog.HerbalismMessages;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

public class HerbalismSporeBloom extends SimpleAdaptation<HerbalismSporeBloom.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> FLOWERS = CommonPreferences.toggle("flower-conversion", HerbalismMessages.PREFERENCE_HERBALISMSPOREBLOOM_FLOWERS, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> SOIL = CommonPreferences.toggle("soil-conversion", HerbalismMessages.PREFERENCE_HERBALISMSPOREBLOOM_SOIL, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Scale> SPREAD = CommonPreferences.scale("spread-limit", HerbalismMessages.PREFERENCE_HERBALISMSPOREBLOOM_SPREAD);

  private static final int CHARGE_UNPAID = 0;
  private static final int CHARGE_IN_PROGRESS = 1;
  private static final int CHARGE_PAID = 2;
  private static final int CHARGE_DENIED = 3;

  private static final Id MYCELIUM = Id.minecraft("mycelium");
  private static final Id PODZOL = Id.minecraft("podzol");
  private static final Id RED_MUSHROOM = Id.minecraft("red_mushroom");
  private static final Id BROWN_MUSHROOM = Id.minecraft("brown_mushroom");
  private static final Set<Id> CONVERTIBLE_SOIL = ids("dirt", "grass_block", "coarse_dirt", "rooted_dirt", "mycelium", "podzol");
  private static final Set<Id> FLOWERS_NAMED = ids("dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy",
      "cornflower", "lily_of_the_valley", "wither_rose", "sunflower", "lilac", "rose_bush", "peony", "torchflower",
      "pink_petals", "spore_blossom");
  private static final Set<Id> WARM_FLOWERS = ids("dandelion", "poppy", "red_tulip", "orange_tulip", "pink_tulip", "sunflower",
      "rose_bush", "peony", "wither_rose", "torchflower", "pink_petals");
  private static final Set<Id> COOL_FLOWERS = ids("blue_orchid", "allium", "azure_bluet", "white_tulip", "oxeye_daisy",
      "cornflower", "lily_of_the_valley", "lilac", "spore_blossom");
  private static final Set<Id> FUNGI = ids("red_mushroom", "brown_mushroom", "crimson_fungus", "warped_fungus");
  private static final Set<String> REPLACEABLE_NAMES = Set.of("TALL_GRASS", "GRASS", "FERN", "LARGE_FERN", "WHEAT", "CARROTS",
      "POTATOES", "BEETROOTS", "NETHER_WART", "TORCHFLOWER_CROP", "PITCHER_CROP");

  private final Cooldowns bloomCooldown = cooldowns();

  public HerbalismSporeBloom() {
    super("herbalism-spore-bloom");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("red_mushroom_block"));
    setInterval(2100);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("brown_mushroom"))
        .key("challenge_herbalism_spore_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_herbalism_spore_500", "herbalism.spore-bloom.blocks-spread", 500, 300);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, FLOWERS, SOIL, SPREAD);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getBloomAttempts(level), 1);
    statLore(v, Form.f(getBloomRadius(level)), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 3);
    statLore(v, C.YELLOW, "* ", getSporeCost(level), 4);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockPlaceEvent e) {
    if (e.hand() != Hand.MAIN_HAND) {
      return;
    }

    if (getActiveLevel(e.player()) <= 0 || !e.player().isSneaking()) {
      return;
    }

    if (!isSporeItem(e.itemInHand())) {
      return;
    }

    BlockRef floor = e.block().relative(0, -1, 0);
    if (!isBloomFloor(floor.type())) {
      return;
    }

    attemptBloom(e, e.player(), floor, e.itemInHand().type());
  }

  private static Set<Id> ids(String... paths) {
    Set<Id> out = new HashSet<>();
    for (String path : paths) {
      out.add(Id.minecraft(path));
    }
    return Set.copyOf(out);
  }

  private void startBloom(PlayerRef player, BlockRef center, Id catalyst, Id spreadSurface,
                          int level, List<BlockRef> path, double ownershipRadius, BloomCharge charge) {
    int intervalTicks = Math.max(1, getSpreadIntervalTicks(level));
    int[] cursor = {0};
    int[] totalChanged = {0};
    Runnable[] bloomTask = new Runnable[1];
    bloomTask[0] = () -> {
      if ((Tasks.isFoliaThreading()
          && (!Tasks.isOwnedByCurrentRegion(player)
          || !Tasks.isOwnedByCurrentRegion(center.location(), ownershipRadius, ownershipRadius)))
          || !player.isOnline()
          || center.world() == null) {
        return;
      }
      if (charge.state().get() == CHARGE_DENIED) {
        return;
      }

      int pulseChanged = 0;
      int batch = getBlocksPerPulse(level);
      BlockRef frontier = null;
      BooleanSupplier ensureCharged = () -> ensureBloomCharged(player, charge);
      for (int i = 0; i < batch && cursor[0] < path.size(); i++) {
        BlockRef cell = path.get(cursor[0]++);
        int cellChanged = spreadAt(player, cell, catalyst, spreadSurface, ensureCharged);
        if (cellChanged > 0) {
          frontier = cell;
        }
        pulseChanged += cellChanged;
      }

      if (pulseChanged > 0) {
        totalChanged[0] += pulseChanged;
        addStat(player, "herbalism.spore-bloom.blocks-spread", pulseChanged);
        xp(player, pulseChanged * getConfig().xpPerMushroomPlaced);
        BlockRef emitAt = frontier != null ? frontier : center;
        fx(emitAt.location().add(0.5, 1.0, 0.5), FxPriority.TRANSITION)
            .particle(Particles.SPORE_BLOSSOM_AIR, 4, 0, 0, 0, 0.4D, 0.02D)
            .particle(Particles.CRIMSON_SPORE, 2, 0, 0, 0, 0.4D, 0.01D)
            .chord(Sounds.BLOCK_FUNGUS_PLACE, 0.45F, 0.75F, Sounds.ENTITY_ENDERMAN_AMBIENT, 0.22F, 0.45F + (ThreadLocalRandom.current().nextFloat() * 0.45F));
      }

      if (cursor[0] >= path.size()) {
        if (totalChanged[0] > 0) {
          fx(center.location().add(0.5, 1.0, 0.5), FxPriority.TRANSITION)
              .dome(Particles.SPORE_BLOSSOM_AIR, getBloomRadius(level), 24)
              .chord(Sounds.ENTITY_ENDERMAN_AMBIENT, 0.3F, 0.7F, Sounds.BLOCK_FUNGUS_PLACE, 0.35F, 0.9F);
        }
        return;
      }

      Tasks.runAt(center.location(), bloomTask[0], intervalTicks);
    };
    bloomTask[0].run();
  }

  private List<BlockRef> buildSpiderPath(BlockRef center, double radius, int spokes, int max, int guaranteedReach) {
    int r = Math.max(1, (int) Math.ceil(radius));
    int sectors = Math.max(8, Math.min(48, Math.max(1, spokes) * 3));
    double maxDistance = radius + 0.35D;
    double maxDistanceSq = maxDistance * maxDistance;
    List<BlockRef> out = new ArrayList<>(Math.max(8, max));
    Set<String> seen = new HashSet<>();
    out.add(center);
    seen.add(key(center));

    if (out.size() >= max) {
      return out;
    }

    ThreadLocalRandom random = ThreadLocalRandom.current();
    double offset = random.nextDouble(Math.PI * 2D);

    int forcedReach = Math.max(0, Math.min(r, guaranteedReach));
    for (int step = 1; step <= forcedReach; step++) {
      addRingSamples(center, out, seen, step, sectors, offset, maxDistanceSq, max);
      if (out.size() >= max) {
        return out;
      }
    }

    for (int step = 1; step <= r; step++) {
      addRingSamples(center, out, seen, step, sectors, offset, maxDistanceSq, max);
      if (out.size() >= max) {
        return out;
      }

      // Offset pass fills sector rounding gaps so rings look uniform.
      addRingSamples(center, out, seen, step, sectors, offset + (Math.PI / sectors), maxDistanceSq, max);
      if (out.size() >= max) {
        return out;
      }
    }

    fillRemainingFromCircle(center, out, seen, r, offset, maxDistanceSq, max);
    return out;
  }

  private void addRingSamples(BlockRef center, List<BlockRef> out, Set<String> seen, int step, int sectors, double offset, double maxDistanceSq, int max) {
    for (int i = 0; i < sectors && out.size() < max; i++) {
      double angle = offset + ((Math.PI * 2D) * i / sectors);
      int dx = (int) Math.round(Math.cos(angle) * step);
      int dz = (int) Math.round(Math.sin(angle) * step);
      if (dx == 0 && dz == 0) {
        continue;
      }

      if ((dx * dx) + (dz * dz) > maxDistanceSq) {
        continue;
      }

      BlockRef block = center.relative(dx, 0, dz);
      if (seen.add(key(block))) {
        out.add(block);
      }
    }
  }

  private void fillRemainingFromCircle(BlockRef center, List<BlockRef> out, Set<String> seen, int r, double offset, double maxDistanceSq, int max) {
    List<int[]> candidates = new ArrayList<>();
    for (int dx = -r; dx <= r; dx++) {
      for (int dz = -r; dz <= r; dz++) {
        if (dx == 0 && dz == 0) {
          continue;
        }

        if ((dx * dx) + (dz * dz) > maxDistanceSq) {
          continue;
        }

        candidates.add(new int[]{dx, dz});
      }
    }

    candidates.sort(Comparator.<int[]>comparingInt(v -> (v[0] * v[0]) + (v[1] * v[1]))
        .thenComparingDouble(v -> normalizeAngle(Math.atan2(v[1], v[0]) - offset)));

    for (int[] c : candidates) {
      if (out.size() >= max) {
        return;
      }

      BlockRef block = center.relative(c[0], 0, c[1]);
      if (seen.add(key(block))) {
        out.add(block);
      }
    }
  }

  private double normalizeAngle(double angle) {
    double out = angle % (Math.PI * 2D);
    return out < 0 ? out + (Math.PI * 2D) : out;
  }

  private int spreadAt(PlayerRef player, BlockRef floor, Id catalyst, Id spreadSurface,
                       BooleanSupplier ensureCharged) {
    int changed = 0;
    BlockRef ground = resolveTopSurfaceSoil(floor);
    if (ground == null) {
      return 0;
    }
    BlockRef above = ground.relative(0, 1, 0);

    Id groundType = ground.type();
    if (preferenceEnabled(player, SOIL) && spreadSurface != null && isConvertibleSoil(groundType) && !groundType.equals(spreadSurface)
        && canBlockBreak(player, ground.location())
        && canBlockPlace(player, ground.location())
        && ProtectionProbes.attemptBlockBreakProbe(player, ground)
        && ProtectionProbes.attemptBlockPlaceProbe(player, ground)
        && ground.type().equals(groundType)
        && ensureCharged.getAsBoolean()
        && ground.type().equals(groundType)) {
      ground.setType(spreadSurface, false);
      changed++;
    }

    Id aboveType = above.type();
    if (getConfig().swapFlowersToMushrooms && preferenceEnabled(player, FLOWERS) && isFlower(aboveType)) {
      Id replacement = getFlowerReplacement(aboveType, catalyst);
      if (replacement != null && !aboveType.equals(replacement)
          && canBlockBreak(player, above.location())
          && canBlockPlace(player, above.location())
          && ProtectionProbes.attemptBlockBreakProbe(player, above)
          && ProtectionProbes.attemptBlockPlaceProbe(player, above)
          && above.type().equals(aboveType)
          && ensureCharged.getAsBoolean()
          && above.type().equals(aboveType)) {
        above.setType(replacement, false);
        changed++;
      }
    }

    return changed;
  }

  private BlockRef resolveTopSurfaceSoil(BlockRef sample) {
    int x = sample.x();
    int z = sample.z();
    int startY = Math.min(sample.world().maxHeight() - 1, sample.y() + 4);
    int minY = Math.max(sample.world().minHeight(), sample.y() - 8);

    for (int y = startY; y >= minY; y--) {
      BlockRef block = sample.world().blockAt(x, y, z);
      if (!isConvertibleSoil(block.type())) {
        continue;
      }

      BlockRef above = block.relative(0, 1, 0);
      if (above.type().isAir() || isReplaceablePlant(above.type()) || isFlower(above.type())) {
        return block;
      }
    }

    return null;
  }

  private boolean isBloomFloor(Id type) {
    return type.equals(MYCELIUM) || type.equals(PODZOL);
  }

  private boolean isSporeItem(ItemRef hand) {
    return isItem(hand) && (hand.type().equals(RED_MUSHROOM) || hand.type().equals(BROWN_MUSHROOM));
  }

  private void attemptBloom(BlockPlaceEvent e, PlayerRef player, BlockRef center, Id catalyst) {
    Id spreadSurface = resolveSpreadSurface(center.type());
    if (spreadSurface == null) {
      return;
    }

    int level = getActiveLevel(player);
    Loc failAt = center.location().add(0.5, 0.75, 0.5);
    if (!bloomCooldown.isReady(player.uuid(), getCooldownMillis(level))) {
      fx(failAt, FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 2, 0.2D)
          .sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.75F);
      return;
    }

    if (player.foodLevel() < getFoodCost(level)) {
      fx(failAt, FxPriority.TRANSITION)
          .particle(Particles.CRIMSON_SPORE, 3, 0, 0.2D, 0, 0.2D, 0.01D)
          .sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.9F);
      return;
    }

    double radius = getBloomRadius(level);
    double ownershipRadius = Math.ceil(radius + 0.35D);
    if (Tasks.isFoliaThreading()
        && !Tasks.isOwnedByCurrentRegion(center.location(), ownershipRadius, ownershipRadius)) {
      return;
    }
    List<BlockRef> path = buildSpiderPath(
        center,
        radius,
        getSpokes(level),
        Math.max(1, (int) Math.floor(getBloomAttempts(level) * preference(player, SPREAD).multiplier())),
        getGuaranteedReach(level)
    );
    if (path.isEmpty()) {
      return;
    }

    e.setCancelled(true);
    Loc activation = center.location().add(0.5, 1.0, 0.5);
    BloomCharge charge = new BloomCharge(
        catalyst,
        getSporeCost(level),
        getFoodCost(level),
        failAt,
        activation,
        new AtomicInteger(CHARGE_UNPAID)
    );
    startBloom(player, center, catalyst, spreadSurface, level, path, ownershipRadius, charge);
  }

  private boolean chargeCatalyst(PlayerRef player, Id catalyst, int cost, Loc failAt) {
    ItemRef held = player.inventory().mainHand();
    if (!isItem(held) || !held.type().equals(catalyst)) {
      return false;
    }

    return payItemCost(player, "spore", AdaptCore.platform().host().items().create(catalyst), cost, () -> {
      if (!player.inventory().contains(catalyst, cost)) {
        fx(failAt, FxPriority.TRANSITION)
            .particle(Particles.CRIMSON_SPORE, 3, 0, 0.2D, 0, 0.2D, 0.01D)
            .sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.6F);
        return false;
      }
      player.inventory().removeItem(catalyst, cost);
      return true;
    });
  }

  private boolean ensureBloomCharged(PlayerRef player, BloomCharge charge) {
    int state = charge.state().get();
    if (state == CHARGE_PAID) {
      return true;
    }
    if (state != CHARGE_UNPAID
        || !charge.state().compareAndSet(CHARGE_UNPAID, CHARGE_IN_PROGRESS)) {
      return charge.state().get() == CHARGE_PAID;
    }

    boolean paid = false;
    try {
      if (player.foodLevel() < charge.foodCost()
          || !chargeCatalyst(player, charge.catalyst(), charge.catalystCost(), charge.failAt())) {
        return false;
      }
      player.setFoodLevel(Math.max(0, player.foodLevel() - charge.foodCost()));
      bloomCooldown.mark(player.uuid());
      fx(charge.activation(), FxPriority.TRANSITION)
          .burst(Particles.SPORE_BLOSSOM_AIR, 20, 0.35D)
          .sound(Sounds.ENTITY_ENDERMAN_AMBIENT, 0.45F, 0.55F);
      FxPresets.chargeRing(this, charge.activation(), 6);
      paid = true;
      return true;
    } finally {
      charge.state().set(paid ? CHARGE_PAID : CHARGE_DENIED);
    }
  }

  private record BloomCharge(Id catalyst, int catalystCost, int foodCost, Loc failAt,
                             Loc activation, AtomicInteger state) {
  }

  private Id resolveSpreadSurface(Id floorType) {
    if (floorType.equals(MYCELIUM)) {
      return MYCELIUM;
    }

    if (floorType.equals(PODZOL)) {
      return PODZOL;
    }

    return null;
  }

  private int getGuaranteedReach(int level) {
    return level >= 5 ? 6 : 0;
  }

  private boolean isConvertibleSoil(Id type) {
    return CONVERTIBLE_SOIL.contains(type);
  }

  private boolean isFlower(Id type) {
    String n = type.legacyName();
    return n.endsWith("_FLOWER")
        || n.endsWith("_TULIP")
        || FLOWERS_NAMED.contains(type);
  }

  private Id getFlowerReplacement(Id flower, Id catalyst) {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    if (isWarmFlower(flower)) {
      // Warm flowers lean red.
      return random.nextDouble() <= 0.7 ? RED_MUSHROOM : BROWN_MUSHROOM;
    }

    if (isCoolFlower(flower)) {
      // Cool flowers lean brown.
      return random.nextDouble() <= 0.7 ? BROWN_MUSHROOM : RED_MUSHROOM;
    }

    // Fallback uses catalyst flavor.
    return catalyst.equals(BROWN_MUSHROOM) ? BROWN_MUSHROOM : RED_MUSHROOM;
  }

  private boolean isWarmFlower(Id flower) {
    return WARM_FLOWERS.contains(flower);
  }

  private boolean isCoolFlower(Id flower) {
    return COOL_FLOWERS.contains(flower);
  }

  private boolean isReplaceablePlant(Id type) {
    if (FUNGI.contains(type)) {
      return true;
    }

    String n = type.legacyName();
    return n.endsWith("_FLOWER")
        || n.endsWith("_TULIP")
        || n.endsWith("_SAPLING")
        || n.endsWith("_SEEDS")
        || n.endsWith("_ROOTS")
        || n.endsWith("_CROP")
        || REPLACEABLE_NAMES.contains(n);
  }

  private String key(BlockRef block) {
    return block.x() + ":" + block.y() + ":" + block.z();
  }

  private int getBloomAttempts(int level) {
    double scaled = getConfig().bloomAttemptsBase + (getLevelPercent(level) * getConfig().bloomAttemptsFactor);
    double perLevel = Math.max(0, level - 1) * getConfig().bloomAttemptsPerLevel;
    return Math.max(1, (int) Math.round(scaled + perLevel));
  }

  private double getBloomRadius(int level) {
    double radius = getConfig().bloomRadiusBase + (getLevelPercent(level) * getConfig().bloomRadiusFactor);
    if (level >= 5) {
      radius = Math.max(6D, radius);
    }
    return radius;
  }

  private int getSpokes(int level) {
    return Math.max(4, (int) Math.round(getConfig().spokesBase + (getLevelPercent(level) * getConfig().spokesFactor)));
  }

  private int getBlocksPerPulse(int level) {
    return Math.max(1, (int) Math.round(getConfig().blocksPerPulseBase + (getLevelPercent(level) * getConfig().blocksPerPulseFactor)));
  }

  private int getSpreadIntervalTicks(int level) {
    return Math.max(1, (int) Math.round(getConfig().spreadIntervalTicksBase - (getLevelPercent(level) * getConfig().spreadIntervalTicksFactor)));
  }

  private int getFoodCost(int level) {
    return Math.max(1, (int) Math.round(getConfig().foodCostBase - (getLevelPercent(level) * getConfig().foodCostFactor)));
  }

  private long getCooldownMillis(int level) {
    return Math.max(250L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }

  private int getSporeCost(int level) {
    return sporeCost(getConfig().sporeCostBase, getConfig().sporeCostPerLevel, level);
  }

  static int sporeCost(double base, double perLevel, int level) {
    if (!Double.isFinite(base) || !Double.isFinite(perLevel)) {
      return 1;
    }

    return Math.max(1, (int) Math.round(base + (Math.max(0, level - 1) * perLevel)));
  }


  @ConfigDescription("Sneak-right-click mycelium with mushrooms to spread an outward spore-web that mutates nearby growth.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Allows flowers hit by the bloom to be replaced with mushrooms.", impact = "Disable this to keep flowers untouched while still converting soil into mushroom blocks.")
    boolean swapFlowersToMushrooms = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bloom Attempts Base for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bloomAttemptsBase = 26;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bloom Attempts Factor for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bloomAttemptsFactor = 58;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional bloom attempts granted each adaptation level.", impact = "Higher values make each level spread across more total blocks.")
    double bloomAttemptsPerLevel = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Mushrooms consumed by a level one bloom.", impact = "Higher values make every bloom cost more mushrooms.")
    double sporeCostBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional mushrooms consumed per adaptation level above one.", impact = "Higher values make larger blooms cost proportionally more mushrooms.")
    double sporeCostPerLevel = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bloom Radius Base for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bloomRadiusBase = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bloom Radius Factor for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bloomRadiusFactor = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spokes Base for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double spokesBase = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spokes Factor for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double spokesFactor = 7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Blocks Per Pulse Base for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double blocksPerPulseBase = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Blocks Per Pulse Factor for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double blocksPerPulseFactor = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spread Interval Ticks Base for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double spreadIntervalTicksBase = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spread Interval Ticks Factor for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double spreadIntervalTicksFactor = 1.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Food Cost Base for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double foodCostBase = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Food Cost Factor for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double foodCostFactor = 1.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 1700;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 1100;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Mushroom Placed for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerMushroomPlaced = 1.4;

    public Config() {
      costFactor = 0.7;
      initialCost = 4;
    }
  }
}
