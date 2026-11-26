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

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.localization.catalog.HerbalismMessages;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

public class HerbalismSeedSower extends SimpleAdaptation<HerbalismSeedSower.Config> {
  private static final Rgb LIME = new Rgb(0, 255, 0);
  public static final Id AIR = Id.minecraft("air");
  public static final Id NETHER_WART = Id.minecraft("nether_wart");
  public static final Id SOUL_SAND = Id.minecraft("soul_sand");
  public static final Id FARMLAND = Id.minecraft("farmland");
  public static final PlayerPreference<HerbalismPreferences.Materials> MATERIALS = HerbalismPreferences.materials("materials", HerbalismMessages.PREFERENCE_HERBALISMSEEDSOWER_MATERIALS);
  public static final PlayerPreference<CommonPreferences.Scale> RADIUS = CommonPreferences.scale("radius", HerbalismMessages.PREFERENCE_HERBALISMSEEDSOWER_RADIUS);
  public static final PlayerPreference<CommonPreferences.Toggle> RESERVE = CommonPreferences.toggle("seed-reserve", HerbalismMessages.PREFERENCE_HERBALISMSEEDSOWER_RESERVE, CommonPreferences.Toggle.OFF);
  private final HerbalismSowing sowing = new HerbalismSowing(this);

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
    Id cropType = cropType(seedType);
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

    if (origin == null || !HerbalismSowing.ownsPlantingTarget(p, origin.location())) {
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
    return sowing.plant(new HerbalismSowing.Request(p, origin, HerbalismSowing.mainHand(p), seedType, cropType, radius,
        maxCrops, preferenceEnabled(p, RESERVE) ? 8 : 0));
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


  /** The crop a seed item plants, or null when it plants nothing. */
  public static Id cropType(Id seedType) {
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

  public static void plantFx(Adaptation<?> owner, BlockRef crop) {
    Fx.now(owner, crop.location().add(0.5, 0.2, 0.5), FxPriority.AMBIENT)
        .particle(Particles.COMPOSTER, 1, 0, 0.05D, 0, 0.1D, 0.01D)
        .particle(Particles.HAPPY_VILLAGER, 1, 0, 0.05D, 0, 0.1D, 0.01D);
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
