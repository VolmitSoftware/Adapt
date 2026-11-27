package art.arcane.adapt.content.golem.item;

import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;

import java.util.Objects;

public final class DormantGolemPlacement {
  private final Awakener awakener;

  public DormantGolemPlacement(Awakener awakener) {
    this.awakener = Objects.requireNonNull(awakener);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockPlaceEvent e) {
    if (e.protectionProbe()
        || GolemItems.kindOf(e.itemInHand()).filter((GolemItemKind kind) -> kind == GolemItemKind.DORMANT_GOLEM).isEmpty()) {
      return;
    }
    e.setCancelled(true);
    awakener.awaken(e);
  }

  public interface Awakener {
    void awaken(BlockPlaceEvent placement);
  }
}
