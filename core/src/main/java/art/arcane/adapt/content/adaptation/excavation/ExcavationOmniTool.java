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
import art.arcane.adapt.content.item.multiItems.OmniTool;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ExcavationMessages;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerDropItemEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.List;

public class ExcavationOmniTool extends SimpleAdaptation<ExcavationOmniTool.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> COMBAT = CommonPreferences.toggle("combat-selection", ExcavationMessages.PREFERENCE_EXCAVATIONOMNITOOL_COMBAT, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> MINING = CommonPreferences.toggle("mining-selection", ExcavationMessages.PREFERENCE_EXCAVATIONOMNITOOL_MINING, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> SOUNDS = CommonPreferences.toggle("swap-sounds", ExcavationMessages.PREFERENCE_EXCAVATIONOMNITOOL_SOUNDS, CommonPreferences.Toggle.ON);

  private static final OmniTool omniTool = new OmniTool();

  public ExcavationOmniTool() {
    super("excavation-omnitool");
    registerConfiguration(ExcavationOmniTool.Config.class);
    setLocalizationKey("excavation.omni_tool");
    setIcon(Id.minecraft("disc_fragment_5"));
    setInterval(20202);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_pickaxe"))
        .key("challenge_excavation_omni_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_pickaxe"))
            .key("challenge_excavation_omni_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_excavation_omni_1k", "excavation.omni-tool.auto-swaps", 1000, 400);
    registerMilestone("challenge_excavation_omni_25k", "excavation.omni-tool.auto-swaps", 25000, 1500);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, COMBAT, MINING, SOUNDS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GRAY + AdaptLanguage.text(ExcavationMessages.OMNI_TOOL_LORE1));
    v.addLore(C.GRAY + AdaptLanguage.text(ExcavationMessages.OMNI_TOOL_LORE2));
    v.addLore(C.GREEN + AdaptLanguage.text(ExcavationMessages.OMNI_TOOL_LORE3));
    v.addLore(C.RED + AdaptLanguage.text(ExcavationMessages.OMNI_TOOL_LORE4));
    v.addLore(C.GRAY + AdaptLanguage.text(ExcavationMessages.OMNI_TOOL_LORE5));
    statLore(v, C.GREEN, "", level + getConfig().startingSlots, 6);
    v.addLore(C.UNDERLINE + AdaptLanguage.text(ExcavationMessages.OMNI_TOOL_LORE7));


  }


  @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void on(EntityDamageByEntityEvent e) {
    if (e.damager() instanceof PlayerRef p && validateTool(p.inventory().mainHand())) {
      ItemRef hand = p.inventory().mainHand();
      if (remainingDurability(hand) <= 2) {
        e.setCancelled(true);
        return;
      }
      if (!hasActiveAdaptation(p) && validateTool(hand)) {
        e.setCancelled(true);
        return;
      }

      if (!validateTool(hand)) {
        return;
      }
      if (preferenceEnabled(p, COMBAT) && !isSword(hand)) {
        Tasks.runEntity(p, () -> p.inventory().setMainHand(omniTool.nextSword(hand)));
        swapFx(p);
      }
      if (hand.damage() > 0) {
        if ((maxDurability(hand) - hand.damage() - 2) <= 2) {
          e.setCancelled(true);
          nearBreakFx(p);
        }
      }

    }
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void on(BlockBreakEvent e) {
    PlayerRef p = e.player();
    ItemRef hand = p.inventory().mainHand();
    if (validateTool(hand)) {
      if (remainingDurability(hand) <= 2) {
        e.setCancelled(true);
        return;
      }


      if (!hasActiveAdaptation(p)) {
        e.setCancelled(true);
        return;
      }
    }
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void on(PlayerInteractEvent e) {
    PlayerRef p = e.player();
    if (validateTool(p.inventory().mainHand())) {
      if (remainingDurability(p.inventory().mainHand()) <= 2) {
        e.setCancelled(true);
        return;
      }

      if (!hasActiveAdaptation(p)) {
        return;
      }
      InteractAction action = e.action();
      if (action == InteractAction.RIGHT_CLICK_BLOCK || action == InteractAction.RIGHT_CLICK_AIR) {
        if (e.hand() != null && e.hand() != Hand.MAIN_HAND) {
          return;
        }
        ItemRef hand = p.inventory().mainHand();
        BlockRef block = action == InteractAction.RIGHT_CLICK_BLOCK ? e.clickedBlock() : p.targetBlock(5);
        if (block != null) {
          if (OmniToolBlocks.isFarmable(block.type())) {
            if (!isHoe(hand)) {
              e.setCancelled(true);
              Tasks.runEntity(p, () -> p.inventory().setMainHand(omniTool.nextHoe(hand)));
              swapFx(p);
              if (hand.damage() > 0) {
                if ((maxDurability(hand) - hand.damage() - 2) <= 2) {
                  nearBreakFx(p);
                }
              }
            }
          } else if (OmniToolBlocks.isBurnable(block.type())) {
            Tasks.runEntity(p, () -> p.inventory().setMainHand(omniTool.nextFnS(hand)));
            swapFx(p);
            if (hand.damage() > 0) {
              if ((maxDurability(hand) - hand.damage() - 2) <= 2) {
                e.setCancelled(true);
                nearBreakFx(p);
              }
            }
          }
        }
      }
    }

  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PlayerDropItemEvent e) {
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p)) {
      return;
    }
    if (p.isSneaking()) {
      ItemRef dropped = e.itemDrop().item();
      if (validateTool(dropped)) {
        List<ItemRef> drops = omniTool.explode(dropped);

        e.itemDrop().remove();
        splitFx(p);
        for (ItemRef i : drops) {
          p.world().dropItem(p.location().position(), i);
        }
      }
    }
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void on(BlockDamageEvent e) {
    PlayerRef p = e.player();
    if (!preferenceEnabled(p, MINING)) {
      return;
    }
    BlockRef b = e.block();
    ItemRef hand = p.inventory().mainHand();

    if (validateTool(hand)) {
      if (!hasActiveAdaptation(p)) {
        return;
      }

      Id type = b.type();
      if (OmniToolBlocks.prefersAxe(type)) {
        if (!isAxe(hand)) {
          Tasks.runEntity(p, () -> p.inventory().setMainHand(omniTool.nextAxe(hand)));
          itemDelegate(e, hand);
        }
      } else if (OmniToolBlocks.prefersShovel(type)) {
        if (!isShovel(hand)) {
          Tasks.runEntity(p, () -> p.inventory().setMainHand(omniTool.nextShovel(hand)));
          itemDelegate(e, hand);
        }
      } else if (OmniToolBlocks.prefersSword(type)) {
        if (!isSword(hand)) {
          Tasks.runEntity(p, () -> p.inventory().setMainHand(omniTool.nextSword(hand)));
          itemDelegate(e, hand);
        }
      } else {
        if (!isPickaxe(hand)) {
          Tasks.runEntity(p, () -> p.inventory().setMainHand(omniTool.nextPickaxe(hand)));
          itemDelegate(e, hand);
        }
      }
    }
  }


  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(InventoryClickEvent e) {
    PlayerRef player = e.player();
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }
    if (e.clickedKind() != null && e.click() == ClickKind.SHIFT_LEFT && e.action() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
      ItemRef cursor = e.cursor().copy();
      ItemRef clicked = e.currentItem().copy();

      int cursorComponents = omniTool.explode(cursor).size();
      int clickedComponents = omniTool.explode(clicked).size();
      if (!fitsCapacity(cursorComponents, clickedComponents, getSlots(level))) {
        e.setCancelled(true);
        fx(player, FxPriority.TRANSITION).sound(Sounds.BLOCK_BEACON_DEACTIVATE, 1f, 0.77f);
        return;
      }
      if (OmniToolBlocks.isTool(cursor.type()) && OmniToolBlocks.isTool(clicked.type())) {
        if (!cursor.type().isAir() && !clicked.type().isAir() && omniTool.supportsItem(cursor) && omniTool.supportsItem(clicked)) {
          e.setCancelled(true);
          e.setCursor(AdaptCore.platform().host().items().empty());
          e.setCurrentItem(omniTool.build(cursor, clicked));
          mergeFx(player);
        }
      }
    }

  }

  private void itemDelegate(BlockDamageEvent e, ItemRef hand) {
    PlayerRef p = e.player();
    addStat(p, "excavation.omni-tool.auto-swaps", 1);
    swapFx(p);
    if (hand.damage() > 0) {
      if ((maxDurability(hand) - hand.damage() - 2) <= 2) {
        e.setCancelled(true);
        nearBreakFx(p);
      }
    }
  }

  private void swapFx(PlayerRef p) {
    fx(p, FxPriority.TRANSITION)
        .particle(Particles.ENCHANTMENT_TABLE, 6, 0, 1.0D, 0, 0.3D, 0.05D)
        .particle(Particles.CRIT_MAGIC, 3, 0, 1.0D, 0, 0.3D, 0.1D);
    if (preferenceEnabled(p, SOUNDS)) {
      fx(p, FxPriority.TRANSITION).sound(Sounds.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f);
    }
  }

  private void nearBreakFx(PlayerRef p) {
    fx(p, FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 3, 0, 1.0D, 0, 0.2D, 0.01D)
        .particle(Particles.CRIT_MAGIC, 1, 0, 1.0D, 0, 0.2D, 0.05D)
        .sound(Sounds.ENTITY_IRON_GOLEM_STEP, 0.25f, 0.77f);
  }

  private void splitFx(PlayerRef p) {
    fx(p, FxPriority.TRANSITION)
        .particle(Particles.CRIT_MAGIC, 8, 0, 1.0D, 0, 0.4D, 0.1D)
        .chord(Sounds.ENTITY_IRON_GOLEM_DEATH, 0.25f, 0.77f, Sounds.ITEM_TRIDENT_RETURN, 0.3f, 1.2f);
  }

  private void mergeFx(PlayerRef p) {
    fx(p, FxPriority.TRANSITION)
        .particle(Particles.WAX_ON, 10, 0, 1.0D, 0, 0.4D, 0.05D)
        .particle(Particles.ELECTRIC_SPARK, 5, 0, 1.0D, 0, 0.4D, 0.1D)
        .chord(Sounds.ITEM_ARMOR_EQUIP_ELYTRA, 1f, 0.77f, Sounds.BLOCK_ANVIL_USE, 0.4f, 1.4f);
  }

  private boolean validateTool(ItemRef item) {
    return omniTool.isMultiItem(item);
  }

  private static int maxDurability(ItemRef item) {
    return AdaptCore.platform().host().items().maxDurability(item.type());
  }

  private static int remainingDurability(ItemRef item) {
    return maxDurability(item) - item.damage();
  }

  static boolean fitsCapacity(int firstComponents, int secondComponents, int capacity) {
    return (long) firstComponents + secondComponents <= capacity;
  }

  private int getSlots(int level) {
    return getConfig().startingSlots + level;
  }

  @ConfigDescription("Dynamically merge and swap tools on the fly based on what you are mining.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Starting Slots for the Excavation Omni Tool adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int startingSlots = 1;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      initialCost = 3;
    }
  }
}
