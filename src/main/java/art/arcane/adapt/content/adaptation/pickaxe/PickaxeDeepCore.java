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
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PickaxeDeepCore extends SimpleAdaptation<PickaxeDeepCore.Config> {
  private static final Set<Material> DEEPSLATE_BLOCKS = EnumSet.of(
      Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.POLISHED_DEEPSLATE,
      Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_TILES,
      Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_COPPER_ORE,
      Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_GOLD_ORE,
      Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE_LAPIS_ORE,
      Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_EMERALD_ORE);

  private static final String HASTE_SLOT = "haste";
  private static final long MILLIS_PER_TICK = 50L;

  private final Map<UUID, Long> hasteExpiresAt = playerState();

  public PickaxeDeepCore() {
    super("pickaxe-deep-core");
    registerConfiguration(PickaxeDeepCore.Config.class);
    setIcon(Material.DEEPSLATE);
    setInterval(5825);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DEEPSLATE)
        .key("challenge_pickaxe_deepcore_5k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_pickaxe_deepcore_5k", "pickaxe.deep-core.deepslate-mined", 5000, 400);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(PickaxeMessages.DEEP_CORE_LORE1));
    statLore(v, C.GREEN, "", getAmplifier(level) + 1, 2);
  }

  private int getAmplifier(int level) {
    return hasteAmplifier(getConfig().amplifierBase, getConfig().maxAmplifier, level);
  }

  static int hasteAmplifier(int amplifierBase, int maxAmplifier, int level) {
    return Math.min(maxAmplifier, amplifierBase + level - 1);
  }

  static double hasteMultiplier(int amplifier) {
    return 0.2D * (amplifier + 1);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockDamageEvent e) {
    if (!DEEPSLATE_BLOCKS.contains(e.getBlock().getType())) {
      return;
    }

    Player p = e.getPlayer();
    if (!isPickaxe(p.getInventory().getItemInMainHand())) {
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

    int amplifier = getAmplifier(context.level());
    long now = M.ms();
    boolean onset = hasteExpiresAt.getOrDefault(p.getUniqueId(), 0L) <= now;
    AdaptAttributeService.get().applyTimed(p, getName(), HASTE_SLOT, Attributes.BLOCK_BREAK_SPEED,
        hasteMultiplier(amplifier), AttributeModifier.Operation.MULTIPLY_SCALAR_1, durationTicks);
    hasteExpiresAt.put(p.getUniqueId(), now + durationTicks * MILLIS_PER_TICK);
    if (onset) {
      fx(p.getEyeLocation(), FxPriority.TRANSITION)
          .particle(Particle.CRIT, 4, 0, 0.3, 0, 0.2, 0.05)
          .particle(Particle.ELECTRIC_SPARK, 3, 0, 0.3, 0, 0.2, 0.03)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.4f);
      if (amplifier >= getConfig().maxAmplifier) {
        fx(p.getLocation(), FxPriority.TRANSITION).ring(Particle.WAX_ON, 0.6D, 12, 0.1D);
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    Block b = e.getBlock();
    if (!DEEPSLATE_BLOCKS.contains(b.getType())) {
      return;
    }

    Player p = e.getPlayer();
    if (!isPickaxe(p.getInventory().getItemInMainHand()) || getActiveLevel(p) <= 0) {
      return;
    }

    addStat(p, "pickaxe.deep-core.deepslate-mined", 1);
    if (Math.floorMod(b.getX() + b.getY() + b.getZ(), 3) == 0) {
      fx(b.getLocation().add(0.5, 0.5, 0.5), FxPriority.TRAIL)
          .particle(Particles.BLOCK_CRACK, 4, 0, 0, 0, 0.2, 0.0, b.getBlockData());
    }
  }


  @ConfigDescription("Gain Haste-equivalent mining speed while mining deepslate so it digs like normal stone.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Haste-equivalent amplifier granted at level 1 while mining deepslate.", impact = "Higher values make deepslate mine faster at every level.")
    int amplifierBase = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum Haste-equivalent amplifier this adaptation can grant.", impact = "Higher values allow stronger mining speed at high levels.")
    int maxAmplifier = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of the mining-speed boost applied when damaging deepslate.", impact = "Higher values keep the effect active longer between swings.")
    int durationTicks = 60;

    public Config() {
      costFactor = 0.5;
      maxLevel = 3;
      initialCost = 3;
    }
  }
}
