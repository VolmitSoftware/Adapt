package art.arcane.adapt.testkit;

import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LastDamage;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityRegainHealthEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Living entity with health, effects, attribute modifiers and equipment. */
public class FakeLiving extends FakeEntity implements LivingRef {
  private final Map<Id, EffectInstance> effects = new LinkedHashMap<>();
  private final Map<Id, Double> attributeBases = new HashMap<>();
  private final Map<Id, Map<Id, AttributeMod>> modifiers = new HashMap<>();
  private final Set<Id> absentAttributes = new HashSet<>();
  private final Map<EquipmentSlot, Float> dropChances = new HashMap<>();
  private boolean canPickupItems;
  private boolean removeWhenFarAway = true;
  private LastDamage lastDamage;
  private final Map<EquipmentSlot, ItemRef> equipment = new EnumMap<>(EquipmentSlot.class);
  private double health = 20D;
  private double absorption;
  private int noDamageTicks;
  private double lastHurtAmount;
  private boolean gliding;
  private boolean swimming;
  private int remainingAir = 300;
  private boolean climbing;
  private boolean handRaised;
  private boolean invisible;
  private boolean ai = true;
  private double damageTaken;
  private Id lastDamageType;
  private EntityRef lastDamageSource;

  private int maxNoDamageTicks = 20;
  private ItemRef activeItem;

  public FakeLiving(Id type, Loc location) {
    super(type, location);
  }

  /** Damage dealt through {@link #damage} so far. */
  public double damageTaken() {
    return damageTaken;
  }

  @Override
  public double health() {
    return health;
  }

  @Override
  public void setHealth(double health) {
    this.health = Math.max(0D, Math.min(maxHealth(), health));
    setDead(this.health <= 0D);
  }

  @Override
  public double maxHealth() {
    return 20D;
  }

  @Override
  public double absorption() {
    return absorption;
  }

  @Override
  public void setAbsorption(double absorption) {
    this.absorption = absorption;
  }

  @Override
  public void damage(double amount, EntityRef source) {
    damageTaken += amount;
    setHealth(health - amount);
  }

  @Override
  public void damage(double amount, Id damageType) {
    damage(amount, (EntityRef) null);
  }

  @Override
  public void damage(double amount, Id damageType, EntityRef source) {
    lastDamageType = damageType;
    lastDamageSource = source;
    damage(amount, source);
  }

  /** Damage type passed to the last typed {@link #damage(double, Id, EntityRef)} call, or null. */
  public Id lastDamageType() {
    return lastDamageType;
  }

  /** Source passed to the last typed {@link #damage(double, Id, EntityRef)} call, or null. */
  public EntityRef lastDamageSource() {
    return lastDamageSource;
  }

  @Override
  public void heal(double amount) {
    setHealth(health + amount);
  }

  @Override
  public int noDamageTicks() {
    return noDamageTicks;
  }

  @Override
  public void setNoDamageTicks(int ticks) {
    noDamageTicks = ticks;
  }

  @Override
  public void setLastHurtAmount(double amount) {
    lastHurtAmount = amount;
  }

  public double lastHurtAmount() {
    return lastHurtAmount;
  }

  @Override
  public Loc eyeLocation() {
    return location().add(0D, 1.62D, 0D);
  }

  @Override
  public boolean isSwimming() {
    return swimming;
  }

  @Override
  public void setSwimming(boolean swimming) {
    this.swimming = swimming;
  }

  @Override
  public int remainingAir() {
    return remainingAir;
  }

  @Override
  public void setRemainingAir(int remainingAir) {
    this.remainingAir = remainingAir;
  }

  @Override
  public int maximumAir() {
    return 300;
  }

  @Override
  public boolean isGliding() {
    return gliding;
  }

  @Override
  public void setGliding(boolean gliding) {
    this.gliding = gliding;
  }

  @Override
  public boolean isClimbing() {
    return climbing;
  }

  public void setClimbing(boolean climbing) {
    this.climbing = climbing;
  }

  @Override
  public boolean isHandRaised() {
    return handRaised;
  }

  public void setHandRaised(boolean handRaised) {
    this.handRaised = handRaised;
  }

  @Override
  public boolean isInvisible() {
    return invisible;
  }

  @Override
  public void setInvisible(boolean invisible) {
    this.invisible = invisible;
  }

  @Override
  public boolean hasAi() {
    return ai;
  }

  @Override
  public void setAi(boolean ai) {
    this.ai = ai;
  }

  @Override
  public PlayerRef killer() {
    return null;
  }

  /** Makes the entity lack these attributes, like a server version that does not have them. */
  public FakeLiving withoutAttributes(Id... attributes) {
    absentAttributes.addAll(List.of(attributes));
    return this;
  }

  @Override
  public double attributeValue(Id attribute) {
    if (absentAttributes.contains(attribute)) {
      return Double.NaN;
    }
    double base = attributeBase(attribute);
    double added = base;
    double multiplier = 1D;
    for (AttributeMod modifier : modifiers.getOrDefault(attribute, Map.of()).values()) {
      switch (modifier.operation()) {
        case ADD_VALUE -> added += modifier.amount();
        case ADD_MULTIPLIED_BASE -> added += base * modifier.amount();
        case ADD_MULTIPLIED_TOTAL -> multiplier *= 1D + modifier.amount();
      }
    }
    return added * multiplier;
  }

  @Override
  public double attributeBase(Id attribute) {
    if (absentAttributes.contains(attribute)) {
      return Double.NaN;
    }
    return attributeBases.getOrDefault(attribute, 1D);
  }

  @Override
  public void setAttributeBase(Id attribute, double value) {
    attributeBases.put(attribute, value);
  }

  @Override
  public boolean hasAttribute(Id attribute) {
    return !absentAttributes.contains(attribute);
  }

  @Override
  public boolean setModifier(Id attribute, AttributeMod modifier) {
    if (absentAttributes.contains(attribute)) {
      return false;
    }
    modifiers.computeIfAbsent(attribute, (Id ignored) -> new LinkedHashMap<>()).put(modifier.key(), modifier);
    return true;
  }

  @Override
  public boolean setTransientModifier(Id attribute, AttributeMod modifier) {
    return setModifier(attribute, modifier);
  }

  @Override
  public void removeModifier(Id attribute, Id modifierKey) {
    Map<Id, AttributeMod> current = modifiers.get(attribute);
    if (current != null) {
      current.remove(modifierKey);
    }
  }

  @Override
  public AttributeMod modifier(Id attribute, Id modifierKey) {
    return modifiers.getOrDefault(attribute, Map.of()).get(modifierKey);
  }

  @Override
  public boolean addEffect(EffectInstance effect) {
    EffectInstance current = effects.get(effect.type());
    if (current != null && current.amplifier() > effect.amplifier()) {
      return false;
    }
    effects.put(effect.type(), effect);
    return true;
  }

  @Override
  public EffectInstance effect(Id type) {
    return effects.get(type);
  }

  @Override
  public boolean hasEffect(Id type) {
    return effects.containsKey(type);
  }

  @Override
  public void removeEffect(Id type) {
    effects.remove(type);
  }

  @Override
  public Collection<EffectInstance> effects() {
    return List.copyOf(effects.values());
  }

  @Override
  public ItemRef equipment(EquipmentSlot slot) {
    return equipment.getOrDefault(slot, FakeItem.empty());
  }

  @Override
  public void setEquipment(EquipmentSlot slot, ItemRef item) {
    if (item == null) {
      equipment.remove(slot);
    } else {
      equipment.put(slot, item);
    }
  }

  @Override
  public List<AttributeMod> modifiers(Id attribute) {
    return List.copyOf(modifiers.getOrDefault(attribute, Map.of()).values());
  }

  @Override
  public void setEquipmentDropChance(EquipmentSlot slot, float chance) {
    dropChances.put(slot, chance);
  }

  /** Drop chance last set for the slot, or null. */
  public Float equipmentDropChance(EquipmentSlot slot) {
    return dropChances.get(slot);
  }

  @Override
  public void damageEquipment(EquipmentSlot slot, int amount) {
    ItemRef item = equipment(slot);
    if (amount <= 0 || item.isEmpty() || item.maxDamage() <= 0) {
      return;
    }
    int next = item.damage() + amount;
    if (next >= item.maxDamage()) {
      setEquipment(slot, null);
      return;
    }
    item.setDamage(next);
    setEquipment(slot, item);
  }

  @Override
  public void playEquipmentBreak(EquipmentSlot slot) {
  }

  @Override
  public boolean canPickupItems() {
    return canPickupItems;
  }

  @Override
  public void setCanPickupItems(boolean pickup) {
    canPickupItems = pickup;
  }

  @Override
  public void setRemoveWhenFarAway(boolean remove) {
    removeWhenFarAway = remove;
  }

  public boolean removeWhenFarAway() {
    return removeWhenFarAway;
  }

  @Override
  public double regainHealth(double amount, EntityRegainHealthEvent.Reason reason) {
    if (!Double.isFinite(amount) || amount <= 0D || isDead() || health() <= 0D || health() >= maxHealth()) {
      return 0D;
    }
    double before = health();
    setHealth(Math.min(maxHealth(), before + amount));
    return health() - before;
  }

  @Override
  public LastDamage lastDamage() {
    return lastDamage;
  }

  private List<BlockRef> lastTwoTargetBlocks = List.of();

  public void setLastDamage(LastDamage damage) {
    lastDamage = damage;
  }

  /** Sets what {@link #lastTwoTargetBlocks(int)} returns: previous block first, target last. */
  public void setLastTwoTargetBlocks(List<BlockRef> blocks) {
    lastTwoTargetBlocks = List.copyOf(blocks);
  }

  @Override
  public List<BlockRef> lastTwoTargetBlocks(int maxDistance) {
    return lastTwoTargetBlocks;
  }

  @Override
  public BlockRef targetBlock(double range) {
    Loc eye = eyeLocation();
    return world().rayTraceBlock(eye.position(), eye.direction(), range, false);
  }

  @Override
  public boolean hasLineOfSight(EntityRef other) {
    return true;
  }

  @Override
  public void swingHand(Hand hand) {
  }

  @Override
  public int maxNoDamageTicks() {
    return maxNoDamageTicks;
  }

  public void setMaxNoDamageTicks(int ticks) {
    maxNoDamageTicks = ticks;
  }

  @Override
  public ItemRef activeItem() {
    return activeItem == null ? FakeItem.empty() : activeItem;
  }

  public void setActiveItem(ItemRef item) {
    activeItem = item;
  }
}
