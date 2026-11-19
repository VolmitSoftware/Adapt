package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.bukkit.event.BukkitEventMaps;
import art.arcane.adapt.bukkit.platform.BukkitConversions;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.platform.BukkitResolve;
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
import art.arcane.adapt.util.common.compat.PaperCompat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Steerable;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BukkitLivingRef extends BukkitEntityRef implements LivingRef {
  private static final Id MAX_HEALTH = Id.minecraft("max_health");

  public BukkitLivingRef(LivingEntity entity) {
    super(entity);
  }

  public LivingEntity living() {
    return (LivingEntity) handle();
  }

  @Override
  public double health() {
    return living().getHealth();
  }

  @Override
  public void setHealth(double health) {
    LivingEntity entity = living();
    double max = maxHealth();
    entity.setHealth(Math.max(0.0D, Double.isNaN(max) ? health : Math.min(max, health)));
  }

  @Override
  public double maxHealth() {
    double value = attributeValue(MAX_HEALTH);
    return Double.isNaN(value) ? 20.0D : value;
  }

  @Override
  public double absorption() {
    return living().getAbsorptionAmount();
  }

  @Override
  public void setAbsorption(double absorption) {
    living().setAbsorptionAmount(Math.max(0.0D, absorption));
  }

  @Override
  public void damage(double amount, EntityRef source) {
    Entity attacker = source == null ? null : BukkitRefs.entity(source);
    if (attacker == null) {
      living().damage(amount);
    } else {
      living().damage(amount, attacker);
    }
  }

  @Override
  public void damage(double amount, Id damageType) {
    if (!BukkitLivingVersion.damage(living(), amount, damageType, null)) {
      living().damage(amount);
    }
  }

  @Override
  public void damage(double amount, Id damageType, EntityRef source) {
    Entity attacker = source == null ? null : BukkitRefs.entity(source);
    if (!BukkitLivingVersion.damage(living(), amount, damageType, attacker)) {
      damage(amount, source);
    }
  }

  @Override
  public void heal(double amount) {
    BukkitLivingVersion.heal(living(), amount);
  }

  @Override
  public int noDamageTicks() {
    return living().getNoDamageTicks();
  }

  @Override
  public void setNoDamageTicks(int ticks) {
    living().setNoDamageTicks(ticks);
  }

  @Override
  public void setLastHurtAmount(double amount) {
    living().setLastDamage(amount);
  }

  @Override
  public Loc eyeLocation() {
    return BukkitRefs.loc(living().getEyeLocation());
  }

  @Override
  public boolean isSwimming() {
    return living().isSwimming();
  }

  @Override
  public void setSwimming(boolean swimming) {
    living().setSwimming(swimming);
  }

  @Override
  public int remainingAir() {
    return living().getRemainingAir();
  }

  @Override
  public void setRemainingAir(int ticks) {
    living().setRemainingAir(ticks);
  }

  @Override
  public int maximumAir() {
    return living().getMaximumAir();
  }

  @Override
  public boolean isGliding() {
    return living().isGliding();
  }

  @Override
  public void setGliding(boolean gliding) {
    living().setGliding(gliding);
  }

  @Override
  public boolean isClimbing() {
    return living().isClimbing();
  }

  @Override
  public boolean isHandRaised() {
    return PaperCompat.isHandRaised(living());
  }

  @Override
  public boolean isInvisible() {
    return PaperCompat.isInvisible(living());
  }

  @Override
  public void setInvisible(boolean invisible) {
    PaperCompat.setInvisible(living(), invisible);
  }

  @Override
  public boolean hasAi() {
    return living().hasAI();
  }

  @Override
  public void setAi(boolean ai) {
    living().setAI(ai);
  }

  @Override
  public PlayerRef killer() {
    Player killer = living().getKiller();
    return killer == null ? null : BukkitRefs.player(killer);
  }

  @Override
  public double attributeValue(Id attribute) {
    AttributeInstance instance = instance(attribute);
    return instance == null ? Double.NaN : instance.getValue();
  }

  @Override
  public double attributeBase(Id attribute) {
    AttributeInstance instance = instance(attribute);
    return instance == null ? Double.NaN : instance.getBaseValue();
  }

  @Override
  public void setAttributeBase(Id attribute, double value) {
    AttributeInstance instance = instance(attribute);
    if (instance != null) {
      instance.setBaseValue(value);
    }
  }

  @Override
  public boolean hasAttribute(Id attribute) {
    return instance(attribute) != null;
  }

  @Override
  public boolean setModifier(Id attribute, AttributeMod modifier) {
    AttributeInstance instance = instance(attribute);
    if (instance == null) {
      return false;
    }
    NamespacedKey key = BukkitConversions.key(modifier.key());
    BukkitLivingVersion.removeModifier(instance, key);
    instance.addModifier(BukkitLivingVersion.modifier(key, modifier.amount(), BukkitConversions.operation(modifier.operation())));
    return true;
  }

  @Override
  public boolean setTransientModifier(Id attribute, AttributeMod modifier) {
    AttributeInstance instance = instance(attribute);
    if (instance == null) {
      return false;
    }
    NamespacedKey key = BukkitConversions.key(modifier.key());
    BukkitLivingVersion.removeModifier(instance, key);
    PaperCompat.addTransientModifier(instance,
        BukkitLivingVersion.modifier(key, modifier.amount(), BukkitConversions.operation(modifier.operation())));
    return true;
  }

  @Override
  public void removeModifier(Id attribute, Id modifierKey) {
    AttributeInstance instance = instance(attribute);
    if (instance != null) {
      BukkitLivingVersion.removeModifier(instance, BukkitConversions.key(modifierKey));
    }
  }

  @Override
  public AttributeMod modifier(Id attribute, Id modifierKey) {
    AttributeInstance instance = instance(attribute);
    if (instance == null) {
      return null;
    }
    AttributeModifier modifier = BukkitLivingVersion.modifier(instance, BukkitConversions.key(modifierKey));
    if (modifier == null) {
      return null;
    }
    return new AttributeMod(modifierKey, modifier.getAmount(), BukkitConversions.operation(modifier.getOperation()));
  }

  @Override
  public boolean addEffect(EffectInstance effect) {
    PotionEffectType type = BukkitResolve.effect(effect.type());
    if (type == null) {
      return false;
    }
    int duration = effect.isInfinite() ? PotionEffect.INFINITE_DURATION : effect.durationTicks();
    return living().addPotionEffect(new PotionEffect(type, duration, effect.amplifier(), effect.ambient(), effect.particles(), effect.icon()), true);
  }

  @Override
  public EffectInstance effect(Id type) {
    PotionEffectType resolved = BukkitResolve.effect(type);
    if (resolved == null) {
      return null;
    }
    PotionEffect effect = living().getPotionEffect(resolved);
    return effect == null ? null : effect(effect);
  }

  @Override
  public boolean hasEffect(Id type) {
    PotionEffectType resolved = BukkitResolve.effect(type);
    return resolved != null && living().hasPotionEffect(resolved);
  }

  @Override
  public void removeEffect(Id type) {
    PotionEffectType resolved = BukkitResolve.effect(type);
    if (resolved != null) {
      living().removePotionEffect(resolved);
    }
  }

  @Override
  public Collection<EffectInstance> effects() {
    Collection<PotionEffect> active = living().getActivePotionEffects();
    if (active.isEmpty()) {
      return List.of();
    }
    List<EffectInstance> effects = new ArrayList<>(active.size());
    for (PotionEffect effect : active) {
      effects.add(effect(effect));
    }
    return effects;
  }

  @Override
  public ItemRef equipment(EquipmentSlot slot) {
    EntityEquipment equipment = living().getEquipment();
    if (equipment == null) {
      return BukkitRefs.emptyItem();
    }
    try {
      return BukkitRefs.item(equipment.getItem(BukkitConversions.slot(slot)));
    } catch (IllegalArgumentException unsupported) {
      return slot == EquipmentSlot.SADDLE ? BukkitRefs.item(saddleWithoutSlot()) : BukkitRefs.emptyItem();
    }
  }

  @Override
  public void setEquipment(EquipmentSlot slot, ItemRef item) {
    EntityEquipment equipment = living().getEquipment();
    if (equipment == null) {
      return;
    }
    try {
      equipment.setItem(BukkitConversions.slot(slot), BukkitInventoryRef.stackOrNull(item));
    } catch (IllegalArgumentException unsupported) {
      if (slot != EquipmentSlot.SADDLE || !setSaddleWithoutSlot(BukkitInventoryRef.stackOrNull(item))) {
        Adapt.verbose("Equipment slot " + slot + " unsupported on " + type());
      }
    }
  }

  /** Servers before the saddle equipment slot (1.21.5) keep the saddle as Steerable state or in the horse inventory. */
  private ItemStack saddleWithoutSlot() {
    if (living() instanceof Steerable steerable) {
      return steerable.hasSaddle() ? new ItemStack(Material.SADDLE) : null;
    }
    return living() instanceof AbstractHorse horse ? horse.getInventory().getSaddle() : null;
  }

  private boolean setSaddleWithoutSlot(ItemStack saddle) {
    if (living() instanceof Steerable steerable) {
      steerable.setSaddle(saddle != null && saddle.getType() == Material.SADDLE);
      return true;
    }
    if (living() instanceof AbstractHorse horse) {
      horse.getInventory().setSaddle(saddle);
      return true;
    }
    return false;
  }

  @Override
  public List<AttributeMod> modifiers(Id attribute) {
    AttributeInstance instance = instance(attribute);
    if (instance == null) {
      return List.of();
    }
    List<AttributeMod> modifiers = new ArrayList<>();
    for (AttributeModifier modifier : instance.getModifiers()) {
      modifiers.add(new AttributeMod(BukkitLivingVersion.id(modifier), modifier.getAmount(),
          BukkitConversions.operation(modifier.getOperation())));
    }
    return modifiers;
  }

  @Override
  public void setEquipmentDropChance(EquipmentSlot slot, float chance) {
    EntityEquipment equipment = living().getEquipment();
    if (equipment == null) {
      return;
    }
    try {
      equipment.setDropChance(BukkitConversions.slot(slot), chance);
    } catch (IllegalArgumentException | UnsupportedOperationException unsupported) {
      Adapt.verbose("Equipment drop chance for " + slot + " unsupported on " + type());
    }
  }

  @Override
  public void damageEquipment(EquipmentSlot slot, int amount) {
    if (amount > 0) {
      living().damageItemStack(BukkitConversions.slot(slot), amount);
    }
  }

  @Override
  public void playEquipmentBreak(EquipmentSlot slot) {
    living().broadcastSlotBreak(BukkitConversions.slot(slot));
  }

  @Override
  public boolean canPickupItems() {
    return living().getCanPickupItems();
  }

  @Override
  public void setCanPickupItems(boolean pickup) {
    living().setCanPickupItems(pickup);
  }

  @Override
  public void setRemoveWhenFarAway(boolean remove) {
    living().setRemoveWhenFarAway(remove);
  }

  @Override
  public double regainHealth(double amount, EntityRegainHealthEvent.Reason reason) {
    LivingEntity entity = living();
    if (!Double.isFinite(amount) || amount <= 0D || entity.isDead()) {
      return 0D;
    }
    double health = entity.getHealth();
    double maximum = maxHealth();
    if (health <= 0D || health >= maximum) {
      return 0D;
    }
    org.bukkit.event.entity.EntityRegainHealthEvent regain = new org.bukkit.event.entity.EntityRegainHealthEvent(entity, amount,
        org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.valueOf(reason.name()));
    Bukkit.getPluginManager().callEvent(regain);
    double granted = regain.getAmount();
    if (regain.isCancelled() || !Double.isFinite(granted) || granted <= 0D) {
      return 0D;
    }
    double next = Math.min(maximum, health + granted);
    entity.setHealth(next);
    return next - health;
  }

  @Override
  public LastDamage lastDamage() {
    org.bukkit.event.entity.EntityDamageEvent last = living().getLastDamageCause();
    if (last == null) {
      return null;
    }
    EntityRef damager = last instanceof EntityDamageByEntityEvent byEntity ? BukkitRefs.entity(byEntity.getDamager()) : null;
    return new LastDamage(BukkitEventMaps.cause(last.getCause()), damager, last.getFinalDamage());
  }

  @Override
  public List<BlockRef> lastTwoTargetBlocks(int maxDistance) {
    List<Block> blocks = living().getLastTwoTargetBlocks(null, maxDistance);
    List<BlockRef> refs = new ArrayList<>(blocks.size());
    for (Block block : blocks) {
      refs.add(BukkitRefs.block(block));
    }
    return refs;
  }

  @Override
  public BlockRef targetBlock(double range) {
    Block block = living().getTargetBlockExact((int) Math.ceil(range));
    return block == null ? null : BukkitRefs.block(block);
  }

  @Override
  public boolean hasLineOfSight(EntityRef other) {
    Entity target = BukkitRefs.entity(other);
    return target != null && living().hasLineOfSight(target);
  }

  @Override
  public void swingHand(Hand hand) {
    if (hand == Hand.MAIN_HAND) {
      living().swingMainHand();
    } else {
      living().swingOffHand();
    }
  }

  @Override
  public int maxNoDamageTicks() {
    return living().getMaximumNoDamageTicks();
  }

  @Override
  public ItemRef activeItem() {
    return BukkitRefs.item(PaperCompat.activeItem(living()));
  }

  private AttributeInstance instance(Id attribute) {
    return instance(living(), attribute);
  }

  /** The entity's instance of a 26.x attribute id, or null when the entity lacks it. */
  public static AttributeInstance instance(LivingEntity living, Id attribute) {
    Attribute resolved = BukkitLivingVersion.attribute(living, attribute);
    return resolved == null ? null : living.getAttribute(resolved);
  }

  private static EffectInstance effect(PotionEffect effect) {
    int duration = effect.isInfinite() ? EffectInstance.INFINITE : effect.getDuration();
    return new EffectInstance(BukkitResolve.id(effect.getType()), duration, effect.getAmplifier(),
        effect.isAmbient(), effect.hasParticles(), effect.hasIcon());
  }
}
