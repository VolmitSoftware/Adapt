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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDamageEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Set;

public class PickaxeObsidianRush extends SimpleAdaptation<PickaxeObsidianRush.Config> {
  public static final Id ID = Id.adapt("pickaxe/obsidian_rush");
  private static final Id OBSIDIAN = Id.minecraft("obsidian");
  private static final Set<String> RUSH_PICKAXES = Set.of("diamond_pickaxe", "netherite_pickaxe");
  private static final Id CRYING_OBSIDIAN = Id.minecraft("crying_obsidian");

  private final PickaxeMiningBoost rush = new PickaxeMiningBoost(this, "rush", Attributes.BLOCK_BREAK_SPEED, playerState());

  public PickaxeObsidianRush() {
    super(ID);
    needs(PlatformNeed.attribute(Attributes.BLOCK_BREAK_SPEED));
    registerConfiguration(PickaxeObsidianRush.Config.class);
    setIcon(CRYING_OBSIDIAN);
    setInterval(6233);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(OBSIDIAN)
        .key("challenge_pickaxe_obsidianrush_1k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_pickaxe_obsidianrush_1k", "pickaxe.obsidian-rush.obsidian-mined", 1000, 500);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    if (getActiveLevel(player.player()) <= 0) {
      AdaptAttributeService.get().removeAll(player.player(), getId().toString());
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + Text.key("adapt.pickaxe.obsidian_rush.lore1").resolve());
    statLore(v, TextFormat.GREEN, "", getAmplifier(level) + 1, 2);
    v.addLore(TextFormat.ITALIC + Text.key("adapt.pickaxe.obsidian_rush.lore3").resolve());
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

  public static boolean isRushTarget(Id type) {
    return OBSIDIAN.equals(type) || CRYING_OBSIDIAN.equals(type);
  }

  /** A diamond or netherite pickaxe, or a modded pickaxe that mines obsidian. */
  public static boolean isRushPickaxe(ItemRef is) {
    return PickaxeTiers.reaches(is, RUSH_PICKAXES, OBSIDIAN);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(BlockDamageEvent e) {
    if (!isRushTarget(e.block().type())) {
      return;
    }

    PlayerRef p = e.player();
    if (!isRushPickaxe(p.inventory().mainHand())) {
      return;
    }

    Adaptation.BlockActionContext context = resolveInteractContext(p, e.block().location());
    if (context == null) {
      return;
    }

    int durationTicks = getConfig().durationTicks;
    if (durationTicks <= 0) {
      return;
    }

    if (rush.surge(p, rushSpeedBonus(getAmplifier(context.level())), durationTicks)) {
      Rgb surge = CRYING_OBSIDIAN.equals(e.block().type()) ? Rgb.of(0x8A2BE2) : Rgb.of(0x3B2E5A);
      timeline(p)
          .duration(6)
          .priority(FxPriority.TRANSITION)
          .frame((fxE, tick, progress) -> {
            fxE.dustHelix(surge, 0.6D, 2.2D, 12, progress * Math.PI * 2.0D, 1.2F);
            if (tick == 0 || tick == 2 || tick == 4) {
              fxE.sound(Sounds.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, (float) (0.7D + (progress * 0.4D)));
            }
          })
          .start();
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    BlockRef b = e.block();
    if (!isRushTarget(b.type())) {
      return;
    }

    PlayerRef p = e.player();
    if (!isRushPickaxe(p.inventory().mainHand()) || getActiveLevel(p) <= 0) {
      return;
    }

    addStat(p, "pickaxe.obsidian-rush.obsidian-mined", 1);
    Rgb tint = CRYING_OBSIDIAN.equals(b.type()) ? Rgb.of(0x8A2BE2) : Rgb.of(0x2A2140);
    fx(b.center(), FxPriority.COMBAT)
        .particle(Particles.blockCrack(b.state()), 8, 0, 0, 0, 0.2, 0.0)
        .dustBurst(tint, 4, 0.2, 1.2f)
        .sound(Sounds.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.7f, 1.2f);
  }

  @ConfigDescription("Gain a strong mining speed burst while mining obsidian with a diamond or netherite pickaxe.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Haste-equivalent amplifier added on top of the adaptation level while mining obsidian; each amplifier step adds 20% block break speed.", impact = "Higher values make obsidian mine faster at every level.")
    int amplifierBase = 3;
    @ConfigDoc(value = "Maximum haste-equivalent amplifier this adaptation can grant.", impact = "Higher values allow stronger mining speed at high levels.")
    int maxAmplifier = 7;
    @ConfigDoc(value = "Duration in ticks of the mining speed burst applied when damaging obsidian.", impact = "Higher values keep the burst active longer between swings.")
    int durationTicks = 120;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 3;
      initialCost = 1;
    }
  }
}
