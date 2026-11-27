package art.arcane.adapt.pack.link;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PackExportTest {
  @TempDir
  Path directory;

  @Test
  void writesTheZipAndItsFiles() throws IOException {
    PackArtifact artifact = TestPacks.pack("export");

    PackExport.export(artifact, directory);

    assertThat(Files.readAllBytes(directory.resolve(PackExport.ZIP))).isEqualTo(artifact.open().readAllBytes());
    assertThat(Files.readString(directory.resolve(PackExport.FOLDER).resolve("pack.mcmeta"))).contains("export");
    assertThat(directory.resolve(PackExport.FOLDER).resolve("assets/adapt/lang/en_us.json")).exists();
  }

  @Test
  void aNewExportReplacesTheOldFiles() throws IOException {
    PackExport.export(TestPacks.pack("old"), directory);
    Path stale = directory.resolve(PackExport.FOLDER).resolve("stale.txt");
    Files.writeString(stale, "x");

    PackExport.export(TestPacks.pack("new"), directory);

    assertThat(stale).doesNotExist();
    assertThat(Files.readString(directory.resolve(PackExport.FOLDER).resolve("pack.mcmeta"))).contains("new");
    try (var files = Files.list(directory)) {
      assertThat(files.map((Path path) -> path.getFileName().toString()))
          .containsExactlyInAnyOrder(PackExport.ZIP, PackExport.FOLDER, PackExport.HASH);
    }
  }

  @Test
  void theSamePackIsNotWrittenAgain() throws IOException {
    PackArtifact artifact = TestPacks.pack("same");
    assertThat(PackExport.export(artifact, directory)).isTrue();
    Path kept = directory.resolve(PackExport.FOLDER).resolve("kept.txt");
    Files.writeString(kept, "x");

    assertThat(PackExport.export(artifact, directory)).isFalse();

    assertThat(kept).exists();
    assertThat(Files.readString(directory.resolve(PackExport.HASH))).isEqualTo(artifact.sha1Hex());
  }

  @Test
  void aMissingExportIsWrittenAgainEvenWithTheSameHash() throws IOException {
    PackArtifact artifact = TestPacks.pack("same");
    PackExport.export(artifact, directory);
    Files.delete(directory.resolve(PackExport.ZIP));

    assertThat(PackExport.export(artifact, directory)).isTrue();

    assertThat(Files.readAllBytes(directory.resolve(PackExport.ZIP))).isEqualTo(artifact.open().readAllBytes());
  }

  @Test
  void aChangedPackReplacesTheHash() throws IOException {
    PackExport.export(TestPacks.pack("old"), directory);
    PackArtifact next = TestPacks.pack("new");

    assertThat(PackExport.export(next, directory)).isTrue();

    assertThat(Files.readString(directory.resolve(PackExport.HASH))).isEqualTo(next.sha1Hex());
  }

  @Test
  void entriesOutsideTheFolderAreRefused() throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      zip.putNextEntry(new ZipEntry("../escape.txt"));
      zip.write("x".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }

    assertThatThrownBy(() -> PackExport.export(PackArtifact.of(bytes.toByteArray(), 84, 97), directory))
        .isInstanceOf(IOException.class);
    assertThat(directory.resolveSibling("escape.txt")).doesNotExist();
  }
}
