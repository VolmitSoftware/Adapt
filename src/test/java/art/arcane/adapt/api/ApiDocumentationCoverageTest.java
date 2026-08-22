package art.arcane.adapt.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ApiDocumentationCoverageTest {
  private static final Pattern PUBLIC_TOP_LEVEL_TYPE = Pattern.compile(
      "(?m)^public (?:final |abstract )?(?:class|interface|record|enum)\\s+|(?m)^public @interface\\s+"
  );

  @Test
  void everyPublicTopLevelApiTypeAppearsInTheNumberedApiDocs() throws IOException {
    Path projectRoot = Path.of(System.getProperty("user.dir"));
    Path apiSource = projectRoot.resolve("src/main/java/art/arcane/adapt/api");
    Path docs = projectRoot.resolve("../docs/adapt").normalize();
    String documentation = readApiDocumentation(docs);
    List<String> undocumented = new ArrayList<>();

    try (Stream<Path> files = Files.walk(apiSource)) {
      files.filter(path -> path.toString().endsWith(".java"))
          .sorted()
          .forEach(path -> collectUndocumentedType(path, documentation, undocumented));
    }

    assertThat(undocumented)
        .as("public top-level types under art.arcane.adapt.api must be named in ../docs/adapt/41-50")
        .isEmpty();
  }

  private static String readApiDocumentation(Path docs) throws IOException {
    StringBuilder documentation = new StringBuilder();
    try (Stream<Path> files = Files.list(docs)) {
      List<Path> apiDocs = files
          .filter(path -> path.getFileName().toString().matches("(?:4[1-9]|50)-api-.+\\.md"))
          .sorted(Comparator.comparing(path -> path.getFileName().toString()))
          .toList();
      assertThat(apiDocs).hasSize(10);
      for (Path apiDoc : apiDocs) {
        documentation.append(Files.readString(apiDoc)).append('\n');
      }
    }
    return documentation.toString();
  }

  private static void collectUndocumentedType(Path source, String documentation, List<String> undocumented) {
    try {
      String java = Files.readString(source);
      if (!PUBLIC_TOP_LEVEL_TYPE.matcher(java).find()) {
        return;
      }
      String fileName = source.getFileName().toString();
      String typeName = fileName.substring(0, fileName.length() - ".java".length());
      if (!Pattern.compile("\\b" + Pattern.quote(typeName) + "\\b").matcher(documentation).find()) {
        undocumented.add(typeName + " (" + source + ")");
      }
    } catch (IOException error) {
      throw new IllegalStateException("Failed to inspect " + source, error);
    }
  }
}
