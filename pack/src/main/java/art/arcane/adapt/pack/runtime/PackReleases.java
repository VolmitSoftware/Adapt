package art.arcane.adapt.pack.runtime;

import art.arcane.adapt.pack.link.PackRelease;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * The released pack a server sends players to: {@code pack.releaseUrl} with the Adapt version filled in, and the SHA-1
 * of the stock pack, which the build bakes and stores in the plugin jar at {@link #STOCK_SHA1}.
 */
public final class PackReleases {
    public static final String STOCK_SHA1 = "/art/arcane/adapt/pack/release.sha1";
    public static final String VERSION = "{version}";
    private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");

    private PackReleases() {
    }

    /** The release at {@code template} for Adapt {@code adapt}; null when the template is blank or the stock hash is unknown. */
    public static PackRelease of(String template, String adapt, String stockSha1) {
        if (template.isBlank() || stockSha1 == null) {
            return null;
        }
        return new PackRelease(template.replace(VERSION, adapt), stockSha1);
    }

    /** The stock pack's SHA-1 stored next to {@code anchor}'s classes, or null when the jar holds none. */
    public static String stockSha1(Class<?> anchor) {
        try (InputStream in = anchor.getResourceAsStream(STOCK_SHA1)) {
            return in == null ? null : sha1(new String(in.readAllBytes(), StandardCharsets.US_ASCII));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** {@code text} trimmed when it is a lowercase SHA-1 hex, else null. */
    static String sha1(String text) {
        String trimmed = text.trim();
        return SHA1.matcher(trimmed).matches() ? trimmed : null;
    }
}
