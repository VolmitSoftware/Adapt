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
import art.arcane.adapt.platform.entity.GameMode;
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

public class HerbalismDropToInventory extends SimpleAdaptation<HerbalismDropToInventory.Config> {
  public static final Id ID = Id.adapt("herbalism/drop_to_inventory");
  public static final PlayerPreference<HerbalismPreferences.Materials> MATERIALS = HerbalismPreferences.materials("materials", Text.key("adapt.herbalism.preferences.herbalismdroptoinventory.materials"));
  public static final PlayerPreference<CommonPreferences.Toggle> SEEDS_ONLY = CommonPreferences.toggle("seeds-only", Text.key("adapt.herbalism.preferences.herbalismdroptoinventory.seeds-only"), CommonPreferences.Toggle.OFF);

  public HerbalismDropToInventory() {
    super(ID);
    registerConfiguration(HerbalismDropToInventory.Config.class);
    setDescriptionText(Text.key("adapt.pickaxe.drop_to_inventory.description"));
    setNameText(Text.key("adapt.herbalism.drop_to_inventory.name"));
    setIcon(Id.minecraft("hopper"));
    setInterval(7999);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("chest"))
        .key("challenge_herbalism_dti_10k")
        .frame(AdvancementFrame.CHALLENGE)
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
    v.addLore(TextFormat.GRAY + Text.key("adapt.pickaxe.drop_to_inventory.lore1").resolve());
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

    if (!ItemKinds.isHoe(p.inventory().mainHand())) {
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
          .trail(Particles.CRIT, chest.x() - source.x(), chest.y() - source.y(), chest.z() - source.z(), Math.min(6.0D, source.distance(chest)), 4)
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
      initialCost = 1;
      baseCost = 1;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
