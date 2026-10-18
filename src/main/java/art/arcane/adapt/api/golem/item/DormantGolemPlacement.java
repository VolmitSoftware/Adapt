package art.arcane.adapt.api.golem.item;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Objects;

public final class DormantGolemPlacement implements Listener {
  private final Awakener awakener;

  public DormantGolemPlacement(Awakener awakener) {
    this.awakener = Objects.requireNonNull(awakener);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockPlaceEvent e) {
    if (GolemItems.kindOf(e.getItemInHand()).filter(kind -> kind == GolemItemKind.DORMANT_GOLEM).isEmpty()) {
      return;
    }
    e.setCancelled(true);
    awakener.awaken(e);
  }

  public interface Awakener {
    void awaken(BlockPlaceEvent placement);
  }
}
