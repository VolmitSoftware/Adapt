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
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.UUID;

public class AgilityVault extends SimpleAdaptation<AgilityVault.Config> {
  private final Cooldowns vaultCooldown = cooldowns();

  public AgilityVault() {
    super("agility-vault");
    registerConfiguration(Config.class);
    setIcon(Material.OAK_FENCE);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.OAK_FENCE)
        .key("challenge_agility_vault_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.MOSSY_COBBLESTONE_WALL)
            .key("challenge_agility_vault_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_agility_vault_250", "agility.vault.vaults", 250, 300);
    registerMilestone("challenge_agility_vault_2500", "agility.vault.vaults", 2500, 1200);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getMaxVaultHeight(level), 1), 1);
    statLore(v, C.YELLOW, "* ", Form.f(getHungerCost(level), 1), 2);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    if (e.getTo() == null || !e.getPlayer().isSprinting()) {
      return;
    }

    Player p = e.getPlayer();
    if (!isVaultEligible(p)) {
      return;
    }

    Vector move = e.getTo().toVector().subtract(e.getFrom().toVector());
    move.setY(0);
    if (move.lengthSquared() < getConfig().minMoveSquared) {
      return;
    }

    withPlayerThread(p, e, () -> attemptVault(p, move));
  }

  private void attemptVault(Player p, Vector move) {
    if (!p.isOnGround()) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    UUID id = p.getUniqueId();
    if (!vaultCooldown.isReady(id, Math.max(0L, getConfig().cooldownMillis))) {
      return;
    }

    Vector direction = move.clone().normalize();
    Location feet = p.getLocation();
    Block frontLower = feet.clone().add(direction).getBlock();
    Block frontUpper = frontLower.getRelative(BlockFace.UP);
    Block frontHead = frontUpper.getRelative(BlockFace.UP);

    if (!frontLower.getType().isSolid() || frontUpper.getType().isSolid() || frontHead.getType().isSolid()) {
      return;
    }

    double effectiveHeight = isTallObstacle(frontLower) ? 1.5D : 1.0D;
    if (effectiveHeight > getMaxVaultHeight(level)) {
      return;
    }

    if (p.getFoodLevel() <= 0) {
      return;
    }

    vaultCooldown.mark(id);
    applyHungerCost(p, getHungerCost(level));

    double up = effectiveHeight >= 1.5D ? getConfig().vaultUpTall : getConfig().vaultUpShort;
    double carry = getConfig().vaultForwardCarry;
    p.setVelocity(new Vector(direction.getX() * carry, up, direction.getZ() * carry));

    fx(feet.clone().add(0, 0.2D, 0), FxPriority.GAMEPLAY)
        .particle(Particles.BLOCK_CRACK, 5, 0, 0.1D, 0, 0.25D, 0.05D, frontLower.getBlockData())
        .chord(Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.7F, 1.2F, Sound.ENTITY_PHANTOM_FLAP, 0.4F, 1.5F);

    addStat(p, "agility.vault.vaults", 1);
    xp(p, getConfig().xpPerVault);
  }

  private boolean isTallObstacle(Block block) {
    Material type = block.getType();
    return Tag.FENCES.isTagged(type) || Tag.FENCE_GATES.isTagged(type) || Tag.WALLS.isTagged(type);
  }

  private void applyHungerCost(Player p, double cost) {
    double saturation = p.getSaturation();
    if (saturation >= cost) {
      p.setSaturation((float) (saturation - cost));
      return;
    }

    p.setSaturation(0F);
    int foodCost = (int) Math.ceil(cost - saturation);
    p.setFoodLevel(Math.max(0, p.getFoodLevel() - foodCost));
  }

  private boolean isVaultEligible(Player p) {
    GameMode mode = p.getGameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      return false;
    }

    return !p.isSwimming()
        && !p.isFlying()
        && !p.isGliding()
        && !p.isClimbing()
        && p.getVehicle() == null;
  }

  private double getMaxVaultHeight(int level) {
    return getConfig().maxVaultHeightBase + (getLevelPercent(level) * getConfig().maxVaultHeightFactor);
  }

  private double getHungerCost(int level) {
    return Math.max(getConfig().hungerCostFloor,
        getConfig().hungerCostBase - (getLevelPercent(level) * getConfig().hungerCostReduction));
  }

  @ConfigDescription("Sprint straight over fences, walls, and low ledges instead of stalling against them.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base maximum obstacle height in blocks that can be vaulted before level scaling.", impact = "Higher values vault taller obstacles even at low level.")
    double maxVaultHeightBase = 1.0;
    @ConfigDoc(value = "Additional vaultable obstacle height in blocks granted at max level.", impact = "Higher values let leveling clear fences and walls.")
    double maxVaultHeightFactor = 0.7;
    @ConfigDoc(value = "Upward velocity used when vaulting a short 1-block obstacle.", impact = "Higher values hop higher over low ledges.")
    double vaultUpShort = 0.42;
    @ConfigDoc(value = "Upward velocity used when vaulting a tall fence or wall.", impact = "Higher values clear taller collision boxes reliably.")
    double vaultUpTall = 0.56;
    @ConfigDoc(value = "Forward velocity carried through a vault.", impact = "Higher values keep more momentum over the obstacle.")
    double vaultForwardCarry = 0.32;
    @ConfigDoc(value = "Base saturation/hunger cost per vault before level scaling.", impact = "Higher values make vaulting drain stamina faster.")
    double hungerCostBase = 1.6;
    @ConfigDoc(value = "Saturation/hunger cost reduction applied at max level.", impact = "Higher values make leveling cheapen vaulting more.")
    double hungerCostReduction = 1.2;
    @ConfigDoc(value = "Minimum saturation/hunger cost per vault after reductions.", impact = "Higher values keep a hard floor under vault cost.")
    double hungerCostFloor = 0.3;
    @ConfigDoc(value = "Cooldown between vaults in milliseconds.", impact = "Higher values prevent rapid repeated vaulting against a wall.")
    long cooldownMillis = 550;
    @ConfigDoc(value = "Minimum squared horizontal move per tick required to trigger a vault.", impact = "Higher values require clearer forward motion before vaulting.")
    double minMoveSquared = 0.0025;
    @ConfigDoc(value = "Experience granted per successful vault.", impact = "Higher values reward vaulting with more skill xp.")
    double xpPerVault = 3;

    public Config() {
      baseCost = 3;
      costFactor = 0.5;
      maxLevel = 4;
      initialCost = 4;
    }
  }
}
