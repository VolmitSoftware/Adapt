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
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import lombok.NoArgsConstructor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ArchitectDemolition extends SimpleAdaptation<ArchitectDemolition.Config> {
  private final Map<Block, DemolitionMark> placed;
  private final Map<UUID, ConcurrentLinkedDeque<Block>> order;

  public ArchitectDemolition() {
    super("architect-demolition");
    registerConfiguration(ArchitectDemolition.Config.class);
    setIcon(Material.TNT);
    setInterval(10880);
    placed = new ConcurrentHashMap<>();
    order = new ConcurrentHashMap<>();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.TNT)
        .key("challenge_architect_demolition_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.TNT)
            .key("challenge_architect_demolition_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_architect_demolition_500", "architect.demolition.blocks-demolished", 500, 300);
    registerMilestone("challenge_architect_demolition_5k", "architect.demolition.blocks-demolished", 5000, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(ArchitectMessages.DEMOLITION_LORE1));
    statLore(v, C.GREEN, "", getWindowMillis(getLevelPercent(level)) / 1000, 2);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPlaceEvent e) {
    Player p = e.getPlayer();
    if (getActiveLevel(p) <= 0) {
      return;
    }

    UUID id = p.getUniqueId();
    Block block = e.getBlock();
    ConcurrentLinkedDeque<Block> deque = order.computeIfAbsent(id, unused -> new ConcurrentLinkedDeque<>());
    deque.addLast(block);
    placed.put(block, new DemolitionMark(id, M.ms(), snapshotPlacedItem(e.getItemInHand())));
    fx(block.getLocation().add(0.5, 0.5, 0.5), FxPriority.AMBIENT)
        .burst(Particles.CRIT_MAGIC, 2, 0.1D);
    int cap = trackingCap(getConfig().maxTrackedPerPlayer);
    while (deque.size() > cap) {
      Block oldest = deque.pollFirst();
      if (oldest != null) {
        placed.remove(oldest);
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockDamageEvent e) {
    if (placed.isEmpty()) {
      return;
    }

    DemolitionMark mark = placed.get(e.getBlock());
    if (mark == null) {
      return;
    }

    Player p = e.getPlayer();
    if (!mark.owner().equals(p.getUniqueId())) {
      return;
    }

    Adaptation.BlockActionContext context = resolveBlockBreakContext(p, e.getBlock().getLocation());
    if (context == null) {
      return;
    }

    if (M.ms() - mark.at() > getWindowMillis(getLevelPercent(context.level()))) {
      placed.remove(e.getBlock(), mark);
      return;
    }

    e.setInstaBreak(true);
    fx(e.getBlock().getLocation().add(0.5, 0.5, 0.5), FxPriority.COMBAT)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.5f, 0.7f);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    if (placed.isEmpty()) {
      return;
    }

    DemolitionMark mark = placed.remove(e.getBlock());
    if (mark == null) {
      return;
    }

    Player p = e.getPlayer();
    if (!mark.owner().equals(p.getUniqueId())) {
      return;
    }

    int level = getActiveBlockBreakLevel(p, e.getBlock().getLocation());
    if (level <= 0) {
      return;
    }

    if (M.ms() - mark.at() > getWindowMillis(getLevelPercent(level))) {
      return;
    }

    List<ItemStack> restitution = collectRestitution(e.getBlock(), mark, p);
    e.setDropItems(false);
    e.setExpToDrop(0);
    restitute(p, e.getBlock().getLocation().add(0.5, 0.5, 0.5), restitution);

    fx(e.getBlock().getLocation().add(0.5, 0.5, 0.5), FxPriority.COMBAT)
        .ring(Particle.SCRAPE, 0.8D, 16, 0.2D)
        .dustBurst(Color.fromRGB(255, 120, 40), 6, 0.4D, 1.0F)
        .chord(Sound.BLOCK_STONE_BREAK, 0.6f, 1.0f, Sound.BLOCK_ANVIL_LAND, 0.15f, 1.6f);

    addStat(p, "architect.demolition.blocks-demolished", 1);
    xp(p, getConfig().xpPerDemolish);
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    ConcurrentLinkedDeque<Block> deque = order.remove(e.getPlayer().getUniqueId());
    if (deque == null) {
      return;
    }

    for (Block block : deque) {
      placed.remove(block);
    }
  }

  /**
   * Collects everything an erased placement owes the player: the item that was
   * placed plus whatever the block was holding at the time of the break.
   */
  private List<ItemStack> collectRestitution(Block block, DemolitionMark mark, Player p) {
    BlockState state = block.getState();
    if (keepsContentsInItem(block.getType())) {
      List<ItemStack> drops = new ArrayList<>(block.getDrops(p.getInventory().getItemInMainHand(), p));
      if (!drops.isEmpty()) {
        return drops;
      }

      return restitution(placedOrFallback(mark, block), null);
    }

    Inventory inventory = blockInventory(state);
    List<ItemStack> items = restitution(placedOrFallback(mark, block),
        inventory == null ? null : inventory.getContents());
    if (inventory != null) {
      inventory.clear();
    }
    return items;
  }

  private void restitute(Player p, Location location, List<ItemStack> items) {
    World world = location.getWorld();
    for (ItemStack item : items) {
      if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
        continue;
      }

      for (ItemStack overflow : p.getInventory().addItem(item).values()) {
        if (world != null && overflow != null && overflow.getType() != Material.AIR) {
          world.dropItemNaturally(location, overflow);
        }
      }
    }
  }

  private ItemStack placedOrFallback(DemolitionMark mark, Block block) {
    ItemStack snapshot = mark.placed();
    if (snapshot != null && snapshot.getType() != Material.AIR) {
      return snapshot.clone();
    }

    Material type = block.getType();
    return type != Material.AIR && type.isItem() ? new ItemStack(type) : null;
  }

  private long getWindowMillis(double factor) {
    return (long) Math.max(1000, M.lerp(getConfig().minWindowSeconds, getConfig().maxWindowSeconds, factor) * 1000D);
  }

  static int trackingCap(int configuredCap) {
    return Math.max(0, configuredCap);
  }

  static ItemStack snapshotPlacedItem(ItemStack hand) {
    if (hand == null || hand.getType() == Material.AIR) {
      return null;
    }

    ItemStack snapshot = hand.clone();
    snapshot.setAmount(1);
    return snapshot;
  }

  /**
   * Shulker boxes carry their contents inside the dropped item, so their
   * inventory must never be harvested separately.
   */
  static boolean keepsContentsInItem(Material type) {
    return type != null && type.name().endsWith("SHULKER_BOX");
  }

  static Inventory blockInventory(BlockState state) {
    if (state instanceof Chest chest) {
      return chest.getBlockInventory();
    }

    return state instanceof BlockInventoryHolder holder ? holder.getInventory() : null;
  }

  static List<ItemStack> restitution(ItemStack placedItem, ItemStack[] contents) {
    List<ItemStack> items = new ArrayList<>();
    if (placedItem != null && placedItem.getType() != Material.AIR) {
      items.add(placedItem);
    }

    if (contents == null) {
      return items;
    }

    for (ItemStack content : contents) {
      if (content == null || content.getType() == Material.AIR || content.getAmount() <= 0) {
        continue;
      }

      items.add(content.clone());
    }

    return items;
  }

  private record DemolitionMark(UUID owner, long at, ItemStack placed) {
  }

  @NoArgsConstructor
  @ConfigDescription("Blocks you recently placed break near-instantly and hand the placed item plus any stored contents straight back to you.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "How many seconds a placement counts as recent at level 0 progression.", impact = "Higher values let low-level players instabreak older placements.")
    double minWindowSeconds = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "How many seconds a placement counts as recent at maximum level progression.", impact = "Higher values let max-level players instabreak older placements.")
    double maxWindowSeconds = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum number of recent placements tracked per player.", impact = "Higher values remember more placements at slightly more memory cost.")
    int maxTrackedPerPlayer = 64;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Adaptation xp granted per demolished block.", impact = "Higher values speed up adaptation progression from demolition.")
    double xpPerDemolish = 1;
  }
}
