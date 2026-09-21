package com.uwright.wirebrowser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
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
    private static final String VERSION = "0.1.1";
    private static final String OPERATOR_URL = "https://chatgpt.com/";
    private static final int FILE_CHOOSER_REQUEST = 7001;
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
    private ValueCallback<Uri[]> pendingFileChooser;
    private String lastChatGptDiagnostic = "{}";
    private String lastChatGptTerminal = "UNKNOWN";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        WebView.setWebContentsDebuggingEnabled(true);
        getWindow().setStatusBarColor(Color.rgb(17, 19, 24));
        getWindow().setNavigationBarColor(Color.rgb(17, 19, 24));
        CookieManager.getInstance().setAcceptCookie(true);
        loadCapabilities();
        buildUi();
        createTab(OPERATOR_URL, true);
        createTab("https://www.google.com/", false);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(17, 19, 24));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                left = bars.left; top = bars.top; right = bars.right; bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(left, top, right, bottom);
            return insets;
        });

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(4), dp(3), dp(4), dp(2));

        Button back = compactButton("‹", 44);
        Button forward = compactButton("›", 44);
        Button reload = compactButton("↻", 44);

        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(Color.LTGRAY);
        address.setBackgroundColor(Color.rgb(35, 38, 46));
        address.setHint("URL or search");
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setPadding(dp(8), 0, dp(8), 0);
        address.setLayoutParams(new LinearLayout.LayoutParams(0, dp(44), 1));

        nav.addView(back);
        nav.addView(forward);
        nav.addView(reload);
        nav.addView(address);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(6), 0, dp(4), dp(3));

        status = new TextView(this);
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(11);
        status.setSingleLine(true);
        status.setText("WIRE Android Browser " + VERSION);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setLayoutParams(new LinearLayout.LayoutParams(0, dp(42), 1));

        Button add = compactButton("+", 44);
        Button list = compactButton("Tabs", 64);
        Button operator = compactButton("WIRE", 68);

        actions.addView(status);
        actions.addView(add);
        actions.addView(list);
        actions.addView(operator);

        webContainer = new FrameLayout(this);
        webContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        root.addView(nav);
        root.addView(actions);
        root.addView(webContainer);
        setContentView(root);
        root.requestApplyInsets();

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

    private Button compactButton(String text, int widthDp) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(4), 0, dp(4), 0);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), dp(42)));
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

        BrowserTab tab = new BrowserTab(id, w);
        w.addJavascriptInterface(new WireBridge(tab), "WIRE_NATIVE");
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);
        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingFileChooser != null) pendingFileChooser.onReceiveValue(null);
                pendingFileChooser = callback;
                Intent intent;
                try {
                    intent = params.createIntent();
                } catch (Exception e) {
                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                }
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    logEvent("file_chooser_opened", tab.id, new JSONObject());
                    return true;
                } catch (Exception e) {
                    pendingFileChooser = null;
                    logEvent("file_chooser_failed", tab.id, json("error", e.toString()));
                    return false;
                }
            }
        });
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            ValueCallback<Uri[]> callback = pendingFileChooser;
            pendingFileChooser = null;
            if (callback == null) return;
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    ClipData clip = data.getClipData();
                    result = new Uri[clip.getItemCount()];
                    for (int i = 0; i < clip.getItemCount(); i++) result[i] = clip.getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    result = new Uri[]{data.getData()};
                }
            }
            callback.onReceiveValue(result);
            logEvent("file_chooser_result", activeTab == null ? "unknown" : activeTab.id,
                    new JSONObjectSafe().put("selected", result == null ? 0 : result.length).obj());
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
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
        String agent = activeTab == operatorTab ? (activeTab.agentInjected ? "AGENT" : "NO-AGENT") : "PAGE";
        status.setText(activeTab.id + " · " + agent + " · " + (autoReturn ? "AUTO" : "MANUAL"));
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
        String js = "(()=>{try{" +
                "const MARK='WIRE_ANDROID_REQUEST_V1';" +
                "const hash=s=>{let h=2166136261;for(let i=0;i<s.length;i++){h^=s.charCodeAt(i);h=Math.imul(h,16777619);}return (h>>>0).toString(16);};" +
                "const parse=t=>{const i=t.indexOf(MARK);if(i<0)return null;const a=t.indexOf('{',i+MARK.length),b=t.lastIndexOf('}');if(a<0||b<=a)return null;let o;try{o=JSON.parse(t.slice(a,b+1));}catch(e){return null;}if(!o||!o.action)return null;const raw=MARK+'\\n'+JSON.stringify(o);return {raw:raw,key:o.request_id||('hash-'+hash(raw))};};" +
                "const nodes=()=>{let n=[...document.querySelectorAll('[data-message-author-role=assistant]')];if(!n.length)n=[...document.querySelectorAll('main pre,main code')].filter(x=>!x.closest('[data-message-author-role=user]'));return n;};" +
                "if(window.__WIRE_ANDROID_AGENT_V011){const r=window.__WIRE_ANDROID_AGENT_V011.scan();return JSON.stringify({ok:true,reused:true,bridge:typeof WIRE_NATIVE!=='undefined',nodes:r.nodes,hits:r.hits,seen:window.__WIRE_ANDROID_AGENT_V011.seen.size});}" +
                "const seen=new Set();for(const n of nodes()){const c=parse(n.innerText||n.textContent||'');if(c)seen.add(c.key);}" +
                "const scan=()=>{let hits=0;const ns=nodes();for(const n of ns){if(n.closest('[data-message-author-role=user]'))continue;const c=parse(n.innerText||n.textContent||'');if(!c||seen.has(c.key))continue;seen.add(c.key);hits++;try{WIRE_NATIVE.onRequest(c.raw);}catch(e){}}return {nodes:ns.length,hits:hits};};" +
                "const obs=new MutationObserver(()=>scan());obs.observe(document.documentElement,{subtree:true,childList:true,characterData:true});" +
                "window.__WIRE_ANDROID_AGENT_V011={scan:scan,seen:seen,observer:obs};" +
                "return JSON.stringify({ok:true,reused:false,bridge:typeof WIRE_NATIVE!=='undefined',baseline_seen:seen.size,assistant_nodes:nodes().length});" +
                "}catch(e){return JSON.stringify({ok:false,error:String(e)});}})()";
        tab.web.evaluateJavascript(js, value -> {
            JSONObject d = parseJsObject(value);
            tab.agentInjected = d.optBoolean("ok") && d.optBoolean("bridge");
            tab.lastAgentDiagnostic = d.toString();
            logEvent("operator_agent", tab.id, d);
            if (activeTab == tab) updateChrome();
        });
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
        String[] items = {"Open ChatGPT operator", "Prime ChatGPT", "Reinject ChatGPT adapter", "ChatGPT diagnostics", "Toggle auto-return", "Core + installed capabilities", "Copy operator prompt", "Runtime status"};
        new AlertDialog.Builder(this).setTitle("WIRE").setItems(items, (d, which) -> {
            switch (which) {
                case 0 -> { if (operatorTab == null) operatorTab = createTab(OPERATOR_URL, true); else activateTab(operatorTab); }
                case 1 -> primeOperator();
                case 2 -> { if (operatorTab != null) { injectOperatorObserver(operatorTab); toast("ChatGPT adapter reinjected"); } else toast("No ChatGPT operator tab"); }
                case 3 -> showChatGptDiagnostics();
                case 4 -> { autoReturn = !autoReturn; updateChrome(); toast("Auto-return " + (autoReturn ? "enabled" : "disabled")); }
                case 5 -> showCapabilities();
                case 6 -> copyOperatorPrompt();
                case 7 -> showRuntimeStatus();
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
        injectOperatorObserver(operatorTab);
        injectIntoChatGpt(operatorPrompt(), true, ok -> {
            if (ok && "VERIFIED".equals(lastChatGptTerminal)) toast("Operator prompt submitted · VERIFIED");
            else if (ok) toast("Operator prompt send observed · " + lastChatGptTerminal);
            else {
                copyOperatorPrompt();
                new AlertDialog.Builder(this)
                        .setTitle("Prime ChatGPT failed")
                        .setMessage("WIRE could not verify submission. The operator prompt was copied. Open ChatGPT diagnostics for the exact page state.")
                        .setPositiveButton("Diagnostics", (d, w) -> showChatGptDiagnostics())
                        .setNegativeButton("Close", null).show();
            }
        });
    }

    private void copyOperatorPrompt() {
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cb.setPrimaryClip(ClipData.newPlainText("WIRE operator prompt", operatorPrompt()));
        toast("Operator prompt copied");
    }

    private void showRuntimeStatus() {
        JSONObject o = new JSONObject();
        try {
            o.put("version", VERSION);
            o.put("tabs", tabs.size());
            o.put("active_tab", activeTab == null ? JSONObject.NULL : activeTab.id);
            o.put("operator_tab", operatorTab == null ? JSONObject.NULL : operatorTab.id);
            o.put("auto_return", autoReturn);
            o.put("installed_capability_count", capabilities.size());
            o.put("operator_agent_injected", operatorTab != null && operatorTab.agentInjected);
            o.put("last_agent_diagnostic", operatorTab == null ? JSONObject.NULL : operatorTab.lastAgentDiagnostic);
            o.put("last_chatgpt_terminal", lastChatGptTerminal);
            o.put("last_chatgpt_diagnostic", lastChatGptDiagnostic);
            o.put("event_log", eventLog().getAbsolutePath());
        } catch (JSONException ignored) {}
        textDialog("Runtime status", o.toString());
    }

    private void showCapabilities() {
        JSONObject o = nativeCapabilities();
        JSONArray installed = new JSONArray();
        for (JSONObject c : capabilities.values()) installed.put(c);
        try {
            o.put("installed_capability_packs", installed);
            o.put("installed_capability_count", installed.length());
        } catch (JSONException ignored) {}
        textDialog("WIRE capabilities", o.toString());
    }

    private String chatGptDiagnosticJs() {
        return "(()=>{try{" +
                "const vis=e=>{const r=e.getBoundingClientRect();const s=getComputedStyle(e);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden';};" +
                "const desc=e=>{const r=e.getBoundingClientRect();return {tag:e.tagName.toLowerCase(),id:e.id||null,testid:e.getAttribute('data-testid'),aria:e.getAttribute('aria-label'),contenteditable:e.getAttribute('contenteditable'),visible:vis(e),rect:{x:Math.round(r.x),y:Math.round(r.y),w:Math.round(r.width),h:Math.round(r.height)}};};" +
                "const qs=s=>[...document.querySelectorAll(s)].slice(0,20).map(desc);" +
                "const a=[...document.querySelectorAll('[data-message-author-role=assistant]')],u=[...document.querySelectorAll('[data-message-author-role=user]')];" +
                "return JSON.stringify({ok:true,href:location.href,ready_state:document.readyState,bridge:typeof WIRE_NATIVE!=='undefined',agent:!!window.__WIRE_ANDROID_AGENT_V011,assistant_messages:a.length,user_messages:u.length,pre_count:document.querySelectorAll('pre').length,code_count:document.querySelectorAll('code').length,file_inputs:document.querySelectorAll('input[type=file]').length,composers:qs('#prompt-textarea,[data-testid=prompt-textarea],textarea,[contenteditable=true]'),send_buttons:qs('button[data-testid=send-button],button[aria-label*=Send],button[aria-label*=send],form button[type=submit]')});" +
                "}catch(e){return JSON.stringify({ok:false,error:String(e)});}})()";
    }

    private void showChatGptDiagnostics() {
        if (operatorTab == null) { toast("No ChatGPT operator tab"); return; }
        operatorTab.web.evaluateJavascript(chatGptDiagnosticJs(), value -> {
            JSONObject d = parseJsObject(value);
            try {
                d.put("native_agent_injected", operatorTab.agentInjected);
                d.put("native_last_agent", operatorTab.lastAgentDiagnostic);
                d.put("last_chatgpt_terminal", lastChatGptTerminal);
                d.put("last_chatgpt_diagnostic", lastChatGptDiagnostic);
            } catch (JSONException ignored) {}
            logEvent("chatgpt_diagnostics", operatorTab.id, d);
            textDialogWithCopy("ChatGPT diagnostics", d.toString());
        });
    }

    private void textDialogWithCopy(String title, String text) {
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextIsSelectable(true);
        tv.setPadding(24, 24, 24, 24);
        sv.addView(tv);
        new AlertDialog.Builder(this).setTitle(title).setView(sv)
                .setPositiveButton("Copy", (d, w) -> {
                    ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cb.setPrimaryClip(ClipData.newPlainText(title, text));
                })
                .setNegativeButton("Close", null).show();
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

    private String nextRequestId() {
        android.content.SharedPreferences p = getSharedPreferences("wire_runtime", MODE_PRIVATE);
        long next = p.getLong("request_counter", 0L) + 1L;
        p.edit().putLong("request_counter", next).apply();
        return String.format(Locale.US, "wire-android-%06d", next);
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
            String requestId = req.optString("request_id", "").trim();
            if (requestId.isEmpty()) requestId = nextRequestId();
            String action = req.optString("action", "");
            JSONObject args = req.optJSONObject("args");
            if (args == null) args = new JSONObject();
            logEvent("request", operatorTab == null ? "unknown" : operatorTab.id, req);
            dispatch(requestId, action, args);
        } catch (Exception e) {
            sendResult(nextRequestId(), "parse", "FAILED", json("error", e.toString()));
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
            o.put("version", VERSION);
            o.put("actions", new JSONArray().put("capabilities").put("tabs").put("active_tab").put("open_tab").put("activate_tab").put("navigate").put("back").put("forward").put("reload").put("page_snapshot").put("query").put("click").put("fill").put("scroll").put("list_capabilities").put("install_capability").put("run_capability"));
            o.put("file_chooser", true);
            o.put("chatgpt_adapter", "observer_v0.1.1");
            o.put("request_id_policy", "optional_input_runtime_monotonic_fallback");
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
                logEvent("return_to_chatgpt", operatorTab.id, new JSONObjectSafe().put("terminal", lastChatGptTerminal).put("diagnostic", lastChatGptDiagnostic).obj());
                if (!ok) showReturnFallback(payload);
            });
        } else showReturnFallback(payload);
    }

    private interface BoolCallback { void done(boolean ok); }

    private void injectIntoChatGpt(String text, boolean submit, BoolCallback cb) {
        if (operatorTab == null || !isOperatorUrl(operatorTab.web.getUrl())) {
            lastChatGptTerminal = "FAILED";
            lastChatGptDiagnostic = "{\"stage\":\"operator_tab\",\"error\":\"unavailable\"}";
            cb.done(false);
            return;
        }
        String q = JSONObject.quote(text);
        String insertJs = "(()=>{try{const text=" + q + ";" +
                "const sels=['#prompt-textarea','[data-testid=prompt-textarea]','textarea','div[contenteditable=true]'];let e=null,used=null;for(const s of sels){const xs=[...document.querySelectorAll(s)];e=xs.find(x=>{const r=x.getBoundingClientRect();return r.width>0&&r.height>0&&!x.disabled;});if(e){used=s;break;}}" +
                "if(!e)return JSON.stringify({ok:false,stage:'locate_composer',error:'composer_not_found'});e.focus();" +
                "if(e.tagName==='TEXTAREA'||e.tagName==='INPUT'){const p=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;const d=Object.getOwnPropertyDescriptor(p,'value');if(d&&d.set)d.set.call(e,text);else e.value=text;e.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:text}));}" +
                "else{const s=window.getSelection(),r=document.createRange();r.selectNodeContents(e);s.removeAllRanges();s.addRange(r);let inserted=false;try{inserted=document.execCommand('insertText',false,text);}catch(x){}if(!inserted){e.textContent=text;e.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:text}));}}" +
                "e.dispatchEvent(new Event('change',{bubbles:true}));const observed=String(e.value!==undefined?e.value:(e.innerText||e.textContent||''));return JSON.stringify({ok:observed.trim()===text.trim(),stage:'insert',selector:used,observed_length:observed.length,expected_length:text.length});" +
                "}catch(e){return JSON.stringify({ok:false,stage:'insert',error:String(e)});}})()";
        operatorTab.web.evaluateJavascript(insertJs, insertValue -> {
            JSONObject inserted = parseJsObject(insertValue);
            lastChatGptDiagnostic = inserted.toString();
            if (!inserted.optBoolean("ok")) {
                lastChatGptTerminal = "FAILED";
                cb.done(false);
                return;
            }
            if (!submit) {
                lastChatGptTerminal = "VERIFIED";
                cb.done(true);
                return;
            }
            main.postDelayed(() -> {
                String submitJs = "(()=>{try{const sels=['button[data-testid=send-button]','button[aria-label=\\\"Send prompt\\\"]','button[aria-label^=Send]','button[aria-label*=send]','form button[type=submit]'];let b=null,used=null;for(const s of sels){const xs=[...document.querySelectorAll(s)];b=xs.find(x=>{const r=x.getBoundingClientRect();return r.width>0&&r.height>0&&!x.disabled;});if(b){used=s;break;}}if(b){b.click();return JSON.stringify({ok:true,stage:'submit',method:'button',selector:used});}const e=document.querySelector('#prompt-textarea,[data-testid=prompt-textarea],textarea,div[contenteditable=true]');const f=e?e.closest('form'):null;if(f&&f.requestSubmit){f.requestSubmit();return JSON.stringify({ok:true,stage:'submit',method:'form_requestSubmit'});}return JSON.stringify({ok:false,stage:'submit',error:'send_control_not_found'});}catch(e){return JSON.stringify({ok:false,stage:'submit',error:String(e)});}})()";
                operatorTab.web.evaluateJavascript(submitJs, submitValue -> {
                    JSONObject submitted = parseJsObject(submitValue);
                    lastChatGptDiagnostic = submitted.toString();
                    if (!submitted.optBoolean("ok")) {
                        lastChatGptTerminal = "FAILED";
                        cb.done(false);
                        return;
                    }
                    main.postDelayed(() -> {
                        String needle = text.substring(0, Math.min(text.length(), 120));
                        String nq = JSONObject.quote(needle);
                        String verifyJs = "(()=>{try{const needle=" + nq + ";const e=document.querySelector('#prompt-textarea,[data-testid=prompt-textarea],textarea,div[contenteditable=true]');const composer=e?String(e.value!==undefined?e.value:(e.innerText||e.textContent||'')):'';const users=[...document.querySelectorAll('[data-message-author-role=user]')];const userObserved=users.some(x=>String(x.innerText||x.textContent||'').includes(needle));const cleared=!composer.includes(needle);return JSON.stringify({ok:userObserved||cleared,user_message_observed:userObserved,composer_cleared:cleared,composer_length:composer.length,user_message_count:users.length});}catch(e){return JSON.stringify({ok:false,error:String(e)});}})()";
                        operatorTab.web.evaluateJavascript(verifyJs, verifyValue -> {
                            JSONObject verified = parseJsObject(verifyValue);
                            lastChatGptDiagnostic = verified.toString();
                            if (verified.optBoolean("user_message_observed")) lastChatGptTerminal = "VERIFIED";
                            else if (verified.optBoolean("composer_cleared")) lastChatGptTerminal = "OBSERVED";
                            else lastChatGptTerminal = "FAILED";
                            cb.done(!"FAILED".equals(lastChatGptTerminal));
                        });
                    }, 1200);
                });
            }, 220);
        });
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
        if (pendingFileChooser != null) pendingFileChooser.onReceiveValue(null);
        for (BrowserTab t : tabs) t.web.destroy();
        super.onDestroy();
    }

    private static final class BrowserTab {
        final String id;
        final WebView web;
        String title = "";
        String lastUrl = "";
        boolean agentInjected = false;
        String lastAgentDiagnostic = "";
        BrowserTab(String id, WebView web) { this.id = id; this.web = web; }
    }

    private static final class JSONObjectSafe {
        final JSONObject o = new JSONObject();
        JSONObjectSafe put(String k, Object v) { try { o.put(k, v); } catch (JSONException ignored) {} return this; }
        JSONObject obj() { return o; }
    }

}
