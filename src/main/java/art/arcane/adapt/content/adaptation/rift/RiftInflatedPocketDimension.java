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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Map;

public class RiftInflatedPocketDimension extends SimpleAdaptation<RiftInflatedPocketDimension.Config> {
  public RiftInflatedPocketDimension() {
    super("rift-inflated-pocket-dimension");
    registerConfiguration(Config.class);
    setIcon(Material.ENDER_EYE);
    setInterval(600);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_CHEST)
        .key("challenge_rift_pocket_5k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENDER_CHEST)
            .key("challenge_rift_pocket_store_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_rift_pocket_5k", "rift.inflated-pocket.items-pulled", 5000, 400);
    registerMilestone("challenge_rift_pocket_store_10k", "rift.inflated-pocket.items-stored", 10000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Localizer.dLocalize("rift.inflated_pocket_dimension.lore1"));
    v.addLore(C.GREEN + "+ " + Localizer.dLocalize("rift.inflated_pocket_dimension.lore2"));
    v.addLore(C.GREEN + "+ " + Localizer.dLocalize("rift.inflated_pocket_dimension.lore3"));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR && action != Action.LEFT_CLICK_AIR) {
      return;
    }

    Player p = e.getPlayer();
    if (!hasActiveAdaptation(p)) {
      return;
    }

    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!hand.getType().isAir()) {
      return;
    }

    Block target = action == Action.RIGHT_CLICK_BLOCK ? e.getClickedBlock() : p.getTargetBlockExact(5);
    if (target == null) {
      return;
    }

    Material requested = target.getType();
    if (!requested.isItem() || requested.isAir()) {
      return;
    }

    int moved = moveFromEnderToPlayer(p, requested, getConfig().rightClickPullAmount, true);
    if (moved <= 0) {
      return;
    }

    e.setCancelled(true);
    Location from = target.getLocation().add(0.5, 0.5, 0.5);
    Vector toHand = p.getEyeLocation().toVector().subtract(from.toVector());
    fx(from, FxPriority.TRANSITION)
        .trail(Particle.PORTAL, toHand.getX(), toHand.getY(), toHand.getZ(), Math.max(0.5, toHand.length()), 6)
        .chord(Sound.BLOCK_ENDER_CHEST_OPEN, 0.4f, 1.7f, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3f, 1.6f);
    addStat(p, "rift.inflated-pocket.items-pulled", moved);
    xp(p, moved * getConfig().xpPerTransferredItem, "rift:inflated-pocket:pull");
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockPlaceEvent e) {
    Player p = e.getPlayer();
    if (!hasActiveAdaptation(p)) {
      return;
    }

    Material placed = e.getBlockPlaced().getType();
    if (!placed.isItem() || placed.isAir()) {
      return;
    }

    J.runEntity(p, () -> {
      ItemStack hand = p.getInventory().getItemInMainHand();
      boolean wasEmptyHand = hand.getType().isAir();
      int needed = 0;
      if (wasEmptyHand) {
        needed = Math.min(getConfig().buildRefillAmount, placed.getMaxStackSize());
      } else if (hand.getType() == placed && hand.getAmount() < placed.getMaxStackSize()) {
        needed = Math.min(getConfig().buildRefillAmount, placed.getMaxStackSize() - hand.getAmount());
      }

      if (needed <= 0) {
        return;
      }

      int moved = moveFromEnderToPlayer(p, placed, needed, true);
      if (moved <= 0) {
        return;
      }

      if (wasEmptyHand) {
        fx(e.getBlockPlaced().getLocation().add(0.5, 0.5, 0.5), FxPriority.TRAIL)
            .particle(Particle.REVERSE_PORTAL, 3, 0, 0.3, 0, 0.15, 0.02)
            .sound(Sound.BLOCK_ENDER_CHEST_OPEN, 0.3f, 1.9f);
      } else {
        fx(p, FxPriority.TRAIL).sound(Sound.BLOCK_ENDER_CHEST_OPEN, 0.3f, 1.9f);
      }
      addStat(p, "rift.inflated-pocket.items-pulled", moved);
      xp(p, moved * getConfig().xpPerTransferredItem, "rift:inflated-pocket:build-refill");
    }, 1);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerDropItemEvent e) {
    Player p = e.getPlayer();
    if (getActiveLevel(p, Player::isSneaking) <= 0) {
      return;
    }

    ItemStack dropped = e.getItemDrop().getItemStack().clone();
    if (!canFullyFitInInventory(p.getEnderChest().getContents(), dropped, p.getEnderChest().getMaxStackSize())) {
      e.setCancelled(true);
      e.getItemDrop().remove();
      fx(p, FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 3, 0.2)
          .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
      return;
    }

    e.getItemDrop().remove();
    p.getEnderChest().addItem(dropped);

    fx(p, FxPriority.TRANSITION)
        .particle(Particle.PORTAL, 5, 0, 1.0, 0, 0.2, 0.03)
        .sound(Sound.BLOCK_ENDER_CHEST_CLOSE, 0.5f, 1.4f);
    addStat(p, "rift.inflated-pocket.items-stored", dropped.getAmount());
    xp(p, dropped.getAmount() * getConfig().xpPerTransferredItem, "rift:inflated-pocket:store");
  }

  private int moveFromEnderToPlayer(Player p, Material type, int amount, boolean preferMainHand) {
    if (amount <= 0) {
      return 0;
    }

    int moved = 0;
    ItemStack[] chest = p.getEnderChest().getContents();
    for (int slot = 0; slot < chest.length && moved < amount; slot++) {
      ItemStack stack = chest[slot];
      if (!isItem(stack) || stack.getType() != type) {
        continue;
      }

      int take = Math.min(amount - moved, stack.getAmount());
      ItemStack transfer = stack.clone();
      transfer.setAmount(take);

      int inserted = insertIntoPlayerInventory(p, transfer, preferMainHand);
      if (inserted <= 0) {
        continue;
      }

      moved += inserted;
      if (stack.getAmount() <= inserted) {
        chest[slot] = null;
      } else {
        stack.setAmount(stack.getAmount() - inserted);
        chest[slot] = stack;
      }
    }

    p.getEnderChest().setContents(chest);
    return moved;
  }

  private int insertIntoPlayerInventory(Player p, ItemStack transfer, boolean preferMainHand) {
    int requested = transfer.getAmount();
    if (requested <= 0) {
      return 0;
    }

    ItemStack hand = p.getInventory().getItemInMainHand();
    if (preferMainHand && (hand == null || hand.getType().isAir())) {
      p.getInventory().setItemInMainHand(transfer);
      return requested;
    }

    if (preferMainHand && hand.getType() == transfer.getType() && hand.getAmount() < hand.getMaxStackSize()) {
      int room = hand.getMaxStackSize() - hand.getAmount();
      int moved = Math.min(room, transfer.getAmount());
      hand.setAmount(hand.getAmount() + moved);
      p.getInventory().setItemInMainHand(hand);
      if (moved >= requested) {
        return requested;
      }

      int remainingRequest = requested - moved;
      transfer.setAmount(remainingRequest);
      Map<Integer, ItemStack> overflow = p.getInventory().addItem(transfer);
      int remaining = sumItemAmounts(overflow);
      return moved + Math.max(0, remainingRequest - remaining);
    }

    Map<Integer, ItemStack> overflow = p.getInventory().addItem(transfer);
    int remaining = sumItemAmounts(overflow);
    return Math.max(0, requested - remaining);
  }

  private int sumItemAmounts(Map<Integer, ItemStack> overflow) {
    int sum = 0;
    for (ItemStack itemStack : overflow.values()) {
      if (itemStack == null || itemStack.getType().isAir()) {
        continue;
      }
      sum += itemStack.getAmount();
    }
    return sum;
  }

  private boolean canFullyFitInInventory(ItemStack[] contents, ItemStack stack, int inventoryMaxStackSize) {
    if (!isItem(stack)) {
      return false;
    }

    int remaining = stack.getAmount();
    int maxPerSlot = Math.min(stack.getMaxStackSize(), inventoryMaxStackSize);

    for (ItemStack existing : contents) {
      if (!isItem(existing) || !existing.isSimilar(stack)) {
        continue;
      }

      remaining -= Math.max(0, maxPerSlot - existing.getAmount());
      if (remaining <= 0) {
        return true;
      }
    }

    for (ItemStack existing : contents) {
      if (existing == null || existing.getType().isAir()) {
        remaining -= maxPerSlot;
        if (remaining <= 0) {
          return true;
        }
      }
    }

    return false;
  }

  @Override
  public void onTick() {

  }

  @ConfigDescription("Building and empty-hand block targeting can fetch materials from your ender chest, and sneak-drop stores items into it.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Build Refill Amount for the Rift Inflated Pocket Dimension adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int buildRefillAmount = 64;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Right Click Pull Amount for the Rift Inflated Pocket Dimension adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int rightClickPullAmount = 64;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP Per Transferred Item for the Rift Inflated Pocket Dimension adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerTransferredItem = 0.08;

    public Config() {
      permanent = true;
      baseCost = 7;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 7;
    }
  }
}
