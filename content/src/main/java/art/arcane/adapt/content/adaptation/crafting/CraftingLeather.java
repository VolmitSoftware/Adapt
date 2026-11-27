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

package art.arcane.adapt.content.adaptation.crafting;

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
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

public class CraftingLeather extends SimpleAdaptation<CraftingLeather.Config> {
  public static final Id ID = Id.adapt("crafting/leather");
  private static final Id ROTTEN_FLESH = Id.minecraft("rotten_flesh");
  private static final Id CAMPFIRE = Id.minecraft("campfire");

  public CraftingLeather() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("leather"));
    setInterval(17776);
    registerRecipe(RecipeSpec.cooking(Id.adapt("crafting-leather"), RecipeSpec.CookingStation.CAMPFIRE, Ingredient.of(ROTTEN_FLESH), AdaptApi.platform().host().items().create(Id.minecraft("leather"), 1), 1, 100));
    AdaptAdvancement leatherCrafted = AdaptAdvancement.builder().key("challenge_crafting_leather_100").icon(Id.minecraft("leather")).title(Text.key("adapt.advancement.challenge_crafting_leather_100.title")).description(Text.key("adapt.advancement.challenge_crafting_leather_100.description")).frame(AdvancementFrame.CHALLENGE).visibility(AdvancementVisibility.VANILLA).build();
    registerAdvancement(leatherCrafted);
    registerMilestone(leatherCrafted.key(), "crafting.leather.leather-crafted", 100, 300);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + "+ " + TextFormat.GRAY + Text.key("adapt.crafting.leather.lore1").resolve());
  }

  @Handle
  public void on(PlayerInteractEvent e) {
    PlayerRef p = e.player();
    if (e.hand() == Hand.OFF_HAND && p.inventory().mainHand().type().equals(ROTTEN_FLESH)) {
      return;
    }

    if (e.item() != null && !e.item().isEmpty() && e.item().type().equals(ROTTEN_FLESH) && e.clickedBlock() != null && e.clickedBlock().type().equals(CAMPFIRE)) {
      Loc smolder = e.clickedBlock().location().add(0.5D, 0.6D, 0.5D);
      if (getActiveLevel(p) <= 0) {
        e.setCancelled(true);
        fx(smolder, FxPriority.TRANSITION)
            .burst(Particles.SMOKE, 2, 0.1D)
            .particle(Particles.WAX_ON, 1, 0, 0.2D, 0, 0.05D, 0.02D)
            .sound(Sounds.BLOCK_FIRE_EXTINGUISH, 0.4F, 1.6F);
      } else {
        addStat(p, "crafting.leather.leather-crafted", 1);
        cureSmolder(smolder);
      }
    }
  }

  private void cureSmolder(Loc center) {
    timeline(center)
        .duration(12)
        .priority(FxPriority.AMBIENT)
        .cullRadius(20)
        .frame((fx, tick, progress) -> {
          double rise = 0.2D + (progress * 1.0D);
          fx.particle(Particles.SMOKE, 2, 0, rise, 0, 0.12D, 0.01D);
          if ((tick & 3) == 0) {
            fx.particle(Particles.FLAME, 1, 0, rise * 0.5D, 0, 0.06D, 0.01D);
          }
          if (tick == 0) {
            fx.chord(Sounds.ITEM_ARMOR_EQUIP_LEATHER, 0.6F, 0.9F, Sounds.BLOCK_FIRE_AMBIENT, 0.4F, 1.0F);
          }
        })
        .start();
  }


  @ConfigDescription("Craft Leather from Rotten Flesh on a campfire.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      initialCost = 1;
      permanent = true;
      baseCost = 1;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
