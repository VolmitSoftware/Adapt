package art.arcane.adapt.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** The native screen that shows a {@link UiScreen}. */
public final class MinecraftUiScreen extends Screen {
    private final MinecraftUiHost host;
    private final MinecraftUiInput input;
    private final UiScreen content;
    private final MinecraftUiDraw draw = new MinecraftUiDraw();

    MinecraftUiScreen(MinecraftUiHost host, MinecraftUiInput input, UiScreen content) {
        super(Component.literal(content.title()));
        this.host = host;
        this.input = input;
        this.content = content;
    }

    UiScreen content() {
        return content;
    }

    private static UiMouseEvent mouse(MouseButtonEvent event) {
        return new UiMouseEvent(event.x(), event.y(), MinecraftUiKeys.button(event.button()), MinecraftUiInput.modifiers(event.modifiers()));
    }

    private static UiKeyEvent key(KeyEvent event) {
        return new UiKeyEvent(MinecraftUiKeys.key(event.key()), MinecraftUiInput.modifiers(event.modifiers()));
    }

    @Override
    protected void init() {
        content.attach(host, input, width, height);
    }

    @Override
    public void added() {
        super.added();
        host.shown(this);
    }

    @Override
    public void removed() {
        host.hidden(this);
        content.removed();
        super.removed();
    }

    @Override
    public void tick() {
        content.tick();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (content.dimsBackground()) {
            super.extractBackground(graphics, mouseX, mouseY, delta);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        content.render(draw.bind(graphics), mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return content.mouseClicked(mouse(event)) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return content.mouseReleased(mouse(event)) || super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return content.mouseDragged(mouse(event), dragX, dragY) || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        content.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return content.mouseScrolled(new UiScrollEvent(mouseX, mouseY, horizontal, vertical))
                || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return content.keyPressed(key(event)) || super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        return content.keyReleased(key(event)) || super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return content.charTyped(new UiCharEvent(event.codepoint(), input.modifiers())) || super.charTyped(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return content.closesOnEscape();
    }

    @Override
    public boolean isPauseScreen() {
        return content.pausesGame();
    }
}
