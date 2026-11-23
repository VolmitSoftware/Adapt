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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.PickaxeMessages;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class PickaxeAutosmelt extends SimpleAdaptation<PickaxeAutosmelt.Config> {
  private static final Id SILK_TOUCH = Id.minecraft("silk_touch");

  public PickaxeAutosmelt() {
    super("pickaxe-autosmelt");
    registerConfiguration(PickaxeAutosmelt.Config.class);
    setLocalizationKey("pickaxe.auto_smelt");
    setIcon(Id.minecraft("raw_gold"));
    setInterval(7444);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("furnace"))
        .key("challenge_pickaxe_autosmelt_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("blast_furnace"))
            .key("challenge_pickaxe_autosmelt_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_pickaxe_autosmelt_1k", "pickaxe.autosmelt.ores-smelted", 1000, 400);
    registerMilestone("challenge_pickaxe_autosmelt_25k", "pickaxe.autosmelt.ores-smelted", 25000, 1500);
  }

  private static boolean replaceRawDrops(BlockDropItemEvent event, Id ore, Adaptation<?> source, int level) {
    Id ingot = getIngotFor(ore);
    Loc location = event.block().location();
    if (ingot == null || location.world() == null) {
      return false;
    }

    ItemEntityRef converted = null;
    int nativeAmount = 0;
    Iterator<ItemEntityRef> iterator = event.items().iterator();
    while (iterator.hasNext()) {
      ItemEntityRef item = iterator.next();
      if (!isNativeRawDrop(ore, item.item().type())) {
        continue;
      }
      nativeAmount += item.item().amount();
      if (converted == null) {
        converted = item;
      } else {
        iterator.remove();
        item.remove();
      }
    }
    if (converted == null) {
      return false;
    }

    int amount = getCommittedSmeltAmount(nativeAmount, level, ThreadLocalRandom.current().nextDouble());
    converted.setItem(AdaptCore.platform().host().items().create(ingot, amount));
    smeltFx(location, source, nativeAmount);
    return true;
  }

  static Id getIngotFor(Id ore) {
    return switch (vanillaPath(ore)) {
      case "iron_ore", "deepslate_iron_ore" -> Id.minecraft("iron_ingot");
      case "gold_ore", "deepslate_gold_ore" -> Id.minecraft("gold_ingot");
      case "copper_ore", "deepslate_copper_ore" -> Id.minecraft("copper_ingot");
      default -> null;
    };
  }

  static Id getRawDropFor(Id ore) {
    return switch (vanillaPath(ore)) {
      case "iron_ore", "deepslate_iron_ore" -> Id.minecraft("raw_iron");
      case "gold_ore", "deepslate_gold_ore" -> Id.minecraft("raw_gold");
      case "copper_ore", "deepslate_copper_ore" -> Id.minecraft("raw_copper");
      default -> null;
    };
  }

  static boolean isNativeRawDrop(Id ore, Id drop) {
    Id rawDrop = getRawDropFor(ore);
    return rawDrop != null && rawDrop.equals(drop);
  }

  static double getExtraDropChance(int level) {
    return (level * 1.25D) / 100.0D;
  }

  static int getCommittedSmeltAmount(int nativeAmount, int level, double roll) {
    return nativeAmount + (roll < getExtraDropChance(level) ? 1 : 0);
  }

  private static String vanillaPath(Id type) {
    return type != null && type.isMinecraft() ? type.path() : "";
  }

  private static void smeltFx(Loc location, Adaptation<?> source, int fortune) {
    Loc center = location.add(0.5, 0.5, 0.5);
    Fx.now(source, center, FxPriority.TRANSITION)
        .particle(Particles.FLAME, 6, 0, 0.1, 0, 0.16, 0.02)
        .particle(Particles.LAVA, 3, 0, 0.1, 0, 0.2, 0.0)
        .particle(Particles.SMOKE, 3, 0, 0.2, 0, 0.12, 0.02)
        .sound(Sounds.BLOCK_LAVA_POP, 1, 1);
    if (fortune > 1) {
      Fx.now(source, center, FxPriority.AMBIENT)
          .particle(Particles.WAX_ON, Math.min(8, fortune), 0, 0.2, 0, 0.22, 0.02)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, 1.9f);
    }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, PickaxePreferences.BYPASS, PickaxePreferences.MATERIALS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(PickaxeMessages.AUTO_SMELT_LORE1));
    statLore(v, C.GREEN, "", level * 1.25, 2);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockDropItemEvent e) {
    PlayerRef p = e.player();
    Id ore = e.brokenState().type();
    if ((preferenceEnabled(p, PickaxePreferences.BYPASS) && p.isSneaking())
        || !preference(p, PickaxePreferences.MATERIALS).accepts(ore)) {
      return;
    }
    if (getIngotFor(ore) == null || e.items().isEmpty()) {
      return;
    }

    ItemRef tool = p.inventory().mainHand();
    if (!isPickaxe(tool) || !e.brokenState().isPreferredTool(tool)) {
      return;
    }
    if (tool.enchantments().containsKey(SILK_TOUCH)) {
      return;
    }

    Adaptation.BlockActionContext context = resolveBlockBreakContext(p, e.block().location());
    if (context == null) {
      return;
    }

    if (!replaceRawDrops(e, ore, this, context.level())) {
      return;
    }
    addStat(p, "pickaxe.autosmelt.ores-smelted", 1);
  }

  @ConfigDescription("Automatically smelt mined ores with a chance for extra drops.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      maxLevel = 4;
      initialCost = 3;
    }
  }
}
