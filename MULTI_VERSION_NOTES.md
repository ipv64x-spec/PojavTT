# Multi-version build - how this works, and what's verified vs. guessed

## Architecture

```
common/                  <- ~95% of the mod. Version-agnostic. Compiled by every module below.
versions/
  1.21/
    build.gradle
    gradle.properties        <- this version's exact MC/Yarn/Loader/Fabric API numbers
    src/main/java/com/pojavtiers/tagger/util/CompatUtil.java   <- THE ONLY version-specific file
  1.21.1/  (same shape)
  1.21.2/  ...
  ...
  1.21.11/ (same shape)
settings.gradle           <- includes all 12 version modules
.github/workflows/main.yml <- matrix build, one job per version, one jar artifact per version
```

`./gradlew build` at the root builds **all 12 jars** (one per Minecraft version).
`./gradlew :1.21.4:build` builds just that one. Each ends up in
`versions/<version>/build/libs/pojavtiertagger-<version>-<version>.jar`.

Every version module compiles the exact same `common/` source. The only file
that differs between modules is `CompatUtil.java`, because it's the only file
that touches the handful of Minecraft APIs that actually changed shape across
1.21 → 1.21.11:

- how a custom bitmap-icon font gets applied to `Text` (`Style.withFont(...)`)
- how a `KeyBinding` gets constructed
- the `DrawContext.drawTexture(...)` argument list

There are four versions of that file, all confirmed by real CI compiler output
(not guesses at this point):
- **Bucket A** (1.21, 1.21.1): the original pre-render-layer-overhaul APIs -
  plain `drawTexture(Identifier, ...)`, `GameProfile.getName()`.
- **Bucket B** (1.21.2 – 1.21.5): `drawTexture` needs a
  `Function<Identifier, RenderLayer>` first argument; still `getName()`.
- **Bucket C** (1.21.6 – 1.21.8): `drawTexture` already needs a
  `RenderPipeline` first argument (same shape as bucket D); still `getName()`.
- **Bucket D / "modern"** (1.21.9 – 1.21.11): `RenderPipeline`-based
  `drawTexture`, `GameProfile.name()`, and the newer 3-arg
  `KeyBinding(String, int, KeyBinding.Category)` constructor - confirmed
  correct for 1.21.11 via Tiers' source, and 1.21.10/1.21.11 have both built
  successfully in CI.

## What's actually verified vs. what's a best-effort guess

I have **no compiler and no real Minecraft/Yarn artifacts in my environment**
(no internet access), so nothing here has been test-compiled by me directly -
every fix so far has come from reading real GitHub Actions compiler output
you pasted back, not from me compiling anything myself. Two rounds of that
loop already caught and fixed real mistakes (the `GameProfile` method rename,
and `drawTexture` actually needing 3 different call shapes across this range,
not the 2 I originally guessed) - so treat every version below as "best
effort until CI says otherwise."

**Confirmed-real Fabric API versions (passed dependency resolution in CI):**
`1.21.6` → `0.128.0+1.21.6`, `1.21.8` → `0.132.0+1.21.8`,
`1.21.10` → `0.138.0+1.21.10`, `1.21.11` → `0.141.2+1.21.11`.
Everything else's `fabric_version` is still an interpolated guess.

**Confirmed-compiling version modules:** 1.21.10 and 1.21.11 have fully built
in CI. Every other version has had at least one real compiler/dependency
error found and fixed via this same loop, but hasn't yet had a fully clean
build confirmed - there may be more small issues CI hasn't surfaced yet
(e.g. the mixins, or `CyclingButtonWidget.onOffBuilder`, which are shared
in `common/` and haven't broken so far, but weren't individually verified
line-by-line either).

1. **Each `CompatUtil-*.java` bucket** - the API *shapes* for font-styling,
   keybinding, and texture-drawing. Buckets A/B/C are now grounded in actual
   compiler-reported overloads, not guesses. The `KeyBinding` constructor
   in buckets A/B/C is still an unconfirmed carry-over assumption (compilation
   never got far enough to test it, since it errored on other lines first) -
   flag this if a future CI run reaches a `KeyBinding`-related error.

2. **Each `gradle.properties`** - the exact Yarn mapping build number, Fabric
   Loader version, Fabric API version, and Mod Menu version *per Minecraft
   version*. Confirmed-real ones are listed above; the rest are still guesses
   and the most likely thing to break next.

## What happens when a version module fails to build (expect this)

This is by design, not a failure of the plan: `fail-fast: false` in the
workflow means all 12 versions attempt to build independently, and a broken
one doesn't stop the others. When the Actions run finishes, paste me:

- **A Loom error about `yarn_mappings`/`loader_version`/`fabric_version`** -
  these are the easiest: Loom's error message lists the actual valid versions
  it found. Fix = update that one number in that one version's
  `gradle.properties`. One line, no code changes.
- **A Java compile error in `CompatUtil.java`** (e.g. "constructor KeyBinding
  cannot be applied to given types", "cannot find symbol StyleSpriteSource")
  - means that version needs the *other* compat bucket (legacy vs. modern),
    or, if it's right on the boundary, a small tweak to its own copy of
    `CompatUtil.java`. This never requires touching `common/` or any other
    version's files.
- **A compile error anywhere in `common/`** - means one of the ~95% "stable"
  APIs (mixins, `CyclingButtonWidget.onOffBuilder`, etc.) also changed shape
  somewhere in this range and needs its own compat-routing added, the same
  way font/keybinding/texture already are. Rarer, but possible - tell me
  which version(s) and I'll add it.

Each of these is a small, isolated fix - the same "paste the error, I fix
that one thing" loop we've been using this whole conversation, just now
scoped to 12 independent, small surfaces instead of one big one.
