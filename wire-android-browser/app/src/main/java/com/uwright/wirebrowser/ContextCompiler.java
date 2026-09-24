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
    private final TruthStore store;
    private final String runtimeVersion;

    public ContextCompiler(TruthStore store, String runtimeVersion) {
        this.store = store;
        this.runtimeVersion = runtimeVersion;
    }

    public JSONObject compileGlobal() {
        return compileForTask("");
    }

    public JSONObject compileForTask(String task) {
        JSONObject out = new JSONObject();
        try {
            out.put("schema", "WAR_CONTEXT_V1");
            out.put("compiled_at_ms", System.currentTimeMillis());
            out.put("fresh_model_assumption", true);
            out.put("task", task == null ? "" : task);
            out.put("runtime", runtimeIdentity());
            out.put("operating_contract", operatingContract());
            out.put("invariants", store.invariants());
            out.put("active_mission", store.activeMission());

            Set<String> terms = keywords(task);
            JSONArray caps = store.capabilityStates();
            JSONArray decisions = store.recentDecisions(50);
            JSONArray facts = store.recentFacts(50);
            JSONArray events = store.recentEvents(30);

            out.put("relevant_capabilities", selectRelevant(caps, terms, terms.isEmpty() ? 40 : 20));
            out.put("relevant_decisions", selectRelevant(decisions, terms, terms.isEmpty() ? 12 : 16));
            out.put("relevant_facts", selectRelevant(facts, terms, terms.isEmpty() ? 12 : 16));
            out.put("recent_evidence", selectEvidenceEvents(events, 12));
            out.put("instruction",
                    "Treat this packet as current machine-readable operating context. Runtime evidence outranks remembered descriptions. " +
                    "If a required fact is absent or UNKNOWN, query W.A.R rather than guessing.");
        } catch (JSONException ignored) {}
        return out;
    }

    private JSONObject runtimeIdentity() {
        JSONObject o = store.readMeta();
        try {
            o.put("version_observed_by_compiler", runtimeVersion);
            o.put("truth_health", store.health());
        } catch (JSONException ignored) {}
        return o;
    }

    private JSONArray operatingContract() {
        JSONArray a = new JSONArray();
        a.put("Human intent controls goals; implementation mechanism is an engineering decision.");
        a.put("EXECUTED does not imply VERIFIED.");
        a.put("Use runtime_truth/context_for_task before substantial architecture or self-modification work.");
        a.put("Do not infer capability maturity from old conversations.");
        a.put("Preserve contradictions, failures, and retired mechanisms.");
        a.put("Do not create a second owner for an existing responsibility.");
        a.put("Keep auto-return regression-safe.");
        return a;
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
                    "make","need","want","wire","android","runtime","please","could","would","should" -> true;
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
            for (int i = 0; i < fallback.size() && out.length() < Math.min(5, limit); i++) out.put(fallback.get(i));
        }
        return out;
    }

    private JSONArray selectEvidenceEvents(JSONArray source, int limit) {
        JSONArray out = new JSONArray();
        if (source == null) return out;
        for (int i = 0; i < source.length() && out.length() < limit; i++) {
            JSONObject e = source.optJSONObject(i);
            if (e == null) continue;
            String type = e.optString("type", "");
            String status = e.optString("status", "");
            if (type.contains("verified") || type.contains("failed") || type.contains("result") ||
                    "VERIFIED".equals(status) || "FAILED".equals(status) || "REPLAY_REJECTED".equals(status)) {
                out.put(e);
            }
        }
        return out;
    }
}
