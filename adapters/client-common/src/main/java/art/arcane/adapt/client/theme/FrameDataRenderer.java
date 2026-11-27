package art.arcane.adapt.client.theme;

import art.arcane.adapt.api.client.FrameCanvas;

/** Draws a {@link FrameData} on a {@link FrameCanvas}, through the same calls a hover frame written in code makes. */
final class FrameDataRenderer {
    /** How far apart in seconds the idle loops of a piece's successive positions run. */
    private static final double PHASE = 0.37;
    private static final double TAU = Math.PI * 2.0;

    private FrameDataRenderer() {
    }

    static void draw(FrameData data, FrameCanvas canvas) {
        for (FrameData.Line line : data.lines()) {
            line(line, canvas);
        }
        for (FrameData.Piece piece : data.pieces()) {
            piece(piece, canvas);
        }
    }

    private static void line(FrameData.Line line, FrameCanvas canvas) {
        int ring = canvas.ringLength();
        int full = line.way() == 0 ? ring >> 1 : ring;
        int grown = (int) Math.floor(progress(canvas, line.start(), line.length(), line.ease()) * full);
        if (grown <= 0) {
            return;
        }
        int from = spot(line.from(), canvas);
        int shift = (int) Math.floor(canvas.seconds() * line.speed());
        if (line.way() == 0) {
            stroke(line, canvas, from, 1, 0, grown, full, shift);
            stroke(line, canvas, from, -1, 1, grown, full, shift);
        } else {
            stroke(line, canvas, from, line.way(), 0, grown, full, shift);
        }
    }

    /** One way of a line, pixels {@code first} to {@code grown} from {@code from}, with its head while still growing. */
    private static void stroke(FrameData.Line line, FrameCanvas canvas, int from, int dir, int first, int grown, int full,
                               int shift) {
        char[] roles = line.roles();
        int[] wave = line.wave();
        for (int i = first; i < grown; i++) {
            int lift = wave == null ? 0 : wave[Math.floorMod(i + shift, wave.length)];
            canvas.pixel(from + dir * i, line.offset() + lift, roles[i % roles.length], line.alpha());
        }
        if (line.head() != null && grown < full) {
            canvas.sprite(line.head(), from + dir * grown, canvas.options().flip(dir < 0));
        }
    }

    private static void piece(FrameData.Piece piece, FrameCanvas canvas) {
        int count = piece.spread() > 0 ? piece.spread() : piece.at().length;
        int ring = canvas.ringLength();
        for (int index = 0; index < count; index++) {
            double start = piece.start() + index * piece.stagger();
            double p = progress(canvas, start, piece.length(), piece.ease());
            if (p <= 0.0) {
                continue;
            }
            double alpha = piece.alpha();
            double outward = piece.outward();
            double dx = 0.0;
            double dy = 0.0;
            if (!canvas.leaving() || piece.leave() == FrameData.Leave.REVERSE) {
                switch (piece.enter()) {
                    case FADE -> alpha *= p;
                    case POP -> {
                        if (p < 1.0) {
                            continue;
                        }
                    }
                    case SLIDE -> {
                        outward += piece.distance() * (1.0 - p);
                        alpha *= p;
                    }
                    case DROP -> {
                        dy -= piece.distance() * (1.0 - p);
                        alpha *= p;
                    }
                }
            } else {
                double gone = 1.0 - p;
                alpha *= p;
                switch (piece.leave()) {
                    case FALL -> dy += piece.distance() * gone;
                    case RISE -> dy -= piece.distance() * gone;
                    case AWAY -> outward += piece.distance() * gone;
                    default -> {
                    }
                }
            }
            double clock = canvas.seconds() + index * PHASE;
            if (piece.bob() != 0.0) {
                outward += Math.round(piece.bob() * (0.5 - 0.5 * Math.cos(TAU * clock / piece.bobPeriod())));
            }
            if (piece.pulse() != 0.0) {
                alpha *= 1.0 - piece.pulse() * (0.5 + 0.5 * Math.sin(TAU * clock / piece.pulsePeriod()));
            }
            if (piece.driftX() != 0.0 || piece.driftY() != 0.0) {
                double cycle = clock / piece.driftPeriod();
                double q = cycle - Math.floor(cycle);
                dx += piece.driftX() * q;
                dy += piece.driftY() * q;
                alpha *= Math.sin(Math.PI * q);
            }
            double s = piece.spread() > 0 ? spot(piece.at()[0], canvas) + (double) index * ring / piece.spread()
                    : spot(piece.at()[index], canvas);
            s += canvas.seconds() * piece.travel();
            String[] sprites = piece.sprites();
            if (sprites.length == 0) {
                FrameData.Paint paint = piece.pixel();
                int offset = (int) Math.round(outward);
                if (paint.role() != 0) {
                    canvas.pixel(s, offset, paint.role(), alpha);
                } else {
                    canvas.pixelArgb(s, offset, paint.argb(), alpha);
                }
                continue;
            }
            String key = sprites.length == 1 ? sprites[0]
                    : sprites[Math.floorMod((int) Math.floor(canvas.seconds() * piece.fps()) + index, sprites.length)];
            FrameCanvas.SpriteOptions options = canvas.options().flip(piece.flip()).sink(piece.sink()).outward(outward).dx(dx)
                    .dy(dy).alpha(alpha).tint(piece.tint());
            if (piece.upright()) {
                options.upright();
            }
            canvas.sprite(key, s, options);
        }
    }

    /** Eased assembly progress through the window starting at {@code start}, 0 to 1. */
    private static double progress(FrameCanvas canvas, double start, double length, FrameData.Ease ease) {
        double p = length <= 0.0 ? canvas.assembled() >= start ? 1.0 : 0.0 : canvas.progress(start, start + length);
        return ease.apply(p);
    }

    /** The ring position of {@code spot} on this canvas. */
    static int spot(FrameData.Spot spot, FrameCanvas canvas) {
        if (spot.anchor() < 0) {
            return (int) Math.floor(spot.fraction() * canvas.ringLength());
        }
        int base = switch (spot.anchor()) {
            case 0 -> canvas.topLeft();
            case 1 -> canvas.topRight();
            case 2 -> canvas.bottomRight();
            case 3 -> canvas.bottomLeft();
            case 4 -> canvas.topMiddle();
            case 5 -> canvas.rightMiddle();
            case 6 -> canvas.bottomMiddle();
            default -> canvas.leftMiddle();
        };
        return base + spot.offset();
    }
}
