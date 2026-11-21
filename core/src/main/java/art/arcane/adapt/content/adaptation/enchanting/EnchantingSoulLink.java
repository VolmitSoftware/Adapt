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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.PreferenceConfirmation;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.EnchantingMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerRespawnEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class EnchantingSoulLink extends SimpleAdaptation<EnchantingSoulLink.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> CONFIRM = CommonPreferences.toggle("confirmation", EnchantingMessages.PREFERENCE_ENCHANTINGSOULLINK_CONFIRM, CommonPreferences.Toggle.OFF);
  public static final PlayerPreference<EnchantingPreferences.Reserve> XP_RESERVE = EnchantingPreferences.reserve("xp-reserve", EnchantingMessages.PREFERENCE_ENCHANTINGSOULLINK_XP_RESERVE);

  private static final DataKey<String> TOKEN_KEY = DataKey.adapt("soul-link-token", DataCodecs.STRING);
  private static final Id ANVIL = Id.minecraft("anvil");
  private static final String PENDING_STORAGE_KEY = "soul-link-pending";
  private static final int DROPPED_XP_PER_LEVEL = 7;

  private final Set<UUID> pendingRestores = ConcurrentHashMap.newKeySet();

  public EnchantingSoulLink() {
    super("enchanting-soul-link");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("totem_of_undying"));
    setInterval(2400);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("totem_of_undying"))
        .key("challenge_enchanting_soul_10")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("nether_star"))
            .key("challenge_enchanting_soul_100")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_enchanting_soul_10", "enchanting.soul-link.items-saved", 10, 400);
    registerMilestone("challenge_enchanting_soul_100", "enchanting.soul-link.items-saved", 100, 1200);
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONFIRM, XP_RESERVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
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
  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PlayerInteractEvent e) {
    if (e.action() != InteractAction.RIGHT_CLICK_BLOCK || e.hand() != Hand.MAIN_HAND) {
      return;
    }

    PlayerRef p = e.player();
    if (!p.isSneaking()) {
      return;
    }

    BlockRef block = e.clickedBlock();
    if (block == null || !block.type().equals(ANVIL)) {
      return;
    }

    if (e.useBlock() == UseResult.DENY) {
      return;
    }

    int level = getActiveLevel(p, PlayerRef::isSneaking);
    if (level <= 0) {
      return;
    }

    ItemRef hand = p.inventory().mainHand();
    if (!isEnchanted(hand)) {
      return;
    }

    e.setCancelled(true);
    long now = System.currentTimeMillis();
    long ready = getStorageLong(p, "soul-link-last-mark", 0L);
    if (now < ready) {
      FxPresets.failFizzle(this, block.location().add(0.5D, 1.0D, 0.5D));
      AdaptHud.actionBar(p, C.RED + AdaptLanguage.text(
          EnchantingMessages.SOUL_LINK_COOLING_MESSAGE,
          trusted("duration", Form.duration(ready - now, 1))
      ));
      return;
    }

    if (preference(p, XP_RESERVE).amount() > 0 && p.level() - getSaveCost(level) < preference(p, XP_RESERVE).amount()
        || preferenceEnabled(p, CONFIRM) && !PreferenceConfirmation.confirm(this, p, "link", hand)) {
      return;
    }
    String token = UUID.randomUUID().toString();
    ItemRef marked = hand.copy();
    marked.data().set(TOKEN_KEY, token);
    p.inventory().setMainHand(marked);

    setStorage(p, "soul-link-token", token);
    long remarkCooldownMs = getRemarkCooldownMs(level);
    setStorage(p, "soul-link-last-mark", now + remarkCooldownMs);
    AbilityGlyphs.cooldown(p.uuid(), this, now, remarkCooldownMs);
    markFx(p);
    AdaptHud.actionBar(p, C.LIGHT_PURPLE + AdaptLanguage.text(EnchantingMessages.SOUL_LINK_LINKED));
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerDeathEvent e) {
    if (e.platformCancelled()) {
      return;
    }
    PlayerRef p = e.player();
    if (e.keepInventory()) {
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

    ItemRef found = null;
    Iterator<ItemRef> iterator = e.drops().iterator();
    while (iterator.hasNext()) {
      ItemRef drop = iterator.next();
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
    if (p.level() - cost < preference(p, XP_RESERVE).amount()) {
      e.drops().add(found);
      FxPresets.failFizzle(this, p.location().add(0, 1.0D, 0));
      return;
    }

    String encoded;
    try {
      encoded = Base64.getEncoder().encodeToString(AdaptCore.platform().host().items().serialize(found));
    } catch (Throwable t) {
      e.drops().add(found);
      AdaptLog.warn("Failed to encode a Soul Link item for " + p.name() + "; item returned to death drops.");
      AdaptLog.error(t);
      return;
    }

    if (!setStorage(p, PENDING_STORAGE_KEY, encoded)) {
      e.drops().add(found);
      return;
    }

    if (e.keepLevel()) {
      e.setNewLevel(Math.max(0, e.newLevel() - cost));
    } else {
      e.setDroppedExp(Math.max(0, e.droppedExp() - (cost * DROPPED_XP_PER_LEVEL)));
    }

    addStat(p, "enchanting.soul-link.items-saved", 1);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerRespawnEvent e) {
    restorePending(e.player());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent e) {
    PlayerRef p = e.player();
    Tasks.runEntity(p, () -> restorePending(p), 20);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    pendingRestores.remove(e.player().uuid());
  }

  @Override
  public void unregister() {
    pendingRestores.clear();
    super.unregister();
  }

  private void restorePending(PlayerRef p) {
    if (!p.isOnline()) {
      return;
    }

    String encoded = getStorageString(p, PENDING_STORAGE_KEY, null);
    if (encoded == null) {
      return;
    }

    UUID playerId = p.uuid();
    if (!pendingRestores.add(playerId)) {
      return;
    }

    ItemRef saved;
    try {
      saved = AdaptCore.platform().host().items().deserialize(Base64.getDecoder().decode(encoded));
    } catch (Throwable t) {
      pendingRestores.remove(playerId);
      logPendingFailure(p, "decode", t);
      return;
    }

    if (!Tasks.runEntity(p, () -> returnSaved(p, saved, encoded), 2)) {
      pendingRestores.remove(playerId);
    }
  }

  private void returnSaved(PlayerRef p, ItemRef saved, String encoded) {
    try {
      if (!p.isOnline() || !encoded.equals(getStorageString(p, PENDING_STORAGE_KEY, null))) {
        return;
      }

      List<ItemRef> originalContents = p.inventory().contents();
      List<ItemEntityRef> droppedItems = new ArrayList<>();
      try {
        List<ItemRef> overflow = p.inventory().addItem(saved);
        for (ItemRef item : overflow) {
          droppedItems.add(p.world().dropItemNaturally(p.location().position(), item));
        }
        if (!setStorage(p, PENDING_STORAGE_KEY, null)) {
          rollbackDelivery(p, originalContents, droppedItems);
          AdaptLog.warn("Soul Link could not clear pending state for " + p.name() + "; delivery was rolled back.");
          return;
        }
      } catch (Throwable t) {
        rollbackDelivery(p, originalContents, droppedItems);
        logPendingFailure(p, "deliver", t);
        return;
      }

      xp(p, getConfig().skillXpOnSave);
      AdaptHud.actionBar(p, C.LIGHT_PURPLE + AdaptLanguage.text(EnchantingMessages.SOUL_LINK_SAVED));
      timeline(p)
          .duration(10)
          .priority(FxPriority.TRANSITION)
          .cullRadius(20.0D)
          .frame((f, tick, progress) -> {
            f.helix(Particles.TOTEM, (0.8D * (1.0D - progress)) + 0.2D, 1.7D, 4, progress * Math.PI * 3.0D);
            if (tick == 0) {
              f.dome(Particles.END_ROD, 1.4D, 12)
                  .chord(Sounds.ITEM_TOTEM_USE, 0.5F, 1.2F, Sounds.BLOCK_BEACON_ACTIVATE, 0.4F, 1.4F);
            }
          })
          .start();
    } catch (Throwable t) {
      AdaptLog.warn("Soul Link delivered an item for " + p.name() + " but its completion effects failed.");
      AdaptLog.error(t);
    } finally {
      pendingRestores.remove(p.uuid());
    }
  }

  private void rollbackDelivery(PlayerRef p, List<ItemRef> originalContents, List<ItemEntityRef> droppedItems) {
    Throwable failure = null;
    for (ItemEntityRef droppedItem : droppedItems) {
      try {
        if (droppedItem != null && droppedItem.isValid()) {
          droppedItem.remove();
        }
      } catch (Throwable t) {
        failure = t;
      }
    }

    try {
      PlayerInventoryRef inventory = p.inventory();
      for (int slot = 0; slot < originalContents.size(); slot++) {
        ItemRef item = originalContents.get(slot);
        inventory.setItem(slot, item == null || item.isEmpty() ? null : item);
      }
    } catch (Throwable t) {
      failure = t;
    }

    if (failure != null) {
      logPendingFailure(p, "roll back delivery for", failure);
    }
  }

  private void logPendingFailure(PlayerRef p, String operation, Throwable t) {
    AdaptLog.warn("Failed to " + operation + " a Soul Link item for " + p.name() + "; pending state retained.");
    AdaptLog.error(t);
  }

  private void markFx(PlayerRef p) {
    timeline(p)
        .duration(8)
        .priority(FxPriority.TRANSITION)
        .cullRadius(16.0D)
        .frame((f, tick, progress) -> {
          f.ring(Particles.TOTEM, (0.4D + progress) * 0.8D, 10, 1.0D);
          if (tick == 0) {
            f.particle(Particles.END_ROD, 4, 0, 0.6D, 0, 0.3D, 0.04D)
                .chord(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 0.9F, Sounds.BLOCK_ENCHANTMENT_TABLE_USE, 0.5F, 1.5F);
          }
        })
        .start();
  }

  private boolean matchesToken(ItemRef item, String token) {
    if (!isItem(item)) {
      return false;
    }

    return token.equals(item.data().get(TOKEN_KEY));
  }

  private boolean isEnchanted(ItemRef item) {
    if (!isItem(item)) {
      return false;
    }

    if (!item.enchantments().isEmpty()) {
      return true;
    }

    return !item.storedEnchantments().isEmpty();
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
