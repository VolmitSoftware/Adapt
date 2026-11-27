package art.arcane.adapt.engine.addon;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.AddonHandle;
import art.arcane.adapt.api.addon.AddonState;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.text.MessageBundle;
import art.arcane.adapt.api.text.MessageSink;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** A rejected addon's reason names what was being loaded: the content id, or the message file, key and line. */
class AddonLoadErrorTest {
  @TempDir
  Path data;

  private TestAdapt adapt;

  @AfterEach
  void stop() {
    if (adapt != null) {
      adapt.close();
    }
  }

  private String rejection(Consumer<ContentRegistrar> register) {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());
    AddonHandle handle = AdaptApi.addons().install(new AdaptAddon() {
      @Override
      public String namespace() {
        return "example";
      }

      @Override
      public void register(ContentRegistrar registrar) {
        register.accept(registrar);
      }
    });
    assertThat(handle.state()).isEqualTo(AddonState.REJECTED);
    return handle.rejection().orElseThrow();
  }

  @Test
  void anInvalidTomlResourceNamesItsFileLineAndKey() {
    String reason = rejection(registrar ->
        registrar.messages(MessageBundle.resources(AddonLoadErrorTest.class, "addon-messages/clash")));

    assertThat(reason).contains("example:en_US").contains("addon-messages/clash/en_US.toml")
        .contains("line 7").contains("[preference.delivery]");
  }

  @Test
  void anInvalidTomlDocumentWrittenInCodeNamesItsLocaleAndLine() {
    String reason = rejection(registrar -> registrar.messages(new MessageBundle() {
      @Override
      public List<String> locales() {
        return List.of(MessageBundle.DEFAULT_LOCALE);
      }

      @Override
      public void load(String locale, MessageSink sink) {
        sink.toml("first = \"one\"\nfirst = \"two\"\n");
      }
    }));

    assertThat(reason).contains("example:en_US").contains("line 2").contains("first = \"two\"");
  }

  @Test
  void aKeyDefinedTwiceNamesTheKeyAndTheFileThatRedefinesIt() {
    String reason = rejection(registrar -> {
      registrar.messages(MessageBundle.builder().message("skill.geology.name", "Geology").build());
      registrar.messages(MessageBundle.resources(AddonLoadErrorTest.class, "addon-messages/table"));
    });

    assertThat(reason).contains("example.skill.geology.name").contains("addon-messages/table/en_US.toml");
  }

  @Test
  void aSkillWhoseFactoryFailsIsNamed() {
    String reason = rejection(registrar -> registrar.skill(Id.of("example", "geology"), () -> {
      throw new IllegalStateException("factory broke");
    }));

    assertThat(reason).contains("example:geology").contains("factory broke");
  }
}
