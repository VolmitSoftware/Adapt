package art.arcane.adapt.api.advancement;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillMutation;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.signature.Signature;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.server.hud.ClientToasts;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.content.CoreContent;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.MutationMessages;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.volmlib.util.localization.TextKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Every discovery and unlock toast carries a short quip, never the skill or adaptation description. */
class ToastQuipsTest {
  private static final int ENGLISH_LIMIT = 48;
  private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-0000000000a7");

  @TempDir
  Path dataFolder;

  @Test
  void everyDiscoveryAndUnlockToastCarriesAQuip() {
    TestPlatform platform = TestPlatform.builder(dataFolder).build();
    platform.host().runGame(FakeGameContent.v26_3());
    try (TestAdapt adapt = TestAdapt.start(platform, CoreContent.skillTypes())) {
      SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
      List<String> failures = new ArrayList<>();
      int unlocks = 0;
      for (Skill<?> skill : registry.getSkills()) {
        SkillPresentation presentation = ((SimpleSkill<?>) skill).getPresentation();
        AdaptAdvancement root = skill.buildAdvancements();
        ServerFrame.Toast discovered = root.clientToast(skill.getName());
        assertThat(discovered.kind()).isEqualTo(ToastKind.DISCOVERY);
        expect(failures, root.getKey(), discovered.body(), List.of(presentation.quip()));
        for (AdaptAdvancement child : root.getChildren()) {
          if (child.getKey().startsWith("adaptation_")) {
            expect(failures, child.getKey(), child.clientToast(skill.getName()).body(), presentation.unlockQuips());
            unlocks++;
          }
        }
      }
      int mutated = 0;
      for (Skill<?> lead : registry.getSkills()) {
        for (Skill<?> partner : registry.getSkills()) {
          MutatedSkill skill = lead == partner ? null : registry.getMutatedSkill(new SkillMutation(lead.getName(), partner.getName()).id());
          if (skill == null) {
            continue;
          }
          Set<Adaptation<?>> learned = new LinkedHashSet<>(skill.getAdaptations());
          for (MutationAdaptation own : skill.getMutationAdaptations()) {
            if (own.getSignature() != null) {
              learned.add(own.getSignature());
            }
          }
          for (Adaptation<?> adaptation : learned) {
            List<TextKey> pool = adaptation instanceof Signature<?> ? ToastQuips.SIGNATURE
                : adaptation instanceof SkillUpgrade<?> ? ToastQuips.UPGRADE : ToastQuips.MUTATED;
            expect(failures, skill.getName() + "/" + adaptation.getName(),
                AdvancementManager.unlockToast(VIEWER, adaptation).body(), pool);
            mutated++;
          }
        }
      }

      assertThat(failures).isEmpty();
      assertThat(unlocks).isGreaterThan(300);
      assertThat(mutated).isGreaterThan(1000);
    }
  }

  @Test
  void everyEnglishQuipIsOneShortLine() {
    TestPlatform platform = TestPlatform.builder(dataFolder).build();
    platform.host().runGame(FakeGameContent.v26_3());
    try (TestAdapt adapt = TestAdapt.start(platform, CoreContent.skillTypes())) {
      List<TextKey> quips = new ArrayList<>(List.of(MutationMessages.AVAILABLE_QUIP));
      quips.addAll(ToastQuips.UPGRADE);
      quips.addAll(ToastQuips.MUTATED);
      quips.addAll(ToastQuips.SIGNATURE);
      for (Skill<?> skill : adapt.core().getAdaptServer().getSkillRegistry().getSkills()) {
        SkillPresentation presentation = ((SimpleSkill<?>) skill).getPresentation();
        quips.add(presentation.quip());
        quips.addAll(presentation.unlockQuips());
      }

      Set<String> seen = new LinkedHashSet<>();
      for (TextKey quip : quips) {
        String text = AdaptLanguage.text(quip);
        assertThat(text).describedAs(quip.id()).isNotBlank().hasSizeLessThanOrEqualTo(ENGLISH_LIMIT)
            .doesNotContain("—", "–", "\n", "!", "/");
        assertThat(seen.add(text)).describedAs("repeated quip " + text).isTrue();
      }
      assertThat(quips).hasSize(1 + 6 + 3 * 24);
    }
  }

  private static void expect(List<String> failures, String where, String body, List<TextKey> pool) {
    List<String> allowed = new ArrayList<>();
    for (TextKey quip : pool) {
      allowed.add(ClientToasts.toast(ToastKind.GENERIC, IconRef.item("minecraft:book"), "", AdaptLanguage.text(quip), "").body());
    }
    if (!allowed.contains(body)) {
      failures.add(where + ": " + body);
    }
  }
}
