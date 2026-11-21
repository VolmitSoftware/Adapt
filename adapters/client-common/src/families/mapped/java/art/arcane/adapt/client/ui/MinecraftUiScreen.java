package art.arcane.adapt.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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

    private UiMouseEvent mouse(double mouseX, double mouseY, int button) {
        return new UiMouseEvent(mouseX, mouseY, MinecraftUiKeys.button(button), input.modifiers());
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
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (content.dimsBackground()) {
            super.renderBackground(graphics, mouseX, mouseY, delta);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        content.render(draw.bind(graphics), mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return content.mouseClicked(mouse(mouseX, mouseY, button)) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return content.mouseReleased(mouse(mouseX, mouseY, button)) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return content.mouseDragged(mouse(mouseX, mouseY, button), dragX, dragY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
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
    public boolean keyPressed(int key, int scancode, int modifiers) {
        return content.keyPressed(new UiKeyEvent(MinecraftUiKeys.key(key), MinecraftUiInput.modifiers(modifiers)))
                || super.keyPressed(key, scancode, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scancode, int modifiers) {
        return content.keyReleased(new UiKeyEvent(MinecraftUiKeys.key(key), MinecraftUiInput.modifiers(modifiers)))
                || super.keyReleased(key, scancode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return content.charTyped(new UiCharEvent(character, MinecraftUiInput.modifiers(modifiers))) || super.charTyped(character, modifiers);
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
