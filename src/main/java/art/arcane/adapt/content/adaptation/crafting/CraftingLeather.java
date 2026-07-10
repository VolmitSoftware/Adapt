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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdvancementSpec;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class CraftingLeather extends SimpleAdaptation<CraftingLeather.Config> {

  public CraftingLeather() {
    super("crafting-leather");
    registerConfiguration(Config.class);
    setIcon(Material.LEATHER);
    setInterval(17776);
    registerRecipe(AdaptRecipe.campfire()
        .key("crafting-leather")
        .ingredient(Material.ROTTEN_FLESH)
        .cookTime(100)
        .experience(1)
        .result(new ItemStack(Material.LEATHER, 1))
        .build());
    AdvancementSpec leatherCrafted = AdvancementSpec.challenge(
        "challenge_crafting_leather_100",
        Material.LEATHER,
        Localizer.dLocalize("advancement.challenge_crafting_leather_100.title"),
        Localizer.dLocalize("advancement.challenge_crafting_leather_100.description")
    );
    registerMilestone(leatherCrafted, "crafting.leather.leather-crafted", 100, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("crafting.leather.lore1"));
  }

  @EventHandler
  public void on(PlayerInteractEvent e) {
    if (e.getHand() == EquipmentSlot.OFF_HAND && e.getPlayer().getInventory().getItemInMainHand().getType() == Material.ROTTEN_FLESH) {
      return;
    }

    if (e.getItem() != null && e.getItem().getType() == Material.ROTTEN_FLESH && e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.CAMPFIRE) {
      Location smolder = e.getClickedBlock().getLocation().add(0.5D, 0.6D, 0.5D);
      if (getActiveLevel(e.getPlayer()) <= 0) {
        e.setCancelled(true);
        fx(smolder, FxPriority.TRANSITION)
            .burst(Particles.SMOKE, 2, 0.1D)
            .particle(Particle.WAX_ON, 1, 0, 0.2D, 0, 0.05D, 0.02D)
            .sound(Sound.BLOCK_FIRE_EXTINGUISH, 0.4F, 1.6F);
      } else {
        getPlayer(e.getPlayer()).getData().addStat("crafting.leather.leather-crafted", 1);
        cureSmolder(smolder);
      }
    }
  }

  private void cureSmolder(Location center) {
    timeline(center)
        .duration(12)
        .priority(FxPriority.AMBIENT)
        .cullRadius(20)
        .frame((fx, tick, progress) -> {
          double rise = 0.2D + (progress * 1.0D);
          fx.particle(Particles.SMOKE, 2, 0, rise, 0, 0.12D, 0.01D);
          if ((tick & 3) == 0) {
            fx.particle(Particle.FLAME, 1, 0, rise * 0.5D, 0, 0.06D, 0.01D);
          }
          if (tick == 0) {
            fx.chord(Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.6F, 0.9F, Sound.BLOCK_FIRE_AMBIENT, 0.4F, 1.0F);
          }
        })
        .start();
  }



  @ConfigDescription("Craft Leather from Rotten Flesh on a campfire.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      permanent = true;
      baseCost = 3;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
