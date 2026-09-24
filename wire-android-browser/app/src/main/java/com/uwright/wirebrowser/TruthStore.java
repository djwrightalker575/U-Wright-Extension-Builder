package com.uwright.wirebrowser;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.UUID;

public final class TruthStore extends SQLiteOpenHelper {
    public static final String DB_NAME = "war_truth.db";
    public static final int SCHEMA_VERSION = 1;

    public TruthStore(Context context) {
        super(context, DB_NAME, null, SCHEMA_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE invariants (id TEXT PRIMARY KEY, text TEXT NOT NULL, rationale TEXT, status TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE capability_state (id TEXT PRIMARY KEY, owner TEXT NOT NULL, maturity TEXT NOT NULL, evidence_json TEXT, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE decisions (id TEXT PRIMARY KEY, topic TEXT NOT NULL, decision TEXT NOT NULL, rationale TEXT, status TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE facts (category TEXT NOT NULL, fact_key TEXT NOT NULL, value_json TEXT NOT NULL, evidence_json TEXT, status TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(category, fact_key))");
        db.execSQL("CREATE TABLE missions (id TEXT PRIMARY KEY, goal TEXT NOT NULL, status TEXT NOT NULL, state_json TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, type TEXT NOT NULL, request_id TEXT, action TEXT, status TEXT, payload_json TEXT)");
        db.execSQL("CREATE INDEX idx_events_ts ON events(ts DESC)");
        db.execSQL("CREATE INDEX idx_events_type ON events(type)");
        db.execSQL("CREATE INDEX idx_decisions_updated ON decisions(updated_at DESC)");
        db.execSQL("CREATE INDEX idx_facts_updated ON facts(updated_at DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("No automatic destructive migration from truth schema " + oldVersion + " to " + newVersion);
    }

    public synchronized void seedDefaults(String runtimeVersion) {
        putMeta("runtime.name", "W.A.R — Wired.Android.Runtime");
        putMeta("runtime.version", runtimeVersion);
        putMeta("runtime.protocol", "WIRE_ANDROID_V1");
        putMeta("truth.schema", "WAR_TRUTH_V1");
        putMeta("context.schema", "WAR_CONTEXT_V1");
        putMeta("authority.runtime", "single_app_instance_expected");
        putMeta("operator.transport", "chatgpt_web_autoreturn");

        seedInvariant("single_authority",
                "One authoritative runtime and one authoritative persistent truth store.",
                "Prevents divergent live state and competing owners.");
        seedInvariant("single_protocol",
                "WIRE_ANDROID_V1 remains the canonical request/result protocol until an explicit migration replaces it.",
                "Prevents request-format drift.");
        seedInvariant("verified_requires_evidence",
                "VERIFIED requires observed evidence of the intended effect; handler success alone is insufficient.",
                "Prevents false terminal success.");
        seedInvariant("single_capability_owner",
                "Each responsibility has one declared owner; fallbacks must be explicit and subordinate.",
                "Prevents duplicate hidden implementations.");
        seedInvariant("permission_expansion_explicit",
                "No capability may silently expand Android privileges or permissions.",
                "Keeps privilege boundaries reviewable.");
        seedInvariant("replacement_requires_retirement",
                "Replacing a mechanism requires an explicit migration or retirement record for the old mechanism.",
                "Prevents obsolete code paths from remaining active.");
        seedInvariant("source_build_runtime_identity",
                "Authoritative source, reproducible build, and running instance identities must be traceable to one another.",
                "Prevents source/ZIP/runtime divergence.");
        seedInvariant("autoreturn_core",
                "Automatic bidirectional ChatGPT return is core infrastructure and must be regression-tested after relevant changes.",
                "Preserves the operating surface.");
        seedInvariant("intent_not_mechanism",
                "Human capability intent is distinct from implementation mechanism; architecture chooses the mechanism.",
                "Allows non-programmer intent without silently approving unsafe implementation details.");
        seedInvariant("fresh_model_assumption",
                "Any reasoning turn may be handled by a fresh model that only knows context explicitly supplied by W.A.R.",
                "Forces continuity into the system rather than model memory.");

        seedCapability("operator.auto_return", "ChatGptAdapter", "DEVICE_VERIFIED",
                jsonString("verified_at", "2026-09-24T05:18:38.967-06:00",
                        "request_id", "wire-android-000001",
                        "evidence", "Result returned and auto-sent into the same ChatGPT conversation on a physical Android device."));
        seedCapability("browser.tabs", "BrowserRuntime", "DEVICE_VERIFIED",
                jsonString("verified_at", "2026-09-24T05:18:38.967-06:00",
                        "request_id", "wire-android-000001",
                        "evidence", "tabs action executed and returned current tab state."));
        seedCapability("request.replay_protection", "RequestKernel", "IMPLEMENTED", "{}");
        seedCapability("browser.page_snapshot", "BrowserRuntime", "IMPLEMENTED", "{}");
        seedCapability("browser.query", "BrowserRuntime", "IMPLEMENTED", "{}");
        seedCapability("browser.click", "BrowserRuntime", "IMPLEMENTED", "{}");
        seedCapability("browser.fill", "BrowserRuntime", "IMPLEMENTED", "{}");
        seedCapability("browser.scroll", "BrowserRuntime", "IMPLEMENTED", "{}");
        seedCapability("truth.store", "TruthKernel", "IMPLEMENTED", "{}");
        seedCapability("context.compiler", "ContextCompiler", "IMPLEMENTED", "{}");

        seedDecision("adr-0001", "Self-modification authority",
                "The source repository is authoritative; live runtime changes are candidates until reconciled into source.",
                "Prevents live self-modification and packaged source from becoming competing realities.",
                "ACTIVE");
        seedDecision("adr-0002", "Auto-return input mechanism",
                "Direct manual WebView InputConnection ownership is retired. Native WebView paste handling is the current auto-return input mechanism.",
                "V0.1.4 crashed when auto-return entered the manually-created InputConnection path; V0.1.5 restored stability and physically verified auto-return.",
                "ACTIVE");
        seedDecision("adr-0003", "Continuity architecture",
                "Model continuity must be reconstructed from W.A.R truth/context data instead of relying on conversation memory alone.",
                "A fresh LLM should be able to recover operating context from machine-readable state.",
                "ACTIVE");
    }

    private String jsonString(Object... kv) {
        JSONObject o = new JSONObject();
        try {
            for (int i = 0; i + 1 < kv.length; i += 2) o.put(String.valueOf(kv[i]), kv[i + 1]);
        } catch (JSONException ignored) {}
        return o.toString();
    }

    public synchronized void putMeta(String key, String value) {
        ContentValues v = new ContentValues();
        v.put("key", key);
        v.put("value", value == null ? "" : value);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("meta", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private synchronized void seedInvariant(String id, String text, String rationale) {
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("text", text);
        v.put("rationale", rationale);
        v.put("status", "ACTIVE");
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("invariants", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private synchronized void seedCapability(String id, String owner, String maturity, String evidenceJson) {
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("owner", owner);
        v.put("maturity", maturity);
        v.put("evidence_json", evidenceJson == null ? "{}" : evidenceJson);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("capability_state", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private synchronized void seedDecision(String id, String topic, String decision, String rationale, String status) {
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("topic", topic);
        v.put("decision", decision);
        v.put("rationale", rationale);
        v.put("status", status);
        v.put("created_at", now);
        v.put("updated_at", now);
        getWritableDatabase().insertWithOnConflict("decisions", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public synchronized String recordDecision(String id, String topic, String decision, String rationale, String status) {
        String actualId = id == null || id.trim().isEmpty() ? "decision-" + UUID.randomUUID() : id.trim();
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("id", actualId);
        v.put("topic", topic == null ? "" : topic);
        v.put("decision", decision == null ? "" : decision);
        v.put("rationale", rationale == null ? "" : rationale);
        v.put("status", status == null || status.isEmpty() ? "ACTIVE" : status);
        v.put("created_at", now);
        v.put("updated_at", now);
        getWritableDatabase().insertWithOnConflict("decisions", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        return actualId;
    }

    public synchronized void putFact(String category, String key, JSONObject value, JSONObject evidence, String status) {
        ContentValues v = new ContentValues();
        v.put("category", category == null || category.isEmpty() ? "general" : category);
        v.put("fact_key", key == null || key.isEmpty() ? "fact-" + UUID.randomUUID() : key);
        v.put("value_json", value == null ? "{}" : value.toString());
        v.put("evidence_json", evidence == null ? "{}" : evidence.toString());
        v.put("status", status == null || status.isEmpty() ? "CURRENT" : status);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("facts", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void putCapabilityState(String id, String owner, String maturity, JSONObject evidence) {
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("owner", owner == null || owner.isEmpty() ? "unknown" : owner);
        v.put("maturity", maturity == null || maturity.isEmpty() ? "UNKNOWN" : maturity.toUpperCase(Locale.ROOT));
        v.put("evidence_json", evidence == null ? "{}" : evidence.toString());
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("capability_state", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void setMission(String id, String goal, String status, JSONObject state, boolean makeActive) {
        String missionId = id == null || id.isEmpty() ? "mission-" + UUID.randomUUID() : id;
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("id", missionId);
        v.put("goal", goal == null ? "" : goal);
        v.put("status", status == null || status.isEmpty() ? "ACTIVE" : status);
        v.put("state_json", state == null ? "{}" : state.toString());
        v.put("created_at", now);
        v.put("updated_at", now);
        getWritableDatabase().insertWithOnConflict("missions", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        if (makeActive) putMeta("active_mission_id", missionId);
    }

    public synchronized void recordEvent(String type, JSONObject payload) {
        ContentValues v = new ContentValues();
        v.put("ts", System.currentTimeMillis());
        v.put("type", type == null ? "unknown" : type);
        if (payload != null) {
            v.put("request_id", payload.optString("request_id", null));
            v.put("action", payload.optString("action", null));
            v.put("status", payload.optString("status", null));
            v.put("payload_json", payload.toString());
        } else {
            v.put("payload_json", "{}");
        }
        getWritableDatabase().insert("events", null, v);
        getWritableDatabase().execSQL("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY id DESC LIMIT 5000)");
    }

    public synchronized JSONObject health() {
        JSONObject o = new JSONObject();
        SQLiteDatabase db = getReadableDatabase();
        try {
            o.put("db_name", DB_NAME);
            o.put("path", db.getPath());
            o.put("schema_version", SCHEMA_VERSION);
            o.put("open", db.isOpen());
            o.put("invariants", count(db, "invariants"));
            o.put("capabilities", count(db, "capability_state"));
            o.put("decisions", count(db, "decisions"));
            o.put("facts", count(db, "facts"));
            o.put("missions", count(db, "missions"));
            o.put("events", count(db, "events"));
        } catch (JSONException ignored) {}
        return o;
    }

    private long count(SQLiteDatabase db, String table) {
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    public synchronized JSONObject snapshot() {
        JSONObject out = new JSONObject();
        try {
            out.put("schema", "WAR_TRUTH_V1");
            out.put("generated_at_ms", System.currentTimeMillis());
            out.put("meta", readMeta());
            out.put("invariants", readRows("SELECT id,text,rationale,status,updated_at FROM invariants ORDER BY id", null,
                    new String[]{"id","text","rationale","status","updated_at"}, 100));
            out.put("capabilities", readRows("SELECT id,owner,maturity,evidence_json,updated_at FROM capability_state ORDER BY id", null,
                    new String[]{"id","owner","maturity","evidence_json","updated_at"}, 300));
            out.put("active_mission", activeMission());
            out.put("recent_decisions", recentDecisions(30));
            out.put("recent_facts", recentFacts(30));
            out.put("recent_events", recentEvents(25));
            out.put("health", health());
        } catch (JSONException ignored) {}
        return out;
    }

    public synchronized JSONObject readMeta() {
        JSONObject o = new JSONObject();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT key,value FROM meta ORDER BY key", null)) {
            while (c.moveToNext()) {
                try { o.put(c.getString(0), c.getString(1)); } catch (JSONException ignored) {}
            }
        }
        return o;
    }

    public synchronized JSONArray invariants() {
        return readRows("SELECT id,text,rationale,status,updated_at FROM invariants WHERE status='ACTIVE' ORDER BY id", null,
                new String[]{"id","text","rationale","status","updated_at"}, 100);
    }

    public synchronized JSONArray capabilityStates() {
        return readRows("SELECT id,owner,maturity,evidence_json,updated_at FROM capability_state ORDER BY id", null,
                new String[]{"id","owner","maturity","evidence_json","updated_at"}, 300);
    }

    public synchronized JSONArray recentDecisions(int limit) {
        return readRows("SELECT id,topic,decision,rationale,status,created_at,updated_at FROM decisions ORDER BY updated_at DESC LIMIT ?",
                new String[]{String.valueOf(Math.max(1, Math.min(limit, 100)))},
                new String[]{"id","topic","decision","rationale","status","created_at","updated_at"}, limit);
    }

    public synchronized JSONArray recentFacts(int limit) {
        return readRows("SELECT category,fact_key,value_json,evidence_json,status,updated_at FROM facts ORDER BY updated_at DESC LIMIT ?",
                new String[]{String.valueOf(Math.max(1, Math.min(limit, 100)))},
                new String[]{"category","fact_key","value_json","evidence_json","status","updated_at"}, limit);
    }

    public synchronized JSONArray recentEvents(int limit) {
        return readRows("SELECT id,ts,type,request_id,action,status,payload_json FROM events ORDER BY id DESC LIMIT ?",
                new String[]{String.valueOf(Math.max(1, Math.min(limit, 100)))},
                new String[]{"id","ts","type","request_id","action","status","payload_json"}, limit);
    }

    public synchronized JSONObject activeMission() {
        JSONObject meta = readMeta();
        String id = meta.optString("active_mission_id", "");
        if (id.isEmpty()) return JSONObject.NULL instanceof JSONObject ? new JSONObject() : new JSONObject();
        JSONArray rows = readRows("SELECT id,goal,status,state_json,created_at,updated_at FROM missions WHERE id=? LIMIT 1",
                new String[]{id},
                new String[]{"id","goal","status","state_json","created_at","updated_at"}, 1);
        return rows.length() > 0 ? rows.optJSONObject(0) : new JSONObject();
    }

    private JSONArray readRows(String sql, String[] args, String[] columns, int limit) {
        JSONArray a = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            int n = 0;
            while (c.moveToNext() && n++ < Math.max(1, limit)) {
                JSONObject o = new JSONObject();
                for (int i = 0; i < columns.length; i++) {
                    int idx = c.getColumnIndex(columns[i]);
                    if (idx < 0 || c.isNull(idx)) continue;
                    try {
                        switch (c.getType(idx)) {
                            case Cursor.FIELD_TYPE_INTEGER -> o.put(columns[i], c.getLong(idx));
                            case Cursor.FIELD_TYPE_FLOAT -> o.put(columns[i], c.getDouble(idx));
                            default -> {
                                String raw = c.getString(idx);
                                if (columns[i].endsWith("_json")) {
                                    Object parsed = parseJson(raw);
                                    o.put(columns[i].substring(0, columns[i].length() - 5), parsed);
                                } else o.put(columns[i], raw);
                            }
                        }
                    } catch (JSONException ignored) {}
                }
                a.put(o);
            }
        }
        return a;
    }

    private Object parseJson(String raw) {
        if (raw == null || raw.isEmpty()) return new JSONObject();
        try { return new JSONObject(raw); } catch (Exception ignored) {}
        try { return new JSONArray(raw); } catch (Exception ignored) {}
        return raw;
    }
}
