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

package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public class AgilityVault extends SimpleAdaptation<AgilityVault.Config> {
  private static final String SLOT_VAULT = "vault";
  private static final int VAULT_LEVELS = 1;
  private static final long RECONCILE_INTERVAL_MILLIS = 1000L;
  private static final double MINIMUM_VAULT_HEIGHT = 1.65D;
  private static final double MINIMUM_DIRECTION_SQUARED = 1.0E-6D;
  private static final double FENCE_PROBE_START = 0.35D;
  private static final double FENCE_PROBE_STEP = 0.20D;
  private static final double FENCE_PROBE_LIMIT = 1.60D;

  private final Map<UUID, Double> armedBonuses = playerState();

  public AgilityVault() {
    super("agility-vault");
    registerConfiguration(Config.class);
    setIcon(Material.OAK_FENCE);
    setCostFactor(0D);
    setMaxLevel(VAULT_LEVELS);
    setInterval(RECONCILE_INTERVAL_MILLIS);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.OAK_FENCE)
        .key("challenge_agility_vault_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHER_BRICK_FENCE)
            .key("challenge_agility_vault_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_agility_vault_250", "agility.vault.vaults", 250, 300);
    registerMilestone("challenge_agility_vault_2500", "agility.vault.vaults", 2500, 1200);
  }

  @Override
  protected void onConfigReload(Config previousConfig, Config newConfig) {
    super.onConfigReload(previousConfig, newConfig);
    newConfig.costFactor = 0D;
    newConfig.maxLevel = VAULT_LEVELS;
    setCostFactor(0D);
    setMaxLevel(VAULT_LEVELS);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(vaultHeight(getConfig().jumpHeight), 2), 1);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    for (AdaptPlayer adaptPlayer : learnedCandidates(System.currentTimeMillis())) {
      Player player = adaptPlayer.getPlayer();
      if (player == null) {
        continue;
      }
      withPlayerThread(player, () -> reconcilePlayer(player, adaptPlayer));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    if (e.getTo() == null || e instanceof PlayerTeleportEvent) {
      return;
    }

    Player p = e.getPlayer();
    if (Attributes.JUMP_STRENGTH == null || !p.isOnGround() || !isVaultEligible(p)) {
      if (!armedBonuses.isEmpty()) {
        withPlayerThread(p, e, () -> disarm(p));
      }
      return;
    }

    Location target = e.getTo().clone();
    Vector movement = target.toVector().subtract(e.getFrom().toVector());
    Vector direction = horizontalDirection(movement, target.getDirection());
    withPlayerThread(p, e, () -> refreshPreArm(p, target, direction));
  }

  @Override
  protected List<Listener> createCompanionListeners() {
    if (!PaperCompat.hasClass("com.destroystokyo.paper.event.player.PlayerJumpEvent")) {
      return List.of();
    }

    return List.of(new JumpListener());
  }

  private final class JumpListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(PlayerJumpEvent e) {
      Player p = e.getPlayer();
      if (!hasAdaptation(p)) {
        return;
      }

      Location origin = e.getFrom();
      Vector movement = e.getTo().toVector().subtract(origin.toVector());
      Vector direction = horizontalDirection(movement, origin.getDirection());
      withPlayerThread(p, e, () -> attemptVault(p, origin, direction));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerTeleportEvent e) {
    withPlayerThread(e.getPlayer(), e, () -> clearArmedState(e.getPlayer()));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerGameModeChangeEvent e) {
    clearArmedState(e.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    clearArmedState(e.getEntity());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    clearArmedState(e.getPlayer());
  }

  static Vector horizontalDirection(Vector movement, Vector facing) {
    Vector direction = movement == null ? new Vector() : movement.clone().setY(0D);
    if (!isFinite(direction) || direction.lengthSquared() < MINIMUM_DIRECTION_SQUARED) {
      direction = facing == null ? new Vector() : facing.clone().setY(0D);
    }
    if (!isFinite(direction) || direction.lengthSquared() < MINIMUM_DIRECTION_SQUARED) {
      return new Vector();
    }
    return direction.normalize();
  }

  static double vaultHeight(double configuredHeight) {
    if (!Double.isFinite(configuredHeight)) {
      return MINIMUM_VAULT_HEIGHT;
    }
    return Math.max(MINIMUM_VAULT_HEIGHT, configuredHeight);
  }

  static boolean shouldPreArm(int level, boolean eligible, boolean grounded, boolean fenceAhead) {
    return level > 0 && eligible && grounded && fenceAhead;
  }

  static double correctedVerticalVelocity(double existingVelocity, double risenHeight, double targetHeight) {
    double safeExisting = Double.isFinite(existingVelocity) ? existingVelocity : 0D;
    double safeRise = Double.isFinite(risenHeight) ? Math.max(0D, risenHeight) : 0D;
    double remainingHeight = Math.max(0D, vaultHeight(targetHeight) - safeRise);
    return Math.max(safeExisting, AgilityJumpPhysics.strengthForHeight(remainingHeight));
  }

  static Vector correctedVelocity(Vector existingVelocity, double risenHeight, double targetHeight) {
    Vector corrected = existingVelocity == null ? new Vector() : existingVelocity.clone();
    corrected.setY(correctedVerticalVelocity(corrected.getY(), risenHeight, targetHeight));
    return corrected;
  }

  static void synchronizeCorrectedVelocity(Player player, Vector existingVelocity, double risenHeight, double targetHeight) {
    player.setVelocity(correctedVelocity(existingVelocity, risenHeight, targetHeight));
  }

  boolean normalizeStoredLevel(PlayerSkillLine line) {
    if (line == null || line.getAdaptationLevel(getName()) <= VAULT_LEVELS) {
      return false;
    }
    line.setAdaptation(this, VAULT_LEVELS);
    return true;
  }

  private void refreshPreArm(Player p, Location origin, Vector direction) {
    boolean grounded = p.isOnGround();
    boolean eligible = Attributes.JUMP_STRENGTH != null && grounded && isVaultEligible(p);
    int level = eligible ? resolvePreArmLevel(p) : 0;
    Block fence = level > 0 && direction.lengthSquared() >= MINIMUM_DIRECTION_SQUARED
        ? findFence(origin, direction)
        : null;
    if (!shouldPreArm(level, eligible, grounded, fence != null)) {
      disarm(p);
      return;
    }
    arm(p);
  }

  private int resolvePreArmLevel(Player p) {
    normalizeStoredLevel(p);
    return getActiveLevel(p);
  }

  private void attemptVault(Player p, Location origin, Vector direction) {
    normalizeStoredLevel(p);
    if (getActiveLevel(p) <= 0 || !isVaultEligible(p) || Attributes.JUMP_STRENGTH == null) {
      disarm(p);
      return;
    }

    Block fence = findFence(origin, direction);
    if (fence == null) {
      disarm(p);
      return;
    }

    arm(p);
    double originY = origin.getY();
    if (!J.runEntity(p, () -> finishVaultJump(p, origin.getWorld(), originY), 1)) {
      disarm(p);
    }

    fx(origin.clone().add(0D, 0.2D, 0D), FxPriority.GAMEPLAY)
        .particle(Particles.BLOCK_CRACK, 5, 0D, 0.1D, 0D, 0.25D, 0.05D, fence.getBlockData())
        .chord(Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.7F, 1.2F, Sound.ENTITY_PHANTOM_FLAP, 0.4F, 1.5F);

    addStat(p, "agility.vault.vaults", 1);
    xp(p, getConfig().xpPerVault);
  }

  private void arm(Player p) {
    double bonus = AgilityJumpPhysics.bonusForHeight(vaultHeight(getConfig().jumpHeight));
    Double armedBonus = armedBonuses.get(p.getUniqueId());
    if (armedBonus != null && Double.compare(armedBonus, bonus) == 0) {
      return;
    }

    AdaptAttributeService.get().apply(p, getName(), SLOT_VAULT, Attributes.JUMP_STRENGTH,
        bonus, AttributeModifier.Operation.ADD_NUMBER);
    armedBonuses.put(p.getUniqueId(), bonus);
  }

  private void disarm(Player p) {
    if (armedBonuses.remove(p.getUniqueId()) == null) {
      return;
    }
    AdaptAttributeService.get().remove(p, getName(), SLOT_VAULT, Attributes.JUMP_STRENGTH);
  }

  private void clearArmedState(Player p) {
    armedBonuses.remove(p.getUniqueId());
    AdaptAttributeService.get().remove(p, getName(), SLOT_VAULT, Attributes.JUMP_STRENGTH);
  }

  private void finishVaultJump(Player p, World originWorld, double originY) {
    disarm(p);
    if (!isRuntimeRegistered() || !p.isOnline() || !p.isValid() || p.isDead() || p.isOnGround()
        || p.getWorld() != originWorld || getActiveLevel(p) <= 0 || !isVaultEligible(p)) {
      return;
    }

    synchronizeCorrectedVelocity(p, p.getVelocity(), p.getLocation().getY() - originY, getConfig().jumpHeight);
  }

  private void reconcilePlayer(Player p, AdaptPlayer adaptPlayer) {
    PlayerSkillLine line = adaptPlayer.getData().getSkillLineNullable(getSkill().getName());
    if (normalizeStoredLevel(line)) {
      clearArmedState(p);
    }
    Location origin = p.getLocation();
    refreshPreArm(p, origin, horizontalDirection(null, origin.getDirection()));
  }

  private void normalizeStoredLevel(Player p) {
    AdaptPlayer adaptPlayer = getPlayer(p);
    PlayerSkillLine line = adaptPlayer.getData().getSkillLineNullable(getSkill().getName());
    if (normalizeStoredLevel(line)) {
      clearArmedState(p);
    }
  }

  static Block findFence(Location origin, Vector direction) {
    return findFence(origin, direction, Tag.FENCES::isTagged);
  }

  static Block findFence(Location origin, Vector direction, Predicate<Material> fencePredicate) {
    Location probe = origin.clone();
    for (double distance = FENCE_PROBE_START; distance <= FENCE_PROBE_LIMIT; distance += FENCE_PROBE_STEP) {
      probe.setX(origin.getX() + (direction.getX() * distance));
      probe.setY(origin.getY());
      probe.setZ(origin.getZ() + (direction.getZ() * distance));
      Block block = probe.getBlock();
      if (fencePredicate.test(block.getType())) {
        return block;
      }
      if (!block.isPassable()) {
        return null;
      }
    }
    return null;
  }

  private static boolean isVaultEligible(Player p) {
    GameMode mode = p.getGameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      return false;
    }

    return p.isOnline()
        && !p.isDead()
        && !p.isSwimming()
        && !p.isFlying()
        && !p.isGliding()
        && !p.isClimbing()
        && p.getVehicle() == null;
  }

  private static boolean isFinite(Vector vector) {
    return Double.isFinite(vector.getX())
        && Double.isFinite(vector.getY())
        && Double.isFinite(vector.getZ());
  }

  @ConfigDescription("Jump toward a fence to clear it.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Jump apex in blocks when clearing a fence.", impact = "Higher values clear fences with more vertical margin.")
    double jumpHeight = 1.75D;
    @ConfigDoc(value = "Experience granted per successful vault.", impact = "Higher values reward vaulting with more skill xp.")
    double xpPerVault = 3D;

    public Config() {
      baseCost = 3;
      costFactor = 0D;
      maxLevel = VAULT_LEVELS;
      initialCost = 4;
    }
  }
}
