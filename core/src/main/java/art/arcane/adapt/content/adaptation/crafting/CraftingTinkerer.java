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

package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CraftingMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class CraftingTinkerer extends SimpleAdaptation<CraftingTinkerer.Config> {

  public CraftingTinkerer() {
    super("crafting-tinkerer");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("anvil"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_pickaxe"))
        .key("challenge_crafting_tinkerer_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_pickaxe"))
            .key("challenge_crafting_tinkerer_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_crafting_tinkerer_50", "crafting.tinkerer.tools-tinkered", 50, 400);
    registerMilestone("challenge_crafting_tinkerer_500", "crafting.tinkerer.tools-tinkered", 500, 1500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(CraftingMessages.TINKERER_LORE1));
    statLore(v, Form.pc(getPreserveChance(level), 0), 2);
    v.addLore(C.YELLOW + "~ " + C.GRAY + AdaptLanguage.text(CraftingMessages.TINKERER_LORE3));
  }

  static double preserveChance(double base, double factor, double levelPercent) {
    return Math.min(1.0, base + (levelPercent * factor));
  }

  private double getPreserveChance(int level) {
    return preserveChance(getConfig().preserveChanceBase, getConfig().preserveChanceFactor, getLevelPercent(level));
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(CraftItemEvent e) {
    PlayerRef p = e.player();

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    ItemRef result = e.currentItem();
    if (result == null || result.isEmpty() || result.type().isAir()) {
      return;
    }

    List<ItemRef> inputs = repairInputs(e.matrix(), result.type());
    if (inputs == null) {
      return;
    }

    Map<Id, Integer> merged = mergeEnchants(inputs);
    if (merged.isEmpty()) {
      return;
    }

    boolean lossless = level >= getMaxLevel() || ThreadLocalRandom.current().nextDouble() <= getPreserveChance(level);
    if (!lossless) {
      dropRandomEnchant(merged);
    }

    if (merged.isEmpty()) {
      return;
    }

    ItemRef tinkered = result.copy();
    for (Map.Entry<Id, Integer> entry : merged.entrySet()) {
      tinkered.setEnchantment(entry.getKey(), entry.getValue());
    }
    e.setCurrentItem(tinkered);

    addStat(p, "crafting.tinkerer.tools-tinkered", 1);
    fx(p.location().add(0, 1, 0), FxPriority.TRANSITION)
        .particle(Particles.ENCHANTMENT_TABLE, 12, 0, 0.3D, 0, 0.4D, 0.4D)
        .burst(Particles.CRIT_MAGIC, 6, 0.2D)
        .chord(Sounds.BLOCK_ANVIL_USE, 0.5F, 1.1F, Sounds.BLOCK_ENCHANTMENT_TABLE_USE, 0.5F, lossless ? 1.6F : 1.0F);
  }

  private List<ItemRef> repairInputs(List<ItemRef> matrix, Id resultType) {
    List<ItemRef> inputs = new ArrayList<>(2);
    for (ItemRef item : matrix) {
      if (item == null || item.isEmpty() || item.type().isAir()) {
        continue;
      }
      if (!item.type().equals(resultType)) {
        return null;
      }
      inputs.add(item);
      if (inputs.size() > 2) {
        return null;
      }
    }
    return inputs.size() == 2 ? inputs : null;
  }

  static Map<Id, Integer> mergeEnchants(List<ItemRef> inputs) {
    Map<Id, Integer> merged = new HashMap<>();
    for (ItemRef input : inputs) {
      mergeEnchants(merged, input.enchantments());
    }
    return merged;
  }

  static <T> Map<T, Integer> mergeEnchants(Map<T, Integer> merged, Map<T, Integer> incoming) {
    for (Map.Entry<T, Integer> entry : incoming.entrySet()) {
      merged.merge(entry.getKey(), entry.getValue(), Math::max);
    }
    return merged;
  }

  private void dropRandomEnchant(Map<Id, Integer> merged) {
    int target = ThreadLocalRandom.current().nextInt(merged.size());
    int index = 0;
    Id chosen = null;
    for (Id enchantment : merged.keySet()) {
      if (index == target) {
        chosen = enchantment;
        break;
      }
      index++;
    }
    if (chosen != null) {
      merged.remove(chosen);
    }
  }

  @ConfigDescription("Combine two damaged tools of the same type in the grid to keep their best enchantments instead of stripping them.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Chance to preserve every enchantment at level 1.", impact = "Higher values keep the full enchantment set more often at low levels.")
    double preserveChanceBase = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional preservation chance gained across levels.", impact = "Higher values reach lossless preservation sooner.")
    double preserveChanceFactor = 0.6;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}
