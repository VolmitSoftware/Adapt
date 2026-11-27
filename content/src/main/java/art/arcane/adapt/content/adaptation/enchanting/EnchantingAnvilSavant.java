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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.PrepareAnvilEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;


public class EnchantingAnvilSavant extends SimpleAdaptation<EnchantingAnvilSavant.Config> {
  public static final Id ID = Id.adapt("enchanting/anvil_savant");
  private final Cooldowns actionbarCooldown = cooldowns();
  private final Map<UUID, Integer> pendingSaved = playerState();

  public EnchantingAnvilSavant() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("anvil"));
    setInterval(2200);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("anvil"))
        .key("challenge_enchanting_anvil_200")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("anvil"))
            .key("challenge_enchanting_anvil_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_enchanting_anvil_200", "enchanting.anvil-savant.levels-saved", 200, 400);
    registerMilestone("challenge_enchanting_anvil_5k", "enchanting.anvil-savant.levels-saved", 5000, 1500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getCostReduction(level), 0), 1);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PrepareAnvilEvent e) {
    PlayerRef p = e.player();
    if (p == null) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    int current = e.repairCost();
    if (current <= 0) {
      pendingSaved.remove(p.uuid());
      return;
    }

    int reduced = Math.max(getConfig().minimumCost, (int) Math.ceil(current * (1D - getCostReduction(level))));
    e.setRepairCost(reduced);
    int saved = current - reduced;
    if (saved > 0) {
      pendingSaved.put(p.uuid(), saved);
      if (actionbarCooldown.isReady(p.uuid(), 350L)) {
        actionbarCooldown.mark(p.uuid());
        HudStatus.show(p, TextFormat.GREEN + Text.key("adapt.enchanting.anvil_savant.saved_message").withMarkup("levels", String.valueOf(saved)).resolve());
      }
    } else {
      pendingSaved.remove(p.uuid());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(InventoryClickEvent e) {
    PlayerRef p = e.player();
    if (e.rawSlot() != 2 || e.viewKind() != InventoryKind.ANVIL) {
      return;
    }

    if (!ItemKinds.isItem(e.currentItem()) || e.action() == InventoryAction.NOTHING) {
      return;
    }

    Integer saved = pendingSaved.remove(p.uuid());
    if (saved != null && saved > 0) {
      addStat(p, "enchanting.anvil-savant.levels-saved", saved);
    }
  }

  private double getCostReduction(int level) {
    return Math.min(getConfig().maximumReduction, getConfig().reductionBase + (getLevelPercent(level) * getConfig().reductionFactor));
  }


  @ConfigDescription("Reduce anvil XP cost when combining, repairing, and renaming.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Reduction Base for the Enchanting Anvil Savant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reductionBase = 0.08;
    @ConfigDoc(value = "Controls Reduction Factor for the Enchanting Anvil Savant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reductionFactor = 0.37;
    @ConfigDoc(value = "Controls Maximum Reduction for the Enchanting Anvil Savant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maximumReduction = 0.65;
    @ConfigDoc(value = "Controls Minimum Cost for the Enchanting Anvil Savant adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int minimumCost = 1;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 4;
      initialCost = 2;
    }
  }
}
