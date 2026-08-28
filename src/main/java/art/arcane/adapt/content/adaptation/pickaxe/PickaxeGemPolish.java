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
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

public class PickaxeGemPolish extends SimpleAdaptation<PickaxeGemPolish.Config> {
  public PickaxeGemPolish() {
    super("pickaxe-gem-polish");
    registerConfiguration(PickaxeGemPolish.Config.class);
    setIcon(Material.DRAGON_HEAD);
    setInterval(6844);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.DRAGON_HEAD)
        .key("challenge_pickaxe_gempolish_25")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_pickaxe_gempolish_25", "pickaxe.gem-polish.trophies-polished", 25, 400);
  }

  @Override
  public void addStats(int level, Element v) {
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

  static boolean isEligibleTrophy(Material type, boolean headsEnabled, boolean dragonEggEnabled) {
    return switch (type) {
      case SKELETON_SKULL, SKELETON_WALL_SKULL,
           WITHER_SKELETON_SKULL, WITHER_SKELETON_WALL_SKULL,
           ZOMBIE_HEAD, ZOMBIE_WALL_HEAD,
           PLAYER_HEAD, PLAYER_WALL_HEAD,
           CREEPER_HEAD, CREEPER_WALL_HEAD,
           DRAGON_HEAD, DRAGON_WALL_HEAD,
           PIGLIN_HEAD, PIGLIN_WALL_HEAD -> headsEnabled;
      case DRAGON_EGG -> dragonEggEnabled;
      default -> false;
    };
  }

  static int rewardXp(int level, int atLevelOne, int perAdditionalLevel, int maximum) {
    long reward = Math.max(0, atLevelOne) + ((long) Math.max(0, level - 1) * Math.max(0, perAdditionalLevel));
    return (int) Math.min(Math.max(0, maximum), reward);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    Config config = getConfig();
    Material trophy = e.getBlock().getType();
    if (!isEligibleTrophy(trophy, config.headsEnabled, config.dragonEggEnabled)) {
      return;
    }

    Player p = e.getPlayer();
    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!isPickaxe(hand)) {
      return;
    }

    if (config.rejectPlayerModifiedBlocks && XpProvenance.hasPermanentPlayerModification(e.getBlock())) {
      return;
    }

    Adaptation.BlockActionContext context = resolveBlockBreakContext(p, e.getBlock().getLocation());
    if (context == null) {
      return;
    }

    Location drop = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
    int bonusXp = rewardXp(context.level(), config.vanillaXpAtLevelOne, config.vanillaXpPerAdditionalLevel, config.maximumXpPerTrophy);
    if (bonusXp <= 0) {
      return;
    }

    e.getBlock().getWorld().spawn(drop, org.bukkit.entity.ExperienceOrb.class).setExperience(bonusXp);
    fx(drop, FxPriority.AMBIENT)
        .column(Particles.END_ROD, 4, 0.8D)
        .sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.4f);

    addStat(p, "pickaxe.gem-polish.trophies-polished", 1);
    Color trophyColor = trophyColor(trophy);
    timeline(drop)
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .frame((fxE, tick, progress) -> {
          double radius = 0.7D - (0.5D * progress);
          fxE.ring(Particles.END_ROD, radius, 4, 0.4D);
          fxE.dustRing(trophyColor, radius, 6, 1.0F);
          if (tick == 0) {
            fxE.chord(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.6f, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.2f);
          }
        })
        .start();
  }

  private static Color trophyColor(Material trophy) {
    return switch (trophy) {
      case DRAGON_EGG, DRAGON_HEAD, DRAGON_WALL_HEAD -> Color.fromRGB(0x9B59B6);
      case WITHER_SKELETON_SKULL, WITHER_SKELETON_WALL_SKULL -> Color.fromRGB(0x4B4B4B);
      default -> Color.fromRGB(0xD6C4A1);
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
