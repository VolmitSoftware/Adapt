package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.entity.EntityInsideBlockEvent;
import art.arcane.adapt.platform.event.entity.EntityPathfindEvent;
import art.arcane.adapt.platform.event.entity.EntityWorldEvent;
import art.arcane.adapt.platform.event.player.PlayerInventorySlotChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerJumpEvent;
import art.arcane.adapt.platform.event.player.PlayerStopUsingItemEvent;
import art.arcane.adapt.platform.event.player.PlayerTradeEvent;
import art.arcane.adapt.platform.event.player.PrePlayerAttackEntityEvent;
import art.arcane.adapt.platform.event.server.ResourcesReloadedEvent;

import java.util.List;
import java.util.Map;

import static art.arcane.adapt.bukkit.event.BukkitEventMaps.block;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.entity;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.player;
import static art.arcane.adapt.bukkit.event.BukkitTranslations.cancel;

/** Translations of events only Paper fires, outside the 1.21 and 26.x sets. Loaded only on Paper. */
final class PaperTranslations {
  private static final Map<io.papermc.paper.event.server.ServerResourcesReloadedEvent.Cause, ResourcesReloadedEvent.Cause> RELOAD_CAUSES =
      BukkitEventMaps.byName(io.papermc.paper.event.server.ServerResourcesReloadedEvent.Cause.class, ResourcesReloadedEvent.Cause.class,
          ResourcesReloadedEvent.Cause.UNKNOWN);

  private PaperTranslations() {
  }

  static List<Translation<?>> all() {
    return List.of(ADD_TO_WORLD, REMOVE_FROM_WORLD, INSIDE_BLOCK, PATHFIND, PRE_ATTACK, STOP_USING, SLOT_CHANGE, TRADE, JUMP,
        RESOURCES_RELOADED);
  }

  static final Translation<com.destroystokyo.paper.event.entity.EntityAddToWorldEvent> ADD_TO_WORLD = BukkitTranslations.simple(
      com.destroystokyo.paper.event.entity.EntityAddToWorldEvent.class, EntityWorldEvent.class,
      (event, poster) -> poster.post(new EntityWorldEvent(entity(event.getEntity()), EntityWorldEvent.Phase.ADD)));

  static final Translation<com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent> REMOVE_FROM_WORLD =
      BukkitTranslations.simple(com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent.class, EntityWorldEvent.class,
          (event, poster) -> poster.post(new EntityWorldEvent(entity(event.getEntity()), EntityWorldEvent.Phase.REMOVE)));

  static final Translation<io.papermc.paper.event.entity.EntityInsideBlockEvent> INSIDE_BLOCK = BukkitTranslations.simple(
      io.papermc.paper.event.entity.EntityInsideBlockEvent.class, EntityInsideBlockEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        EntityInsideBlockEvent core = new EntityInsideBlockEvent(entity(event.getEntity()), block(event.getBlock()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<com.destroystokyo.paper.event.entity.EntityPathfindEvent> PATHFIND = BukkitTranslations.simple(
      com.destroystokyo.paper.event.entity.EntityPathfindEvent.class, EntityPathfindEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        EntityPathfindEvent core = new EntityPathfindEvent(entity(event.getEntity()), BukkitRefs.loc(event.getLoc()),
            entity(event.getTargetEntity()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<io.papermc.paper.event.player.PrePlayerAttackEntityEvent> PRE_ATTACK = BukkitTranslations.simple(
      io.papermc.paper.event.player.PrePlayerAttackEntityEvent.class, PrePlayerAttackEntityEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        PrePlayerAttackEntityEvent core = new PrePlayerAttackEntityEvent(player(event.getPlayer()), entity(event.getAttacked()),
            event.willAttack());
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<io.papermc.paper.event.player.PlayerStopUsingItemEvent> STOP_USING = BukkitTranslations.simple(
      io.papermc.paper.event.player.PlayerStopUsingItemEvent.class, PlayerStopUsingItemEvent.class,
      (event, poster) -> poster.post(new PlayerStopUsingItemEvent(player(event.getPlayer()),
          GapEventMaps.itemOrEmpty(event.getItem()), event.getTicksHeldFor())));

  static final Translation<io.papermc.paper.event.player.PlayerInventorySlotChangeEvent> SLOT_CHANGE = BukkitTranslations.simple(
      io.papermc.paper.event.player.PlayerInventorySlotChangeEvent.class, PlayerInventorySlotChangeEvent.class,
      (event, poster) -> poster.post(new PlayerInventorySlotChangeEvent(player(event.getPlayer()), event.getRawSlot(),
          event.getSlot(), GapEventMaps.itemOrEmpty(event.getOldItemStack()),
          GapEventMaps.itemOrEmpty(event.getNewItemStack()))));

  static final Translation<io.papermc.paper.event.player.PlayerPurchaseEvent> TRADE = new Translation<>(
      io.papermc.paper.event.player.PlayerPurchaseEvent.class, List.of(PlayerTradeEvent.class)) {
    @Override
    public Class<? extends AdaptEvent> target(io.papermc.paper.event.player.PlayerPurchaseEvent event) {
      return event instanceof io.papermc.paper.event.player.PlayerTradeEvent ? PlayerTradeEvent.class : null;
    }

    @Override
    public void bridge(io.papermc.paper.event.player.PlayerPurchaseEvent event, Poster poster) {
      if (!(event instanceof io.papermc.paper.event.player.PlayerTradeEvent trade)) {
        return;
      }
      boolean cancelled = event.isCancelled();
      boolean rewardExp = event.isRewardingExp();
      boolean increaseUses = event.willIncreaseTradeUses();
      PlayerTradeEvent core = new PlayerTradeEvent(player(event.getPlayer()), entity(trade.getVillager()),
          GapEventMaps.itemOrEmpty(event.getTrade().getResult()), rewardExp, increaseUses);
      core.setCancelled(cancelled);
      poster.post(core);
      cancel(event, cancelled, core);
      if (core.rewardExp() != rewardExp) {
        event.setRewardExp(core.rewardExp());
      }
      if (core.increaseTradeUses() != increaseUses) {
        event.setIncreaseTradeUses(core.increaseTradeUses());
      }
    }
  };

  static final Translation<com.destroystokyo.paper.event.player.PlayerJumpEvent> JUMP = BukkitTranslations.simple(
      com.destroystokyo.paper.event.player.PlayerJumpEvent.class, PlayerJumpEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        PlayerJumpEvent core = new PlayerJumpEvent(player(event.getPlayer()), BukkitRefs.loc(event.getFrom()),
            BukkitRefs.loc(event.getTo()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<io.papermc.paper.event.server.ServerResourcesReloadedEvent> RESOURCES_RELOADED =
      BukkitTranslations.simple(io.papermc.paper.event.server.ServerResourcesReloadedEvent.class, ResourcesReloadedEvent.class,
          (event, poster) -> poster.post(new ResourcesReloadedEvent(reloadCause(event.getCause()))));

  private static ResourcesReloadedEvent.Cause reloadCause(io.papermc.paper.event.server.ServerResourcesReloadedEvent.Cause cause) {
    return cause == null ? ResourcesReloadedEvent.Cause.UNKNOWN : RELOAD_CAUSES.get(cause);
  }
}
