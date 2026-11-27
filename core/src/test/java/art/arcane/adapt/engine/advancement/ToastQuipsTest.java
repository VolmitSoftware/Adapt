package art.arcane.adapt.engine.advancement;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.server.hud.ClientToasts;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.engine.skill.SkillHost;
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
    try (TestAdapt adapt = TestAdapt.start(platform, new BuiltinAddon())) {
      SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
      List<String> failures = new ArrayList<>();
      int unlocks = 0;
      for (Skill<?> skill : registry.getSkills()) {
        SkillHost host = SkillHost.of(skill);
        AdaptAdvancement root = host.buildAdvancements();
        ServerFrame.Toast discovered = root.clientToast(host.getLineKey());
        assertThat(discovered.kind()).isEqualTo(ToastKind.DISCOVERY);
        expect(failures, root.getKey(), discovered.body(), List.of(host.quip()));
        for (AdaptAdvancement child : root.getChildren()) {
          if (child.getKey().startsWith("adaptation/")) {
            expect(failures, child.getKey(), child.clientToast(host.getLineKey()).body(), host.unlockQuips());
            unlocks++;
          }
        }
      }
      int mutated = 0;
      for (Skill<?> lead : registry.getSkills()) {
        for (Skill<?> partner : registry.getSkills()) {
          MutatedSkill skill = lead == partner ? null : registry.getMutatedSkill(new SkillPair(lead.getId(), partner.getId()));
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
            expect(failures, skill.lineKey() + "/" + adaptation.getId().toString(),
                AdvancementManager.unlockToast(VIEWER, adaptation).body(), texts(pool));
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
    try (TestAdapt adapt = TestAdapt.start(platform, new BuiltinAddon())) {
      List<TextKey> shared = new ArrayList<>(List.of(MutationMessages.AVAILABLE_QUIP));
      shared.addAll(ToastQuips.UPGRADE);
      shared.addAll(ToastQuips.MUTATED);
      shared.addAll(ToastQuips.SIGNATURE);
      List<String> quips = new ArrayList<>(texts(shared));
      for (Skill<?> skill : adapt.core().getAdaptServer().getSkillRegistry().getSkills()) {
        SkillHost host = SkillHost.of(skill);
        quips.add(host.quip());
        quips.addAll(host.unlockQuips());
      }

      Set<String> seen = new LinkedHashSet<>();
      for (String text : quips) {
        assertThat(text).describedAs(text).isNotBlank().hasSizeLessThanOrEqualTo(ENGLISH_LIMIT)
            .doesNotContain("—", "–", "\n", "!", "/");
        assertThat(seen.add(text)).describedAs("repeated quip " + text).isTrue();
      }
      assertThat(quips).hasSize(1 + 6 + 3 * 24);
    }
  }

  private static List<String> texts(List<TextKey> keys) {
    List<String> texts = new ArrayList<>();
    for (TextKey key : keys) {
      texts.add(AdaptLanguage.text(key));
    }
    return texts;
  }

  private static void expect(List<String> failures, String where, String body, List<String> pool) {
    List<String> allowed = new ArrayList<>();
    for (String quip : pool) {
      allowed.add(ClientToasts.toast(ToastKind.GENERIC, IconRef.item("minecraft:book"), "", quip, "").body());
    }
    if (!allowed.contains(body)) {
      failures.add(where + ": " + body);
    }
  }
}
