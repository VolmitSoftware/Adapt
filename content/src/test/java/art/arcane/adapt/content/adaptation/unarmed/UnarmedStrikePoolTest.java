package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.content.skill.SkillUnarmed;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Power, Glass Cannon and Sucker Punch share one pool: their bonuses are shares of the bare fist and add to each
 * other. At their top levels with a fist of 1 and no armor the shares are Power 2.57, Glass Cannon 2.05
 * (2.0 + 7 * 0.15 - 1) and Sucker Punch 0.75 (0.2 + 0.55), so a punch deals 1 * (1 + 2.57 + 2.05 + 0.75) + 1.75 = 8.12;
 * Iron Fists 3, a full Combo Chain 10.5 and Battering Charge 4.7 then add to 26.32. Compounded the same punch dealt
 * 3.57 * 1.75 * 5.05 + 1.75 = 33.30 before the flat adds.
 */
class UnarmedStrikePoolTest {
  private static final double CRIT = 1.5D;
  private static final double TOLERANCE = 1.0E-9D;

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Brawler", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillUnarmed.class));
    profile = adapt.join(player);
    player.setAttributeBase(Attributes.ATTACK_DAMAGE, 1D);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void powerAloneRaisesTheFistTo3_57() {
    maxed(UnarmedPower.class);

    assertThat(punch(swing(1D)).damage()).isCloseTo(3.57D, within(TOLERANCE));
  }

  @Test
  void glassCannonAloneDeals4_8WithItsNewNakedFactorOf2() {
    maxed(UnarmedGlassCannon.class);

    assertThat(punch(swing(1D)).damage()).isCloseTo((1D * 3.05D) + 1.75D, within(TOLERANCE));
  }

  @Test
  void suckerPunchAloneDeals1_75() {
    maxed(UnarmedSuckerPunch.class);
    player.setSprinting(true);

    assertThat(punch(swing(1D)).damage()).isCloseTo(1.75D, within(TOLERANCE));
  }

  @Test
  void powerAndGlassCannonAddTo7_37() {
    maxed(UnarmedPower.class);
    maxed(UnarmedGlassCannon.class);

    assertThat(punch(swing(1D)).damage()).isCloseTo((1D * (1D + 2.57D + 2.05D)) + 1.75D, within(TOLERANCE));
  }

  @Test
  void powerAndSuckerPunchAddTo4_32() {
    maxed(UnarmedPower.class);
    maxed(UnarmedSuckerPunch.class);
    player.setSprinting(true);

    assertThat(punch(swing(1D)).damage()).isCloseTo(1D + 2.57D + 0.75D, within(TOLERANCE));
  }

  @Test
  void glassCannonAndSuckerPunchAddTo5_55() {
    maxed(UnarmedGlassCannon.class);
    maxed(UnarmedSuckerPunch.class);
    player.setSprinting(true);

    assertThat(punch(swing(1D)).damage()).isCloseTo((1D * (1D + 2.05D + 0.75D)) + 1.75D, within(TOLERANCE));
  }

  @Test
  void allThreeAddTo8_12() {
    maxedPool();

    assertThat(punch(swing(1D)).damage()).isCloseTo(8.12D, within(TOLERANCE));
  }

  @Test
  void aCritScalesTheFistSoAllThreeDeal11_305() {
    maxedPool();

    assertThat(punch(swing(CRIT)).damage()).isCloseTo((CRIT * 6.37D) + 1.75D, within(TOLERANCE));
  }

  @Test
  void ironFistsAddsFlatAfterThePoolFor11_12() {
    maxedPool();
    maxed(UnarmedIronFists.class);

    assertThat(punch(swing(1D)).damage()).isCloseTo(8.12D + 3D, within(TOLERANCE));
  }

  @Test
  void thePoolIsTheSameInEitherHandlerOrder() {
    maxedPool();
    UnarmedGlassCannon glass = adaptation(UnarmedGlassCannon.class);
    UnarmedSuckerPunch sucker = adaptation(UnarmedSuckerPunch.class);
    double swing = swing(1D);

    EntityDamageByEntityEvent glassFirst = hit(swing);
    glass.on(glassFirst);
    sucker.on(glassFirst);
    EntityDamageByEntityEvent suckerFirst = hit(swing);
    sucker.on(suckerFirst);
    glass.on(suckerFirst);

    assertThat(glassFirst.damage()).isCloseTo(8.12D, within(TOLERANCE));
    assertThat(suckerFirst.damage()).isCloseTo(8.12D, within(TOLERANCE));
  }

  @Test
  void thePoolHoldsWhenEachPriorityGetsAFreshCopyOfTheHit() {
    maxedPool();
    double swing = swing(1D);

    EntityDamageByEntityEvent copy = null;
    double current = swing;
    for (EventPriority priority : EventPriority.values()) {
      EntityDamageByEntityEvent next = hit(current);
      next.setSource(null, swing, (LivingRef target, double amount) -> {
      });
      if (copy != null) {
        next.setDispatchId(copy.dispatchId());
      }
      adapt.bus().post(next, priority);
      current = next.damage();
      copy = next;
    }

    assertThat(current).isCloseTo(8.12D, within(TOLERANCE));
  }

  @Test
  void armorCutsOnlyGlassCannonsShare() {
    maxedPool();
    player.setEquipment(EquipmentSlot.CHEST, FakeItem.of("diamond_chestplate"));
    double armor = adaptation(UnarmedGlassCannon.class).getArmorValue(player);
    assertThat(armor).isPositive();

    double expected = (1D * (1D + 2.57D + 0.75D)) + Math.max(0D, 1.75D - (1D * armor));
    assertThat(punch(swing(1D)).damage()).isCloseTo(expected, within(TOLERANCE));
  }

  @Test
  void armoredGlassCannonNeverTakesFromTheOtherShares() {
    player.setAttributeBase(Attributes.ATTACK_DAMAGE, 10D);
    maxed(UnarmedPower.class);
    maxed(UnarmedGlassCannon.class);
    player.setEquipment(EquipmentSlot.CHEST, FakeItem.of("diamond_chestplate"));

    assertThat(punch(swing(1D)).damage()).isCloseTo(10D * 3.57D, within(TOLERANCE));
  }

  @Test
  void armoredGlassCannonAloneNeverDropsBelowTheHit() {
    player.setAttributeBase(Attributes.ATTACK_DAMAGE, 10D);
    maxed(UnarmedGlassCannon.class);
    player.setEquipment(EquipmentSlot.CHEST, FakeItem.of("diamond_chestplate"));

    assertThat(punch(swing(1D)).damage()).isCloseTo(10D, within(TOLERANCE));
  }

  private void maxedPool() {
    maxed(UnarmedPower.class);
    maxed(UnarmedGlassCannon.class);
    maxed(UnarmedSuckerPunch.class);
    player.setSprinting(true);
  }

  /** The damage vanilla hands the event: the attack damage attribute, Power's modifier included, times the crit. */
  private double swing(double crit) {
    punch(new FakeLiving(Id.minecraft("pig"), new Loc(platform.world(), 1.5D, 61D, 0.5D)), 1D);
    return player.attributeValue(Attributes.ATTACK_DAMAGE) * crit;
  }

  private EntityDamageByEntityEvent punch(double damage) {
    return adapt.post(hit(damage));
  }

  private void punch(FakeLiving target, double damage) {
    adapt.post(new EntityDamageByEntityEvent(target, player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), damage, false, FinalDamage.IDENTITY));
  }

  private EntityDamageByEntityEvent hit(double damage) {
    return new EntityDamageByEntityEvent(zombie(), player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), damage, false, FinalDamage.IDENTITY);
  }

  private FakeLiving zombie() {
    FakeLiving zombie = new FakeLiving(Id.minecraft("zombie"), new Loc(platform.world(), 1.5D, 61D, 0.5D));
    zombie.trait(EntityTrait.MONSTER);
    return zombie;
  }

  private <A extends Adaptation<?>> void maxed(Class<A> type) {
    A adaptation = adaptation(type);
    profile.getData().getSkillLine("unarmed").setAdaptation(adaptation, adaptation.getMaxLevel());
  }

  private <A extends Adaptation<?>> A adaptation(Class<A> type) {
    Skill<?> skill = AdaptCore.get().getAdaptServer().getSkillRegistry().getSkill("unarmed");
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (type.isInstance(adaptation)) {
        return type.cast(adaptation);
      }
    }
    throw new AssertionError("No adaptation " + type.getSimpleName());
  }
}
