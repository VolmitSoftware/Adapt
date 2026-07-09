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

package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class AxeChop extends SimpleAdaptation<AxeChop.Config> {

  public AxeChop() {
    super("axe-chop");
    registerConfiguration(Config.class);
    setIcon(Material.IRON_AXE);
    setInterval(6911);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_AXE)
        .key("challenge_axe_chop_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_AXE)
            .key("challenge_axe_chop_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.NETHERITE_AXE)
        .key("challenge_axe_chop_one_swing")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_axe_chop_100", "axe.chop.trees-felled", 100, 400);
    registerMilestone("challenge_axe_chop_2500", "axe.chop.trees-felled", 2500, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, level, 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTime(getLevelPercent(level)) * 50D, 1), 2);
    statLore(v, C.RED, "- ", getDamagePerBlock(getLevelPercent(level)), 3);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
      return;
    }

    if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    if (p.getCooldown(p.getInventory().getItemInMainHand().getType()) > 0) {
      return;
    }

    if (!isAxe(p.getInventory().getItemInMainHand()) || !hasActiveAdaptation(p)) {
      return;
    }

    Block target = action == Action.RIGHT_CLICK_BLOCK ? e.getClickedBlock() : p.getTargetBlockExact(5);
    if (target == null) {
      return;
    }

    if (!canBlockBreak(p, target.getLocation())) {
      return;
    }

    BlockData b = target.getBlockData();
    if (isLog(new ItemStack(b.getMaterial()))) {
      e.setCancelled(true);
      Location chopLoc = target.getLocation().add(0.5D, 1.0D, 0.5D);
      timeline(chopLoc)
          .duration(4)
          .priority(FxPriority.GAMEPLAY)
          .cullRadius(24)
          .frame((fx, tick, progress) -> {
            fx.ring(Particles.CRIT_MAGIC, 0.6D - (0.55D * progress), 8, 0.0D);
            if (tick == 0) {
              fx.chord(Sound.ITEM_AXE_STRIP, 1.25F, 0.6F, Sound.ITEM_AXE_WAX_OFF, 0.4F, 1.4F);
            }
          })
          .start();
      int logsChopped = 0;
      for (int i = 0; i < getLevel(p); i++) {
        if (breakStuff(target, getRange(getLevel(p)), p)) {
          logsChopped++;
          p.setCooldown(p.getInventory().getItemInMainHand().getType(), getCooldownTime(getLevelPercent(p)));
          damageHand(p, getDamagePerBlock(getLevelPercent(p)));
        }
      }
      if (logsChopped > 0) {
        addStat(p, "axe.chop.trees-felled", 1);
        int felledLogs = logsChopped;
        int steps = Math.min(8, Math.max(3, felledLogs));
        Location base = target.getLocation().add(0.5D, 0.0D, 0.5D);
        timeline(base)
            .duration(steps)
            .priority(FxPriority.TRANSITION)
            .cullRadius(24)
            .frame((fx, tick, progress) -> {
              fx.particle(Particles.BLOCK_CRACK, 3, 0.0D, progress * felledLogs, 0.0D, 0.15D, 0.02D, b);
              if ((tick & 1) == 0) {
                fx.sound(Sound.ITEM_AXE_STRIP, 0.6F, (float) (0.9D + (0.5D * progress)));
              }
            })
            .start();
        if (logsChopped >= 30 && grantOnce(p, "challenge_axe_chop_one_swing")) {
          fx(p.getLocation().add(0.0D, 1.0D, 0.0D), FxPriority.TRANSITION)
              .column(Particles.TOTEM, 20, 2.0D)
              .chord(Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.0F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.6F);
        }
      }
    }
  }

  private int getRange(int level) {
    return level * getConfig().rangeLevelMultiplier;
  }

  private int getCooldownTime(double levelPercent) {
    return (int) (getConfig().cooldownTicksBase + (getConfig().cooldownTicksInverseLevelMultiplier * ((1D - levelPercent))));
  }

  private int getDamagePerBlock(double levelPercent) {
    return (int) (getConfig().damagePerBlockBase + (getConfig().damagePerBlockInverseLevelMultiplier * ((1D - levelPercent))));
  }

  private boolean breakStuff(Block b, int power, Player player) {
    Block last = b;
    for (int i = b.getY(); i < power + b.getY(); i++) {
      Block bb = b.getWorld().getBlockAt(b.getX(), i, b.getZ());
      if (isLog(new ItemStack(bb.getType()))) {
        last = bb;
      } else {
        break;
      }
    }

    if (!canBlockBreak(player, last.getLocation())) {
      return false;
    }

    if (!isLog(new ItemStack(last.getType()))) {
      return false;
    }

    player.breakBlock(last);
    return true;
  }


  @Override
  public void onTick() {

  }

  @ConfigDescription("Chop down trees by right-clicking the base log.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Range Level Multiplier for the Axe Chop adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int rangeLevelMultiplier = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Axe Chop adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Inverse Level Multiplier for the Axe Chop adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksInverseLevelMultiplier = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Per Block Base for the Axe Chop adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damagePerBlockBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Per Block Inverse Level Multiplier for the Axe Chop adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damagePerBlockInverseLevelMultiplier = 4;

    public Config() {
      baseCost = 3;
      costFactor = 0.35;
    }
  }
}
