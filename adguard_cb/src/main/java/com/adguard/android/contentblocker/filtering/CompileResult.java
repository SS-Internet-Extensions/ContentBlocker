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
