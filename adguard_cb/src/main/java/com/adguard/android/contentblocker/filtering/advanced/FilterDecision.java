package com.adguard.android.contentblocker.filtering.advanced;

public final class FilterDecision {

    public enum Action {
        ALLOW,
        BLOCK,
        REDIRECT_NOOP_JS,
        REDIRECT_NOOP_CSS,
        REDIRECT_EMPTY
    }

    private final Action action;
    private final AdvancedRule rule;

    private FilterDecision(Action action, AdvancedRule rule) {
        this.action = action;
        this.rule = rule;
    }

    public static FilterDecision allow() {
        return new FilterDecision(Action.ALLOW, null);
    }

    public static FilterDecision of(Action action, AdvancedRule rule) {
        return new FilterDecision(action, rule);
    }

    public Action getAction() {
        return action;
    }

    public AdvancedRule getRule() {
        return rule;
    }
}
