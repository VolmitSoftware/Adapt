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

package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.adapt.util.reflect.registries.PotionEffectTypes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.UUID;

public class AgilitySuperJump extends SimpleAdaptation<AgilitySuperJump.Config> {
  private final Cooldowns cooldowns = cooldowns();

  public AgilitySuperJump() {
    super("agility-super-jump");
    registerConfiguration(Config.class);
    setIcon(Material.LEATHER_BOOTS);
    setInterval(9999);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.LEATHER_BOOTS)
        .key("challenge_agility_super_jump_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.GOLDEN_BOOTS)
            .key("challenge_agility_super_jump_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_agility_super_jump_100", "agility.super-jump.jumps", 100, 300);
    registerMilestone("challenge_agility_super_jump_5k", "agility.super-jump.jumps", 5000, 1500);
  }

  private double getJumpHeight(int level) {
    return getConfig().baseJumpMultiplier + (getConfig().jumpLevelMultiplier * level);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getJumpHeight(level), 0), 1);
    v.addLore(C.LIGHT_PURPLE + " " + Localizer.dLocalize("agility.super_jump.lore2"));
  }

  @EventHandler
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    withAdaptedPlayer(p, e, () -> {
      if (e.isSneaking() && p.isOnGround()) {
        fx(p.getLocation(), FxPriority.GAMEPLAY)
            .ring(Particle.CLOUD, 0.5D, 6, 0.05D)
            .sound(Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.3F, 0.35F);
      }
    });
  }

  @EventHandler
  public void on(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    if (p.isSwimming() || p.isFlying() || p.isGliding() || p.isSprinting()) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      if (!p.isSneaking() || !canUse(getPlayer(p))) {
        return;
      }

      Vector velocity = p.getVelocity();

      if (velocity.getY() > 0) {
        double jumpVelocity = 0.4;
        PotionEffect jumpPotion = p.getPotionEffect(PotionEffectTypes.JUMP);

        if (jumpPotion != null) {
          jumpVelocity += (double) ((float) jumpPotion.getAmplifier() + 1) * 0.1F;
        }

        UUID id = p.getUniqueId();
        if (!cooldowns.isReady(id, 1000)) {
          return;
        }
        if (p.getLocation().getBlock().getType() != Material.LADDER && velocity.getY() > jumpVelocity && p.isOnline()) {
          double jumpHeight = getJumpHeight(getLevel(p));
          p.setVelocity(p.getVelocity().setY(jumpHeight));
          cooldowns.mark(id);
          addStat(p, "agility.super-jump.jumps", 1);

          float topPitch = (float) Math.min(2.0D, 1.4D + jumpHeight);
          double shockRadius = Math.max(0.6D, jumpHeight * 1.5D);
          fx(p.getLocation(), FxPriority.GAMEPLAY)
              .column(Particle.CLOUD, 8, 1.2D)
              .ring(Particles.BLOCK_CRACK, shockRadius, 10, 0.05D, p.getLocation().getBlock().getRelative(BlockFace.DOWN).getBlockData())
              .chord(Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.25F, 0.7F, Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.25F, topPitch, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.3F, 0.7F);
        }
      }
    });
  }


  @ConfigDescription("Sneak and jump for exceptional height advantage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Jump Multiplier for the Agility Super Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseJumpMultiplier = 0.23;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Jump Level Multiplier for the Agility Super Jump adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double jumpLevelMultiplier = 0.23;

    public Config() {
      baseCost = 2;
      costFactor = 0.55;
      maxLevel = 3;
      initialCost = 5;
    }
  }
}
