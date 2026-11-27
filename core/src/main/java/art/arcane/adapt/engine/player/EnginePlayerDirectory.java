package art.arcane.adapt.engine.player;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.player.PlayerDirectory;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.player.PlayerSection;
import art.arcane.adapt.engine.addon.DeclaredData;
import art.arcane.adapt.engine.world.AdaptDebugMode;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** {@link PlayerDirectory} over the running core's online players. */
public final class EnginePlayerDirectory implements PlayerDirectory {
  EnginePlayerDirectory() {
  }

  @Override
  public Optional<PlayerProfile> find(PlayerRef player) {
    return Optional.<PlayerProfile>ofNullable(profile(player));
  }

  @Override
  public Optional<PlayerProfile> find(UUID player) {
    AdaptPlayer adaptPlayer = loaded(player);
    return adaptPlayer == null ? Optional.empty() : Optional.<PlayerProfile>of(PlayerApi.profile(adaptPlayer));
  }

  @Override
  public EnginePlayerProfile require(PlayerRef player) {
    EnginePlayerProfile profile = profile(player);
    if (profile == null) {
      throw new IllegalStateException("Adapt has not loaded the data of " + player.name() + " yet");
    }
    return profile;
  }

  @Override
  public List<PlayerProfile> online() {
    AdaptServer server = server();
    if (server == null) {
      return List.of();
    }
    List<PlayerProfile> online = new ArrayList<>();
    for (AdaptPlayer adaptPlayer : server.getOnlineAdaptPlayerSnapshot()) {
      if (loaded(adaptPlayer.getPlayer().uuid()) == adaptPlayer) {
        online.add(PlayerApi.profile(adaptPlayer));
      }
    }
    return List.copyOf(online);
  }

  /** The profile of a loaded online player, or null. */
  public EnginePlayerProfile profile(PlayerRef player) {
    Objects.requireNonNull(player, "player");
    AdaptPlayer adaptPlayer = loaded(player.uuid());
    return adaptPlayer != null && player.equals(adaptPlayer.getPlayer()) ? PlayerApi.profile(adaptPlayer) : null;
  }

  @Override
  public void boost(double multiplier, long durationMillis) {
    AdaptServer server = server();
    if (server == null) {
      throw new IllegalStateException("Adapt is not running");
    }
    server.boostXP(multiplier, durationMillis);
  }

  @Override
  public <T> T peek(UUID player, PlayerSection<T> section) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(section, "section");
    AdaptServer server = server();
    if (server == null) {
      throw new IllegalStateException("Adapt is not running");
    }
    PlayerSection<T> declared = DeclaredData.requireSection(section);
    return server.peekData(player).section(declared);
  }

  @Override
  public boolean isDebugging(UUID player) {
    return AdaptDebugMode.isActive(player);
  }

  private static AdaptPlayer loaded(UUID player) {
    AdaptServer server = server();
    return server == null || player == null ? null : server.getOnlineAdaptPlayer(player);
  }

  private static AdaptServer server() {
    AdaptCore core = AdaptCore.current();
    return core == null ? null : core.getAdaptServer();
  }
}
