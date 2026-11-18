package art.arcane.adapt.util.director.handlers;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

import java.util.ArrayList;
import java.util.List;

public class PlayerHandler implements DirectorParameterHandler<PlayerRef> {
  @Override
  public KList<PlayerRef> getPossibilities() {
    return new KList<>(playerOptions());
  }

  @Override
  public String toString(PlayerRef player) {
    return player.name();
  }

  @Override
  public PlayerRef parse(String in, boolean force) throws DirectorParsingException {
    KList<PlayerRef> options = getPossibilities(in);
    if (options.isEmpty()) {
      throw new DirectorParsingException("Unable to find Player \"" + in + "\"");
    }

    for (PlayerRef player : options) {
      if (toString(player).equalsIgnoreCase(in)) {
        return player;
      }
    }
    throw new DirectorParsingException("Unable to filter which Player \"" + in + "\"");
  }

  @Override
  public boolean supports(Class<?> type) {
    return type.equals(PlayerRef.class);
  }

  @Override
  public String getRandomDefault() {
    return "playername";
  }

  private static List<PlayerRef> playerOptions() {
    AdaptCore core = AdaptCore.current();
    if (core == null) {
      return List.of();
    }
    if (core.getAdaptServer() != null) {
      return new ArrayList<>(core.getAdaptServer().getOnlinePlayerSnapshot());
    }
    return new ArrayList<>(core.getPlatform().host().onlinePlayers());
  }
}
