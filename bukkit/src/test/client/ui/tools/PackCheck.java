import art.arcane.adapt.pack.bake.PackValidator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Runs the pack bake's validator over a built pack zip and prints one problem per line. Exit 1 when any is found. */
public final class PackCheck {
    private PackCheck() {
    }

    public static void main(String[] args) throws IOException {
        Path zip = Path.of(args[0]);
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (InputStream input = Files.newInputStream(zip); ZipInputStream entries = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = entries.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    files.put(entry.getName(), entries.readAllBytes());
                }
            }
        }
        List<String> problems = PackValidator.check(files);
        for (String problem : problems) {
            System.out.println(problem);
        }
        System.exit(problems.isEmpty() ? 0 : 1);
    }
}
