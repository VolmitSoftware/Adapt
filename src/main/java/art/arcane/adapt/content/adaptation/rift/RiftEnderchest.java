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

package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RiftMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;


public class RiftEnderchest extends SimpleAdaptation<RiftEnderchest.Config> {
  public RiftEnderchest() {
    super("rift-enderchest");
    setLocalizationKey("rift.chest");
    setIcon(Material.ENDER_CHEST);
    setInterval(9248);
    registerConfiguration(Config.class);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_CHEST)
        .key("challenge_rift_enderchest_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_rift_enderchest_200", "rift.enderchest.opens", 200, 300);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.ITALIC + AdaptLanguage.text(RiftMessages.CHEST_LORE1));
  }

  @EventHandler
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
      return;
    }

    if (e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    ItemStack hand = p.getInventory().getItemInMainHand();
    if (hand.getType() != Material.ENDER_CHEST || !hasActiveAdaptation(p)) {
      return;
    }

    if (p.hasCooldown(hand.getType())) {
      e.setCancelled(true);
      fx(p.getEyeLocation(), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 2, 0.15)
          .sound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.6f);
      return;
    }

    p.setCooldown(Material.ENDER_CHEST, 100);
    PlayerSkillLine line = getPlayer(p).getData().getSkillLine("rift");
    PlayerAdaptation adaptation = line != null ? line.getAdaptation("rift-resist") : null;
    if (adaptation != null && adaptation.getLevel() > 0) {
      RiftResist.riftResistStackAdd(this, p, 10, 2);
    }
    Location origin = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.5));
    fx(origin, FxPriority.TRANSITION)
        .helix(Particle.PORTAL, 0.4, 0.8, 8, 0)
        .particle(Particle.REVERSE_PORTAL, 3, 0, 0, 0, 0.15, 0.02)
        .chord(Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.8f, Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1.0f, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.4f);
    p.openInventory(p.getEnderChest());
    addStat(p, "rift.enderchest.opens", 1);
  }



  @ConfigDescription("Open an enderchest by left-clicking it in your hand.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 0;
      costFactor = 0.0;
      maxLevel = 1;
      initialCost = 10;
    }
  }
}