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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;
import java.util.Set;

public class HerbalismRootedFooting extends SimpleAdaptation<HerbalismRootedFooting.Config> {
  public static final Id ID = Id.adapt("herbalism/rooted_footing");
  private static final Id FARMLAND_BLOCK = Id.minecraft("farmland");
  private static final Set<Id> NATURE_GROUND = Set.of(
      FARMLAND_BLOCK,
      Id.minecraft("grass_block"),
      Id.minecraft("moss_block"),
      Id.minecraft("mycelium"),
      Id.minecraft("dirt"),
      Id.minecraft("rooted_dirt")
  );
  public static final PlayerPreference<CommonPreferences.Toggle> FARMLAND = CommonPreferences.toggle("farmland", Text.key("adapt.herbalism.preferences.herbalismrootedfooting.farmland"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> FALL = CommonPreferences.toggle("fall-conversion", Text.key("adapt.herbalism.preferences.herbalismrootedfooting.fall-conversion"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> FOOD_RESERVE = CommonPreferences.toggle("food-reserve", Text.key("adapt.herbalism.preferences.herbalismrootedfooting.food-reserve"), CommonPreferences.Toggle.OFF);

  private final Cooldowns trampleFx = cooldowns();

  public HerbalismRootedFooting() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("farmland"));
    setInterval(2050);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("farmland"))
        .key("challenge_herbalism_rooted_500")
        .frame(AdvancementFrame.CHALLENGE)
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
    statLore(v, Format.pc(absorb, 0), 1);
    statLore(v, TextFormat.YELLOW, "* ", getConfig().foodPerDamage, 2);
    v.addLore(TextFormat.GREEN + "+ " + Text.key("adapt.herbalism.rooted_footing.lore3").resolve());
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
    @ConfigDoc(value = "Controls Absorb Base for the Herbalism Rooted Footing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double absorbBase = 0.28;
    @ConfigDoc(value = "Controls Absorb Factor for the Herbalism Rooted Footing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double absorbFactor = 0.12;
    @ConfigDoc(value = "Controls Max Absorb Percent for the Herbalism Rooted Footing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxAbsorbPercent = 0.45;
    @ConfigDoc(value = "Controls Food Per Damage for the Herbalism Rooted Footing adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double foodPerDamage = 1.8;

    public Config() {
      permanent = true;
      baseCost = 2;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 2;
    }
  }
}
