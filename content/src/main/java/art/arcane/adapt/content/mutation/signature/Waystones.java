package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.discovery.upgraded.Omniscience;
import art.arcane.adapt.content.adaptation.rift.RiftAnchors;
import art.arcane.adapt.content.adaptation.rift.upgraded.PocketRealm;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;


/**
 * Discovery and Rift, either leading, replacing Convergence: a structure in your Atlas can be made a waystone, an
 * anchor without a block that counts against your Rift Anchor cap and is travelled to like one, pearl and channel
 * included.
 */
public final class Waystones extends Signature<Waystones.Config> implements Omniscience.StructureAction {
  private final Map<UUID, Omniscience.Structure> making = new ConcurrentHashMap<>();
  private final Predicate<PlayerRef> keeper = (PlayerRef player) -> usable() && getActiveLevel(player) > 0;

  public Waystones(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("lodestone"));
    Omniscience.addStructureAction(this);
    PocketRealm.addWaystoneKeeper(keeper);
  }

  @Override
  public void unregister() {
    Omniscience.removeStructureAction(this);
    PocketRealm.removeWaystoneKeeper(keeper);
    super.unregister();
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, allowed(level), 1);
  }

  @Override
  public long cooldownMillis(int level) {
    return 0L;
  }

  @Override
  public @Nullable Text label(PlayerRef player, Omniscience.Structure structure) {
    PocketRealm realm = realm();
    if (realm == null || getActiveLevel(player) <= 0 || realm.anchorCap(player) <= 0) {
      return null;
    }
    return realm.waystone(player, structure.id()) == null
        ? Text.key("adapt.tool.discovery.explorers_atlas.make_waystone") : Text.key("adapt.tool.discovery.explorers_atlas.remove_waystone");
  }

  @Override
  public void run(PlayerRef player, Omniscience.Structure structure) {
    PocketRealm realm = realm();
    if (realm == null) {
      return;
    }
    RiftAnchors.Anchor existing = realm.waystone(player, structure.id());
    if (existing != null) {
      realm.removeWaystone(player, existing);
      return;
    }
    making.put(player.uuid(), structure);
    try {
      fire(player, null, null, false);
    } finally {
      making.remove(player.uuid());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    making.remove(e.player().uuid());
  }

  @Override
  public Outcome run(BlockUse use) {
    PlayerRef player = use.player();
    Omniscience.Structure structure = making.get(player.uuid());
    PocketRealm realm = realm();
    WorldRef world = structure == null ? null : AdaptApi.platform().host().world(structure.worldId());
    if (structure == null || realm == null || world == null) {
      return Outcome.MISSED;
    }
    int max = allowed(use.level());
    int set = 0;
    for (RiftAnchors.Anchor anchor : realm.anchors(player)) {
      if (anchor.blockless()) {
        set++;
      }
    }
    if (set >= max) {
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.tool.rift.anchor.waystones_full").withMarkup("max", String.valueOf(max)).resolve());
      return Outcome.MISSED;
    }
    Loc spot = new Loc(world, structure.x(), structure.y(), structure.z());
    return realm.addWaystone(player, spot, structure.id(), structure.name()) == null ? Outcome.MISSED : Outcome.INSTANT;
  }

  private @Nullable PocketRealm realm() {
    return PairParts.owned(Id.adapt("rift/upgrade/pocket_realm"), PocketRealm.class);
  }

  int allowed(int level) {
    return Math.max(1, getConfig().waystonesBase + level);
  }

  @ConfigDescription("Structures in your Atlas can be made waystones you travel to like Rift Anchors.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Waystones a player may set, before adding the level; they also count against the Rift Anchor cap.", impact = "Higher values allow more waystones.")
    int waystonesBase = 0;
  }
}
