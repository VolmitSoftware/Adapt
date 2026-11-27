package art.arcane.adapt.content.golem.gui;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PrePlayerAttackEntityEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;

import art.arcane.adapt.content.golem.GolemAnchor;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemPacking;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.item.GolemItemKind;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.content.golem.overseer.Overseer;

import java.util.Objects;
import java.util.Optional;


public final class CrookInteractions {
  private final CrookGestures<GolemHandle> gestures = new CrookGestures<>();
  private final NodeAreaSelector areas;

  public CrookInteractions(NodeAreaSelector areas) {
    this.areas = Objects.requireNonNull(areas);
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void on(PlayerInteractEntityEvent e) {
    PlayerRef player = e.player();
    if (e.protectionProbe() || !isCrook(player.inventory().mainHand()) || areas.swallows(player)) {
      return;
    }
    Optional<GolemHandle> handle = GolemancyRuntime.handle(e.clicked());
    boolean ownGolem = handle.isPresent() && handle.get().owner().equals(player.uuid());
    boolean sneaking = player.isSneaking();
    CrookGestures.Action action = CrookGestures.onEntity(ownGolem, sneaking, ownGolem && sneaking && GolemPacking.packs(player));
    if (action == CrookGestures.Action.NONE) {
      return;
    }
    e.setCancelled(true);
    gestures.entityClicked(player.uuid(), System.currentTimeMillis());
    if (e.hand() != Hand.MAIN_HAND) {
      return;
    }
    if (action == CrookGestures.Action.PACK) {
      GolemPacking.packer().ifPresent(packer -> packer.pack(player, handle.get()));
    } else {
      GolemGui.open(player, handle.get(), gestures);
    }
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void on(PlayerInteractEvent e) {
    if (e.protectionProbe() || e.hand() != Hand.MAIN_HAND || !isCrook(e.item()) || areas.swallows(e.player())) {
      return;
    }
    PlayerRef player = e.player();
    long now = System.currentTimeMillis();
    switch (e.action()) {
      case RIGHT_CLICK_AIR -> {
        if (CrookGestures.onAir(player.isSneaking(), gestures.isEntityClickEcho(player.uuid(), now)) == CrookGestures.Action.ROSTER_GUI) {
          RosterGui.open(player);
        }
      }
      case RIGHT_CLICK_BLOCK -> linkHomeChest(e, player, now);
      case LEFT_CLICK_AIR -> signal(player, now);
      default -> {
      }
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PrePlayerAttackEntityEvent e) {
    attacked(e.player());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDamageByEntityEvent e) {
    if (e.damager() instanceof PlayerRef player) {
      attacked(player);
    }
  }

  private void attacked(PlayerRef player) {
    if (isCrook(player.inventory().mainHand())) {
      gestures.entityClicked(player.uuid(), System.currentTimeMillis());
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    gestures.forget(e.player().uuid());
  }

  private void signal(PlayerRef player, long now) {
    CrookGestures.Action action = CrookGestures.onLeftAir(player.isSneaking(), gestures.isEntityClickEcho(player.uuid(), now));
    switch (action) {
      case SEND_SIGNAL -> Overseer.commander().ifPresent(commander -> commander.send(player));
      case CYCLE_SIGNAL -> Overseer.commander().ifPresent(commander -> commander.cycle(player));
      case NONE, GOLEM_GUI, PACK, ROSTER_GUI, LINK_CHEST -> {
      }
    }
  }

  private void linkHomeChest(PlayerInteractEvent e, PlayerRef player, long now) {
    BlockRef block = e.clickedBlock();
    Optional<GolemHandle> target = gestures.linkTarget(player.uuid(), now);
    boolean container = target.isPresent() && block != null && block.container() != null;
    if (CrookGestures.onBlock(target.isPresent(), container) != CrookGestures.Action.LINK_CHEST) {
      return;
    }
    e.setUseBlock(UseResult.DENY);
    e.setUseItem(UseResult.DENY);
    Loc chestLocation = block.location();
    if (!canAccessChest(player, chestLocation)) {
      GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.chest_denied"));
      return;
    }
    GolemHandle handle = target.get();
    GolemAnchor chest = GolemAnchor.at(chestLocation);
    boolean scheduled = AdaptApi.scheduler().runEntity(handle.entity(), () -> {
      if (handle.isRetired() || !handle.entity().isValid()) {
        gestures.clearLink(player.uuid());
        GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.gone"));
        return;
      }
      double leashRadius = GolemancyRuntime.brainSettings().leashRadius();
      GolemAnchor home = handle.state().home();
      GolemAnchor center = home == null ? GolemAnchor.at(handle.entity().location()) : home;
      if (!GolemGuiActions.chestInReach(chest, handle.entity().world().uid(), center, leashRadius)) {
        GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.chest_out_of_reach").withMarkup("radius", String.valueOf((int) leashRadius)));
        return;
      }
      handle.setHomeChest(chest);
      gestures.clearLink(player.uuid());
      GolemGuiSupport.notify(player, Text.key("adapt.golemancy.message.chest_linked").withMarkup("x", String.valueOf(chest.x())).withMarkup("y", String.valueOf(chest.y())).withMarkup("z", String.valueOf(chest.z())));
    }, () -> {
      gestures.clearLink(player.uuid());
      GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.gone"));
    });
    if (!scheduled) {
      gestures.clearLink(player.uuid());
      GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.gone"));
    }
  }

  private static boolean canAccessChest(PlayerRef player, Loc location) {
    return GolemancyRuntime.protectionPolicy().map(policy -> policy.canAccessChest(player, location)).orElse(false);
  }

  private static boolean isCrook(ItemRef item) {
    return GolemItems.kindOf(item).filter(kind -> kind == GolemItemKind.CROOK).isPresent();
  }
}
