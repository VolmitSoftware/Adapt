package art.arcane.adapt.content.adaptation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IdleTickDemandCoverageTest {
  private static final List<String> LEARNER_BOUND = List.of(
      "agility.AgilityVault",
      "chronos.ChronosAccelerate",
      "chronos.ChronosPocketWatch",
      "chronos.ChronosTimeInABottle",
      "discovery.DiscoveryArmor",
      "discovery.DiscoveryKeenEye",
      "discovery.DiscoveryPolymath",
      "discovery.DiscoverySixthSense",
      "discovery.DiscoveryTrailblazer",
      "herbalism.HerbalismBeeShepherd",
      "herbalism.HerbalismGrowthAura",
      "kinetics.KineticsHeavyFrame",
      "kinetics.KineticsMoonJump",
      "kinetics.KineticsPhalanxReach",
      "kinetics.KineticsRubberSoul",
      "kinetics.KineticsSurfaceSkate",
      "seaborrne.SeaborneBrineSkin",
      "seaborrne.SeaborneDeepSalvager",
      "seaborrne.SeaborneOxygen",
      "seaborrne.SeaborneTurtlesMiningSpeed",
      "seaborrne.SeaborneTurtlesVision",
      "taming.TamingFetch",
      "unarmed.UnarmedPower"
  );
  private static final List<String> STATE_DRIVEN = List.of(
      "agility.AgilityArmorUp",
      "architect.ArchitectPlacement",
      "brewing.BrewingSuperHeated",
      "discovery.DiscoveryArchaeologist",
      "discovery.DiscoveryArmor",
      "discovery.DiscoveryInsight",
      "herbalism.HerbalismBeeShepherd",
      "herbalism.HerbalismGrowthAura",
      "hunter.HunterBloodTrail",
      "hunter.HunterSnareLine",
      "kinetics.KineticsMassShift",
      "rift.RiftVoidMagnet",
      "seaborrne.SeabornePressureDiver",
      "taming.TamingMountedTactics"
  );

  @Test
  void majorityPassRetainsExplicitIdleDemandContracts() throws ReflectiveOperationException {
    for (String className : LEARNER_BOUND) {
      Method method = adaptationClass(className).getDeclaredMethod("usesLearnerBoundTicking");
      assertThat(method.getReturnType()).isEqualTo(boolean.class);
    }
    for (String className : STATE_DRIVEN) {
      Method method = adaptationClass(className).getDeclaredMethod("hasTickDemand");
      assertThat(method.getReturnType()).isEqualTo(boolean.class);
    }

    Set<String> covered = new HashSet<>(LEARNER_BOUND);
    covered.addAll(STATE_DRIVEN);
    assertThat(covered).hasSize(34);
  }

  private static Class<?> adaptationClass(String relativeName) throws ClassNotFoundException {
    return Class.forName(
        "art.arcane.adapt.content.adaptation." + relativeName,
        false,
        IdleTickDemandCoverageTest.class.getClassLoader()
    );
  }
}
