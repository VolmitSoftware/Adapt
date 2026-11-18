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
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.PickaxeMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

public class PickaxeGemPolish extends SimpleAdaptation<PickaxeGemPolish.Config> {
  public PickaxeGemPolish() {
    super("pickaxe-gem-polish");
    registerConfiguration(PickaxeGemPolish.Config.class);
    setIcon(Id.minecraft("dragon_head"));
    setInterval(6844);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("dragon_head"))
        .key("challenge_pickaxe_gempolish_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_pickaxe_gempolish_25", "pickaxe.gem-polish.trophies-polished", 25, 400);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(PickaxeMessages.GEM_POLISH_LORE1));
    v.addLore(C.GRAY + AdaptLanguage.text(PickaxeMessages.GEM_POLISH_LORE2));
    statLore(v, rewardXp(level, getConfig().vanillaXpAtLevelOne, getConfig().vanillaXpPerAdditionalLevel, getConfig().maximumXpPerTrophy), 3);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.normalizeForPersistence();
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  static boolean isEligibleTrophy(Id type, boolean headsEnabled, boolean dragonEggEnabled) {
    if (type == null || !type.isMinecraft()) {
      return false;
    }
    return switch (type.path()) {
      case "skeleton_skull", "skeleton_wall_skull",
           "wither_skeleton_skull", "wither_skeleton_wall_skull",
           "zombie_head", "zombie_wall_head",
           "player_head", "player_wall_head",
           "creeper_head", "creeper_wall_head",
           "dragon_head", "dragon_wall_head",
           "piglin_head", "piglin_wall_head" -> headsEnabled;
      case "dragon_egg" -> dragonEggEnabled;
      default -> false;
    };
  }

  static int rewardXp(int level, int atLevelOne, int perAdditionalLevel, int maximum) {
    long reward = Math.max(0, atLevelOne) + ((long) Math.max(0, level - 1) * Math.max(0, perAdditionalLevel));
    return (int) Math.min(Math.max(0, maximum), reward);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    Config config = getConfig();
    Id trophy = e.block().type();
    if (!isEligibleTrophy(trophy, config.headsEnabled, config.dragonEggEnabled)) {
      return;
    }

    PlayerRef p = e.player();
    ItemRef hand = p.inventory().mainHand();
    if (!isPickaxe(hand)) {
      return;
    }

    if (config.rejectPlayerModifiedBlocks && XpProvenance.hasPermanentPlayerModification(e.block())) {
      return;
    }

    Adaptation.BlockActionContext context = resolveBlockBreakContext(p, e.block().location());
    if (context == null) {
      return;
    }

    Loc drop = e.block().center();
    int bonusXp = rewardXp(context.level(), config.vanillaXpAtLevelOne, config.vanillaXpPerAdditionalLevel, config.maximumXpPerTrophy);
    if (bonusXp <= 0) {
      return;
    }

    e.block().world().spawnExperience(drop.position(), bonusXp);
    fx(drop, FxPriority.AMBIENT)
        .column(Particles.END_ROD, 4, 0.8D)
        .sound(Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.4f);

    addStat(p, "pickaxe.gem-polish.trophies-polished", 1);
    Rgb trophyColor = trophyColor(trophy);
    timeline(drop)
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .frame((fxE, tick, progress) -> {
          double radius = 0.7D - (0.5D * progress);
          fxE.ring(Particles.END_ROD, radius, 4, 0.4D);
          fxE.dustRing(trophyColor, radius, 6, 1.0F);
          if (tick == 0) {
            fxE.chord(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.6f, Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.2f);
          }
        })
        .start();
  }

  private static Rgb trophyColor(Id trophy) {
    return switch (trophy.path()) {
      case "dragon_egg", "dragon_head", "dragon_wall_head" -> Rgb.of(0x9B59B6);
      case "wither_skeleton_skull", "wither_skeleton_wall_skull" -> Rgb.of(0x4B4B4B);
      default -> Rgb.of(0xD6C4A1);
    };
  }

  @ConfigDescription("Mining naturally generated heads, skulls, and dragon eggs grants a bounded vanilla XP reward.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Allows naturally generated standing and wall head or skull blocks to grant XP.", impact = "Disable to restrict the adaptation to dragon eggs.")
    boolean headsEnabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Allows naturally generated dragon egg blocks to grant XP.", impact = "Disable to restrict the adaptation to heads and skulls.")
    boolean dragonEggEnabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Permanently rejects blocks with player placement or break provenance.", impact = "Keep enabled to prevent placing and repeatedly mining the same trophy for XP.")
    boolean rejectPlayerModifiedBlocks = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Vanilla XP points granted by a level-one trophy polish.", impact = "Higher values increase the base trophy reward.")
    int vanillaXpAtLevelOne = 7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Vanilla XP points added for each adaptation level after level one.", impact = "Higher values make the trophy reward scale faster with adaptation level.")
    int vanillaXpPerAdditionalLevel = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum vanilla XP points granted by one trophy.", impact = "Caps oversized or heavily scaled rewards.")
    int maximumXpPerTrophy = 24;

    public Config() {
      baseCost = 6;
      costFactor = 0.7;
      initialCost = 4;
      normalizeForPersistence();
    }

    void normalizeForPersistence() {
      vanillaXpAtLevelOne = Math.max(0, Math.min(vanillaXpAtLevelOne, 100000));
      vanillaXpPerAdditionalLevel = Math.max(0, Math.min(vanillaXpPerAdditionalLevel, 100000));
      maximumXpPerTrophy = Math.max(0, Math.min(maximumXpPerTrophy, 100000));
    }
  }
}
