package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.ability.Minions;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockFormEvent;
import art.arcane.adapt.platform.event.entity.EntityChangeBlockEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

final class GolemGuardListener {
  private static final String COPPER_STATUE_SUFFIX = "copper_golem_statue";

  private final Function<UUID, PlayerRef> players;
  private final Function<EntityRef, Optional<GolemHandle>> handles;

  GolemGuardListener(Function<UUID, PlayerRef> players, Function<EntityRef, Optional<GolemHandle>> handles) {
    this.players = Objects.requireNonNull(players);
    this.handles = Objects.requireNonNull(handles);
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(EntityDamageByEntityEvent e) {
    EntityRef damager = sourceOf(e.damager());
    EntityRef victim = e.entity();
    if (damager == null || !GolemState.has(damager) && !GolemState.has(victim)) {
      return;
    }
    if (cancels(damager, victim)) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityTargetEvent e) {
    EntityRef golem = e.entity();
    LivingRef target = e.target();
    if (target == null || !GolemState.has(golem)) {
      return;
    }
    if (combatOff(golem) || cancels(golem, target)) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(ProjectileLaunchEvent e) {
    EntityRef shooter = e.projectile().shooter();
    if (shooter instanceof MobRef && GolemState.has(shooter) && combatOff(shooter)) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityChangeBlockEvent e) {
    if (isCopperStatue(e.to()) && GolemBody.of(e.entity()).orElse(null) == GolemBody.COPPER && GolemState.has(e.entity())) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEntityEvent e) {
    EntityRef clicked = e.clicked();
    if (!(clicked instanceof LivingRef golem) || GolemBody.of(golem).orElse(null) != GolemBody.COPPER || !GolemState.has(golem)
        || !empty(e.player().inventory().hand(e.hand()))) {
      return;
    }
    if (!empty(golem.equipment(EquipmentSlot.MAIN_HAND))) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(BlockFormEvent e) {
    EntityRef golem = e.entity();
    if (golem == null || GolemBody.of(golem).orElse(null) != GolemBody.FROST || !GolemState.has(golem)) {
      return;
    }
    if (!handles.apply(golem).map(handle -> handle.state().snowTrail()).orElse(false)) {
      e.setCancelled(true);
    }
  }

  static boolean isCopperStatue(BlockStateRef state) {
    return state != null && isCopperStatue(state.type());
  }

  static boolean isCopperStatue(Id material) {
    return material != null && material.path().endsWith(COPPER_STATUE_SUFFIX);
  }

  private static boolean empty(ItemRef item) {
    return item == null || item.isEmpty() || item.type().isAir();
  }

  private boolean combatOff(EntityRef golem) {
    return handles.apply(golem).map(handle -> !GolemCombat.enabled(handle)).orElse(false);
  }

  private boolean cancels(EntityRef damager, EntityRef victim) {
    GolemFriendlyFire.Party attacker = party(damager);
    return GolemFriendlyFire.cancels(attacker, party(victim), () -> golemMayHitPlayer(attacker.owner(), victim));
  }

  private boolean golemMayHitPlayer(UUID owner, EntityRef victim) {
    PlayerRef online = owner == null ? null : players.apply(owner);
    return online != null && victim instanceof LivingRef living && GolemProtection.canDamage(online, living);
  }

  private static GolemFriendlyFire.Party party(EntityRef entity) {
    return new GolemFriendlyFire.Party(entity.uuid(), entity instanceof PlayerRef, GolemState.has(entity), Minions.ownerOf(entity).orElse(null));
  }

  private static EntityRef sourceOf(EntityRef damager) {
    return damager instanceof ProjectileRef projectile && projectile.shooter() != null ? projectile.shooter() : damager;
  }
}
