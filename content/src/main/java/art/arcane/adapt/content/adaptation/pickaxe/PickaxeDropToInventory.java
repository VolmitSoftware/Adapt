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
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.ArrayList;
import java.util.List;

public class PickaxeDropToInventory extends SimpleAdaptation<PickaxeDropToInventory.Config> {
  public static final Id ID = Id.adapt("pickaxe/drop_to_inventory");
  public PickaxeDropToInventory() {
    super(ID);
    registerConfiguration(PickaxeDropToInventory.Config.class);
    setIcon(Id.minecraft("minecart"));
    setInterval(7944);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("chest"))
        .key("challenge_pickaxe_dti_25k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_pickaxe_dti_25k", "pickaxe.drop-to-inv.items-caught", 25000, 500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, PickaxePreferences.BYPASS, PickaxePreferences.MATERIALS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GRAY + Text.key("adapt.pickaxe.drop_to_inventory.lore1").resolve());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockDropItemEvent e) {
    PlayerRef p = e.player();
    if (preferenceEnabled(p, PickaxePreferences.BYPASS) && p.isSneaking()) {
      return;
    }
    if (resolveBlockBreakContext(p, e.block().location(), null, true) == null) {
      return;
    }
    if (!ItemKinds.isPickaxe(p.inventory().mainHand())) {
      return;
    }
    List<ItemEntityRef> items = new ArrayList<>(e.items());
    int caught = 0;
    boolean overflow = false;
    for (ItemEntityRef i : items) {
      ItemRef stack = i.item().copy();
      if (!preference(p, PickaxePreferences.MATERIALS).accepts(stack.type())) {
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
      List<ItemRef> leftovers = p.inventory().addItem(stack);
      if (leftovers != null && !leftovers.isEmpty()) {
        for (ItemRef leftover : leftovers) {
          p.world().dropItem(p.location().position(), leftover);
        }
        overflow = true;
      }
      caught++;
    }
    if (caught > 0) {
      Loc target = p.location().add(0, 1.1D, 0);
      fx(e.block().center(), FxPriority.TRAIL)
          .line(Particles.WAX_ON, target.x(), target.y(), target.z(), 8)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.25f, 1.6f);
      addStat(p, "pickaxe.drop-to-inv.items-caught", caught);
    }
    if (overflow) {
      fx(p.location(), FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 2, 0, 0.1, 0, 0.05, 0.01)
          .sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.4f, 0.6f);
    }
  }

  @ConfigDescription("Mined blocks drop directly into your inventory.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 1;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 1;
    }
  }
}
