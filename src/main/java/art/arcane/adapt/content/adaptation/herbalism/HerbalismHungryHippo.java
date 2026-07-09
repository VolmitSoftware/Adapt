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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.item.ItemListings;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemConsumeEvent;

public class HerbalismHungryHippo extends SimpleAdaptation<HerbalismHungryHippo.Config> {

  public HerbalismHungryHippo() {
    super("herbalism-hippo");
    registerConfiguration(Config.class);
    setIcon(Material.POTATO);
    setInterval(8111);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.GOLDEN_APPLE)
        .key("challenge_herbalism_hippo_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_herbalism_hippo_500", "herbalism.hungry-hippo.bonus-saturation", 500, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ (" + (2 + level) + C.GRAY + " + " + Localizer.dLocalize("herbalism.hippo.lore1"));
  }

  @EventHandler(priority = EventPriority.NORMAL)
  public void on(PlayerItemConsumeEvent e) {
    Player p = e.getPlayer();
    if (!hasActiveAdaptation(p)) {
      return;
    }

    Material food = e.getItem().getType();
    if (!ItemListings.getFood().contains(food)) {
      return;
    }

    int level = getLevel(p);
    p.setFoodLevel(p.getFoodLevel() + 2 + level);
    addStat(p, "herbalism.hungry-hippo.bonus-saturation", 2 + level);
    xp(p, 5);

    boolean bigMeal = food == Material.GOLDEN_APPLE || food == Material.ENCHANTED_GOLDEN_APPLE || food == Material.GOLDEN_CARROT;
    double radius = Math.min(2.5D, 0.9D + (level * 0.18D));
    int ringPoints = Math.min(16, 8 + (level * 2));
    Color ringColor = bigMeal ? Color.fromRGB(255, 205, 70) : Color.fromRGB(120, 220, 90);
    FxEmitter emitter = fx(p.getLocation().add(0, 0.25, 0), FxPriority.TRANSITION)
        .dustRing(ringColor, radius * 0.5D, 12, 1.0F)
        .ring(Particles.VILLAGER_HAPPY, radius, ringPoints, 0.9D)
        .particle(Particle.COMPOSTER, 3, 0, 0.4D, 0, 0.25D, 0.03D)
        .chord(Sound.BLOCK_POINTED_DRIPSTONE_LAND, 1F, 0.25F, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4F, 1.4F);
    if (bigMeal) {
      emitter.particle(Particle.WAX_ON, 4, 0, 0.6D, 0, 0.5D, 0.02D)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.6F);
    }
  }


  @Override
  public void onTick() {

  }

  @ConfigDescription("Consuming food gives you more saturation.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 8;
      costFactor = 0.75;
      maxLevel = 7;
      initialCost = 3;
    }
  }
}
