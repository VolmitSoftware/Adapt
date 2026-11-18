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

package art.arcane.adapt.content.adaptation.herbalism;

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
import art.arcane.adapt.localization.catalog.HerbalismMessages;
import art.arcane.adapt.localization.catalog.PickaxeMessages;
import art.arcane.adapt.platform.entity.GameMode;
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

public class HerbalismDropToInventory extends SimpleAdaptation<HerbalismDropToInventory.Config> {
  public static final PlayerPreference<HerbalismPreferences.Materials> MATERIALS = HerbalismPreferences.materials("materials", HerbalismMessages.PREFERENCE_HERBALISMDROPTOINVENTORY_MATERIALS);
  public static final PlayerPreference<CommonPreferences.Toggle> SEEDS_ONLY = CommonPreferences.toggle("seeds-only", HerbalismMessages.PREFERENCE_HERBALISMDROPTOINVENTORY_SEEDS_ONLY, CommonPreferences.Toggle.OFF);

  public HerbalismDropToInventory() {
    super("herbalism-drop-to-inventory");
    registerConfiguration(HerbalismDropToInventory.Config.class);
    setDescriptionKey(PickaxeMessages.DROP_TO_INVENTORY_DESCRIPTION);
    setDisplayNameKey(HerbalismMessages.DROP_TO_INVENTORY_NAME);
    setIcon(Id.minecraft("hopper"));
    setInterval(7999);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("chest"))
        .key("challenge_herbalism_dti_10k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_herbalism_dti_10k", "herbalism.drop-to-inv.items-caught", 10000, 500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, MATERIALS, SEEDS_ONLY);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GRAY + AdaptLanguage.text(PickaxeMessages.DROP_TO_INVENTORY_LORE1));
  }

  public boolean acceptsDrop(PlayerRef player, Id material) {
    return preference(player, MATERIALS).allows(material)
        && (!preferenceEnabled(player, SEEDS_ONLY) || HerbalismPreferences.isPlantingItem(material));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockDropItemEvent e) {
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p) || p.gameMode() != GameMode.SURVIVAL) {
      return;
    }

    if (!isHoe(p.inventory().mainHand())) {
      return;
    }

    List<ItemEntityRef> items = new ArrayList<>(e.items());
    if (items.isEmpty()) {
      return;
    }

    int stored = 0;
    boolean overflow = false;
    for (ItemEntityRef i : items) {
      ItemRef stack = i.item().copy();
      if (!acceptsDrop(p, stack.type())) {
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
      addStat(p, "herbalism.drop-to-inv.items-caught", 1);
      List<ItemRef> leftovers = p.inventory().addItem(stack);
      if (leftovers == null || leftovers.isEmpty()) {
        stored++;
      } else {
        overflow = true;
        for (ItemRef leftover : leftovers) {
          p.world().dropItem(p.location().position(), leftover);
        }
      }
    }

    if (stored > 0) {
      Loc source = e.block().center();
      Loc chest = p.eyeLocation().add(0, -0.4, 0);
      fx(source, FxPriority.TRANSITION)
          .trail(Particles.CRIT_MAGIC, chest.x() - source.x(), chest.y() - source.y(), chest.z() - source.z(), Math.min(6.0D, source.distance(chest)), 4)
          .particle(Particles.VILLAGER_HAPPY, 2, 0, 0.1D, 0, 0.15D, 0.02D)
          .chord(Sounds.BLOCK_NOTE_BLOCK_CHIME, 0.5F, 1.6F, Sounds.BLOCK_CALCITE_HIT, 0.3F, 1.2F);
    }

    if (overflow) {
      fx(p.location().add(0, 1, 0), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 2, 0.25D)
          .sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.4F, 0.8F);
    }
  }



  @ConfigDescription("Harvested crops drop directly into your inventory.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 1;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
