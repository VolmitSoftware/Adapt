package art.arcane.adapt.api.golem;

import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class GolemHandle {
  private final Mob entity;
  private final UUID entityId;
  private final GolemState state;
  private final GolemPlatform platform;
  private final GolemWritePolicy writePolicy = new GolemWritePolicy();
  private volatile DormancyReason dormancyReason = DormancyReason.OWNER_UNKNOWN;
  private volatile boolean awake;
  private volatile boolean retired;

  GolemHandle(Mob entity, GolemState state, GolemPlatform platform) {
    this.entity = Objects.requireNonNull(entity);
    this.entityId = entity.getUniqueId();
    this.state = Objects.requireNonNull(state);
    this.platform = Objects.requireNonNull(platform);
  }

  public UUID id() {
    return state.id();
  }

  public UUID entityId() {
    return entityId;
  }

  public UUID owner() {
    return state.owner();
  }

  public GolemBody body() {
    return state.body();
  }

  public GolemCoreType core() {
    return state.primaryCore();
  }

  public Mob entity() {
    return entity;
  }

  public GolemState state() {
    return state;
  }

  public boolean isDormant() {
    return dormancyReason != DormancyReason.NONE;
  }

  public DormancyReason dormancyReason() {
    return dormancyReason;
  }

  public boolean isRetired() {
    return retired;
  }

  public void setCarried(List<ItemStack> items) {
    List<String> encoded = new ArrayList<>(items.size());
    for (ItemStack item : items) {
      if (item != null && !item.getType().isAir()) {
        encoded.add(GolemState.encodeItem(item));
      }
    }
    state.setCarried(encoded);
    flush();
  }

  public void setCores(List<GolemCoreType> cores) {
    state.setCores(cores);
    flush();
  }

  public void setRunes(List<GolemRune> runes) {
    state.setRunes(runes);
    flush();
  }

  public void setMode(GolemMode mode) {
    state.setMode(Objects.requireNonNull(mode));
    flush();
  }

  public void setChannels(int mask) {
    state.setChannels(mask);
    flush();
  }

  public void setHome(GolemState.Anchor home) {
    state.setHome(Objects.requireNonNull(home));
    flush();
  }

  public void setHomeChest(GolemState.Anchor chest) {
    state.setHomeChest(chest);
    flush();
  }

  public void setEssence(double essence) {
    state.setEssence(essence);
    flush();
  }

  public void setMasterwork(boolean masterwork) {
    state.setMasterwork(masterwork);
    flush();
  }

  public void setColossus(boolean colossus) {
    state.setColossus(colossus);
    flush();
  }

  public void addKills(long kills) {
    state.stats().addKills(kills);
    markDebounced();
  }

  public void addDamage(double damage) {
    state.stats().addDamage(damage);
    markDebounced();
  }

  public void addTasks(long tasks) {
    state.stats().addTasks(tasks);
    markDebounced();
  }

  public void addItemsMoved(long items) {
    state.stats().addItemsMoved(items);
    markDebounced();
  }

  public double hostileDamagePool() {
    return state.hostileDamagePool();
  }

  public void setHostileDamagePool(double pool) {
    state.setHostileDamagePool(pool);
    markDebounced();
  }

  public void flush() {
    if (retired) {
      return;
    }
    state.write(entity);
    writePolicy.flushed();
  }

  boolean isDirty() {
    return writePolicy.isDirty();
  }

  void flushIfDirty() {
    if (writePolicy.isDirty()) {
      flush();
    }
  }

  boolean isAwake() {
    return awake;
  }

  void setAwake(boolean awake) {
    this.awake = awake;
  }

  void setDormancyReason(DormancyReason reason) {
    dormancyReason = Objects.requireNonNull(reason);
  }

  void retire() {
    retired = true;
  }

  private void markDebounced() {
    if (writePolicy.markDirty(platform.nowMillis())) {
      scheduleDebouncedFlush(GolemWritePolicy.DEBOUNCE_MILLIS);
    }
  }

  private void scheduleDebouncedFlush(long delayMillis) {
    platform.runEntity(entity, this::flushIfDue, GolemWritePolicy.ticks(delayMillis));
  }

  private void flushIfDue() {
    if (retired || !writePolicy.isDirty()) {
      return;
    }
    long now = platform.nowMillis();
    if (writePolicy.isDue(now)) {
      flush();
      return;
    }
    scheduleDebouncedFlush(writePolicy.remainingMillis(now));
  }
}
