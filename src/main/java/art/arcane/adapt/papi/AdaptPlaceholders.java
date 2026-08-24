package art.arcane.adapt.papi;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.mutation.MutationManager;
import art.arcane.adapt.api.mutation.MutationSnapshot;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.xp.NewtonCurve;
import art.arcane.adapt.service.MutationSVC;
import art.arcane.volmlib.util.bukkit.papi.PlaceholderSnapshot;
import art.arcane.volmlib.util.bukkit.papi.PlayerSnapshotStore;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AdaptPlaceholders {
  public static final long QUIT_GRACE_MS = PlayerSnapshotStore.DEFAULT_GRACE_MS;

  private static final AdaptPlaceholders INSTANCE = new AdaptPlaceholders();

  private final PlayerSnapshotStore<AdaptPlayerSnapshot> players = new PlayerSnapshotStore<>();
  private final PlaceholderSnapshot<AdaptCatalogSnapshot> catalog = new PlaceholderSnapshot<>();
  private final AtomicBoolean publishFailureLogged = new AtomicBoolean();

  private AdaptPlaceholders() {
  }

  public static AdaptPlaceholders get() {
    return INSTANCE;
  }

  public PlayerSnapshotStore<AdaptPlayerSnapshot> players() {
    return players;
  }

  public PlaceholderSnapshot<AdaptCatalogSnapshot> catalog() {
    return catalog;
  }

  public void publishCatalog(long revision, List<Skill<?>> skills) {
    if (skills == null) {
      return;
    }

    AdaptCatalogSnapshot current = catalog.get();

    if (current != null && current.revision() == revision) {
      return;
    }

    try {
      catalog.publish(AdaptCatalogSnapshot.build(revision, skills));
    } catch (Throwable failure) {
      reportPublishFailure(failure);
    }
  }

  public void publishPlayer(AdaptPlayer player) {
    if (player == null || player.getPlayer() == null) {
      return;
    }

    UUID playerId = player.getPlayer().getUniqueId();
    PlayerData data = player.getData();

    if (playerId == null || data == null) {
      return;
    }

    try {
      AdaptConfig config = AdaptConfig.get();
      NewtonCurve curve = config.getXpCurve().getCurve();
      players.publish(playerId, AdaptPlayerSnapshotBuilder.build(
          players.get(playerId),
          data,
          mutationView(playerId),
          curve,
          config.experienceMaxLevel,
          config.getPowerPerLevel()
      ));
    } catch (Throwable failure) {
      reportPublishFailure(failure);
    }
  }

  public void evictAfterGrace(UUID playerId) {
    players.evictAfterGrace(playerId, QUIT_GRACE_MS);
  }

  public void evictNow(UUID playerId) {
    players.publish(playerId, null);
  }

  public void clear() {
    players.clear();
    catalog.publish(null);
  }

  private AdaptMutationView mutationView(UUID playerId) {
    MutationSVC service = MutationSVC.get();

    if (service == null) {
      return AdaptMutationView.unavailable();
    }

    MutationManager manager = service.getManager();

    if (manager == null) {
      return AdaptMutationView.unavailable();
    }

    MutationSnapshot source = manager.publishedSnapshot(playerId);

    if (source == null) {
      return AdaptMutationView.unavailable();
    }

    return AdaptPlayerSnapshotBuilder.mutationView(
        source,
        manager.getConfig().isEnabled(),
        manager.getCombatLock().remainingMillis(playerId),
        catalog.get()
    );
  }

  private void reportPublishFailure(Throwable failure) {
    if (!publishFailureLogged.compareAndSet(false, true)) {
      return;
    }

    try {
      Adapt.warn("Failed to publish an Adapt placeholder snapshot: "
          + failure.getClass().getName() + ": " + failure.getMessage());
    } catch (Throwable ignored) {
    }
  }
}
