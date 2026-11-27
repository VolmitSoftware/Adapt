package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.architect.ArchitectBuilds;
import art.arcane.adapt.content.adaptation.architect.ArchitectPattern;
import art.arcane.adapt.content.item.MasonsTemplate;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.content.golem.node.NodeContainers;
import art.arcane.adapt.content.golem.node.NodeIndex;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeService;
import art.arcane.adapt.api.world.BuildBatch;
import art.arcane.adapt.api.world.BuildPayment;
import art.arcane.adapt.api.world.BuildBatches;
import art.arcane.adapt.api.world.BuildResult;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.platform.TaskHandle;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Golemancy and Architect, either leading, replacing Convergence: the Mason. Preview a Mason's Template stamp, then
 * right-click one of your golems with the template, and it builds that stamp block by block at golem pace, paid only
 * from your nearest Deposit Node, protection-checked as you, and only while you stay near it.
 */
public final class BuilderGolem extends Signature<BuilderGolem.Config> {
  private final Map<UUID, Preview> previews = new ConcurrentHashMap<>();
  private final Map<UUID, Order> orders = new ConcurrentHashMap<>();
  private final Map<UUID, Job> jobs = new ConcurrentHashMap<>();

  public BuilderGolem(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_block"));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, blocks(level), 1);
    statLore(v, perSecond(level), 2);
  }

  @Handle(priority = EventPriority.LOW, ignoreCancelled = false)
  public void on(PlayerInteractEvent e) {
    if (e.protectionProbe() || e.hand() != Hand.MAIN_HAND || e.action() != InteractAction.RIGHT_CLICK_BLOCK
        || e.clickedBlock() == null || e.clickedFace() == null || e.player().isSneaking()) {
      return;
    }
    MasonsTemplate.Data data = template(e.player().inventory().mainHand());
    if (data == null) {
      return;
    }
    BlockRef anchor = e.clickedBlock().relative(e.clickedFace());
    previews.put(e.player().uuid(), new Preview(anchor.world().uid(), anchor.x(), anchor.y(), anchor.z(),
        data.pattern.fingerprint(), System.currentTimeMillis() + (long) (getConfig().orderSeconds * 1000D)));
  }

  @Handle(priority = EventPriority.LOW, ignoreCancelled = false)
  public void on(PlayerInteractEntityEvent e) {
    PlayerRef player = e.player();
    if (e.protectionProbe() || (e.hand() != null && e.hand() != Hand.MAIN_HAND) || player.isSneaking()
        || !(e.clicked() instanceof MobRef body)) {
      return;
    }
    MasonsTemplate.Data data = template(player.inventory().mainHand());
    GolemHandle golem = data == null ? null : GolemancyRuntime.handle(body).orElse(null);
    if (golem == null || !player.uuid().equals(golem.owner())) {
      return;
    }
    Preview preview = previews.get(player.uuid());
    BlockRef anchor = preview == null ? null : preview.anchor(player.world(), data.pattern.fingerprint());
    if (anchor == null) {
      HudStatus.show(player, TextFormat.GRAY + Text.key("adapt.upgrade.architect.master_builder.nothing").resolve());
      return;
    }
    orders.put(player.uuid(), new Order(body, anchor, data));
    try {
      fire(player, null, anchor.location(), false);
    } finally {
      orders.remove(player.uuid());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    UUID id = e.player().uuid();
    previews.remove(id);
    orders.remove(id);
    Job job = jobs.get(id);
    if (job != null) {
      job.finish(BuildResult.Stop.CANCELLED);
    }
  }

  @Override
  public Outcome run(BlockUse use) {
    PlayerRef player = use.player();
    Order order = orders.remove(player.uuid());
    if (order == null || jobs.containsKey(player.uuid())) {
      return Outcome.MISSED;
    }
    if (depositNear(player, order.golem().location()) == null) {
      HudStatus.show(player, TextFormat.YELLOW + Text.key("adapt.upgrade.architect.master_builder.stopped_items").withMarkup("placed", "0").resolve());
      return Outcome.MISSED;
    }
    List<ArchitectPattern.Placement> placements = order.data().pattern.placements(order.anchor(), order.data().turns,
        order.data().mirror);
    Job job = new Job(player, order.golem(), placements, new NodePayment(player, order.golem()), blocks(use.level()));
    int interval = Math.max(1, (int) Math.round(20D / perSecond(use.level())));
    job.task = AdaptApi.scheduler().repeatEntity(order.golem(), job::tick, interval, interval, () -> job.finish(BuildResult.Stop.CANCELLED));
    if (job.task == null) {
      return Outcome.MISSED;
    }
    jobs.put(player.uuid(), job);
    previews.remove(player.uuid());
    return Outcome.INSTANT;
  }

  /** The owner's nearest Deposit Node container they may open, within reach of the golem. */
  @Nullable InventoryRef depositNear(PlayerRef owner, Loc at) {
    NodeService service = GolemancyRuntime.nodes().orElse(null);
    if (service == null) {
      return null;
    }
    WorldRef world = at.world();
    InventoryRef nearest = null;
    double best = Double.MAX_VALUE;
    for (NodeIndex.Entry entry : service.index().near(world.uid(), at.x(), at.y(), at.z(), getConfig().nodeRadius)) {
      NodeKey key = entry.key();
      if (entry.record().type() != NodeType.DEPOSIT || !owner.uuid().equals(entry.record().owner())) {
        continue;
      }
      BlockRef block = world.blockAt(key.x(), key.y(), key.z());
      if (!AdaptApi.scheduler().owns(block.location()) || !canAccessChest(owner, block.location())) {
        continue;
      }
      InventoryRef inventory = NodeContainers.inventory(block).orElse(null);
      double distance = key.distanceSquared(at.x(), at.y(), at.z());
      if (inventory != null && distance < best) {
        nearest = inventory;
        best = distance;
      }
    }
    return nearest;
  }

  private static @Nullable MasonsTemplate.Data template(ItemRef held) {
    MasonsTemplate tool = MasonsTemplate.find();
    MasonsTemplate.Data data = tool == null ? null : tool.read(held).orElse(null);
    return data != null && data.hasPattern() ? data : null;
  }

  int blocks(int level) {
    return (int) Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().blocksAtFirstLevel,
        getConfig().blocksAtTopLevel));
  }

  double perSecond(int level) {
    return Math.max(0.05D, SignatureKit.scaled(level, getMaxLevel(), getConfig().blocksPerSecondAtFirstLevel,
        getConfig().blocksPerSecondAtTopLevel));
  }

  private record Preview(UUID world, int x, int y, int z, int pattern, long until) {
    @Nullable BlockRef anchor(WorldRef current, int fingerprint) {
      return System.currentTimeMillis() <= until && pattern == fingerprint && current.uid().equals(world)
          ? current.blockAt(x, y, z) : null;
    }
  }

  private record Order(MobRef golem, BlockRef anchor, MasonsTemplate.Data data) {
  }

  /** Pays each block from the Deposit Node found at that moment, so a node broken or swapped mid-job pays nothing. */
  private final class NodePayment implements BuildPayment {
    private final PlayerRef owner;
    private final MobRef golem;
    private @Nullable InventoryRef paidFrom;

    private NodePayment(PlayerRef owner, MobRef golem) {
      this.owner = owner;
      this.golem = golem;
    }

    @Override
    public @Nullable ItemRef take(ItemRef unit) {
      InventoryRef node = depositNear(owner, golem.location());
      paidFrom = node;
      return node == null ? null : BuildPayment.container(node, owner).take(unit);
    }

    @Override
    public void giveBack(ItemRef taken) {
      InventoryRef node = paidFrom;
      if (node == null) {
        BuildPayment.give(owner, List.of(taken));
      } else {
        BuildPayment.container(node, owner).giveBack(taken);
      }
    }
  }

  /** One golem laying one stamp, a block per step, while its owner stays near. */
  private final class Job {
    private final PlayerRef owner;
    private final MobRef golem;
    private final List<ArchitectPattern.Placement> placements;
    private final BuildPayment payment;
    private final int most;
    private int next;
    private int placed;
    private boolean ended;
    private @Nullable TaskHandle task;

    private Job(PlayerRef owner, MobRef golem, List<ArchitectPattern.Placement> placements, BuildPayment payment, int most) {
      this.owner = owner;
      this.golem = golem;
      this.placements = placements;
      this.payment = payment;
      this.most = most;
    }

    private void tick() {
      if (ended) {
        return;
      }
      if (!owner.isOnline() || !golem.isValid() || golem.isDead() || !owner.location().sameWorld(golem.location())
          || owner.location().distanceSquared(golem.location()) > getConfig().leashBlocks * getConfig().leashBlocks) {
        finish(BuildResult.Stop.CANCELLED);
        return;
      }
      if (BuildBatches.isRunning(owner)) {
        return;
      }
      if (placed >= most) {
        finish(BuildResult.Stop.LIMIT);
        return;
      }
      ArchitectPattern.Placement placement = nextOpen();
      if (placement == null) {
        finish(BuildResult.Stop.DONE);
        return;
      }
      BuildBatch step = BuildBatch.of(BuilderGolem.this, owner).limit(1).pace(1).payFrom(payment)
          .place(placement.block(), placement.state())
          .onDone((BuildResult result) -> {
            if (result.placed() > 0) {
              placed++;
            } else if (result.stop() == BuildResult.Stop.NO_ITEMS || result.stop() == BuildResult.Stop.PROTECTED) {
              finish(result.stop());
            }
          })
          .build();
      if (BuildBatches.start(step)) {
        golem.swingHand(Hand.MAIN_HAND);
      }
    }

    private @Nullable ArchitectPattern.Placement nextOpen() {
      double reach = getConfig().golemReach * getConfig().golemReach;
      while (next < placements.size()) {
        ArchitectPattern.Placement placement = placements.get(next++);
        BlockRef block = placement.block();
        WorldRef world = block.world();
        if (placement.state().placementItem() == null || block.y() < world.minHeight() || block.y() >= world.maxHeight()
            || !world.isChunkLoaded(block.x() >> 4, block.z() >> 4) || !(block.isAir() || block.isReplaceable())
            || golem.location().distanceSquared(block.center()) > reach) {
          continue;
        }
        return placement;
      }
      return null;
    }

    private void finish(BuildResult.Stop stop) {
      if (ended) {
        return;
      }
      ended = true;
      if (task != null) {
        task.cancel();
      }
      jobs.remove(owner.uuid(), this);
      if (owner.isOnline()) {
        HudStatus.show(owner, ArchitectBuilds.report(stop, placed));
      }
    }
  }

  @ConfigDescription("A golem given a Mason's Template builds the previewed stamp from your Deposit Node while you stay near.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Most blocks one golem lays for one order at level 1.", impact = "Higher values let golems finish larger stamps.")
    int blocksAtFirstLevel = 216;
    @ConfigDoc(value = "Most blocks one golem lays for one order at the top level.", impact = "Higher values let golems finish larger stamps.")
    int blocksAtTopLevel = 2744;
    @ConfigDoc(value = "Blocks a golem lays per second at level 1.", impact = "Higher values build faster.")
    double blocksPerSecondAtFirstLevel = 1;
    @ConfigDoc(value = "Blocks a golem lays per second at the top level.", impact = "Higher values build faster.")
    double blocksPerSecondAtTopLevel = 4;
    @ConfigDoc(value = "Blocks around the golem searched for your Deposit Node.", impact = "Higher values find nodes farther from the golem.")
    double nodeRadius = 16;
    @ConfigDoc(value = "Blocks from the golem a placement may be.", impact = "Higher values let a golem build farther from where it stands.")
    double golemReach = 24;
    @ConfigDoc(value = "Blocks you may move from the golem before it stops building.", impact = "Higher values let you wander farther.")
    double leashBlocks = 32;
    @ConfigDoc(value = "Seconds after a template preview during which a golem can be given that stamp.", impact = "Higher values give more time to reach the golem.")
    double orderSeconds = 60;
  }
}
