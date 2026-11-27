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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PreferenceToggleGroup;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.ArrayList;
import java.util.List;

public class HerbalismGrowthAura extends SimpleAdaptation<HerbalismGrowthAura.Config> {
  public static final Id ID = Id.adapt("herbalism/growth_aura");
  public static final PreferenceToggleGroup GROWABLES = HerbalismGrowables.group("growables", Text.key("adapt.herbalism.preferences.herbalismgrowthaura.growables"), Id.minecraft("wheat_seeds"));
  public static final PlayerPreference<CommonPreferences.Toggle> SURFACE = CommonPreferences.toggle("surface-only", Text.key("adapt.herbalism.preferences.herbalismgrowthaura.surface-only"), CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Toggle> FOOD_RESERVE = CommonPreferences.toggle("food-reserve", Text.key("adapt.herbalism.preferences.herbalismgrowthaura.food-reserve"), CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<CommonPreferences.Scale> RADIUS = CommonPreferences.scale("radius", Text.key("adapt.herbalism.preferences.herbalismgrowthaura.radius"));
  private static final List<PlayerPreference<?>> PREFERENCES = preferences();

  private static final Rgb LIME = new Rgb(0, 255, 0);
  private final HerbalismGrowthPulses pulses = new HerbalismGrowthPulses(this, playerState(),
      new HerbalismGrowthPulses.Planner() {
        @Override
        public HerbalismGrowthPulses.Pulse plan(PlayerRef player, int level) {
          double factor = getLevelPercent(level);
          double radius = getRadius(factor) * preference(player, RADIUS).multiplier();
          return new HerbalismGrowthPulses.Pulse(radius, HerbalismGrowthPulses.sampleCountForRadius(radius),
              getStrength(level), getFoodCost(factor), getConfig().surfaceOnly || preferenceEnabled(player, SURFACE));
        }

        @Override
        public boolean grows(PlayerRef player, Id block) {
          return HerbalismGrowthAura.this.grows(player, block);
        }

        @Override
        public int foodFloor(PlayerRef player) {
          return preferenceEnabled(player, FOOD_RESERVE) ? 16 : 10;
        }
      },
      (PlayerRef player, int grown) -> addStat(player, "herbalism.growth-aura.blocks-grown", grown));

  public HerbalismGrowthAura() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("bone_meal"));
    setInterval(10);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("wheat"))
        .key("challenge_herbalism_growth_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("hay_block"))
            .key("challenge_herbalism_growth_25k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_herbalism_growth_1k", "herbalism.growth-aura.blocks-grown", 1000, 300);
    registerMilestone("challenge_herbalism_growth_25k", "herbalism.growth-aura.blocks-grown", 25000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return PREFERENCES;
  }

  @Override
  public List<PreferenceToggleGroup> getPlayerPreferenceGroups() {
    return List.of(GROWABLES);
  }

  @Override
  public boolean isPlayerPreferenceBlocked(PlayerPreference<?> preference) {
    return HerbalismGrowables.blocked(preference, AdaptApi.settings()::isGrowthBlacklisted);
  }

  boolean grows(PlayerRef p, Id block) {
    return !AdaptApi.settings().isGrowthBlacklisted(block)
        && preferenceEnabled(p, HerbalismGrowables.of(block).preference());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getRadius(getLevelPercent(level)), 0), 1);
    statLore(v, Format.pc(getStrength(level), 0), 2);
    statLore(v, TextFormat.YELLOW, "+ ", Format.f(getFoodCost(getLevelPercent(level)), 2), 3);
  }

  private double getRadius(double factor) {
    return factor * getConfig().radiusFactor;
  }

  private double getStrength(int level) {
    return level * getConfig().strengthFactor;
  }

  private double getFoodCost(double factor) {
    return ((1D - factor) + getConfig().minFoodCost * (getConfig().maxFoodCost - (1D - factor)));
  }

  @Override
  public boolean hasTickDemand() {
    return super.hasTickDemand() || pulses.hasDemand();
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    pulses.tick();
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    pulses.forget(e.player().uuid());
  }

  private static List<PlayerPreference<?>> preferences() {
    List<PlayerPreference<?>> preferences = new ArrayList<>(GROWABLES.entries().size() + 4);
    preferences.add(CommonPreferences.ENABLED);
    preferences.addAll(GROWABLES.preferences());
    preferences.add(SURFACE);
    preferences.add(FOOD_RESERVE);
    preferences.add(RADIUS);
    return List.copyOf(preferences);
  }

  /** The sparkle on a crop the aura grew; {@code mature} adds the ring and chime of a finished crop. */
  public static void growFx(Adaptation<?> owner, BlockRef block, boolean mature) {
    if (mature) {
      Fx.now(owner, block.location().add(0.5, 1.0, 0.5), FxPriority.AMBIENT)
          .dustRing(LIME, 0.6D, 10, 0.9F)
          .particle(Particles.HAPPY_VILLAGER, 2, 0, 0, 0, 0.1D, 0.02D)
          .chord(Sounds.ITEM_CROP_PLANT, 0.35F, 1.2F, Sounds.BLOCK_NOTE_BLOCK_CHIME, 0.3F, 1.6F);
    } else {
      Fx.now(owner, block.location().add(0.5, 0.6, 0.5), FxPriority.AMBIENT)
          .particle(Particles.HAPPY_VILLAGER, 2, 0, 0, 0, 0.1D, 0.02D)
          .particle(Particles.COMPOSTER, 1, 0, 0.1D, 0, 0.1D, 0.02D)
          .sound(Sounds.ITEM_CROP_PLANT, 0.25F, 1.5F);
    }
  }

  @ConfigDescription("Grow nature around you in an aura at the cost of hunger.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Surface Only for the Herbalism Growth Aura adaptation.", impact = "True enables this behavior and false disables it.")
    boolean surfaceOnly = true;
    @ConfigDoc(value = "Controls Min Food Cost for the Herbalism Growth Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minFoodCost = 0.05;
    @ConfigDoc(value = "Controls Max Food Cost for the Herbalism Growth Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxFoodCost = 0.4;
    @ConfigDoc(value = "Controls Radius Factor for the Herbalism Growth Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 18;
    @ConfigDoc(value = "Controls Strength Factor for the Herbalism Growth Aura adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double strengthFactor = 0.75;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      maxLevel = 7;
      initialCost = 3;
    }
  }
}
