package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.item.ItemRef;
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
  void globeDividesTheBaseHitThenAddsTheLevelBonusToEveryTarget() {
    assertThat(TragoulGlobe.damagePerEntity(12D, 2, 2D)).isEqualTo(6D);
    assertThat(TragoulGlobe.damagePerEntity(12D, 1, 2D)).isEqualTo(8D);
    assertThat(TragoulGlobe.damagePerEntity(Double.NaN, 2, 2D)).isZero();
    assertThat(TragoulGlobe.damagePerEntity(12D, 2, Double.NaN)).isZero();
  }

  @Test
  void lanceCapsSearchesAndRejectsConcurrentOwnerChains() {
    TragoulLances.LanceWorkBudget budget = new TragoulLances.LanceWorkBudget(2, 50L);
    TragoulLances.LanceAdmission admission = new TragoulLances.LanceAdmission(2);
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
    TragoulPlague.PlagueWorkBudget budget = new TragoulPlague.PlagueWorkBudget(2, 1, 50L);
    TragoulPlague.PendingEntityGate gate = new TragoulPlague.PendingEntityGate(2);
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
    assertThat(TragoulPlague.scaledRadius(8D, 20D, 1, 5)).isEqualTo(8D);
    assertThat(TragoulPlague.scaledRadius(8D, 20D, 5, 5)).isEqualTo(20D);
    assertThat(TragoulPlague.scaledRadius(8D, 20D, 3, 5)).isEqualTo(14D);
    assertThat(TragoulPlague.amplifiedEffect(2, 1)).isEqualTo(3);
    assertThat(TragoulPlague.amplifiedEffect(255, 10)).isEqualTo(255);
  }

  @Test
  void corpseLanceTriplesDamageOnlyWhenEveryArmorSlotIsEmpty() {
    ItemRef armor = mock(ItemRef.class);
    when(armor.isEmpty()).thenReturn(false);
    ItemRef[] emptyArmor = new ItemRef[]{null, null, null, null};
    ItemRef[] equippedArmor = new ItemRef[]{null, armor, null, null};

    assertThat(TragoulLances.isUnarmored(null)).isTrue();
    assertThat(TragoulLances.isUnarmored(emptyArmor)).isTrue();
    assertThat(TragoulLances.isUnarmored(equippedArmor)).isFalse();
    assertThat(new TragoulLance.Config().unarmoredDamageMultiplier).isEqualTo(3D);
    assertThat(TragoulLances.lanceDamage(10D, 1D, 3D, true)).isEqualTo(30D);
    assertThat(TragoulLances.lanceDamage(10D, 1D, 3D, false)).isEqualTo(10D);
    assertThat(TragoulLances.lanceDamage(-10D, 1D, 3D, true)).isZero();
  }

  @Test
  void corpseLancePaysFlatSelfDamageThatFallsWithLevels() {
    TragoulLance.Config config = new TragoulLance.Config();

    assertThat(config.selfDamageAtFirstLevel).isEqualTo(6D);
    assertThat(config.selfDamageAtMaxLevel).isEqualTo(2D);
    assertThat(TragoulLances.selfDamageForLevel(1, 5, 6D, 2D)).isEqualTo(6D);
    assertThat(TragoulLances.selfDamageForLevel(3, 5, 6D, 2D)).isEqualTo(4D);
    assertThat(TragoulLances.selfDamageForLevel(5, 5, 6D, 2D)).isEqualTo(2D);
    assertThat(TragoulLances.selfDamageForLevel(8, 5, 6D, 2D)).isEqualTo(2D);
    assertThat(TragoulLances.selfDamageForLevel(0, 5, 6D, 2D)).isZero();
    assertThat(TragoulLances.selfDamageForLevel(3, 5, Double.NaN, 2D)).isZero();
  }

  @Test
  void corpseLanceNeverTreatsItsCasterAsATarget() {
    UUID ownerId = new UUID(3L, 1L);

    assertThat(TragoulLances.isCaster(ownerId, ownerId)).isTrue();
    assertThat(TragoulLances.isCaster(ownerId, new UUID(3L, 2L))).isFalse();
  }

  @Test
  void corpseLanceOnlyContinuesForALivingValidOnlineCaster() {
    PlayerRef owner = mock(PlayerRef.class);
    when(owner.isOnline()).thenReturn(true);
    when(owner.isValid()).thenReturn(true);
    when(owner.isDead()).thenReturn(false);

    assertThat(TragoulLances.isOwnerEligible(owner)).isTrue();
    assertThat(TragoulLances.canContinueChain(owner, true, 2)).isTrue();
    assertThat(TragoulLances.canContinueChain(owner, false, 2)).isFalse();
    assertThat(TragoulLances.canContinueChain(owner, true, 1)).isFalse();

    when(owner.isDead()).thenReturn(true);
    assertThat(TragoulLances.isOwnerEligible(owner)).isFalse();
    assertThat(TragoulLances.canContinueChain(owner, true, 2)).isFalse();

    when(owner.isDead()).thenReturn(false);
    when(owner.isValid()).thenReturn(false);
    assertThat(TragoulLances.isOwnerEligible(owner)).isFalse();

    when(owner.isValid()).thenReturn(true);
    when(owner.isOnline()).thenReturn(false);
    assertThat(TragoulLances.isOwnerEligible(owner)).isFalse();
    assertThat(TragoulLances.isOwnerEligible(null)).isFalse();
  }

  @Test
  void corpseLanceResolvesDirectAndProjectilePlayers() {
    PlayerRef player = mock(PlayerRef.class);
    ProjectileRef projectile = mock(ProjectileRef.class);
    EntityRef other = mock(EntityRef.class);
    when(projectile.shooter()).thenReturn(player);

    assertThat(TragoulLances.resolvePlayerDamager(player)).isSameAs(player);
    assertThat(TragoulLances.resolvePlayerDamager(projectile)).isSameAs(player);
    assertThat(TragoulLances.resolvePlayerDamager(other)).isNull();
  }

  @Test
  void corpseExplosionSuppressionOnlyBlocksFreshNovaVictims() {
    assertThat(TragoulNova.isSuppressed(null, 1000L, 500L)).isFalse();
    assertThat(TragoulNova.isSuppressed(900L, 1000L, 500L)).isTrue();
    assertThat(TragoulNova.isSuppressed(400L, 1000L, 500L)).isFalse();
  }

  @Test
  void corpseExplosionTargetsEveryHostileEnemyFamily() {
    LivingRef groundedEnemy = mock(LivingRef.class);
    when(groundedEnemy.has(EntityTrait.ENEMY)).thenReturn(true);
    LivingRef flyingEnemy = mock(LivingRef.class);
    when(flyingEnemy.has(EntityTrait.ENEMY)).thenReturn(true);
    LivingRef neutralMob = mock(LivingRef.class);

    assertThat(TragoulNova.isNovaTarget(groundedEnemy)).isTrue();
    assertThat(TragoulNova.isNovaTarget(flyingEnemy)).isTrue();
    assertThat(TragoulNova.isNovaTarget(neutralMob)).isFalse();
  }
}
