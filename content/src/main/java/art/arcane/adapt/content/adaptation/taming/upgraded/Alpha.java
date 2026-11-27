package art.arcane.adapt.content.adaptation.taming.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.taming.TameAttributeBoost;
import art.arcane.adapt.content.adaptation.taming.TameBonds;
import art.arcane.adapt.content.adaptation.taming.TamePackBuff;
import art.arcane.adapt.content.adaptation.taming.TameRegeneration;
import art.arcane.adapt.content.adaptation.taming.TamingPetSweep;
import art.arcane.adapt.content.adaptation.taming.TamingPreferences;
import art.arcane.adapt.content.adaptation.taming.TamingStableHand;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tame Damage, Tame Health, Tame Regeneration and Pack Leader Aura fused: every pet you own hits harder and has more
 * health, a hurt pet heals itself, and pets inside your aura hit harder still, heal more and gain speed and regeneration.
 * One horse, donkey, mule, camel or strider bonded with the Beastmaster's Whistle gets Mounted Tactics at its top level
 * while you ride it, grows faster and tougher with the distance you ride it, up to a cap, and comes when the whistle
 * calls if a path exists. Its growth stays on the mount but works only under its bonded owner.
 */
public class Alpha extends SkillUpgrade<Alpha.Config> implements TameBonds.Bonds {
  public static final Id ID = Id.adapt("taming/upgrade/alpha");

  private static final long LEADER_MAX_AGE_MILLIS = 5000L;
  private static final String GROWTH_SPEED = "bond-speed";
  private static final String GROWTH_HEALTH = "bond-health";
  private static final long GROWTH_TICKS = 40L;
  private final Map<UUID, Loc> lastRidden = playerState();
  private final TamingPetSweep sweep = new TamingPetSweep("upgraded-taming-alpha");
  private final TameAttributeBoost damage = new TameAttributeBoost(Id.of("adapt", "upgraded-taming-alpha-damage"),
      Attributes.ATTACK_DAMAGE, this::showBoost);
  private final TameAttributeBoost health = new TameAttributeBoost(Id.of("adapt", "upgraded-taming-alpha-health"),
      Attributes.MAX_HEALTH, this::showBoost);
  private final Map<UUID, Leader> leaders = new ConcurrentHashMap<>();
  private final Cooldowns regenCooldowns = cooldowns();

  public Alpha() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("bone"));
    setInterval(1000);
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, TamingPreferences.SPEED, TamingPreferences.REGEN);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getDamageBoost(level), 0), 1);
    statLore(v, Format.pc(getHealthBoost(level), 0), 2);
    statLore(v, Format.f(getAuraRadius(level), 1), 3);
    statLore(v, Format.pc(getGrowthCap(level), 0), 4);
    statLore(v, Format.f(getConfig().callRange, 0), 5);
  }

  @Override
  public int tacticsLevel(PlayerRef rider, EntityRef vehicle, Adaptation<?> tactics) {
    return getActiveLevel(rider) > 0 && TameBonds.bondedTo(rider, vehicle) ? tactics.getMaxLevel() : 0;
  }

  @Override
  public boolean bond(PlayerRef player, LivingRef mount) {
    if (getActiveLevel(player) <= 0 || !TameBonds.bondable(player, mount) || !canInteract(player, mount.location())) {
      return false;
    }
    UUID previous = player.data().get(TameBonds.PLAYER_MOUNT);
    EntityRef earlier = previous == null || previous.equals(mount.uuid()) ? null : AdaptApi.platform().host().entity(previous);
    if (earlier != null && player.uuid().equals(earlier.data().get(TameBonds.MOUNT_OWNER))) {
      AdaptApi.scheduler().runEntity(earlier, () -> earlier.data().remove(TameBonds.MOUNT_OWNER));
    }
    player.data().set(TameBonds.PLAYER_MOUNT, mount.uuid());
    AdaptApi.scheduler().runEntity(mount, () -> {
      mount.data().set(TameBonds.MOUNT_OWNER, player.uuid());
      TamingStableHand.shapedFx(this, mount);
    });
    cue(player, MutationCue.BOON);
    addStat(player, "taming.alpha.mounts-bonded", 1);
    return true;
  }

  @Override
  public boolean call(PlayerRef player) {
    UUID id = getActiveLevel(player) > 0 ? player.data().get(TameBonds.PLAYER_MOUNT) : null;
    EntityRef found = id == null ? null : AdaptApi.platform().host().entity(id);
    if (!(found instanceof MobRef mount) || !TameBonds.bondedTo(player, mount) || mount.equals(player.vehicle())) {
      return false;
    }
    Loc to = player.location();
    double range = getConfig().callRange;
    if (!mount.location().sameWorld(to) || mount.location().distanceSquared(to) > range * range
        || !AdaptApi.scheduler().owns(mount)) {
      return false;
    }
    return mount.pathTo(to, getConfig().callSpeed);
  }

  private void ride(PlayerRef owner, int level) {
    EntityRef vehicle = owner.vehicle();
    if (!(vehicle instanceof LivingRef mount) || !TameBonds.bondedTo(owner, mount)) {
      lastRidden.remove(owner.uuid());
      return;
    }
    Loc here = owner.location();
    Loc before = lastRidden.put(owner.uuid(), here);
    double cap = getGrowthCap(level);
    AdaptApi.scheduler().runEntity(mount, () -> grow(mount, before, here, cap));
  }

  private void grow(LivingRef mount, Loc before, Loc here, double cap) {
    if (!mount.isValid() || mount.isDead()) {
      return;
    }
    double ridden = TameBonds.ridden(mount);
    if (before != null && before.sameWorld(here)) {
      double moved = Math.min(getConfig().maxBlocksPerSecond, before.withY(0D).distance(here.withY(0D)));
      double next = ridden + Math.max(0D, moved);
      if (Math.floor(growth(next, cap) / getConfig().growthStep) > Math.floor(growth(ridden, cap) / getConfig().growthStep)) {
        TamingStableHand.shapedFx(this, mount);
      }
      ridden = next;
      mount.data().set(TameBonds.MOUNT_RIDDEN, ridden);
    }
    double growth = growth(ridden, cap);
    if (growth <= 0D) {
      return;
    }
    AdaptAttributeService.get().applyTimed(mount, getId().toString(), GROWTH_SPEED, Attributes.MOVEMENT_SPEED, growth,
        AttributeOperation.ADD_MULTIPLIED_BASE, GROWTH_TICKS);
    AdaptAttributeService.get().applyTimed(mount, getId().toString(), GROWTH_HEALTH, Attributes.MAX_HEALTH, growth,
        AttributeOperation.ADD_MULTIPLIED_BASE, GROWTH_TICKS);
  }

  double growth(double ridden, double cap) {
    return Math.min(cap, Math.max(0D, ridden * getConfig().growthPerBlock));
  }

  @Override
  public void unregister() {
    sweep.close((LivingRef pet) -> {
      damage.remove(pet);
      health.remove(pet);
    });
    leaders.clear();
    super.unregister();
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    List<PlayerProfile> owners = learnedCandidates(now);
    for (PlayerProfile data : owners) {
      PlayerRef owner = data.player();
      if (owner == null) {
        continue;
      }
      if (AdaptApi.scheduler().isRegionized()) {
        AdaptApi.scheduler().runEntity(owner, () -> lead(owner, now));
      } else {
        lead(owner, now);
      }
    }
    leaders.values().removeIf((Leader leader) -> now - leader.capturedAt() > LEADER_MAX_AGE_MILLIS);
    if (leaders.isEmpty() && damage.isEmpty() && health.isEmpty()) {
      return;
    }
    sweep.pass(owners, now, getConfig().maxPetsPerPass, this::refresh);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof LivingRef pet) || !pet.has(EntityTrait.TAMEABLE) || e.finalDamage() <= 0D
        || !AdaptApi.platform().host().animals().isTamed(pet)) {
      return;
    }
    UUID ownerId = pet.tameOwner();
    Leader leader = ownerId == null ? null : leaders.get(ownerId);
    if (leader == null || !regenCooldowns.isReady(pet.uuid(), (long) getConfig().regenCooldownMillis)) {
      return;
    }
    regenCooldowns.mark(pet.uuid());
    int level = leader.level();
    double requested = getRegeneration(level) * (leader.near(pet.location(), getAuraRadius(level)) ? getConfig().nearMultiplier : 1D);
    AdaptApi.scheduler().runEntity(pet, () -> regenerate(pet, ownerId, requested));
  }

  private void regenerate(LivingRef pet, UUID ownerId, double requested) {
    if (!pet.isValid() || pet.isDead()) {
      return;
    }
    double healed = TameRegeneration.heal(this, pet, requested);
    PlayerRef owner = healed > 0D ? AdaptApi.platform().host().player(ownerId) : null;
    if (owner != null) {
      AdaptApi.scheduler().runEntity(owner, () -> {
        addStat(owner, "taming.alpha.health-regenerated", healed);
        cue(owner, MutationCue.BOON);
      });
    }
  }

  private void lead(PlayerRef owner, long now) {
    int level = getActiveLevel(owner);
    if (level <= 0) {
      leaders.remove(owner.uuid());
      lastRidden.remove(owner.uuid());
      return;
    }
    ride(owner, level);
    leaders.put(owner.uuid(), new Leader(level, owner.location(), now,
        preferenceEnabled(owner, TamingPreferences.SPEED), preferenceEnabled(owner, TamingPreferences.REGEN)));
  }

  private void refresh(LivingRef pet, UUID ownerId) {
    Leader leader = ownerId == null ? null : leaders.get(ownerId);
    if (leader == null) {
      damage.update(pet, 0, 0D);
      health.update(pet, 0, 0D);
      return;
    }

    int level = leader.level();
    boolean near = leader.near(pet.location(), getAuraRadius(level));
    double damageBoost = getDamageBoost(level) * (near ? getConfig().nearMultiplier : 1D);
    damage.update(pet, (level * 2) + (near ? 1 : 0), damageBoost);
    health.update(pet, level, getHealthBoost(level));
    if (near) {
      TamePackBuff.apply(this, pet, getAuraAmplifier(level), getAuraTicks(), leader.speed(), leader.regeneration());
    }
  }

  private void showBoost(LivingRef pet) {
    fx(pet, FxPriority.TRANSITION)
        .ring(Particles.CRIT, 0.4D, 6, 0.6D)
        .dustBurst(Rgb.of(0xE8E0C8), 3, 0.3D, 1.0F);
  }

  double getDamageBoost(int level) {
    return getConfig().damageBoostBase + (level * getConfig().damageBoostPerLevel);
  }

  double getHealthBoost(int level) {
    return getConfig().healthBoostBase + (level * getConfig().healthBoostPerLevel);
  }

  double getRegeneration(int level) {
    return getConfig().regenerationBase + (level * getConfig().regenerationPerLevel);
  }

  public double getAuraRadius(int level) {
    return getConfig().auraRadiusBase + (level * getConfig().auraRadiusPerLevel);
  }

  public int getAuraAmplifier(int level) {
    return Math.max(0, level - 1);
  }

  public int getAuraTicks() {
    return getConfig().auraTicks;
  }

  double getGrowthCap(int level) {
    return Math.max(0D, getConfig().growthCapBase + (level * getConfig().growthCapPerLevel));
  }

  private record Leader(int level, Loc location, long capturedAt, boolean speed, boolean regeneration) {
    private boolean near(Loc pet, double radius) {
      return pet.sameWorld(location) && pet.distanceSquared(location) <= radius * radius;
    }
  }

  @ConfigDescription("Your pets hit harder, have more health and heal when hurt, more so inside your aura, and your bonded mount grows as you ride.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Pet damage bonus before level scaling.", impact = "Higher values make every pet hit harder.")
    double damageBoostBase = 0.25;
    @ConfigDoc(value = "Pet damage bonus added per level.", impact = "Higher values reward higher levels more.")
    double damageBoostPerLevel = 0.2;
    @ConfigDoc(value = "Pet maximum health bonus before level scaling.", impact = "Higher values make every pet tougher.")
    double healthBoostBase = 1.57;
    @ConfigDoc(value = "Pet maximum health bonus added per level.", impact = "Higher values reward higher levels more.")
    double healthBoostPerLevel = 0.5;
    @ConfigDoc(value = "Health a hurt pet regains before level scaling.", impact = "Higher values heal pets more per hit taken.")
    double regenerationBase = 3.0;
    @ConfigDoc(value = "Health regained added per level.", impact = "Higher values reward higher levels more.")
    double regenerationPerLevel = 1.0;
    @ConfigDoc(value = "Time before the same pet heals again, in milliseconds.", impact = "Lower values let pets heal more often.")
    double regenCooldownMillis = 6000;
    @ConfigDoc(value = "Aura radius around you before level scaling, in blocks.", impact = "Higher values reach farther pets.")
    double auraRadiusBase = 10;
    @ConfigDoc(value = "Aura radius added per level, in blocks.", impact = "Higher values widen the aura at higher levels.")
    double auraRadiusPerLevel = 4;
    @ConfigDoc(value = "Multiplier on the damage bonus and healing for pets inside your aura.", impact = "Higher values reward keeping pets close.")
    double nearMultiplier = 1.5;
    @ConfigDoc(value = "Ticks the aura's speed and regeneration last after each refresh.", impact = "Higher values keep the buffs longer after a pet leaves the aura.")
    int auraTicks = 80;
    @ConfigDoc(value = "Most loaded pets refreshed each second.", impact = "Higher values refresh large packs faster but cost more per tick.")
    int maxPetsPerPass = 128;
    @ConfigDoc(value = "Bonded mount growth cap before level scaling, as a fraction of its speed and health. 0.1 is 10%.", impact = "Higher values let a bonded mount grow further.")
    double growthCapBase = 0;
    @ConfigDoc(value = "Growth cap added per level.", impact = "Higher values reward higher levels more.")
    double growthCapPerLevel = 0.1;
    @ConfigDoc(value = "Growth a bonded mount gains for each block you ride it, as a fraction. 0.0001 is 1% per 100 blocks.", impact = "Higher values reach the cap sooner.")
    double growthPerBlock = 0.0001;
    @ConfigDoc(value = "Most blocks of riding counted each second.", impact = "Stops teleports and fast travel from counting as riding.")
    double maxBlocksPerSecond = 20;
    @ConfigDoc(value = "Growth between two shows of the mount's growth, as a fraction.", impact = "Lower values show growth more often.")
    double growthStep = 0.05;
    @ConfigDoc(value = "Farthest a bonded mount comes from when the whistle calls, in blocks.", impact = "Higher values call the mount from farther away.")
    double callRange = 128;
    @ConfigDoc(value = "Walking speed of a called mount.", impact = "Higher values bring the mount sooner.")
    double callSpeed = 1.4;
  }
}
