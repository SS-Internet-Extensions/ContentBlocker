package com.adguard.android.contentblocker.filtering.advanced;

import com.adguard.android.contentblocker.commons.StringHelperUtils;
import com.adguard.android.contentblocker.service.FilterService;
import com.adguard.android.contentblocker.service.PreferencesService;

import java.util.ArrayList;
import java.util.List;

public final class AdvancedRuntimeRepository {

    private final FilterService filterService;
    private final PreferencesService preferencesService;
    private final AdvancedRuleCompiler compiler;

    public AdvancedRuntimeRepository(FilterService filterService, PreferencesService preferencesService) {
        this.filterService = filterService;
        this.preferencesService = preferencesService;
        this.compiler = new AdvancedRuleCompiler();
    }

    public AdvancedRuleSet load() {
        List<String> rules = new ArrayList<>();
        rules.addAll(filterService.getAllEnabledRules());
        rules.addAll(StringHelperUtils.splitAndTrim(preferencesService.getUserRules(), "\n"));
        return compiler.compile(rules);
    }
}
