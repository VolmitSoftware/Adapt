package art.arcane.adapt.util.director.specialhandlers;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.command.KnownPlayer;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Resolves a target that may be offline, by exact name or by UUID, and treats the omitted-argument
 * sentinel as null so the command can fall back to the sender. Tab completion only offers online
 * players; offline names still parse.
 */
public class NullableOfflinePlayerHandler implements DirectorParameterHandler<KnownPlayer> {
  @Override
  public KList<KnownPlayer> getPossibilities() {
    AdaptCore core = AdaptCore.current();
    if (core == null) {
      return new KList<>();
    }
    Collection<PlayerRef> online = core.getAdaptServer() != null
        ? core.getAdaptServer().getOnlinePlayerSnapshot()
        : core.getPlatform().host().onlinePlayers();
    List<KnownPlayer> options = new ArrayList<>();
    for (PlayerRef player : online) {
      options.add(KnownPlayer.of(player));
    }
    return new KList<>(options);
  }

  @Override
  public String toString(KnownPlayer player) {
    return player.displayName();
  }

  @Override
  public KnownPlayer parse(String in, boolean force) throws DirectorParsingException {
    if (in == null) {
      return null;
    }
    String value = in.trim();
    if (value.isEmpty() || value.equals("---") || value.equalsIgnoreCase("null")) {
      return null;
    }
    PlatformHost host = AdaptCore.platform().host();
    PlayerRef online = host.player(value);
    if (online != null) {
      return KnownPlayer.of(online);
    }
    if (value.length() == 36 && value.indexOf('-') == 8) {
      try {
        UUID id = UUID.fromString(value);
        return new KnownPlayer(id, host.knownPlayerName(id));
      } catch (IllegalArgumentException ignored) {
        throw new DirectorParsingException("Unable to find Player \"" + in + "\"");
      }
    }
    UUID cached = host.knownPlayerId(value);
    if (cached != null) {
      String name = host.knownPlayerName(cached);
      return new KnownPlayer(cached, name == null ? value : name);
    }
    throw new DirectorParsingException("Unable to find Player \"" + in + "\"");
  }

  @Override
  public boolean supports(Class<?> type) {
    return type.equals(KnownPlayer.class);
  }

  @Override
  public String getRandomDefault() {
    return "playername";
  }
}
