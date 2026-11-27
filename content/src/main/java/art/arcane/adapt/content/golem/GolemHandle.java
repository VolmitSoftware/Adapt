package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class GolemHandle {
  private final MobRef entity;
  private final UUID entityId;
  private final GolemState state;
  private final GolemPlatform platform;
  private final GolemWritePolicy writePolicy = new GolemWritePolicy();
  private volatile boolean dormant = true;
  private volatile ChunkPosition position;
  private volatile boolean validated;
  private volatile boolean retired;
  private volatile GolemStatPlan appliedStats;
  private volatile int patinaStage;
  private volatile boolean fullHealthOwed;
  private volatile GolemToolNote toolNote;
  private volatile GolemWorkStatus workStatus = GolemWorkStatus.WAITING;

  GolemHandle(MobRef entity, GolemState state, GolemPlatform platform) {
    this.entity = Objects.requireNonNull(entity);
    this.entityId = entity.uuid();
    this.state = Objects.requireNonNull(state);
    this.platform = Objects.requireNonNull(platform);
    trackPosition(entity.location());
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

  public MobRef entity() {
    return entity;
  }

  public GolemState state() {
    return state;
  }

  public boolean isDormant() {
    return dormant;
  }

  public boolean isRetired() {
    return retired;
  }

  public boolean isValidated() {
    return validated;
  }

  public int patinaStage() {
    return patinaStage;
  }

  public GolemWorkStatus workStatus() {
    return workStatus;
  }

  void setWorkStatus(GolemWorkStatus status) {
    workStatus = Objects.requireNonNull(status);
  }

  public GolemToolNote toolNote() {
    return toolNote;
  }

  public boolean removeCarried(String encoded) {
    if (retired || !entity.isValid()) {
      return false;
    }
    List<String> remaining = new ArrayList<>(state.carried());
    if (!remaining.remove(encoded)) {
      return false;
    }
    state.setCarried(remaining);
    flush();
    return true;
  }

  public boolean setHeldEncoded(String encoded) {
    if (retired || !entity.isValid()) {
      return false;
    }
    state.setHeld(encoded);
    flush();
    return true;
  }

  public boolean equip(GolemEquipmentSlot slot, ItemRef item) {
    if (retired || !entity.isValid() || body() != GolemBody.BONE || item.amount() != 1 || !slot.accepts(item.type())
        || state.equipment().containsKey(slot)) {
      return false;
    }
    state.setEquipment(slot, platform.encodeItem(item));
    flush();
    wear(slot, item.copy());
    return true;
  }

  public Optional<ItemRef> unequip(GolemEquipmentSlot slot, String expected) {
    if (retired || !entity.isValid()) {
      return Optional.empty();
    }
    String stored = state.equipment().get(slot);
    if (stored == null || !stored.equals(expected)) {
      return Optional.empty();
    }
    ItemRef item;
    try {
      item = platform.decodeItem(stored);
    } catch (RuntimeException corrupt) {
      AdaptApi.log().error("Skipping unreadable equipped item on golem " + id() + "; the stored entry is kept.", corrupt);
      return Optional.empty();
    }
    state.setEquipment(slot, null);
    flush();
    wear(slot, null);
    return Optional.of(item);
  }

  public List<ItemRef> equipmentItems() {
    Map<GolemEquipmentSlot, String> stored = state.equipment();
    List<ItemRef> items = new ArrayList<>(stored.size());
    for (String encoded : stored.values()) {
      try {
        items.add(platform.decodeItem(encoded));
      } catch (RuntimeException corrupt) {
        AdaptApi.log().error("Skipping unreadable equipped item on golem " + id() + "; the stored entry is kept.", corrupt);
      }
    }
    return items;
  }

  Optional<ItemRef> equipped(GolemEquipmentSlot slot) {
    String stored = state.equipment().get(slot);
    if (stored == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(platform.decodeItem(stored));
    } catch (RuntimeException corrupt) {
      AdaptApi.log().error("Skipping unreadable equipped item on golem " + id() + "; the stored entry is kept.", corrupt);
      return Optional.empty();
    }
  }

  void damageEquipment(GolemEquipmentSlot slot, int amount) {
    if (retired || !entity.isValid() || body() != GolemBody.BONE || amount <= 0 || !state.equipment().containsKey(slot)) {
      return;
    }
    entity.damageEquipment(slot.slot(), amount);
    equipmentChanged(slot, entity.equipment(slot.slot()));
  }

  void equipmentChanged(GolemEquipmentSlot slot, ItemRef item) {
    if (retired || !entity.isValid() || body() != GolemBody.BONE) {
      return;
    }
    String stored = state.equipment().get(slot);
    if (stored == null) {
      return;
    }
    String encoded = item == null || item.isEmpty() || item.amount() <= 0 || GolemEquipmentSlot.isAir(item.type()) ? null : platform.encodeItem(item);
    if (!Objects.equals(stored, encoded)) {
      state.setEquipment(slot, encoded);
      flush();
    }
  }

  public void setCores(List<GolemCoreType> cores) {
    state.setCores(cores);
    if (GolemCombat.locked(this)) {
      state.setCombatOff(false);
    }
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

  public void setHome(GolemAnchor home) {
    state.setHome(Objects.requireNonNull(home));
    flush();
  }

  public void setHomeChest(GolemAnchor chest) {
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

  public void setSnowTrail(boolean snowTrail) {
    state.setSnowTrail(snowTrail);
    flush();
  }

  public boolean setCombat(boolean enabled) {
    if (!enabled && GolemCombat.locked(this)) {
      return false;
    }
    state.setCombatOff(!enabled);
    flush();
    return true;
  }

  void setName(String name) {
    state.setName(Objects.requireNonNull(name));
    flush();
  }

  public void restoreFullHealth() {
    if (isDormant()) {
      fullHealthOwed = true;
      return;
    }
    fillHealth();
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

  public int ownerLevel(String adaptation) {
    return state.ownerLevel(adaptation);
  }

  public void recordOwnerLevel(String adaptation, int level) {
    if (state.setOwnerLevel(adaptation, level)) {
      markDebounced();
    }
  }

  public void setHostileDamagePool(double pool) {
    state.setHostileDamagePool(pool);
    markDebounced();
  }

  public boolean flush() {
    if (retired) {
      return false;
    }
    trackPosition(entity.location());
    state.write(entity);
    writePolicy.flushed();
    return true;
  }

  void settleFullHealth() {
    if (fullHealthOwed) {
      fullHealthOwed = false;
      fillHealth();
    }
  }

  boolean isDirty() {
    return writePolicy.isDirty();
  }

  void flushIfDirty() {
    if (writePolicy.isDirty()) {
      flush();
    }
  }

  GolemPlatform platform() {
    return platform;
  }

  void setCarriedEncoded(List<String> encoded) {
    state.setCarried(encoded);
    flush();
  }

  GolemStatPlan appliedStats() {
    return appliedStats;
  }

  void setAppliedStats(GolemStatPlan plan) {
    appliedStats = plan;
  }

  boolean refreshPatinaStage() {
    if (body() != GolemBody.COPPER) {
      return false;
    }
    int stage = platform.patinaStage(entity);
    if (stage == patinaStage) {
      return false;
    }
    patinaStage = stage;
    return true;
  }

  void setValidated(boolean validated) {
    this.validated = validated;
  }

  ChunkPosition position() {
    return position;
  }

  ChunkPosition trackPosition(Loc location) {
    WorldRef world = location == null ? null : location.world();
    if (world == null) {
      return position;
    }
    UUID worldId = world.uid();
    long chunkKey = GolemRoster.chunkKey(location.blockX() >> 4, location.blockZ() >> 4);
    ChunkPosition current = position;
    if (current == null || current.chunkKey() != chunkKey || !current.world().equals(worldId)) {
      position = new ChunkPosition(worldId, chunkKey);
    }
    return position;
  }

  void setToolNote(GolemToolNote note) {
    toolNote = note;
  }

  void setDormant(boolean next) {
    dormant = next;
  }

  void retire() {
    retired = true;
  }

  private void wear(GolemEquipmentSlot slot, ItemRef item) {
    entity.setEquipment(slot.slot(), item);
    entity.setEquipmentDropChance(slot.slot(), 0F);
  }

  private void fillHealth() {
    GolemAttribute maximum = platform.attribute(entity, GolemStat.MAX_HEALTH);
    if (maximum != null && !entity.isDead()) {
      entity.setHealth(maximum.value());
    }
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

  record ChunkPosition(UUID world, long chunkKey) {
  }
}
