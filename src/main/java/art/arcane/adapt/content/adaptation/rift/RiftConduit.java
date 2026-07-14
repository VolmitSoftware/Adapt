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
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static art.arcane.adapt.api.adaptation.chunk.ChunkLoading.loadChunkAsync;

public class RiftConduit extends SimpleAdaptation<RiftConduit.Config> {
  static final int HARD_MAX_FLOW_ITEMS = 1152;
  static final double HARD_MAX_RANGE = 512D;
  static final int FLOW_DELIVERY_TIMEOUT_TICKS = 100;

  private final NamespacedKey partnerKey;
  private final NamespacedKey linkKey;
  private final NamespacedKey taglockLocKey;
  private final NamespacedKey taglockIdKey;

  public RiftConduit() {
    super("rift-conduit");
    registerConfiguration(Config.class);
    setIcon(Material.CONDUIT);
    setInterval(1000);
    partnerKey = new NamespacedKey(Adapt.instance, "rift_conduit_partner");
    linkKey = new NamespacedKey(Adapt.instance, "rift_conduit_link");
    taglockLocKey = new NamespacedKey(Adapt.instance, "rift_conduit_taglock_loc");
    taglockIdKey = new NamespacedKey(Adapt.instance, "rift_conduit_taglock_id");
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.ENDER_PEARL)
        .key("challenge_rift_conduit_10")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.CONDUIT)
            .key("challenge_rift_conduit_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_rift_conduit_10", "rift.conduit.links-formed", 10, 500);
    registerMilestone("challenge_rift_conduit_10k", "rift.conduit.items-flowed", 10000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, getThroughput(level), 1);
    statLore(v, Form.f(getRange(level), 0), 2);
    if (isCrossDimension(level)) {
      v.addLore(C.LIGHT_PURPLE + "+ " + Localizer.dLocalize("rift.conduit.lore3"));
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    if (e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    ItemStack hand = p.getInventory().getItemInMainHand();
    if (hand.getType() != Material.ENDER_PEARL) {
      return;
    }

    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
      return;
    }

    Block clicked = e.getClickedBlock();
    boolean container = clicked != null && isConduitContainer(clicked);

    if (isConduitTaglock(hand)) {
      e.setCancelled(true);
      if (container) {
        completeBind(p, level, hand, clicked.getLocation());
      } else {
        p.sendMessage(C.GRAY + Localizer.dLocalize("rift.conduit.msg_need_container"));
        fx(p.getEyeLocation(), FxPriority.TRANSITION)
            .burst(Particles.SMOKE, 2, 0.15)
            .sound(Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.5f, 0.7f);
      }
      return;
    }

    if (p.isSneaking() && container && !hand.hasItemMeta()) {
      e.setCancelled(true);
      beginCapture(p, hand, clicked.getLocation());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(InventoryCloseEvent e) {
    if (!(e.getPlayer() instanceof Player p)) {
      return;
    }

    Inventory inv = e.getInventory();
    Location loc = inv.getLocation();
    if (loc == null) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    Location sourceLoc = loc.getBlock().getLocation();
    int throughput = getThroughput(level);
    double xpPerFlow = getConfig().xpPerFlow;
    J.runAt(sourceLoc, () -> flowFromSource(p, sourceLoc, throughput, xpPerFlow), 1);
  }

  private void beginCapture(Player p, ItemStack hand, Location clicked) {
    Location loc = clicked.getBlock().getLocation();
    String linkId = UUID.randomUUID().toString();
    ItemStack taglock = makeTaglock(loc, linkId);
    if (hand.getAmount() <= 1) {
      p.getInventory().setItemInMainHand(taglock);
    } else {
      hand.setAmount(hand.getAmount() - 1);
      p.getInventory().addItem(taglock).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
    }

    fx(loc.clone().add(0.5, 0.5, 0.5), FxPriority.TRANSITION)
        .ring(Particle.REVERSE_PORTAL, 0.7, 10, 0.0)
        .particle(Particles.END_ROD, 6, 0, 0.6, 0, 0.05, 0.02)
        .chord(Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.2f, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.5f);
    p.sendMessage(C.LIGHT_PURPLE + Localizer.dLocalize("rift.conduit.msg_captured"));
  }

  private void completeBind(Player p, int level, ItemStack taglock, Location clicked) {
    ConduitLocation stored = decodeLocation(getTaglockLocation(taglock));
    String linkId = getTaglockLinkId(taglock);
    if (stored == null || linkId == null) {
      failBind(p, "rift.conduit.msg_stale");
      return;
    }

    World aWorld = WorldIdentity.resolve(stored.worldKey()).orElse(null);
    if (aWorld == null) {
      failBind(p, "rift.conduit.msg_stale");
      return;
    }

    Location aLoc = new Location(aWorld, stored.x(), stored.y(), stored.z());
    Location bLoc = clicked.getBlock().getLocation();
    if (sameBlock(aLoc, bLoc)) {
      failBind(p, "rift.conduit.msg_same");
      return;
    }

    if (!isCrossDimension(level)) {
      if (aWorld != bLoc.getWorld()) {
        failBind(p, "rift.conduit.msg_range");
        return;
      }
      double range = getRange(level);
      if (aLoc.distanceSquared(bLoc) > range * range) {
        failBind(p, "rift.conduit.msg_range");
        return;
      }
    }

    loadChunkAsync(aLoc, chunk -> J.runAt(aLoc, () -> writeSourceThenPartner(p, aLoc, bLoc, linkId)));
  }

  private void writeSourceThenPartner(Player p, Location aLoc, Location bLoc, String linkId) {
    BlockState st = aLoc.getBlock().getState(false);
    if (!(st instanceof Container aContainer)) {
      J.runEntity(p, () -> failBind(p, "rift.conduit.msg_stale"));
      return;
    }

    writeLink(aContainer, bLoc, linkId);
    loadChunkAsync(bLoc, chunk -> J.runAt(bLoc, () -> finishBind(p, aLoc, bLoc, linkId)));
  }

  private void finishBind(Player p, Location aLoc, Location bLoc, String linkId) {
    BlockState st = bLoc.getBlock().getState(false);
    if (!(st instanceof Container bContainer)) {
      loadChunkAsync(aLoc, chunk -> J.runAt(aLoc, () -> clearLinkAt(aLoc, linkId)));
      J.runEntity(p, () -> failBind(p, "rift.conduit.msg_stale"));
      return;
    }

    writeLink(bContainer, aLoc, linkId);
    fx(bLoc.clone().add(0.5, 0.5, 0.5), FxPriority.TRANSITION)
        .ring(Particles.END_ROD, 0.8, 12, 0.1)
        .particle(Particle.REVERSE_PORTAL, 8, 0, 0.6, 0, 0.2, 0.03)
        .chord(Sound.BLOCK_CONDUIT_ACTIVATE, 0.6f, 1.1f, Sound.BLOCK_BEACON_ACTIVATE, 0.4f, 1.4f);
    J.runEntity(p, () -> rewardBind(p, linkId));
  }

  private void rewardBind(Player p, String linkId) {
    if (!p.isOnline()) {
      return;
    }

    ItemStack hand = p.getInventory().getItemInMainHand();
    if (isConduitTaglock(hand) && linkId.equals(getTaglockLinkId(hand))) {
      if (hand.getAmount() <= 1) {
        p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
      } else {
        hand.setAmount(hand.getAmount() - 1);
        p.getInventory().setItemInMainHand(hand);
      }
    }

    addStat(p, "rift.conduit.links-formed", 1);
    xp(p, getConfig().xpOnLink, "rift:conduit:link");
    p.sendMessage(C.LIGHT_PURPLE + Localizer.dLocalize("rift.conduit.msg_linked"));
  }

  private void failBind(Player p, String messageKey) {
    if (!p.isOnline()) {
      return;
    }
    p.sendMessage(C.RED + Localizer.dLocalize(messageKey));
    fx(p.getEyeLocation(), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 3, 0.2)
        .sound(Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.6f, 0.6f);
  }

  private void flowFromSource(Player p, Location sourceLoc, int throughput, double xpPerFlow) {
    BlockState st = sourceLoc.getBlock().getState(false);
    if (!(st instanceof Container source)) {
      return;
    }

    LinkRef link = readLink(source);
    if (link == null) {
      return;
    }

    List<ItemStack> moving = extractItems(source.getInventory(), throughput);
    if (moving.isEmpty()) {
      return;
    }

    Location partnerLoc = link.location();
    String linkId = link.linkId();
    AtomicBoolean settled = new AtomicBoolean(false);
    loadChunkAsync(partnerLoc, chunk -> {
      boolean scheduled = J.runAt(partnerLoc, () -> {
        if (settled.compareAndSet(false, true)) {
          depositToPartner(p, sourceLoc, partnerLoc, linkId, moving, xpPerFlow);
        }
      });
      if (!scheduled && settled.compareAndSet(false, true)) {
        restoreToSource(sourceLoc, moving);
      }
    });
    J.runAt(sourceLoc, () -> {
      if (settled.compareAndSet(false, true)) {
        restoreToSource(sourceLoc, moving);
      }
    }, FLOW_DELIVERY_TIMEOUT_TICKS);
  }

  private void depositToPartner(Player p, Location sourceLoc, Location partnerLoc, String linkId,
                                List<ItemStack> moving, double xpPerFlow) {
    BlockState st = partnerLoc.getBlock().getState(false);
    if (!(st instanceof Container partner) || !linkMatches(partner, linkId, sourceLoc)) {
      restoreToSource(sourceLoc, moving);
      return;
    }

    List<ItemStack> leftovers = addItems(partner.getInventory(), moving);
    int moved = totalAmount(moving) - totalAmount(leftovers);
    if (!leftovers.isEmpty()) {
      restoreToSource(sourceLoc, leftovers);
    }

    if (moved <= 0) {
      return;
    }

    fx(partnerLoc.clone().add(0.5, 0.9, 0.5), FxPriority.AMBIENT)
        .particle(Particle.REVERSE_PORTAL, 6, 0, 0.3, 0, 0.2, 0.03)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.6f);
    int delivered = moved;
    J.runEntity(p, () -> {
      if (!p.isOnline()) {
        return;
      }
      addStat(p, "rift.conduit.items-flowed", delivered);
      xp(p, delivered * xpPerFlow, "rift:conduit:flow");
    });
  }

  private void restoreToSource(Location sourceLoc, List<ItemStack> items) {
    if (items.isEmpty()) {
      return;
    }
    loadChunkAsync(sourceLoc, chunk -> J.runAt(sourceLoc, () -> {
      BlockState st = sourceLoc.getBlock().getState(false);
      if (st instanceof Container container) {
        dropAt(sourceLoc, addItems(container.getInventory(), items));
      } else {
        dropAt(sourceLoc, items);
      }
    }));
  }

  private void dropAt(Location loc, List<ItemStack> items) {
    World world = loc.getWorld();
    if (world == null) {
      return;
    }
    Location drop = loc.clone().add(0.5, 0.5, 0.5);
    for (ItemStack item : items) {
      if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
        world.dropItem(drop, item);
      }
    }
  }

  private List<ItemStack> extractItems(Inventory inventory, int budget) {
    List<ItemStack> removed = new ArrayList<>();
    int remaining = Math.max(0, budget);
    ItemStack[] contents = inventory.getContents();
    for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
      ItemStack stack = contents[slot];
      if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
        continue;
      }
      int take = Math.min(remaining, stack.getAmount());
      ItemStack piece = stack.clone();
      piece.setAmount(take);
      removed.add(piece);
      remaining -= take;
      if (take >= stack.getAmount()) {
        inventory.setItem(slot, null);
      } else {
        ItemStack reduced = stack.clone();
        reduced.setAmount(stack.getAmount() - take);
        inventory.setItem(slot, reduced);
      }
    }
    return removed;
  }

  private List<ItemStack> addItems(Inventory inventory, List<ItemStack> items) {
    List<ItemStack> leftovers = new ArrayList<>();
    for (ItemStack item : items) {
      if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
        continue;
      }
      Map<Integer, ItemStack> overflow = inventory.addItem(item.clone());
      for (ItemStack leftover : overflow.values()) {
        if (leftover != null && !leftover.getType().isAir() && leftover.getAmount() > 0) {
          leftovers.add(leftover);
        }
      }
    }
    return leftovers;
  }

  private int totalAmount(List<ItemStack> items) {
    int sum = 0;
    for (ItemStack item : items) {
      if (item != null && !item.getType().isAir()) {
        sum += item.getAmount();
      }
    }
    return sum;
  }

  private boolean isConduitContainer(Block block) {
    return isStorage(block.getBlockData()) && block.getState(false) instanceof Container;
  }

  private LinkRef readLink(Container container) {
    PersistentDataContainer pdc = container.getPersistentDataContainer();
    String partnerRaw = pdc.get(partnerKey, PersistentDataType.STRING);
    String linkId = pdc.get(linkKey, PersistentDataType.STRING);
    if (partnerRaw == null || linkId == null) {
      return null;
    }
    ConduitLocation decoded = decodeLocation(partnerRaw);
    if (decoded == null) {
      return null;
    }
    World world = WorldIdentity.resolve(decoded.worldKey()).orElse(null);
    if (world == null) {
      return null;
    }
    return new LinkRef(new Location(world, decoded.x(), decoded.y(), decoded.z()), linkId);
  }

  private void writeLink(Container container, Location partner, String linkId) {
    World world = partner.getWorld();
    if (world == null) {
      return;
    }
    PersistentDataContainer pdc = container.getPersistentDataContainer();
    pdc.set(partnerKey, PersistentDataType.STRING,
        encodeLocation(WorldIdentity.serialize(world), partner.getBlockX(), partner.getBlockY(), partner.getBlockZ()));
    pdc.set(linkKey, PersistentDataType.STRING, linkId);
    container.update();
  }

  private void clearLinkAt(Location loc, String linkId) {
    BlockState st = loc.getBlock().getState(false);
    if (!(st instanceof Container container)) {
      return;
    }
    PersistentDataContainer pdc = container.getPersistentDataContainer();
    String existing = pdc.get(linkKey, PersistentDataType.STRING);
    if (existing == null || !existing.equals(linkId)) {
      return;
    }
    pdc.remove(partnerKey);
    pdc.remove(linkKey);
    container.update();
  }

  private boolean linkMatches(Container partner, String linkId, Location sourceLoc) {
    PersistentDataContainer pdc = partner.getPersistentDataContainer();
    String partnerLinkId = pdc.get(linkKey, PersistentDataType.STRING);
    if (partnerLinkId == null || !partnerLinkId.equals(linkId)) {
      return false;
    }
    ConduitLocation decoded = decodeLocation(pdc.get(partnerKey, PersistentDataType.STRING));
    World world = sourceLoc.getWorld();
    return decoded != null
        && world != null
        && decoded.worldKey().equals(WorldIdentity.serialize(world))
        && decoded.x() == sourceLoc.getBlockX()
        && decoded.y() == sourceLoc.getBlockY()
        && decoded.z() == sourceLoc.getBlockZ();
  }

  private ItemStack makeTaglock(Location loc, String linkId) {
    ItemStack item = new ItemStack(Material.ENDER_PEARL, 1);
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }
    meta.setDisplayName(C.LIGHT_PURPLE + Localizer.dLocalize("rift.conduit.taglock_name"));
    List<String> lore = new ArrayList<>();
    lore.add(C.GRAY + Localizer.dLocalize("rift.conduit.taglock_lore1"));
    lore.add(C.DARK_GRAY.toString() + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
    lore.add(C.DARK_GRAY + Localizer.dLocalize("rift.conduit.taglock_lore2"));
    meta.setLore(lore);
    World world = loc.getWorld();
    if (world != null) {
      meta.getPersistentDataContainer().set(taglockLocKey, PersistentDataType.STRING,
          encodeLocation(WorldIdentity.serialize(world), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    }
    meta.getPersistentDataContainer().set(taglockIdKey, PersistentDataType.STRING, linkId);
    item.setItemMeta(meta);
    return item;
  }

  private boolean isConduitTaglock(ItemStack stack) {
    if (stack == null || stack.getType() != Material.ENDER_PEARL || !stack.hasItemMeta()) {
      return false;
    }
    PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
    return pdc.has(taglockLocKey, PersistentDataType.STRING) && pdc.has(taglockIdKey, PersistentDataType.STRING);
  }

  private String getTaglockLocation(ItemStack stack) {
    if (!isConduitTaglock(stack)) {
      return null;
    }
    return stack.getItemMeta().getPersistentDataContainer().get(taglockLocKey, PersistentDataType.STRING);
  }

  private String getTaglockLinkId(ItemStack stack) {
    if (!isConduitTaglock(stack)) {
      return null;
    }
    return stack.getItemMeta().getPersistentDataContainer().get(taglockIdKey, PersistentDataType.STRING);
  }

  private boolean sameBlock(Location a, Location b) {
    return a.getWorld() == b.getWorld()
        && a.getBlockX() == b.getBlockX()
        && a.getBlockY() == b.getBlockY()
        && a.getBlockZ() == b.getBlockZ();
  }

  private boolean isCrossDimension(int level) {
    return getConfig().crossDimensionAtMax && level >= getMaxLevel();
  }

  private int getThroughput(int level) {
    return boundedThroughput(getConfig().throughputBase + (getLevelPercent(level) * getConfig().throughputFactor));
  }

  private double getRange(int level) {
    return boundedRange(getConfig().rangeBase + (getLevelPercent(level) * getConfig().rangeFactor));
  }

  static int boundedThroughput(double value) {
    if (!Double.isFinite(value)) {
      return 1;
    }
    return Math.max(1, Math.min(HARD_MAX_FLOW_ITEMS, (int) Math.round(value)));
  }

  static double boundedRange(double value) {
    if (!Double.isFinite(value)) {
      return 1D;
    }
    return Math.max(1D, Math.min(HARD_MAX_RANGE, value));
  }

  static String encodeLocation(String worldKey, int x, int y, int z) {
    return WorldIdentity.parse(worldKey) + "|" + x + "|" + y + "|" + z;
  }

  static ConduitLocation decodeLocation(String raw) {
    if (raw == null) {
      return null;
    }
    int p3 = raw.lastIndexOf('|');
    if (p3 <= 0) {
      return null;
    }
    int p2 = raw.lastIndexOf('|', p3 - 1);
    if (p2 <= 0) {
      return null;
    }
    int p1 = raw.lastIndexOf('|', p2 - 1);
    if (p1 <= 0) {
      return null;
    }
    try {
      String worldKey = WorldIdentity.parse(raw.substring(0, p1)).toString();
      int x = Integer.parseInt(raw.substring(p1 + 1, p2));
      int y = Integer.parseInt(raw.substring(p2 + 1, p3));
      int z = Integer.parseInt(raw.substring(p3 + 1));
      return new ConduitLocation(worldKey, x, y, z);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  @ConfigDescription("Bind two containers with paired taglocks; items placed in one flow to the other, and cross dimensions at max level.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base number of items moved per flow before level scaling.", impact = "Higher values move more items each time a linked container is closed, up to a hard 1152-item limit.")
    double throughputBase = 48;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional items moved per flow granted at max level, scaling with level.", impact = "Higher values widen the gap between low-level and max-level throughput.")
    double throughputFactor = 336;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base binding range in blocks before level scaling.", impact = "Higher values allow linking containers that are further apart, up to a hard 512-block limit.")
    double rangeBase = 24;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional binding range in blocks granted at max level, scaling with level.", impact = "Higher values let higher levels link containers much further apart.")
    double rangeFactor = 200;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Allows binding across different worlds once the adaptation reaches max level.", impact = "True lets a max-level link ignore world and distance limits; false keeps every link same-world.")
    boolean crossDimensionAtMax = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Skill XP granted when a new link is formed.", impact = "Higher values grant more skill XP per link created.")
    double xpOnLink = 30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Skill XP granted per item flowed between linked containers.", impact = "Higher values grant more skill XP as items move through the conduit.")
    double xpPerFlow = 0.4;

    public Config() {
      baseCost = 8;
      costFactor = 0.6;
      maxLevel = 4;
      initialCost = 8;
    }
  }

  record ConduitLocation(String worldKey, int x, int y, int z) {
  }

  private record LinkRef(Location location, String linkId) {
  }
}
