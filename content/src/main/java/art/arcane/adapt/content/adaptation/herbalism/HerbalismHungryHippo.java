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
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerItemConsumeEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;

import java.util.Set;

public class HerbalismHungryHippo extends SimpleAdaptation<HerbalismHungryHippo.Config> {
  public static final Id ID = Id.adapt("herbalism/hippo");
  private static final Id GOLDEN_APPLE = Id.minecraft("golden_apple");
  private static final Id ENCHANTED_GOLDEN_APPLE = Id.minecraft("enchanted_golden_apple");
  private static final Id GOLDEN_CARROT = Id.minecraft("golden_carrot");
  private static final Set<Id> FOOD = Set.of(
      Id.minecraft("apple"),
      Id.minecraft("baked_potato"),
      Id.minecraft("beetroot"),
      Id.minecraft("beetroot_soup"),
      Id.minecraft("bread"),
      Id.minecraft("carrot"),
      Id.minecraft("chorus_fruit"),
      Id.minecraft("cooked_chicken"),
      Id.minecraft("cooked_cod"),
      Id.minecraft("cooked_mutton"),
      Id.minecraft("cooked_porkchop"),
      Id.minecraft("cooked_rabbit"),
      Id.minecraft("cooked_salmon"),
      Id.minecraft("cookie"),
      Id.minecraft("dried_kelp"),
      Id.minecraft("golden_apple"),
      Id.minecraft("glow_berries"),
      Id.minecraft("golden_carrot"),
      Id.minecraft("honey_block"),
      Id.minecraft("melon_slice"),
      Id.minecraft("mushroom_stew"),
      Id.minecraft("poisonous_potato"),
      Id.minecraft("potato"),
      Id.minecraft("pufferfish"),
      Id.minecraft("pumpkin_pie"),
      Id.minecraft("rabbit_stew"),
      Id.minecraft("beef"),
      Id.minecraft("chicken"),
      Id.minecraft("cod"),
      Id.minecraft("mutton"),
      Id.minecraft("porkchop"),
      Id.minecraft("salmon"),
      Id.minecraft("rotten_flesh"),
      Id.minecraft("spider_eye"),
      Id.minecraft("cooked_beef"),
      Id.minecraft("suspicious_stew"),
      Id.minecraft("sweet_berries"),
      Id.minecraft("tropical_fish")
  );

  public HerbalismHungryHippo() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("potato"));
    setInterval(8111);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("golden_apple"))
        .key("challenge_herbalism_hippo_500")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_herbalism_hippo_500", "herbalism.hungry-hippo.bonus-saturation", 500, 400);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, TextFormat.GREEN, "+ ", "(" + (2 + level) + " +", 1);
  }

  @Handle(priority = EventPriority.NORMAL)
  public void on(PlayerItemConsumeEvent e) {
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p)) {
      return;
    }

    Id food = e.item().type();
    if (!isFood(food)) {
      return;
    }

    int level = getLevel(p);
    int bonus = 2 + level;
    feed(p, bonus);
    addStat(p, "herbalism.hungry-hippo.bonus-saturation", bonus);
    xp(p, 5);
    mealFx(this, p, food, level);
  }

  public static boolean isFood(Id type) {
    return FOOD.contains(type);
  }

  public static void feed(PlayerRef p, int bonus) {
    int newFood = Math.min(20, p.foodLevel() + bonus);
    p.setFoodLevel(newFood);
    p.setSaturation(Math.min((float) newFood, p.saturation() + bonus));
  }

  public static void mealFx(Adaptation<?> owner, PlayerRef p, Id food, int level) {
    boolean bigMeal = food.equals(GOLDEN_APPLE) || food.equals(ENCHANTED_GOLDEN_APPLE) || food.equals(GOLDEN_CARROT);
    double radius = Math.min(2.5D, 0.9D + (level * 0.18D));
    int ringPoints = Math.min(16, 8 + (level * 2));
    Rgb ringColor = bigMeal ? new Rgb(255, 205, 70) : new Rgb(120, 220, 90);
    FxEmitter emitter = Fx.now(owner, p.location().add(0, 0.25, 0), FxPriority.TRANSITION)
        .dustRing(ringColor, radius * 0.5D, 12, 1.0F)
        .ring(Particles.VILLAGER_HAPPY, radius, ringPoints, 0.9D)
        .particle(Particles.COMPOSTER, 3, 0, 0.4D, 0, 0.25D, 0.03D)
        .chord(Sounds.BLOCK_POINTED_DRIPSTONE_LAND, 1F, 0.25F, Sounds.BLOCK_NOTE_BLOCK_CHIME, 0.4F, 1.4F);
    if (bigMeal) {
      emitter.particle(Particles.WAX_ON, 4, 0, 0.6D, 0, 0.5D, 0.02D)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.6F);
    }
  }

  @ConfigDescription("Consuming food gives you more saturation.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 7;
      initialCost = 1;
    }
  }
}
