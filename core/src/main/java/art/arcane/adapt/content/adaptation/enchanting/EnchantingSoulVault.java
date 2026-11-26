package art.arcane.adapt.content.adaptation.enchanting;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.EnchantingMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * Soul Link's item vault: marks one enchanted item per player, pulls it out of the death drops for an XP cost, and
 * returns it on respawn or join. State lives in the owning adaptation's player storage.
 */
public final class EnchantingSoulVault {
  public static final Id ANVIL = Id.minecraft("anvil");
  private static final DataKey<String> TOKEN_KEY = DataKey.adapt("soul-link-token", DataCodecs.STRING);
  private static final String TOKEN_STORAGE_KEY = "soul-link-token";
  private static final String LAST_MARK_STORAGE_KEY = "soul-link-last-mark";
  private static final String PENDING_STORAGE_KEY = "soul-link-pending";
  private static final int DROPPED_XP_PER_LEVEL = 7;

  private final Adaptation<?> owner;
  private final Feedback feedback;
  private final Set<UUID> pendingRestores = ConcurrentHashMap.newKeySet();

  public EnchantingSoulVault(Adaptation<?> owner, Feedback feedback) {
    this.owner = owner;
    this.feedback = feedback;
  }

  /** How the owning adaptation rewards and shows each step. */
  public interface Feedback {
    void refused(Loc at);

    void linked(PlayerRef p);

    void saved(PlayerRef p);

    void returned(PlayerRef p);
  }

  public static boolean isEnchanted(ItemRef item) {
    if (item == null || item.type().isAir()) {
      return false;
    }
    if (!item.enchantments().isEmpty()) {
      return true;
    }
    return !item.storedEnchantments().isEmpty();
  }

  /**
   * Marks the player's main-hand item once the re-mark cooldown is over and {@code permitted} agrees; fizzles at
   * {@code fxAt} while cooling down.
   */
  public boolean link(PlayerRef p, Loc fxAt, long remarkCooldownMs, BooleanSupplier permitted) {
    long now = System.currentTimeMillis();
    long ready = owner.getStorageLong(p, LAST_MARK_STORAGE_KEY, 0L);
    if (now < ready) {
      feedback.refused(fxAt);
      AdaptHud.actionBar(p, C.RED + AdaptLanguage.text(
          EnchantingMessages.SOUL_LINK_COOLING_MESSAGE,
          trusted("duration", Form.duration(ready - now, 1))
      ));
      return false;
    }

    if (!permitted.getAsBoolean()) {
      return false;
    }

    String token = UUID.randomUUID().toString();
    ItemRef marked = p.inventory().mainHand().copy();
    marked.data().set(TOKEN_KEY, token);
    p.inventory().setMainHand(marked);
    owner.setStorage(p, TOKEN_STORAGE_KEY, token);
    owner.setStorage(p, LAST_MARK_STORAGE_KEY, now + remarkCooldownMs);
    AbilityGlyphs.cooldown(p.uuid(), owner, now, remarkCooldownMs);
    feedback.linked(p);
    AdaptHud.actionBar(p, C.LIGHT_PURPLE + AdaptLanguage.text(EnchantingMessages.SOUL_LINK_LINKED));
    return true;
  }

  /** Whether {@code item} is the player's linked item. */
  public boolean isLinked(PlayerRef p, ItemRef item) {
    String token = owner.getStorageString(p, TOKEN_STORAGE_KEY, null);
    return token != null && matchesToken(item, token);
  }

  /** The inventory slot holding the linked item, or -1. */
  public int linkedSlot(PlayerRef p) {
    String token = owner.getStorageString(p, TOKEN_STORAGE_KEY, null);
    if (token == null) {
      return -1;
    }
    PlayerInventoryRef inventory = p.inventory();
    for (int slot = 0; slot < inventory.size(); slot++) {
      if (matchesToken(inventory.item(slot), token)) {
        return slot;
      }
    }
    return -1;
  }

  /**
   * Pulls the linked item out of the death drops for {@code cost} levels and keeps it for the next respawn or join.
   * Callers run at MONITOR so keep-inventory is final: a kept inventory, or an item no longer among the drops, stores
   * nothing.
   */
  public void capture(PlayerDeathEvent e, PlayerRef p, int cost, int reserve) {
    if (e.keepInventory() || owner.getStorageString(p, PENDING_STORAGE_KEY, null) != null) {
      return;
    }

    String token = owner.getStorageString(p, TOKEN_STORAGE_KEY, null);
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

    if (p.level() - cost < reserve) {
      e.drops().add(found);
      feedback.refused(p.location().add(0, 1.0D, 0));
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

    if (!owner.setStorage(p, PENDING_STORAGE_KEY, encoded)) {
      e.drops().add(found);
      return;
    }

    if (e.keepLevel()) {
      e.setNewLevel(Math.max(0, e.newLevel() - cost));
    } else {
      e.setDroppedExp(Math.max(0, e.droppedExp() - (cost * DROPPED_XP_PER_LEVEL)));
    }
    feedback.saved(p);
  }

  public void restorePending(PlayerRef p) {
    if (!p.isOnline()) {
      return;
    }

    String encoded = owner.getStorageString(p, PENDING_STORAGE_KEY, null);
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

  public void forget(UUID playerId) {
    pendingRestores.remove(playerId);
  }

  public void clear() {
    pendingRestores.clear();
  }

  private void returnSaved(PlayerRef p, ItemRef saved, String encoded) {
    try {
      if (!p.isOnline() || !encoded.equals(owner.getStorageString(p, PENDING_STORAGE_KEY, null))) {
        return;
      }

      List<ItemRef> originalContents = p.inventory().contents();
      List<ItemEntityRef> droppedItems = new ArrayList<>();
      try {
        List<ItemRef> overflow = p.inventory().addItem(saved);
        for (ItemRef item : overflow) {
          droppedItems.add(p.world().dropItemNaturally(p.location().position(), item));
        }

        if (!owner.setStorage(p, PENDING_STORAGE_KEY, null)) {
          rollbackDelivery(p, originalContents, droppedItems);
          AdaptLog.warn("Soul Link could not clear pending state for " + p.name() + "; delivery was rolled back.");
          return;
        }
      } catch (Throwable t) {
        rollbackDelivery(p, originalContents, droppedItems);
        logPendingFailure(p, "deliver", t);
        return;
      }

      feedback.returned(p);
      AdaptHud.actionBar(p, C.LIGHT_PURPLE + AdaptLanguage.text(EnchantingMessages.SOUL_LINK_SAVED));
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

  private static boolean matchesToken(ItemRef item, String token) {
    if (item == null || item.type().isAir()) {
      return false;
    }
    return token.equals(item.data().get(TOKEN_KEY));
  }
}
