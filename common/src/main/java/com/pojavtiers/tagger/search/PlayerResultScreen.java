package com.pojavtiers.tagger.search;

import com.pojavtiers.tagger.config.PojavConfig;
import com.pojavtiers.tagger.model.GameMode;
import com.pojavtiers.tagger.model.PlayerRanking;
import com.pojavtiers.tagger.util.CompatUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shows the result of a player search: a skin "bust" (head + hat overlay,
 * composited from the raw skin texture) beside that player's tier list, or
 * one of two "cool message" states if the player has no tiers yet.
 * <p>
 * The bust intentionally renders as a scaled 2D head icon rather than a full
 * 3D isometric body model. A full 3D bust (like some reference tier-list mods
 * use) needs a fair amount of version-sensitive entity-rendering code; a flat
 * head composite uses only long-standing, version-stable APIs
 * (NativeImageBackedTexture + a plain textured-quad draw), so it works
 * identically and reliably across every Minecraft-version module this mod
 * supports, with no risk of a "Resolution Scalar" or GUI-scale-related crash.
 */
public class PlayerResultScreen extends Screen {
    private static final Identifier STEVE_SKIN = Identifier.of("minecraft", "textures/entity/player/wide/steve.png");
    private static final int BUST_SIZE = 64; // on-screen size of the rendered head

    private final Screen parent;
    private final PlayerLookupService.SearchResult.Found data;

    private Identifier skinTexture;
    private boolean ownsSkinTexture = false; // true if we registered a dynamic texture we must clean up

    public PlayerResultScreen(Screen parent, PlayerLookupService.SearchResult.Found data) {
        super(Text.literal(data.resolvedName() + "'s Profile"));
        this.parent = parent;
        this.data = data;
    }

    @Override
    protected void init() {
        this.skinTexture = registerSkinTexture(data.skin());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close())
                .dimensions(this.width / 2 - 100, this.height - 30, 200, 20)
                .build());
    }

    private static final Identifier DYNAMIC_SKIN_ID = Identifier.of("pojavtiertagger", "dynamic/search_result_skin");

    /** Registers the downloaded skin bytes as a texture, or falls back to the bundled vanilla Steve skin. */
    private Identifier registerSkinTexture(PlayerLookupService.SkinResult skin) {
        if (skin == null || skin.type() == PlayerLookupService.AccountType.CRACKED || skin.skinPng() == null) {
            ownsSkinTexture = false;
            return STEVE_SKIN;
        }
        ownsSkinTexture = CompatUtil.registerDynamicTexture(DYNAMIC_SKIN_ID, skin.skinPng());
        return ownsSkinTexture ? DYNAMIC_SKIN_ID : STEVE_SKIN;
    }

    @Override
    public void removed() {
        super.removed();
        // Dynamic textures aren't freed automatically - avoid leaking GPU memory
        // if the user searches several different players in one session.
        if (ownsSkinTexture) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(DYNAMIC_SKIN_ID);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        boolean hasTier = data.ranking() != null && data.ranking().hasAnyTier();
        int contentTop = 50;

        int bustX = this.width / 2 - 120;
        int bustY = contentTop;
        drawBust(context, bustX, bustY);

        int infoX = this.width / 2 - 40;
        int infoY = contentTop;

        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Region: ").append(Text.literal(nullToUnknown(data.ranking() != null ? data.ranking().region : null))
                        .formatted(Formatting.AQUA)),
                infoX, infoY, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Points: ").append(Text.literal(String.valueOf(data.ranking() != null ? data.ranking().overallPoints : 0))
                        .formatted(Formatting.GOLD)),
                infoX, infoY + 14, 0xFFFFFF);

        if (!hasTier) {
            // "Registered but no tier" - cool styled message.
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("★ No tiers on record yet ★").formatted(Formatting.GRAY, Formatting.ITALIC),
                    this.width / 2, contentTop + 60, 0xAAAAAA);
            return;
        }

        int listY = infoY + 34;
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Rankings:").formatted(Formatting.WHITE, Formatting.UNDERLINE),
                infoX, listY, 0xFFFFFF);
        listY += 12;

        for (RankedLine line : sortedRankings(data.ranking())) {
            PojavConfig cfg = PojavConfig.get();
            int color = cfg.getTierColor(line.tier);
            Text row = Text.literal(line.label + ": ").formatted(Formatting.GRAY)
                    .append(Text.literal(line.tier).styled(s -> s.withColor(color)));
            context.drawTextWithShadow(this.textRenderer, row, infoX, listY, 0xFFFFFF);
            listY += 11;
        }
    }

    /** Draws the head (base layer + hat overlay) scaled up, clipped so it can never overflow its box. */
    private void drawBust(DrawContext context, int x, int y) {
        context.enableScissor(x, y, x + BUST_SIZE, y + BUST_SIZE);
        // Base head, front face: UV (8,8) 8x8 out of a 64x64 skin.
        CompatUtil.drawTextureRegion(context, skinTexture, x, y, 8, 8, 8, 8, 64, 64);
        // Hat overlay, front face: UV (40,8) 8x8. Skipped safely if the
        // texture is an older 64x32 skin with no second layer - drawing this
        // region on a 64x32 image just samples transparent padding, no crash.
        CompatUtil.drawTextureRegion(context, skinTexture, x, y, 40, 8, 8, 8, 64, 64);
        context.disableScissor();
    }

    private record RankedLine(String label, String tier, int sortKey) {}

    private static List<RankedLine> sortedRankings(PlayerRanking ranking) {
        List<RankedLine> lines = new ArrayList<>();
        for (Map.Entry<String, String> entry : ranking.ranks.entrySet()) {
            GameMode mode = GameMode.fromKey(entry.getKey());
            String label = mode != null ? mode.displayName() : entry.getKey();
            int sortKey = mode != null ? mode.ordinal() : Integer.MAX_VALUE;
            lines.add(new RankedLine(label, entry.getValue(), sortKey));
        }
        lines.sort((a, b) -> Integer.compare(a.sortKey, b.sortKey));
        return lines;
    }

    private static String nullToUnknown(String s) {
        return (s == null || s.isBlank()) ? "Unknown" : s;
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }
}
