package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.event.player.FoodLevelChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerAdvancementDoneEvent;
import art.arcane.adapt.platform.event.player.PlayerChangedWorldEvent;
import art.arcane.adapt.platform.event.player.PlayerDropItemEvent;
import art.arcane.adapt.platform.event.player.PlayerExpChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerFishEvent;
import art.arcane.adapt.platform.event.player.PlayerGameModeChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerItemConsumeEvent;
import art.arcane.adapt.platform.event.player.PlayerItemDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerItemHeldEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerJumpEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerRespawnEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleFlightEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSprintEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import io.papermc.paper.advancement.AdvancementDisplay;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

import static art.arcane.adapt.bukkit.event.BukkitEventMaps.block;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.entity;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.hand;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.item;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.player;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.stack;
import static art.arcane.adapt.bukkit.event.BukkitTranslations.cancel;

final class PlayerTranslations {
  private PlayerTranslations() {
  }

  static List<Translation<?>> all() {
    return List.of(INTERACT, INTERACT_ENTITY, MOVE, TELEPORT, JUMP, JOIN, QUIT, RESPAWN, CHANGED_WORLD, DROP_ITEM,
        EXP_CHANGE, FISH, GAME_MODE_CHANGE, ITEM_CONSUME, ITEM_DAMAGE, ITEM_HELD, SWAP_HANDS, TOGGLE_FLIGHT,
        TOGGLE_SNEAK, TOGGLE_SPRINT, FOOD_LEVEL, ADVANCEMENT_DONE);
  }

  static final Translation<org.bukkit.event.player.PlayerInteractEvent> INTERACT = new Translation<>(
      org.bukkit.event.player.PlayerInteractEvent.class, List.of(PlayerInteractEvent.class)) {
    @Override
    public void bridge(org.bukkit.event.player.PlayerInteractEvent event, Poster poster) {
      UseResult useBlock = BukkitEventMaps.result(event.useInteractedBlock());
      UseResult useItem = BukkitEventMaps.result(event.useItemInHand());
      Location point = event.getInteractionPoint();
      PlayerInteractEvent core = new PlayerInteractEvent(player(event.getPlayer()), BukkitEventMaps.action(event.getAction()),
          hand(event.getHand()), item(event.getItem()), block(event.getClickedBlock()),
          BukkitEventMaps.face(event.getBlockFace()),
          point == null ? null : new Vec3(point.getX(), point.getY(), point.getZ()),
          useBlock, useItem);
      poster.post(core);
      if (core.useBlock() != useBlock) {
        event.setUseInteractedBlock(BukkitEventMaps.result(core.useBlock()));
      }
      if (core.useItem() != useItem) {
        event.setUseItemInHand(BukkitEventMaps.result(core.useItem()));
      }
    }
  };

  static final Translation<org.bukkit.event.player.PlayerInteractEntityEvent> INTERACT_ENTITY = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerInteractEntityEvent.class, PlayerInteractEntityEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        PlayerInteractEntityEvent core = new PlayerInteractEntityEvent(player(event.getPlayer()), entity(event.getRightClicked()),
            hand(event.getHand()), event instanceof org.bukkit.event.player.PlayerInteractAtEntityEvent at
            ? BukkitEventMaps.vec(at.getClickedPosition())
            : null);
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.player.PlayerMoveEvent> MOVE = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerMoveEvent.class, PlayerMoveEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        Loc to = BukkitRefs.loc(event.getTo());
        PlayerMoveEvent core = new PlayerMoveEvent(player(event.getPlayer()), BukkitRefs.loc(event.getFrom()), to);
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
        if (!core.to().equals(to)) {
          event.setTo(BukkitRefs.location(core.to()));
        }
      });

  static final Translation<org.bukkit.event.player.PlayerTeleportEvent> TELEPORT = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerTeleportEvent.class, PlayerTeleportEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        Loc to = BukkitRefs.loc(event.getTo());
        PlayerTeleportEvent core = new PlayerTeleportEvent(player(event.getPlayer()), BukkitRefs.loc(event.getFrom()), to,
            BukkitEventMaps.teleportCause(event.getCause()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
        if (!core.to().equals(to)) {
          event.setTo(BukkitRefs.location(core.to()));
        }
      });

  static final Translation<com.destroystokyo.paper.event.player.PlayerJumpEvent> JUMP = BukkitTranslations.simple(
      com.destroystokyo.paper.event.player.PlayerJumpEvent.class, PlayerJumpEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        PlayerJumpEvent core = new PlayerJumpEvent(player(event.getPlayer()), BukkitRefs.loc(event.getFrom()),
            BukkitRefs.loc(event.getTo()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.player.PlayerJoinEvent> JOIN = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerJoinEvent.class, PlayerJoinEvent.class,
      (event, poster) -> poster.post(new PlayerJoinEvent(player(event.getPlayer()))));

  static final Translation<org.bukkit.event.player.PlayerQuitEvent> QUIT = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerQuitEvent.class, PlayerQuitEvent.class,
      (event, poster) -> poster.post(new PlayerQuitEvent(player(event.getPlayer()))));

  static final Translation<org.bukkit.event.player.PlayerRespawnEvent> RESPAWN = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerRespawnEvent.class, PlayerRespawnEvent.class, (event, poster) -> {
        Loc location = BukkitRefs.loc(event.getRespawnLocation());
        PlayerRespawnEvent core = new PlayerRespawnEvent(player(event.getPlayer()), location, event.isBedSpawn(),
            event.isAnchorSpawn());
        poster.post(core);
        if (!core.respawnLocation().equals(location)) {
          event.setRespawnLocation(BukkitRefs.location(core.respawnLocation()));
        }
      });

  static final Translation<org.bukkit.event.player.PlayerChangedWorldEvent> CHANGED_WORLD = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerChangedWorldEvent.class, PlayerChangedWorldEvent.class,
      (event, poster) -> poster.post(new PlayerChangedWorldEvent(player(event.getPlayer()), BukkitRefs.world(event.getFrom()))));

  static final Translation<org.bukkit.event.player.PlayerAdvancementDoneEvent> ADVANCEMENT_DONE = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerAdvancementDoneEvent.class, PlayerAdvancementDoneEvent.class, (event, poster) -> {
        AdvancementDisplay display = event.getAdvancement().getDisplay();
        poster.post(new PlayerAdvancementDoneEvent(player(event.getPlayer()), BukkitRefs.id(event.getAdvancement().getKey()),
            display == null ? null : frame(display.frame())));
      });

  private static AdvancementFrame frame(AdvancementDisplay.Frame frame) {
    return switch (frame) {
      case GOAL -> AdvancementFrame.GOAL;
      case CHALLENGE -> AdvancementFrame.CHALLENGE;
      case TASK -> AdvancementFrame.TASK;
    };
  }

  static final Translation<org.bukkit.event.player.PlayerDropItemEvent> DROP_ITEM = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerDropItemEvent.class, PlayerDropItemEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        PlayerDropItemEvent core = new PlayerDropItemEvent(player(event.getPlayer()), BukkitRefs.itemEntity(event.getItemDrop()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.player.PlayerExpChangeEvent> EXP_CHANGE = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerExpChangeEvent.class, PlayerExpChangeEvent.class, (event, poster) -> {
        int amount = event.getAmount();
        PlayerExpChangeEvent core = new PlayerExpChangeEvent(player(event.getPlayer()), amount);
        poster.post(core);
        if (core.amount() != amount) {
          event.setAmount(core.amount());
        }
      });

  static final Translation<org.bukkit.event.player.PlayerFishEvent> FISH = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerFishEvent.class, PlayerFishEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        int experience = event.getExpToDrop();
        PlayerFishEvent core = new PlayerFishEvent(player(event.getPlayer()), entity(event.getCaught()), entity(event.getHook()),
            BukkitEventMaps.fishState(event.getState()), experience);
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
        if (core.experience() != experience) {
          event.setExpToDrop(core.experience());
        }
      });

  static final Translation<org.bukkit.event.player.PlayerGameModeChangeEvent> GAME_MODE_CHANGE = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerGameModeChangeEvent.class, PlayerGameModeChangeEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        PlayerGameModeChangeEvent core = new PlayerGameModeChangeEvent(player(event.getPlayer()),
            BukkitEventMaps.gameMode(event.getNewGameMode()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.player.PlayerItemConsumeEvent> ITEM_CONSUME = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerItemConsumeEvent.class, PlayerItemConsumeEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        ItemRef item = item(event.getItem());
        ItemRef replacement = item(event.getReplacement());
        PlayerItemConsumeEvent core = new PlayerItemConsumeEvent(player(event.getPlayer()), item, hand(event.getHand()), replacement);
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
        // Bukkit hands out a copy of the consumed item, so in-place edits are compared by value.
        ItemStack chosen = stack(core.item());
        if (!chosen.equals(event.getItem())) {
          event.setItem(chosen);
        }
        if (core.replacement() != replacement) {
          event.setReplacement(stack(core.replacement()));
        }
      });

  static final Translation<org.bukkit.event.player.PlayerItemDamageEvent> ITEM_DAMAGE = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerItemDamageEvent.class, PlayerItemDamageEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        int damage = event.getDamage();
        PlayerItemDamageEvent core = new PlayerItemDamageEvent(player(event.getPlayer()), item(event.getItem()), damage,
            event.getOriginalDamage());
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
        if (core.damage() != damage) {
          event.setDamage(core.damage());
        }
      });

  static final Translation<org.bukkit.event.player.PlayerItemHeldEvent> ITEM_HELD = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerItemHeldEvent.class, PlayerItemHeldEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        PlayerItemHeldEvent core = new PlayerItemHeldEvent(player(event.getPlayer()), event.getPreviousSlot(), event.getNewSlot());
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.player.PlayerSwapHandItemsEvent> SWAP_HANDS = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerSwapHandItemsEvent.class, PlayerSwapHandItemsEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        ItemRef mainHand = item(event.getMainHandItem());
        ItemRef offHand = item(event.getOffHandItem());
        PlayerSwapHandItemsEvent core = new PlayerSwapHandItemsEvent(player(event.getPlayer()), mainHand, offHand);
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
        if (core.mainHand() != mainHand) {
          event.setMainHandItem(stack(core.mainHand()));
        }
        if (core.offHand() != offHand) {
          event.setOffHandItem(stack(core.offHand()));
        }
      });

  static final Translation<org.bukkit.event.player.PlayerToggleFlightEvent> TOGGLE_FLIGHT = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerToggleFlightEvent.class, PlayerToggleFlightEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        PlayerToggleFlightEvent core = new PlayerToggleFlightEvent(player(event.getPlayer()), event.isFlying());
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.player.PlayerToggleSneakEvent> TOGGLE_SNEAK = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerToggleSneakEvent.class, PlayerToggleSneakEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        PlayerToggleSneakEvent core = new PlayerToggleSneakEvent(player(event.getPlayer()), event.isSneaking());
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.player.PlayerToggleSprintEvent> TOGGLE_SPRINT = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerToggleSprintEvent.class, PlayerToggleSprintEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        PlayerToggleSprintEvent core = new PlayerToggleSprintEvent(player(event.getPlayer()), event.isSprinting());
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.entity.FoodLevelChangeEvent> FOOD_LEVEL = new Translation<>(
      org.bukkit.event.entity.FoodLevelChangeEvent.class, List.of(FoodLevelChangeEvent.class)) {
    @Override
    public Class<FoodLevelChangeEvent> target(org.bukkit.event.entity.FoodLevelChangeEvent event) {
      return event.getEntity() instanceof Player ? FoodLevelChangeEvent.class : null;
    }

    @Override
    public void bridge(org.bukkit.event.entity.FoodLevelChangeEvent event, Poster poster) {
      boolean cancelled = event.isCancelled();
      int level = event.getFoodLevel();
      FoodLevelChangeEvent core = new FoodLevelChangeEvent(player((Player) event.getEntity()), level, item(event.getItem()));
      core.setCancelled(cancelled);
      poster.post(core);
      cancel(event, cancelled, core);
      if (core.foodLevel() != level) {
        event.setFoodLevel(core.foodLevel());
      }
    }
  };
}
