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

package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.golem.GolemKillCredit;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.HunterMessages;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class HunterDropToInventory extends SimpleAdaptation<HunterDropToInventory.Config> {
  private final Cooldowns catchFx = cooldowns();

  public HunterDropToInventory() {
    super("hunter-drop-to-inventory");
    registerConfiguration(HunterDropToInventory.Config.class);
    setIcon(Id.minecraft("trapped_chest"));
    setInterval(18440);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("chest"))
        .key("challenge_hunter_dti_10k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_hunter_dti_10k", "hunter.drop-to-inv.items-caught", 10000, 500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, HunterPreferences.BLOCK_DROPS, HunterPreferences.MOB_DROPS, HunterPreferences.ITEMS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GRAY + AdaptLanguage.text(HunterMessages.DROP_TO_INVENTORY_LORE1));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockDropItemEvent e) {
    PlayerRef p = e.player();
    Loc blockLocation = e.block().location();
    if (!preferenceEnabled(p, HunterPreferences.BLOCK_DROPS) || resolveInteractContext(p, blockLocation, null, true) == null
        || !canBlockBreak(p, blockLocation)) {
      return;
    }

    if (isSword(p.inventory().mainHand())) {
      List<ItemEntityRef> items = new ArrayList<>(e.items());
      int caught = 0;
      for (ItemEntityRef i : items) {
        if (!preference(p, HunterPreferences.ITEMS).accepts(i.item().type())) {
          continue;
        }
        ItemRef stack = i.item().copy();
        int remaining = ProtectionProbes.remainingAfterPickup(p.inventory(), stack);
        if (!ProtectionProbes.attemptBlockDropPickup(p, i, remaining, blockLocation) || i.isDead()) {
          continue;
        }
        stack = i.item().copy();
        if (!e.items().remove(i)) {
          continue;
        }
        dropLeftovers(p, p.inventory().addItem(stack));
        caught++;
      }
      if (caught > 0) {
        addStat(p, "hunter.drop-to-inv.items-caught", caught);
        emitCatch(p, e.block().center(), caught);
      }
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDeathEvent e) {
    LivingRef k = e.entity();
    PlayerRef p = e.killer();
    if (p == null || GolemKillCredit.isGolemKill(k)) {
      return;
    }
    if (!preferenceEnabled(p, HunterPreferences.MOB_DROPS) || k instanceof PlayerRef || getActiveDamageLevel(p, k) <= 0) {
      return;
    }
    if (!p.isFakePlayer()) {
      int itemCount = 0;
      Iterator<ItemRef> drops = e.drops().iterator();
      while (drops.hasNext()) {
        ItemRef item = drops.next();
        if (!preference(p, HunterPreferences.ITEMS).accepts(item.type())) {
          continue;
        }
        dropLeftovers(p, p.inventory().addItem(item));
        drops.remove();
        itemCount++;
      }
      addStat(p, "hunter.drop-to-inv.items-caught", itemCount);
      emitCatch(p, k.location(), itemCount);
    }
  }

  private static void dropLeftovers(PlayerRef p, List<ItemRef> leftovers) {
    if (leftovers == null) {
      return;
    }
    for (ItemRef leftover : leftovers) {
      p.world().dropItem(p.location().position(), leftover);
    }
  }

  private void emitCatch(PlayerRef p, Loc origin, int itemCount) {
    UUID id = p.uuid();
    if (!catchFx.isReady(id, 150L)) {
      return;
    }
    catchFx.mark(id);

    Loc to = p.location().add(0, 1.0D, 0);
    fx(origin, FxPriority.AMBIENT)
        .particle(Particles.WAX_ON, 4, 0, 0.2D, 0, 0.15D, 0)
        .line(Particles.GLOW, to.x(), to.y(), to.z(), 3)
        .chord(Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4F, 1.4F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.35F, 1.8F);

    if (itemCount >= 6) {
      timeline(p)
          .duration(3)
          .priority(FxPriority.AMBIENT)
          .cullRadius(20)
          .frame((f, tick, progress) -> {
            f.helix(Particles.GLOW, 0.5D, 1.6D, 12, progress * Math.PI * 2.0D);
            if (tick == 0) {
              f.sound(Sounds.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.05F);
            }
          })
          .start();
    }
  }


  @ConfigDescription("Mob and block drops teleport directly into your inventory.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 1;
      costFactor = 1;
      maxLevel = 1;
    }
  }
}
