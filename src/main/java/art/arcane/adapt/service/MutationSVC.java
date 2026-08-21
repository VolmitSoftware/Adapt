package art.arcane.adapt.service;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.mutation.MutationConfig;
import art.arcane.adapt.api.mutation.MutationManager;
import art.arcane.adapt.api.mutation.MutationType;
import art.arcane.adapt.api.notification.SoundNotification;
import art.arcane.adapt.api.notification.TitleNotification;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.MutationMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.plugin.AdaptService;
import art.arcane.adapt.util.common.scheduling.J;
import lombok.Getter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.io.File;
import java.util.UUID;

public final class MutationSVC implements AdaptService {
  private static volatile MutationSVC instance;

  @Getter
  private volatile MutationManager manager;

  public MutationSVC() {
    instance = this;
  }

  public static MutationSVC get() {
    return instance;
  }

  @Override
  public void onEnable() {
    manager = new MutationManager(MutationConfig.get());
    if (manager.getConfig().isEnabled()) {
      for (AdaptPlayer player : Adapt.instance.getAdaptServer().getOnlineAdaptPlayerSnapshot()) {
        Player bukkitPlayer = player.getPlayer();
        if (bukkitPlayer != null) {
          J.runEntity(bukkitPlayer, () -> manager.reconcile(player));
        }
      }
      Adapt.info("Experimental Mutations are enabled with " + MutationType.values().length + " available Mutations.");
    } else {
      Adapt.info("Experimental Mutations are off. Set enabled=true in adapt/mutations.toml to test them.");
    }
  }

  @Override
  public void onDisable() {
    MutationManager active = manager;
    manager = null;
    if (active != null) {
      active.shutdown();
    }
    if (instance == this) {
      instance = null;
    }
  }

  public synchronized boolean reload() {
    if (!MutationConfig.reload()) {
      return false;
    }
    MutationManager active = manager;
    if (active == null) {
      manager = new MutationManager(MutationConfig.get());
    } else {
      active.reload(MutationConfig.get());
    }
    MutationRuntimeSVC runtime = MutationRuntimeSVC.get();
    if (runtime != null) {
      runtime.onConfigReload();
    }
    return true;
  }

  public synchronized boolean reloadSnapshot(String raw, File sourceFile) {
    if (!MutationConfig.reloadSnapshot(raw, sourceFile)) {
      return false;
    }
    MutationManager active = manager;
    if (active == null) {
      manager = new MutationManager(MutationConfig.get());
    } else {
      active.reload(MutationConfig.get());
    }
    MutationRuntimeSVC runtime = MutationRuntimeSVC.get();
    if (runtime != null) {
      runtime.onConfigReload();
    }
    return true;
  }

  public void onLevelChanged(AdaptPlayer player, int previousLevel, int currentLevel) {
    MutationManager active = manager;
    if (active == null || player == null) {
      return;
    }
    MutationConfig current = active.getConfig();
    if (!current.isEnabled()) {
      return;
    }
    active.reconcile(player);
    if (previousLevel < current.getSlotOneUnlockLevel() && currentLevel >= current.getSlotOneUnlockLevel()) {
      announce(
          player,
          C.LIGHT_PURPLE + AdaptLanguage.text(MutationMessages.SLOT_UNLOCKED_TITLE),
          C.GRAY + AdaptLanguage.text(MutationMessages.SLOT_UNLOCKED_SUBTITLE)
      );
    }
    if (previousLevel < current.getSlotTwoUnlockLevel() && currentLevel >= current.getSlotTwoUnlockLevel()) {
      announce(
          player,
          C.LIGHT_PURPLE + AdaptLanguage.text(MutationMessages.SECOND_SLOT_TITLE),
          C.GRAY + AdaptLanguage.text(MutationMessages.SECOND_SLOT_SUBTITLE)
      );
    }
    if (current.isPerfectAdaptationEnabled()
        && previousLevel < current.getPerfectAdaptationLevel()
        && currentLevel >= current.getPerfectAdaptationLevel()) {
      announce(
          player,
          C.GOLD + AdaptLanguage.text(MutationMessages.PERFECT_TITLE),
          C.GRAY + AdaptLanguage.text(MutationMessages.PERFECT_SUBTITLE)
      );
    } else if (current.isPerfectAdaptationEnabled()
        && previousLevel >= current.getPerfectAdaptationLevel()
        && currentLevel < current.getPerfectAdaptationLevel()) {
      Adapt.messagePlayer(player.getPlayer(), C.YELLOW + AdaptLanguage.text(MutationMessages.PERFECT_LOST));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onCombat(EntityDamageByEntityEvent event) {
    MutationManager active = manager;
    if (active == null || !active.getConfig().isEnabled() || event.getFinalDamage() <= 0D) {
      return;
    }
    Player dealer = resolvePlayerDamager(event.getDamager());
    Player receiver = event.getEntity() instanceof Player player ? player : null;
    if (dealer != null && receiver != null) {
      active.getCombatLock().tag(dealer.getUniqueId(), receiver.getUniqueId());
    } else if (dealer != null) {
      active.getCombatLock().tag(dealer.getUniqueId());
    } else if (receiver != null) {
      active.getCombatLock().tag(receiver.getUniqueId());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    MutationManager active = manager;
    if (active != null && active.getConfig().isEnabled()) {
      J.runEntity(event.getPlayer(), () -> active.reconcile(event.getPlayer()), 1);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    MutationManager active = manager;
    if (active != null) {
      active.cleanup(event.getPlayer().getUniqueId());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onDeath(PlayerDeathEvent event) {
    MutationManager active = manager;
    if (active != null) {
      active.cleanupTransient(event.getEntity().getUniqueId());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onWorldChange(PlayerChangedWorldEvent event) {
    MutationManager active = manager;
    if (active == null) {
      return;
    }
    UUID playerId = event.getPlayer().getUniqueId();
    active.cleanupTransient(playerId);
    if (active.getConfig().isEnabled()) {
      active.reconcile(event.getPlayer());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onRespawn(PlayerRespawnEvent event) {
    MutationManager active = manager;
    if (active != null && active.getConfig().isEnabled()) {
      J.runEntity(event.getPlayer(), () -> active.reconcile(event.getPlayer()), 1);
    }
  }

  private Player resolvePlayerDamager(Entity damager) {
    if (damager instanceof Player player) {
      return player;
    }
    if (!(damager instanceof Projectile projectile)) {
      return null;
    }
    ProjectileSource source = projectile.getShooter();
    return source instanceof Player player ? player : null;
  }

  private void announce(AdaptPlayer player, String title, String subtitle) {
    player.getNot().queue(
        SoundNotification.builder()
            .sound(Sound.BLOCK_BEACON_ACTIVATE)
            .volume(0.8F)
            .pitch(1.35F)
            .group("mutation-progression")
            .build(),
        TitleNotification.builder()
            .in(250)
            .stay(1800)
            .out(900)
            .group("mutation-progression")
            .title(title)
            .subtitle(subtitle)
            .build()
    );
  }
}
