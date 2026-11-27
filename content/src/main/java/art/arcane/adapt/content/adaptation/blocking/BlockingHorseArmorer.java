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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;

public class BlockingHorseArmorer extends SimpleAdaptation<BlockingHorseArmorer.Config> {
  public static final Id ID = Id.adapt("blocking/horsearmorer");

  public BlockingHorseArmorer() {
    super(ID);
    registerConfiguration(Config.class);
    setLocalizationKey("adapt.blocking.horse_armorer");
    setIcon(Id.minecraft("golden_horse_armor"));
    setInterval(17774);
    ItemOps items = AdaptApi.platform().host().items();
    registerRecipe(RecipeSpec.shaped(Id.adapt("blocking-horsearmorerleather"), List.of(
            "III",
            "IUI",
            "III"), Map.of('I', Ingredient.of(Id.minecraft("leather")),
            'U', Ingredient.of(Id.minecraft("saddle"))), items.create(Id.minecraft("leather_horse_armor"), 1)));
    registerRecipe(RecipeSpec.shaped(Id.adapt("blocking-horsearmoreriron"), List.of(
            "III",
            "IUI",
            "III"), Map.of('I', Ingredient.of(Id.minecraft("iron_ingot")),
            'U', Ingredient.of(Id.minecraft("saddle"))), items.create(Id.minecraft("iron_horse_armor"), 1)));
    registerRecipe(RecipeSpec.shaped(Id.adapt("blocking-horsearmorergold"), List.of(
            "III",
            "IUI",
            "III"), Map.of('I', Ingredient.of(Id.minecraft("gold_ingot")),
            'U', Ingredient.of(Id.minecraft("saddle"))), items.create(Id.minecraft("golden_horse_armor"), 1)));
    registerRecipe(RecipeSpec.shaped(Id.adapt("blocking-horsearmorerdiamond"), List.of(
            "III",
            "IUI",
            "III"), Map.of('I', Ingredient.of(Id.minecraft("diamond")),
            'U', Ingredient.of(Id.minecraft("saddle"))), items.create(Id.minecraft("diamond_horse_armor"), 1)));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_horse_armor"))
        .key("challenge_blocking_horse_armor_10")
        .frame(AdvancementFrame.CHALLENGE)
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
          .burst(Particles.CRIT, 4, 0.3D)
          .chord(Sounds.ENTITY_HORSE_SADDLE, 0.6F, 1.0F, Sounds.BLOCK_ANVIL_USE, 0.3F, 1.3F);
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + "+ " + TextFormat.GRAY + Text.key("adapt.blocking.horse_armorer.lore1").resolve());
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
