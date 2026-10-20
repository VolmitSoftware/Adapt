package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.minion.MinionIdentity;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

final class GolemGuardListener implements Listener {
  private static final String COPPER_STATUE_SUFFIX = "COPPER_GOLEM_STATUE";

  private final Function<UUID, Player> players;
  private final Function<Entity, Optional<GolemHandle>> handles;

  GolemGuardListener(Function<UUID, Player> players, Function<Entity, Optional<GolemHandle>> handles) {
    this.players = Objects.requireNonNull(players);
    this.handles = Objects.requireNonNull(handles);
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    Entity damager = sourceOf(e.getDamager());
    Entity victim = e.getEntity();
    if (!GolemState.has(damager) && !GolemState.has(victim)) {
      return;
    }
    if (cancels(damager, victim)) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityTargetLivingEntityEvent e) {
    Entity golem = e.getEntity();
    LivingEntity target = e.getTarget();
    if (target == null || !GolemState.has(golem)) {
      return;
    }
    if (cancels(golem, target)) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(EntityChangeBlockEvent e) {
    if (isCopperStatue(e.getTo()) && GolemBody.of(e.getEntity()).orElse(null) == GolemBody.COPPER && GolemState.has(e.getEntity())) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void on(PlayerInteractEntityEvent e) {
    if (!(e.getRightClicked() instanceof CopperGolem golem) || !GolemState.has(golem) || !empty(e.getPlayer().getInventory().getItem(e.getHand()))) {
      return;
    }
    EntityEquipment equipment = golem.getEquipment();
    if (equipment != null && !empty(equipment.getItemInMainHand())) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(EntityBlockFormEvent e) {
    Entity golem = e.getEntity();
    if (!(golem instanceof Snowman) || !GolemState.has(golem)) {
      return;
    }
    if (!handles.apply(golem).map(handle -> handle.state().snowTrail()).orElse(false)) {
      e.setCancelled(true);
    }
  }

  static boolean isCopperStatue(Material material) {
    return material != null && material.name().endsWith(COPPER_STATUE_SUFFIX);
  }

  private static boolean empty(ItemStack item) {
    return item == null || item.getType() == Material.AIR;
  }

  private boolean cancels(Entity damager, Entity victim) {
    GolemFriendlyFire.Party attacker = party(damager);
    return GolemFriendlyFire.cancels(attacker, party(victim), () -> golemMayHitPlayer(attacker.owner(), victim));
  }

  private boolean golemMayHitPlayer(UUID owner, Entity victim) {
    Player online = owner == null ? null : players.apply(owner);
    return online != null && victim instanceof LivingEntity living && GolemProtection.canDamage(online, living);
  }

  private static GolemFriendlyFire.Party party(Entity entity) {
    return new GolemFriendlyFire.Party(entity.getUniqueId(), entity instanceof Player, GolemState.has(entity), MinionIdentity.ownerOf(entity));
  }

  private static Entity sourceOf(Entity damager) {
    return damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter ? shooter : damager;
  }
}
