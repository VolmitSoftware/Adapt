package art.arcane.adapt.client.theme;

/** How the pieces of one slot move at idle: a breeze sway, a bob, faster frame steps for spinners, or none. */
enum DressMove {
    STILL,
    SWAY,
    BOB,
    SPIN,
    FLICK,
    DRIP;

    static DressMove of(String name) {
        if (name == null) {
            return STILL;
        }
        return switch (name) {
            case "sway" -> SWAY;
            case "bob" -> BOB;
            case "spin" -> SPIN;
            case "flick" -> FLICK;
            case "drip" -> DRIP;
            default -> STILL;
        };
    }
}
