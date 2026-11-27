package art.arcane.adapt.addon;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.AddonHandle;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.player.PlayerSection;
import art.arcane.adapt.api.world.BlockSlice;
import art.arcane.adapt.engine.addon.DeclaredData;
import art.arcane.adapt.engine.addon.EngineRegistrar;
import art.arcane.adapt.engine.player.PlayerApi;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Player sections and block slices are declared through the registrar and read only as declared. */
class DeclaredDataTest {
  private static final Id JOURNAL = Id.of("notes", "journal");

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private AddonHandle notes;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    adapt = TestAdapt.start(platform);
    notes = AdaptApi.addons().install(new NotesAddon());
  }

  @AfterEach
  void stop() {
    notes.uninstall();
    adapt.close();
  }

  @Test
  void theRegistrarNamesTheSectionAfterTheAddonAndTheProfileReadsIt() {
    PlayerProfile profile = join("Scribe");

    Journal journal = profile.section(NotesAddon.journal);
    journal.lines.add("first");

    assertThat(NotesAddon.journal.id()).isEqualTo(JOURNAL);
    assertThat(profile.section(NotesAddon.journal)).isSameAs(journal);
    assertThat(AdaptApi.players().peek(profile.uuid(), NotesAddon.journal)).isSameAs(journal);
  }

  @Test
  void aSectionNoRegistrarHandedOutIsRefusedNamingTheSectionItsOwnerAndTheCaller() {
    PlayerProfile profile = join("Thief");
    PlayerSection<Journal> forged = new PlayerSection<>() {
      @Override
      public Id id() {
        return JOURNAL;
      }

      @Override
      public Class<Journal> type() {
        return Journal.class;
      }
    };

    assertThatThrownBy(() -> profile.section(forged))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("notes:journal")
        .hasMessageContaining("addon notes")
        .hasMessageContaining(DeclaredDataTest.class.getName());
    assertThatThrownBy(() -> AdaptApi.players().peek(profile.uuid(), forged))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("notes:journal");
  }

  @Test
  void anUninstalledAddonsSectionIsRefusedAndAReinstallHandsOutTheSameObject() {
    PlayerProfile profile = join("Archivist");
    PlayerSection<Journal> first = NotesAddon.journal;

    notes.uninstall();

    assertThatThrownBy(() -> profile.section(first))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("notes:journal")
        .hasMessageContaining("not declared by an installed addon")
        .hasMessageContaining(DeclaredDataTest.class.getName());

    notes = AdaptApi.addons().install(new NotesAddon());

    assertThat(NotesAddon.journal).isSameAs(first);
    assertThat(profile.section(first)).isNotNull();
  }

  @Test
  void aSliceFromAnEarlierInstallReadsWithTheCodecOfTheNewOne() {
    BlockSlice<String> first = NotesAddon.marks;
    BlockSlice.Codec<String> firstCodec = first.codec();

    notes.uninstall();
    assertThatThrownBy(() -> DeclaredData.requireSlice(first))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("notes:marks");

    notes = AdaptApi.addons().install(new NotesAddon());

    BlockSlice<String> live = DeclaredData.requireSlice(first);
    assertThat(live.codec()).isNotSameAs(firstCodec).isSameAs(NotesAddon.marks.codec());
    assertThat(live.id()).isEqualTo(Id.of("notes", "marks"));
  }

  @Test
  void aPathDeclaredTwiceIsRefused() {
    EngineRegistrar registrar = new EngineRegistrar("twice");
    registrar.playerSection("journal", Journal.class, Journal::new);
    registrar.blockSlice("journal", String.class, new MarkCodec());

    assertThatThrownBy(() -> registrar.playerSection("journal", Journal.class, Journal::new))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("twice:journal");
    assertThatThrownBy(() -> registrar.blockSlice("journal", String.class, new MarkCodec()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("twice:journal");
  }

  private PlayerProfile join(String name) {
    FakePlayer player = platform.player(name, 0.5D, 61D, 0.5D);
    AdaptPlayer adaptPlayer = adapt.join(player);
    return PlayerApi.profile(adaptPlayer);
  }

  public static final class Journal {
    private final List<String> lines = new ArrayList<>();
  }

  private static final class MarkCodec implements BlockSlice.Codec<String> {
    @Override
    public void write(String value, DataOutputStream out) throws IOException {
      out.writeUTF(value);
    }

    @Override
    public String read(DataInputStream in) throws IOException {
      return in.readUTF();
    }
  }

  private static final class NotesAddon implements AdaptAddon {
    private static PlayerSection<Journal> journal;
    private static BlockSlice<String> marks;

    @Override
    public String namespace() {
      return "notes";
    }

    @Override
    public void register(ContentRegistrar registrar) {
      journal = registrar.playerSection("journal", Journal.class, Journal::new);
      marks = registrar.blockSlice("marks", String.class, new MarkCodec());
    }
  }
}
