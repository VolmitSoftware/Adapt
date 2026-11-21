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

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.localization.catalog.HerbalismMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class HerbalismCompostCascade extends SimpleAdaptation<HerbalismCompostCascade.Config> {
  public static final PlayerPreference<HerbalismPreferences.Materials> MATERIALS = HerbalismPreferences.materials("materials", HerbalismMessages.PREFERENCE_HERBALISMCOMPOSTCASCADE_MATERIALS);
  public static final PlayerPreference<CommonPreferences.Toggle> LEAVES = CommonPreferences.toggle("leaves", HerbalismMessages.PREFERENCE_HERBALISMCOMPOSTCASCADE_LEAVES, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> DROPS = CommonPreferences.toggle("dropped-items", HerbalismMessages.PREFERENCE_HERBALISMCOMPOSTCASCADE_DROPS, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> INVENTORY = CommonPreferences.toggle("inventory-items", HerbalismMessages.PREFERENCE_HERBALISMCOMPOSTCASCADE_INVENTORY, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> RESERVE = CommonPreferences.toggle("item-reserve", HerbalismMessages.PREFERENCE_HERBALISMCOMPOSTCASCADE_RESERVE, CommonPreferences.Toggle.OFF);

  private static final int MAX_COMPOST_LEVEL = 8;
  private static final int SCAN_BLOCK_BUDGET = 24576;
  private static final int PLAYER_STORAGE_SLOTS = 36;
  private static final int MAX_TRACKED_IMMATURE_CROPS = 512;
  private static final DataKey<Byte> REWARD_KEY = DataKey.adapt("compost_cascade_reward", DataCodecs.BYTE);
  private static final Id COMPOSTER = Id.minecraft("composter");
  private static final Id AIR = Id.minecraft("air");
  private static final Set<Id> COMPOSTABLE_TYPES = Set.of(
      Id.minecraft("wheat"),
      Id.minecraft("beetroot"),
      Id.minecraft("carrot"),
      Id.minecraft("potato"),
      Id.minecraft("poisonous_potato"),
      Id.minecraft("nether_wart"),
      Id.minecraft("cactus"),
      Id.minecraft("sugar_cane"),
      Id.minecraft("bamboo"),
      Id.minecraft("short_grass"),
      Id.minecraft("tall_grass"),
      Id.minecraft("sea_pickle")
  );
  private static final Set<Id> NON_CROPS = Set.of(Id.minecraft("chorus_plant"), Id.minecraft("sugar_cane"), Id.minecraft("bamboo"));

  public HerbalismCompostCascade() {
    super("herbalism-compost-cascade");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("composter"));
    setInterval(600);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("composter"))
        .key("challenge_herbalism_compost_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("bone_meal"))
            .key("challenge_herbalism_compost_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_herbalism_compost_1k", "herbalism.compost-cascade.items-composted", 1000, 300);
    registerMilestone("challenge_herbalism_compost_25k", "herbalism.compost-cascade.items-composted", 25000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, MATERIALS, LEAVES, DROPS, INVENTORY, RESERVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getRadius(level)), 1);
    statLore(v, getMaxItems(level), 2);
    statLore(v, Form.pc(getFillChance(level), 0), 3);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTicks(level) * 50D, 1), 4);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if ((action != InteractAction.RIGHT_CLICK_BLOCK && action != InteractAction.RIGHT_CLICK_AIR) || e.hand() != Hand.MAIN_HAND) {
      return;
    }
    if (e.isCancelled()
        || (action == InteractAction.RIGHT_CLICK_BLOCK && e.useBlock() == UseResult.DENY)) {
      return;
    }

    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p) || !p.isSneaking() || p.hasCooldown(COMPOSTER)) {
      return;
    }
    if (action == InteractAction.RIGHT_CLICK_AIR && Tasks.isFoliaThreading()) {
      return;
    }

    BlockRef composter = e.clickedBlock();
    if (composter == null) {
      composter = p.targetBlock(5);
    }

    if (composter == null || !composter.type().equals(COMPOSTER)) {
      return;
    }

    if (e.clickedBlock() == null && !ProtectionProbes.attemptBlockUse(p, composter)) {
      return;
    }

    if (!canInteract(p, composter.location()) || !canBlockPlace(p, composter.location())) {
      return;
    }

    int oldLevel = composterLevel(composter.state());
    if (oldLevel < 0) {
      return;
    }

    int level = getActiveLevel(p);
    double fillChance = getFillChance(level);
    int maxItems = getMaxItems(level);
    double radius = getRadius(level);
    Loc center = composter.center();
    WorldRef world = center.world();
    if (world == null) {
      return;
    }

    CompostState state = new CompostState(oldLevel);
    CompostBudget budget = splitBudget(maxItems);
    List<BlockRef> immatureCrops = new ArrayList<>();

    state.beginPhase(budget.drops());
    if (preferenceEnabled(p, DROPS)) {
      processDroppedItems(p, world, center, radius, state, fillChance);
    }
    state.beginPhase(budget.growth());
    scanGrowth(p, world, center, radius, level, state, fillChance, immatureCrops);
    state.beginPhase(budget.inventory());
    if (preferenceEnabled(p, INVENTORY)) {
      processInventoryItems(p, state, fillChance);
    }

    if (state.consumed <= 0) {
      return;
    }

    int matured = matureCrops(p, immatureCrops, maturationAttempts(getMaturationAttempts(level), state.totalFills()));

    int updatedLevel = Math.min(MAX_COMPOST_LEVEL, Math.max(oldLevel, state.compostLevel));
    composter.setState(composter.state().with("level", Integer.toString(updatedLevel)), true);

    int cooldownTicks = getCooldownTicks(level);
    p.setCooldown(COMPOSTER, cooldownTicks);
    AbilityGlyphs.cooldown(p, this, cooldownTicks * 50L);
    e.setCancelled(true);

    addStat(p, "harvest.composted", state.consumed);
    addStat(p, "herbalism.compost-cascade.items-composted", state.consumed);
    xp(p, (state.consumed * getConfig().xpPerItemConsumed)
        + (state.levelGains * getConfig().xpPerLevelGain)
        + (matured * getConfig().xpPerCropMatured));

    double visualRadius = Math.min(8.0D, radius);
    timeline(center)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(20)
        .frame((fx, tick, progress) -> {
          double r = 0.4D + ((visualRadius - 0.4D) * (1.0D - progress));
          fx.dome(Particles.SPORE_BLOSSOM_AIR, r, 8);
          fx.ring(Particles.COMPOSTER, r * 0.85D, 8, 0.2D);
          if (tick == 0) {
            fx.sound(Sounds.BLOCK_COMPOSTER_FILL, 0.8F, 1.25F);
          } else if (tick == 1) {
            fx.sound(Sounds.BLOCK_COMPOSTER_FILL, 0.5F, 0.9F);
          } else if ((tick & 1) == 0) {
            fx.sound(Sounds.BLOCK_NOTE_BLOCK_HAT, 0.3F, (float) (1.1D + (progress * 0.6D)));
          }
        })
        .start();

    if (updatedLevel >= MAX_COMPOST_LEVEL) {
      fx(center, FxPriority.TRANSITION)
          .dustRing(new Rgb(120, 230, 120), 1.2D, 16, 1.1F)
          .column(Particles.VILLAGER_HAPPY, 10, 1.4D)
          .chord(Sounds.BLOCK_COMPOSTER_READY, 1.0F, 1.12F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.2F);
    }

    dropRewards(world, center, level, oldLevel, updatedLevel, state);
  }

  private void processDroppedItems(PlayerRef p, WorldRef world, Loc center, double radius, CompostState state, double fillChance) {
    if (state.phaseDone()
        || (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(center, radius, radius))) {
      return;
    }

    for (EntityRef entity : world.nearbyEntities(center.position(), radius, radius, radius)) {
      if (!(entity instanceof ItemEntityRef item) || state.phaseDone()) {
        continue;
      }

      if ((Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(item))
          || isRewardDrop(item)
          || !canSnatchItem(p, item)) {
        continue;
      }

      ItemRef stack = item.item().copy();
      if (!isItem(stack) || !isCompostable(stack.type())) {
        continue;
      }

      int transferable = compostProcessCount(Math.max(0, stack.amount() - (preferenceEnabled(p, RESERVE) ? 8 : 0)), state.phaseLimit, state.phaseProcessed);
      int remaining = stack.amount() - transferable;
      if ((Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(item))
          || !ProtectionProbes.attemptItemPickup(p, item, remaining)
          || !item.isValid()
          || item.isDead()) {
        continue;
      }

      ItemRef current = item.item();
      if (!current.isSimilar(stack) || current.amount() != stack.amount()) {
        continue;
      }

      if (compostStack(p, stack, state, fillChance) <= 0) {
        continue;
      }
      if (stack.amount() <= 0) {
        item.remove();
      } else {
        item.setItem(stack);
      }
    }
  }

  private void scanGrowth(PlayerRef p, WorldRef world, Loc center, double radius, int level, CompostState state,
                          double fillChance, List<BlockRef> immatureCrops) {
    int r = Math.max(1, (int) Math.ceil(radius));
    int span = (2 * r) + 1;
    int budget = workFor(span * span * span, SCAN_BLOCK_BUDGET);
    double rs = radius * radius;
    boolean consumeLeaves = getConfig().consumeLeaves && preferenceEnabled(p, LEAVES);
    int bursts = getLeafCompostBursts(level);
    double leafFillChance = getLeafFillChance(level, fillChance);
    int inspected = 0;
    int puffs = 0;

    for (int x = -r; x <= r; x++) {
      for (int y = -r; y <= r; y++) {
        for (int z = -r; z <= r; z++) {
          if (inspected >= budget) {
            return;
          }

          if ((x * x) + (y * y) + (z * z) > rs) {
            continue;
          }

          inspected++;
          int blockX = center.blockX() + x;
          int blockY = center.blockY() + y;
          int blockZ = center.blockZ() + z;
          if (Tasks.isFoliaThreading()
              && !Tasks.isOwnedByCurrentRegion(new Loc(world, blockX, blockY, blockZ))) {
            continue;
          }
          BlockRef b = world.blockAt(blockX, blockY, blockZ);
          if (!preference(p, MATERIALS).allows(b.type())) {
            continue;
          }
          BlockStateRef ageable = b.state();
          if (ageable.age() >= 0 && isCropCandidate(b.type())) {
            if (ageable.age() < ageable.maxAge()) {
              if (immatureCrops.size() < MAX_TRACKED_IMMATURE_CROPS && !AdaptConfig.get().isGrowthBlacklisted(b.type())) {
                immatureCrops.add(b);
              }
              continue;
            }

            if (!state.phaseDone()) {
              harvestMatureCrop(p, world, b, state, fillChance);
            }
            continue;
          }

          if (consumeLeaves && !state.phaseDone() && isLeafBlock(b.type())) {
            Id expectedLeafType = b.type();
            if (!canBlockBreak(p, b.location())
                || !ProtectionProbes.attemptBlockBreakProbe(p, b)) {
              continue;
            }
            if (!isCurrentLeaf(p, b, expectedLeafType)) {
              continue;
            }

            ItemRef leaves = AdaptCore.platform().host().items().create(Id.minecraft("oak_leaves"), bursts);
            if (!isCurrentLeaf(p, b, expectedLeafType)
                || compostStack(p, leaves, state, leafFillChance) <= 0
                || !isCurrentLeaf(p, b, expectedLeafType)) {
              continue;
            }
            b.setType(AIR, false);

            if (puffs < 8) {
              fx(b.center(), FxPriority.AMBIENT)
                  .particle(Particles.SPORE_BLOSSOM_AIR, 2, 0, 0.1D, 0, 0.15D, 0.01D)
                  .particle(Particles.COMPOSTER, 1, 0, 0.1D, 0, 0.1D, 0.01D);
              puffs++;
            }
          }
        }
      }
    }
  }

  private void harvestMatureCrop(PlayerRef p, WorldRef world, BlockRef b, CompostState state, double fillChance) {
    Id expectedCropType = b.type();
    if (!canBlockBreak(p, b.location())
        || !canBlockPlace(p, b.location())
        || !ProtectionProbes.attemptBlockBreakProbe(p, b)
        || !ProtectionProbes.attemptBlockPlaceProbe(p, b)) {
      return;
    }
    if (currentCropData(p, b, expectedCropType, true) == null) {
      return;
    }

    List<ItemRef> drops = b.drops(null, null);
    int composted = 0;
    for (ItemRef drop : drops) {
      if (!isItem(drop)) {
        continue;
      }

      if (isCompostable(drop.type())) {
        if (currentCropData(p, b, expectedCropType, true) == null) {
          return;
        }
        composted += compostStack(p, drop, state, fillChance);
      }
    }
    BlockStateRef commitData = currentCropData(p, b, expectedCropType, true);
    if (composted <= 0 || commitData == null) {
      return;
    }
    b.setState(commitData.with("age", "0"), true);

    for (ItemRef drop : drops) {
      if (isItem(drop) && drop.amount() > 0) {
        world.dropItemNaturally(b.center().position(), drop);
      }
    }
  }

  private void processInventoryItems(PlayerRef p, CompostState state, double fillChance) {
    if (state.phaseDone()) {
      return;
    }

    PlayerInventoryRef inventory = p.inventory();
    int slots = Math.min(PLAYER_STORAGE_SLOTS, inventory.size());
    for (int i = 0; i < slots; i++) {
      if (state.phaseDone()) {
        break;
      }

      ItemRef stack = inventory.item(i);
      if (!isItem(stack) || !isCompostable(stack.type())) {
        continue;
      }

      if (compostStack(p, stack, state, fillChance) <= 0) {
        continue;
      }
      if (stack.amount() <= 0) {
        inventory.clear(i);
      } else {
        inventory.setItem(i, stack);
      }
    }
  }

  private int matureCrops(PlayerRef p, List<BlockRef> immatureCrops, int attempts) {
    if (attempts <= 0 || immatureCrops.isEmpty()) {
      return 0;
    }

    ThreadLocalRandom random = ThreadLocalRandom.current();
    int matured = 0;
    int sparkles = 0;
    for (int i = 0; i < attempts; i++) {
      BlockRef b = immatureCrops.get(random.nextInt(immatureCrops.size()));
      Id expectedCropType = b.type();
      if (currentCropData(p, b, expectedCropType, false) == null) {
        continue;
      }

      if (!canBlockPlace(p, b.location())
          || !ProtectionProbes.attemptBlockPlaceProbe(p, b)) {
        continue;
      }

      BlockStateRef commitData = currentCropData(p, b, expectedCropType, false);
      if (commitData == null) {
        continue;
      }
      b.setState(commitData.with("age", Integer.toString(commitData.age() + 1)), true);
      matured++;

      if (sparkles < 6) {
        fx(b.location().add(0.5, 1.0, 0.5), FxPriority.AMBIENT)
            .particle(Particles.VILLAGER_HAPPY, 2, 0, 0, 0, 0.1D, 0.01D)
            .particle(Particles.COMPOSTER, 1, 0, 0.1D, 0, 0.1D, 0.01D);
        sparkles++;
      }
    }

    return matured;
  }

  private int compostStack(PlayerRef p, ItemRef stack, CompostState state, double fillChance) {
    if (!preference(p, MATERIALS).allows(stack.type())) {
      return 0;
    }
    int processCount = compostProcessCount(Math.max(0, stack.amount() - (preferenceEnabled(p, RESERVE) ? 8 : 0)), state.phaseLimit, state.phaseProcessed);
    if (processCount <= 0) {
      return 0;
    }

    if (!payItemCost(p, "compost", AdaptCore.platform().host().items().create(stack.type()), processCount, () -> {
      if (stack.amount() < processCount) {
        return false;
      }
      stack.setAmount(stack.amount() - processCount);
      return true;
    })) {
      return 0;
    }

    for (int processed = 0; processed < processCount; processed++) {
      state.processed++;
      state.phaseProcessed++;
      state.consumed++;

      if (ThreadLocalRandom.current().nextDouble() <= fillChance) {
        if (state.compostLevel < MAX_COMPOST_LEVEL) {
          state.compostLevel++;
          state.levelGains++;
        } else {
          state.overflowFills++;
        }
      }
    }
    return processCount;
  }

  static int compostProcessCount(int stackAmount, int phaseLimit, int phaseProcessed) {
    return Math.min(Math.max(0, stackAmount), Math.max(0, phaseLimit - phaseProcessed));
  }

  private void dropRewards(WorldRef world, Loc center, int level, int oldLevel, int newLevel, CompostState state) {
    int boneMeal = getBaseBoneMeal(level)
        + Math.max(0, state.consumed / getItemsPerBoneMeal(level))
        + overflowBoneMeal(state.overflowFills, getOverflowFillsPerBoneMeal());
    if (newLevel >= MAX_COMPOST_LEVEL && oldLevel < MAX_COMPOST_LEVEL) {
      boneMeal += getReadyBonusBoneMeal(level);
    }

    if (boneMeal > 0) {
      dropReward(world, center, AdaptCore.platform().host().items().create(Id.minecraft("bone_meal"), Math.min(64, boneMeal)));
    }

    if (newLevel < MAX_COMPOST_LEVEL) {
      return;
    }

    int rolls = getValuableRolls(level);
    int sparkled = 0;
    for (int i = 0; i < rolls; i++) {
      if (ThreadLocalRandom.current().nextDouble() <= getValuableChance(level)) {
        ItemRef reward = rollValuableReward(level);
        dropReward(world, center, reward);
        if (sparkled < 3) {
          valuableSparkle(center, reward.type());
          sparkled++;
        }
      }
    }
  }

  private void dropReward(WorldRef world, Loc center, ItemRef stack) {
    ItemEntityRef dropped = world.dropItem(center.position(), stack);
    dropped.data().set(REWARD_KEY, (byte) 1);
  }

  private boolean isRewardDrop(ItemEntityRef item) {
    return item.data().has(REWARD_KEY);
  }

  private void valuableSparkle(Loc center, Id type) {
    FxEmitter emitter = fx(center, FxPriority.TRANSITION)
        .particle(Particles.WAX_ON, 6, 0, 0.3D, 0, 0.4D, 0.02D)
        .sound(Sounds.BLOCK_AMETHYST_CLUSTER_BREAK, 0.5F, 1.7F);
    if (type.equals(Id.minecraft("diamond"))) {
      emitter.column(Particles.END_ROD, 4, 1.2D).sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 2.0F);
    } else if (type.equals(Id.minecraft("emerald"))) {
      emitter.column(Particles.END_ROD, 2, 0.9D);
    }
  }

  private static ItemRef reward(String type, int amount) {
    return AdaptCore.platform().host().items().create(Id.minecraft(type), amount);
  }

  private ItemRef rollValuableReward(int level) {
    double lp = getLevelPercent(level);
    double r = ThreadLocalRandom.current().nextDouble();

    if (r < 0.45) {
      return reward("honeycomb", 1 + ThreadLocalRandom.current().nextInt(1 + Math.max(1, (int) Math.round(lp * 3))));
    }

    if (r < 0.7) {
      return reward("glow_berries", 2 + ThreadLocalRandom.current().nextInt(2 + Math.max(1, (int) Math.round(lp * 4))));
    }

    if (r < 0.88) {
      return reward("amethyst_shard", 1 + ThreadLocalRandom.current().nextInt(1 + Math.max(1, (int) Math.round(lp * 4))));
    }

    if (r < 0.97) {
      return reward("emerald", 1);
    }

    return reward("diamond", 1);
  }

  static int workFor(int candidates, int limit) {
    return Math.min(Math.max(0, candidates), Math.max(0, limit));
  }

  static CompostBudget splitBudget(int maxItems) {
    int total = Math.max(1, maxItems);
    int growth = (total * 40) / 100;
    int inventory = (total * 20) / 100;
    return new CompostBudget(total - growth - inventory, growth, inventory);
  }

  static int overflowBoneMeal(int overflowFills, int fillsPerBoneMeal) {
    if (fillsPerBoneMeal <= 0) {
      return 0;
    }

    return Math.max(0, overflowFills) / fillsPerBoneMeal;
  }

  static int maturationAttempts(int configuredAttempts, int compostProgress) {
    return Math.max(0, Math.min(configuredAttempts, compostProgress));
  }

  static boolean matchesLeafCommit(Id expectedType, Id currentType) {
    return expectedType != null
        && expectedType.equals(currentType)
        && expectedType.legacyName().endsWith("_LEAVES");
  }

  static boolean matchesCropCommit(Id expectedType, Id currentType, BlockStateRef data,
                                   boolean mature) {
    if (expectedType == null || !expectedType.equals(currentType) || data == null || data.age() < 0) {
      return false;
    }
    return mature
        ? data.age() == data.maxAge()
        : data.age() < data.maxAge();
  }

  private static int composterLevel(BlockStateRef state) {
    String level = state.property("level");
    if (level == null) {
      return -1;
    }
    try {
      return Integer.parseInt(level);
    } catch (NumberFormatException ignored) {
      return -1;
    }
  }

  private boolean isCurrentLeaf(PlayerRef player, BlockRef block, Id expectedType) {
    return ownsCompostTarget(player, block)
        && matchesLeafCommit(expectedType, block.type());
  }

  private BlockStateRef currentCropData(PlayerRef player, BlockRef block, Id expectedType,
                                        boolean mature) {
    if (!ownsCompostTarget(player, block)) {
      return null;
    }
    Id currentType = block.type();
    BlockStateRef currentData = block.state();
    return matchesCropCommit(expectedType, currentType, currentData, mature)
        ? currentData
        : null;
  }

  private boolean ownsCompostTarget(PlayerRef player, BlockRef block) {
    return !Tasks.isFoliaThreading()
        || (Tasks.isOwnedByCurrentRegion(player) && Tasks.isOwnedByCurrentRegion(block.location()));
  }

  private boolean isCropCandidate(Id type) {
    return !NON_CROPS.contains(type);
  }

  private boolean isLeafBlock(Id type) {
    return type.legacyName().endsWith("_LEAVES");
  }

  private boolean isCompostable(Id type) {
    String n = type.legacyName();
    return n.contains("SEEDS")
        || n.contains("SAPLING")
        || n.contains("LEAVES")
        || n.contains("FLOWER")
        || n.contains("MUSHROOM")
        || n.contains("ROOTS")
        || n.contains("VINE")
        || n.contains("KELP")
        || n.contains("DRIPLEAF")
        || n.contains("MOSS")
        || COMPOSTABLE_TYPES.contains(type);
  }

  private int getMaxItems(int level) {
    return Math.max(1, (int) Math.round(getConfig().maxItemsBase + (getLevelPercent(level) * getConfig().maxItemsFactor)));
  }

  private double getRadius(int level) {
    return Math.max(1, getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor));
  }

  private double getFillChance(int level) {
    return Math.min(getConfig().maxFillChance, getConfig().fillChanceBase + (getLevelPercent(level) * getConfig().fillChanceFactor));
  }

  private double getLeafFillChance(int level, double baseFillChance) {
    return Math.min(1.0, baseFillChance * (getConfig().leafFillChanceMultiplierBase + (getLevelPercent(level) * getConfig().leafFillChanceMultiplierFactor)));
  }

  private int getLeafCompostBursts(int level) {
    return Math.max(1, (int) Math.round(getConfig().leafCompostBurstsBase + (getLevelPercent(level) * getConfig().leafCompostBurstsFactor)));
  }

  private int getCooldownTicks(int level) {
    return Math.max(4, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksReduction)));
  }

  private int getBaseBoneMeal(int level) {
    return Math.max(0, (int) Math.round(getConfig().boneMealBase + (getLevelPercent(level) * getConfig().boneMealFactor)));
  }

  private int getReadyBonusBoneMeal(int level) {
    return Math.max(0, (int) Math.round(getConfig().readyBonusBoneMealBase + (getLevelPercent(level) * getConfig().readyBonusBoneMealFactor)));
  }

  private int getItemsPerBoneMeal(int level) {
    return Math.max(1, (int) Math.round(getConfig().itemsPerBoneMealBase - (getLevelPercent(level) * getConfig().itemsPerBoneMealReduction)));
  }

  private int getOverflowFillsPerBoneMeal() {
    return Math.max(1, (int) Math.round(getConfig().overflowFillsPerBoneMeal));
  }

  private int getMaturationAttempts(int level) {
    return Math.max(0, (int) Math.round(getConfig().maturationAttemptsBase
        + (Math.max(0, level - 1) * getConfig().maturationAttemptsPerLevel)));
  }

  private double getValuableChance(int level) {
    return Math.min(getConfig().maxValuableChance, getConfig().valuableChanceBase + (getLevelPercent(level) * getConfig().valuableChanceFactor));
  }

  private int getValuableRolls(int level) {
    return Math.max(0, (int) Math.round(getConfig().valuableRollsBase + (getLevelPercent(level) * getConfig().valuableRollsFactor)));
  }


  @ConfigDescription("Sneak-right-click a composter to consume nearby drops, harvest and replant mature crops, compost your inventory, and spend the compost maturing nearby crops.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Allows the cascade to break and compost nearby leaf blocks.", impact = "True lets the cascade strip leaves inside its radius; false leaves trees untouched.")
    boolean consumeLeaves = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 5.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 12.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Items Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxItemsBase = 80.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Items Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxItemsFactor = 240.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fill Chance Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fillChanceBase = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fill Chance Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double fillChanceFactor = 0.42;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Fill Chance for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxFillChance = 0.98;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Leaf Compost Bursts Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double leafCompostBurstsBase = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Leaf Compost Bursts Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double leafCompostBurstsFactor = 9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Leaf Fill Chance Multiplier Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double leafFillChanceMultiplierBase = 1.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Leaf Fill Chance Multiplier Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double leafFillChanceMultiplierFactor = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 36.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Reduction for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksReduction = 28.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bone Meal Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double boneMealBase = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bone Meal Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double boneMealFactor = 6.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Ready Bonus Bone Meal Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double readyBonusBoneMealBase = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Ready Bonus Bone Meal Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double readyBonusBoneMealFactor = 8.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Items Per Bone Meal Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double itemsPerBoneMealBase = 20.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Items Per Bone Meal Reduction for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double itemsPerBoneMealReduction = 14.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Compost fills wasted on a full composter that are worth one extra bone meal.", impact = "Lower values convert overflow compost into bone meal faster; higher values waste more of it.")
    double overflowFillsPerBoneMeal = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Crop maturation attempts granted at level one.", impact = "Higher values let a single cascade push more nearby crops one growth stage forward.")
    double maturationAttemptsBase = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional crop maturation attempts granted per adaptation level above one.", impact = "Higher values make each level mature noticeably more crops per cascade.")
    double maturationAttemptsPerLevel = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Valuable Chance Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double valuableChanceBase = 0.01;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Valuable Chance Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double valuableChanceFactor = 0.09;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Valuable Chance for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxValuableChance = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Valuable Rolls Base for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double valuableRollsBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Valuable Rolls Factor for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double valuableRollsFactor = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Item Consumed for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerItemConsumed = 1.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Level Gain for the Herbalism Compost Cascade adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerLevelGain = 2.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Experience granted for every nearby crop the cascade matures.", impact = "Higher values reward the maturation phase more heavily.")
    double xpPerCropMatured = 2.0;

    public Config() {
      costFactor = 0.72;
      maxLevel = 6;
      initialCost = 4;
    }
  }

  record CompostBudget(int drops, int growth, int inventory) {
  }

  private static class CompostState {
    private int compostLevel;
    private int processed;
    private int consumed;
    private int levelGains;
    private int overflowFills;
    private int phaseProcessed;
    private int phaseLimit;

    private CompostState(int compostLevel) {
      this.compostLevel = compostLevel;
    }

    private void beginPhase(int limit) {
      phaseProcessed = 0;
      phaseLimit = Math.max(0, limit);
    }

    private boolean phaseDone() {
      return phaseProcessed >= phaseLimit;
    }

    private int totalFills() {
      return levelGains + overflowFills;
    }
  }
}
