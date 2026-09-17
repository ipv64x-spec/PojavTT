package com.pojavtiers.tagger.util;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import java.io.ByteArrayInputStream;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.util.Identifier;

/**
 * MODERN variant of CompatUtil - used by the 1.21.9, 1.21.10, and 1.21.11
 * version modules, where these APIs use their newer shapes:
 * <ul>
 *   <li>{@code Style.withFont(new StyleSpriteSource.Font(Identifier))} -
 *       confirmed real and working on 1.21.11 by cross-checking against the
 *       independently-built Tiers mod's source, which targets the same
 *       Minecraft version and uses this exact call.</li>
 *   <li>{@code KeyBinding}'s 3-arg constructor
 *       {@code (String, int, KeyBinding.Category)} - also confirmed via Tiers.</li>
 *   <li>{@code DrawContext.drawTexture} now takes a RenderPipeline
 *       ({@code RenderPipelines.GUI_TEXTURED}) as its first argument -
 *       also confirmed via Tiers.</li>
 * </ul>
 * We previously used {@code Class.forName("net.minecraft...")} string-based
 * reflection here to guess at these shapes. That was an actual bug: Fabric
 * Loom remaps class/method references embedded in normal bytecode (plain
 * {@code import} + direct calls), but does NOT remap class names embedded in
 * a plain string literal passed to {@code Class.forName}, so in a real
 * (production) launch those lookups quietly failed. Direct calls, as used
 * here, avoid that failure mode entirely.
 */
public final class CompatUtil {

    private CompatUtil() {}

    public static String profileName(GameProfile profile) {
        return profile != null ? profile.name() : null;
    }

    public static Style withFontCompat(Style style, Identifier fontId) {
        return style.withFont(new StyleSpriteSource.Font(fontId));
    }

    public static KeyBinding createKeyBinding(String translationKey, int keyCode, String modId) {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(modId));
        return new KeyBinding(translationKey, keyCode, category);
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
