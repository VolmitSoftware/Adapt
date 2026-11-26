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
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class PickaxeSilkSpawner extends SimpleAdaptation<PickaxeSilkSpawner.Config> {
  public static final Id SPAWNER = Id.minecraft("spawner");
  private static final Id SILK_TOUCH = Id.minecraft("silk_touch");
  private final Map<DropKey, DropPlan> pendingDrops = new ConcurrentHashMap<>();

  public PickaxeSilkSpawner() {
    super("pickaxe-silk-spawner");
    registerConfiguration(PickaxeSilkSpawner.Config.class);
    setIcon(SPAWNER);
    setInterval(8444);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(SPAWNER)
        .key("challenge_pickaxe_spawner_10")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(SPAWNER)
            .key("challenge_pickaxe_spawner_50")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_pickaxe_spawner_10", "pickaxe.silk-spawner.spawners-collected", 10, 500);
    registerMilestone("challenge_pickaxe_spawner_50", "pickaxe.silk-spawner.spawners-collected", 50, 2000);
  }

  @Handle(priority = EventPriority.HIGH)
  public void onBlockDropPrepare(BlockDropItemEvent event) {
    PlayerRef player = event.player();
    if (preferenceEnabled(player, PickaxePreferences.REQUIRE_SNEAK) && !player.isSneaking()) {
      return;
    }
    BlockRef block = event.block();
    Adaptation.BlockActionContext context = resolveBlockBreakContext(player, block.location());
    if (!SPAWNER.equals(event.brokenState().type()) || context == null) {
      return;
    }
    ItemRef tool = player.inventory().mainHand();
    if (!isPickaxe(tool) || !event.brokenState().isPreferredTool(tool)) {
      return;
    }
    int level = context.level();
    boolean silkTouch = tool.enchantments().containsKey(SILK_TOUCH);
    if (!silkTouch && (level <= 1 || !player.isSneaking())) {
      return;
    }

    ItemRef spawner = createSpawnerItem(event.brokenSpawnerType());
    ItemEntityRef item = dropSpawner(event, player, spawner);
    pendingDrops.put(DropKey.of(event), new DropPlan(player, item.location(), block.center(), item));
  }

  /** Replaces the spawner drops of {@code event} with {@code spawner}, dropped near the block and owned by {@code player}. */
  public static ItemEntityRef dropSpawner(BlockDropItemEvent event, PlayerRef player, ItemRef spawner) {
    BlockRef block = event.block();
    ThreadLocalRandom random = ThreadLocalRandom.current();
    Loc loc = block.location().add(
        random.nextDouble(-0.25D, 0.25D),
        random.nextDouble(-0.25D, 0.25D) - 0.125D,
        random.nextDouble(-0.25D, 0.25D)
    );
    ItemEntityRef item = block.world().createItemEntity(loc.position(), spawner);
    item.setOwner(player.uuid());

    event.items().removeIf((ItemEntityRef drop) -> SPAWNER.equals(drop.item().type()));
    event.items().add(item);
    return item;
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void onBlockDropCommit(BlockDropItemEvent event) {
    DropPlan plan = pendingDrops.remove(DropKey.of(event));
    if (plan == null) {
      return;
    }
    if (event.isCancelled()) {
      if (plan.item().isValid()) {
        plan.item().remove();
      }
      fx(plan.center(), FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 6, 0, 0.3, 0, 0.2, 0.02)
          .chord(Sounds.BLOCK_FIRE_EXTINGUISH, 0.6f, 0.6f, Sounds.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
      return;
    }

    timeline(plan.center())
        .duration(18)
        .priority(FxPriority.TRANSITION)
        .cullRadius(32)
        .frame((fxE, tick, progress) -> {
          fxE.ring(Particles.SOUL, 1.4D - (1.2D * progress), 16, 0.4D);
          fxE.particle(Particles.PORTAL, 4, 0, 0.5, 0, 0.8, 0.1);
          if (tick == 0) {
            fxE.sound(Sounds.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 0.8f);
          } else if ((tick & 3) == 0) {
            fxE.sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, (float) (1.0D + (progress * 0.8D)));
          }
          if (tick == 17) {
            fxE.particle(Particles.FLASH, 1, 0, 0.5, 0, 0, 0)
                .ring(Particles.SOUL, 1.6D, 20, 0.4D)
                .chord(Sounds.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.9f, 1.2f, Sounds.BLOCK_BEACON_ACTIVATE, 0.5f, 1.0f);
          }
        })
        .start();
    addStat(plan.player(), "pickaxe.silk-spawner.spawners-collected", 1);
    fx(plan.dropLocation(), FxPriority.TRANSITION)
        .column(Particles.END_ROD, 8, 1.0D)
        .sound(Sounds.ENTITY_ITEM_PICKUP, 0.6f, 0.7f);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, PickaxePreferences.REQUIRE_SNEAK);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(
        level < 2 ? PickaxeMessages.SILK_SPAWNER_LORE1 : PickaxeMessages.SILK_SPAWNER_LORE2
    ));
  }

  public static ItemRef createSpawnerItem(Id spawnedType) {
    return AdaptCore.platform().host().items().spawner(spawnedType);
  }

  @ConfigDescription("Spawners drop when broken with silk touch or while sneaking.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 7;
      costFactor = 0.7;
      maxLevel = 2;
      initialCost = 6;
    }
  }

  private record DropKey(UUID player, UUID world, int x, int y, int z) {
    static DropKey of(BlockDropItemEvent event) {
      BlockRef block = event.block();
      return new DropKey(event.player().uuid(), block.world().uid(), block.x(), block.y(), block.z());
    }
  }

  private record DropPlan(PlayerRef player, Loc dropLocation, Loc center, ItemEntityRef item) {
  }
}
