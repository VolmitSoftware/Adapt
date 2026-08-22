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

package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.PickaxeMessages;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

public class PickaxeObsidianRush extends SimpleAdaptation<PickaxeObsidianRush.Config> {
  private static final String RUSH_SLOT = "rush";
  private static final long MILLIS_PER_TICK = 50L;

  private final Map<UUID, Long> rushActiveUntil = playerState();

  public PickaxeObsidianRush() {
    super("pickaxe-obsidian-rush");
    registerConfiguration(PickaxeObsidianRush.Config.class);
    setIcon(Material.CRYING_OBSIDIAN);
    setInterval(6233);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.OBSIDIAN)
        .key("challenge_pickaxe_obsidianrush_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_pickaxe_obsidianrush_1k", "pickaxe.obsidian-rush.obsidian-mined", 1000, 500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(PickaxeMessages.OBSIDIAN_RUSH_LORE1));
    statLore(v, C.GREEN, "", getAmplifier(level) + 1, 2);
    v.addLore(C.ITALIC + AdaptLanguage.text(PickaxeMessages.OBSIDIAN_RUSH_LORE3));
  }

  static int rushAmplifier(int amplifierBase, int maxAmplifier, int level) {
    return Math.min(maxAmplifier, amplifierBase + level);
  }

  static double rushSpeedBonus(int amplifier) {
    return 0.2D * (amplifier + 1);
  }

  private int getAmplifier(int level) {
    return rushAmplifier(getConfig().amplifierBase, getConfig().maxAmplifier, level);
  }

  private boolean isRushTarget(Material type) {
    return type == Material.OBSIDIAN || type == Material.CRYING_OBSIDIAN;
  }

  private boolean isRushPickaxe(ItemStack is) {
    if (!isItem(is)) {
      return false;
    }

    return switch (is.getType()) {
      case DIAMOND_PICKAXE, NETHERITE_PICKAXE -> true;
      default -> false;
    };
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockDamageEvent e) {
    if (!isRushTarget(e.getBlock().getType())) {
      return;
    }

    Player p = e.getPlayer();
    if (!isRushPickaxe(p.getInventory().getItemInMainHand())) {
      return;
    }

    Adaptation.BlockActionContext context = resolveInteractContext(p, e.getBlock().getLocation());
    if (context == null) {
      return;
    }

    int durationTicks = getConfig().durationTicks;
    if (durationTicks <= 0) {
      return;
    }

    long now = M.ms();
    Long previousExpiry = rushActiveUntil.put(p.getUniqueId(), now + (durationTicks * MILLIS_PER_TICK));
    boolean onset = previousExpiry == null || previousExpiry <= now;
    AdaptAttributeService.get().applyTimed(p, getName(), RUSH_SLOT, Attributes.BLOCK_BREAK_SPEED, rushSpeedBonus(getAmplifier(context.level())), AttributeModifier.Operation.MULTIPLY_SCALAR_1, durationTicks);
    if (onset) {
      Color surge = e.getBlock().getType() == Material.CRYING_OBSIDIAN ? Color.fromRGB(0x8A2BE2) : Color.fromRGB(0x3B2E5A);
      timeline(p)
          .duration(6)
          .priority(FxPriority.TRANSITION)
          .frame((fxE, tick, progress) -> {
            fxE.dustHelix(surge, 0.6D, 2.2D, 12, progress * Math.PI * 2.0D, 1.2F);
            if (tick == 0 || tick == 2 || tick == 4) {
              fxE.sound(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, (float) (0.7D + (progress * 0.4D)));
            }
          })
          .start();
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    Block b = e.getBlock();
    if (!isRushTarget(b.getType())) {
      return;
    }

    Player p = e.getPlayer();
    if (!isRushPickaxe(p.getInventory().getItemInMainHand()) || getActiveLevel(p) <= 0) {
      return;
    }

    addStat(p, "pickaxe.obsidian-rush.obsidian-mined", 1);
    Color tint = b.getType() == Material.CRYING_OBSIDIAN ? Color.fromRGB(0x8A2BE2) : Color.fromRGB(0x2A2140);
    fx(b.getLocation().add(0.5, 0.5, 0.5), FxPriority.COMBAT)
        .particle(Particles.BLOCK_CRACK, 8, 0, 0, 0, 0.2, 0.0, b.getBlockData())
        .dustBurst(tint, 4, 0.2, 1.2f)
        .sound(Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.7f, 1.2f);
  }


  @ConfigDescription("Gain a strong mining speed burst while mining obsidian with a diamond or netherite pickaxe.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Haste-equivalent amplifier added on top of the adaptation level while mining obsidian; each amplifier step adds 20% block break speed.", impact = "Higher values make obsidian mine faster at every level.")
    int amplifierBase = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum haste-equivalent amplifier this adaptation can grant.", impact = "Higher values allow stronger mining speed at high levels.")
    int maxAmplifier = 7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of the mining speed burst applied when damaging obsidian.", impact = "Higher values keep the burst active longer between swings.")
    int durationTicks = 120;

    public Config() {
      baseCost = 5;
      costFactor = 0.55;
      maxLevel = 3;
      initialCost = 4;
    }
  }
}
