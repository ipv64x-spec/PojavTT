package com.pojavtiers.tagger.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import java.io.ByteArrayInputStream;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Style;
import net.minecraft.util.Identifier;

/**
 * Bucket A variant of CompatUtil - used by 1.21 and 1.21.1, confirmed via a
 * real CI compile run:
 * <ul>
 *   <li>{@code GameProfile.getName()} - {@code .name()} doesn't exist yet.</li>
 *   <li>{@code DrawContext.drawTexture(Identifier, int, int, int, int, int, int, int, int)}
 *       - the plain pre-render-layer-overhaul signature, no pipeline/render-layer
 *       argument needed (confirmed: this compiled clean on both versions).</li>
 *   <li>{@code KeyBinding}'s classic 4-arg {@code (String, InputUtil.Type, int, String)}
 *       constructor (unconfirmed directly, but this predates the Category-based
 *       rewrite Tiers confirmed for 1.21.11, so kept as the long-standing API).</li>
 * </ul>
 */
public final class CompatUtil {

    private CompatUtil() {}

    public static String profileName(GameProfile profile) {
        return profile != null ? profile.getName() : null;
    }

    public static Style withFontCompat(Style style, Identifier fontId) {
        return style.withFont(fontId);
    }

    public static KeyBinding createKeyBinding(String translationKey, int keyCode, String modId) {
        return new KeyBinding(translationKey, InputUtil.Type.KEYSYM, keyCode, "key.categories." + modId);
    }

    public static void drawTexture(DrawContext context, Identifier texture, int x, int y, int width, int height) {
        context.drawTexture(texture, x, y, 0, 0, width, height, width, height);
    }

    /**
     * General textured-region draw, used by the Player Search skin bust to
     * composite a sub-rectangle (e.g. just the head) out of a larger skin
     * texture, rather than stretching the whole texture.
     */
    public static void drawTextureRegion(DrawContext context, Identifier texture, int x, int y,
                                          int u, int v, int regionWidth, int regionHeight,
                                          int textureWidth, int textureHeight) {
        context.drawTexture(texture, x, y, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
    }

    /**
     * Decodes {@code png} and registers it as a texture under {@code id}, for
     * the Player Search skin bust. Returns false (caller falls back to the
     * default Steve skin) on any failure - a malformed or unexpected image
     * should never crash the screen.
     * <p>
     * CONFIRMED via real CI compile runs: {@code NativeImage} lives at
     * {@code net.minecraft.client.texture.NativeImage} here. On this version,
     * {@code NativeImageBackedTexture} only exposes the
     * {@code (NativeImage)} and {@code (int, int, boolean)} constructors -
     * the {@code (Supplier<String>, NativeImage)} debug-label overload was
     * only added starting with 1.21.5, so it must not be used here.
     */
    public static boolean registerDynamicTexture(Identifier id, byte[] png) {
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
