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

package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ExcavationMessages;
import art.arcane.adapt.localization.catalog.PickaxeMessages;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.ArrayList;
import java.util.List;

public class ExcavationDropToInventory extends SimpleAdaptation<ExcavationDropToInventory.Config> {
  public static final PlayerPreference<ExcavationPreferences.Materials> MATERIALS = ExcavationPreferences.materials("materials", ExcavationMessages.PREFERENCE_EXCAVATIONDROPTOINVENTORY_MATERIALS);
  public static final PlayerPreference<CommonPreferences.Toggle> BLOCKS_ONLY = CommonPreferences.toggle("blocks-only", ExcavationMessages.PREFERENCE_EXCAVATIONDROPTOINVENTORY_BLOCKS_ONLY, CommonPreferences.Toggle.OFF);

  public ExcavationDropToInventory() {
    super("excavation-drop-to-inventory");
    registerConfiguration(ExcavationDropToInventory.Config.class);
    setDescriptionKey(PickaxeMessages.DROP_TO_INVENTORY_DESCRIPTION);
    setDisplayNameKey(ExcavationMessages.DROP_TO_INVENTORY_NAME);
    setIcon(Id.minecraft("chest"));
    setInterval(11777);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("chest"))
        .key("challenge_excavation_dti_10k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_excavation_dti_10k", "excavation.drop-to-inv.items-caught", 10000, 500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, MATERIALS, BLOCKS_ONLY);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GRAY + AdaptLanguage.text(PickaxeMessages.DROP_TO_INVENTORY_LORE1));
  }


  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockDropItemEvent e) {
    PlayerRef p = e.player();
    if (resolveInteractBreakContext(p, e.block().location(), null, true) == null) {
      return;
    }
    if (!isShovel(p.inventory().mainHand())) {
      return;
    }

    List<ItemEntityRef> items = new ArrayList<>(e.items());
    int caught = 0;
    boolean overflow = false;
    for (ItemEntityRef i : items) {
      ItemRef stack = i.item().copy();
      if (preferenceEnabled(p, BLOCKS_ONLY) && !AdaptCore.platform().host().items().isBlock(stack.type())
          || !preference(p, MATERIALS).allows(stack.type())) {
        continue;
      }
      int remaining = ProtectionProbes.remainingAfterPickup(p.inventory(), stack);
      if (!ProtectionProbes.attemptBlockDropPickup(p, i, remaining, e.block().location()) || i.isDead()) {
        continue;
      }
      stack = i.item().copy();
      if (!e.items().remove(i)) {
        continue;
      }
      xp(p, 2);
      addStat(p, "excavation.drop-to-inv.items-caught", 1);
      List<ItemRef> leftovers = p.inventory().addItem(stack);
      if (leftovers != null && !leftovers.isEmpty()) {
        for (ItemRef leftover : leftovers) {
          p.world().dropItem(p.location().position(), leftover);
        }
        overflow = true;
      } else {
        caught++;
      }
    }

    if (caught > 0) {
      fx(p.location(), FxPriority.AMBIENT)
          .particle(Particles.ENCHANTMENT_TABLE, 4, 0, 1.0D, 0, 0.3D, 0.05D)
          .chord(Sounds.ENTITY_ITEM_PICKUP, 0.5f, 1.4f, Sounds.BLOCK_CALCITE_HIT, 0.3f, 1.6f);
    }

    if (overflow) {
      fx(p.location(), FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 2, 0, 1.0D, 0, 0.1D, 0.01D)
          .sound(Sounds.BLOCK_DISPENSER_FAIL, 0.4f, 1.0f);
    }
  }



  @ConfigDescription("Excavated blocks drop directly into your inventory.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 1;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 1;
    }
  }
}
