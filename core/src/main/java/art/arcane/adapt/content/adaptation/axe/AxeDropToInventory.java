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

package art.arcane.adapt.content.adaptation.axe;

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
import art.arcane.adapt.localization.catalog.AxeMessages;
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

import java.util.ArrayList;
import java.util.List;

public class AxeDropToInventory extends SimpleAdaptation<AxeDropToInventory.Config> {
  public AxeDropToInventory() {
    super("axe-drop-to-inventory");
    registerConfiguration(AxeDropToInventory.Config.class);
    setDescriptionKey(PickaxeMessages.DROP_TO_INVENTORY_DESCRIPTION);
    setDisplayNameKey(AxeMessages.DROP_TO_INVENTORY_NAME);
    setIcon(Id.minecraft("barrel"));
    setInterval(8800);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("chest"))
        .key("challenge_axe_dti_5k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_axe_dti_5k", "axe.drop-to-inv.items-caught", 5000, 500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, AxePreferences.ITEMS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GRAY + AdaptLanguage.text(PickaxeMessages.DROP_TO_INVENTORY_LORE1));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockDropItemEvent e) {
    PlayerRef p = e.player();
    if (resolveBlockBreakContext(p, e.block().location(), null, true) == null) {
      return;
    }

    if (!isAxe(p.inventory().mainHand())) {
      return;
    }

    Id broken = e.brokenState().type();
    if (!isLog(broken) && !isLeaves(broken)) {
      return;
    }

    List<ItemEntityRef> items = new ArrayList<>(e.items());
    int caught = 0;
    boolean overflow = false;
    for (ItemEntityRef i : items) {
      if (!preference(p, AxePreferences.ITEMS).accepts(i.item().type())) {
        continue;
      }
      ItemRef stack = i.item().copy();
      int remaining = ProtectionProbes.remainingAfterPickup(p.inventory(), stack);
      if (!ProtectionProbes.attemptBlockDropPickup(p, i, remaining, e.block().location()) || i.isDead()) {
        continue;
      }
      stack = i.item().copy();
      if (!e.items().remove(i)) {
        continue;
      }
      List<ItemRef> leftovers = p.inventory().addItem(stack);
      if (leftovers != null && !leftovers.isEmpty()) {
        for (ItemRef leftover : leftovers) {
          p.world().dropItem(p.location().position(), leftover);
        }
        overflow = true;
      }
      caught++;
    }
    if (caught <= 0) {
      return;
    }

    addStat(p, "axe.drop-to-inv.items-caught", caught);
    int caughtCount = caught;
    Loc from = e.block().location().add(0.5D, 0.5D, 0.5D);
    Loc to = p.location().add(0.0D, 1.0D, 0.0D);
    double dx = to.x() - from.x();
    double dy = to.y() - from.y();
    double dz = to.z() - from.z();
    timeline(from)
        .duration(5)
        .priority(FxPriority.TRAIL)
        .cullRadius(16)
        .frame((fx, tick, progress) -> {
          fx.particle(Particles.ENCHANTMENT_TABLE, 2, dx * progress, dy * progress, dz * progress, 0.05D, 0.0D);
          if (tick == 0) {
            fx.chord(Sounds.ENTITY_ITEM_PICKUP, 0.5F, (float) Math.min(2.0D, 1.2D + (caughtCount * 0.03D)), Sounds.BLOCK_AMETHYST_BLOCK_HIT, 0.3F, 1.8F);
          }
        })
        .start();
    if (overflow) {
      fx(to, FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 3, 0.2D)
          .sound(Sounds.BLOCK_DISPENSER_FAIL, 0.4F, 0.8F);
    }
  }

  @ConfigDescription("Chopped wood drops directly into your inventory.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 1;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 3;
    }
  }
}
