package com.uwright.wirebrowser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String OPERATOR_URL = "https://chatgpt.com/";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<BrowserTab> tabs = new ArrayList<>();
    private final Map<String, JSONObject> capabilities = new HashMap<>();
    private LinearLayout root;
    private FrameLayout webContainer;
    private EditText address;
    private TextView status;
    private BrowserTab activeTab;
    private BrowserTab operatorTab;
    private boolean autoReturn = true;
    private int nextTab = 1;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        WebView.setWebContentsDebuggingEnabled(true);
        loadCapabilities();
        buildUi();
        createTab(OPERATOR_URL, true);
        createTab("https://www.google.com/", false);
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(17, 19, 24));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(4, 4, 4, 4);

        Button back = button("‹");
        Button forward = button("›");
        Button reload = button("↻");
        Button add = button("+");
        Button list = button("Tabs");
        Button operator = button("WIRE");

        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(Color.LTGRAY);
        address.setHint("URL or search");
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        controls.addView(back);
        controls.addView(forward);
        controls.addView(reload);
        controls.addView(address);
        controls.addView(add);
        controls.addView(list);
        controls.addView(operator);

        status = new TextView(this);
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(11);
        status.setPadding(8, 2, 8, 4);
        status.setText("WIRE Android Browser v0.1");

        webContainer = new FrameLayout(this);
        webContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        root.addView(controls);
        root.addView(status);
        root.addView(webContainer);
        setContentView(root);

        back.setOnClickListener(v -> { if (activeTab != null && activeTab.web.canGoBack()) activeTab.web.goBack(); });
        forward.setOnClickListener(v -> { if (activeTab != null && activeTab.web.canGoForward()) activeTab.web.goForward(); });
        reload.setOnClickListener(v -> { if (activeTab != null) activeTab.web.reload(); });
        add.setOnClickListener(v -> createTab("https://www.google.com/", true));
        list.setOnClickListener(v -> showTabs());
        operator.setOnClickListener(v -> showWireMenu());
        address.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO) {
                navigateInput(address.getText().toString());
                return true;
            }
            return false;
        });
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(10, 0, 10, 0);
        return b;
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private BrowserTab createTab(String url, boolean activate) {
        String id = "tab-" + (nextTab++);
        WebView w = new WebView(this);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setUserAgentString(s.getUserAgentString() + " WIREAndroid/0.1");

        BrowserTab tab = new BrowserTab(id, w);
        w.addJavascriptInterface(new WireBridge(tab), "WIRE_NATIVE");
        w.setWebChromeClient(new WebChromeClient());
        w.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String loadedUrl) {
                tab.lastUrl = loadedUrl;
                tab.title = view.getTitle();
                if (isOperatorUrl(loadedUrl)) {
                    operatorTab = tab;
                    injectOperatorObserver(tab);
                }
                if (activeTab == tab) updateChrome();
                logEvent("page_finished", tab.id, new JSONObjectSafe().put("url", loadedUrl).obj());
            }
        });
        tabs.add(tab);
        w.loadUrl(normalizeUrl(url));
        if (activate) activateTab(tab);
        return tab;
    }

    private void activateTab(BrowserTab tab) {
        activeTab = tab;
        webContainer.removeAllViews();
        if (tab.web.getParent() != null) ((ViewGroup) tab.web.getParent()).removeView(tab.web);
        webContainer.addView(tab.web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updateChrome();
    }

    private void updateChrome() {
        if (activeTab == null) return;
        String url = activeTab.web.getUrl();
        address.setText(url == null ? "" : url);
        status.setText(activeTab.id + " · " + (activeTab.web.getTitle() == null ? "" : activeTab.web.getTitle()) + " · " + (autoReturn ? "AUTO-RETURN" : "MANUAL-RETURN"));
    }

    private void navigateInput(String input) {
        if (activeTab == null) return;
        String x = input.trim();
        if (!x.contains("://") && !x.contains(".") ) {
            x = "https://www.google.com/search?q=" + Uri.encode(x);
        }
        activeTab.web.loadUrl(normalizeUrl(x));
    }

    private String normalizeUrl(String x) {
        if (x == null || x.trim().isEmpty()) return "about:blank";
        String v = x.trim();
        if (v.startsWith("http://") || v.startsWith("https://") || v.startsWith("about:")) return v;
        return "https://" + v;
    }

    private boolean isOperatorUrl(String url) {
        try {
            if (url == null) return false;
            String host = Uri.parse(url).getHost();
            return "chatgpt.com".equalsIgnoreCase(host) || "www.chatgpt.com".equalsIgnoreCase(host);
        } catch (Exception e) { return false; }
    }

    private void injectOperatorObserver(BrowserTab tab) {
        String js = "(function(){if(window.__wireAndroidObserver)return;window.__wireAndroidObserver=true;" +
                "const seen=new Set();function scan(){document.querySelectorAll('pre').forEach(p=>{" +
                "const t=(p.innerText||'').trim();if(!t.startsWith('WIRE_ANDROID_REQUEST_V1'))return;" +
                "const k=t.slice(0,1024);if(seen.has(k))return;seen.add(k);try{WIRE_NATIVE.onRequest(t);}catch(e){}});}" +
                "new MutationObserver(scan).observe(document.documentElement,{subtree:true,childList:true,characterData:true});scan();})();";
        tab.web.evaluateJavascript(js, null);
    }

    private void showTabs() {
        String[] labels = new String[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            BrowserTab t = tabs.get(i);
            labels[i] = t.id + "  " + safe(t.web.getTitle()) + "\n" + safe(t.web.getUrl());
        }
        new AlertDialog.Builder(this).setTitle("WIRE Tabs").setItems(labels, (d, which) -> activateTab(tabs.get(which))).show();
    }

    private void showWireMenu() {
        String[] items = {"Open ChatGPT operator", "Prime ChatGPT", "Toggle auto-return", "Capabilities", "Copy operator prompt", "Runtime status"};
        new AlertDialog.Builder(this).setTitle("WIRE").setItems(items, (d, which) -> {
            switch (which) {
                case 0 -> { if (operatorTab == null) operatorTab = createTab(OPERATOR_URL, true); else activateTab(operatorTab); }
                case 1 -> primeOperator();
                case 2 -> { autoReturn = !autoReturn; updateChrome(); toast("Auto-return " + (autoReturn ? "enabled" : "disabled")); }
                case 3 -> showCapabilities();
                case 4 -> copyOperatorPrompt();
                case 5 -> showRuntimeStatus();
            }
        }).show();
    }

    private String operatorPrompt() {
        try {
            try (java.io.InputStream in = getAssets().open("operator_prompt.txt"); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } catch (Exception e) {
            return "Use WIRE_ANDROID_REQUEST_V1 fenced JSON requests.";
        }
    }

    private void primeOperator() {
        if (operatorTab == null) operatorTab = createTab(OPERATOR_URL, true);
        else activateTab(operatorTab);
        injectIntoChatGpt(operatorPrompt(), true, ok -> toast(ok ? "Operator prompt submitted" : "Could not locate ChatGPT composer; prompt copied"));
    }

    private void copyOperatorPrompt() {
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cb.setPrimaryClip(ClipData.newPlainText("WIRE operator prompt", operatorPrompt()));
        toast("Operator prompt copied");
    }

    private void showRuntimeStatus() {
        JSONObject o = new JSONObject();
        try {
            o.put("version", "0.1.0");
            o.put("tabs", tabs.size());
            o.put("active_tab", activeTab == null ? JSONObject.NULL : activeTab.id);
            o.put("operator_tab", operatorTab == null ? JSONObject.NULL : operatorTab.id);
            o.put("auto_return", autoReturn);
            o.put("capability_count", capabilities.size());
            o.put("event_log", eventLog().getAbsolutePath());
        } catch (JSONException ignored) {}
        textDialog("Runtime status", o.toString());
    }

    private void showCapabilities() {
        JSONArray a = new JSONArray();
        for (JSONObject c : capabilities.values()) a.put(c);
        textDialog("Capabilities", a.toString());
    }

    private void textDialog(String title, String text) {
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextIsSelectable(true);
        tv.setPadding(24, 24, 24, 24);
        sv.addView(tv);
        new AlertDialog.Builder(this).setTitle(title).setView(sv).setPositiveButton("Close", null).show();
    }

    private BrowserTab tabById(String id) {
        if (id == null || id.isEmpty()) return activeTab;
        for (BrowserTab t : tabs) if (t.id.equals(id)) return t;
        return null;
    }

    private final class WireBridge {
        private final BrowserTab source;
        WireBridge(BrowserTab source) { this.source = source; }

        @JavascriptInterface
        public void onRequest(String raw) {
            main.post(() -> {
                if (!isOperatorUrl(source.web.getUrl())) {
                    logEvent("request_rejected", source.id, new JSONObjectSafe().put("reason", "non_operator_origin").obj());
                    return;
                }
                handleWireRequest(raw);
            });
        }
    }

    private void handleWireRequest(String raw) {
        try {
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (!raw.startsWith("WIRE_ANDROID_REQUEST_V1") || start < 0 || end <= start) throw new JSONException("invalid envelope");
            JSONObject req = new JSONObject(raw.substring(start, end + 1));
            String requestId = req.optString("request_id", "wire-" + UUID.randomUUID());
            String action = req.optString("action", "");
            JSONObject args = req.optJSONObject("args");
            if (args == null) args = new JSONObject();
            logEvent("request", operatorTab == null ? "unknown" : operatorTab.id, req);
            dispatch(requestId, action, args);
        } catch (Exception e) {
            sendResult("unknown", "parse", "FAILED", json("error", e.toString()));
        }
    }

    private void dispatch(String requestId, String action, JSONObject args) {
        switch (action) {
            case "capabilities" -> sendResult(requestId, action, "VERIFIED", nativeCapabilities());
            case "tabs" -> sendResult(requestId, action, "VERIFIED", tabsJson());
            case "active_tab" -> sendResult(requestId, action, "VERIFIED", tabJson(activeTab));
            case "open_tab" -> {
                BrowserTab t = createTab(args.optString("url", "about:blank"), args.optBoolean("activate", true));
                sendResult(requestId, action, "VERIFIED", tabJson(t));
            }
            case "activate_tab" -> {
                BrowserTab t = tabById(args.optString("tab_id"));
                if (t == null) fail(requestId, action, "tab_not_found");
                else { activateTab(t); sendResult(requestId, action, "VERIFIED", tabJson(t)); }
            }
            case "navigate" -> navigateAction(requestId, action, args);
            case "back" -> historyAction(requestId, action, args, true);
            case "forward" -> historyAction(requestId, action, args, false);
            case "reload" -> reloadAction(requestId, action, args);
            case "page_snapshot" -> snapshotAction(requestId, action, args);
            case "query" -> queryAction(requestId, action, args);
            case "click" -> clickAction(requestId, action, args);
            case "fill" -> fillAction(requestId, action, args);
            case "scroll" -> scrollAction(requestId, action, args);
            case "list_capabilities" -> sendResult(requestId, action, "VERIFIED", listCapabilityJson());
            case "install_capability" -> installCapability(requestId, action, args);
            case "run_capability" -> runCapability(requestId, action, args);
            default -> fail(requestId, action, "unknown_action");
        }
    }

    private JSONObject nativeCapabilities() {
        JSONObject o = new JSONObject();
        try {
            o.put("protocol", "WIRE_ANDROID_V1");
            o.put("runtime", "WIRE Android Browser");
            o.put("version", "0.1.0");
            o.put("actions", new JSONArray().put("capabilities").put("tabs").put("active_tab").put("open_tab").put("activate_tab").put("navigate").put("back").put("forward").put("reload").put("page_snapshot").put("query").put("click").put("fill").put("scroll").put("list_capabilities").put("install_capability").put("run_capability"));
            o.put("self_extension", "browser_scoped_javascript_capability_packs");
            o.put("authority_boundary", "no_native_permissions_or_shell_from_capability_packs");
        } catch (JSONException ignored) {}
        return o;
    }

    private JSONObject tabsJson() {
        JSONObject o = new JSONObject();
        JSONArray a = new JSONArray();
        for (BrowserTab t : tabs) a.put(tabJson(t));
        try { o.put("tabs", a); o.put("active_tab", activeTab == null ? JSONObject.NULL : activeTab.id); } catch (JSONException ignored) {}
        return o;
    }

    private JSONObject tabJson(BrowserTab t) {
        JSONObject o = new JSONObject();
        if (t == null) return o;
        try {
            o.put("tab_id", t.id);
            o.put("title", t.web.getTitle());
            o.put("url", t.web.getUrl());
            o.put("active", t == activeTab);
            o.put("operator", t == operatorTab || isOperatorUrl(t.web.getUrl()));
        } catch (JSONException ignored) {}
        return o;
    }

    private void navigateAction(String requestId, String action, JSONObject args) {
        BrowserTab t = tabById(args.optString("tab_id"));
        if (t == null) { fail(requestId, action, "tab_not_found"); return; }
        String target = normalizeUrl(args.optString("url", "about:blank"));
        t.web.loadUrl(target);
        main.postDelayed(() -> {
            String observed = t.web.getUrl();
            JSONObject d = tabJson(t);
            try { d.put("requested_url", target); d.put("observed_url", observed); } catch (JSONException ignored) {}
            String status = observed != null && (observed.equals(target) || observed.startsWith(target)) ? "VERIFIED" : "OBSERVED";
            sendResult(requestId, action, status, d);
        }, 1200);
    }

    private void historyAction(String requestId, String action, JSONObject args, boolean back) {
        BrowserTab t = tabById(args.optString("tab_id"));
        if (t == null) { fail(requestId, action, "tab_not_found"); return; }
        boolean can = back ? t.web.canGoBack() : t.web.canGoForward();
        if (!can) { fail(requestId, action, back ? "cannot_go_back" : "cannot_go_forward"); return; }
        String before = t.web.getUrl();
        if (back) t.web.goBack(); else t.web.goForward();
        main.postDelayed(() -> {
            JSONObject d = tabJson(t);
            try { d.put("before_url", before); } catch (JSONException ignored) {}
            sendResult(requestId, action, before != null && !before.equals(t.web.getUrl()) ? "VERIFIED" : "OBSERVED", d);
        }, 1000);
    }

    private void reloadAction(String requestId, String action, JSONObject args) {
        BrowserTab t = tabById(args.optString("tab_id"));
        if (t == null) { fail(requestId, action, "tab_not_found"); return; }
        t.web.reload();
        main.postDelayed(() -> sendResult(requestId, action, "OBSERVED", tabJson(t)), 700);
    }

    private void snapshotAction(String requestId, String action, JSONObject args) {
        BrowserTab t = tabById(args.optString("tab_id"));
        if (t == null) { fail(requestId, action, "tab_not_found"); return; }
        String js = "(()=>{const clip=(s,n)=>String(s||'').slice(0,n);const vis=e=>!!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);" +
                "const nodes=[...document.querySelectorAll('a,button,input,textarea,select,[role=button]')].filter(vis).slice(0,180).map((e,i)=>({i,tag:e.tagName.toLowerCase(),id:e.id||null,name:e.getAttribute('name'),role:e.getAttribute('role'),type:e.getAttribute('type'),text:clip(e.innerText||e.value||e.getAttribute('aria-label')||'',180),href:e.href||null,aria:e.getAttribute('aria-label')}));" +
                "return JSON.stringify({url:location.href,title:document.title,text:clip(document.body?document.body.innerText:'',14000),elements:nodes});})()";
        t.web.evaluateJavascript(js, value -> sendJsJsonResult(requestId, action, value, "VERIFIED"));
    }

    private void queryAction(String requestId, String action, JSONObject args) {
        BrowserTab t = tabById(args.optString("tab_id"));
        if (t == null) { fail(requestId, action, "tab_not_found"); return; }
        String selector = JSONObject.quote(args.optString("selector", ""));
        String js = "(()=>{try{const es=[...document.querySelectorAll(" + selector + ")].slice(0,100);return JSON.stringify({count:es.length,elements:es.map((e,i)=>({i,tag:e.tagName.toLowerCase(),id:e.id||null,text:String(e.innerText||e.value||e.getAttribute('aria-label')||'').slice(0,300),href:e.href||null,value:e.value||null}))});}catch(e){return JSON.stringify({error:String(e)});}})()";
        t.web.evaluateJavascript(js, value -> sendJsJsonResult(requestId, action, value, "VERIFIED"));
    }

    private void clickAction(String requestId, String action, JSONObject args) {
        BrowserTab t = tabById(args.optString("tab_id"));
        if (t == null) { fail(requestId, action, "tab_not_found"); return; }
        String selector = JSONObject.quote(args.optString("selector", ""));
        String js = "(()=>{try{const e=document.querySelector(" + selector + ");if(!e)return JSON.stringify({ok:false,reason:'not_found'});const before=location.href;const desc={tag:e.tagName.toLowerCase(),id:e.id||null,text:String(e.innerText||e.value||e.getAttribute('aria-label')||'').slice(0,200)};e.scrollIntoView({block:'center'});e.click();return JSON.stringify({ok:true,before,element:desc});}catch(e){return JSON.stringify({ok:false,error:String(e)});}})()";
        t.web.evaluateJavascript(js, value -> {
            JSONObject initial = parseJsObject(value);
            if (!initial.optBoolean("ok")) { sendResult(requestId, action, "FAILED", initial); return; }
            main.postDelayed(() -> {
                JSONObject d = initial;
                try { d.put("after_url", t.web.getUrl()); d.put("postcondition", "click_dispatched_and_page_reobserved"); } catch (JSONException ignored) {}
                String before = initial.optString("before", "");
                String after = t.web.getUrl();
                sendResult(requestId, action, after != null && !before.equals(after) ? "VERIFIED" : "OBSERVED", d);
            }, 650);
        });
    }

    private void fillAction(String requestId, String action, JSONObject args) {
        BrowserTab t = tabById(args.optString("tab_id"));
        if (t == null) { fail(requestId, action, "tab_not_found"); return; }
        String selector = JSONObject.quote(args.optString("selector", ""));
        String text = JSONObject.quote(args.optString("text", ""));
        String js = "(()=>{try{const e=document.querySelector(" + selector + ");if(!e)return JSON.stringify({ok:false,reason:'not_found'});e.focus();" +
                "const v=" + text + ";const p=Object.getPrototypeOf(e);const d=Object.getOwnPropertyDescriptor(p,'value');if(d&&d.set)d.set.call(e,v);else e.value=v;" +
                "e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));return JSON.stringify({ok:true,value:String(e.value||e.textContent||'')});}catch(e){return JSON.stringify({ok:false,error:String(e)});}})()";
        t.web.evaluateJavascript(js, value -> {
            JSONObject d = parseJsObject(value);
            boolean verified = d.optBoolean("ok") && args.optString("text", "").equals(d.optString("value", ""));
            sendResult(requestId, action, verified ? "VERIFIED" : (d.optBoolean("ok") ? "OBSERVED" : "FAILED"), d);
        });
    }

    private void scrollAction(String requestId, String action, JSONObject args) {
        BrowserTab t = tabById(args.optString("tab_id"));
        if (t == null) { fail(requestId, action, "tab_not_found"); return; }
        int x = args.optInt("x", 0), y = args.optInt("y", 700);
        String js = "(()=>{window.scrollBy(" + x + "," + y + ");return JSON.stringify({x:window.scrollX,y:window.scrollY});})()";
        t.web.evaluateJavascript(js, value -> sendJsJsonResult(requestId, action, value, "VERIFIED"));
    }

    private JSONObject listCapabilityJson() {
        JSONObject o = new JSONObject();
        JSONArray a = new JSONArray();
        for (JSONObject c : capabilities.values()) a.put(c);
        try { o.put("capabilities", a); } catch (JSONException ignored) {}
        return o;
    }

    private void installCapability(String requestId, String action, JSONObject args) {
        String id = args.optString("id", "").trim();
        String host = args.optString("host_pattern", "*").trim();
        String script = args.optString("script", "");
        if (!id.matches("[A-Za-z0-9._-]{1,80}") || script.isEmpty()) { fail(requestId, action, "invalid_capability"); return; }
        if (script.length() > 120000) { fail(requestId, action, "script_too_large"); return; }
        JSONObject c = new JSONObject();
        try {
            c.put("id", id);
            c.put("host_pattern", host.isEmpty() ? "*" : host);
            c.put("description", args.optString("description", ""));
            c.put("script", script);
            c.put("installed_at", System.currentTimeMillis());
            c.put("scope", "browser_javascript_only");
        } catch (JSONException ignored) {}
        capabilities.put(id, c);
        saveCapabilities();
        sendResult(requestId, action, "VERIFIED", c);
    }

    private void runCapability(String requestId, String action, JSONObject args) {
        BrowserTab t = tabById(args.optString("tab_id"));
        JSONObject c = capabilities.get(args.optString("id"));
        if (t == null) { fail(requestId, action, "tab_not_found"); return; }
        if (c == null) { fail(requestId, action, "capability_not_found"); return; }
        String hostPattern = c.optString("host_pattern", "*");
        String host = "";
        try { host = Uri.parse(t.web.getUrl()).getHost(); } catch (Exception ignored) {}
        if (!hostMatches(hostPattern, host)) { fail(requestId, action, "host_pattern_mismatch"); return; }
        String script = c.optString("script", "");
        JSONObject capArgs = args.optJSONObject("capability_args");
        if (capArgs == null) capArgs = new JSONObject();
        String js = "(async()=>{try{const __args=" + capArgs.toString() + ";const __fn=new Function('args'," + JSONObject.quote(script) + ");const r=await __fn(__args);return JSON.stringify({ok:true,result:r===undefined?null:r});}catch(e){return JSON.stringify({ok:false,error:String(e),stack:e&&e.stack?String(e.stack):null});}})()";
        t.web.evaluateJavascript(js, value -> {
            JSONObject d = parseJsObject(value);
            sendResult(requestId, action, d.optBoolean("ok") ? "VERIFIED" : "FAILED", d);
        });
    }

    private boolean hostMatches(String pattern, String host) {
        if ("*".equals(pattern)) return true;
        if (host == null) return false;
        if (pattern.startsWith("*.")) {
            String suffix = pattern.substring(1).toLowerCase(Locale.ROOT);
            return host.toLowerCase(Locale.ROOT).endsWith(suffix) || host.equalsIgnoreCase(pattern.substring(2));
        }
        return host.equalsIgnoreCase(pattern);
    }

    private void sendJsJsonResult(String requestId, String action, String jsValue, String okStatus) {
        JSONObject d = parseJsObject(jsValue);
        if (d.has("error")) sendResult(requestId, action, "FAILED", d);
        else sendResult(requestId, action, okStatus, d);
    }

    private JSONObject parseJsObject(String jsValue) {
        try {
            if (jsValue == null || "null".equals(jsValue)) return new JSONObject();
            String s = jsValue;
            if (s.startsWith("\"") && s.endsWith("\"")) s = new JSONArray("[" + s + "]").getString(0);
            return new JSONObject(s);
        } catch (Exception e) {
            JSONObject o = json("raw", String.valueOf(jsValue));
            try { o.put("parse_error", e.toString()); } catch (JSONException ignored) {}
            return o;
        }
    }

    private void fail(String requestId, String action, String reason) {
        sendResult(requestId, action, "FAILED", json("error", reason));
    }

    private void sendResult(String requestId, String action, String terminalStatus, JSONObject data) {
        JSONObject envelope = new JSONObject();
        try {
            envelope.put("wire_direct_result", "WIRE_ANDROID_RESULT_V1");
            envelope.put("request_id", requestId);
            envelope.put("action", action);
            envelope.put("status", terminalStatus);
            envelope.put("observed_at", isoNow());
            envelope.put("data", data == null ? new JSONObject() : data);
        } catch (JSONException ignored) {}
        logEvent("result", activeTab == null ? "unknown" : activeTab.id, envelope);
        String payload = "WIRE_ANDROID_RESULT_V1\n" + envelope.toString();
        if (autoReturn && operatorTab != null && isOperatorUrl(operatorTab.web.getUrl())) {
            injectIntoChatGpt(payload, true, ok -> {
                if (!ok) showReturnFallback(payload);
            });
        } else showReturnFallback(payload);
    }

    private interface BoolCallback { void done(boolean ok); }

    private void injectIntoChatGpt(String text, boolean submit, BoolCallback cb) {
        if (operatorTab == null) { cb.done(false); return; }
        String q = JSONObject.quote(text);
        String js = "(()=>{try{const text=" + q + ";let e=document.querySelector('textarea');" +
                "if(!e)e=document.querySelector('[contenteditable=true][data-testid*=prompt],div[contenteditable=true]');if(!e)return false;" +
                "e.focus();if(e.tagName==='TEXTAREA'){const d=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value');d.set.call(e,text);e.dispatchEvent(new Event('input',{bubbles:true}));}" +
                "else{e.textContent=text;e.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:text}));}" +
                (submit ? "setTimeout(()=>{const b=document.querySelector('[data-testid=send-button],button[aria-label*=Send],button[aria-label*=send]');if(b&&!b.disabled)b.click();else{const f=e.closest('form');if(f)f.requestSubmit();}},120);" : "") +
                "return true;}catch(x){return false;}})()";
        operatorTab.web.evaluateJavascript(js, value -> cb.done("true".equals(value)));
    }

    private void showReturnFallback(String payload) {
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cb.setPrimaryClip(ClipData.newPlainText("WIRE result", payload));
        new AlertDialog.Builder(this)
                .setTitle("WIRE result ready")
                .setMessage("ChatGPT composer could not be automated or auto-return is disabled. The result has been copied to the clipboard.")
                .setPositiveButton("Open operator", (d, w) -> { if (operatorTab != null) activateTab(operatorTab); })
                .setNegativeButton("Close", null).show();
    }

    private void loadCapabilities() {
        try {
            String raw = getPreferences(MODE_PRIVATE).getString("capabilities", "[]");
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject c = a.getJSONObject(i);
                capabilities.put(c.getString("id"), c);
            }
        } catch (Exception ignored) {}
    }

    private void saveCapabilities() {
        JSONArray a = new JSONArray();
        for (JSONObject c : capabilities.values()) a.put(c);
        getPreferences(MODE_PRIVATE).edit().putString("capabilities", a.toString()).apply();
    }

    private File eventLog() { return new File(getFilesDir(), "wire-events.jsonl"); }

    private void logEvent(String type, String tabId, JSONObject payload) {
        try {
            JSONObject e = new JSONObject();
            e.put("ts", isoNow());
            e.put("type", type);
            e.put("tab_id", tabId);
            e.put("payload", payload == null ? new JSONObject() : payload);
            try (FileOutputStream out = new FileOutputStream(eventLog(), true)) {
                out.write((e.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }

    private String isoNow() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(new Date());
    }

    private JSONObject json(String k, Object v) {
        JSONObject o = new JSONObject();
        try { o.put(k, v); } catch (JSONException ignored) {}
        return o;
    }

    private String safe(String x) { return x == null ? "" : x; }
    private void toast(String x) { Toast.makeText(this, x, Toast.LENGTH_SHORT).show(); }

    @Override
    public void onBackPressed() {
        if (activeTab != null && activeTab.web.canGoBack()) activeTab.web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        for (BrowserTab t : tabs) t.web.destroy();
        super.onDestroy();
    }

    private static final class BrowserTab {
        final String id;
        final WebView web;
        String title = "";
        String lastUrl = "";
        BrowserTab(String id, WebView web) { this.id = id; this.web = web; }
    }

    private static final class JSONObjectSafe {
        final JSONObject o = new JSONObject();
        JSONObjectSafe put(String k, Object v) { try { o.put(k, v); } catch (JSONException ignored) {} return this; }
        JSONObject obj() { return o; }
    }

}
