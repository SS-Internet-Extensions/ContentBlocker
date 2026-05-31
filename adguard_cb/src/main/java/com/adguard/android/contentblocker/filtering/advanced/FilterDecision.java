package com.adguard.android.contentblocker.filtering.advanced;

public final class FilterDecision {

    public enum Action {
        ALLOW,
        BLOCK,
        REDIRECT
    }

    private final Action action;
    private final AdvancedRule rule;
    private final RedirectResource redirectResource;

    private FilterDecision(Action action, AdvancedRule rule, RedirectResource redirectResource) {
        this.action = action;
        this.rule = rule;
        this.redirectResource = redirectResource;
    }

    public static FilterDecision allow() {
        return new FilterDecision(Action.ALLOW, null, null);
    }

    public static FilterDecision of(Action action, AdvancedRule rule) {
        return new FilterDecision(action, rule, null);
    }

    public static FilterDecision redirect(AdvancedRule rule, RedirectResource redirectResource) {
        return new FilterDecision(Action.REDIRECT, rule, redirectResource);
    }

    public Action getAction() {
        return action;
    }

    public AdvancedRule getRule() {
        return rule;
    }

    public RedirectResource getRedirectResource() {
        return redirectResource;
    }
}
