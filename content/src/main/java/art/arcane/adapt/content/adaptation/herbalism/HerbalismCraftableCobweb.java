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
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;

public class HerbalismCraftableCobweb extends SimpleAdaptation<HerbalismCraftableCobweb.Config> {
  public static final Id ID = Id.adapt("herbalism/cobweb");
  static final String RECIPE_KEY = "herbalism-cobwebblock";

  public HerbalismCraftableCobweb() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("string"));
    setInterval(17771);
    ItemOps items = AdaptApi.platform().host().items();
    registerRecipe(RecipeSpec.shaped(Id.adapt(RECIPE_KEY), List.of(
            "III",
            "III",
            "III"), Map.of('I', Ingredient.of(Id.minecraft("string"))), items.create(Id.minecraft("cobweb"), 1)));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("cobweb"))
        .key("challenge_herbalism_cobweb_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_herbalism_cobweb_100", "herbalism.cobweb.cobwebs-crafted", 100, 300);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + "+ " + TextFormat.GRAY + Text.key("adapt.herbalism.cobweb.lore1").resolve());
  }


  @Handle(priority = EventPriority.MONITOR)
  public void on(CraftItemEvent e) {
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p)) {
      return;
    }
    if (e.recipeKind() == CraftItemEvent.RecipeKind.SHAPED && Id.adapt(RECIPE_KEY).equals(e.recipeKey())) {
      addStat(p, "herbalism.cobweb.cobwebs-crafted", 1);
      if (statOf(p, "herbalism.cobweb.cobwebs-crafted") == 1) {
        fx(p.location().add(0, 1, 0), FxPriority.TRANSITION)
            .column(Particles.END_ROD, 6, 1.4D)
            .particle(Particles.VILLAGER_HAPPY, 6, 0, 0.6D, 0, 0.4D, 0.05D)
            .chord(Sounds.BLOCK_WOOL_BREAK, 0.6F, 1.2F, Sounds.ENTITY_PLAYER_LEVELUP, 0.4F, 1.5F);
      }
    }
  }


  @ConfigDescription("Craft Cobwebs from String in a Crafting Table.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      initialCost = 1;
      baseCost = 1;
      permanent = true;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
