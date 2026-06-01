package com.adguard.android.contentblocker.ui;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.adguard.android.contentblocker.R;
import com.adguard.android.contentblocker.ServiceLocator;
import com.adguard.android.contentblocker.filtering.advanced.AdvancedRuleEngine;
import com.adguard.android.contentblocker.filtering.advanced.AdvancedRuleSet;
import com.adguard.android.contentblocker.filtering.advanced.AdvancedFilterEvent;
import com.adguard.android.contentblocker.filtering.advanced.AdvancedFilterLogger;
import com.adguard.android.contentblocker.filtering.advanced.AdvancedRuntimeRepository;
import com.adguard.android.contentblocker.filtering.advanced.FilterDecision;
import com.adguard.android.contentblocker.filtering.advanced.ProceduralCosmeticScriptBuilder;
import com.adguard.android.contentblocker.filtering.advanced.RequestContext;
import com.adguard.android.contentblocker.filtering.advanced.RedirectResource;
import com.adguard.android.contentblocker.filtering.advanced.ScriptletScriptBuilder;
import com.adguard.android.contentblocker.filtering.advanced.StaticCosmeticScriptBuilder;
import com.adguard.android.contentblocker.filtering.advanced.TrackingParameterCleaner;

import java.io.ByteArrayInputStream;
import java.util.Map;

public class AdvancedBrowserActivity extends AppCompatActivity {

    private WebView webView;
    private EditText urlEditText;
    private AdvancedRuleSet ruleSet;
    private AdvancedRuleEngine engine;
    private AdvancedFilterLogger advancedFilterLogger;
    private TrackingParameterCleaner trackingParameterCleaner;
    private final ScriptletScriptBuilder scriptletScriptBuilder = new ScriptletScriptBuilder();
    private final StaticCosmeticScriptBuilder staticCosmeticScriptBuilder = new StaticCosmeticScriptBuilder();
    private final ProceduralCosmeticScriptBuilder proceduralCosmeticScriptBuilder = new ProceduralCosmeticScriptBuilder();

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_advanced_browser);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        ServiceLocator serviceLocator = ServiceLocator.getInstance(getApplicationContext());
        ruleSet = new AdvancedRuntimeRepository(serviceLocator.getFilterService(), serviceLocator.getPreferencesService()).load();
        advancedFilterLogger = new AdvancedFilterLogger(256);
        engine = new AdvancedRuleEngine(ruleSet, advancedFilterLogger);
        trackingParameterCleaner = new TrackingParameterCleaner(ruleSet, advancedFilterLogger);

        urlEditText = findViewById(R.id.advanced_browser_url);
        webView = findViewById(R.id.advanced_browser_webview);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);

        webView.setWebChromeClient(new BlockingWebChromeClient());
        webView.setWebViewClient(new FilteringWebViewClient());

        findViewById(R.id.advanced_browser_go).setOnClickListener(v -> loadFromInput());

        String initialUrl = getIntent() != null && getIntent().getData() != null
                ? getIntent().getData().toString()
                : "https://example.com";
        urlEditText.setText(initialUrl);
        loadUrl(initialUrl);
    }

    private void loadFromInput() {
        loadUrl(urlEditText.getText().toString());
    }

    private void loadUrl(String rawUrl) {
        String url = normalizeUrl(rawUrl);
        String cleanUrl = trackingParameterCleaner.clean(url, url);
        urlEditText.setText(cleanUrl);
        webView.loadUrl(cleanUrl);
    }

    private static String normalizeUrl(String rawUrl) {
        String trimmed = rawUrl == null ? "" : rawUrl.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }

    private static WebResourceResponse emptyResponse(String mimeType) {
        return new WebResourceResponse(mimeType, "UTF-8", new ByteArrayInputStream(new byte[0]));
    }

    private static WebResourceResponse redirectResponse(RedirectResource resource) {
        return new WebResourceResponse(
                resource.getMimeType(),
                resource.getEncoding(),
                new ByteArrayInputStream(resource.getBody()));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    private final class BlockingWebChromeClient extends WebChromeClient {
        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
            return false;
        }
    }

    private final class FilteringWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String currentUrl = view.getUrl();
            String cleanUrl = trackingParameterCleaner.clean(request.getUrl().toString(), currentUrl);
            if (engine.evaluatePopup(cleanUrl, currentUrl).getAction() == FilterDecision.Action.BLOCK) {
                return true;
            }
            if (!cleanUrl.equals(request.getUrl().toString())) {
                urlEditText.setText(cleanUrl);
                view.loadUrl(cleanUrl);
                return true;
            }
            return false;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            FilterDecision decision = engine.evaluate(RequestContext.infer(
                    uri.toString(),
                    view.getUrl(),
                    request.isForMainFrame(),
                    acceptHeader(request)));
            if (decision.getAction() == FilterDecision.Action.REDIRECT) {
                return redirectResponse(decision.getRedirectResource());
            }
            if (decision.getAction() == FilterDecision.Action.BLOCK) {
                return emptyResponse("text/plain");
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            urlEditText.setText(url);
            String staticCosmeticScript = staticCosmeticScriptBuilder.build(ruleSet.getCosmeticRules(), url, ruleSet.getNetworkRules());
            String scriptletScript = scriptletScriptBuilder.build(ruleSet.getScriptletRules(), url);
            String proceduralCosmeticScript = proceduralCosmeticScriptBuilder.build(
                    ruleSet.getProceduralCosmeticRules(),
                    url,
                    ruleSet.getNetworkRules());
            if (!ruleSet.getCosmeticRules().isEmpty()) {
                recordInjection(url, AdvancedFilterEvent.Type.COSMETIC, "static");
            }
            if (!ruleSet.getScriptletRules().isEmpty()) {
                recordInjection(url, AdvancedFilterEvent.Type.SCRIPTLET, "scriptlet");
            }
            if (!ruleSet.getProceduralCosmeticRules().isEmpty()) {
                recordInjection(url, AdvancedFilterEvent.Type.COSMETIC, "procedural");
            }
            view.evaluateJavascript(staticCosmeticScript, null);
            view.evaluateJavascript(scriptletScript, null);
            view.evaluateJavascript(proceduralCosmeticScript, null);
        }

        private void recordInjection(String url, AdvancedFilterEvent.Type type, String detail) {
            if (advancedFilterLogger == null) {
                return;
            }
            advancedFilterLogger.record(new AdvancedFilterEvent(type, url, url, "", detail));
        }

        private String acceptHeader(WebResourceRequest request) {
            Map<String, String> headers = request.getRequestHeaders();
            if (headers == null) {
                return "";
            }
            String accept = headers.get("Accept");
            return accept == null ? "" : accept;
        }
    }
}
