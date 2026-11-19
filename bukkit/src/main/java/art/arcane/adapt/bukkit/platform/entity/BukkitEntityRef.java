package art.arcane.adapt.bukkit.platform.entity;

import org.bukkit.entity.Ambient;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.Painting;
import org.bukkit.entity.ItemFrame;
import art.arcane.adapt.bukkit.platform.BukkitConversions;
import java.util.function.Function;
import org.bukkit.entity.Player;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.bukkit.platform.BukkitPlatform;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.platform.BukkitResolve;
import art.arcane.adapt.bukkit.platform.BukkitText;
import art.arcane.adapt.bukkit.platform.data.PdcDataHolder;
import art.arcane.adapt.bukkit.platform.data.PdcStore;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.compat.PaperCompat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Animals;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Display;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Fish;
import org.bukkit.entity.Golem;
import org.bukkit.entity.Monster;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Handle over a Bukkit entity. Equality is by UUID. A non-player handle follows the Bukkit entity
 * object: once Bukkit invalidates it (removal, unload, dimension change) {@link #isValid()} is false.
 */
public class BukkitEntityRef implements EntityRef {
  private static final String TELEPORT_FLAG_CLASS = "io.papermc.paper.entity.TeleportFlag";
  private static final Id LEGACY_POTION = Id.minecraft("potion");
  private static final Id SPLASH_POTION = Id.minecraft("splash_potion");
  private static final Id LINGERING_POTION = Id.minecraft("lingering_potion");
  private static volatile boolean teleportFlagsSupported = true;
  private final UUID uuid;
  private final int entityId;
  private final Id type;
  private volatile Entity entity;
  private DataHolder data;

  public BukkitEntityRef(Entity entity) {
    this.entity = Objects.requireNonNull(entity, "entity");
    this.uuid = entity.getUniqueId();
    this.entityId = entity.getEntityId();
    this.type = typeId(entity);
  }

  /**
   * Core works with 26.x entity ids. Before 1.21.5 splash and lingering potions were one entity type, minecraft:potion,
   * told apart by the thrown item.
   */
  private static Id typeId(Entity entity) {
    Id type = BukkitResolve.id(entity.getType());
    if (entity instanceof ThrownPotion potion && LEGACY_POTION.equals(type)) {
      return potion.getItem().getType() == Material.LINGERING_POTION ? LINGERING_POTION : SPLASH_POTION;
    }
    return type;
  }

  /** Current native entity. Players rebind on respawn; other entities never change. */
  public Entity handle() {
    return entity;
  }

  /** Points the handle at a replacement native object with the same UUID. */
  protected void rebind(Entity replacement) {
    if (replacement != null && replacement.getUniqueId().equals(uuid)) {
      entity = replacement;
    }
  }

  @Override
  public UUID uuid() {
    return uuid;
  }

  @Override
  public int entityId() {
    return entityId;
  }

  @Override
  public Id type() {
    return type;
  }

  @Override
  public boolean has(EntityTrait trait) {
    Entity entity = handle();
    return switch (trait) {
      case MONSTER -> entity instanceof Monster;
      case ENEMY -> entity instanceof Enemy;
      case ANIMAL -> entity instanceof Animals;
      case TAMEABLE -> entity instanceof Tameable;
      case HORSE -> entity instanceof AbstractHorse;
      case WATER_MOB -> entity instanceof WaterMob;
      case GOLEM -> entity instanceof Golem;
      case VILLAGER -> entity instanceof AbstractVillager;
      case BOSS -> entity instanceof Boss;
      case UNDEAD -> BukkitEntityVersion.undead(entity);
      case ARMOR_STAND -> entity instanceof ArmorStand;
      case DISPLAY -> entity instanceof Display;
      case VEHICLE -> entity instanceof Vehicle;
      case EXPERIENCE_ORB -> entity instanceof ExperienceOrb;
      case FIREBALL -> entity instanceof Fireball;
      case ARROW -> entity instanceof AbstractArrow;
      case FISH -> entity instanceof Fish;
      case ITEM_FRAME -> entity instanceof ItemFrame;
      case PAINTING -> entity instanceof Painting;
      case AMBIENT -> entity instanceof Ambient;
      case SLIME -> entity instanceof Slime;
      case ZOMBIE -> entity instanceof Zombie;
      case SKELETON -> entity instanceof AbstractSkeleton;
      case SPIDER -> entity instanceof Spider;
    };
  }

  @Override
  public boolean isValid() {
    return handle().isValid();
  }

  @Override
  public boolean isDead() {
    return handle().isDead();
  }

  @Override
  public WorldRef world() {
    return BukkitRefs.world(handle().getWorld());
  }

  @Override
  public Loc location() {
    return BukkitRefs.loc(handle().getLocation());
  }

  @Override
  public Vec3 velocity() {
    return BukkitConversions.vec(handle().getVelocity());
  }

  @Override
  public void setVelocity(Vec3 velocity) {
    handle().setVelocity(BukkitConversions.vector(velocity));
  }

  @Override
  public Box boundingBox() {
    return BukkitConversions.box(handle().getBoundingBox());
  }

  @Override
  public double height() {
    return handle().getHeight();
  }

  @Override
  public double width() {
    return handle().getWidth();
  }

  @Override
  public boolean isOnGround() {
    return handle().isOnGround();
  }

  @Override
  public boolean isInWater() {
    return handle().isInWater();
  }

  @Override
  public boolean isInLava() {
    return handle().isInLava();
  }

  @Override
  public float fallDistance() {
    return handle().getFallDistance();
  }

  @Override
  public void setFallDistance(float distance) {
    handle().setFallDistance(distance);
  }

  @Override
  public int fireTicks() {
    return handle().getFireTicks();
  }

  @Override
  public void setFireTicks(int ticks) {
    handle().setFireTicks(ticks);
  }

  @Override
  public int freezeTicks() {
    return handle().getFreezeTicks();
  }

  @Override
  public void setFreezeTicks(int ticks) {
    handle().setFreezeTicks(ticks);
  }

  @Override
  public int maxFreezeTicks() {
    return handle().getMaxFreezeTicks();
  }

  @Override
  public boolean isFreezeTickingLocked() {
    try {
      return handle().isFreezeTickingLocked();
    } catch (NoSuchMethodError absent) {
      return false;
    }
  }

  @Override
  public int ticksLived() {
    return handle().getTicksLived();
  }

  @Override
  public boolean isInvulnerable() {
    return handle().isInvulnerable();
  }

  @Override
  public void setInvulnerable(boolean invulnerable) {
    handle().setInvulnerable(invulnerable);
  }

  @Override
  public boolean isSilent() {
    return handle().isSilent();
  }

  @Override
  public void setSilent(boolean silent) {
    handle().setSilent(silent);
  }

  @Override
  public boolean hasGravity() {
    return handle().hasGravity();
  }

  @Override
  public void setGravity(boolean gravity) {
    handle().setGravity(gravity);
  }

  @Override
  public boolean isGlowing() {
    return handle().isGlowing();
  }

  @Override
  public void setGlowing(boolean glowing) {
    handle().setGlowing(glowing);
  }

  @Override
  public boolean isPersistent() {
    return handle().isPersistent();
  }

  @Override
  public void setPersistent(boolean persistent) {
    handle().setPersistent(persistent);
  }

  @Override
  public String customName() {
    return BukkitText.legacy(PaperCompat.customName(handle()));
  }

  @Override
  public void setCustomName(String markup, boolean visible) {
    Entity entity = handle();
    String legacy = BukkitText.legacy(markup);
    PaperCompat.customName(entity, legacy == null ? null : BukkitText.legacyComponent(legacy));
    entity.setCustomNameVisible(visible && markup != null);
  }

  @Override
  public EntityRef vehicle() {
    Entity vehicle = handle().getVehicle();
    return vehicle == null ? null : BukkitRefs.entity(vehicle);
  }

  @Override
  public List<EntityRef> passengers() {
    return wrap(handle().getPassengers());
  }

  @Override
  public boolean addPassenger(EntityRef passenger) {
    return handle().addPassenger(BukkitRefs.entity(passenger));
  }

  @Override
  public boolean eject() {
    return handle().eject();
  }

  @Override
  public List<EntityRef> nearbyEntities(double radiusX, double radiusY, double radiusZ) {
    return wrap(handle().getNearbyEntities(radiusX, radiusY, radiusZ));
  }

  @Override
  public CompletableFuture<Boolean> teleport(Loc destination, TeleportCause cause) {
    return announced(destination, cause, (Loc arrival) -> {
      Location target = BukkitRefs.location(arrival);
      if (target == null) {
        return CompletableFuture.completedFuture(false);
      }
      return PaperCompat.teleportAsync(handle(), target, BukkitConversions.cause(cause));
    });
  }

  @Override
  public CompletableFuture<Boolean> teleport(Loc destination, TeleportCause cause, boolean retainPassengers) {
    if (!retainPassengers) {
      return teleport(destination, cause);
    }
    return announced(destination, cause, (Loc arrival) -> teleportRetainingPassengers(arrival, cause));
  }

  private CompletableFuture<Boolean> announced(Loc destination, TeleportCause cause, Function<Loc, CompletableFuture<Boolean>> move) {
    AdaptScheduler scheduler = BukkitPlatform.scheduler();
    AdaptEventBus bus = BukkitPlatform.bus();
    if (!(handle() instanceof Player player) || scheduler == null || !scheduler.isRegionized() || bus == null) {
      return move.apply(destination);
    }
    return RegionTeleports.announce(scheduler, bus, BukkitRefs.player(player), () -> BukkitRefs.loc(player.getLocation()),
        destination, cause, move);
  }

  private CompletableFuture<Boolean> teleportRetainingPassengers(Loc destination, TeleportCause cause) {
    Location target = BukkitRefs.location(destination);
    if (target == null) {
      return CompletableFuture.completedFuture(false);
    }
    Entity entity = handle();
    PlayerTeleportEvent.TeleportCause bukkitCause = BukkitConversions.cause(cause);
    if (teleportFlagsSupported && PaperCompat.hasClass(TELEPORT_FLAG_CLASS)) {
      try {
        return BukkitTeleportVersion.teleportRetainingPassengers(entity, target, bukkitCause);
      } catch (NoSuchMethodError | NoClassDefFoundError absent) {
        teleportFlagsSupported = false;
      }
    }
    List<Entity> riders = List.copyOf(entity.getPassengers());
    for (Entity rider : riders) {
      entity.removePassenger(rider);
    }
    boolean moved = entity.teleport(target, bukkitCause);
    for (Entity rider : riders) {
      if (moved) {
        rider.teleport(target, bukkitCause);
      }
      entity.addPassenger(rider);
    }
    return CompletableFuture.completedFuture(moved);
  }

  @Override
  public String customNameJson() {
    Component name = PaperCompat.customName(handle());
    return name == null ? null : GsonComponentSerializer.gson().serialize(name);
  }

  @Override
  public void setCustomNameJson(String json) {
    Component name = null;
    if (json != null && !json.isBlank()) {
      try {
        name = GsonComponentSerializer.gson().deserialize(json);
      } catch (RuntimeException malformed) {
        name = null;
      }
    }
    PaperCompat.customName(handle(), name);
  }

  @Override
  public void remove() {
    handle().remove();
  }

  @Override
  public DataHolder data() {
    DataHolder current = data;
    if (current == null) {
      current = new PdcDataHolder(new EntityStore(this));
      data = current;
    }
    return current;
  }

  @Override
  public UUID tameOwner() {
    return handle() instanceof Tameable tameable ? PaperCompat.tamedOwnerId(tameable) : null;
  }

  @Override
  public EntityRef responsibleEntity() {
    Entity handle = handle();
    Object source = null;
    if (handle instanceof TNTPrimed tnt) {
      source = tnt.getSource();
    } else if (handle instanceof AreaEffectCloud cloud) {
      source = cloud.getSource();
    } else if (handle instanceof EvokerFangs fangs) {
      source = fangs.getOwner();
    }
    return source instanceof Entity entity ? BukkitRefs.entity(entity) : null;
  }

  @Override
  public Object unwrap() {
    return handle();
  }

  @Override
  public final boolean equals(Object other) {
    return other instanceof EntityRef that && uuid.equals(that.uuid());
  }

  @Override
  public final int hashCode() {
    return uuid.hashCode();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "[" + type + " " + uuid + "]";
  }

  static List<EntityRef> wrap(List<? extends Entity> entities) {
    if (entities.isEmpty()) {
      return List.of();
    }
    List<EntityRef> wrapped = new ArrayList<>(entities.size());
    for (Entity entity : entities) {
      wrapped.add(BukkitRefs.entity(entity));
    }
    return wrapped;
  }

  private record EntityStore(BukkitEntityRef ref) implements PdcStore {
    @Override
    public PersistentDataContainer read() {
      return ref.handle().getPersistentDataContainer();
    }

    @Override
    public void write(Consumer<PersistentDataContainer> edit) {
      edit.accept(ref.handle().getPersistentDataContainer());
    }
  }
}
