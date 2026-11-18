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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
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
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.HerbalismMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Set;

public class HerbalismRootedFooting extends SimpleAdaptation<HerbalismRootedFooting.Config> {
  private static final Id FARMLAND_BLOCK = Id.minecraft("farmland");
  private static final Set<Id> NATURE_GROUND = Set.of(
      FARMLAND_BLOCK,
      Id.minecraft("grass_block"),
      Id.minecraft("moss_block"),
      Id.minecraft("mycelium"),
      Id.minecraft("dirt"),
      Id.minecraft("rooted_dirt")
  );
  public static final PlayerPreference<CommonPreferences.Toggle> FARMLAND = CommonPreferences.toggle("farmland", HerbalismMessages.PREFERENCE_HERBALISMROOTEDFOOTING_FARMLAND, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> FALL = CommonPreferences.toggle("fall-conversion", HerbalismMessages.PREFERENCE_HERBALISMROOTEDFOOTING_FALL, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> FOOD_RESERVE = CommonPreferences.toggle("food-reserve", HerbalismMessages.PREFERENCE_HERBALISMROOTEDFOOTING_FOOD_RESERVE, CommonPreferences.Toggle.OFF);

  private final Cooldowns trampleFx = cooldowns();

  public HerbalismRootedFooting() {
    super("herbalism-rooted-footing");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("farmland"));
    setInterval(2050);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("farmland"))
        .key("challenge_herbalism_rooted_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_herbalism_rooted_500", "herbalism.rooted-footing.farmland-saved", 500, 300);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, FARMLAND, FALL, FOOD_RESERVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    double absorb = getFallAbsorb(level);
    statLore(v, Form.pc(absorb, 0), 1);
    statLore(v, C.YELLOW, "* ", getConfig().foodPerDamage, 2);
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(HerbalismMessages.ROOTED_FOOTING_LORE3));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    if (e.action() != InteractAction.PHYSICAL || e.clickedBlock() == null) {
      return;
    }
    PlayerRef p = e.player();

    if (!hasActiveAdaptation(p) || !preferenceEnabled(p, FARMLAND)) {
      return;
    }

    if (e.clickedBlock().type().equals(FARMLAND_BLOCK)) {
      e.setCancelled(true);
      addStat(p, "herbalism.rooted-footing.farmland-saved", 1);
      if (trampleFx.isReady(p.uuid(), 500L)) {
        trampleFx.mark(p.uuid());
        fx(e.clickedBlock().location().add(0.5, 1.0, 0.5), FxPriority.AMBIENT)
            .particle(Particles.COMPOSTER, 2, 0, 0, 0, 0.2D, 0.01D)
            .sound(Sounds.BLOCK_ROOTED_DIRT_PLACE, 0.3F, 1.2F);
      }
    }
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p) || e.cause() != DamageCause.FALL) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0 || !isNatureGround(p) || !preferenceEnabled(p, FALL)) {
      return;
    }

    double absorbCap = e.damage() * getFallAbsorb(level);
    int foodRequired = (int) Math.ceil(absorbCap * getConfig().foodPerDamage);
    if (foodRequired <= 0 || p.foodLevel() <= 0) {
      return;
    }

    int usableFood = Math.min(p.foodLevel(), foodRequired);
    if (preferenceEnabled(p, FOOD_RESERVE) && p.foodLevel() - usableFood < 8) {
      return;
    }
    double absorbed = absorbedDamage(absorbCap, usableFood, getConfig().foodPerDamage);
    if (absorbed <= 0) {
      return;
    }

    p.setFoodLevel(Math.max(0, p.foodLevel() - usableFood));
    e.setDamage(Math.max(0, e.damage() - absorbed));

    int count = (int) Math.max(4, Math.min(12, Math.ceil(absorbed)));
    double ringRadius = Math.min(2.2D, 0.9D + (absorbed * 0.12D));
    FxEmitter landing = fx(p.location(), FxPriority.COMBAT)
        .dustRing(new Rgb(120, 180, 90), ringRadius, 16, 1.0F)
        .dome(Particles.COMPOSTER, ringRadius, count)
        .chord(Sounds.BLOCK_GRASS_BREAK, 0.6F, 0.8F, Sounds.BLOCK_ROOTED_DIRT_PLACE, 0.5F, 1.1F);

    if (e.damage() <= 0.01) {
      e.setCancelled(true);
      landing.particle(Particles.HAPPY_VILLAGER, 4, 0, 0.4D, 0, 0.4D, 0.02D)
          .sound(Sounds.BLOCK_NOTE_BLOCK_CHIME, 0.4F, 1.4F);
    }
  }

  static double absorbedDamage(double absorbCap, int usableFood, double foodPerDamage) {
    if (!Double.isFinite(absorbCap)
        || !Double.isFinite(foodPerDamage)
        || absorbCap <= 0D
        || usableFood <= 0
        || foodPerDamage <= 0D) {
      return 0D;
    }

    return Math.min(absorbCap, usableFood / foodPerDamage);
  }

  private boolean isNatureGround(PlayerRef p) {
    return NATURE_GROUND.contains(p.world().blockAt(p.location().add(0, -1, 0)).type());
  }

  private double getFallAbsorb(int level) {
    return Math.min(getConfig().maxAbsorbPercent, getConfig().absorbBase + (getLevelPercent(level) * getConfig().absorbFactor));
  }


  @ConfigDescription("Protect farmland and convert fall damage into hunger on natural ground.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Absorb Base for the Herbalism Rooted Footing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double absorbBase = 0.28;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Absorb Factor for the Herbalism Rooted Footing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double absorbFactor = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Absorb Percent for the Herbalism Rooted Footing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxAbsorbPercent = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Food Per Damage for the Herbalism Rooted Footing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double foodPerDamage = 1.8;

    public Config() {
      permanent = true;
      baseCost = 3;
      costFactor = 0.65;
      maxLevel = 1;
      initialCost = 3;
    }
  }
}
