package art.arcane.adapt.localization;

import art.arcane.adapt.Adapt;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class AdaptLanguageBundle {

  private AdaptLanguageBundle() {
  }

  public static File bundleFile(String locale) {
    return new File(AdaptLanguageReference.folder(), locale + ".toml");
  }

  public static boolean extract(String locale) {
    if (locale == null || !AdaptLanguage.LOCALE_NAME.matcher(locale).matches()) {
      return false;
    }
    if (AdaptMessages.catalog().englishLocale().equalsIgnoreCase(locale)) {
      return false;
    }

    String bundled = read(locale);
    if (bundled == null) {
      return false;
    }
    return LanguageFileWriter.write(bundleFile(locale).toPath(), "bundled locale", render(locale, bundled));
  }

  private static String read(String locale) {
    InputStream resource = Adapt.instance.getResource(locale + ".toml");
    if (resource == null) {
      return null;
    }
    try (InputStream stream = resource) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Throwable e) {
      Adapt.warn("Failed to read bundled locale " + locale + ": " + e.getMessage());
      return null;
    }
  }

  private static String render(String locale, String bundled) {
    StringBuilder out = new StringBuilder(bundled.length() + 256);
    for (String line : header(locale)) {
      out.append("# ").append(line).append('\n');
    }
    return out.append('\n').append(bundled).toString();
  }

  private static List<String> header(String locale) {
    return List.of(
        "Adapt bundled locale: " + locale,
        "Extracted from the bundled translation and regenerated on every boot and reload.",
        "Edits to this file are ignored.",
        "Copy the keys you want to change into languages/overrides/" + locale + ".toml."
    );
  }
}
