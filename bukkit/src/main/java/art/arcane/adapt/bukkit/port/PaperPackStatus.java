package art.arcane.adapt.bukkit.port;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/** The last resource pack status the client reported, packs sent before join included. Paper only. */
final class PaperPackStatus {
  private PaperPackStatus() {
  }

  static PlayerResourcePackStatusEvent.Status of(Player player) {
    return player.getResourcePackStatus();
  }
}
