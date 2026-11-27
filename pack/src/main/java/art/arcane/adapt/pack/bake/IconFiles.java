package art.arcane.adapt.pack.bake;

import art.arcane.adapt.platform.value.Id;

/** 16x16 icon sprites by sprite id such as {@code adapt:gui/skill/agility}, and PNG size reading. */
public final class IconFiles {
    public static final int SIZE = 16;

    private IconFiles() {
    }

    /** An icon: its sprite id and its PNG. */
    public record Icon(Id id, byte[] png) {
        public String sprite() {
            return id.toString();
        }

        public String namespace() {
            return id.namespace();
        }

        /** The sprite's path under {@code textures/}, such as {@code gui/skill/agility}. */
        public String path() {
            return id.path();
        }
    }

    static int width(byte[] png) {
        return png.length < 24 ? -1 : readInt(png, 16);
    }

    static int height(byte[] png) {
        return png.length < 24 ? -1 : readInt(png, 20);
    }

    private static int readInt(byte[] bytes, int at) {
        return (bytes[at] & 0xFF) << 24 | (bytes[at + 1] & 0xFF) << 16 | (bytes[at + 2] & 0xFF) << 8 | bytes[at + 3] & 0xFF;
    }
}
