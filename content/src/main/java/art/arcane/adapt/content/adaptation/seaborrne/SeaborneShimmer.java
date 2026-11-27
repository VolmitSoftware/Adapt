package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.viewer.ViewerDisplayPort;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.api.world.BlockScan;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Deep Salvager's private glow on chests and barrels near the player, owned by the calling adaptation. */
public final class SeaborneShimmer {
  public static final int HARD_MAX_RANGE = 9;
  private static final int VERTICAL_RANGE = 3;
  private static final int HARD_MAX_BLOCKS = 3200;
  private static final int MAX_SHIMMER = 6;
  private static final Id BARREL = Id.minecraft("barrel");
  private static final Set<Id> CONTAINERS = Set.of(Id.minecraft("chest"), Id.minecraft("trapped_chest"), BARREL);

  private final Adaptation<?> owner;
  private final Predicate<PlayerRef> showing;
  private final Function<PlayerRef, Rgb> color;
  private final Consumer<PlayerRef> found;
  private final Map<UUID, UUID> activeScans = new ConcurrentHashMap<>();

  public SeaborneShimmer(Adaptation<?> owner, Predicate<PlayerRef> showing, Function<PlayerRef, Rgb> color,
                         Consumer<PlayerRef> found) {
    this.owner = owner;
    this.showing = showing;
    this.color = color;
    this.found = found;
  }

  public boolean scanning(UUID playerId) {
    return activeScans.containsKey(playerId);
  }

  public void scan(PlayerRef player, int range, SeaborneDeepSalvager.ContainerTypes selection, int durationTicks) {
    Loc base = player.location();
    if (base.world() == null) {
      return;
    }
    int baseY = base.blockY();
    int radius = Math.min(HARD_MAX_RANGE, range);
    BlockScan request = BlockScan.around(base)
        .radius(radius)
        .denseRadius(radius)
        .maxSamples(HARD_MAX_BLOCKS)
        .seed(0)
        .maxResults(MAX_SHIMMER)
        .matchingBlocks(block -> Math.abs(block.y() - baseY) <= VERTICAL_RANGE
            && CONTAINERS.contains(block.type())
            && (selection == SeaborneDeepSalvager.ContainerTypes.ALL
            || (selection == SeaborneDeepSalvager.ContainerTypes.BARRELS) == block.type().equals(BARREL)))
        .onComplete(result -> complete(player, result, durationTicks))
        .build();
    UUID scanId = request.submit(owner, player.uuid());
    activeScans.put(player.uuid(), scanId);
  }

  public void forget(UUID playerId) {
    activeScans.remove(playerId);
    BlockScan.cancel(owner, playerId);
    displays().clearViewer(owner.getId().toString(), playerId);
  }

  public void clear() {
    activeScans.clear();
    BlockScan.cancelAll(owner);
    displays().clearChannel(owner.getId().toString());
  }

  private void complete(PlayerRef player, BlockScan.Result result, int durationTicks) {
    UUID playerId = player.uuid();
    if (!result.scan().equals(activeScans.get(playerId))) {
      return;
    }
    if (!AdaptApi.scheduler().runEntity(player, () -> show(player, playerId, result, durationTicks))) {
      activeScans.remove(playerId, result.scan());
    }
  }

  private void show(PlayerRef player, UUID playerId, BlockScan.Result result, int durationTicks) {
    if (!activeScans.remove(playerId, result.scan()) || !player.isOnline() || !showing.test(player)) {
      return;
    }
    List<BlockScan.Match> matches = result.matches();
    for (BlockScan.Match match : matches) {
      BlockStateRef block = AdaptApi.platform().host().items().blockState(match.block());
      if (block == null) {
        continue;
      }
      Loc location = new Loc(result.world(), match.x(), match.y(), match.z());
      displays().showBlock(owner.getId().toString(), "container-" + match.x() + ":" + match.y() + ":" + match.z(), player,
          location, block, color.apply(player), durationTicks);
    }
    if (!matches.isEmpty()) {
      found.accept(player);
    }
  }

  /** Shows {@code block} glowing at {@code location} to {@code player} alone, under this shimmer's colour. */
  public void outline(PlayerRef player, String id, Loc location, BlockStateRef block, int durationTicks) {
    if (showing.test(player)) {
      displays().showBlock(owner.getId().toString(), id, player, location, block, color.apply(player), durationTicks);
    }
  }

  public static Rgb color(SeaborneDeepSalvager.OutlineColor outline) {
    return switch (outline) {
      case AQUA -> new Rgb(70, 230, 235);
      case GOLD -> new Rgb(255, 195, 50);
      case PURPLE -> new Rgb(180, 90, 235);
    };
  }

  private static ViewerDisplayPort displays() {
    return ViewerDisplayPort.current();
  }
}
