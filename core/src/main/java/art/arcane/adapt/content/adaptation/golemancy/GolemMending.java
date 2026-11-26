/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMend;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRepairXp;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemSnapshot;
import art.arcane.adapt.api.golem.GolemVitals;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.item.PackedGolem;
import art.arcane.adapt.api.mutation.upgrade.UpgradeLoans;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillGolemancy;
import art.arcane.adapt.content.skill.golemancy.CopperCare;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.math.M;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityRegainHealthEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.fx.ParticleSpec;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class GolemMending extends SimpleAdaptation<GolemMending.Config> {
  private static final Id IRON_GOLEM = Id.minecraft("iron_golem");
  private static final Id IRON_INGOT = Id.minecraft("iron_ingot");
  private static final Id RESISTANCE = Id.minecraft("resistance");
  private static final long SWEEP_INTERVAL_MILLIS = 5_000L;
  private static final long REPAIR_WINDOW_MILLIS = 1_000L;
  private static final long MILLIS_PER_SECOND = 1_000L;
  private static final int TICKS_PER_SECOND = 20;

  private volatile GolemMend.Mender mender;
  private final Map<UUID, PendingRepair> repairs = new ConcurrentHashMap<>();

  public GolemMending() {
    super("golem-mending");
    registerConfiguration(Config.class);
    setLocalizationKey("golemancy.mending");
    setIcon(Id.minecraft("glistering_melon_slice"));
    setInterval(SWEEP_INTERVAL_MILLIS);
  }

  @Override
  public void unregister() {
    GolemMend.Mender registered = mender;
    mender = null;
    if (registered != null) {
      GolemMend.unregister(registered);
    }
    repairs.clear();
    super.unregister();
  }

  @Override
  public void addStats(int level, StatLines v) {
    Config config = getConfig();
    statLore(v, Form.pc(MendingRules.healBonus(config.healBonusByLevel, level), 0), 1);
    unlockLore(v, GolemancyMessages.MENDING_LORE2);
    boolean ownerRegen = GolemancyRules.unlocked(level, config.ownerRegenLevel);
    boolean homeRegen = GolemancyRules.unlocked(level, config.homeRegenLevel);
    if (ownerRegen) {
      statLore(v, config.ownerRegenAmount, 3);
    }
    if (homeRegen) {
      statLore(v, config.homeRegenAmount, 4);
    }
    if (ownerRegen || homeRegen) {
      statLore(v, C.YELLOW, "* ", config.regenIntervalSeconds, 5);
    }
    if (GolemancyRules.unlocked(level, config.shatterLevel)) {
      unlockLore(v, GolemancyMessages.MENDING_LORE6);
    }
    if (GolemancyRules.unlocked(level, config.rebornLevel)) {
      statLore(v, config.rebornResistanceSeconds, 7);
      unlockLore(v, GolemancyMessages.MENDING_LORE8);
    }
  }

  @Override
  public boolean hasTickDemand() {
    return !repairs.isEmpty();
  }

  @Override
  public void onTick() {
    long now = M.ms();
    repairs.values().removeIf(repair -> now >= repair.until());
  }

  @Override
  public int getActiveLevel(PlayerRef p) {
    return UpgradeLoans.level(this, super.getActiveLevel(p), (Adaptation<?> upgrade) -> upgrade.getActiveLevel(p));
  }

  @Override
  public int getLevel(AdaptPlayer adaptPlayer) {
    return UpgradeLoans.level(this, super.getLevel(adaptPlayer), (Adaptation<?> upgrade) -> upgrade.getLevel(adaptPlayer));
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerInteractEntityEvent e) {
    PlayerRef player = e.player();
    Hand hand = e.hand();
    if (!(e.clicked() instanceof LivingRef target) || !Tasks.isOwnedByCurrentRegion(target)) {
      return;
    }
    ItemRef held = player.inventory().hand(hand);
    if (held.isEmpty()) {
      return;
    }
    if (hand == Hand.MAIN_HAND && player.isSneaking() && target instanceof MobRef golem && rebuildable(golem)
        && GolemItems.kindOf(held).filter(kind -> kind == GolemItemKind.SHATTERED_CORE).isPresent()) {
      e.setCancelled(true);
      rebuild(player, golem, held);
      return;
    }
    if (GolemItems.isGolemItem(held)) {
      return;
    }
    MendingRules.Remedy.of(held.type())
        .filter(remedy -> mendable(player, target, remedy))
        .ifPresent(remedy -> mend(e, player, target, remedy));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void onIngotRepair(PlayerInteractEntityEvent e) {
    if (!(e.clicked() instanceof MobRef golem) || !IRON_GOLEM.equals(golem.type()) || !IRON_INGOT.equals(SkillGolemancy.heldType(e.player(), e.hand()))) {
      return;
    }
    Optional<GolemHandle> bound = GolemancyRuntime.handle(golem);
    if (bound.isEmpty()) {
      return;
    }
    double bonus = MendingRules.ingotBonus(getConfig().healBonusByLevel, ownerLevel(bound.get()));
    if (bonus <= 0D) {
      return;
    }
    UUID golemId = golem.uuid();
    PendingRepair repair = new PendingRepair(bonus, M.ms() + REPAIR_WINDOW_MILLIS);
    repairs.put(golemId, repair);
    if (!Tasks.runEntity(golem, () -> repairs.remove(golemId, repair), 1)) {
      repairs.remove(golemId, repair);
    }
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityRegainHealthEvent e) {
    if (e.reason() != EntityRegainHealthEvent.Reason.CUSTOM || repairs.isEmpty()) {
      return;
    }
    PendingRepair repair = repairs.remove(e.entity().uuid());
    if (repair != null && M.ms() < repair.until()) {
      e.setAmount(e.amount() + repair.bonus());
    }
  }

  @Override
  protected void onRuntimeActivated() {
    GolemMend.Mender next = new Mender();
    mender = next;
    GolemMend.register(next);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.copperIngotHeal = GolemancyRules.nonNegative(loadedConfig.copperIngotHeal);
    loadedConfig.snowBlockHeal = GolemancyRules.nonNegative(loadedConfig.snowBlockHeal);
    loadedConfig.boneMealHeal = GolemancyRules.nonNegative(loadedConfig.boneMealHeal);
    loadedConfig.ownerRegenAmount = GolemancyRules.nonNegative(loadedConfig.ownerRegenAmount);
    loadedConfig.ownerRegenRadius = GolemancyRules.nonNegative(loadedConfig.ownerRegenRadius);
    loadedConfig.homeRegenAmount = GolemancyRules.nonNegative(loadedConfig.homeRegenAmount);
    loadedConfig.homeRegenRadius = GolemancyRules.nonNegative(loadedConfig.homeRegenRadius);
    loadedConfig.regenIntervalSeconds = Math.max(1, loadedConfig.regenIntervalSeconds);
    loadedConfig.rebornResistanceSeconds = Math.max(0, loadedConfig.rebornResistanceSeconds);
    loadedConfig.rebornResistanceAmplifier = Math.max(0, Math.min(MendingRules.MAX_AMPLIFIER, loadedConfig.rebornResistanceAmplifier));
  }

  int ownerLevel(GolemHandle handle) {
    return ownerLevel(handle.owner());
  }

  boolean ownerOnline(GolemHandle handle) {
    return getServer().getOnlineAdaptPlayer(handle.owner()) != null;
  }

  Optional<GolemRoster> roster(PlayerRef player) {
    AdaptPlayer adaptPlayer = getServer().getOnlineAdaptPlayer(player.uuid());
    return adaptPlayer == null ? Optional.empty() : Optional.of(adaptPlayer.getData().getGolemancy());
  }

  Optional<GolemAnimusBinding> binding() {
    Skill<?> skill = getSkill();
    if (skill == null) {
      return Optional.empty();
    }
    for (Adaptation<?> sibling : skill.getAdaptations()) {
      if (sibling instanceof GolemAnimusBinding binding) {
        return Optional.of(binding);
      }
    }
    return Optional.empty();
  }

  void applyRebornBoon(MobRef golem, GolemSnapshot snapshot) {
    Config config = getConfig();
    golem.setWeathering(snapshot.copperStage(), snapshot.waxed());
    CopperCare.restored(golem, snapshot.copperStage());
    int ticks = config.rebornResistanceSeconds * TICKS_PER_SECOND;
    if (ticks > 0) {
      golem.addEffect(new EffectInstance(RESISTANCE, ticks, config.rebornResistanceAmplifier, false, true, true));
    }
  }

  void mendFx(LivingRef target, MendingRules.Remedy remedy) {
    fx(target, FxPriority.GAMEPLAY)
        .particle(Particles.HEART, 3, 0D, target.height() * 0.6D, 0D, 0.35D, 0D)
        .burst(remedyParticle(remedy), 8, 0.35D)
        .sound(remedySound(remedy), 0.8F, 1.1F)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.6F);
  }

  void rebornFx(MobRef golem) {
    fx(golem, FxPriority.TRANSITION)
        .burst(Particles.END_ROD, 24, 0.5D)
        .burst(Particles.TOTEM, 16, 0.4D)
        .sound(Sounds.BLOCK_BEACON_POWER_SELECT, 0.6F, 1.4F)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8F, 1.2F);
  }

  void crumbleFx(PlayerRef player) {
    fx(player, FxPriority.AMBIENT)
        .burst(Particles.SMOKE, 10, 0.3D)
        .sound(Sounds.BLOCK_FIRE_EXTINGUISH, 0.5F, 1.4F);
  }

  void shatterFx(Loc at) {
    fx(at, FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 16, 0.5D)
        .burst(Particles.ASH, 12, 0.5D)
        .sound(Sounds.BLOCK_GLASS_BREAK, 0.9F, 0.6F);
  }

  private boolean mendable(PlayerRef player, LivingRef target, MendingRules.Remedy remedy) {
    UUID playerId = player.uuid();
    if (remedy.body() == null) {
      return TragoulSkeletalServant.isServant(target) ? playerId.equals(TragoulSkeletalServant.getServantOwnerId(target))
          : GolemancyRuntime.handle(target).filter(handle -> handle.body() == GolemBody.BONE && playerId.equals(handle.owner())).isPresent();
    }
    return GolemancyRuntime.handle(target).filter(handle -> handle.body() == remedy.body() && playerId.equals(handle.owner())).isPresent();
  }

  private void mend(PlayerInteractEntityEvent e, PlayerRef player, LivingRef target, MendingRules.Remedy remedy) {
    int level = getActiveLevel(player);
    if (level < 1) {
      return;
    }
    e.setCancelled(true);
    if (GolemVitals.fullHealth(target)) {
      warn(player, GolemancyMessages.MENDING_FULL_HEALTH);
      return;
    }
    double healed = GolemVitals.heal(target, MendingRules.healAmount(remedy.base(getConfig()), getConfig().healBonusByLevel, level), EntityRegainHealthEvent.Reason.CUSTOM);
    if (healed <= 0D) {
      return;
    }
    if (player.gameMode() != GameMode.CREATIVE) {
      consumeOne(player.inventory(), e.hand());
    }
    mendFx(target, remedy);
    UpgradeLoans.landed(this, player);
    if (remedy.body() != null) {
      GolemRepairXp.repaired(player, target, healed);
    }
  }

  private void rebuild(PlayerRef player, MobRef golem, ItemRef held) {
    if (!MendingRules.withinReach(player.eyeLocation().position(), golem.boundingBox())) {
      return;
    }
    PackedGolem item = GolemItems.packedGolemOf(held).orElse(null);
    GolemSnapshot snapshot = item == null ? null : GolemSnapshot.fromJson(item.state())
        .filter(read -> read.id().equals(item.golemId()) && read.owner().equals(item.owner()))
        .orElse(null);
    Rebuild rebuild = new Rebuild(player, golem, item, snapshot, GolemancyRuntime.registry().orElse(null), getActiveLevel(player));
    RebuildVerdict verdict = MendingRules.rebuildVerdict(assess(rebuild));
    switch (verdict) {
      case READY -> reborn(rebuild);
      case VOID -> crumble(player, held);
      default -> deny(player, verdict, snapshot);
    }
  }

  private RebuildCheck assess(Rebuild rebuild) {
    PlayerRef player = rebuild.player();
    MobRef golem = rebuild.golem();
    PackedGolem item = rebuild.item();
    GolemSnapshot snapshot = rebuild.snapshot();
    GolemRegistry registry = rebuild.registry();
    UUID playerId = player.uuid();
    GolemAnimusBinding binding = binding().orElse(null);
    Optional<GolemRoster> roster = registry == null || binding == null ? Optional.empty() : roster(player);
    Optional<GolemRoster.GolemEntry> entry = item == null ? Optional.empty() : roster.flatMap(known -> known.golem(item.golemId()));
    GolemRegistry.BindBlock block = registry == null || snapshot == null ? null : registry.restoreBlock(golem, snapshot).orElse(null);
    GolemBody body = snapshot == null ? GolemBody.of(golem).orElseThrow() : snapshot.body();
    boolean exclusive = binding != null && binding.builderExclusive(golem, playerId, System.currentTimeMillis());
    boolean bodyUnlocked = binding != null && binding.bodyUnlocked(player, body);
    Loc at = golem.location();
    return new RebuildCheck(snapshot != null, item != null && playerId.equals(item.owner()), roster.isPresent(),
        entry.map(GolemRoster.GolemEntry::status).orElse(null), entry.filter(known -> known.holds(item.state())).isPresent(), rebuild.level(),
        getConfig().shatterLevel, block, GolemAnimusBinding.playerCreated(golem), binding != null && binding.villageGolemsAllowed(), exclusive,
        bodyUnlocked, canInteract(player, at) && canBlockPlace(player, at));
  }

  private void reborn(Rebuild rebuild) {
    PlayerRef player = rebuild.player();
    MobRef golem = rebuild.golem();
    GolemSnapshot snapshot = rebuild.snapshot();
    Optional<GolemHandle> handle;
    try {
      handle = rebuild.registry().restore(player, golem, new GolemRegistry.Restore(rebuild.item(), GolemRoster.Status.SHATTERED));
    } catch (RuntimeException failure) {
      AdaptLog.warn("Golem Mending could not rebuild golem " + snapshot.id() + " for " + player.name() + "; its Shattered Core was kept.", failure);
      warn(player, GolemancyMessages.BINDING_INTERRUPTED);
      return;
    }
    PlayerInventoryRef inventory = player.inventory();
    if (handle.isEmpty()) {
      crumble(player, inventory.mainHand());
      return;
    }
    if (GolemancyRules.unlocked(rebuild.level(), getConfig().rebornLevel)) {
      applyRebornBoon(golem, snapshot);
    }
    handle.get().restoreFullHealth();
    decrementItemstack(inventory.mainHand(), player);
    player.saveData();
    rebornFx(golem);
    notify(player, GolemancyMessages.MENDING_REBORN, trusted("body", AdaptLanguage.text(snapshot.body().displayName())));
  }

  private void crumble(PlayerRef player, ItemRef held) {
    decrementItemstack(held, player);
    crumbleFx(player);
    warn(player, GolemancyMessages.MENDING_VOID);
  }

  private void deny(PlayerRef player, RebuildVerdict verdict, GolemSnapshot snapshot) {
    String body = snapshot == null ? "" : AdaptLanguage.text(snapshot.body().displayName());
    String core = snapshot == null ? "" : AdaptLanguage.text(snapshot.primaryCore().displayName());
    switch (verdict) {
      case READY, VOID -> {
      }
      case UNREADABLE -> warn(player, GolemancyMessages.MENDING_UNREADABLE);
      case NOT_OWNER -> warn(player, GolemancyMessages.MENDING_NOT_OWNER);
      case UNAVAILABLE -> warn(player, GolemancyMessages.BINDING_UNAVAILABLE);
      case LOCKED -> warn(player, GolemancyMessages.MENDING_LOCKED, trusted("level", getConfig().shatterLevel));
      case ALREADY_BOUND -> warn(player, GolemancyMessages.BINDING_ALREADY_BOUND);
      case BODY_MISMATCH -> warn(player, GolemancyMessages.MENDING_WRONG_BODY, trusted("body", body));
      case VILLAGE_GOLEM -> warn(player, GolemancyMessages.BINDING_VILLAGE);
      case NOT_BUILDER -> warn(player, GolemancyMessages.BINDING_BUILDER);
      case BODY_LOCKED -> warn(player, GolemancyMessages.BINDING_BODY_LOCKED, trusted("body", body));
      case CORE_NOT_ALLOWED -> warn(player, GolemancyMessages.BINDING_CORE_NOT_ALLOWED, trusted("body", body), trusted("core", core));
      case PROTECTED -> warn(player, GolemancyMessages.BINDING_PROTECTED);
      case CHUNK_CAP -> warn(player, GolemancyMessages.BINDING_CHUNK_CAP, trusted("count", GolemancyRuntime.settings().maxBoundGolemsPerChunk()));
    }
  }

  private static boolean rebuildable(MobRef golem) {
    return GolemBody.of(golem).filter(body -> body != GolemBody.BONE).isPresent();
  }

  private static void consumeOne(PlayerInventoryRef inventory, Hand hand) {
    ItemRef held = inventory.hand(hand);
    if (held.isEmpty()) {
      return;
    }
    if (held.amount() > 1) {
      held.setAmount(held.amount() - 1);
      inventory.setHand(hand, held);
    } else {
      inventory.setHand(hand, AdaptCore.platform().host().items().empty());
    }
  }

  private static ParticleSpec remedyParticle(MendingRules.Remedy remedy) {
    return switch (remedy) {
      case COPPER_INGOT -> Particles.WAX_ON;
      case SNOW_BLOCK -> Particles.SNOWFLAKE;
      case BONE_MEAL -> Particles.VILLAGER_HAPPY;
    };
  }

  private static Id remedySound(MendingRules.Remedy remedy) {
    return switch (remedy) {
      case COPPER_INGOT -> Sounds.BLOCK_COPPER_PLACE;
      case SNOW_BLOCK -> Sounds.BLOCK_SNOW_PLACE;
      case BONE_MEAL -> Sounds.ITEM_BONE_MEAL_USE;
    };
  }

  private static void notify(PlayerRef player, TextKey key, MessageArgument... arguments) {
    Tasks.runEntity(player, () -> AdaptHud.actionBar(player, C.AQUA + AdaptLanguage.text(key, arguments)));
  }

  private static void warn(PlayerRef player, TextKey key, MessageArgument... arguments) {
    Tasks.runEntity(player, () -> AdaptHud.actionBar(player, C.RED + AdaptLanguage.text(key, arguments)));
  }

  private record PendingRepair(double bonus, long until) {
  }

  private record Rebuild(PlayerRef player, MobRef golem, PackedGolem item, GolemSnapshot snapshot, GolemRegistry registry, int level) {
  }

  private final class Mender implements GolemMend.Mender {
    @Override
    public long regenIntervalMillis() {
      return Math.max(1, getConfig().regenIntervalSeconds) * MILLIS_PER_SECOND;
    }

    @Override
    public double regen(GolemHandle handle, GolemMend.RegenSenses senses) {
      double reach = GolemLoans.strength(GolemMending.this, handle.owner());
      GolemMend.RegenSenses reached = reach > 1D
          ? new GolemMend.RegenSenses(senses.ownerDistance() / reach, senses.homeDistance(), senses.idle())
          : senses;
      return MendingRules.regen(getConfig(), ownerLevel(handle), reached);
    }

    @Override
    public boolean shatters(GolemHandle handle) {
      return MendingRules.shatters(handle.body(), handle.isValidated(), ownerOnline(handle), ownerLevel(handle), getConfig().shatterLevel);
    }

    @Override
    public void shattered(GolemHandle handle, Loc at) {
      shatterFx(at);
      AdaptPlayer owner = getServer().getOnlineAdaptPlayer(handle.owner());
      if (owner != null) {
        warn(owner.getPlayer(), GolemancyMessages.MENDING_SHATTERED, trusted("body", AdaptLanguage.text(handle.body().displayName())));
      }
    }
  }

  @ConfigDescription("Heal your golems with copper ingots, snow blocks and bone meal, let them mend near you and at home, and rebuild fallen golems from their Shattered Core.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Fraction added to every heal you give your golems and servants with items, by Golem Mending level from level 1 upward. 0.2 heals 20% more; an iron ingot on a bound iron golem gains this fraction of the vanilla 25.", impact = "Higher values make item heals stronger.")
    List<Double> healBonusByLevel = new ArrayList<>(List.of(0.2, 0.4, 0.6, 0.8, 1.0));
    @ConfigDoc(value = "Health a copper ingot restores to your bound copper golem before the level bonus. Values below 0 load as 0.", impact = "Higher values make copper ingots heal more.")
    double copperIngotHeal = 4;
    @ConfigDoc(value = "Health a snow block restores to your bound frost golem before the level bonus. Values below 0 load as 0.", impact = "Higher values make snow blocks heal more.")
    double snowBlockHeal = 2;
    @ConfigDoc(value = "Health bone meal restores to your TragOul skeletal servant before the level bonus. Values below 0 load as 0.", impact = "Higher values make bone meal heal more.")
    double boneMealHeal = 2;
    @ConfigDoc(value = "Golem Mending level at which your awake golems regenerate while you are near them.", impact = "Lower values unlock regeneration near you sooner.")
    int ownerRegenLevel = 2;
    @ConfigDoc(value = "Health your golems regenerate each regenIntervalSeconds while they are within ownerRegenRadius blocks of you. Values below 0 load as 0.", impact = "Higher values heal golems near you faster.")
    double ownerRegenAmount = 0.5;
    @ConfigDoc(value = "Blocks from you, in the same world, within which your golems regenerate. Values below 0 load as 0.", impact = "Higher values let golems farther from you regenerate.")
    double ownerRegenRadius = 16;
    @ConfigDoc(value = "Golem Mending level at which your idle golems regenerate at home.", impact = "Lower values unlock regeneration at home sooner.")
    int homeRegenLevel = 3;
    @ConfigDoc(value = "Health your golems regenerate each regenIntervalSeconds while they are idle, with no job and no target, within homeRegenRadius blocks of their home. When both regenerations apply, only the larger one heals. Values below 0 load as 0.", impact = "Higher values heal idle golems at home faster.")
    double homeRegenAmount = 1.0;
    @ConfigDoc(value = "Blocks from its home within which an idle golem regenerates. Values below 0 load as 0.", impact = "Higher values let idle golems regenerate farther from home.")
    double homeRegenRadius = 4;
    @ConfigDoc(value = "Seconds between regeneration heals. Values below 1 load as 1.", impact = "Lower values make regeneration heal more often.")
    int regenIntervalSeconds = 5;
    @ConfigDoc(value = "Golem Mending level at which your bound golems leave a Shattered Core when they die while you are online.", impact = "Lower values unlock the Shattered Core sooner.")
    int shatterLevel = 4;
    @ConfigDoc(value = "Golem Mending level at which a golem rebuilt from its Shattered Core keeps its copper oxidation stage and wax and gains Resistance.", impact = "Lower values unlock the reborn bonus sooner.")
    int rebornLevel = 5;
    @ConfigDoc(value = "Seconds of Resistance a golem rebuilt from its Shattered Core gains at rebornLevel. Values below 0 load as 0, which gives none.", impact = "Higher values protect reborn golems longer.")
    int rebornResistanceSeconds = 10;
    @ConfigDoc(value = "Amplifier of the Resistance a reborn golem gains, where 0 is Resistance I and 1 is Resistance II. Values load between 0 and 255.", impact = "Higher values make reborn golems take less damage.")
    int rebornResistanceAmplifier = 1;

    public Config() {
      costFactor = 0.5;
      initialCost = 1;
      baseCost = 2;
      requires = new ArrayList<>(List.of("golem-animus-binding:1"));
    }
  }
}
