package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismGrowables;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismGrowthAura;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismGrowthPulses;
import art.arcane.adapt.platform.TaskHandle;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Chronos with Herbalism, replacing Convergence: once per in-game day, the clock gesture in a field ages every crop
 * around you one stage, a few per tick. The growth counts as forced, like bone meal.
 */
public final class SeasonTurn extends Signature<SeasonTurn.Config> {
  private static final long DAY_TICKS = 24_000L;
  private final Map<UUID, Day> lastDay = new ConcurrentHashMap<>();
  private final Map<UUID, Long> searchedAt = new ConcurrentHashMap<>();

  public SeasonTurn(MutationOverride row) {
    super(row);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("wheat"));
  }

  @Override
  public boolean paysOut() {
    return true;
  }

  @Override
  public long cooldownMillis(MutationAdaptation adaptation, int level) {
    return 0L;
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Form.f(radius(level), 0), 1);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerSwapHandItemsEvent e) {
    PlayerRef player = e.player();
    if (SignatureKit.clockSwap(e) && getActiveLevel(player) > 0 && fire(player, null, player.location(), false)) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    searchedAt.remove(e.player().uuid());
  }

  @Override
  protected Outcome run(BlockUse use) {
    PlayerRef player = use.player();
    Loc center = player.location();
    Day day = new Day(center.world().uid(), center.world().fullTime() / DAY_TICKS);
    long now = System.currentTimeMillis();
    Long searched = searchedAt.get(player.uuid());
    if (day.equals(lastDay.get(player.uuid())) || (searched != null && now - searched < getConfig().searchGapMillis)) {
      return Outcome.MISSED;
    }
    Deque<BlockRef> crops = crops(center, radius(use.level()));
    if (crops.isEmpty()) {
      searchedAt.put(player.uuid(), now);
      return Outcome.MISSED;
    }
    lastDay.put(player.uuid(), day);
    int perTick = Math.max(1, getConfig().cropsPerTick);
    AtomicReference<TaskHandle> task = new AtomicReference<>();
    task.set(Tasks.repeatEntity(player, () -> {
      for (int grown = 0; grown < perTick && !crops.isEmpty(); grown++) {
        grow(player, crops.pollFirst());
      }
      TaskHandle handle = task.get();
      if (crops.isEmpty() && handle != null) {
        handle.cancel();
      }
    }, 0, 1, crops::clear));
    return Outcome.INSTANT;
  }

  private Deque<BlockRef> crops(Loc center, double radius) {
    WorldRef world = center.world();
    int r = Math.max(1, (int) Math.ceil(radius));
    int height = Math.max(0, getConfig().verticalReach);
    double limit = radius * radius;
    Deque<BlockRef> crops = new ArrayDeque<>();
    for (int x = -r; x <= r; x++) {
      for (int z = -r; z <= r; z++) {
        if ((x * x) + (z * z) > limit) {
          continue;
        }
        for (int y = -height; y <= height; y++) {
          int blockY = center.blockY() + y;
          if (blockY < world.minHeight() || blockY >= world.maxHeight()) {
            continue;
          }
          Loc at = new Loc(world, center.blockX() + x, blockY, center.blockZ() + z);
          if (!Tasks.isOwnedByCurrentRegion(at)) {
            continue;
          }
          BlockRef block = world.blockAt(at);
          BlockStateRef state = block.state();
          if (HerbalismGrowables.isCrop(block.type()) && state.age() >= 0 && state.age() < state.maxAge()) {
            crops.add(block);
          }
        }
      }
    }
    return crops;
  }

  private void grow(PlayerRef player, BlockRef block) {
    BlockStateRef state = block.state();
    if (!HerbalismGrowables.isCrop(block.type()) || state.age() < 0 || state.age() >= state.maxAge()
        || !HerbalismGrowthPulses.mayGrow(this, player, block)) {
      return;
    }
    block.setState(state.with("age", Integer.toString(state.age() + 1)), true);
    XpProvenance.recordBonemeal(block);
    BlockStateRef grown = block.state();
    HerbalismGrowthAura.growFx(this, block, grown.age() >= grown.maxAge());
  }

  private record Day(UUID world, long day) {
  }

  double radius(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().radiusAtFirstLevel, getConfig().radiusAtTopLevel);
  }

  @ConfigDescription("Once per in-game day, every crop around you grows one stage.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Radius of the field at level 1, in blocks.", impact = "Higher values age a wider field.")
    double radiusAtFirstLevel = 8;
    @ConfigDoc(value = "Radius of the field at the top level, in blocks.", impact = "Higher values age a wider field.")
    double radiusAtTopLevel = 16;
    @ConfigDoc(value = "Blocks above and below your feet that are searched for crops.", impact = "Higher values reach terraced fields at more cost.")
    int verticalReach = 2;
    @ConfigDoc(value = "Least time between two field searches, in milliseconds.", impact = "Lower values let a missed gesture search again sooner.")
    long searchGapMillis = 2000;
    @ConfigDoc(value = "Crops aged per tick.", impact = "Higher values finish the field sooner at more cost per tick.")
    int cropsPerTick = 32;
  }
}
