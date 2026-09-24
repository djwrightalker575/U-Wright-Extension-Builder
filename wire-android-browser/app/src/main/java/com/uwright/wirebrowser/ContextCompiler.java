package com.uwright.wirebrowser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ContextCompiler {
    public static final int DEFAULT_MAX_CHARS = 7600;

    private final TruthStore store;
    private final String runtimeVersion;

    public ContextCompiler(TruthStore store, String runtimeVersion) {
        this.store = store;
        this.runtimeVersion = runtimeVersion;
    }

    public JSONObject compileGlobal() {
        return compileForTask("", DEFAULT_MAX_CHARS);
    }

    public JSONObject compileForTask(String task) {
        return compileForTask(task, DEFAULT_MAX_CHARS);
    }

    public JSONObject compileForTask(String task, int requestedMaxChars) {
        int maxChars = Math.max(3500, Math.min(requestedMaxChars, 12000));
        JSONObject out = build(task == null ? "" : task, false);
        boolean trimmed = false;

        if (out.toString().length() > maxChars) {
            out = build(task == null ? "" : task, true);
            trimmed = true;
        }

        try {
            out.put("transport_profile", "INLINE_COMPACT_V1");
            out.put("max_chars", maxChars);
            out.put("trimmed_for_transport", trimmed);
            out.put("estimated_chars", out.toString().length());
        } catch (JSONException ignored) {}
        return out;
    }

    private JSONObject build(String task, boolean aggressive) {
        JSONObject out = new JSONObject();
        try {
            out.put("schema", "WAR_CONTEXT_V1");
            out.put("profile", aggressive ? "compact_aggressive" : "compact");
            out.put("compiled_at_ms", System.currentTimeMillis());
            out.put("fresh_model_assumption", true);
            out.put("task", task);
            out.put("runtime", runtimeIdentity());
            out.put("operating_contract", operatingContract());
            out.put("invariants", compactInvariants(store.invariants(), aggressive ? 8 : 10));
            out.put("active_mission", compactMission(store.activeMission()));

            Set<String> terms = keywords(task);
            JSONArray caps = store.capabilityStates();
            JSONArray decisions = store.recentDecisions(30);
            JSONArray facts = store.recentFacts(30);
            JSONArray events = store.recentEventHeaders(20);

            out.put("relevant_capabilities",
                    compactCapabilities(selectRelevant(caps, terms, aggressive ? 6 : 12), aggressive ? 6 : 12));
            out.put("relevant_decisions",
                    compactDecisions(selectRelevant(decisions, terms, aggressive ? 4 : 8), aggressive ? 4 : 8));
            out.put("relevant_facts",
                    compactFacts(selectRelevant(facts, terms, aggressive ? 4 : 8), aggressive ? 4 : 8));
            out.put("recent_evidence", selectEvidenceHeaders(events, aggressive ? 4 : 6));
            out.put("instruction",
                    "Treat this packet as current machine-readable operating context. Runtime evidence outranks remembered descriptions. " +
                    "If a required fact is absent or UNKNOWN, query W.A.R rather than guessing.");
        } catch (JSONException ignored) {}
        return out;
    }

    private JSONObject runtimeIdentity() {
        JSONObject meta = store.readMeta();
        JSONObject o = new JSONObject();
        try {
            o.put("name", meta.optString("runtime.name", "W.A.R — Wired.Android.Runtime"));
            o.put("version", meta.optString("runtime.version", runtimeVersion));
            o.put("protocol", meta.optString("runtime.protocol", "WIRE_ANDROID_V1"));
            o.put("truth_schema", meta.optString("truth.schema", "WAR_TRUTH_V1"));
            o.put("context_schema", meta.optString("context.schema", "WAR_CONTEXT_V1"));
            o.put("authority", meta.optString("authority.runtime", "unknown"));
            JSONObject health = store.health();
            JSONObject compactHealth = new JSONObject();
            compactHealth.put("open", health.optBoolean("open"));
            compactHealth.put("schema_version", health.optInt("schema_version"));
            compactHealth.put("invariants", health.optLong("invariants"));
            compactHealth.put("capabilities", health.optLong("capabilities"));
            compactHealth.put("decisions", health.optLong("decisions"));
            compactHealth.put("facts", health.optLong("facts"));
            compactHealth.put("missions", health.optLong("missions"));
            compactHealth.put("events", health.optLong("events"));
            o.put("truth_health", compactHealth);
        } catch (JSONException ignored) {}
        return o;
    }

    private JSONArray operatingContract() {
        JSONArray a = new JSONArray();
        a.put("Human intent controls goals; implementation mechanism is an engineering decision.");
        a.put("EXECUTED does not imply VERIFIED.");
        a.put("Use context_for_task before substantial architecture or self-modification work.");
        a.put("Do not infer capability maturity from old conversations.");
        a.put("Preserve contradictions, failures, and retired mechanisms.");
        a.put("Do not create a second owner for an existing responsibility.");
        a.put("Keep auto-return regression-safe.");
        return a;
    }

    private JSONArray compactInvariants(JSONArray source, int limit) {
        JSONArray out = new JSONArray();
        for (int i = 0; i < source.length() && out.length() < limit; i++) {
            JSONObject x = source.optJSONObject(i);
            if (x == null) continue;
            JSONObject o = new JSONObject();
            try {
                o.put("id", x.optString("id"));
                o.put("rule", x.optString("text"));
                o.put("status", x.optString("status"));
            } catch (JSONException ignored) {}
            out.put(o);
        }
        return out;
    }

    private JSONObject compactMission(JSONObject x) {
        JSONObject o = new JSONObject();
        if (x == null || x.length() == 0) return o;
        try {
            o.put("id", x.optString("id"));
            o.put("goal", x.optString("goal"));
            o.put("status", x.optString("status"));
            if (x.has("state")) o.put("state", x.opt("state"));
        } catch (JSONException ignored) {}
        return o;
    }

    private JSONArray compactCapabilities(JSONArray source, int limit) {
        JSONArray out = new JSONArray();
        for (int i = 0; i < source.length() && out.length() < limit; i++) {
            JSONObject x = source.optJSONObject(i);
            if (x == null) continue;
            JSONObject o = new JSONObject();
            try {
                o.put("id", x.optString("id"));
                o.put("owner", x.optString("owner"));
                o.put("maturity", x.optString("maturity"));
                JSONObject evidence = x.optJSONObject("evidence");
                if (evidence != null && evidence.length() > 0) {
                    JSONObject e = new JSONObject();
                    if (evidence.has("verified_at")) e.put("verified_at", evidence.opt("verified_at"));
                    if (evidence.has("request_id")) e.put("request_id", evidence.opt("request_id"));
                    o.put("evidence", e);
                }
            } catch (JSONException ignored) {}
            out.put(o);
        }
        return out;
    }

    private JSONArray compactDecisions(JSONArray source, int limit) {
        JSONArray out = new JSONArray();
        for (int i = 0; i < source.length() && out.length() < limit; i++) {
            JSONObject x = source.optJSONObject(i);
            if (x == null) continue;
            JSONObject o = new JSONObject();
            try {
                o.put("id", x.optString("id"));
                o.put("topic", x.optString("topic"));
                o.put("decision", x.optString("decision"));
                o.put("status", x.optString("status"));
            } catch (JSONException ignored) {}
            out.put(o);
        }
        return out;
    }

    private JSONArray compactFacts(JSONArray source, int limit) {
        JSONArray out = new JSONArray();
        for (int i = 0; i < source.length() && out.length() < limit; i++) {
            JSONObject x = source.optJSONObject(i);
            if (x == null) continue;
            JSONObject o = new JSONObject();
            try {
                o.put("category", x.optString("category"));
                o.put("key", x.optString("fact_key"));
                o.put("status", x.optString("status"));
                if (x.has("value")) o.put("value", x.opt("value"));
            } catch (JSONException ignored) {}
            out.put(o);
        }
        return out;
    }

    private Set<String> keywords(String task) {
        Set<String> out = new HashSet<>();
        if (task == null) return out;
        String[] parts = task.toLowerCase(Locale.ROOT).split("[^a-z0-9_]+");
        for (String p : parts) {
            if (p.length() >= 4 && !isStopWord(p)) out.add(p);
            if (out.size() >= 12) break;
        }
        return out;
    }

    private boolean isStopWord(String s) {
        return switch (s) {
            case "this","that","with","from","have","will","into","what","when","where","which","there","about",
                    "make","need","want","wire","android","runtime","please","could","would","should","verify" -> true;
            default -> false;
        };
    }

    private JSONArray selectRelevant(JSONArray source, Set<String> terms, int limit) {
        JSONArray out = new JSONArray();
        if (source == null) return out;
        if (terms.isEmpty()) {
            for (int i = 0; i < source.length() && out.length() < limit; i++) out.put(source.opt(i));
            return out;
        }

        List<JSONObject> fallback = new ArrayList<>();
        for (int i = 0; i < source.length(); i++) {
            JSONObject o = source.optJSONObject(i);
            if (o == null) continue;
            fallback.add(o);
            String hay = o.toString().toLowerCase(Locale.ROOT);
            boolean match = false;
            for (String t : terms) {
                if (hay.contains(t)) { match = true; break; }
            }
            if (match) {
                out.put(o);
                if (out.length() >= limit) break;
            }
        }

        if (out.length() == 0) {
            for (int i = 0; i < fallback.size() && out.length() < Math.min(4, limit); i++) out.put(fallback.get(i));
        }
        return out;
    }

    private JSONArray selectEvidenceHeaders(JSONArray source, int limit) {
        JSONArray out = new JSONArray();
        if (source == null) return out;
        for (int i = 0; i < source.length() && out.length() < limit; i++) {
            JSONObject e = source.optJSONObject(i);
            if (e == null) continue;
            String type = e.optString("type", "");
            String status = e.optString("status", "");
            if (type.contains("verified") || type.contains("failed") || type.contains("result") ||
                    "VERIFIED".equals(status) || "FAILED".equals(status) || "REPLAY_REJECTED".equals(status)) {
                JSONObject h = new JSONObject();
                try {
                    h.put("id", e.optLong("id"));
                    h.put("ts", e.optLong("ts"));
                    h.put("type", type);
                    if (e.has("request_id")) h.put("request_id", e.optString("request_id"));
                    if (e.has("action")) h.put("action", e.optString("action"));
                    if (e.has("status")) h.put("status", status);
                } catch (JSONException ignored) {}
                out.put(h);
            }
        }
        return out;
    }
}
