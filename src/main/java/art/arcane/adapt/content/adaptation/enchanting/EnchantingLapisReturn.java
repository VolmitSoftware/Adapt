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

package art.arcane.adapt.content.adaptation.enchanting;

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
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class EnchantingLapisReturn extends SimpleAdaptation<EnchantingLapisReturn.Config> {
  private final Cooldowns cooldown = cooldowns();

  public EnchantingLapisReturn() {
    super("enchanting-lapis-return");
    registerConfiguration(Config.class);
    setIcon(Material.LAPIS_LAZULI);
    setInterval(20999);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.LAPIS_LAZULI)
        .key("challenge_enchanting_lapis_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.LAPIS_BLOCK)
            .key("challenge_enchanting_lapis_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_enchanting_lapis_100", "enchanting.lapis-return.lapis-saved", 100, 300);
    registerMilestone("challenge_enchanting_lapis_2500", "enchanting.lapis-return.lapis-saved", 2500, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("enchanting.lapis_return.lore1"));
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void on(EnchantItemEvent e) {
    Player p = e.getEnchanter();
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (ThreadLocalRandom.current().nextDouble() < getRefundChance(level)) {
      UUID playerId = p.getUniqueId();
      if (!cooldown.isReady(playerId, 20000L)) {
        return;
      }

      cooldown.mark(playerId);
      Location drop = p.getLocation();
      p.getWorld().dropItemNaturally(drop, new ItemStack(Material.LAPIS_LAZULI, level));
      addStat(p, "enchanting.lapis-return.lapis-saved", level);
      lapisRefundFx(e, drop);
    }
  }

  private double getRefundChance(int level) {
    return Math.min(getConfig().maxRefundChance, getConfig().refundChanceBase + (getLevelPercent(level) * getConfig().refundChanceFactor));
  }

  private void lapisRefundFx(EnchantItemEvent e, Location drop) {
    Color lapis = Color.fromRGB(38, 97, 156);
    ItemStack fragment = new ItemStack(Material.LAPIS_LAZULI);
    timeline(drop)
        .duration(6)
        .priority(FxPriority.TRANSITION)
        .cullRadius(16.0D)
        .frame((f, tick, progress) -> {
          f.ring(Particles.REDSTONE, (0.6D * (1.0D - progress)) + 0.15D, 10, 0.1D + (0.8D * progress), new Particle.DustOptions(lapis, 1.0F));
          if (tick == 0) {
            f.particle(Particles.ITEM_CRACK, 5, 0, 0.2D, 0, 0.25D, 0.05D, fragment)
                .chord(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7F, 0.9F, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5F, 1.3F);
          }
        })
        .start();
    Block enchantBlock = e.getEnchantBlock();
    if (enchantBlock != null) {
      fx(enchantBlock.getLocation().add(0.5D, 1.0D, 0.5D), FxPriority.AMBIENT)
          .particle(Particle.GLOW, 3, 0, 0, 0, 0.1D, 0.02D);
    }
  }


  @ConfigDescription("Chance to refund lapis when enchanting, scaling with level.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base chance to refund lapis on an enchant.", impact = "Higher values refund lapis more often at every level.")
    double refundChanceBase = 0.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional refund chance granted as the adaptation levels up.", impact = "Higher values make high levels refund lapis more often.")
    double refundChanceFactor = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Upper bound on the lapis refund chance.", impact = "Higher values allow the scaled chance to climb further.")
    double maxRefundChance = 0.4;

    public Config() {
      baseCost = 5;
      costFactor = 0.9;
      maxLevel = 3;
    }
  }
}
