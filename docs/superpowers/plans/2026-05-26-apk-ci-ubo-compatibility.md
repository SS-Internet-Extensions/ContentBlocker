# APK CI And uBO Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a GitHub Actions workflow that builds the Android APK and add a tested uBlock Origin compatibility layer for the listed static filtering features that can be represented in the current Samsung/Yandex content-blocker delivery model.

**Architecture:** Keep the app's existing browser integration: it writes `filters.txt` in app storage and broadcasts Samsung/Yandex content-blocker update intents. Add a pure Java compiler/normalizer that converts uBO-style static filter lines into AdGuard/content-blocker-compatible lines before `FilterServiceImpl` writes `filters.txt`; unsupported browser-extension-only behavior is emitted as filter comments so users can see that the line was not silently applied.

**Tech Stack:** Android Gradle Plugin 7.4.1, Gradle wrapper 7.5.1, Java 8 source compatibility, JUnit 4, GitHub Actions with `actions/checkout@v6`, `actions/setup-java@v5`, and `actions/upload-artifact@v7`.

---

## Scope Check

This request spans two independent subsystems:

- CI packaging: create `.github/workflows/build-apk.yml`.
- uBO feature compatibility: parser/compiler, service integration, tests, and docs.

This plan keeps them together because Task 1 is independently shippable and Tasks 2-5 produce a working compatibility slice for the listed features. The current app is a content-blocker provider for Samsung Internet and Yandex Browser, not a full browser extension runtime. Therefore DOM-level scriptlets, popup closing, redirect resources, and URL parameter stripping can only work when the target browser's content-blocker engine honors the resulting filter syntax. The app cannot observe browser tabs, intercept arbitrary browser requests itself, or run extension APIs.

## Sources Checked

- `actions/checkout` latest major is v6, with v6.0.2 listed as latest in releases.
- `actions/setup-java` latest major is v5, with v5.2.0 listed as latest in releases.
- `actions/upload-artifact` README shows the current usage examples with v7 and notes older v1/v2/v3 deprecations.
- uBlock Origin static filter syntax reference: `https://github.com/gorhill/uBlock/wiki/Static-filter-syntax`
- Samsung Internet content blocker guide: `https://developer.samsung.com/internet/android/adblockers-guide.html`

## File Structure

- Create `.github/workflows/build-apk.yml`: CI workflow for tests, release APK build, and artifact upload.
- Create `adguard_cb/src/main/java/com/adguard/android/contentblocker/filtering/CompileResult.java`: immutable result object for one source rule.
- Create `adguard_cb/src/main/java/com/adguard/android/contentblocker/filtering/UboRuleCompiler.java`: pure Java compiler for supported uBO-style static filters.
- Create `adguard_cb/src/test/java/com/adguard/android/contentblocker/filtering/UboRuleCompilerTest.java`: JUnit coverage for regex, pattern, wildcard, context options, redirect, cosmetic, scriptlet, popup, and `removeparam`.
- Modify `adguard_cb/src/main/java/com/adguard/android/contentblocker/service/FilterServiceImpl.java`: run enabled downloaded rules and enabled user rules through `UboRuleCompiler` before writing `filters.txt`.
- Modify `README.md`: document supported uBO compatibility and browser/runtime limitations.

---

### Task 1: GitHub Action APK Build

**Files:**
- Create: `.github/workflows/build-apk.yml`

- [ ] **Step 1: Create workflow file**

Create `.github/workflows/build-apk.yml` with this exact content:

```yaml
name: Build APK

on:
  push:
    branches:
      - master
      - main
  pull_request:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  build:
    name: Build content-blocker APK
    runs-on: ubuntu-latest
    timeout-minutes: 45

    steps:
      - name: Checkout
        uses: actions/checkout@v6

      - name: Set up JDK
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: "11"
          cache: gradle

      - name: Make Gradle wrapper executable
        run: chmod +x ./gradlew

      - name: Run unit tests
        run: ./gradlew :adguard_cb:testProdProdBackendDebugUnitTest --stacktrace

      - name: Build release APK
        run: ./gradlew :adguard_cb:assembleProdProdBackendRelease --stacktrace

      - name: Upload APK
        uses: actions/upload-artifact@v7
        with:
          name: content-blocker-android
          path: content-blocker-android.apk
          if-no-files-found: error
```

- [ ] **Step 2: Verify workflow YAML path**

Run: `rtk rg -n "Build APK|assembleProdProdBackendRelease|content-blocker-android.apk" .github/workflows/build-apk.yml`

Expected: three matching lines showing the workflow name, Gradle release task, and artifact path.

- [ ] **Step 3: Verify local Gradle task names**

Run: `rtk ./gradlew :adguard_cb:tasks --all`

Expected: output contains `assembleProdProdBackendRelease` and `testProdProdBackendDebugUnitTest`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/build-apk.yml
git commit -m "ci: build apk in github actions"
```

---

### Task 2: uBO Compiler Contract Tests

**Files:**
- Create: `adguard_cb/src/test/java/com/adguard/android/contentblocker/filtering/UboRuleCompilerTest.java`

- [ ] **Step 1: Write failing tests**

Create `adguard_cb/src/test/java/com/adguard/android/contentblocker/filtering/UboRuleCompilerTest.java`:

```java
package com.adguard.android.contentblocker.filtering;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UboRuleCompilerTest {

    private final UboRuleCompiler compiler = new UboRuleCompiler();

    @Test
    public void compilesNetworkPatternsAndContextOptions() {
        assertEquals(
                Arrays.asList("||ads.example.com^$third-party,script"),
                compiler.compileAll(Arrays.asList("||ads.example.com^$third-party,script")));

        assertEquals(
                Arrays.asList("/adserver\\d+\\.js/$script,domain=example.com"),
                compiler.compileAll(Arrays.asList("/adserver\\d+\\.js/$script,domain=example.com")));

        assertEquals(
                Arrays.asList("*://*.tracker.example/*$image,domain=example.com|example.org"),
                compiler.compileAll(Arrays.asList("*://*.tracker.example/*$image,domain=example.com|example.org")));
    }

    @Test
    public void compilesCosmeticFilters() {
        assertEquals(
                Arrays.asList("example.com##.ad-banner"),
                compiler.compileAll(Arrays.asList("example.com##.ad-banner")));

        assertEquals(
                Arrays.asList("example.com#@#.allowed-ad"),
                compiler.compileAll(Arrays.asList("example.com#@#.allowed-ad")));
    }

    @Test
    public void convertsCommonScriptletAliases() {
        assertEquals(
                Arrays.asList("example.com#%#//scriptlet('set-constant', 'adBlockDetected', 'false')"),
                compiler.compileAll(Arrays.asList("example.com##+js(set, adBlockDetected, false)")));

        assertEquals(
                Arrays.asList("example.com#%#//scriptlet('abort-on-property-read', 'canRunAds')"),
                compiler.compileAll(Arrays.asList("example.com##+js(aopr, canRunAds)")));
    }

    @Test
    public void preservesRedirectPopupAndRemoveparamRules() {
        assertEquals(
                Arrays.asList("||cdn.example.com/ads.js$script,redirect=noopjs"),
                compiler.compileAll(Arrays.asList("||cdn.example.com/ads.js$script,redirect=noopjs")));

        assertEquals(
                Arrays.asList("||popup.example^$popup,domain=example.com"),
                compiler.compileAll(Arrays.asList("||popup.example^$popup,domain=example.com")));

        assertEquals(
                Arrays.asList("*$removeparam=utm_source"),
                compiler.compileAll(Arrays.asList("*$removeparam=utm_source")));
    }

    @Test
    public void commentsUnsupportedProceduralCosmeticFilters() {
        List<String> compiled = compiler.compileAll(Arrays.asList("example.com##:has-text(Sponsored)"));

        assertEquals(1, compiled.size());
        assertTrue(compiled.get(0).startsWith("! ubo-unsupported: procedural cosmetic filter: "));
    }

    @Test
    public void commentsUnsupportedScriptletsWithoutDroppingTheOriginalLine() {
        List<String> compiled = compiler.compileAll(Arrays.asList("example.com##+js(trusted-set-cookie, flag, 1)"));

        assertEquals(1, compiled.size());
        assertTrue(compiled.get(0).startsWith("! ubo-unsupported: scriptlet trusted-set-cookie: "));
        assertTrue(compiled.get(0).contains("example.com##+js(trusted-set-cookie, flag, 1)"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `rtk ./gradlew :adguard_cb:testProdProdBackendDebugUnitTest --tests com.adguard.android.contentblocker.filtering.UboRuleCompilerTest`

Expected: FAIL because `UboRuleCompiler` does not exist.

- [ ] **Step 3: Commit failing tests**

```bash
git add adguard_cb/src/test/java/com/adguard/android/contentblocker/filtering/UboRuleCompilerTest.java
git commit -m "test: define ubo compatibility compiler behavior"
```

---

### Task 3: uBO Compiler Implementation

**Files:**
- Create: `adguard_cb/src/main/java/com/adguard/android/contentblocker/filtering/CompileResult.java`
- Create: `adguard_cb/src/main/java/com/adguard/android/contentblocker/filtering/UboRuleCompiler.java`

- [ ] **Step 1: Add compile result value object**

Create `adguard_cb/src/main/java/com/adguard/android/contentblocker/filtering/CompileResult.java`:

```java
package com.adguard.android.contentblocker.filtering;

import java.util.Collections;
import java.util.List;

public final class CompileResult {

    private final String originalRule;
    private final List<String> compiledRules;
    private final boolean supported;
    private final String reason;

    private CompileResult(String originalRule, List<String> compiledRules, boolean supported, String reason) {
        this.originalRule = originalRule;
        this.compiledRules = compiledRules;
        this.supported = supported;
        this.reason = reason;
    }

    public static CompileResult supported(String originalRule, List<String> compiledRules) {
        return new CompileResult(originalRule, compiledRules, true, "");
    }

    public static CompileResult unsupported(String originalRule, String reason) {
        return new CompileResult(
                originalRule,
                Collections.singletonList("! ubo-unsupported: " + reason + ": " + originalRule),
                false,
                reason);
    }

    public String getOriginalRule() {
        return originalRule;
    }

    public List<String> getCompiledRules() {
        return compiledRules;
    }

    public boolean isSupported() {
        return supported;
    }

    public String getReason() {
        return reason;
    }
}
```

- [ ] **Step 2: Add compiler**

Create `adguard_cb/src/main/java/com/adguard/android/contentblocker/filtering/UboRuleCompiler.java`:

```java
package com.adguard.android.contentblocker.filtering;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class UboRuleCompiler {

    private static final String COMMENT = "!";
    private static final String ADBLOCK_META_START = "[Adblock";
    private static final String UBO_SCRIPTLET_MARKER = "##+js(";
    private static final String UBO_EXCEPTION_SCRIPTLET_MARKER = "#@#+js(";

    private static final Set<String> PROCEDURAL_COSMETIC_MARKERS = new HashSet<>(Arrays.asList(
            ":has-text(",
            ":matches-attr(",
            ":matches-css(",
            ":xpath(",
            ":watch-attr("));

    private static final Set<String> PASS_THROUGH_OPTIONS = new HashSet<>(Arrays.asList(
            "1p",
            "3p",
            "all",
            "document",
            "domain",
            "font",
            "frame",
            "image",
            "media",
            "object",
            "other",
            "ping",
            "popup",
            "removeparam",
            "redirect",
            "redirect-rule",
            "script",
            "stylesheet",
            "subdocument",
            "third-party",
            "webrtc",
            "xmlhttprequest"));

    private static final Map<String, String> SCRIPTLET_ALIASES = new HashMap<>();

    static {
        SCRIPTLET_ALIASES.put("aopw", "abort-on-property-write");
        SCRIPTLET_ALIASES.put("abort-on-property-write", "abort-on-property-write");
        SCRIPTLET_ALIASES.put("aopr", "abort-on-property-read");
        SCRIPTLET_ALIASES.put("abort-on-property-read", "abort-on-property-read");
        SCRIPTLET_ALIASES.put("acis", "abort-current-inline-script");
        SCRIPTLET_ALIASES.put("abort-current-inline-script", "abort-current-inline-script");
        SCRIPTLET_ALIASES.put("ra", "remove-attr");
        SCRIPTLET_ALIASES.put("remove-attr", "remove-attr");
        SCRIPTLET_ALIASES.put("rc", "remove-class");
        SCRIPTLET_ALIASES.put("remove-class", "remove-class");
        SCRIPTLET_ALIASES.put("set", "set-constant");
        SCRIPTLET_ALIASES.put("set-constant", "set-constant");
    }

    public List<String> compileAll(Collection<String> sourceRules) {
        List<String> compiled = new ArrayList<>();
        for (String sourceRule : sourceRules) {
            compiled.addAll(compile(sourceRule).getCompiledRules());
        }
        return compiled;
    }

    public CompileResult compile(String sourceRule) {
        String rule = StringUtils.trimToEmpty(sourceRule);

        if (StringUtils.isBlank(rule)) {
            return CompileResult.supported(sourceRule, Collections.<String>emptyList());
        }

        if (StringUtils.startsWith(rule, COMMENT) || StringUtils.startsWith(rule, ADBLOCK_META_START)) {
            return CompileResult.supported(sourceRule, Collections.singletonList(rule));
        }

        if (StringUtils.contains(rule, UBO_EXCEPTION_SCRIPTLET_MARKER)) {
            return CompileResult.unsupported(rule, "scriptlet exception");
        }

        if (StringUtils.contains(rule, UBO_SCRIPTLET_MARKER)) {
            return compileScriptlet(rule);
        }

        if (isProceduralCosmetic(rule)) {
            return CompileResult.unsupported(rule, "procedural cosmetic filter");
        }

        if (!hasSupportedOptions(rule)) {
            return CompileResult.unsupported(rule, "unsupported network option");
        }

        return CompileResult.supported(sourceRule, Collections.singletonList(rule));
    }

    private CompileResult compileScriptlet(String rule) {
        int markerStart = rule.indexOf(UBO_SCRIPTLET_MARKER);
        int argsStart = markerStart + UBO_SCRIPTLET_MARKER.length();
        int argsEnd = rule.lastIndexOf(')');

        if (argsEnd <= argsStart) {
            return CompileResult.unsupported(rule, "malformed scriptlet");
        }

        String domainPrefix = rule.substring(0, markerStart);
        List<String> args = splitArguments(rule.substring(argsStart, argsEnd));

        if (args.isEmpty()) {
            return CompileResult.unsupported(rule, "malformed scriptlet");
        }

        String scriptletName = args.remove(0).toLowerCase(Locale.US);
        String mappedName = SCRIPTLET_ALIASES.get(scriptletName);
        if (mappedName == null) {
            return CompileResult.unsupported(rule, "scriptlet " + scriptletName);
        }

        List<String> quoted = new ArrayList<>();
        quoted.add("'" + escapeSingleQuotes(mappedName) + "'");
        for (String arg : args) {
            quoted.add("'" + escapeSingleQuotes(arg) + "'");
        }

        String compiled = domainPrefix + "#%#//scriptlet(" + StringUtils.join(quoted, ", ") + ")";
        return CompileResult.supported(rule, Collections.singletonList(compiled));
    }

    private static List<String> splitArguments(String value) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        char quote = 0;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c == '\'' || c == '"') && (i == 0 || value.charAt(i - 1) != '\\')) {
                if (!quoted) {
                    quoted = true;
                    quote = c;
                } else if (quote == c) {
                    quoted = false;
                } else {
                    current.append(c);
                }
            } else if (c == ',' && !quoted) {
                result.add(StringUtils.trim(current.toString()));
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            result.add(StringUtils.trim(current.toString()));
        }

        return result;
    }

    private static boolean isProceduralCosmetic(String rule) {
        if (!StringUtils.contains(rule, "##")) {
            return false;
        }

        for (String marker : PROCEDURAL_COSMETIC_MARKERS) {
            if (StringUtils.contains(rule, marker)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasSupportedOptions(String rule) {
        int optionsStart = rule.indexOf('$');
        if (optionsStart < 0 || optionsStart == rule.length() - 1) {
            return true;
        }

        String options = rule.substring(optionsStart + 1);
        String[] optionParts = StringUtils.split(options, ',');
        if (optionParts == null) {
            return true;
        }

        for (String optionPart : optionParts) {
            String option = StringUtils.trimToEmpty(optionPart);
            if (StringUtils.startsWith(option, "~")) {
                option = option.substring(1);
            }

            int valueStart = option.indexOf('=');
            String optionName = valueStart >= 0 ? option.substring(0, valueStart) : option;
            optionName = optionName.toLowerCase(Locale.US);

            if (!PASS_THROUGH_OPTIONS.contains(optionName)) {
                return false;
            }
        }

        return true;
    }

    private static String escapeSingleQuotes(String value) {
        return StringUtils.replace(StringUtils.trimToEmpty(value), "'", "\\'");
    }
}
```

- [ ] **Step 3: Run compiler tests**

Run: `rtk ./gradlew :adguard_cb:testProdProdBackendDebugUnitTest --tests com.adguard.android.contentblocker.filtering.UboRuleCompilerTest`

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add adguard_cb/src/main/java/com/adguard/android/contentblocker/filtering/CompileResult.java adguard_cb/src/main/java/com/adguard/android/contentblocker/filtering/UboRuleCompiler.java
git commit -m "feat: compile ubo-style filter rules"
```

---

### Task 4: Wire Compiler Into Filter Application

**Files:**
- Modify: `adguard_cb/src/main/java/com/adguard/android/contentblocker/service/FilterServiceImpl.java`
- Test: `adguard_cb/src/test/java/com/adguard/android/contentblocker/filtering/UboRuleCompilerTest.java`

- [ ] **Step 1: Add integration test for batch ordering**

Append this test method to `UboRuleCompilerTest`:

```java
    @Test
    public void keepsOrderWhenCompilingMixedRuleLists() {
        List<String> compiled = compiler.compileAll(Arrays.asList(
                "! user rules",
                "||ads.example.com^",
                "example.com##+js(set, adBlockDetected, false)",
                "example.com##:has-text(Sponsored)",
                "*$removeparam=utm_campaign"));

        assertEquals("! user rules", compiled.get(0));
        assertEquals("||ads.example.com^", compiled.get(1));
        assertEquals("example.com#%#//scriptlet('set-constant', 'adBlockDetected', 'false')", compiled.get(2));
        assertTrue(compiled.get(3).startsWith("! ubo-unsupported: procedural cosmetic filter: "));
        assertEquals("*$removeparam=utm_campaign", compiled.get(4));
    }
```

- [ ] **Step 2: Run test to verify current compiler still passes before service integration**

Run: `rtk ./gradlew :adguard_cb:testProdProdBackendDebugUnitTest --tests com.adguard.android.contentblocker.filtering.UboRuleCompilerTest`

Expected: PASS.

- [ ] **Step 3: Modify imports and field**

In `adguard_cb/src/main/java/com/adguard/android/contentblocker/service/FilterServiceImpl.java`, add:

```java
import com.adguard.android.contentblocker.filtering.UboRuleCompiler;
```

Then add a field near the existing service fields:

```java
    private final UboRuleCompiler uboRuleCompiler = new UboRuleCompiler();
```

- [ ] **Step 4: Replace initial rule list compilation in `applyNewSettings`**

Change the first line in `applyNewSettings` from:

```java
        List<String> rules = getAllEnabledRules();
```

to:

```java
        List<String> rules = uboRuleCompiler.compileAll(getAllEnabledRules());
```

- [ ] **Step 5: Compile user rules before appending**

Change this block in `applyNewSettings`:

```java
            if (validateRuleText(userRule) && !disabledUserRules.contains(userRule)) {
                rules.add(userRule);
            }
```

to:

```java
            if (validateRuleText(userRule) && !disabledUserRules.contains(userRule)) {
                rules.addAll(uboRuleCompiler.compile(userRule).getCompiledRules());
            }
```

- [ ] **Step 6: Compile whitelist generated rules through the same path**

Change:

```java
                rules.add(createWhiteListRule(whitelistRule));
```

to:

```java
                rules.addAll(uboRuleCompiler.compile(createWhiteListRule(whitelistRule)).getCompiledRules());
```

Change:

```java
                rules.add(String.format("@@||%s^$elemhide", whitelistRule));
```

to:

```java
                rules.addAll(uboRuleCompiler.compile(String.format("@@||%s^$elemhide", whitelistRule)).getCompiledRules());
```

- [ ] **Step 7: Allow generated whitelist `elemhide` option**

In `UboRuleCompiler.java`, add `"elemhide"` to `PASS_THROUGH_OPTIONS`:

```java
            "document",
            "domain",
            "elemhide",
            "font",
```

- [ ] **Step 8: Run tests**

Run: `rtk ./gradlew :adguard_cb:testProdProdBackendDebugUnitTest`

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add adguard_cb/src/main/java/com/adguard/android/contentblocker/service/FilterServiceImpl.java adguard_cb/src/main/java/com/adguard/android/contentblocker/filtering/UboRuleCompiler.java adguard_cb/src/test/java/com/adguard/android/contentblocker/filtering/UboRuleCompilerTest.java
git commit -m "feat: apply ubo compiler before writing filters"
```

---

### Task 5: Document Supported Compatibility

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add compatibility section**

Add this section after the existing introductory paragraph that says the app supports Samsung Internet and Yandex Browser:

```markdown
## uBlock Origin filter compatibility

This app accepts common uBlock Origin-style static filter syntax in user rules and imported lists, then normalizes supported rules before passing them to the Samsung/Yandex content-blocker engine.

Supported compatibility categories:

- Network URL patterns: plain patterns, wildcard patterns, hostname anchors such as `||example.com^`, and regex-delimited rules such as `/adserver\d+\.js/`.
- Context options: common request type and context options such as `$script`, `$image`, `$stylesheet`, `$third-party`, `$domain=...`, `$popup`, `$redirect=...`, and `$removeparam=...`.
- Cosmetic filters: standard CSS selector hiding and exceptions such as `example.com##.ad` and `example.com#@#.ad`.
- Scriptlet aliases: common uBO `##+js(...)` aliases are converted to AdGuard scriptlet syntax for `set`, `set-constant`, `aopr`, `abort-on-property-read`, `aopw`, `abort-on-property-write`, `acis`, `abort-current-inline-script`, `ra`, `remove-attr`, `rc`, and `remove-class`.

Runtime limitations:

- The app is a content-blocker provider. It does not run a browser extension runtime and cannot directly inspect browser tabs, close popup windows, rewrite browser requests, or inject JavaScript by itself.
- Redirect, popup, scriptlet, and tracking-parameter removal rules work only when the target browser's content-blocker engine supports the normalized rule syntax.
- Unsupported procedural cosmetic filters and unknown scriptlets are written as `! ubo-unsupported: ...` comments in `filters.txt` instead of being silently dropped.
```

- [ ] **Step 2: Verify text**

Run: `rtk rg -n "uBlock Origin filter compatibility|ubo-unsupported|removeparam|scriptlet aliases" README.md`

Expected: four matching lines.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: describe ubo filter compatibility"
```

---

### Task 6: Full Verification

**Files:**
- Verify: `.github/workflows/build-apk.yml`
- Verify: `adguard_cb/src/main/java/com/adguard/android/contentblocker/filtering/CompileResult.java`
- Verify: `adguard_cb/src/main/java/com/adguard/android/contentblocker/filtering/UboRuleCompiler.java`
- Verify: `adguard_cb/src/main/java/com/adguard/android/contentblocker/service/FilterServiceImpl.java`
- Verify: `adguard_cb/src/test/java/com/adguard/android/contentblocker/filtering/UboRuleCompilerTest.java`
- Verify: `README.md`

- [ ] **Step 1: Run unit tests**

Run: `rtk ./gradlew :adguard_cb:testProdProdBackendDebugUnitTest`

Expected: PASS.

- [ ] **Step 2: Build release APK locally**

Run: `rtk ./gradlew :adguard_cb:assembleProdProdBackendRelease --stacktrace`

Expected: PASS and `content-blocker-android.apk` exists at repo root because `adguard_cb/build.gradle` rewrites variant outputs to `../../content-blocker-android.apk`.

- [ ] **Step 3: Confirm APK artifact path**

Run: `rtk powershell -NoProfile -Command "Test-Path -LiteralPath 'content-blocker-android.apk'"`

Expected: `True`.

- [ ] **Step 4: Inspect changed files**

Run: `rtk git status --short`

Expected: only the intended files are modified or added.

- [ ] **Step 5: Commit verification fixups if any**

If verification requires small fixes, commit only those files:

```bash
git add .github/workflows/build-apk.yml README.md adguard_cb/src/main/java/com/adguard/android/contentblocker/filtering/CompileResult.java adguard_cb/src/main/java/com/adguard/android/contentblocker/filtering/UboRuleCompiler.java adguard_cb/src/main/java/com/adguard/android/contentblocker/service/FilterServiceImpl.java adguard_cb/src/test/java/com/adguard/android/contentblocker/filtering/UboRuleCompilerTest.java
git commit -m "fix: stabilize ubo compatibility build"
```

---

## Self-Review

**Spec coverage:**

- GitHub Action to build APK: Task 1.
- Regex-based pattern: Task 2 and Task 3 preserve `/.../` network rules.
- Pattern-based filtering: Task 2 and Task 3 preserve hostname and URL pattern rules.
- Wildcard-based pattern: Task 2 and Task 3 preserve `*://*.tracker.example/*`.
- Context information: Task 2 and Task 3 validate common `$...` options.
- Redirect to neutered resource: Task 2 and Task 3 preserve `$redirect=noopjs`.
- Cosmetic filtering: Task 2 and Task 3 preserve standard `##` and `#@#` CSS filters.
- Scriptlet injection filtering: Task 2 and Task 3 convert common `##+js(...)` aliases to `#%#//scriptlet(...)`.
- Unwanted popups: Task 2 and Task 3 preserve `$popup`; README explains browser-runtime limits.
- Remove tracking parameters: Task 2 and Task 3 preserve `$removeparam=...`; README explains browser-runtime limits.
- "Many other features": the compiler has a central `PASS_THROUGH_OPTIONS` and `SCRIPTLET_ALIASES` map so more uBO-compatible syntax can be added with focused tests.

**Placeholder scan:** No step contains unresolved placeholder work. Unsupported features are explicitly represented as comment output with a reason.

**Type consistency:** `UboRuleCompiler.compile(String)` returns `CompileResult`; `UboRuleCompiler.compileAll(Collection<String>)` returns `List<String>`; service integration uses `getCompiledRules()` consistently.

---

Plan complete and saved to `docs/superpowers/plans/2026-05-26-apk-ci-ubo-compatibility.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
