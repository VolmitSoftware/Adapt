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

package art.arcane.adapt.content.adaptation.rift;

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
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RiftMessages;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerDropItemEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.List;

public class RiftInflatedPocketDimension extends SimpleAdaptation<RiftInflatedPocketDimension.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> PULL = CommonPreferences.toggle("pull", RiftMessages.RIFTINFLATEDPOCKETDIMENSION_PREFERENCE_PULL, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> REFILL = CommonPreferences.toggle("refill", RiftMessages.RIFTINFLATEDPOCKETDIMENSION_PREFERENCE_REFILL, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> STORE = CommonPreferences.toggle("store", RiftMessages.RIFTINFLATEDPOCKETDIMENSION_PREFERENCE_STORE, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<Items> ITEMS = new PlayerPreference<>(Items.class,
      new PlayerPreference.Definition<>("items", RiftMessages.RIFTINFLATEDPOCKETDIMENSION_PREFERENCE_ITEMS, Items.ALL, List.of(
          new PlayerPreference.Choice<>(Items.ALL, RiftMessages.RIFTINFLATEDPOCKETDIMENSION_PREFERENCE_ITEMS_ALL, Id.minecraft("hopper"), 1),
          new PlayerPreference.Choice<>(Items.BLOCKS, RiftMessages.RIFTINFLATEDPOCKETDIMENSION_PREFERENCE_ITEMS_BLOCKS, Id.minecraft("stone"), 1),
          new PlayerPreference.Choice<>(Items.FOOD, RiftMessages.RIFTINFLATEDPOCKETDIMENSION_PREFERENCE_ITEMS_FOOD, Id.minecraft("bread"), 1))));

  public RiftInflatedPocketDimension() {
    super("rift-inflated-pocket-dimension");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("ender_eye"));
    setInterval(600);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_chest"))
        .key("challenge_rift_pocket_5k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("ender_chest"))
            .key("challenge_rift_pocket_store_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_pocket_5k", "rift.inflated-pocket.items-pulled", 5000, 400);
    registerMilestone("challenge_rift_pocket_store_10k", "rift.inflated-pocket.items-stored", 10000, 1000);
  }

  private static ItemOps items() {
    return AdaptCore.platform().host().items();
  }

  private boolean acceptsItem(PlayerRef player, Id type) {
    return switch (preference(player, ITEMS)) {
      case ALL -> true;
      case BLOCKS -> items().isBlock(type);
      case FOOD -> items().isEdible(type);
    };
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, PULL, REFILL, STORE, ITEMS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(RiftMessages.INFLATED_POCKET_DIMENSION_LORE1));
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(RiftMessages.INFLATED_POCKET_DIMENSION_LORE2));
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(RiftMessages.INFLATED_POCKET_DIMENSION_LORE3));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    if (e.hand() != Hand.MAIN_HAND) {
      return;
    }

    InteractAction action = e.action();
    if (action != InteractAction.RIGHT_CLICK_BLOCK && action != InteractAction.RIGHT_CLICK_AIR && action != InteractAction.LEFT_CLICK_AIR) {
      return;
    }

    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p) || !preferenceEnabled(p, PULL)) {
      return;
    }

    ItemRef hand = p.inventory().mainHand();
    if (!hand.type().isAir()) {
      return;
    }

    BlockRef target = action == InteractAction.RIGHT_CLICK_BLOCK ? e.clickedBlock() : p.targetBlock(5);
    if (target == null) {
      return;
    }

    Id requested = target.type();
    if (!items().isItem(requested) || requested.isAir()) {
      return;
    }

    int moved = moveFromEnderToPlayer(p, requested, getConfig().rightClickPullAmount, true);
    if (moved <= 0) {
      return;
    }

    e.setCancelled(true);
    Loc from = target.location().add(0.5, 0.5, 0.5);
    Vec3 toHand = p.eyeLocation().position().subtract(from.position());
    fx(from, FxPriority.TRANSITION)
        .trail(Particles.PORTAL, toHand.x(), toHand.y(), toHand.z(), Math.max(0.5, toHand.length()), 6)
        .chord(Sounds.BLOCK_ENDER_CHEST_OPEN, 0.4f, 1.7f, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.3f, 1.6f);
    addStat(p, "rift.inflated-pocket.items-pulled", moved);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockPlaceEvent e) {
    if (!preferenceEnabled(e.player(), REFILL)) {
      return;
    }
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p)) {
      return;
    }

    Id placed = e.block().type();
    if (!items().isItem(placed) || placed.isAir()) {
      return;
    }

    BlockRef placedBlock = e.block();
    Tasks.runEntity(p, () -> {
      ItemRef hand = p.inventory().mainHand();
      boolean wasEmptyHand = hand.type().isAir();
      int maxStackSize = items().maxStackSize(placed);
      int needed = 0;
      if (wasEmptyHand) {
        needed = Math.min(getConfig().buildRefillAmount, maxStackSize);
      } else if (hand.type().equals(placed) && hand.amount() < maxStackSize) {
        needed = Math.min(getConfig().buildRefillAmount, maxStackSize - hand.amount());
      }

      if (needed <= 0) {
        return;
      }

      if (!preferenceEnabled(p, REFILL)) {
        return;
      }
      int moved = moveFromEnderToPlayer(p, placed, needed, true);
      if (moved <= 0) {
        return;
      }

      if (wasEmptyHand) {
        fx(placedBlock.location().add(0.5, 0.5, 0.5), FxPriority.TRAIL)
            .particle(Particles.REVERSE_PORTAL, 3, 0, 0.3, 0, 0.15, 0.02)
            .sound(Sounds.BLOCK_ENDER_CHEST_OPEN, 0.3f, 1.9f);
      } else {
        fx(p, FxPriority.TRAIL).sound(Sounds.BLOCK_ENDER_CHEST_OPEN, 0.3f, 1.9f);
      }
      addStat(p, "rift.inflated-pocket.items-pulled", moved);
    }, 1);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerDropItemEvent e) {
    PlayerRef p = e.player();
    if (!preferenceEnabled(p, STORE) || getActiveLevel(p, PlayerRef::isSneaking) <= 0) {
      return;
    }

    ItemRef dropped = e.itemDrop().item().copy();
    if (!acceptsItem(p, dropped.type())) {
      return;
    }
    InventoryRef enderChest = p.enderChest();
    if (!canFullyFitInInventory(enderChest, dropped, enderChest.maxStackSize())) {
      e.setCancelled(true);
      fx(p, FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 3, 0.2)
          .sound(Sounds.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
      return;
    }

    e.itemDrop().remove();
    enderChest.addItem(dropped);

    fx(p, FxPriority.TRANSITION)
        .particle(Particles.PORTAL, 5, 0, 1.0, 0, 0.2, 0.03)
        .sound(Sounds.BLOCK_ENDER_CHEST_CLOSE, 0.5f, 1.4f);
    double credited = getPlayer(p).getData().getItemTransferXp().claim(dropped.type().legacyName(),
        dropped.amount(), dropped.maxStackSize(), getConfig().transferXpWindowMillis, System.currentTimeMillis());
    if (credited > 0D) {
      addStat(p, "rift.inflated-pocket.items-stored", credited);
      xp(p, credited * getConfig().xpPerTransferredItem, "rift:inflated-pocket:store");
    }
  }

  @Override
  protected void normalizeLoadedConfig(Config config) {
    config.transferXpWindowMillis = Math.max(1000L, Math.min(86400000L, config.transferXpWindowMillis));
  }

  private int moveFromEnderToPlayer(PlayerRef p, Id type, int amount, boolean preferMainHand) {
    if (!hasActiveAdaptation(p) || !acceptsItem(p, type)) {
      return 0;
    }
    if (amount <= 0) {
      return 0;
    }

    int moved = 0;
    InventoryRef chest = p.enderChest();
    int size = chest.size();
    for (int slot = 0; slot < size && moved < amount; slot++) {
      ItemRef stack = chest.item(slot);
      if (!isItem(stack) || !stack.type().equals(type)) {
        continue;
      }

      int take = Math.min(amount - moved, stack.amount());
      ItemRef transfer = stack.copyWithAmount(take);

      int inserted = insertIntoPlayerInventory(p, transfer, preferMainHand);
      if (inserted <= 0) {
        continue;
      }

      moved += inserted;
      if (stack.amount() <= inserted) {
        chest.clear(slot);
      } else {
        chest.setItem(slot, stack.copyWithAmount(stack.amount() - inserted));
      }
    }

    return moved;
  }

  private int insertIntoPlayerInventory(PlayerRef p, ItemRef transfer, boolean preferMainHand) {
    int requested = transfer.amount();
    if (requested <= 0) {
      return 0;
    }

    ItemRef hand = p.inventory().mainHand();
    if (preferMainHand && hand.type().isAir()) {
      p.inventory().setMainHand(transfer);
      return requested;
    }

    if (preferMainHand && hand.type().equals(transfer.type()) && hand.amount() < hand.maxStackSize()) {
      int room = hand.maxStackSize() - hand.amount();
      int moved = Math.min(room, transfer.amount());
      hand.setAmount(hand.amount() + moved);
      p.inventory().setMainHand(hand);
      if (moved >= requested) {
        return requested;
      }

      int remainingRequest = requested - moved;
      List<ItemRef> overflow = p.inventory().addItem(transfer.copyWithAmount(remainingRequest));
      int remaining = sumItemAmounts(overflow);
      return moved + Math.max(0, remainingRequest - remaining);
    }

    List<ItemRef> overflow = p.inventory().addItem(transfer);
    int remaining = sumItemAmounts(overflow);
    return Math.max(0, requested - remaining);
  }

  private int sumItemAmounts(List<ItemRef> overflow) {
    int sum = 0;
    for (ItemRef itemStack : overflow) {
      if (itemStack == null || itemStack.type().isAir()) {
        continue;
      }
      sum += itemStack.amount();
    }
    return sum;
  }

  private boolean canFullyFitInInventory(InventoryRef inventory, ItemRef stack, int inventoryMaxStackSize) {
    if (!isItem(stack)) {
      return false;
    }

    int remaining = stack.amount();
    int maxPerSlot = Math.min(stack.maxStackSize(), inventoryMaxStackSize);
    List<ItemRef> contents = inventory.contents();

    for (ItemRef existing : contents) {
      if (!isItem(existing) || !existing.isSimilar(stack)) {
        continue;
      }

      remaining -= Math.max(0, maxPerSlot - existing.amount());
      if (remaining <= 0) {
        return true;
      }
    }

    for (ItemRef existing : contents) {
      if (existing == null || existing.type().isAir()) {
        remaining -= maxPerSlot;
        if (remaining <= 0) {
          return true;
        }
      }
    }

    return false;
  }


  @ConfigDescription("Building and empty-hand block targeting can fetch materials from your ender chest, and sneak-drop stores items into it.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Build Refill Amount for the Rift Inflated Pocket Dimension adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int buildRefillAmount = 64;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Right Click Pull Amount for the Rift Inflated Pocket Dimension adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int rightClickPullAmount = 64;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP Per Transferred Item for the Rift Inflated Pocket Dimension adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerTransferredItem = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds to replenish one stack of storage XP credit per material.", impact = "Higher values reduce rewards for repeatedly storing the same material.")
    long transferXpWindowMillis = 600000L;

    public Config() {
      permanent = true;
      baseCost = 5;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 6;
    }
  }

  public enum Items { ALL, BLOCKS, FOOD }
}
