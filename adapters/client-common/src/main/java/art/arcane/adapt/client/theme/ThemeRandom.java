package art.arcane.adapt.client.theme;

/**
 * The demo's seeded numbers, bit for bit: a 32-bit xorshift stream and an FNV-1a string hash, so a page composes the
 * same here as in the approved demo. {@link #h01} hashes {@code prefix + ":" + value} without building the string.
 */
final class ThemeRandom {
    private static final int FNV_OFFSET = 0x811C9DC5;
    private static final int FNV_PRIME = 16777619;
    private static final double TWO_32 = 4294967296.0;

    private int state;

    ThemeRandom(long seed) {
        int folded = (int) seed;
        state = folded == 0 ? 1 : folded;
    }

    /** The next value in [0, 1). */
    double next() {
        int s = state;
        s ^= s << 13;
        s ^= s >>> 17;
        s ^= s << 5;
        state = s;
        return Integer.toUnsignedLong(s) / TWO_32;
    }

    /** FNV-1a over the string's UTF-16 units, as the demo's {@code hs}. */
    static int hash(String text) {
        return feed(FNV_OFFSET, text);
    }

    static long unsigned(int hash) {
        return Integer.toUnsignedLong(hash);
    }

    static int feed(int hash, String text) {
        int h = hash;
        for (int index = 0; index < text.length(); index++) {
            h ^= text.charAt(index);
            h *= FNV_PRIME;
        }
        return h;
    }

    static int feed(int hash, char unit) {
        return (hash ^ unit) * FNV_PRIME;
    }

    /** Feeds the decimal digits of {@code value}, as JavaScript prints an integer. */
    static int feed(int hash, long value) {
        int h = hash;
        if (value < 0) {
            h = feed(h, '-');
        }
        long magnitude = Math.abs(value);
        long scale = 1;
        while (magnitude / scale >= 10) {
            scale *= 10;
        }
        for (; scale > 0; scale /= 10) {
            h = feed(h, (char) ('0' + magnitude / scale % 10));
        }
        return h;
    }

    /** The hash state after {@code prefix + ":"}, to finish with {@link #h01(int, long)}. */
    static int prefix(String prefix) {
        return feed(feed(FNV_OFFSET, prefix), ':');
    }

    /** The demo's {@code h01(prefix, value)} for an integer value, from {@link #prefix}. */
    static double h01(int prefixState, long value) {
        return unsigned(feed(prefixState, value)) / TWO_32;
    }

    static double h01(String prefix, long value) {
        return h01(prefix(prefix), value);
    }
}
