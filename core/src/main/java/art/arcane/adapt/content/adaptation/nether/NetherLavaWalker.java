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

package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.localization.catalog.NetherMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.Environment;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;

public class NetherLavaWalker extends SimpleAdaptation<NetherLavaWalker.Config> {
  private static final Rgb ORANGE = Rgb.of(0xFFA500);
  public static final PlayerPreference<CommonPreferences.Toggle> SNEAK_DROP = CommonPreferences.toggle("sneak-drop", NetherMessages.NETHERLAVAWALKER_PREFERENCE_SNEAK_DROP, CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<FoodReserve> FOOD_RESERVE = new PlayerPreference<>(FoodReserve.class,
      new PlayerPreference.Definition<>("food-reserve", NetherMessages.NETHERLAVAWALKER_PREFERENCE_FOOD_RESERVE, FoodReserve.NONE, List.of(
          new PlayerPreference.Choice<>(FoodReserve.NONE, NetherMessages.NETHERLAVAWALKER_PREFERENCE_FOOD_RESERVE_NONE, Id.minecraft("bowl"), 1),
          new PlayerPreference.Choice<>(FoodReserve.FOUR, NetherMessages.NETHERLAVAWALKER_PREFERENCE_FOOD_RESERVE_FOUR, Id.minecraft("bread"), 1),
          new PlayerPreference.Choice<>(FoodReserve.EIGHT, NetherMessages.NETHERLAVAWALKER_PREFERENCE_FOOD_RESERVE_EIGHT, Id.minecraft("cooked_beef"), 1))));

  public NetherLavaWalker() {
    super("nether-lava-walker");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("magma_block"));
    setInterval(1000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("magma_block"))
        .key("challenge_nether_lava_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_ingot"))
            .key("challenge_nether_lava_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_nether_lava_1k", "nether.lava-walker.blocks-walked", 1000, 300);
    registerMilestone("challenge_nether_lava_25k", "nether.lava-walker.blocks-walked", 25000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, SNEAK_DROP, FOOD_RESERVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getStride(level), 0), 1);
    statLore(v, C.YELLOW, "* ", getHungerCost(level), 2);
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    if (p.world().environment() != Environment.NETHER) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (!NetherLavaStride.canStride(p) || !NetherLavaStride.onLava(p)) {
        return;
      }

      int level = getActiveLevel(p);
      if (getStorageLong(p, "lavaWalkerCooldown", 0L) > System.currentTimeMillis()) {
        return;
      }

      int reserve = preference(p, FOOD_RESERVE).ordinal() * 4;
      if ((preferenceEnabled(p, SNEAK_DROP) && p.isSneaking())
          || (reserve > 0 && p.foodLevel() - getHungerCost(level) < reserve)) {
        return;
      }
      NetherLavaStride.stride(this, p, getStride(level), getHungerCost(level), getConfig().fireResistTicks);
      long now = System.currentTimeMillis();
      long cooldownMillis = getCooldownMillis(level);
      setStorage(p, "lavaWalkerCooldown", now + cooldownMillis);
      AbilityGlyphs.cooldown(p.uuid(), this, now, cooldownMillis);
      xp(p, getConfig().xpPerStride);
      addStat(p, "nether.lava-walker.blocks-walked", 1);
    });
  }

  private double getStride(int level) {
    return getConfig().strideBase + (getLevelPercent(level) * getConfig().strideFactor);
  }

  private int getHungerCost(int level) {
    return Math.max(1, (int) Math.round(getConfig().hungerCostBase - (getLevelPercent(level) * getConfig().hungerCostFactor)));
  }

  private long getCooldownMillis(int level) {
    return Math.max(100, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }


  static void strideFx(Adaptation<?> owner, PlayerRef p, Vec3 dir, boolean hadFireResistance) {
    Fx.now(owner, p.location(), FxPriority.TRAIL)
        .trail(Particles.LAVA, dir.x(), 0.1D, dir.z(), 1.0D, 6)
        .trail(Particles.FLAME, dir.x(), 0.1D, dir.z(), 0.8D, 4)
        .particle(Particles.SMOKE, 2, 0D, 0.1D, 0D, 0.15D, 0.02D)
        .chord(Sounds.BLOCK_FIRE_EXTINGUISH, 0.25F, 1.4F, Sounds.ENTITY_BLAZE_AMBIENT, 0.2F, 1.6F);
    if (!hadFireResistance) {
      Fx.now(owner, p.location(), FxPriority.TRANSITION)
          .dustRing(ORANGE, 0.7D, 8, 1.0F)
          .sound(Sounds.BLOCK_FIRE_AMBIENT, 0.2F, 1.2F);
    }
  }

  @ConfigDescription("Stride over lava in the Nether at the cost of hunger.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stride Base for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double strideBase = 0.18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stride Factor for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double strideFactor = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Hunger Cost Base for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hungerCostBase = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Hunger Cost Factor for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hungerCostFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisBase = 900;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownMillisFactor = 700;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Fire Resist Ticks for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int fireResistTicks = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Stride for the Nether Lava Walker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerStride = 3.5;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      initialCost = 3;
    }
  }

  public enum FoodReserve { NONE, FOUR, EIGHT }
}
