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
import art.arcane.adapt.api.recipe.MaterialChar;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class CraftingSkulls extends SimpleAdaptation<CraftingSkulls.Config> {

  public CraftingSkulls() {
    super("crafting-skulls");
    registerConfiguration(Config.class);
    setIcon(Material.SKELETON_SKULL);
    setInterval(17776);
    registerRecipe(AdaptRecipe.shaped()
        .key("crafting-skeletonskull")
        .ingredient(new MaterialChar('I', Material.BONE))
        .ingredient(new MaterialChar('X', Material.BONE_BLOCK))
        .shapes(List.of(
            "III",
            "IXI",
            "III"))
        .result(new ItemStack(Material.SKELETON_SKULL, 1))
        .build());
    registerRecipe(AdaptRecipe.shaped()
        .key("crafting-witherskeletonskull")
        .ingredient(new MaterialChar('I', Material.NETHER_BRICK))
        .ingredient(new MaterialChar('X', Material.BONE_BLOCK))
        .shapes(List.of(
            "III",
            "IXI",
            "III"))
        .result(new ItemStack(Material.WITHER_SKELETON_SKULL, 1))
        .build());
    registerRecipe(AdaptRecipe.shaped()
        .key("crafting-zombieskull")
        .ingredient(new MaterialChar('I', Material.ROTTEN_FLESH))
        .ingredient(new MaterialChar('X', Material.BONE_BLOCK))
        .shapes(List.of(
            "III",
            "IXI",
            "III"))
        .result(new ItemStack(Material.ZOMBIE_HEAD, 1))
        .build());
    registerRecipe(AdaptRecipe.shaped()
        .key("crafting-creeperhead")
        .ingredient(new MaterialChar('I', Material.GUNPOWDER))
        .ingredient(new MaterialChar('X', Material.BONE_BLOCK))
        .shapes(List.of(
            "III",
            "IXI",
            "III"))
        .result(new ItemStack(Material.CREEPER_HEAD, 1))
        .build());
    registerRecipe(AdaptRecipe.shaped()
        .key("crafting-dragonhead")
        .ingredient(new MaterialChar('I', Material.DRAGON_BREATH))
        .ingredient(new MaterialChar('X', Material.BONE_BLOCK))
        .shapes(List.of(
            "III",
            "IXI",
            "III"))
        .result(new ItemStack(Material.DRAGON_HEAD, 1))
        .build());
    AdvancementSpec skulls100 = AdvancementSpec.challenge(
        "challenge_crafting_skulls_100",
        Material.WITHER_SKELETON_SKULL,
        Localizer.dLocalize("advancement.challenge_crafting_skulls_100.title"),
        Localizer.dLocalize("advancement.challenge_crafting_skulls_100.description")
    );
    AdvancementSpec skulls10 = AdvancementSpec.challenge(
        "challenge_crafting_skulls_10",
        Material.SKELETON_SKULL,
        Localizer.dLocalize("advancement.challenge_crafting_skulls_10.title"),
        Localizer.dLocalize("advancement.challenge_crafting_skulls_10.description")
    ).withChild(skulls100);
    registerAdvancementSpec(skulls10);
    registerStatTracker(skulls10.statTracker("crafting.skulls.skulls-crafted", 10, 300));
    registerStatTracker(skulls100.statTracker("crafting.skulls.skulls-crafted", 100, 1000));
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + Localizer.dLocalize("crafting.skulls.lore1"));
    v.addLore(C.YELLOW + "- " + C.GRAY + Localizer.dLocalize("crafting.skulls.lore2"));
    v.addLore(C.YELLOW + "- " + C.GRAY + Localizer.dLocalize("crafting.skulls.lore3"));
    v.addLore(C.YELLOW + "- " + C.GRAY + Localizer.dLocalize("crafting.skulls.lore4"));
    v.addLore(C.YELLOW + "- " + C.GRAY + Localizer.dLocalize("crafting.skulls.lore5"));
    v.addLore(C.YELLOW + "- " + C.GRAY + Localizer.dLocalize("crafting.skulls.lore6"));
  }


  @EventHandler
  public void on(CraftItemEvent e) {
    Player p = (Player) e.getWhoClicked();
    if (!hasActiveAdaptation(p)) return;
    if (e.getRecipe() != null) {
      Material result = e.getRecipe().getResult().getType();
      if (result == Material.SKELETON_SKULL || result == Material.WITHER_SKELETON_SKULL
          || result == Material.ZOMBIE_HEAD || result == Material.CREEPER_HEAD
          || result == Material.DRAGON_HEAD) {
        addStat(p, "crafting.skulls.skulls-crafted", 1);
        soulForge(p.getLocation().add(0, 1, 0), result);
      }
    }
  }

  private void soulForge(Location center, Material result) {
    timeline(center)
        .duration(10)
        .priority(FxPriority.TRANSITION)
        .cullRadius(20)
        .frame((fx, tick, progress) -> {
          double rise = 0.2D + (progress * 1.2D);
          fx.particle(Particle.SOUL, 2, 0, rise, 0, 0.1D, 0.01D);
          fx.particle(Particles.SMOKE, 2, 0, rise, 0, 0.12D, 0.01D);
          if (tick == 0 || tick == 5) {
            fx.ring(Particle.SOUL, 0.4D, 8, 1.6D);
          }
          if (tick == 0) {
            fx.chord(Sound.PARTICLE_SOUL_ESCAPE, 0.7F, 0.6F, Sound.BLOCK_BONE_BLOCK_PLACE, 0.8F, 0.8F, Sound.ENTITY_SKELETON_AMBIENT, 0.4F, 0.5F);
          }
        })
        .start();
    switch (result) {
      case CREEPER_HEAD -> fx(center, FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 6, 0.3D)
          .sound(Sound.ENTITY_CREEPER_PRIMED, 0.4F, 1.2F);
      case WITHER_SKELETON_SKULL -> fx(center, FxPriority.TRANSITION)
          .burst(Particle.SOUL, 6, 0.3D)
          .sound(Sound.ENTITY_WITHER_SPAWN, 0.3F, 1.4F);
      case DRAGON_HEAD -> fx(center, FxPriority.TRANSITION)
          .particle(Particle.REVERSE_PORTAL, 10, 0, 0.6D, 0, 0.4D, 0.05D)
          .dustRing(Color.fromRGB(30, 30, 40), 1.2D, 20, 1.2F)
          .sound(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3F, 0.8F);
      case ZOMBIE_HEAD -> fx(center, FxPriority.TRANSITION)
          .sound(Sound.ENTITY_ZOMBIE_AMBIENT, 0.4F, 0.6F);
      default -> {
      }
    }
  }


  @ConfigDescription("Craft Mob Skulls using materials surrounding a Bone Block.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      permanent = true;
      baseCost = 8;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
