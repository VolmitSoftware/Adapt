package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.player.EntityExhaustionEvent;
import art.arcane.adapt.platform.event.player.PlayerAnimationEvent;
import art.arcane.adapt.platform.event.player.PlayerBedEnterEvent;
import art.arcane.adapt.platform.event.player.PlayerBucketEmptyEvent;
import art.arcane.adapt.platform.event.player.PlayerChatEvent;
import art.arcane.adapt.platform.event.player.PlayerItemBreakEvent;
import art.arcane.adapt.platform.event.player.PlayerPreLoginEvent;
import art.arcane.adapt.platform.event.player.PlayerRecipeBookClickEvent;
import art.arcane.adapt.platform.event.player.PlayerShearEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerVelocityEvent;
import art.arcane.adapt.platform.value.Vec3;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.util.Vector;

import java.util.List;

import static art.arcane.adapt.bukkit.event.BukkitEventMaps.block;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.entity;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.player;
import static art.arcane.adapt.bukkit.event.BukkitEventMaps.vec;
import static art.arcane.adapt.bukkit.event.BukkitTranslations.cancel;

final class PlayerGapTranslations {
  private PlayerGapTranslations() {
  }

  static List<Translation<?>> all() {
    return List.of(CHAT, PRE_LOGIN, ANIMATION, BED_ENTER, BUCKET_EMPTY, SHEAR, VELOCITY, ITEM_BREAK,
        RECIPE_BOOK, EXHAUSTION);
  }

  @SuppressWarnings("deprecation")
  static final Translation<org.bukkit.event.player.AsyncPlayerChatEvent> CHAT = BukkitTranslations.simple(
      org.bukkit.event.player.AsyncPlayerChatEvent.class, PlayerChatEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        PlayerChatEvent core = new PlayerChatEvent(player(event.getPlayer()), event.getMessage(), event.isAsynchronous());
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<AsyncPlayerPreLoginEvent> PRE_LOGIN = BukkitTranslations.simple(
      AsyncPlayerPreLoginEvent.class, PlayerPreLoginEvent.class,
      (event, poster) -> poster.post(new PlayerPreLoginEvent(event.getUniqueId(), event.getName(),
          event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED)));

  static final Translation<org.bukkit.event.player.PlayerAnimationEvent> ANIMATION = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerAnimationEvent.class, PlayerAnimationEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        PlayerAnimationEvent core = new PlayerAnimationEvent(player(event.getPlayer()),
            GapEventMaps.animation(event.getAnimationType()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.player.PlayerBedEnterEvent> BED_ENTER = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerBedEnterEvent.class, PlayerBedEnterEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        PlayerBedEnterEvent core = new PlayerBedEnterEvent(player(event.getPlayer()), block(event.getBed()),
            GapEventMaps.bedResult(event.getBedEnterResult()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.player.PlayerBucketEmptyEvent> BUCKET_EMPTY = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerBucketEmptyEvent.class, PlayerBucketEmptyEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        PlayerBucketEmptyEvent core = new PlayerBucketEmptyEvent(player(event.getPlayer()), block(event.getBlock()),
            BukkitRefs.id(event.getBucket()));
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.player.PlayerShearEntityEvent> SHEAR = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerShearEntityEvent.class, PlayerShearEntityEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        Hand hand = BukkitEventMaps.hand(event.getHand());
        PlayerShearEntityEvent core = new PlayerShearEntityEvent(player(event.getPlayer()), entity(event.getEntity()),
            GapEventMaps.itemOrEmpty(event.getItem()), hand == null ? Hand.MAIN_HAND : hand);
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
      });

  static final Translation<org.bukkit.event.player.PlayerVelocityEvent> VELOCITY = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerVelocityEvent.class, PlayerVelocityEvent.class, (event, poster) -> {
        boolean cancelled = event.isCancelled();
        Vec3 velocity = vec(event.getVelocity());
        PlayerVelocityEvent core = new PlayerVelocityEvent(player(event.getPlayer()), velocity);
        core.setCancelled(cancelled);
        poster.post(core);
        cancel(event, cancelled, core);
        if (!core.velocity().equals(velocity)) {
          event.setVelocity(new Vector(core.velocity().x(), core.velocity().y(), core.velocity().z()));
        }
      });

  static final Translation<org.bukkit.event.player.PlayerItemBreakEvent> ITEM_BREAK = BukkitTranslations.simple(
      org.bukkit.event.player.PlayerItemBreakEvent.class, PlayerItemBreakEvent.class,
      (event, poster) -> poster.post(new PlayerItemBreakEvent(player(event.getPlayer()),
          GapEventMaps.itemOrEmpty(event.getBrokenItem()))));

  @SuppressWarnings("removal")
  static final Translation<org.bukkit.event.player.PlayerRecipeBookClickEvent> RECIPE_BOOK = new Translation<>(
      org.bukkit.event.player.PlayerRecipeBookClickEvent.class, List.of(PlayerRecipeBookClickEvent.class)) {
    @Override
    public Class<? extends AdaptEvent> target(org.bukkit.event.player.PlayerRecipeBookClickEvent event) {
      return PlayerRecipeBookClickEvent.class;
    }

    @Override
    public void bridge(org.bukkit.event.player.PlayerRecipeBookClickEvent event, Poster poster) {
      poster.post(new PlayerRecipeBookClickEvent(player(event.getPlayer()), GapEventMaps.key(event.getRecipe()), event.isShiftClick()));
    }
  };

  static final Translation<org.bukkit.event.entity.EntityExhaustionEvent> EXHAUSTION = new Translation<>(
      org.bukkit.event.entity.EntityExhaustionEvent.class, List.of(EntityExhaustionEvent.class)) {
    @Override
    public Class<? extends AdaptEvent> target(org.bukkit.event.entity.EntityExhaustionEvent event) {
      return event.getEntity() instanceof Player ? EntityExhaustionEvent.class : null;
    }

    @Override
    public void bridge(org.bukkit.event.entity.EntityExhaustionEvent event, Poster poster) {
      boolean cancelled = event.isCancelled();
      float exhaustion = event.getExhaustion();
      EntityExhaustionEvent core = new EntityExhaustionEvent(player((Player) event.getEntity()),
          GapEventMaps.exhaustionReason(event.getExhaustionReason()), exhaustion);
      core.setCancelled(cancelled);
      poster.post(core);
      cancel(event, cancelled, core);
      if (core.exhaustion() != exhaustion) {
        event.setExhaustion(core.exhaustion());
      }
    }
  };
}
