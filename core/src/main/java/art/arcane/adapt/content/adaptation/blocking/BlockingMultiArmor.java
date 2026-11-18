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

package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.AdaptCore;
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
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.content.item.multiItems.MultiArmor;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.BlockingMessages;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.player.PlayerDropItemEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.List;
import java.util.Set;

public class BlockingMultiArmor extends SimpleAdaptation<BlockingMultiArmor.Config> {
  private static final Id ELYTRA = Id.minecraft("elytra");
  private static final Set<Id> MULTI_ARMORABLE = Set.of(
      ELYTRA,
      Id.minecraft("chainmail_chestplate"),
      Id.minecraft("diamond_chestplate"),
      Id.minecraft("golden_chestplate"),
      Id.minecraft("iron_chestplate"),
      Id.minecraft("leather_chestplate"),
      Id.minecraft("netherite_chestplate")
  );
  private static final MultiArmor multiarmor = new MultiArmor();
  private static final long SWAP_COOLDOWN_MILLIS = 3000L;
  private final Cooldowns swapCooldowns = cooldowns();


  public BlockingMultiArmor() {
    super("blocking-multiarmor");
    registerConfiguration(BlockingMultiArmor.Config.class);
    setLocalizationKey("blocking.multi_armor");
    setIcon(ELYTRA);
    setInterval(20202);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("elytra"))
        .key("challenge_blocking_multi_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_chestplate"))
            .key("challenge_blocking_multi_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_blocking_multi_200", "blocking.multi-armor.swaps", 200, 400);
    registerMilestone("challenge_blocking_multi_5k", "blocking.multi-armor.swaps", 5000, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, BlockingPreferences.GROUND, BlockingPreferences.FALL, BlockingPreferences.FALL_DISTANCE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GRAY + AdaptLanguage.text(BlockingMessages.MULTI_ARMOR_LORE1));
    v.addLore(C.GRAY + "" + C.GRAY + AdaptLanguage.text(BlockingMessages.MULTI_ARMOR_LORE2));
    v.addLore(C.GREEN + AdaptLanguage.text(BlockingMessages.MULTI_ARMOR_LORE3));
    v.addLore(C.RED + AdaptLanguage.text(BlockingMessages.MULTI_ARMOR_LORE4));
    v.addLore(C.GRAY + AdaptLanguage.text(BlockingMessages.MULTI_ARMOR_LORE5));
    v.addLore(C.UNDERLINE + AdaptLanguage.text(BlockingMessages.MULTI_ARMOR_LORE6));
  }


  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerMoveEvent e) {
    if (!e.changedBlock()) {
      return;
    }

    PlayerRef p = e.player();
    ItemRef chest = p.inventory().armor(EquipmentSlot.CHEST);
    if (chest.isEmpty() || !hasActiveAdaptation(p) || !validateArmor(chest)) {
      return;
    }

    if (!swapCooldowns.isReady(p.uuid(), SWAP_COOLDOWN_MILLIS)) {
      return;
    }

    if (p.isOnGround() && !p.isFlying() && preferenceEnabled(p, BlockingPreferences.GROUND)) {
      if (isChestplate(chest)) {
        return;
      }
      Tasks.runEntity(p, () -> p.inventory().setArmor(EquipmentSlot.CHEST, multiarmor.nextChestplate(chest)));
      swapCooldowns.mark(p.uuid());
      transformFx(p, false);
      addStat(p, "blocking.multi-armor.swaps", 1);
    } else if (preferenceEnabled(p, BlockingPreferences.FALL) && preference(p, BlockingPreferences.FALL_DISTANCE).exceeded(p.fallDistance())) {
      if (isElytra(chest)) {
        return;
      }
      Tasks.runEntity(p, () -> p.inventory().setArmor(EquipmentSlot.CHEST, multiarmor.nextElytra(chest)));
      swapCooldowns.mark(p.uuid());
      transformFx(p, true);
      addStat(p, "blocking.multi-armor.swaps", 1);
    }
  }

  private void transformFx(PlayerRef p, boolean toElytra) {
    if (toElytra) {
      fx(p, FxPriority.TRANSITION)
          .column(Particles.WAX_ON, 10, 1.8D)
          .burst(Particles.END_ROD, 3, 0.2D)
          .chord(Sounds.ITEM_ARMOR_EQUIP_ELYTRA, 1.0F, 0.77F, Sounds.ENTITY_IRON_GOLEM_STEP, 0.5F, 0.77F, Sounds.BLOCK_CONDUIT_ACTIVATE, 0.4F, 1.4F);
      return;
    }
    fx(p, FxPriority.TRANSITION)
        .column(Particles.WAX_ON, 10, 1.8D)
        .burst(Particles.END_ROD, 3, 0.2D)
        .chord(Sounds.ITEM_ARMOR_EQUIP_ELYTRA, 1.0F, 0.77F, Sounds.BLOCK_BEEHIVE_SHEAR, 0.5F, 0.77F, Sounds.BLOCK_CONDUIT_DEACTIVATE, 0.4F, 0.9F);
  }


  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerDropItemEvent e) {
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p)) {
      return;
    }
    if (p.isSneaking()) {
      if (validateArmor(e.itemDrop().item())) {
        ItemRef source = e.itemDrop().item();
        List<ItemRef> drops = multiarmor.explode(source);

        Tasks.runEntity(p, () -> {
          fx(p, FxPriority.TRANSITION)
              .particle(Particles.itemCrack(source), 12, 0, 0.2D, 0, 0.3D, 0.05D)
              .burst(Particles.CRIT_MAGIC, 4, 0.3D)
              .chord(Sounds.ENTITY_IRON_GOLEM_DEATH, 0.25F, 0.77F, Sounds.BLOCK_ANVIL_DESTROY, 0.3F, 1.1F);
          for (ItemRef i : drops) {
            p.world().dropItem(p.location().position(), i);
          }
        });
        e.itemDrop().setItem(AdaptCore.platform().host().items().empty());
      }
    }
  }


  @Handle(priority = EventPriority.HIGHEST)
  public void on(InventoryClickEvent e) {
    PlayerRef player = e.player();
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }
    if (e.clickedKind() != null
        && e.click() == ClickKind.LEFT
        && e.currentItem() != null
        && e.action() == InventoryAction.SWAP_WITH_CURSOR) {
      ItemRef cursor = e.cursor().copy();
      ItemRef clicked = e.currentItem().copy();

      if (cursor.type().equals(ELYTRA) || clicked.type().equals(ELYTRA)) {

        if (multiarmor.explode(cursor).size() > 1 || multiarmor.explode(clicked).size() > 1) {

          if (multiarmor.explode(cursor).size() >= getSlots(level) || multiarmor.explode(clicked).size() >= getSlots(level)) {
            e.setCancelled(true);
            fx(player, FxPriority.TRANSITION)
                .particle(Particles.SMOKE, 3, 0, 1.0D, 0, 0.1D, 0.01D)
                .sound(Sounds.BLOCK_BEACON_DEACTIVATE, 1.0F, 0.77F);
            return;
          }
        }
        if (MULTI_ARMORABLE.contains(cursor.type()) && MULTI_ARMORABLE.contains(clicked.type())) {

          if (!cursor.type().isAir() && !clicked.type().isAir() && multiarmor.supportsItem(cursor) && multiarmor.supportsItem(clicked)) {
            e.setCancelled(true);
            e.setCursor(AdaptCore.platform().host().items().empty());
            e.setCurrentItem(multiarmor.build(cursor, clicked));
            fx(player, FxPriority.TRANSITION)
                .burst(Particles.END_ROD, 8, 0.4D)
                .chord(Sounds.ITEM_ARMOR_EQUIP_ELYTRA, 1.0F, 0.77F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.4F);
          }
        }
      }
    }
  }


  private boolean validateArmor(ItemRef item) {
    return multiarmor.isMultiItem(item);
  }


  private double getSlots(double level) {
    return getConfig().startingSlots + level;
  }

  @ConfigDescription("Bind Elytras to armor for dynamic merge and swap.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Starting Slots for the Blocking Multi Armor adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int startingSlots = 1;

    public Config() {
      baseCost = 1;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 3;
    }
  }
}
