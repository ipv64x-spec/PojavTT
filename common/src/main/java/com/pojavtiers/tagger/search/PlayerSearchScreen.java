package com.pojavtiers.tagger.search;

import com.pojavtiers.tagger.gui.ConfigScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The dedicated Player Search sub-screen, opened from the "Search player..."
 * button at the top of {@link ConfigScreen}. Lets the user type an IGN and
 * submit it; on success it opens {@link PlayerResultScreen} with the bust +
 * tier data, and on "not found" it shows an inline message right here rather
 * than navigating away, so the user can immediately try another name.
 * <p>
 * Deliberately built from plain, long-standing widgets ({@link TextFieldWidget},
 * {@link ButtonWidget}) rather than anything version-sensitive, so this one
 * screen class works unmodified across every Minecraft-version module - no
 * {@code CompatUtil} routing needed here.
 */
public class PlayerSearchScreen extends Screen {
    private final Screen parent;

    private TextFieldWidget nameField;
    private ButtonWidget searchButton;

    private boolean searching = false;
    private Text statusMessage = null;
    private int statusColor = 0xFFFFFF;

    public PlayerSearchScreen(Screen parent) {
        super(Text.literal("Search Player"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int fieldWidth = 200;
        int centerX = this.width / 2;
        int fieldY = this.height / 2 - 40;

        this.nameField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, fieldY, fieldWidth, 20,
                Text.literal("Username"));
        this.nameField.setMaxLength(16);
        this.nameField.setPlaceholder(Text.literal("Enter a player's IGN...").formatted(Formatting.DARK_GRAY));
        this.addSelectableChild(this.nameField);
        this.setInitialFocus(this.nameField);

        this.searchButton = ButtonWidget.builder(Text.literal("Search"), b -> this.doSearch())
                .dimensions(centerX - fieldWidth / 2, fieldY + 28, fieldWidth, 20)
                .build();
        this.addDrawableChild(this.searchButton);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close())
                .dimensions(centerX - fieldWidth / 2, fieldY + 56, fieldWidth, 20)
                .build());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter submits the search, same as clicking the button.
        if ((keyCode == 257 || keyCode == 335) && this.nameField.isFocused()) {
            doSearch();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void doSearch() {
        String username = this.nameField.getText().trim();
        if (username.isEmpty() || searching) return;

        searching = true;
        statusMessage = Text.literal("Searching...").formatted(Formatting.YELLOW);
        searchButton.active = false;

        PlayerLookupService.search(username).whenComplete((result, throwable) ->
                MinecraftClient.getInstance().execute(() -> {
                    searching = false;
                    searchButton.active = true;

                    if (throwable != null || result instanceof PlayerLookupService.SearchResult.NotFound) {
                        // "Cool message" for a name that isn't in the tier database at all.
                        statusMessage = Text.literal("Player not found in the tier list.").formatted(Formatting.RED);
                        return;
                    }

                    if (result instanceof PlayerLookupService.SearchResult.Found found) {
                        statusMessage = null;
                        MinecraftClient.getInstance().setScreen(new PlayerResultScreen(this, found));
                    }
                }));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 64, 0xFFFFFF);
        this.nameField.render(context, mouseX, mouseY, delta);

        if (statusMessage != null) {
            context.drawCenteredTextWithShadow(this.textRenderer, statusMessage,
                    this.width / 2, this.height / 2 + 30, statusColor);
        }
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }
}
