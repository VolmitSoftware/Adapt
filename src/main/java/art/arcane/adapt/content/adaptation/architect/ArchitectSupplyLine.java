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

package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ArchitectMessages;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.item.BackpackItem;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ArchitectSupplyLine extends SimpleAdaptation<ArchitectSupplyLine.Config> {
  private final Map<UUID, RefillWindow> windows = playerState();
  private final Cooldowns failCd = cooldowns();

  public ArchitectSupplyLine() {
    super("architect-supply-line");
    registerConfiguration(ArchitectSupplyLine.Config.class);
    setIcon(Material.SHULKER_BOX);
    setInterval(13780);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SHULKER_BOX)
        .key("challenge_architect_supply_line_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.SHULKER_BOX)
            .key("challenge_architect_supply_line_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_architect_supply_line_100", "architect.supply-line.refills", 100, 300);
    registerMilestone("challenge_architect_supply_line_1k", "architect.supply-line.refills", 1000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(ArchitectMessages.SUPPLY_LINE_LORE1));
    statLore(v, C.GREEN, "", getRefillsPerMinute(getLevelPercent(level)), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPlaceEvent e) {
    ItemStack hand = e.getItemInHand();
    if (!consumesLastOfStack(hand)) {
      return;
    }

    Player p = e.getPlayer();
    Adaptation.BlockActionContext context = resolveBlockPlaceContext(p, e.getBlock().getLocation());
    if (context == null) {
      return;
    }

    UUID playerId = p.getUniqueId();
    int allowed = getRefillsPerMinute(getLevelPercent(context.level()));
    if (!hasRefillCapacity(playerId, allowed)) {
      playRefillFailure(p);
      return;
    }

    ItemStack template = hand.clone();
    template.setAmount(1);
    EquipmentSlot slot = e.getHand();
    J.runEntity(p, () -> refill(p, slot, template, allowed), 1);
  }

  /**
   * The placed stack is the last one when the hand holds a single item at
   * placement time, or already reports zero on servers that consume the item
   * before monitor handlers run.
   */
  static boolean consumesLastOfStack(ItemStack hand) {
    return hand != null && hand.getType() != Material.AIR && hand.getAmount() <= 1;
  }

  private boolean hasRefillCapacity(UUID id, int allowed) {
    return hasRefillCapacity(windows.get(id), M.ms(), allowed);
  }

  private void refill(Player p, EquipmentSlot slot, ItemStack template, int allowed) {
    if (!p.isOnline()) {
      return;
    }

    ItemStack current = slot == EquipmentSlot.OFF_HAND ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand();
    if (current != null && current.getType() != Material.AIR) {
      return;
    }

    UUID playerId = p.getUniqueId();
    if (!hasRefillCapacity(playerId, allowed)) {
      playRefillFailure(p);
      return;
    }

    ItemStack pulled = pullMatching(p, template);
    if (pulled == null) {
      return;
    }

    if (slot == EquipmentSlot.OFF_HAND) {
      p.getInventory().setItemInOffHand(pulled);
    } else {
      p.getInventory().setItemInMainHand(pulled);
    }
    windows.compute(playerId, (id, window) -> nextSuccessfulWindow(window, M.ms()));

    fx(p.getLocation().add(0, 1, 0), FxPriority.TRANSITION)
        .particle(Particle.WAX_ON, 6, 0, 0, 0, 0.3D, 0)
        .column(Particles.END_ROD, 4, 0.8D)
        .chord(Sound.BLOCK_COMPOSTER_FILL, 0.6f, 1.3f, Sound.ITEM_BUNDLE_REMOVE_ONE, 0.4f, 1.4f);

    addStat(p, "architect.supply-line.refills", 1);
    xp(p, getConfig().xpPerRefill);
  }

  private void playRefillFailure(Player player) {
    UUID playerId = player.getUniqueId();
    if (failCd.isReady(playerId, 1500)) {
      failCd.mark(playerId);
      fx(player.getLocation(), FxPriority.TRANSITION).sound(Sound.BLOCK_DISPENSER_FAIL, 0.3f, 0.9f);
    }
  }

  /**
   * Loose stacks first, then bundles, then Adapt backpacks, then shulker boxes.
   * Loose stacks are what a player expects to consume next, containers are the
   * deep supply.
   */
  private ItemStack pullMatching(Player p, ItemStack template) {
    ItemStack[] contents = p.getInventory().getStorageContents();
    int loose = matchingSlot(contents, template, p.getInventory().getHeldItemSlot());
    if (loose >= 0) {
      ItemStack pulled = contents[loose].clone();
      p.getInventory().setItem(loose, null);
      return pulled;
    }

    for (ItemStack candidate : contents) {
      if (candidate == null || candidate.getType() == Material.AIR) {
        continue;
      }

      ItemStack pulled = pullFromBundle(candidate, template);
      if (pulled == null) {
        pulled = pullFromBackpack(candidate, template);
      }
      if (pulled == null) {
        pulled = pullFromShulker(candidate, template);
      }

      if (pulled != null) {
        return pulled;
      }
    }

    return null;
  }

  /**
   * Mode agnostic: both backpack storage models keep an ordinary stack array,
   * so a refill never has to know whether the backpack is in slot or bundle
   * mode. An unreadable payload is left completely untouched.
   */
  private ItemStack pullFromBackpack(ItemStack container, ItemStack template) {
    if (!BackpackItem.isBackpack(container)) {
      return null;
    }

    ItemStack[] stored;
    try {
      stored = BackpackItem.readContents(container);
    } catch (IllegalStateException unreadable) {
      return null;
    }

    BackpackItem.BackpackExtraction extraction = BackpackItem.extractMatching(stored, template);
    if (extraction == null) {
      return null;
    }

    BackpackItem.Data data = BackpackItem.dataOf(container);
    ItemStack[] remaining = BackpackItem.modeOf(container) == BackpackItem.Mode.BUNDLE
        ? BackpackItem.compact(extraction.remaining())
        : extraction.remaining();
    BackpackItem.write(
        container,
        BackpackItem.Data.of(
            data.getId(),
            BackpackItem.Mode.parse(data.getMode()),
            data.getCapacity(),
            BackpackItem.modeOf(container) == BackpackItem.Mode.BUNDLE
                ? BackpackItem.heapWeight(BackpackItem.readHeap(remaining))
                : BackpackItem.countStored(remaining)
        ),
        remaining
    );
    return extraction.pulled();
  }

  private ItemStack pullFromShulker(ItemStack container, ItemStack template) {
    if (!isShulkerBox(container.getType())) {
      return null;
    }

    if (!(container.getItemMeta() instanceof BlockStateMeta meta) || !(meta.getBlockState() instanceof ShulkerBox box)) {
      return null;
    }

    Inventory inv = box.getInventory();
    ItemStack[] stored = inv.getContents();
    int slot = matchingSlot(stored, template);
    if (slot < 0) {
      return null;
    }

    ItemStack pulled = stored[slot].clone();
    inv.setItem(slot, null);
    meta.setBlockState(box);
    container.setItemMeta(meta);
    return pulled;
  }

  private ItemStack pullFromBundle(ItemStack container, ItemStack template) {
    if (!(container.getItemMeta() instanceof BundleMeta meta)) {
      return null;
    }

    BundleExtraction extraction = extractFromBundle(meta.getItems(), template);
    if (extraction == null) {
      return null;
    }

    meta.setItems(extraction.remaining());
    container.setItemMeta(meta);
    return extraction.pulled();
  }

  static boolean isShulkerBox(Material type) {
    return type != null && type.name().endsWith("SHULKER_BOX");
  }

  /**
   * Matches on the whole item, not just its material, so custom named or
   * enchanted stacks are never silently swapped for a plain one.
   */
  static int matchingSlot(ItemStack[] contents, ItemStack template) {
    return matchingSlot(contents, template, -1);
  }

  static int matchingSlot(ItemStack[] contents, ItemStack template, int skipSlot) {
    if (contents == null || template == null) {
      return -1;
    }

    for (int i = 0; i < contents.length; i++) {
      ItemStack candidate = contents[i];
      if (i == skipSlot || candidate == null || candidate.getType() == Material.AIR || candidate.getAmount() <= 0) {
        continue;
      }

      if (candidate.isSimilar(template)) {
        return i;
      }
    }

    return -1;
  }

  static BundleExtraction extractFromBundle(List<ItemStack> items, ItemStack template) {
    if (items == null || items.isEmpty() || template == null) {
      return null;
    }

    List<ItemStack> remaining = new ArrayList<>(items.size());
    ItemStack pulled = null;
    for (ItemStack item : items) {
      if (pulled == null && item != null && item.getType() != Material.AIR && item.isSimilar(template)) {
        pulled = item.clone();
        continue;
      }

      remaining.add(item);
    }

    return pulled == null ? null : new BundleExtraction(pulled, remaining);
  }

  private int getRefillsPerMinute(double factor) {
    return (int) Math.max(1, M.lerp(getConfig().minRefillsPerMinute, getConfig().maxRefillsPerMinute, factor));
  }

  static boolean hasRefillCapacity(RefillWindow window, long now, int allowed) {
    return allowed > 0 && (window == null || now - window.start() >= 60000L || window.used() < allowed);
  }

  static RefillWindow nextSuccessfulWindow(RefillWindow window, long now) {
    if (window == null || now - window.start() >= 60000L) {
      return new RefillWindow(now, 1);
    }
    return new RefillWindow(window.start(), window.used() + 1);
  }

  record RefillWindow(long start, int used) {
  }

  record BundleExtraction(ItemStack pulled, List<ItemStack> remaining) {
  }

  @ConfigDescription("Automatically refill your hand from your inventory, bundles, or shulker boxes when a placed stack runs out.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hand refills allowed per minute at level 0 progression.", impact = "Higher values let low-level players refill more often.")
    int minRefillsPerMinute = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hand refills allowed per minute at maximum level progression.", impact = "Higher values let max-level players refill more often.")
    int maxRefillsPerMinute = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Adaptation xp granted per successful refill.", impact = "Higher values speed up adaptation progression from refills.")
    double xpPerRefill = 2;

    public Config() {
      baseCost = 5;
      costFactor = 0.5;
    }
  }
}
