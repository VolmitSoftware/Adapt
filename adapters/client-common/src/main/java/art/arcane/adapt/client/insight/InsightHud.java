package art.arcane.adapt.client.insight;

import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.config.ClientConfig;
import art.arcane.adapt.client.net.ClientNet;
import art.arcane.adapt.client.net.InsightSink;
import art.arcane.adapt.client.qa.UiProbe;
import art.arcane.adapt.client.ui.UiCamera;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiEntityPose;
import art.arcane.adapt.client.ui.UiHost;
import art.arcane.adapt.client.ui.UiProjection;
import art.arcane.adapt.client.ui.UiScreenPoint;
import art.arcane.adapt.client.ui.UiWorld;

import java.util.Locale;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Draws what the server's Insight reveals, anchored to the entities on screen: a plate over the focused target's head
 * that opens into the full readout while the crosshair is on the target and folds into a slim health bar otherwise,
 * and a short bar over each nearby tracked creature. Positions come from the client's own interpolated entities and
 * camera every frame, so nothing waits on the network to move.
 */
public final class InsightHud {
    static final double HEAD_CLEARANCE = 0.25D;
    static final int MARK_WIDTH = 14;
    static final int MARK_HEIGHT = 2;
    private static final int MAX_OBSTACLES = 48;

    private final UiHost host;
    private final UiWorld world;
    private final ClientNet net;
    private final ClientConfig config;
    private final InsightSettings settings;
    private final LongSupplier clock;
    private final InsightState state;
    private final InsightFocus focus = new InsightFocus();
    private final double[] obstacleBoxes = new double[MAX_OBSTACLES * 4];
    private InsightObstacles obstacles = InsightObstacles.NONE;
    private InsightBox lastPlate;

    public InsightHud(UiHost host, UiWorld world, ClientNet net, LongSupplier clock) {
        this.host = Objects.requireNonNull(host, "host");
        this.world = Objects.requireNonNull(world, "world");
        this.net = Objects.requireNonNull(net, "net");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.config = net.config();
        this.settings = config.section(InsightSettings.SECTION, InsightSettings.class);
        this.state = new InsightState(clock);
        if (settings.enabled()) {
            net.install(net.sinks().withInsight(state));
        }
    }

    public InsightState state() {
        return state;
    }

    public InsightSettings settings() {
        return settings;
    }

    /** Turns the native readout on or off; off withdraws the capability so the server falls back to its own. */
    public void enabled(boolean enabled) {
        settings.enabled(enabled);
        config.save();
        net.install(net.sinks().withInsight(enabled ? state : InsightSink.NONE));
        if (!enabled) {
            state.reset();
        }
    }

    /** The boxes the target plate moves off, read each frame. */
    public void avoid(InsightObstacles source) {
        obstacles = Objects.requireNonNull(source, "source");
    }

    /** Where the target plate was last drawn, in GUI units; null when none was. */
    public InsightBox lastPlate() {
        return lastPlate;
    }

    public void render(UiDraw draw, float partialTick) {
        draw(draw, partialTick);
        if (UiProbe.watched()) {
            UiProbe.insight(probe());
        }
    }

    private String probe() {
        InsightBox plate = lastPlate;
        ServerFrame.InsightTarget view = state.view();
        if (plate == null || view == null) {
            return "{\"target\":" + (view == null ? "null" : Integer.toString(view.entityId())) + ",\"plate\":null,\"marks\":"
                    + state.marks().size() + "}";
        }
        return String.format(Locale.ROOT, "{\"target\":%d,\"plate\":{\"x\":%.2f,\"y\":%.2f,\"w\":%.2f,\"h\":%.2f},"
                + "\"expanded\":%.3f,\"marks\":%d}", view.entityId(), plate.x(), plate.y(), plate.width(), plate.height(),
                focus.eased(), state.marks().size());
    }

    private void draw(UiDraw draw, float partialTick) {
        lastPlate = null;
        state.expire();
        if (!settings.enabled() || !state.active()) {
            return;
        }
        UiCamera camera = world.camera(partialTick);
        if (camera == null) {
            return;
        }
        double scale = InsightLayout.scale(settings.scale(), draw.guiScale());
        if (settings.showMarks()) {
            for (ServerFrame.InsightMarks.Mark mark : state.marks()) {
                drawMark(draw, camera, mark, partialTick, scale);
            }
        }
        drawTarget(draw, camera, state.view(), partialTick, scale);
    }

    private void drawMark(UiDraw draw, UiCamera camera, ServerFrame.InsightMarks.Mark mark, float partialTick, double scale) {
        UiEntityPose pose = world.entity(mark.entityId(), partialTick);
        if (pose == null) {
            return;
        }
        UiScreenPoint head = UiProjection.project(camera, pose.x(), pose.y() + pose.height() + HEAD_CLEARANCE, pose.z(),
                draw.width(), draw.height());
        if (head == null || head.x() < 0 || head.y() < 0 || head.x() > draw.width() || head.y() > draw.height()) {
            return;
        }
        double opacity = 0.85D * InsightLayout.fade(head.depth());
        draw.pushPose();
        draw.translate((float) (head.x() - MARK_WIDTH * scale / 2D), (float) (head.y() - (MARK_HEIGHT + 2) * scale));
        draw.scale((float) scale, (float) scale);
        bar(draw, 0, 0, MARK_WIDTH, MARK_HEIGHT, mark.health(), mark.health(), 0D, InsightPlate.accent(mark.kind()), opacity);
        draw.popPose();
    }

    private void drawTarget(UiDraw draw, UiCamera camera, ServerFrame.InsightTarget view, float partialTick, double scale) {
        UiEntityPose pose = world.entity(view.entityId(), partialTick);
        if (pose == null) {
            return;
        }
        int width = draw.width();
        int height = draw.height();
        UiScreenPoint head = UiProjection.project(camera, pose.x(), pose.y() + pose.height() + HEAD_CLEARANCE, pose.z(),
                width, height);
        if (head == null) {
            return;
        }
        UiScreenPoint feet = UiProjection.project(camera, pose.x(), pose.y(), pose.z(), width, height);
        boolean focused = feet != null && crosshairOn(camera, pose, head, feet, width, height);
        double expansion = focus.advance(view.entityId(), focused, clock.getAsLong());
        InsightPlate plate = InsightPlate.measure(view, state.health(), state.absorption(), settings, host,
                draw::textWidth, draw.lineHeight());
        double plateWidth = lerp(InsightPlate.COLLAPSED_WIDTH, plate.width(), expansion);
        double plateHeight = lerp(InsightPlate.BAR_HEIGHT, plate.height(), expansion);
        int obstacleCount = Math.min(MAX_OBSTACLES, obstacles.collect(obstacleBoxes));
        InsightBox box = InsightLayout.place(head.x(), head.y(), plateWidth * scale, plateHeight * scale, width, height,
                obstacleBoxes, obstacleCount);
        lastPlate = box;
        double opacity = InsightLayout.fade(head.depth());
        draw.pushPose();
        draw.translate((float) box.x(), (float) box.y());
        draw.scale((float) scale, (float) scale);
        int panel = InsightColors.alpha(InsightColors.PANEL, InsightColors.PANEL_OPACITY * opacity * expansion);
        if (panel != 0) {
            draw.fill(0, 0, (int) Math.round(plateWidth), (int) Math.round(plateHeight), panel);
        }
        double barX = lerp(0D, InsightPlate.PAD, expansion);
        double barY = lerp(0D, plate.barTop(), expansion);
        double barWidth = lerp(InsightPlate.COLLAPSED_WIDTH, plate.barWidth(), expansion);
        double max = view.maxHealth() > 0D ? view.maxHealth() : 1D;
        bar(draw, (int) Math.round(barX), (int) Math.round(barY), (int) Math.round(barWidth), InsightPlate.BAR_HEIGHT,
                state.health() / max, state.ghostHealth() / max, state.absorption() / max, InsightPlate.accent(view.kind()),
                opacity);
        double details = (expansion - 0.55D) / 0.45D;
        if (details > 0D) {
            plate.drawDetails(draw, state, details * opacity);
        }
        draw.popPose();
    }

    static boolean crosshairOn(UiCamera camera, UiEntityPose pose, UiScreenPoint head, UiScreenPoint feet, int width,
                               int height) {
        double half = pose.width() / 2D * UiProjection.pixelsPerBlock(camera, Math.min(head.depth(), feet.depth()), height);
        double centerX = width / 2D;
        double centerY = height / 2D;
        return centerX >= Math.min(head.x(), feet.x()) - half && centerX <= Math.max(head.x(), feet.x()) + half
                && centerY >= Math.min(head.y(), feet.y()) && centerY <= Math.max(head.y(), feet.y());
    }

    private static void bar(UiDraw draw, int x, int y, int width, int height, double health, double ghost,
                            double absorption, int accent, double opacity) {
        int track = InsightColors.alpha(InsightColors.TRACK, InsightColors.TRACK_OPACITY * opacity);
        if (track == 0) {
            return;
        }
        draw.fill(x - 1, y - 1, x + width + 1, y + height + 1, track);
        int filled = fraction(width, health);
        int ghosted = fraction(width, ghost);
        if (ghosted > filled) {
            draw.fill(x + filled, y, x + ghosted, y + height, InsightColors.alpha(InsightColors.GHOST, 0.75D * opacity));
        }
        if (filled > 0) {
            draw.fill(x, y, x + filled, y + height, InsightColors.alpha(accent, opacity));
        }
        int shield = fraction(width, absorption);
        if (shield > 0) {
            draw.fill(x, y + height - 1, x + shield, y + height, InsightColors.alpha(InsightColors.ABSORPTION, opacity));
        }
    }

    private static int fraction(int width, double value) {
        if (!(value > 0D)) {
            return 0;
        }
        return (int) Math.max(1L, Math.round(width * Math.min(1D, value)));
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }
}
