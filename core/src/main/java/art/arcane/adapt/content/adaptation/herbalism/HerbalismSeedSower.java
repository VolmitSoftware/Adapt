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
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
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
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
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
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;

public class HerbalismSeedSower extends SimpleAdaptation<HerbalismSeedSower.Config> {
  private static final Rgb LIME = new Rgb(0, 255, 0);
  private static final Id AIR = Id.minecraft("air");
  private static final Id NETHER_WART = Id.minecraft("nether_wart");
  private static final Id SOUL_SAND = Id.minecraft("soul_sand");
  private static final Id FARMLAND = Id.minecraft("farmland");
  public static final PlayerPreference<HerbalismPreferences.Materials> MATERIALS = HerbalismPreferences.materials("materials", HerbalismMessages.PREFERENCE_HERBALISMSEEDSOWER_MATERIALS);
  public static final PlayerPreference<CommonPreferences.Scale> RADIUS = CommonPreferences.scale("radius", HerbalismMessages.PREFERENCE_HERBALISMSEEDSOWER_RADIUS);
  public static final PlayerPreference<CommonPreferences.Toggle> RESERVE = CommonPreferences.toggle("seed-reserve", HerbalismMessages.PREFERENCE_HERBALISMSEEDSOWER_RESERVE, CommonPreferences.Toggle.OFF);

  public HerbalismSeedSower() {
    super("herbalism-seed-sower");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("wheat_seeds"));
    setInterval(6920);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wheat_seeds"))
        .key("challenge_herbalism_seed_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("farmland"))
            .key("challenge_herbalism_seed_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_herbalism_seed_1k", "herbalism.seed-sower.seeds-planted", 1000, 300);
    registerMilestone("challenge_herbalism_seed_25k", "herbalism.seed-sower.seeds-planted", 25000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, MATERIALS, RADIUS, RESERVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    double factor = getLevelPercent(level);
    statLore(v, getRadius(level), 1);
    statLore(v, getMaxCrops(level), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTicks(factor) * 50D, 1), 3);
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if ((action != InteractAction.RIGHT_CLICK_BLOCK && action != InteractAction.RIGHT_CLICK_AIR) || e.hand() != Hand.MAIN_HAND) {
      return;
    }

    PlayerRef p = e.player();
    if (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(p)) {
      return;
    }
    if (!p.isSneaking() || !hasActiveAdaptation(p)) {
      return;
    }

    ItemRef hand = p.inventory().mainHand();
    if (!isItem(hand)) {
      return;
    }

    Id seedType = hand.type();
    Id cropType = getCropType(seedType);
    if (cropType == null) {
      return;
    }

    boolean vetoed = e.clickedBlock() != null
        ? e.useBlock() == UseResult.DENY
        : e.useItem() == UseResult.DENY;
    if (vetoed) {
      if (p.hasCooldown(seedType)) {
        e.setCancelled(true);
      }
      return;
    }

    if (p.hasCooldown(seedType)) {
      return;
    }

    BlockRef origin = e.clickedBlock();
    if (origin == null) {
      if (Tasks.isFoliaThreading()) {
        return;
      }
      origin = p.targetBlock(5);
    }

    if (origin == null || !ownsPlantingTarget(p, origin.location())) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    int planted = plantNearby(p, origin, seedType, cropType, Math.max(1, (int) Math.floor(getRadius(level) * preference(p, RADIUS).multiplier())), getMaxCrops(level));
    if (planted <= 0) {
      return;
    }

    e.setCancelled(true);
    int cooldownTicks = getCooldownTicks(getLevelPercent(level));
    p.setCooldown(seedType, cooldownTicks);
    AbilityGlyphs.cooldown(p, this, cooldownTicks * 50L);
    addStat(p, "harvest.planted", planted);
    addStat(p, "herbalism.seed-sower.seeds-planted", planted);
    xp(p, planted * getConfig().xpPerCrop);

    double footprint = Math.max(1.0D, getRadius(level));
    FxEmitter emitter = fx(origin.center(), FxPriority.TRANSITION)
        .dustRing(LIME, footprint, Math.min(28, (int) Math.round(footprint * 8)), 1.0F)
        .sound(Sounds.ITEM_CROP_PLANT, 0.6F, 1.25F);
    if (planted > 4) {
      emitter.sound(Sounds.BLOCK_ROOTED_DIRT_PLACE, 0.5F, 1.35F);
    }
  }

  private int plantNearby(PlayerRef p, BlockRef origin, Id seedType, Id cropType, int radius, int maxCrops) {
    if (!preference(p, MATERIALS).allows(cropType)) {
      return 0;
    }
    int available = p.gameMode() == GameMode.CREATIVE
        ? maxCrops
        : Math.max(0, p.inventory().mainHand().amount() - (preferenceEnabled(p, RESERVE) ? 8 : 0));
    List<BlockRef> targets = findPlantingTargets(p, origin, seedType, radius, Math.min(maxCrops, available));
    if (targets.isEmpty()) {
      return 0;
    }

    if (p.gameMode() == GameMode.CREATIVE) {
      int planted = plantTargets(p, targets, cropType);
      emitPlantingFx(targets.subList(0, planted));
      return planted;
    }

    int requested = targets.size();
    AbilityCharge charge = payItemCostDeferred(
        p,
        "seeds",
        AdaptCore.platform().host().items().create(seedType),
        requested,
        () -> hasHeldSeeds(p, seedType, requested)
    );
    if (!charge.allowed()) {
      return 0;
    }

    int planted = plantTargets(p, targets, cropType);
    if (planted != requested) {
      rollbackPlanting(targets, planted, cropType);
      refundCost(charge.activationId(), AbilityRefundReason.ACTIVATION_FAILED);
      return 0;
    }

    boolean defaultConsumed = !charge.defaultCostSuppressed()
        && consumeHeldSeeds(p, seedType, planted);
    if (!charge.defaultCostSuppressed() && !defaultConsumed) {
      rollbackPlanting(targets, planted, cropType);
      refundCost(charge.activationId(), AbilityRefundReason.ACTIVATION_FAILED);
      return 0;
    }

    boolean settled = settleCost(charge.activationId());
    if (!acceptsPlantingSettlement(defaultConsumed, charge.defaultCostSuppressed(), settled)) {
      rollbackPlanting(targets, planted, cropType);
      refundCost(charge.activationId(), AbilityRefundReason.ACTIVATION_FAILED);
      return 0;
    }

    emitPlantingFx(targets);
    return planted;
  }

  private List<BlockRef> findPlantingTargets(PlayerRef p, BlockRef origin, Id seedType, int radius, int limit) {
    List<BlockRef> targets = new ArrayList<>(Math.max(0, limit));
    WorldRef world = origin.world();
    int y = origin.y();

    for (int x = -radius; x <= radius && targets.size() < limit; x++) {
      for (int z = -radius; z <= radius && targets.size() < limit; z++) {
        int blockX = origin.x() + x;
        int blockZ = origin.z() + z;
        Loc baseLocation = new Loc(world, blockX, y, blockZ, 0F, 0F);
        if (!ownsPlantingTarget(p, baseLocation)) {
          continue;
        }

        BlockRef base = world.blockAt(blockX, y, blockZ);
        if (!isValidBase(seedType, base.type())) {
          continue;
        }

        Loc cropLocation = new Loc(world, blockX, y + 1, blockZ, 0F, 0F);
        if (!ownsPlantingTarget(p, cropLocation)) {
          continue;
        }

        BlockRef crop = world.blockAt(blockX, y + 1, blockZ);
        if (!crop.isAir()
            || !canBlockPlace(p, cropLocation)
            || !ProtectionProbes.attemptBlockPlaceProbe(p, crop)) {
          continue;
        }
        if (!ownsPlantingTarget(p, cropLocation)
            || !crop.isAir()
            || !isValidBase(seedType, base.type())) {
          continue;
        }

        targets.add(crop);
      }
    }

    return targets;
  }

  private int plantTargets(PlayerRef player, List<BlockRef> targets, Id cropType) {
    int planted = 0;
    for (BlockRef crop : targets) {
      if (!ownsPlantingTarget(player, crop.location()) || !crop.isAir()) {
        break;
      }
      crop.setType(cropType, true);
      planted++;
    }
    return planted;
  }

  private boolean hasHeldSeeds(PlayerRef p, Id seedType, int amount) {
    ItemRef hand = p.inventory().mainHand();
    return hand.type().equals(seedType) && hand.amount() >= amount;
  }

  private boolean consumeHeldSeeds(PlayerRef p, Id seedType, int amount) {
    if (!hasHeldSeeds(p, seedType, amount)) {
      return false;
    }

    ItemRef hand = p.inventory().mainHand();
    int remaining = hand.amount() - amount;
    if (remaining <= 0) {
      p.inventory().setMainHand(AdaptCore.platform().host().items().empty());
    } else {
      hand.setAmount(remaining);
      p.inventory().setMainHand(hand);
    }
    return true;
  }

  private void rollbackPlanting(List<BlockRef> targets, int planted, Id cropType) {
    for (int index = 0; index < planted; index++) {
      BlockRef crop = targets.get(index);
      if ((!Tasks.isFoliaThreading() || Tasks.isOwnedByCurrentRegion(crop.location()))
          && crop.type().equals(cropType)) {
        crop.setType(AIR, true);
      }
    }
  }

  private boolean ownsPlantingTarget(PlayerRef player, Loc location) {
    return !Tasks.isFoliaThreading()
        || (Tasks.isOwnedByCurrentRegion(player) && Tasks.isOwnedByCurrentRegion(location));
  }

  private void emitPlantingFx(List<BlockRef> targets) {
    int count = Math.min(12, targets.size());
    for (int index = 0; index < count; index++) {
      fx(targets.get(index).location().add(0.5, 0.2, 0.5), FxPriority.AMBIENT)
          .particle(Particles.COMPOSTER, 1, 0, 0.05D, 0, 0.1D, 0.01D)
          .particle(Particles.HAPPY_VILLAGER, 1, 0, 0.05D, 0, 0.1D, 0.01D);
    }
  }

  static boolean acceptsPlantingSettlement(boolean defaultConsumed, boolean defaultCostSuppressed, boolean settled) {
    return defaultConsumed || (defaultCostSuppressed && settled);
  }

  private boolean isValidBase(Id seedType, Id base) {
    if (seedType.equals(NETHER_WART)) {
      return base.equals(SOUL_SAND);
    }

    return base.equals(FARMLAND);
  }

  private Id getCropType(Id seedType) {
    if (!seedType.isMinecraft()) {
      return null;
    }
    return switch (seedType.path()) {
      case "wheat_seeds" -> Id.minecraft("wheat");
      case "carrot" -> Id.minecraft("carrots");
      case "potato" -> Id.minecraft("potatoes");
      case "beetroot_seeds" -> Id.minecraft("beetroots");
      case "melon_seeds" -> Id.minecraft("melon_stem");
      case "pumpkin_seeds" -> Id.minecraft("pumpkin_stem");
      case "torchflower_seeds" -> Id.minecraft("torchflower_crop");
      case "nether_wart" -> NETHER_WART;
      default -> null;
    };
  }

  private int getRadius(int level) {
    return Math.max(1, (int) Math.round(getConfig().baseRadius + (getLevelPercent(level) * getConfig().radiusFactor)));
  }

  private int getMaxCrops(int level) {
    return Math.max(1, (int) Math.round(getConfig().baseCropCount + (getLevelPercent(level) * getConfig().cropCountFactor)));
  }

  private int getCooldownTicks(double factor) {
    return Math.max(2, (int) Math.round(getConfig().cooldownTicksBase - (factor * getConfig().cooldownTicksReduction)));
  }


  @ConfigDescription("Sneak-right-click with seeds to plant nearby farmland and soul-sand plots.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Radius for the Herbalism Seed Sower adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseRadius = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Herbalism Seed Sower adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Crop Count for the Herbalism Seed Sower adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseCropCount = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Crop Count Factor for the Herbalism Seed Sower adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cropCountFactor = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Herbalism Seed Sower adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Reduction for the Herbalism Seed Sower adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksReduction = 42;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Crop for the Herbalism Seed Sower adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerCrop = 1.45;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }
}
