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
import com.adguard.android.contentblocker.filtering.advanced.AdvancedRuntimeRepository;
import com.adguard.android.contentblocker.filtering.advanced.FilterDecision;
import com.adguard.android.contentblocker.filtering.advanced.ProceduralCosmeticScriptBuilder;
import com.adguard.android.contentblocker.filtering.advanced.ScriptletScriptBuilder;
import com.adguard.android.contentblocker.filtering.advanced.TrackingParameterCleaner;

import java.io.ByteArrayInputStream;

public class AdvancedBrowserActivity extends AppCompatActivity {

    private WebView webView;
    private EditText urlEditText;
    private AdvancedRuleSet ruleSet;
    private AdvancedRuleEngine engine;
    private TrackingParameterCleaner trackingParameterCleaner;
    private final ScriptletScriptBuilder scriptletScriptBuilder = new ScriptletScriptBuilder();
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
        engine = new AdvancedRuleEngine(ruleSet);
        trackingParameterCleaner = new TrackingParameterCleaner(ruleSet);

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
        String cleanUrl = trackingParameterCleaner.clean(url);
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
            String cleanUrl = trackingParameterCleaner.clean(request.getUrl().toString());
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
            FilterDecision decision = engine.evaluate(uri.toString(), view.getUrl());
            if (decision.getAction() == FilterDecision.Action.REDIRECT_NOOP_JS) {
                return emptyResponse("application/javascript");
            }
            if (decision.getAction() == FilterDecision.Action.REDIRECT_NOOP_CSS) {
                return emptyResponse("text/css");
            }
            if (decision.getAction() == FilterDecision.Action.REDIRECT_EMPTY ||
                    decision.getAction() == FilterDecision.Action.BLOCK) {
                return emptyResponse("text/plain");
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            urlEditText.setText(url);
            view.evaluateJavascript(scriptletScriptBuilder.build(ruleSet.getScriptletRules(), url), null);
            view.evaluateJavascript(proceduralCosmeticScriptBuilder.build(ruleSet.getProceduralCosmeticRules(), url), null);
        }
    }
}
