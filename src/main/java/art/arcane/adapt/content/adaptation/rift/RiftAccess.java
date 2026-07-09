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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.content.item.BoundEnderPearl;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.EqualsAndHashCode;
import manifold.rt.api.util.Pair;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import us.lynuxcraft.deadsilenceiv.advancedchests.AdvancedChestsAPI;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static art.arcane.adapt.api.adaptation.chunk.ChunkLoading.loadChunkAsync;

public class RiftAccess extends SimpleAdaptation<RiftAccess.Config> {
  private final Map<Pair<ChunkPos, Location>, List<InventoryView>> activeViewsMap = new ConcurrentHashMap<>();
  private final Map<ChunkPos, AtomicInteger> tickets = new ConcurrentHashMap<>();

  public RiftAccess() {
    super("rift-access");
    registerConfiguration(Config.class);
    setLocalizationKey("rift.remote_access");
    setIcon(Material.NETHER_STAR);
    setInterval(1000);
    registerRecipe(AdaptRecipe.shapeless()
        .key("rift-remote-access")
        .ingredient(Material.ENDER_PEARL)
        .ingredient(Material.COMPASS)
        .result(BoundEnderPearl.io.withData(new BoundEnderPearl.Data(null)))
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CHEST)
        .key("challenge_rift_access_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ENDER_CHEST)
            .key("challenge_rift_access_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_rift_access_100", "rift.access.remote-opens", 100, 300);
    registerMilestone("challenge_rift_access_2500", "rift.access.remote-opens", 2500, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.ITALIC + Localizer.dLocalize("rift.remote_access.lore1"));
    v.addLore(C.ITALIC + Localizer.dLocalize("rift.remote_access.lore2"));
    v.addLore(C.ITALIC + Localizer.dLocalize("rift.remote_access.lore3"));
  }


  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    Player p = e.getPlayer();
    ItemStack mainHand = p.getInventory().getItemInMainHand();
    ItemStack offHand = p.getInventory().getItemInOffHand();
    Block block = e.getClickedBlock();

    boolean mainHandBound = BoundEnderPearl.isBindableItem(mainHand);
    boolean offHandBound = BoundEnderPearl.isBindableItem(offHand);

    // Cancel event if the enderpearl is in the offhand
    if (offHandBound && e.getHand() != null && e.getHand().equals(EquipmentSlot.OFF_HAND)) {
      e.setCancelled(true);
      return;
    }

    // If the main hand is holding a bound enderpearl
    if (mainHandBound) {
      e.setCancelled(true);
      if (e.getHand() == EquipmentSlot.HAND && hasActiveAdaptation(p)) {
        Adapt.verbose("Player using bound enderpearl.");
        handleEnderPearlInteraction(e, p, block);
      }
    }
  }

  private void handleEnderPearlInteraction(PlayerInteractEvent event, Player player, Block block) {
    boolean canUseInCreative = AdaptConfig.get().allowAdaptationsInCreative;
    boolean isCreative = player.getGameMode() == GameMode.CREATIVE;
    boolean sneaking = player.isSneaking();
    boolean allowed = canUseInCreative || !isCreative;


    // Check if the player is allowed to use the bound item in creative
    if (!allowed) {
      Adapt.info("Player " + player.getName() + " tried to use the bound item in creative mode.");
      return;
    }

    Action action = event.getAction();
    if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
      if (!sneaking) {
        return;
      }

      Block target = action == Action.LEFT_CLICK_BLOCK ? block : player.getTargetBlockExact(5);
      if (target == null || !isStorage(target.getBlockData())) {
        return;
      }

      if (canAccessChest(player, target.getLocation())) {
        linkPearl(player, target, event);
      } else {
        Adapt.verbose("Player " + player.getName() + " doesn't have permission.");
      }
    } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
      openPearl(player);
    }
  }

  private void linkPearl(Player p, Block block, PlayerInteractEvent event) {
    event.setCancelled(true);
    Location center = block.getLocation().add(0.5, 0.5, 0.5);
    timeline(center)
        .duration(10)
        .priority(FxPriority.TRANSITION)
        .cullRadius(24)
        .frame((fx, tick, progress) -> {
          fx.ring(Particle.REVERSE_PORTAL, 0.7, 10, 0.0);
          fx.particle(Particles.END_ROD, 1, 0, 1.0 - progress, 0, 0.02, 0);
          if (tick == 0) {
            fx.sound(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 1.6f);
          }
          if (tick == 9) {
            fx.sound(Sound.BLOCK_ENDER_CHEST_CLOSE, 0.5f, 0.8f);
          }
        })
        .start();
    ItemStack hand = p.getInventory().getItemInMainHand();

    if (hand.getAmount() == 1) {
      BoundEnderPearl.setData(hand, block);
    } else {
      hand.setAmount(hand.getAmount() - 1);
      ItemStack pearl = BoundEnderPearl.withData(block);
      p.getInventory().addItem(pearl).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
    }
  }

  private void openPearl(Player p) {
    Block b = BoundEnderPearl.getBlock(p.getInventory().getItemInMainHand());
    if (b == null || !canAccessChest(p, b.getLocation())) {
      timeline(p)
          .duration(3)
          .priority(FxPriority.TRANSITION)
          .frame((fx, tick, progress) -> {
            if (tick == 0) {
              fx.burst(Particles.SMOKE, 3, 0.2).sound(Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            }
            if (tick == 2) {
              fx.sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
            }
          })
          .start();
      return;
    }
    loadChunkAsync(b.getLocation(), chunk -> {
      if (Bukkit.getPluginManager().isPluginEnabled("AdvancedChests") &&
          AdvancedChestsAPI.getChestManager().getAdvancedChest(b.getLocation()) != null) {
        AdvancedChestsAPI.getChestManager().getAdvancedChest(b.getLocation()).openPage(p, 1);
        Adapt.verbose("Opening AdvancedChests GUI");
      } else if (b.getState() instanceof InventoryHolder holder) {
        InventoryView view = p.openInventory(holder.getInventory());
        if (view == null) return;
        activeViewsMap.computeIfAbsent(Pair.make(new ChunkPos(chunk).add(), b.getLocation()), k -> new ArrayList<>()).add(view);
      }
      timeline(p)
          .duration(6)
          .priority(FxPriority.TRANSITION)
          .frame((fx, tick, progress) -> {
            fx.helix(Particle.PORTAL, 0.5, 1.4, 8, progress * Math.PI * 2.0);
            if (tick == 0) {
              fx.chord(Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.8f, Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1.0f);
            }
          })
          .start();
      addStat(p, "rift.access.remote-opens", 1);
    });
  }

  @Override
  public void onTick() {
    checkActiveViews();
  }

  private void checkActiveViews() {
    Iterator<Map.Entry<Pair<ChunkPos, Location>, List<InventoryView>>> mapIterator = activeViewsMap.entrySet().iterator();
    while (mapIterator.hasNext()) {
      Map.Entry<Pair<ChunkPos, Location>, List<InventoryView>> entry = mapIterator.next();
      removeInvalidViews(entry);
      removeEntryIfViewsEmpty(mapIterator, entry);
    }
  }

  private void removeInvalidViews(Map.Entry<Pair<ChunkPos, Location>, List<InventoryView>> entry) {
    List<InventoryView> views = entry.getValue();
    for (int ii = views.size() - 1; ii >= 0; ii--) {
      InventoryView i = views.get(ii);
      if (shouldRemoveView(i)) {
        views.remove(ii);
      }
    }
  }

  private boolean shouldRemoveView(InventoryView i) {
    Location location = i.getTopInventory().getLocation();
    return !i.getPlayer().getOpenInventory().equals(i) || (location == null || !isStorage(location.getBlock().getBlockData()));
  }

  private void removeEntryIfViewsEmpty(Iterator<Map.Entry<Pair<ChunkPos, Location>, List<InventoryView>>> mapIterator, Map.Entry<Pair<ChunkPos, Location>, List<InventoryView>> entry) {
    List<InventoryView> views = entry.getValue();
    if (views.isEmpty()) {
      mapIterator.remove();
      entry.getKey().getFirst().remove();
    }
  }


  @EventHandler(priority = EventPriority.MONITOR)
  public void on(BlockBurnEvent event) {
    invClose(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(BlockPistonRetractEvent event) {
    for (Block b : event.getBlocks()) {
      invClose(b);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(BlockPistonExtendEvent event) {
    for (Block b : event.getBlocks()) {
      invClose(b);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(BlockExplodeEvent event) {
    for (Block b : event.blockList()) {
      invClose(b);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent event) {
    invClose(event.getBlock());
  }


  private void invClose(Block block) {
    List<InventoryView> views = activeViewsMap.get(block.getLocation());
    if (views != null) {
      for (InventoryView view : views) {
        view.getPlayer().closeInventory();
      }
      activeViewsMap.remove(block.getLocation());
    }
  }


  @ConfigDescription("Craft a Reliquary Portkey to access marked containers remotely.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 3;
      costFactor = 0.2;
      initialCost = 15;
      maxLevel = 1;
    }
  }

  @EqualsAndHashCode
  private class ChunkPos {
    @EqualsAndHashCode.Exclude
    private final WeakReference<World> world;
    private final String name;
    private final int x, z;

    private ChunkPos(Chunk chunk) {
      this.world = new WeakReference<>(chunk.getWorld());
      this.name = chunk.getWorld().getName();
      this.x = chunk.getX();
      this.z = chunk.getZ();
    }

    public ChunkPos add() {
      World world = this.world.get();
      if (world == null) return this;
      if (tickets.computeIfAbsent(this, k -> new AtomicInteger()).getAndIncrement() == 0)
        world.addPluginChunkTicket(x, z, Adapt.instance);
      return this;
    }

    public void remove() {
      World world = this.world.get();
      if (world == null) {
        tickets.remove(this);
        return;
      }
      if (tickets.computeIfAbsent(this, k -> new AtomicInteger()).decrementAndGet() <= 0) {
        world.removePluginChunkTicket(x, z, Adapt.instance);
        world.unloadChunkRequest(x, z);
        tickets.remove(this);
      }
    }
  }
}
