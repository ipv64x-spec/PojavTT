package com.pojavtiers.tagger.util;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import java.io.ByteArrayInputStream;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Style;
import net.minecraft.util.Identifier;

/**
 * Bucket C variant of CompatUtil - used by 1.21.6 through 1.21.8, confirmed
 * via a real CI compile run:
 * <ul>
 *   <li>{@code GameProfile.getName()} - {@code .name()} doesn't exist yet
 *       (the rename to {@code .name()} only happens at 1.21.9).</li>
 *   <li>{@code DrawContext.drawTexture} already takes a {@code RenderPipeline}
 *       as its first argument here (the same call shape as the "modern"
 *       1.21.9+ bucket) - the compiler's own error output confirmed this
 *       exact 10-argument overload is what's available.</li>
 *   <li>{@code KeyBinding}'s classic 4-arg constructor - the Category-based
 *       rewrite is unconfirmed before 1.21.9, so kept as the older shape
 *       (unconfirmed directly since compilation didn't get that far, but this
 *       is the long-standing API up to that point).</li>
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
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, width, height, width, height);
    }

    /**
     * General textured-region draw, used by the Player Search skin bust to
     * composite a sub-rectangle (e.g. just the head) out of a larger skin
     * texture, rather than stretching the whole texture.
     */
    public static void drawTextureRegion(DrawContext context, Identifier texture, int x, int y,
                                          int u, int v, int regionWidth, int regionHeight,
                                          int textureWidth, int textureHeight) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
    }

    /**
     * Decodes {@code png} and registers it as a texture under {@code id}, for
     * the Player Search skin bust. Returns false (caller falls back to the
     * default Steve skin) on any failure - a malformed or unexpected image
     * should never crash the screen.
     * <p>
     * UNVERIFIED: {@code NativeImage}'s package has moved between Minecraft
     * versions in the past (between {@code net.minecraft.client.texture} and
     * {@code com.mojang.blaze3d.platform}); this file currently assumes the
     * latter for every version bucket. If CI shows an import error here for
     * a specific version, the fix is an isolated one-line change to just
     * this file's import, same as every other CompatUtil fix so far.
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
