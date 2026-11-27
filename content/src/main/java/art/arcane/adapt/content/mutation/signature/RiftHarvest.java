package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismGrowables;
import art.arcane.adapt.content.adaptation.herbalism.upgraded.HarvestMoon;
import art.arcane.adapt.content.adaptation.rift.RiftAnchors;
import art.arcane.adapt.content.adaptation.rift.RiftItemMoves;
import art.arcane.adapt.content.adaptation.rift.RiftVoidMagnet;
import art.arcane.adapt.content.adaptation.rift.upgraded.PocketRealm;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Herbalism leading Rift, replacing Convergence: the produce a Harvest Moon sweep yields goes through the void into the
 * containers beside the Rift Anchor you chose, or your ender chest without one, before it ever drops. Seeds stay with
 * you for replanting. Sneak and right-click one of your anchors with a hoe to choose it, again to unchoose it.
 */
public final class RiftHarvest extends Signature<RiftHarvest.Config> implements HarvestMoon.SweepHook {
  private static final String ANCHOR_KEY = "harvest-anchor";
  private static final Id LODESTONE = Id.minecraft("lodestone");
  private final Map<UUID, Sweep> swept = new ConcurrentHashMap<>();
  private final Map<UUID, RiftAnchors.Anchor> choosing = new ConcurrentHashMap<>();

  public RiftHarvest(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("popped_chorus_fruit"));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, maxStacks(level), 1);
  }

  @Override
  public long cooldownMillis(int level) {
    return Math.max(0L, getConfig().cooldownMillis);
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(PlayerInteractEvent e) {
    BlockRef block = e.clickedBlock();
    PlayerRef player = e.player();
    if (e.action() != InteractAction.RIGHT_CLICK_BLOCK || e.hand() != Hand.MAIN_HAND || block == null
        || !player.isSneaking() || !LODESTONE.equals(block.type()) || getActiveLevel(player) <= 0
        || !ItemKinds.isHoe(player.inventory().mainHand())) {
      return;
    }
    PocketRealm realm = realm();
    RiftAnchors.Anchor anchor = realm == null ? null : realm.ownAnchor(player, block);
    if (anchor == null) {
      return;
    }
    e.setCancelled(true);
    choosing.put(player.uuid(), anchor);
    try {
      fire(player, null, block.center(), false);
    } finally {
      choosing.remove(player.uuid());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    swept.remove(e.player().uuid());
    choosing.remove(e.player().uuid());
  }

  @Override
  public boolean takesProduce(PlayerRef p) {
    return getActiveLevel(p) > 0 && usable();
  }

  @Override
  public List<ItemRef> swept(PlayerRef p, BlockRef target, boolean farmed, List<ItemRef> produce) {
    if (produce.isEmpty() || getActiveLevel(p) <= 0) {
      return produce;
    }
    Sweep sweep = new Sweep(produce, new ArrayList<>(produce));
    swept.put(p.uuid(), sweep);
    try {
      fire(p, null, target.center(), false);
    } finally {
      swept.remove(p.uuid());
    }
    return sweep.left();
  }

  @Override
  public Outcome run(BlockUse use) {
    PlayerRef player = use.player();
    RiftAnchors.Anchor chosen = choosing.get(player.uuid());
    if (chosen != null) {
      boolean clear = key(chosen).equals(use.adaptation().getStorageString(player, ANCHOR_KEY, null));
      use.adaptation().setStorage(player, ANCHOR_KEY, clear ? null : key(chosen));
      HudStatus.show(player, TextFormat.AQUA + (clear ? Text.key("adapt.tool.rift.anchor.harvests_ender") : Text.key("adapt.tool.rift.anchor.harvests_here")).resolve());
      return Outcome.INSTANT;
    }
    Sweep sweep = swept.get(player.uuid());
    if (sweep == null) {
      return Outcome.MISSED;
    }
    List<ItemRef> taken = take(sweep, maxStacks(use.level()));
    if (taken.isEmpty()) {
      return Outcome.MISSED;
    }
    int moved = RiftItemMoves.total(taken);
    PocketRealm realm = realm();
    RiftAnchors.Anchor anchor = realm == null ? null : anchor(realm, player, use.adaptation().getStorageString(player, ANCHOR_KEY, null));
    if (anchor != null) {
      realm.deliver(player, anchor, taken, (List<ItemRef> rest) -> toEnderChest(player, rest));
    } else {
      toEnderChest(player, taken);
    }
    RiftVoidMagnet.pullFx(this, player, moved);
    return Outcome.INSTANT;
  }

  /** Stacks up the sweep's produce, at most {@code stacks} stacks of it, out of what it leaves; seeds are left. */
  private static List<ItemRef> take(Sweep sweep, int stacks) {
    List<ItemRef> taken = new ArrayList<>();
    for (ItemRef item : sweep.produce()) {
      if (!HerbalismGrowables.isProduce(item.type())) {
        continue;
      }
      int amount = item.amount();
      for (ItemRef stack : taken) {
        int room = stack.maxStackSize() - stack.amount();
        if (amount > 0 && room > 0 && stack.isSimilar(item)) {
          int moved = Math.min(room, amount);
          stack.setAmount(stack.amount() + moved);
          amount -= moved;
        }
      }
      while (amount > 0 && taken.size() < stacks) {
        ItemRef stack = item.copy();
        stack.setAmount(Math.min(amount, Math.max(1, item.maxStackSize())));
        taken.add(stack);
        amount -= stack.amount();
      }
      sweep.left().remove(item);
      if (amount > 0) {
        ItemRef rest = item.copy();
        rest.setAmount(amount);
        sweep.left().add(rest);
      }
    }
    return taken;
  }

  /** Puts {@code items} in the ender chest; what does not fit goes back to the inventory, then to the ground. */
  private static void toEnderChest(PlayerRef player, List<ItemRef> items) {
    for (ItemRef item : items) {
      for (ItemRef rest : player.enderChest().addItem(item)) {
        for (ItemRef spill : player.inventory().addItem(rest)) {
          player.world().dropItem(player.location().position(), spill);
        }
      }
    }
  }

  private static String key(RiftAnchors.Anchor anchor) {
    return anchor.worldId() + ":" + anchor.x() + ":" + anchor.y() + ":" + anchor.z();
  }

  private static @Nullable RiftAnchors.Anchor anchor(PocketRealm realm, PlayerRef player, @Nullable String key) {
    if (key == null || key.isEmpty()) {
      return null;
    }
    for (RiftAnchors.Anchor anchor : realm.anchors(player)) {
      if (!anchor.blockless() && key.equals(key(anchor))) {
        return anchor;
      }
    }
    return null;
  }

  private @Nullable PocketRealm realm() {
    return PairParts.owned(Id.adapt("rift/upgrade/pocket_realm"), PocketRealm.class);
  }

  int maxStacks(int level) {
    return (int) Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().stacksAtFirstLevel,
        getConfig().stacksAtTopLevel));
  }

  private record Sweep(List<ItemRef> produce, List<ItemRef> left) {
  }

  @ConfigDescription("Harvest Moon sweeps send their produce to your chosen Rift Anchor or your ender chest.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Produce stacks one sweep sends at level 1.", impact = "Higher values send more of each sweep.")
    int stacksAtFirstLevel = 4;
    @ConfigDoc(value = "Produce stacks one sweep sends at the top level.", impact = "Higher values send more of each sweep.")
    int stacksAtTopLevel = 16;
    @ConfigDoc(value = "Cooldown after a sweep is sent, in milliseconds.", impact = "Higher values send fewer sweeps.")
    long cooldownMillis = 0L;
  }
}
