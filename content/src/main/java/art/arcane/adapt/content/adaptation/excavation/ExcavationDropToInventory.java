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

import art.arcane.adapt.api.AdaptApi;
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

import java.util.ArrayList;
import java.util.List;

public class ExcavationDropToInventory extends SimpleAdaptation<ExcavationDropToInventory.Config> {
  public static final Id ID = Id.adapt("excavation/drop_to_inventory");
  public static final PlayerPreference<ExcavationPreferences.Materials> MATERIALS = ExcavationPreferences.materials("materials", Text.key("adapt.excavation.preferences.excavationdroptoinventory.materials"));
  public static final PlayerPreference<CommonPreferences.Toggle> BLOCKS_ONLY = CommonPreferences.toggle("blocks-only", Text.key("adapt.excavation.preferences.excavationdroptoinventory.blocks-only"), CommonPreferences.Toggle.OFF);

  public ExcavationDropToInventory() {
    super(ID);
    registerConfiguration(ExcavationDropToInventory.Config.class);
    setDescriptionText(Text.key("adapt.pickaxe.drop_to_inventory.description"));
    setNameText(Text.key("adapt.excavation.drop_to_inventory.name"));
    setIcon(Id.minecraft("chest"));
    setInterval(11777);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("chest"))
        .key("challenge_excavation_dti_10k")
        .frame(AdvancementFrame.CHALLENGE)
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
    v.addLore(TextFormat.GRAY + Text.key("adapt.pickaxe.drop_to_inventory.lore1").resolve());
  }


  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockDropItemEvent e) {
    PlayerRef p = e.player();
    if (resolveInteractBreakContext(p, e.block().location(), null, true) == null) {
      return;
    }
    if (!ItemKinds.isShovel(p.inventory().mainHand())) {
      return;
    }

    List<ItemEntityRef> items = new ArrayList<>(e.items());
    int caught = 0;
    boolean overflow = false;
    for (ItemEntityRef i : items) {
      ItemRef stack = i.item().copy();
      if (preferenceEnabled(p, BLOCKS_ONLY) && !AdaptApi.platform().host().items().isBlock(stack.type())
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
          .particle(Particles.ENCHANT, 4, 0, 1.0D, 0, 0.3D, 0.05D)
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
