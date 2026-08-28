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

package art.arcane.adapt.content.adaptation.enchanting;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.EnchantingMessages;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class EnchantingSoulLink extends SimpleAdaptation<EnchantingSoulLink.Config> {
  private static final NamespacedKey TOKEN_KEY = new NamespacedKey("adapt", "soul-link-token");
  private static final String PENDING_STORAGE_KEY = "soul-link-pending";
  private static final int DROPPED_XP_PER_LEVEL = 7;

  private final Set<UUID> pendingRestores = ConcurrentHashMap.newKeySet();

  public EnchantingSoulLink() {
    super("enchanting-soul-link");
    registerConfiguration(Config.class);
    setIcon(Material.TOTEM_OF_UNDYING);
    setInterval(2400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.TOTEM_OF_UNDYING)
        .key("challenge_enchanting_soul_10")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHER_STAR)
            .key("challenge_enchanting_soul_100")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_enchanting_soul_10", "enchanting.soul-link.items-saved", 10, 400);
    registerMilestone("challenge_enchanting_soul_100", "enchanting.soul-link.items-saved", 100, 1200);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, C.YELLOW, "* ", getSaveCost(level), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getRemarkCooldownMs(level), 1), 2);
  }

  static int soulSaveCost(double base, double factor, int min, double levelPercent) {
    return Math.max(min, (int) Math.round(base - (levelPercent * factor)));
  }

  static long soulRemarkCooldownMs(double base, double factor, long min, double levelPercent) {
    return Math.max(min, Math.round(base - (levelPercent * factor)));
  }

  private int getSaveCost(int level) {
    return soulSaveCost(getConfig().saveCostBase, getConfig().saveCostFactor, getConfig().minSaveCost, getLevelPercent(level));
  }

  private long getRemarkCooldownMs(int level) {
    return soulRemarkCooldownMs(getConfig().remarkCooldownBase, getConfig().remarkCooldownFactor, getConfig().minRemarkCooldown, getLevelPercent(level));
  }

  @ReceiveCancelledEvents
  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    if (!p.isSneaking()) {
      return;
    }

    Block block = e.getClickedBlock();
    if (block == null || block.getType() != Material.ANVIL) {
      return;
    }

    if (e.useInteractedBlock() == Event.Result.DENY) {
      return;
    }

    int level = getActiveLevel(p, Player::isSneaking);
    if (level <= 0) {
      return;
    }

    ItemStack hand = p.getInventory().getItemInMainHand();
    if (!isEnchanted(hand)) {
      return;
    }

    e.setCancelled(true);
    long now = System.currentTimeMillis();
    long ready = getStorageLong(p, "soul-link-last-mark", 0L);
    if (now < ready) {
      FxPresets.failFizzle(this, block.getLocation().add(0.5D, 1.0D, 0.5D));
      Adapt.actionbar(p, C.RED + AdaptLanguage.text(
          EnchantingMessages.SOUL_LINK_COOLING_MESSAGE,
          trusted("duration", Form.duration(ready - now, 1))
      ));
      return;
    }

    String token = UUID.randomUUID().toString();
    ItemStack marked = hand.clone();
    ItemMeta meta = marked.getItemMeta();
    meta.getPersistentDataContainer().set(TOKEN_KEY, PersistentDataType.STRING, token);
    marked.setItemMeta(meta);
    p.getInventory().setItemInMainHand(marked);

    setStorage(p, "soul-link-token", token);
    setStorage(p, "soul-link-last-mark", now + getRemarkCooldownMs(level));
    markFx(p);
    Adapt.actionbar(p, C.LIGHT_PURPLE + AdaptLanguage.text(EnchantingMessages.SOUL_LINK_LINKED));
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void on(PlayerDeathEvent e) {
    Player p = e.getEntity();
    if (e.getKeepInventory()) {
      return;
    }

    int level = getActiveDeathLevel(p);
    if (level <= 0) {
      return;
    }

    if (getStorageString(p, PENDING_STORAGE_KEY, null) != null) {
      return;
    }

    String token = getStorageString(p, "soul-link-token", null);
    if (token == null) {
      return;
    }

    ItemStack found = null;
    Iterator<ItemStack> iterator = e.getDrops().iterator();
    while (iterator.hasNext()) {
      ItemStack drop = iterator.next();
      if (matchesToken(drop, token)) {
        found = drop;
        iterator.remove();
        break;
      }
    }

    if (found == null) {
      return;
    }

    int cost = getSaveCost(level);
    if (p.getLevel() < cost) {
      e.getDrops().add(found);
      FxPresets.failFizzle(this, p.getLocation().add(0, 1.0D, 0));
      return;
    }

    String encoded;
    try {
      encoded = Base64.getEncoder().encodeToString(found.serializeAsBytes());
    } catch (Throwable t) {
      e.getDrops().add(found);
      Adapt.warn("Failed to encode a Soul Link item for " + p.getName() + "; item returned to death drops.");
      Adapt.error(t);
      return;
    }

    if (!setStorage(p, PENDING_STORAGE_KEY, encoded)) {
      e.getDrops().add(found);
      return;
    }

    if (e.getKeepLevel()) {
      e.setNewLevel(Math.max(0, e.getNewLevel() - cost));
    } else {
      e.setDroppedExp(Math.max(0, e.getDroppedExp() - (cost * DROPPED_XP_PER_LEVEL)));
    }

    addStat(p, "enchanting.soul-link.items-saved", 1);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerRespawnEvent e) {
    restorePending(e.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent e) {
    Player p = e.getPlayer();
    J.runEntity(p, () -> restorePending(p), 20);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    pendingRestores.remove(e.getPlayer().getUniqueId());
  }

  @Override
  public void unregister() {
    pendingRestores.clear();
    super.unregister();
  }

  private void restorePending(Player p) {
    if (!p.isOnline()) {
      return;
    }

    String encoded = getStorageString(p, PENDING_STORAGE_KEY, null);
    if (encoded == null) {
      return;
    }

    UUID playerId = p.getUniqueId();
    if (!pendingRestores.add(playerId)) {
      return;
    }

    ItemStack saved;
    try {
      saved = ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
    } catch (Throwable t) {
      pendingRestores.remove(playerId);
      logPendingFailure(p, "decode", t);
      return;
    }

    if (!J.runEntity(p, () -> returnSaved(p, saved, encoded), 2)) {
      pendingRestores.remove(playerId);
    }
  }

  private void returnSaved(Player p, ItemStack saved, String encoded) {
    try {
      if (!p.isOnline() || !encoded.equals(getStorageString(p, PENDING_STORAGE_KEY, null))) {
        return;
      }

      ItemStack[] originalContents = cloneContents(p.getInventory().getContents());
      List<Item> droppedItems = new ArrayList<>();
      try {
        Map<Integer, ItemStack> overflow = p.getInventory().addItem(saved);
        for (ItemStack item : overflow.values()) {
          droppedItems.add(p.getWorld().dropItemNaturally(p.getLocation(), item));
        }
        if (!setStorage(p, PENDING_STORAGE_KEY, null)) {
          rollbackDelivery(p, originalContents, droppedItems);
          Adapt.warn("Soul Link could not clear pending state for " + p.getName() + "; delivery was rolled back.");
          return;
        }
      } catch (Throwable t) {
        rollbackDelivery(p, originalContents, droppedItems);
        logPendingFailure(p, "deliver", t);
        return;
      }

      xp(p, getConfig().skillXpOnSave);
      Adapt.actionbar(p, C.LIGHT_PURPLE + AdaptLanguage.text(EnchantingMessages.SOUL_LINK_SAVED));
      timeline(p)
          .duration(10)
          .priority(FxPriority.TRANSITION)
          .cullRadius(20.0D)
          .frame((f, tick, progress) -> {
            f.helix(Particles.TOTEM, (0.8D * (1.0D - progress)) + 0.2D, 1.7D, 4, progress * Math.PI * 3.0D);
            if (tick == 0) {
              f.dome(Particles.END_ROD, 1.4D, 12)
                  .chord(Sound.ITEM_TOTEM_USE, 0.5F, 1.2F, Sound.BLOCK_BEACON_ACTIVATE, 0.4F, 1.4F);
            }
          })
          .start();
    } catch (Throwable t) {
      Adapt.warn("Soul Link delivered an item for " + p.getName() + " but its completion effects failed.");
      Adapt.error(t);
    } finally {
      pendingRestores.remove(p.getUniqueId());
    }
  }

  private static ItemStack[] cloneContents(ItemStack[] contents) {
    ItemStack[] snapshot = new ItemStack[contents.length];
    for (int i = 0; i < contents.length; i++) {
      snapshot[i] = contents[i] == null ? null : contents[i].clone();
    }
    return snapshot;
  }

  private void rollbackDelivery(Player p, ItemStack[] originalContents, List<Item> droppedItems) {
    Throwable failure = null;
    for (Item droppedItem : droppedItems) {
      try {
        if (droppedItem.isValid()) {
          droppedItem.remove();
        }
      } catch (Throwable t) {
        failure = t;
      }
    }

    try {
      p.getInventory().setContents(originalContents);
    } catch (Throwable t) {
      failure = t;
    }

    if (failure != null) {
      logPendingFailure(p, "roll back delivery for", failure);
    }
  }

  private void logPendingFailure(Player p, String operation, Throwable t) {
    Adapt.warn("Failed to " + operation + " a Soul Link item for " + p.getName() + "; pending state retained.");
    Adapt.error(t);
  }

  private void markFx(Player p) {
    timeline(p)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(16.0D)
        .frame((f, tick, progress) -> {
          f.ring(Particles.TOTEM, (0.4D + progress) * 0.8D, 10, 1.0D);
          if (tick == 0) {
            f.particle(Particles.END_ROD, 4, 0, 0.6D, 0, 0.3D, 0.04D)
                .chord(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 0.9F, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5F, 1.5F);
          }
        })
        .start();
  }

  private boolean matchesToken(ItemStack item, String token) {
    if (!isItem(item)) {
      return false;
    }

    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return false;
    }

    PersistentDataContainer data = meta.getPersistentDataContainer();
    return token.equals(data.get(TOKEN_KEY, PersistentDataType.STRING));
  }

  private boolean isEnchanted(ItemStack item) {
    if (!isItem(item)) {
      return false;
    }

    if (!item.getEnchantments().isEmpty()) {
      return true;
    }

    return item.getItemMeta() instanceof EnchantmentStorageMeta stored && !stored.getStoredEnchants().isEmpty();
  }


  @ConfigDescription("Sneak-right-click an anvil to soul-link an enchanted item so it survives death for an XP cost.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Save Cost Base for the Enchanting Soul Link adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double saveCostBase = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Save Cost Factor for the Enchanting Soul Link adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double saveCostFactor = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Save Cost for the Enchanting Soul Link adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int minSaveCost = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Remark Cooldown Base for the Enchanting Soul Link adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double remarkCooldownBase = 60000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Remark Cooldown Factor for the Enchanting Soul Link adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double remarkCooldownFactor = 45000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Remark Cooldown for the Enchanting Soul Link adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long minRemarkCooldown = 8000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Skill Xp On Save for the Enchanting Soul Link adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double skillXpOnSave = 40;

    public Config() {
      baseCost = 6;
      costFactor = 0.85;
      maxLevel = 5;
      initialCost = 6;
    }
  }
}
