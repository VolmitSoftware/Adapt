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

package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerGameModeChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.event.player.PlayerVelocityEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class BlockingBastionStance extends SimpleAdaptation<BlockingBastionStance.Config> {
  private static final Id SHIELD = Id.minecraft("shield");
  private static final Id IRON_BLOCK = Id.minecraft("iron_block");
  private static final String SLOT_KNOCKBACK = "knockback";
  private static final String SLOT_EXPLOSION = "explosion";
  private static final int STANCE_REFRESH_TICKS = 2;
  private static final double IMPACT_FX_THRESHOLD = 0.4D;
  private final Map<UUID, Boolean> activeSessions = playerState();
  private final Map<UUID, StanceState> stanceStates = playerState();

  public BlockingBastionStance() {
    super("blocking-bastion-stance");
    needs(PlatformNeed.partialAttribute(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, "no explosion knockback resistance"));
    registerConfiguration(Config.class);
    setIcon(SHIELD);
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("shield"))
        .key("challenge_blocking_bastion_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_blocking_bastion_500", "blocking.bastion-stance.projectiles-softened", 500, 500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("shield"))
        .key("challenge_blocking_bastion_10")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    endStance(player.getPlayer());
    if (getActiveLevel(player.getPlayer()) > 0) { startStance(player.getPlayer()); }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, BlockingPreferences.SNEAK_STANCE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getKnockbackReduction(level), 0), 1);
    statLore(v, Form.pc(getProjectileReduction(level), 0), 2);
    statLore(v, Form.pc(getProjectileNegateChance(level), 0), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (e.isCancelled()) {
      return;
    }

    if (!(e.entity() instanceof PlayerRef defender) || !isBastionStance(defender)) {
      return;
    }

    if (!(e.damager() instanceof ProjectileRef)) {
      return;
    }

    int level = getActiveLevel(defender);
    if (level <= 0) {
      return;
    }

    int sessionCount = getStorageInt(defender, "bastionSessionCount", 0) + 1;
    setStorage(defender, "bastionSessionCount", sessionCount);
    activeSessions.put(defender.uuid(), Boolean.TRUE);
    if (sessionCount >= 10) {
      grantOnce(defender, "challenge_blocking_bastion_10");
    }

    if (ThreadLocalRandom.current().nextDouble() <= getProjectileNegateChance(level)) {
      e.setCancelled(true);
      hardParry(defender);
      xp(defender, getConfig().xpOnNegate);
      addStat(defender, "blocking.bastion-stance.projectiles-softened", 1);
      return;
    }

    e.setDamage(Math.max(0, e.damage() * (1D - getProjectileReduction(level))));
    softenPuff(defender);
    xp(defender, e.damage() * getConfig().xpPerMitigatedDamage);
    addStat(defender, "blocking.bastion-stance.projectiles-softened", 1);
  }

  private void hardParry(PlayerRef defender) {
    Loc chest = defender.location().add(0, 1.0D, 0);
    fx(chest, FxPriority.COMBAT)
        .burst(Particles.END_ROD, 2, 0.05D)
        .chord(Sounds.ITEM_SHIELD_BLOCK, 1.0F, 0.9F, Sounds.BLOCK_ANVIL_LAND, 0.3F, 1.6F);
    timeline(chest)
        .duration(3)
        .priority(FxPriority.COMBAT)
        .cullRadius(24.0D)
        .frame((fxE, tick, progress) -> fxE.ring(Particles.CRIT_MAGIC, 1.0D - (0.7D * progress), 4, 0))
        .start();
  }

  private void softenPuff(PlayerRef defender) {
    Vec3 look = defender.eyeLocation().direction();
    Loc shield = defender.eyeLocation().add(look.x() * 0.5D, look.y() * 0.5D, look.z() * 0.5D);
    fx(shield, FxPriority.COMBAT)
        .particle(Particles.CLOUD, 5, 0, 0, 0, 0.1D, 0.02D)
        .sound(Sounds.ITEM_SHIELD_BLOCK, 0.75F, 0.75F);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerVelocityEvent e) {
    PlayerRef p = e.player();
    int level = getActiveLevel(p);
    if (!isBastionStance(p, level)) {
      return;
    }

    Vec3 v = e.velocity();
    double horizontal = Math.sqrt((v.x() * v.x()) + (v.z() * v.z()));
    if (braceImpactFx(horizontal, getKnockbackReduction(level))) {
      fx(p.location(), FxPriority.COMBAT)
          .ring(Particles.blockCrack(AdaptCore.platform().host().items().blockState(IRON_BLOCK)), 0.6D, 4, 0.1D)
          .sound(Sounds.ENTITY_IRON_GOLEM_STEP, 0.4F, 0.9F);
    }
  }

  @Handle
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    if (e.isSneaking()) {
      if (hasActiveAdaptation(p)) {
        startStance(p);
      }
      return;
    }

    endStance(p);
  }

  @Handle
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    if ((preferenceEnabled(p, BlockingPreferences.SNEAK_STANCE) && !p.isSneaking()) || stanceStates.containsKey(p.uuid())) {
      return;
    }

    if (hasActiveAdaptation(p)) {
      startStance(p);
    }
  }

  @Handle
  public void on(PlayerGameModeChangeEvent e) {
    GameMode mode = e.newGameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      endStance(e.player());
    }
  }

  @Override
  public void unregister() {
    super.unregister();
    for (UUID playerId : stanceStates.keySet()) {
      PlayerRef p = AdaptCore.platform().host().player(playerId);
      if (p != null) {
        AdaptAttributeService.get().removeAll(p, getName());
      }
    }
    stanceStates.clear();
  }

  private void startStance(PlayerRef p) {
    StanceState state = stanceStates.computeIfAbsent(p.uuid(), key -> new StanceState());
    if (state.refreshScheduled.compareAndSet(false, true)
        && !Tasks.runEntity(p, () -> refreshStance(p, state), 1)) {
      endStance(p);
    }
  }

  private void refreshStance(PlayerRef p, StanceState state) {
    if (stanceStates.get(p.uuid()) != state || !p.isOnline()) {
      state.refreshScheduled.set(false);
      return;
    }

    int level = getActiveLevel(p);
    if (!p.isSneaking() || level <= 0) {
      endStance(p);
      return;
    }

    if (isBastionStance(p, level)) {
      applyBrace(p, state, level);
    } else {
      clearBrace(p, state);
    }

    if (!Tasks.runEntity(p, () -> refreshStance(p, state), STANCE_REFRESH_TICKS)) {
      endStance(p);
    }
  }

  private void applyBrace(PlayerRef p, StanceState state, int level) {
    double amount = braceAmount(getKnockbackReduction(level));
    if (amount <= 0) {
      clearBrace(p, state);
      return;
    }

    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.apply(p, getName(), SLOT_KNOCKBACK, Attributes.KNOCKBACK_RESISTANCE, amount, AttributeOperation.ADD_VALUE);
    attributes.apply(p, getName(), SLOT_EXPLOSION, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, amount, AttributeOperation.ADD_VALUE);
    if (!state.braced) {
      AbilityGlyphs.toggle(p.uuid(), this, true);
    }
    state.braced = true;
  }

  private void clearBrace(PlayerRef p, StanceState state) {
    if (!state.braced) {
      return;
    }

    state.braced = false;
    AbilityGlyphs.clear(p.uuid(), this, HudGlyph.TOGGLE);
    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.remove(p, getName(), SLOT_KNOCKBACK, Attributes.KNOCKBACK_RESISTANCE);
    attributes.remove(p, getName(), SLOT_EXPLOSION, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE);
  }

  private void endStance(PlayerRef p) {
    if (p == null) {
      return;
    }

    StanceState state = stanceStates.remove(p.uuid());
    if (state == null) {
      return;
    }

    state.refreshScheduled.set(false);
    clearBrace(p, state);
  }

  static double braceAmount(double reduction) {
    return Math.min(1.0D, Math.max(0.0D, reduction));
  }

  static boolean braceImpactFx(double dampedHorizontal, double reduction) {
    if (reduction <= 0) {
      return false;
    }

    double damping = 1.0D - braceAmount(reduction);
    if (damping <= 0) {
      return false;
    }

    return dampedHorizontal / damping > IMPACT_FX_THRESHOLD;
  }

  private boolean isBastionStance(PlayerRef p) {
    return isBastionStance(p, getActiveLevel(p));
  }

  private boolean isBastionStance(PlayerRef p, int level) {
    return level > 0 && p.isBlocking() && (!preferenceEnabled(p, BlockingPreferences.SNEAK_STANCE) || p.isSneaking()) && hasShield(p);
  }

  private boolean hasShield(PlayerRef p) {
    ItemRef main = p.inventory().mainHand();
    ItemRef off = p.inventory().offHand();
    return (isItem(main) && SHIELD.equals(main.type())) || (isItem(off) && SHIELD.equals(off.type()));
  }

  private double getKnockbackReduction(int level) {
    return Math.min(getConfig().maxKnockbackReduction, getConfig().knockbackReductionBase + (getLevelPercent(level) * getConfig().knockbackReductionFactor));
  }

  private double getProjectileReduction(int level) {
    return Math.min(getConfig().maxProjectileReduction, getConfig().projectileReductionBase + (getLevelPercent(level) * getConfig().projectileReductionFactor));
  }

  private double getProjectileNegateChance(int level) {
    return Math.min(getConfig().maxProjectileNegateChance, getConfig().projectileNegateChanceBase + (getLevelPercent(level) * getConfig().projectileNegateChanceFactor));
  }

  @Override
  public void onTick() {
    for (UUID playerId : activeSessions.keySet()) {
      PlayerRef p = AdaptCore.platform().host().player(playerId);
      if (p == null || !p.isOnline()) {
        activeSessions.remove(playerId);
        continue;
      }
      withPlayerThread(p, () -> {
        int level = getActiveLevel(p);
        if (level <= 0 || !isBastionStance(p, level)) {
          setStorage(p, "bastionSessionCount", 0);
          activeSessions.remove(playerId);
        }
      });
    }
  }

  private static class StanceState {
    private final AtomicBoolean refreshScheduled = new AtomicBoolean();
    private boolean braced;
  }

  @ConfigDescription("Sneak-block with a shield to brace against knockback and soften projectiles.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Knockback Reduction Base for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double knockbackReductionBase = 0.18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Knockback Reduction Factor for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double knockbackReductionFactor = 0.52;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Knockback Reduction for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxKnockbackReduction = 0.75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Projectile Reduction Base for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double projectileReductionBase = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Projectile Reduction Factor for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double projectileReductionFactor = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Projectile Reduction for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxProjectileReduction = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Projectile Negate Chance Base for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double projectileNegateChanceBase = 0.05;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Projectile Negate Chance Factor for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double projectileNegateChanceFactor = 0.22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Projectile Negate Chance for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxProjectileNegateChance = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Mitigated Damage for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerMitigatedDamage = 2.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp On Negate for the Blocking Bastion Stance adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnNegate = 8.0;

    public Config() {
      costFactor = 0.68;
      initialCost = 4;
    }
  }
}
