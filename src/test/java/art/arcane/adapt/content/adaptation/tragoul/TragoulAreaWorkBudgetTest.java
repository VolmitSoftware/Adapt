package art.arcane.adapt.content.adaptation.tragoul;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TragoulAreaWorkBudgetTest {
  @Test
  void globeCapsActivationsAndAffectedTargetsPerWindow() {
    TragoulGlobe.GlobeWorkBudget budget = new TragoulGlobe.GlobeWorkBudget(2, 3, 50L);

    assertThat(budget.tryActivation(0L)).isTrue();
    assertThat(budget.tryActivation(0L)).isTrue();
    assertThat(budget.tryActivation(0L)).isFalse();
    assertThat(budget.reserveTargets(2, 0L)).isEqualTo(2);
    assertThat(budget.reserveTargets(2, 0L)).isEqualTo(1);
    assertThat(budget.reserveTargets(1, 0L)).isZero();
    assertThat(budget.tryActivation(50L)).isTrue();
    assertThat(budget.reserveTargets(3, 50L)).isEqualTo(3);
  }

  @Test
  void lanceCapsSearchesAndRejectsConcurrentOwnerChains() {
    TragoulLance.LanceWorkBudget budget = new TragoulLance.LanceWorkBudget(2, 50L);
    TragoulLance.LanceAdmission admission = new TragoulLance.LanceAdmission(2);
    UUID firstOwner = new UUID(1L, 1L);
    UUID secondOwner = new UUID(1L, 2L);
    UUID thirdOwner = new UUID(1L, 3L);

    assertThat(budget.trySearch(0L)).isTrue();
    assertThat(budget.trySearch(0L)).isTrue();
    assertThat(budget.trySearch(0L)).isFalse();
    assertThat(budget.trySearch(50L)).isTrue();

    long firstToken = admission.admit(firstOwner, 0L, 10L);
    assertThat(firstToken).isPositive();
    assertThat(admission.admit(firstOwner, 1L, 10L)).isEqualTo(-1L);
    assertThat(admission.admit(secondOwner, 1L, 100L)).isPositive();
    assertThat(admission.admit(thirdOwner, 1L, 100L)).isEqualTo(-1L);
    long replacement = admission.admit(firstOwner, 10L, 100L);
    assertThat(replacement).isGreaterThan(firstToken);
    assertThat(admission.complete(firstOwner, firstToken)).isFalse();
    assertThat(admission.complete(firstOwner, replacement)).isTrue();
  }

  @Test
  void plagueBudgetsMarksSpreadsAndCoalescesMonsterWork() {
    TragoulPlagueBearer.PlagueWorkBudget budget = new TragoulPlagueBearer.PlagueWorkBudget(2, 1, 50L);
    TragoulPlagueBearer.PendingEntityGate gate = new TragoulPlagueBearer.PendingEntityGate(2);
    UUID firstMonster = new UUID(2L, 1L);
    UUID secondMonster = new UUID(2L, 2L);
    UUID thirdMonster = new UUID(2L, 3L);

    assertThat(budget.tryMark(0L)).isTrue();
    assertThat(budget.tryMark(0L)).isTrue();
    assertThat(budget.tryMark(0L)).isFalse();
    assertThat(budget.trySpread(0L)).isTrue();
    assertThat(budget.trySpread(0L)).isFalse();
    assertThat(budget.tryMark(50L)).isTrue();
    assertThat(budget.trySpread(50L)).isTrue();

    assertThat(gate.admit(firstMonster)).isTrue();
    assertThat(gate.admit(firstMonster)).isFalse();
    assertThat(gate.admit(secondMonster)).isTrue();
    assertThat(gate.admit(thirdMonster)).isFalse();
    gate.complete(firstMonster);
    assertThat(gate.admit(thirdMonster)).isTrue();
    assertThat(gate.size()).isEqualTo(2);
  }

  @Test
  void plagueUsesExactLargeRadiusEndpointsAndAmplifiesTheSourceEffect() {
    TragoulPlagueBearer.Config config = new TragoulPlagueBearer.Config();

    assertThat(config.spreadRadiusStart).isEqualTo(8D);
    assertThat(config.spreadRadiusEnd).isEqualTo(20D);
    assertThat(config.amplifierBonus).isEqualTo(1);
    assertThat(TragoulPlagueBearer.scaledRadius(8D, 20D, 1, 5)).isEqualTo(8D);
    assertThat(TragoulPlagueBearer.scaledRadius(8D, 20D, 5, 5)).isEqualTo(20D);
    assertThat(TragoulPlagueBearer.scaledRadius(8D, 20D, 3, 5)).isEqualTo(14D);
    assertThat(TragoulPlagueBearer.amplifiedEffect(2, 1)).isEqualTo(3);
    assertThat(TragoulPlagueBearer.amplifiedEffect(255, 10)).isEqualTo(255);
  }

  @Test
  void corpseLanceTriplesDamageOnlyWhenEveryArmorSlotIsEmpty() {
    ItemStack armor = mock(ItemStack.class);
    when(armor.getType()).thenReturn(Material.LEATHER_BOOTS);
    ItemStack[] emptyArmor = new ItemStack[]{null, null, null, null};
    ItemStack[] equippedArmor = new ItemStack[]{null, armor, null, null};

    assertThat(TragoulLance.isUnarmored(null)).isTrue();
    assertThat(TragoulLance.isUnarmored(emptyArmor)).isTrue();
    assertThat(TragoulLance.isUnarmored(equippedArmor)).isFalse();
    assertThat(new TragoulLance.Config().unarmoredDamageMultiplier).isEqualTo(3D);
    assertThat(TragoulLance.lanceDamage(10D, 1D, 3D, true)).isEqualTo(30D);
    assertThat(TragoulLance.lanceDamage(10D, 1D, 3D, false)).isEqualTo(10D);
    assertThat(TragoulLance.lanceDamage(-10D, 1D, 3D, true)).isZero();
  }

  @Test
  void corpseLancePaysFlatSelfDamageThatFallsWithLevels() {
    TragoulLance.Config config = new TragoulLance.Config();

    assertThat(config.selfDamageAtFirstLevel).isEqualTo(6D);
    assertThat(config.selfDamageAtMaxLevel).isEqualTo(2D);
    assertThat(TragoulLance.selfDamageForLevel(1, 5, 6D, 2D)).isEqualTo(6D);
    assertThat(TragoulLance.selfDamageForLevel(3, 5, 6D, 2D)).isEqualTo(4D);
    assertThat(TragoulLance.selfDamageForLevel(5, 5, 6D, 2D)).isEqualTo(2D);
    assertThat(TragoulLance.selfDamageForLevel(8, 5, 6D, 2D)).isEqualTo(2D);
    assertThat(TragoulLance.selfDamageForLevel(0, 5, 6D, 2D)).isZero();
    assertThat(TragoulLance.selfDamageForLevel(3, 5, Double.NaN, 2D)).isZero();
  }

  @Test
  void corpseLanceNeverTreatsItsCasterAsATarget() {
    UUID ownerId = new UUID(3L, 1L);

    assertThat(TragoulLance.isCaster(ownerId, ownerId)).isTrue();
    assertThat(TragoulLance.isCaster(ownerId, new UUID(3L, 2L))).isFalse();
  }

  @Test
  void corpseLanceOnlyContinuesForALivingValidOnlineCaster() {
    Player owner = mock(Player.class);
    when(owner.isOnline()).thenReturn(true);
    when(owner.isValid()).thenReturn(true);
    when(owner.isDead()).thenReturn(false);

    assertThat(TragoulLance.isOwnerEligible(owner)).isTrue();
    assertThat(TragoulLance.canContinueChain(owner, true, 2)).isTrue();
    assertThat(TragoulLance.canContinueChain(owner, false, 2)).isFalse();
    assertThat(TragoulLance.canContinueChain(owner, true, 1)).isFalse();

    when(owner.isDead()).thenReturn(true);
    assertThat(TragoulLance.isOwnerEligible(owner)).isFalse();
    assertThat(TragoulLance.canContinueChain(owner, true, 2)).isFalse();

    when(owner.isDead()).thenReturn(false);
    when(owner.isValid()).thenReturn(false);
    assertThat(TragoulLance.isOwnerEligible(owner)).isFalse();

    when(owner.isValid()).thenReturn(true);
    when(owner.isOnline()).thenReturn(false);
    assertThat(TragoulLance.isOwnerEligible(owner)).isFalse();
    assertThat(TragoulLance.isOwnerEligible(null)).isFalse();
  }

  @Test
  void corpseLanceResolvesDirectAndProjectilePlayers() {
    Player player = mock(Player.class);
    Projectile projectile = mock(Projectile.class);
    Entity other = mock(Entity.class);
    when(projectile.getShooter()).thenReturn(player);

    assertThat(TragoulLance.resolvePlayerDamager(player)).isSameAs(player);
    assertThat(TragoulLance.resolvePlayerDamager(projectile)).isSameAs(player);
    assertThat(TragoulLance.resolvePlayerDamager(other)).isNull();
  }

  @Test
  void corpseExplosionSuppressionOnlyBlocksFreshNovaVictims() {
    assertThat(TragoulCorpseExplosion.isSuppressed(null, 1000L, 500L)).isFalse();
    assertThat(TragoulCorpseExplosion.isSuppressed(900L, 1000L, 500L)).isTrue();
    assertThat(TragoulCorpseExplosion.isSuppressed(400L, 1000L, 500L)).isFalse();
  }

  @Test
  void corpseExplosionTargetsEveryHostileEnemyFamily() {
    Monster groundedEnemy = mock(Monster.class);
    Ghast flyingEnemy = mock(Ghast.class);
    LivingEntity neutralMob = mock(LivingEntity.class);

    assertThat(TragoulCorpseExplosion.isNovaTarget(groundedEnemy)).isTrue();
    assertThat(TragoulCorpseExplosion.isNovaTarget(flyingEnemy)).isTrue();
    assertThat(TragoulCorpseExplosion.isNovaTarget(neutralMob)).isFalse();
  }
}
