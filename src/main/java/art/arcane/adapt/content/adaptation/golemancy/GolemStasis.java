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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemPacking;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemScale;
import art.arcane.adapt.api.golem.GolemSnapshot;
import art.arcane.adapt.api.golem.GolemVitals;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.DormantGolem;
import art.arcane.adapt.api.golem.item.DormantGolemPlacement;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.item.OwnerBoundDrop;
import art.arcane.adapt.api.golem.item.PackedGolem;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.runtime.AdaptationGate;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockState;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class GolemStasis extends SimpleAdaptation<GolemStasis.Config> {
  private static final double RISE_FROM_SCALE = 0.1D;
  private static final long MILLIS_PER_SECOND = 1_000L;
  private static final int TICKS_PER_SECOND = 20;
  private static final int CHANNEL_SOUND_INTERVAL_TICKS = 10;

  private final Map<UUID, PackChannel> channelsByGolem = new ConcurrentHashMap<>();
  private final Map<UUID, PackChannel> channelsByOwner = new ConcurrentHashMap<>();
  private final RecallCooldowns recalls = new RecallCooldowns();
  private volatile GolemPacking.Packer packer;

  public GolemStasis() {
    super("golem-stasis");
    registerConfiguration(Config.class);
    setLocalizationKey("golemancy.stasis");
    setIcon(Material.CARVED_PUMPKIN);
  }

  @Override
  public void unregister() {
    GolemPacking.Packer registered = packer;
    packer = null;
    if (registered != null) {
      GolemPacking.unregister(registered);
    }
    for (PackChannel channel : List.copyOf(channelsByGolem.values())) {
      onGolemThread(channel, () -> abort(channel, false));
    }
    recalls.clear();
    super.unregister();
  }

  @Override
  protected void onRuntimeActivated() {
    GolemPacking.Packer next = new Packer();
    packer = next;
    GolemPacking.register(next);
  }

  @Override
  protected List<Listener> createCompanionListeners() {
    return List.of(new DormantGolemPlacement(this::awaken));
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.channelSecondsByLevel = StasisRules.clampChannelSeconds(loadedConfig.channelSecondsByLevel);
    loadedConfig.channelAbortDistance = nonNegative(loadedConfig.channelAbortDistance);
    loadedConfig.packedHealAmount = nonNegative(loadedConfig.packedHealAmount);
    loadedConfig.packedHealIntervalSeconds = Math.max(1, loadedConfig.packedHealIntervalSeconds);
    loadedConfig.recallCooldownSeconds = Math.max(0, loadedConfig.recallCooldownSeconds);
    loadedConfig.awakenRiseTicks = Math.max(0, loadedConfig.awakenRiseTicks);
  }

  @Override
  public void addStats(int level, Element v) {
    Config config = getConfig();
    int channelTicks = StasisRules.channelTicks(config.channelSecondsByLevel, level);
    if (channelTicks > 0) {
      statLore(v, C.YELLOW, "* ", (double) channelTicks / TICKS_PER_SECOND, 1);
    } else {
      unlockLore(v, GolemancyMessages.STASIS_LORE2);
    }
    unlockLore(v, GolemancyMessages.STASIS_LORE3);
    if (StasisRules.unlocked(level, config.packedHealLevel)) {
      statLore(v, config.packedHealAmount, 4);
      statLore(v, C.YELLOW, "* ", config.packedHealIntervalSeconds, 5);
    }
    if (StasisRules.unlocked(level, config.recallLevel)) {
      unlockLore(v, GolemancyMessages.STASIS_LORE6);
      statLore(v, C.YELLOW, "* ", config.recallCooldownSeconds, 7);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    PackChannel channel = channelsByGolem.get(e.getEntity().getUniqueId());
    if (channel != null) {
      abort(channel, true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    PackChannel channel = channelsByOwner.get(e.getPlayer().getUniqueId());
    if (channel != null) {
      onGolemThread(channel, () -> abort(channel, false));
    }
  }

  boolean packs(Player player) {
    return getActiveLevel(player) >= 1;
  }

  boolean recalls(Player player) {
    return StasisRules.unlocked(getActiveLevel(player), getConfig().recallLevel);
  }

  void pack(Player player, GolemHandle handle) {
    int level = getActiveLevel(player);
    if (level < 1 || !player.getUniqueId().equals(handle.owner())) {
      return;
    }
    if (busy(player.getUniqueId(), handle.entity().getUniqueId(), System.currentTimeMillis())) {
      warn(player, GolemancyMessages.STASIS_BUSY);
      return;
    }
    int ticks = StasisRules.channelTicks(getConfig().channelSecondsByLevel, level);
    if (ticks > 0) {
      channel(player, handle, ticks);
      return;
    }
    packOnGolemThread(player, handle, () -> {
    });
  }

  void recall(Player player, GolemHandle handle) {
    Config config = getConfig();
    UUID owner = player.getUniqueId();
    if (!StasisRules.unlocked(getActiveLevel(player), config.recallLevel) || !owner.equals(handle.owner())) {
      return;
    }
    if (handle.isRetired() || !player.getWorld().equals(handle.entity().getWorld())) {
      warn(player, GolemancyMessages.STASIS_RECALL_UNAVAILABLE);
      return;
    }
    long now = System.currentTimeMillis();
    if (!recalls.tryStart(owner, now, config.recallCooldownSeconds * MILLIS_PER_SECOND)) {
      long remaining = recalls.remainingMillis(owner, now);
      warn(player, GolemancyMessages.STASIS_RECALL_COOLDOWN, trusted("seconds", (remaining + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND));
      return;
    }
    packOnGolemThread(player, handle, () -> recalls.cancel(owner));
  }

  void channel(Player player, GolemHandle handle, int ticks) {
    PackChannel channel = new PackChannel(player, handle, ticks, System.currentTimeMillis());
    channelsByOwner.put(player.getUniqueId(), channel);
    channelsByGolem.put(handle.entity().getUniqueId(), channel);
    channel.attach(channelFx(channel));
    schedulePulse(channel);
  }

  void awaken(BlockPlaceEvent e) {
    Player player = e.getPlayer();
    PackedGolem item = GolemItems.packedGolemOf(e.getItemInHand()).orElse(null);
    GolemSnapshot snapshot = item == null ? null : GolemSnapshot.fromJson(item.state())
        .filter(read -> read.id().equals(item.golemId()) && read.owner().equals(item.owner()))
        .orElse(null);
    Location at = e.getBlockPlaced().getLocation().add(0.5D, 0D, 0.5D);
    at.setYaw(player.getLocation().getYaw() + 180F);
    at.setPitch(0F);
    Awaken awaken = new Awaken(player, e.getHand(), item, snapshot, at, GolemancyRuntime.registry().orElse(null), getActiveLevel(player));
    AwakenVerdict verdict = StasisRules.awakenVerdict(assess(awaken, e));
    switch (verdict) {
      case READY -> deploy(awaken);
      case VOID -> crumble(awaken);
      default -> deny(player, verdict, snapshot);
    }
  }

  Optional<GolemRoster> roster(Player player) {
    AdaptPlayer adaptPlayer = getServer().getOnlineAdaptPlayer(player.getUniqueId());
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

  boolean spaceClear(BlockPlaceEvent e) {
    BlockState replaced = e.getBlockReplacedState();
    return replaced.getBlockData().getCollisionShape(e.getBlockPlaced().getLocation()).getBoundingBoxes().isEmpty();
  }

  boolean worldAllows(Player player, Location at) {
    return !AdaptationGate.shouldSkipWorld(at.getWorld(), getSkill()) && canBlockPlace(player, at);
  }

  FxTimeline channelFx(PackChannel channel) {
    Mob golem = channel.golem();
    Particle bodyParticle = bodyParticle(channel.handle().body());
    double height = golem.getHeight();
    FxTimeline timeline = timeline(golem)
        .duration(channel.durationTicks())
        .priority(FxPriority.TRANSITION)
        .cullRadius(24D)
        .frame((fx, tick, progress) -> {
          fx.helix(Particle.REVERSE_PORTAL, 0.15D + 1.1D * (1D - progress), height, 3, -tick * 0.5D);
          fx.particle(bodyParticle, 1, 0D, height * 0.5D, 0D, 0.25D, 0.01D);
          if (tick % CHANNEL_SOUND_INTERVAL_TICKS == 0) {
            fx.sound(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.7F, 0.8F + ((float) progress * 0.8F));
          }
        });
    timeline.start();
    return timeline;
  }

  void packFx(Location at, GolemBody body) {
    fx(at, FxPriority.TRANSITION)
        .burst(Particle.REVERSE_PORTAL, 24, 0.5D)
        .burst(bodyParticle(body), 10, 0.4D)
        .sound(Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8F, 1.4F);
  }

  void awakenFx(Mob golem) {
    fx(golem, FxPriority.TRANSITION)
        .burst(Particles.END_ROD, 20, 0.5D)
        .burst(Particle.REVERSE_PORTAL, 16, 0.4D)
        .sound(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8F, 1.2F)
        .sound(Sound.BLOCK_BEACON_ACTIVATE, 0.5F, 1.6F);
  }

  void crumbleFx(Player player) {
    fx(player, FxPriority.AMBIENT)
        .burst(Particles.SMOKE, 10, 0.3D)
        .sound(Sound.BLOCK_FIRE_EXTINGUISH, 0.5F, 1.4F);
  }

  void rise(Mob golem, GolemBody body) {
    int ticks = getConfig().awakenRiseTicks;
    if (ticks <= 0) {
      return;
    }
    double target = GolemScale.target(body);
    GolemScale.stepTo(golem, RISE_FROM_SCALE, target, 0D);
    scheduleRise(golem, target, 1, ticks);
  }

  private void scheduleRise(Mob golem, double target, int step, int ticks) {
    J.runEntity(golem, () -> riseStep(golem, target, step, ticks), 1);
  }

  private void riseStep(Mob golem, double target, int step, int ticks) {
    if (!golem.isValid()) {
      return;
    }
    double progress = (double) step / ticks;
    GolemScale.stepTo(golem, RISE_FROM_SCALE, target, progress);
    fx(golem, FxPriority.AMBIENT).ring(Particles.END_ROD, 0.6D, 6, golem.getHeight() * progress);
    if (step >= ticks) {
      GolemScale.clearStep(golem);
      return;
    }
    scheduleRise(golem, target, step + 1, ticks);
  }

  private void schedulePulse(PackChannel channel) {
    if (!J.runEntity(channel.golem(), () -> pulse(channel), 1, () -> abort(channel, true))) {
      abort(channel, true);
    }
  }

  private void pulse(PackChannel channel) {
    if (channel.isFinished()) {
      return;
    }
    channel.pulsed(System.currentTimeMillis());
    if (!holds(channel)) {
      abort(channel, true);
      return;
    }
    channel.advance();
    if (channel.complete()) {
      complete(channel);
      return;
    }
    schedulePulse(channel);
  }

  private boolean holds(PackChannel channel) {
    Mob golem = channel.golem();
    Player owner = channel.owner();
    return golem.isValid() && !golem.isDead() && !channel.handle().isRetired()
        && owner.isOnline() && owner.isValid() && !owner.isDead()
        && J.isOwnedByCurrentRegion(owner)
        && channel.withinReach(getConfig().channelAbortDistance)
        && channel.stillHeld();
  }

  private void complete(PackChannel channel) {
    if (!channel.finish()) {
      return;
    }
    forget(channel);
    packNow(channel.owner(), channel.handle(), () -> {
    });
  }

  private void abort(PackChannel channel, boolean announce) {
    if (!channel.finish()) {
      return;
    }
    forget(channel);
    if (announce && channel.owner().isOnline()) {
      warn(channel.owner(), GolemancyMessages.STASIS_INTERRUPTED);
    }
  }

  private void forget(PackChannel channel) {
    channelsByOwner.remove(channel.owner().getUniqueId(), channel);
    channelsByGolem.remove(channel.golem().getUniqueId(), channel);
  }

  private boolean busy(UUID owner, UUID golem, long now) {
    return active(channelsByOwner, owner, now) || active(channelsByGolem, golem, now);
  }

  private boolean active(Map<UUID, PackChannel> channels, UUID key, long now) {
    PackChannel existing = channels.get(key);
    if (existing == null) {
      return false;
    }
    if (existing.isFinished()) {
      forget(existing);
      return false;
    }
    if (!existing.stale(now)) {
      return true;
    }
    onGolemThread(existing, () -> abort(existing, false));
    return false;
  }

  private void onGolemThread(PackChannel channel, Runnable task) {
    Mob golem = channel.golem();
    if (J.isOwnedByCurrentRegion(golem)) {
      task.run();
    } else if (!J.runEntity(golem, task, () -> lose(channel))) {
      lose(channel);
    }
  }

  private void lose(PackChannel channel) {
    channel.finish();
    forget(channel);
  }

  private void packOnGolemThread(Player player, GolemHandle handle, Runnable refused) {
    AtomicBoolean claimed = new AtomicBoolean();
    Runnable gone = () -> {
      if (claimed.compareAndSet(false, true)) {
        refused.run();
        warn(player, GolemancyMessages.MESSAGE_GONE);
      }
    };
    Runnable packing = () -> {
      if (claimed.compareAndSet(false, true)) {
        packNow(player, handle, refused);
      }
    };
    if (!J.runEntity(handle.entity(), packing, gone)) {
      gone.run();
    }
  }

  private void packNow(Player player, GolemHandle handle, Runnable refused) {
    GolemRegistry registry = GolemancyRuntime.registry().orElse(null);
    Optional<GolemRegistry.Packing> packing = Optional.empty();
    if (registry != null && !handle.isRetired()) {
      try {
        packing = registry.pack(handle);
      } catch (RuntimeException failure) {
        Adapt.warn("Stasis could not pack golem " + handle.id() + " for " + player.getName() + ".", failure);
      }
    }
    if (packing.isEmpty()) {
      refused.run();
      warn(player, GolemancyMessages.STASIS_PACK_FAILED);
      return;
    }
    GolemRegistry.Packing packed = packing.get();
    GolemancyRuntime.reveal().ifPresent(reveal -> reveal.forget(handle));
    GolemSnapshot snapshot = packed.snapshot();
    packFx(packed.at(), snapshot.body());
    ItemStack item = GolemItems.dormantGolem(new DormantGolem(packed.item(), GolemItems.dormantBlock(snapshot.body(), snapshot.copperStage(), snapshot.waxed()),
        snapshot.cores(), snapshot.runes(), snapshot.health(), packed.maxHealth()));
    handOver(player, item, packed.at(), snapshot.body());
  }

  private void handOver(Player player, ItemStack item, Location at, GolemBody body) {
    UUID owner = player.getUniqueId();
    AtomicBoolean settled = new AtomicBoolean();
    Runnable give = () -> {
      if (!settled.compareAndSet(false, true)) {
        return;
      }
      boolean dropped = give(player, item);
      player.saveData();
      notify(player, dropped ? GolemancyMessages.STASIS_DROPPED : GolemancyMessages.STASIS_PACKED, trusted("body", AdaptLanguage.text(body.displayName())));
    };
    Runnable drop = () -> {
      if (settled.compareAndSet(false, true)) {
        dropAt(at, item, owner);
      }
    };
    if (!J.runEntity(player, give, drop)) {
      drop.run();
    }
  }

  private AwakenCheck assess(Awaken awaken, BlockPlaceEvent e) {
    Player player = awaken.player();
    PackedGolem item = awaken.item();
    GolemSnapshot snapshot = awaken.snapshot();
    GolemRegistry registry = awaken.registry();
    GolemAnimusBinding binding = binding().orElse(null);
    Optional<GolemRoster> roster = registry == null || binding == null ? Optional.empty() : roster(player);
    Optional<GolemRoster.GolemEntry> entry = item == null ? Optional.empty() : roster.flatMap(known -> known.golem(item.golemId()));
    GolemRegistry.BindBlock block = registry == null || snapshot == null ? null : registry.awakenBlock(awaken.at(), snapshot).orElse(null);
    boolean bodyUnlocked = binding != null && snapshot != null && binding.bodyUnlocked(player, snapshot.body());
    return new AwakenCheck(snapshot != null, item != null && player.getUniqueId().equals(item.owner()), awaken.level(), roster.isPresent(),
        entry.map(GolemRoster.GolemEntry::status).orElse(null), entry.filter(known -> known.holds(item.state())).isPresent(), bodyUnlocked, block,
        spaceClear(e), worldAllows(player, awaken.at()));
  }

  private void deploy(Awaken awaken) {
    Player player = awaken.player();
    GolemSnapshot snapshot = awaken.snapshot();
    Optional<GolemHandle> handle;
    try {
      handle = awaken.registry().awaken(player, awaken.at(), new GolemRegistry.Restore(awaken.item(), GolemRoster.Status.PACKED),
          body -> prepare(body, snapshot));
    } catch (RuntimeException failure) {
      Adapt.warn("Stasis could not awaken golem " + snapshot.id() + " for " + player.getName() + "; its Dormant Golem was kept.", failure);
      warn(player, GolemancyMessages.STASIS_AWAKEN_FAILED);
      return;
    }
    if (handle.isEmpty()) {
      crumble(awaken);
      return;
    }
    Mob golem = handle.get().entity();
    golem.setHealth(awakenHealth(snapshot, GolemVitals.maxHealth(golem), awaken.level()));
    consumeOne(player.getInventory(), awaken.hand(), awaken.item());
    player.saveData();
    rise(golem, snapshot.body());
    awakenFx(golem);
    notify(player, GolemancyMessages.STASIS_AWAKENED, trusted("body", AdaptLanguage.text(snapshot.body().displayName())));
  }

  private double awakenHealth(GolemSnapshot snapshot, double maximum, int level) {
    Config config = getConfig();
    double amount = StasisRules.unlocked(level, config.packedHealLevel) ? config.packedHealAmount : 0D;
    return StasisRules.packedHealth(snapshot.health(), maximum, snapshot.takenAt(), System.currentTimeMillis(), config.packedHealIntervalSeconds, amount);
  }

  private void crumble(Awaken awaken) {
    Player player = awaken.player();
    consumeOne(player.getInventory(), awaken.hand(), awaken.item());
    crumbleFx(player);
    warn(player, GolemancyMessages.STASIS_VOID);
  }

  private void deny(Player player, AwakenVerdict verdict, GolemSnapshot snapshot) {
    String body = snapshot == null ? "" : AdaptLanguage.text(snapshot.body().displayName());
    String core = snapshot == null ? "" : AdaptLanguage.text(snapshot.primaryCore().displayName());
    switch (verdict) {
      case READY, VOID -> {
      }
      case UNREADABLE -> warn(player, GolemancyMessages.STASIS_UNREADABLE);
      case NOT_OWNER -> warn(player, GolemancyMessages.STASIS_NOT_OWNER);
      case LOCKED -> warn(player, GolemancyMessages.STASIS_LOCKED);
      case UNAVAILABLE -> warn(player, GolemancyMessages.BINDING_UNAVAILABLE);
      case BODY_LOCKED -> warn(player, GolemancyMessages.STASIS_BODY_LOCKED, trusted("body", body));
      case CORE_NOT_ALLOWED -> warn(player, GolemancyMessages.BINDING_CORE_NOT_ALLOWED, trusted("body", body), trusted("core", core));
      case NO_ROOM -> warn(player, GolemancyMessages.STASIS_NO_ROOM);
      case CHUNK_CAP -> warn(player, GolemancyMessages.BINDING_CHUNK_CAP, trusted("count", GolemancyRuntime.settings().maxBoundGolemsPerChunk()));
      case SERVER_CAP -> warn(player, GolemancyMessages.STASIS_SERVER_CAP);
      case PROTECTED -> warn(player, GolemancyMessages.STASIS_PROTECTED);
    }
  }

  private static void prepare(Mob body, GolemSnapshot snapshot) {
    PaperCompat.restoreCopper(body, snapshot.copperStage(), snapshot.waxed());
    if (body instanceof IronGolem iron) {
      iron.setPlayerCreated(true);
    }
  }

  private static boolean give(Player player, ItemStack item) {
    Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
    for (ItemStack leftover : leftovers.values()) {
      OwnerBoundDrop.drop(player.getLocation(), leftover, player.getUniqueId());
    }
    return !leftovers.isEmpty();
  }

  private static void dropAt(Location at, ItemStack item, UUID owner) {
    if (!J.runAt(at, () -> OwnerBoundDrop.drop(at, item, owner))) {
      Adapt.warn("Stasis could not drop the Dormant Golem of " + owner + " at " + at + "; the packed golem was lost.");
    }
  }

  private static void consumeOne(PlayerInventory inventory, EquipmentSlot hand, PackedGolem item) {
    ItemStack held = inventory.getItem(hand);
    if (held == null || GolemItems.packedGolemOf(held).filter(item::equals).isEmpty()) {
      return;
    }
    if (held.getAmount() > 1) {
      held.setAmount(held.getAmount() - 1);
    } else {
      inventory.setItem(hand, null);
    }
  }

  private static Particle bodyParticle(GolemBody body) {
    return switch (body) {
      case FROST -> Particle.SNOWFLAKE;
      case COPPER -> Particle.WAX_ON;
      case IRON, BONE -> Particle.ASH;
    };
  }

  private static double nonNegative(double value) {
    return Double.isFinite(value) ? Math.max(0D, value) : 0D;
  }

  private static void unlockLore(Element v, TextKey key) {
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(key));
  }

  private static void notify(Player player, TextKey key, MessageArgument... arguments) {
    J.runEntity(player, () -> AdaptHud.actionBar(player, C.AQUA + AdaptLanguage.text(key, arguments)));
  }

  private static void warn(Player player, TextKey key, MessageArgument... arguments) {
    J.runEntity(player, () -> AdaptHud.actionBar(player, C.RED + AdaptLanguage.text(key, arguments)));
  }

  private record Awaken(Player player, EquipmentSlot hand, PackedGolem item, GolemSnapshot snapshot, Location at, GolemRegistry registry, int level) {
  }

  private final class Packer implements GolemPacking.Packer {
    @Override
    public boolean packs(Player player) {
      return GolemStasis.this.packs(player);
    }

    @Override
    public void pack(Player player, GolemHandle handle) {
      GolemStasis.this.pack(player, handle);
    }

    @Override
    public boolean recalls(Player player) {
      return GolemStasis.this.recalls(player);
    }

    @Override
    public void recall(Player player, GolemHandle handle) {
      GolemStasis.this.recall(player, handle);
    }
  }

  @ConfigDescription("Pack your bound golems into Dormant Golem blocks with the Crook and place the block to wake them anywhere, with their cores, runes, cargo and health intact.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Seconds you channel to pack a golem, by Stasis level from level 1 upward. 0 packs at once. Values load between 0 and 30.", impact = "Lower values pack golems faster.")
    List<Double> channelSecondsByLevel = new ArrayList<>(List.of(2.0, 0.0, 0.0));
    @ConfigDoc(value = "Blocks you can move away from the golem before a packing channel breaks. Values below 0 load as 0.", impact = "Higher values let you move farther while packing.")
    double channelAbortDistance = 4;
    @ConfigDoc(value = "Stasis level at which packed golems heal for the time they spend packed.", impact = "Lower values unlock packed healing sooner.")
    int packedHealLevel = 2;
    @ConfigDoc(value = "Health a packed golem heals for each packedHealIntervalSeconds it spent packed, added when it awakens and never above its maximum health. Values below 0 load as 0.", impact = "Higher values wake packed golems healthier.")
    double packedHealAmount = 1;
    @ConfigDoc(value = "Seconds a golem must stay packed for each packedHealAmount of healing. Values below 1 load as 1.", impact = "Lower values heal packed golems faster.")
    int packedHealIntervalSeconds = 10;
    @ConfigDoc(value = "Stasis level at which Pocket Roster lets you recall a loaded golem in your world from the Roster GUI.", impact = "Lower values unlock Pocket Roster sooner.")
    int recallLevel = 3;
    @ConfigDoc(value = "Seconds after a Pocket Roster recall before you can recall another golem. Values below 0 load as 0.", impact = "Lower values let you recall golems more often.")
    int recallCooldownSeconds = 120;
    @ConfigDoc(value = "Ticks an awakened golem takes to rise from a tenth of a block to its bound size. 0 skips the rise.", impact = "Higher values make the rise slower.")
    int awakenRiseTicks = 20;

    public Config() {
      maxLevel = 3;
      requires = new ArrayList<>(List.of("golem-animus-binding:2"));
    }
  }
}
