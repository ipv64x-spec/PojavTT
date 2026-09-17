package com.pojavtiers.tagger.search;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pojavtiers.tagger.PojavTierManager;
import com.pojavtiers.tagger.util.Http;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Resolves a typed-in username to a skin image for the Player Search feature.
 * <p>
 * Flow: username -> Mojang's public profile API (UUID lookup) -> Mojang's
 * session server (skin texture URL) -> download the actual skin PNG bytes.
 * If any step fails - the name isn't a real Mojang/Microsoft account, or the
 * lookup is rate-limited/offline - this is treated as a <b>cracked/offline</b>
 * account rather than an error, and the caller falls back to the vanilla
 * default Steve skin. This mirrors what a Pojav player actually wants: most
 * players tagged by this mod are on cracked/offline accounts, so "couldn't
 * find a premium account" is the common case, not a failure state.
 */
public final class PlayerLookupService {

    private static final String UUID_LOOKUP_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private static final ExecutorService EXEC = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "PojavTierTagger-search");
        t.setDaemon(true);
        return t;
    });

    public enum AccountType {
        PREMIUM, CRACKED
    }

    /** @param skinPng null when {@code type} is CRACKED (caller uses the default Steve skin instead). */
    public record SkinResult(AccountType type, byte[] skinPng) {
        static SkinResult cracked() {
            return new SkinResult(AccountType.CRACKED, null);
        }
    }

    /** The outcome of a full search: either a tier lookup + skin, or "not found" at all. */
    public sealed interface SearchResult {
        record Found(String resolvedName, SkinResult skin, com.pojavtiers.tagger.model.PlayerRanking ranking) implements SearchResult {}
        record NotFound() implements SearchResult {}
    }

    private PlayerLookupService() {}

    /**
     * Looks up tier data via the already-cached rankings (populated by the
     * normal background refresh - no separate tier API call needed here)
     * and, in parallel, resolves a skin. "Not found" means the name doesn't
     * appear in the tier list at all; a tier-list hit with no skin match
     * (cracked account) still counts as Found, with the default Steve skin.
     */
    public static CompletableFuture<SearchResult> search(String username) {
        return CompletableFuture.supplyAsync(() -> {
            var ranking = PojavTierManager.lookup(username);
            if (ranking == null) {
                return (SearchResult) new SearchResult.NotFound();
            }
            SkinResult skin = fetchSkinBlocking(username);
            return (SearchResult) new SearchResult.Found(ranking.minecraftUsername, skin, ranking);
        }, EXEC);
    }

    private static SkinResult fetchSkinBlocking(String username) {
        try {
            String profileJson = Http.get(UUID_LOOKUP_URL + urlEncode(username));
            JsonObject profile = JsonParser.parseString(profileJson).getAsJsonObject();
            JsonElement idElem = profile.get("id");
            if (idElem == null) return SkinResult.cracked();
            String uuid = idElem.getAsString();

            String sessionJson = Http.get(SESSION_URL + uuid);
            JsonObject session = JsonParser.parseString(sessionJson).getAsJsonObject();
            JsonArray properties = session.getAsJsonArray("properties");
            if (properties == null) return SkinResult.cracked();

            for (JsonElement propElem : properties) {
                JsonObject prop = propElem.getAsJsonObject();
                if (!"textures".equals(prop.get("name").getAsString())) continue;

                String decoded = new String(
                        Base64.getDecoder().decode(prop.get("value").getAsString()), StandardCharsets.UTF_8);
                JsonObject texturesRoot = JsonParser.parseString(decoded).getAsJsonObject()
                        .getAsJsonObject("textures");
                if (texturesRoot == null) continue;
                JsonObject skinObj = texturesRoot.getAsJsonObject("SKIN");
                if (skinObj == null) continue;

                String skinUrl = skinObj.get("url").getAsString();
                byte[] png = Http.getBytes(skinUrl);
                if (png != null) {
                    return new SkinResult(AccountType.PREMIUM, png);
                }
            }
            // Premium account exists but has no custom skin set - not really
            // reachable in practice (Mojang always returns a default), but
            // fall back safely rather than assume.
            return SkinResult.cracked();
        } catch (Exception e) {
            // Any failure (name not a real account, rate limit, network
            // hiccup) - treat as a cracked/offline player, not an error.
            return SkinResult.cracked();
        }
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
