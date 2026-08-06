package art.arcane.adapt.util.director.specialhandlers;

import art.arcane.adapt.Adapt;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Resolves a target that may be offline, by exact name or by UUID, and treats the omitted-argument
 * sentinel as null so the command can fall back to the sender. Tab completion only offers online
 * players; offline names still parse.
 */
public class NullableOfflinePlayerHandler implements DirectorParameterHandler<OfflinePlayer> {
  @Override
  public KList<OfflinePlayer> getPossibilities() {
    List<OfflinePlayer> options = new ArrayList<>();
    if (Adapt.instance != null && Adapt.instance.getAdaptServer() != null) {
      options.addAll(Adapt.instance.getAdaptServer().getOnlinePlayerSnapshot());
      return new KList<>(options);
    }

    options.addAll(Bukkit.getOnlinePlayers());
    return new KList<>(options);
  }

  @Override
  public String toString(OfflinePlayer offlinePlayer) {
    String name = offlinePlayer.getName();
    return name == null ? offlinePlayer.getUniqueId().toString() : name;
  }

  @Override
  public OfflinePlayer parse(String in, boolean force) throws DirectorParsingException {
    if (in == null) {
      return null;
    }

    String value = in.trim();
    if (value.isEmpty() || value.equals("---") || value.equalsIgnoreCase("null")) {
      return null;
    }

    Player online = Bukkit.getPlayerExact(value);
    if (online != null) {
      return online;
    }

    if (value.length() == 36 && value.indexOf('-') == 8) {
      try {
        return Bukkit.getOfflinePlayer(UUID.fromString(value));
      } catch (IllegalArgumentException ignored) {
        throw new DirectorParsingException("Unable to find Player \"" + in + "\"");
      }
    }

    OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(value);
    if (cached != null) {
      return cached;
    }

    throw new DirectorParsingException("Unable to find Player \"" + in + "\"");
  }

  @Override
  public boolean supports(Class<?> type) {
    return type.equals(OfflinePlayer.class);
  }

  @Override
  public String getRandomDefault() {
    return "playername";
  }
}
