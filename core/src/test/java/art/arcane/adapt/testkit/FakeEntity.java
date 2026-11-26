package art.arcane.adapt.testkit;

import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Plain entity with mutable state, living in a {@link FakeWorld}. */
public class FakeEntity implements EntityRef {
  private static final AtomicInteger IDS = new AtomicInteger(1);

  private final UUID uuid = UUID.randomUUID();
  private final int entityId = IDS.getAndIncrement();
  private final Id type;
  private final Set<EntityTrait> traits = EnumSet.noneOf(EntityTrait.class);
  private final MemoryData data = new MemoryData();
  private final List<EntityRef> passengers = new ArrayList<>();
  private FakeWorld world;
  private Loc location;
  private Vec3 velocity = Vec3.ZERO;
  private boolean valid = true;
  private boolean dead;
  private boolean onGround = true;
  private boolean inWater;
  private float fallDistance;
  private int fireTicks;
  private int freezeTicks;
  private int ticksLived = 100;
  private boolean invulnerable;
  private boolean silent;
  private boolean gravity = true;
  private boolean glowing;
  private boolean persistent = true;
  private String customName;
  private String customNameJson;
  private EntityRef responsibleEntity;
  private UUID tameOwner;

  public FakeEntity(Id type, Loc location) {
    this.type = type;
    this.world = (FakeWorld) location.world();
    this.location = location;
  }

  public FakeEntity trait(EntityTrait trait) {
    traits.add(trait);
    return this;
  }

  public void moveTo(Loc destination) {
    world = (FakeWorld) destination.world();
    location = destination;
  }

  public void setOnGround(boolean onGround) {
    this.onGround = onGround;
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
    return traits.contains(trait);
  }

  @Override
  public boolean isValid() {
    return valid && !dead;
  }

  @Override
  public boolean isDead() {
    return dead;
  }

  public void setDead(boolean dead) {
    this.dead = dead;
  }

  @Override
  public WorldRef world() {
    return world;
  }

  @Override
  public Loc location() {
    return location;
  }

  @Override
  public Vec3 velocity() {
    return velocity;
  }

  @Override
  public void setVelocity(Vec3 velocity) {
    this.velocity = velocity;
  }

  @Override
  public Box boundingBox() {
    return Box.around(location.position().add(0D, height() / 2D, 0D), width() / 2D, height() / 2D, width() / 2D);
  }

  @Override
  public double height() {
    return 1.8D;
  }

  @Override
  public double width() {
    return 0.6D;
  }

  @Override
  public boolean isOnGround() {
    return onGround;
  }

  @Override
  public boolean isInWater() {
    return inWater;
  }

  public void setInWater(boolean inWater) {
    this.inWater = inWater;
  }

  @Override
  public boolean isInLava() {
    return false;
  }

  @Override
  public float fallDistance() {
    return fallDistance;
  }

  @Override
  public void setFallDistance(float distance) {
    fallDistance = distance;
  }

  @Override
  public int fireTicks() {
    return fireTicks;
  }

  @Override
  public void setFireTicks(int ticks) {
    fireTicks = ticks;
  }

  @Override
  public int freezeTicks() {
    return freezeTicks;
  }

  @Override
  public void setFreezeTicks(int ticks) {
    freezeTicks = ticks;
  }

  @Override
  public int maxFreezeTicks() {
    return 140;
  }

  @Override
  public boolean isFreezeTickingLocked() {
    return false;
  }

  @Override
  public int ticksLived() {
    return ticksLived;
  }

  public void setTicksLived(int ticks) {
    ticksLived = ticks;
  }

  @Override
  public boolean isInvulnerable() {
    return invulnerable;
  }

  @Override
  public void setInvulnerable(boolean invulnerable) {
    this.invulnerable = invulnerable;
  }

  @Override
  public boolean isSilent() {
    return silent;
  }

  @Override
  public void setSilent(boolean silent) {
    this.silent = silent;
  }

  @Override
  public boolean hasGravity() {
    return gravity;
  }

  @Override
  public void setGravity(boolean gravity) {
    this.gravity = gravity;
  }

  @Override
  public boolean isGlowing() {
    return glowing;
  }

  @Override
  public void setGlowing(boolean glowing) {
    this.glowing = glowing;
  }

  @Override
  public boolean isPersistent() {
    return persistent;
  }

  @Override
  public void setPersistent(boolean persistent) {
    this.persistent = persistent;
  }

  @Override
  public String customName() {
    return customName;
  }

  @Override
  public void setCustomName(String markup, boolean visible) {
    customName = markup;
  }

  @Override
  public String customNameJson() {
    return customNameJson;
  }

  @Override
  public void setCustomNameJson(String json) {
    customNameJson = json == null || json.isBlank() ? null : json;
  }

  @Override
  public CompletableFuture<Boolean> teleport(Loc destination, TeleportCause cause, boolean retainPassengers) {
    moveTo(destination);
    return CompletableFuture.completedFuture(true);
  }

  @Override
  public EntityRef vehicle() {
    return null;
  }

  @Override
  public List<EntityRef> passengers() {
    return List.copyOf(passengers);
  }

  @Override
  public boolean addPassenger(EntityRef passenger) {
    return passengers.add(passenger);
  }

  @Override
  public boolean eject() {
    boolean had = !passengers.isEmpty();
    passengers.clear();
    return had;
  }

  @Override
  public List<EntityRef> nearbyEntities(double radiusX, double radiusY, double radiusZ) {
    return world.entitiesIn(Box.around(location.position(), radiusX, radiusY, radiusZ),
        (EntityRef entity) -> entity != this);
  }

  @Override
  public CompletableFuture<Boolean> teleport(Loc destination, TeleportCause cause) {
    moveTo(destination);
    return CompletableFuture.completedFuture(true);
  }

  @Override
  public void remove() {
    valid = false;
    world.forget(this);
  }

  @Override
  public DataHolder data() {
    return data;
  }

  @Override
  public UUID tameOwner() {
    return tameOwner;
  }

  public void setTameOwner(UUID owner) {
    tameOwner = owner;
  }

  @Override
  public EntityRef responsibleEntity() {
    return responsibleEntity;
  }

  public void setResponsibleEntity(EntityRef entity) {
    responsibleEntity = entity;
  }

  @Override
  public Object unwrap() {
    return this;
  }
}
