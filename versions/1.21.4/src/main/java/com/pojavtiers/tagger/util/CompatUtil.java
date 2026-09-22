package com.pojavtiers.tagger.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import java.io.ByteArrayInputStream;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Style;
import net.minecraft.util.Identifier;

/**
 * Bucket B variant of CompatUtil - used by 1.21.2 through 1.21.5, confirmed
 * via a real CI compile run:
 * <ul>
 *   <li>{@code GameProfile.getName()} - {@code .name()} doesn't exist yet.</li>
 *   <li>{@code DrawContext.drawTexture} now requires a
 *       {@code Function<Identifier, RenderLayer>} as its first argument
 *       (the compiler's own error listed this exact overload as available).
 *       {@code RenderLayer::getGuiTextured} is the standard supplier used for
 *       plain GUI textures in this API generation.</li>
 *   <li>{@code KeyBinding}'s classic 4-arg constructor (same as bucket A).</li>
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
        context.drawTexture(RenderLayer::getGuiTextured, texture, x, y, 0, 0, width, height, width, height);
    }

    /**
     * General textured-region draw, used by the Player Search skin bust to
     * composite a sub-rectangle (e.g. just the head) out of a larger skin
     * texture, rather than stretching the whole texture.
     */
    public static void drawTextureRegion(DrawContext context, Identifier texture, int x, int y,
                                          int u, int v, int regionWidth, int regionHeight,
                                          int textureWidth, int textureHeight) {
        context.drawTexture(RenderLayer::getGuiTextured, texture, x, y, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
    }

    /**
     * Decodes {@code png} and registers it as a texture under {@code id}, for
     * the Player Search skin bust. Returns false (caller falls back to the
     * default Steve skin) on any failure - a malformed or unexpected image
     * should never crash the screen.
     * <p>
     * CONFIRMED via real CI compile runs across every version bucket:
     * {@code NativeImage} lives at {@code net.minecraft.client.texture.NativeImage}
     * here, not {@code com.mojang.blaze3d.platform} (an earlier guess that
     * failed identically on every version that reached this line), and
     * {@code NativeImageBackedTexture}'s constructor needs a debug-label
     * {@code Supplier<String>} as its first argument (a bare
     * {@code NativeImageBackedTexture(NativeImage)} overload doesn't exist).
     */
    public static boolean registerDynamicTexture(Identifier id, byte[] png) {
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "pojavtiertagger_search_skin", image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
