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

package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.BlockingMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.List;
import java.util.Map;

public class BlockingHorseArmorer extends SimpleAdaptation<BlockingHorseArmorer.Config> {

  public BlockingHorseArmorer() {
    super("blocking-horsearmorer");
    registerConfiguration(Config.class);
    setLocalizationKey("blocking.horse_armorer");
    setIcon(Id.minecraft("golden_horse_armor"));
    setInterval(17774);
    ItemOps items = AdaptCore.platform().host().items();
    registerRecipe(new RecipeSpec.Shaped(Id.adapt("blocking-horsearmorerleather"),
        List.of(
            "III",
            "IUI",
            "III"),
        Map.of('I', Ingredient.of(Id.minecraft("leather")),
            'U', Ingredient.of(Id.minecraft("saddle"))),
        items.create(Id.minecraft("leather_horse_armor"), 1)));
    registerRecipe(new RecipeSpec.Shaped(Id.adapt("blocking-horsearmoreriron"),
        List.of(
            "III",
            "IUI",
            "III"),
        Map.of('I', Ingredient.of(Id.minecraft("iron_ingot")),
            'U', Ingredient.of(Id.minecraft("saddle"))),
        items.create(Id.minecraft("iron_horse_armor"), 1)));
    registerRecipe(new RecipeSpec.Shaped(Id.adapt("blocking-horsearmorergold"),
        List.of(
            "III",
            "IUI",
            "III"),
        Map.of('I', Ingredient.of(Id.minecraft("gold_ingot")),
            'U', Ingredient.of(Id.minecraft("saddle"))),
        items.create(Id.minecraft("golden_horse_armor"), 1)));
    registerRecipe(new RecipeSpec.Shaped(Id.adapt("blocking-horsearmorerdiamond"),
        List.of(
            "III",
            "IUI",
            "III"),
        Map.of('I', Ingredient.of(Id.minecraft("diamond")),
            'U', Ingredient.of(Id.minecraft("saddle"))),
        items.create(Id.minecraft("diamond_horse_armor"), 1)));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_horse_armor"))
        .key("challenge_blocking_horse_armor_10")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_blocking_horse_armor_10", "blocking.horse-armorer.armor-crafted", 10, 400);
  }

  @Handle
  public void on(CraftItemEvent e) {
    PlayerRef p = e.player();
    if (hasActiveAdaptation(p) && isAdaptationRecipe(e.recipeKey())) {
      addStat(p, "blocking.horse-armorer.armor-crafted", 1);
      fx(p, FxPriority.TRANSITION)
          .column(Particles.WAX_ON, 5, 1.4D)
          .burst(Particles.CRIT_MAGIC, 4, 0.3D)
          .chord(Sounds.ENTITY_HORSE_SADDLE, 0.6F, 1.0F, Sounds.BLOCK_ANVIL_USE, 0.3F, 1.3F);
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(BlockingMessages.HORSE_ARMORER_LORE1));
  }



  @ConfigDescription("Craft Horse Armor by surrounding a saddle with material.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      enabled = true;
      permanent = true;
      baseCost = 1;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 1;
    }
  }
}
