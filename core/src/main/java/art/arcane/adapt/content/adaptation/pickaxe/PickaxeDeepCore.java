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

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.PickaxeMessages;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDamageEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.math.M;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PickaxeDeepCore extends SimpleAdaptation<PickaxeDeepCore.Config> {
  private static final Set<Id> DEEPSLATE_BLOCKS = Set.of(
      Id.minecraft("deepslate"), Id.minecraft("cobbled_deepslate"), Id.minecraft("polished_deepslate"),
      Id.minecraft("deepslate_bricks"), Id.minecraft("deepslate_tiles"),
      Id.minecraft("deepslate_coal_ore"), Id.minecraft("deepslate_copper_ore"),
      Id.minecraft("deepslate_iron_ore"), Id.minecraft("deepslate_gold_ore"),
      Id.minecraft("deepslate_redstone_ore"), Id.minecraft("deepslate_lapis_ore"),
      Id.minecraft("deepslate_diamond_ore"), Id.minecraft("deepslate_emerald_ore"));

  private static final String HASTE_SLOT = "haste";
  private static final long MILLIS_PER_TICK = 50L;

  private final Map<UUID, Long> hasteExpiresAt = playerState();

  public PickaxeDeepCore() {
    super("pickaxe-deep-core");
    registerConfiguration(PickaxeDeepCore.Config.class);
    setIcon(Id.minecraft("deepslate"));
    setInterval(5825);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("deepslate"))
        .key("challenge_pickaxe_deepcore_5k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_pickaxe_deepcore_5k", "pickaxe.deep-core.deepslate-mined", 5000, 400);
  }

  @Override
  public void addStats(int level, StatLines v) {
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

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockDamageEvent e) {
    BlockRef block = e.block();
    if (!DEEPSLATE_BLOCKS.contains(block.type())) {
      return;
    }

    PlayerRef p = e.player();
    if (!isPickaxe(p.inventory().mainHand())) {
      return;
    }

    Adaptation.BlockActionContext context = resolveInteractContext(p, block.location());
    if (context == null) {
      return;
    }

    int durationTicks = getConfig().durationTicks;
    if (durationTicks <= 0) {
      return;
    }

    int amplifier = getAmplifier(context.level());
    long now = M.ms();
    boolean onset = hasteExpiresAt.getOrDefault(p.uuid(), 0L) <= now;
    AdaptAttributeService.get().applyTimed(p, getName(), HASTE_SLOT, Attributes.BLOCK_BREAK_SPEED,
        hasteMultiplier(amplifier), AttributeOperation.ADD_MULTIPLIED_TOTAL, durationTicks);
    hasteExpiresAt.put(p.uuid(), now + durationTicks * MILLIS_PER_TICK);
    if (onset) {
      fx(p.eyeLocation(), FxPriority.TRANSITION)
          .particle(Particles.CRIT, 4, 0, 0.3, 0, 0.2, 0.05)
          .particle(Particles.ELECTRIC_SPARK, 3, 0, 0.3, 0, 0.2, 0.03)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.4f);
      if (amplifier >= getConfig().maxAmplifier) {
        fx(p.location(), FxPriority.TRANSITION).ring(Particles.WAX_ON, 0.6D, 12, 0.1D);
      }
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    BlockRef b = e.block();
    if (!DEEPSLATE_BLOCKS.contains(b.type())) {
      return;
    }

    PlayerRef p = e.player();
    if (!isPickaxe(p.inventory().mainHand()) || getActiveLevel(p) <= 0) {
      return;
    }

    addStat(p, "pickaxe.deep-core.deepslate-mined", 1);
    if (Math.floorMod(b.x() + b.y() + b.z(), 3) == 0) {
      fx(b.center(), FxPriority.TRAIL)
          .particle(Particles.blockCrack(b.state()), 4, 0, 0, 0, 0.2, 0.0);
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
