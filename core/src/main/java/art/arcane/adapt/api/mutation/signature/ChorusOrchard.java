package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismGrowables;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismHarvest;
import art.arcane.adapt.content.adaptation.herbalism.upgraded.HarvestMoon;
import art.arcane.adapt.content.adaptation.rift.RiftBlink;
import art.arcane.adapt.content.adaptation.rift.RiftBlinkPath;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.player.PlayerHarvestBlockEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rift leading Herbalism, replacing Convergence: each mature crop you harvest grows the chorus flowers around it one
 * step, recorded as forced growth, and chorus fruit sends you to the spot you look at instead of a random one.
 */
public final class ChorusOrchard extends Signature<ChorusOrchard.Config> implements HarvestMoon.SweepHook {
  private static final Id CHORUS_FLOWER = Id.minecraft("chorus_flower");
  private static final Id CHORUS_PLANT = Id.minecraft("chorus_plant");
  private static final Id END_STONE = Id.minecraft("end_stone");
  private static final int LAST_AGE = 5;

  private final Map<UUID, PlayerTeleportEvent> eating = new ConcurrentHashMap<>();
  private final Cooldowns aimed = cooldowns();
  private final Cooldowns ripened = cooldowns();

  public ChorusOrchard(MutationOverride row) {
    super(row);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("chorus_flower"));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Form.f(range(level), 0), 1);
    statLore(v, flowersPerHarvest(level), 2);
  }

  @Override
  public long cooldownMillis(MutationAdaptation adaptation, int level) {
    return 0L;
  }

  @Override
  public boolean paysOut() {
    return true;
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerTeleportEvent e) {
    PlayerRef player = e.player();
    long cooldown = Math.max(0L, getConfig().aimCooldownMillis);
    if (e.cause() != TeleportCause.CHORUS_FRUIT || getActiveLevel(player) <= 0
        || !aimed.isReady(player.uuid(), cooldown)) {
      return;
    }
    eating.put(player.uuid(), e);
    try {
      if (fire(player, null, player.location(), false)) {
        aimed.mark(player.uuid(), cooldown);
      }
    } finally {
      eating.remove(player.uuid());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    if (!e.protectionProbe()) {
      harvested(e.player(), e.block());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerHarvestBlockEvent e) {
    harvested(e.player(), e.harvestedBlock());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    eating.remove(e.player().uuid());
  }

  @Override
  public boolean takesProduce(PlayerRef p) {
    return false;
  }

  @Override
  public List<ItemRef> swept(PlayerRef p, BlockRef target, boolean farmed, List<ItemRef> produce) {
    if (getActiveLevel(p) > 0) {
      ripen(p, target, farmed);
    }
    return produce;
  }

  private void harvested(PlayerRef player, BlockRef crop) {
    if (crop == null || !HerbalismGrowables.isCrop(crop.type()) || !HerbalismHarvest.isMature(crop)
        || getActiveLevel(player) <= 0) {
      return;
    }
    ripen(player, crop, MutationPayouts.farmedHarvest(crop));
  }

  private void ripen(PlayerRef player, BlockRef crop, boolean farmed) {
    long cooldown = Math.max(0L, getConfig().ripenCooldownMillis);
    if (ripened.isReady(player.uuid(), cooldown) && fire(player, null, crop.center(), farmed)) {
      ripened.mark(player.uuid(), cooldown);
    }
  }

  @Override
  protected Outcome run(BlockUse use) {
    PlayerTeleportEvent teleport = eating.get(use.player().uuid());
    return teleport != null ? aim(use, teleport) : ripen(use);
  }

  private Outcome aim(BlockUse use, PlayerTeleportEvent teleport) {
    PlayerRef player = use.player();
    double range = range(use.level());
    Loc landing = RiftBlinkPath.find(player, player.eyeLocation().direction(), new RiftBlinkPath.Shape(range, 1.5D,
        (double vertical) -> false, RiftBlink.Targeting.DISTANCE, RiftBlink.Landing.SERVER, 3, 0));
    if (landing == null || !canInteract(player, landing) || !checkRegion(player, landing)) {
      return Outcome.MISSED;
    }
    Loc origin = player.location();
    teleport.setTo(landing.withRotation(origin.yaw(), origin.pitch()));
    fx(origin.add(0, 1, 0), FxPriority.TRANSITION).burst(Particles.PORTAL, 16, 0.4D);
    return Outcome.INSTANT;
  }

  private Outcome ripen(BlockUse use) {
    PlayerRef player = use.player();
    Loc center = use.at();
    if (center == null) {
      return Outcome.MISSED;
    }
    int reach = Math.max(1, Math.min(8, getConfig().reachBase + use.level()));
    int height = Math.max(0, Math.min(4, getConfig().reachHeight));
    int left = flowersPerHarvest(use.level());
    int grown = 0;
    BlockRef origin = center.world().blockAt(center);
    for (int dy = -height; dy <= height && left > 0; dy++) {
      for (int dx = -reach; dx <= reach && left > 0; dx++) {
        for (int dz = -reach; dz <= reach && left > 0; dz++) {
          BlockRef flower = origin.relative(dx, dy, dz);
          if (flower.type().equals(CHORUS_FLOWER) && grow(player, flower)) {
            grown++;
            left--;
          }
        }
      }
    }
    return grown > 0 ? Outcome.INSTANT : Outcome.MISSED;
  }

  /** One upward step, as a chorus flower grows on its own: the flower rises a block and leaves a stem. */
  private boolean grow(PlayerRef player, BlockRef flower) {
    BlockStateRef state = flower.state();
    BlockRef above = flower.relative(BlockFace.UP);
    if (state.age() < 0 || state.age() >= LAST_AGE || !above.isAir() || !above.relative(BlockFace.UP).isAir()
        || stemHeight(flower) >= getConfig().maxStemHeight
        || !canBlockPlace(player, flower.location()) || !canBlockPlace(player, above.location())
        || !ProtectionProbes.attemptBlockPlaceProbe(player, flower)
        || !ProtectionProbes.attemptBlockPlaceProbe(player, above)) {
      return false;
    }
    above.setState(state, false);
    BlockRef below = flower.relative(BlockFace.DOWN);
    boolean rooted = below.type().equals(CHORUS_PLANT) || below.type().equals(END_STONE);
    flower.setState(AdaptCore.platform().host().items().blockState(CHORUS_PLANT)
        .with("up", "true").with("down", String.valueOf(rooted)), false);
    XpProvenance.recordBonemeal(above);
    XpProvenance.recordBonemeal(flower);
    fx(above.center(), FxPriority.TRANSITION).burst(Particles.REVERSE_PORTAL, 6, 0.25D);
    return true;
  }

  private static int stemHeight(BlockRef flower) {
    int height = 0;
    BlockRef below = flower.relative(BlockFace.DOWN);
    while (height < 16 && below.type().equals(CHORUS_PLANT)) {
      height++;
      below = below.relative(BlockFace.DOWN);
    }
    return height;
  }

  double range(int level) {
    return getConfig().rangeBase + (level * getConfig().rangePerLevel);
  }

  int flowersPerHarvest(int level) {
    return Math.max(1, getConfig().flowersBase + level);
  }

  @ConfigDescription("Harvests nearby grow your chorus flowers, and chorus fruit sends you where you look.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Aimed chorus range before level scaling, in blocks.", impact = "Higher values send you farther.")
    double rangeBase = 4D;
    @ConfigDoc(value = "Aimed chorus range added per level, in blocks.", impact = "Higher values reward higher levels more.")
    double rangePerLevel = 4D;
    @ConfigDoc(value = "Chorus flowers one harvest grows, before adding the level.", impact = "Higher values grow more flowers at once.")
    int flowersBase = 0;
    @ConfigDoc(value = "Sideways reach from the harvested crop to the flowers, before adding the level, in blocks (at most 8).", impact = "Higher values reach flowers farther from the crops.")
    int reachBase = 2;
    @ConfigDoc(value = "Reach above and below the harvested crop, in blocks (at most 4).", impact = "Higher values reach flowers on other levels of the farm.")
    int reachHeight = 2;
    @ConfigDoc(value = "Tallest straight stem a flower grows on before it stops rising.", impact = "Higher values grow taller plants.")
    int maxStemHeight = 4;
    @ConfigDoc(value = "Cooldown after an aimed landing, in milliseconds.", impact = "Higher values space aimed landings further apart.")
    long aimCooldownMillis = 1_000L;
    @ConfigDoc(value = "Cooldown after harvests grow flowers, in milliseconds.", impact = "Higher values space growth further apart.")
    long ripenCooldownMillis = 1_000L;
  }
}
