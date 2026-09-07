package art.arcane.adapt.content.adaptation;

import art.arcane.adapt.content.adaptation.blocking.BlockingBastionStance;
import art.arcane.adapt.content.adaptation.hunter.HunterBigGameHunter;
import art.arcane.adapt.content.adaptation.hunter.HunterBloodTrail;
import art.arcane.adapt.content.adaptation.hunter.HunterPredatorFocus;
import art.arcane.adapt.content.adaptation.sword.SwordsBladeFlow;
import art.arcane.adapt.content.adaptation.sword.SwordsBloodyBlade;
import art.arcane.adapt.content.adaptation.sword.SwordsCrimsonCyclone;
import art.arcane.adapt.content.adaptation.sword.SwordsDualWield;
import art.arcane.adapt.content.adaptation.sword.SwordsDuelistsFocus;
import art.arcane.adapt.content.adaptation.sword.SwordsExecutionersEdge;
import art.arcane.adapt.content.adaptation.sword.SwordsHamstring;
import art.arcane.adapt.content.adaptation.sword.SwordsLungeStrike;
import art.arcane.adapt.content.adaptation.sword.SwordsPoisonedBlade;
import art.arcane.adapt.content.adaptation.sword.SwordsRiposteWindow;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedDisarm;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedGrapple;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DamageHandlerCancellationPolicyTest {
  private static final List<Class<?>> CANCELLATION_AWARE_DAMAGE_HANDLERS = List.of(
      UnarmedDisarm.class,
      UnarmedGrapple.class,
      BlockingBastionStance.class,
      HunterPredatorFocus.class,
      HunterBigGameHunter.class,
      HunterBloodTrail.class,
      SwordsPoisonedBlade.class,
      SwordsBloodyBlade.class,
      SwordsDualWield.class,
      SwordsExecutionersEdge.class,
      SwordsRiposteWindow.class,
      SwordsCrimsonCyclone.class,
      SwordsLungeStrike.class,
      SwordsBladeFlow.class,
      SwordsDuelistsFocus.class,
      SwordsHamstring.class
  );

  @Test
  void everyDamageHandlerNeverReceivesAlreadyCancelledDamage() throws ReflectiveOperationException {
    for (Class<?> adaptationType : CANCELLATION_AWARE_DAMAGE_HANDLERS) {
      Method handler = adaptationType.getDeclaredMethod("on", EntityDamageByEntityEvent.class);
      EventHandler policy = handler.getAnnotation(EventHandler.class);
      assertThat(policy).as("%s damage handler policy", adaptationType.getSimpleName()).isNotNull();
      assertThat(policy.ignoreCancelled())
          .as("%s must ignore cancelled damage", adaptationType.getSimpleName())
          .isTrue();
    }
  }
}
