package dev.daniel.scathechat.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("scathe", Context.MODE_PRIVATE)

    fun loadGroup(): Group? {
        val url = sp.getString("db", null) ?: return null
        val sec = sp.getString("sec", null) ?: return null
        return try {
            Group(url, Vault.unwrap(sec), sp.getString("gname", "Brothers") ?: "Brothers")
        } catch (e: Exception) {
            null
        }
    }

    fun saveGroup(g: Group) {
        sp.edit().putString("db", g.dbUrl).putString("sec", Vault.wrap(g.secret)).putString("gname", g.name).apply()
    }

    fun loadMe(): Me? {
        val id = sp.getString("me_id", null) ?: return null
        return Me(id, sp.getString("me_name", "Me") ?: "Me", sp.getInt("me_color", 0))
    }

    fun saveMe(m: Me) {
        sp.edit().putString("me_id", m.id).putString("me_name", m.name).putInt("me_color", m.color).apply()
    }

    var lastKey: String?
        get() = sp.getString("last_key", null)
        set(v) = sp.edit().putString("last_key", v).apply()

    var lastRead: String?
        get() = sp.getString("last_read", null)
        set(v) = sp.edit().putString("last_read", v).apply()

    var background: Boolean
        get() = sp.getBoolean("bg", true)
        set(v) = sp.edit().putBoolean("bg", v).apply()

    var askedBattery: Boolean
        get() = sp.getBoolean("asked_battery", false)
        set(v) = sp.edit().putBoolean("asked_battery", v).apply()

    var askedNotif: Boolean
        get() = sp.getBoolean("asked_notif", false)
        set(v) = sp.edit().putBoolean("asked_notif", v).apply()

    // ---- Call of Duty (this phone only; the Activision session never leaves the phone)
    var codCookie: String?
        get() = sp.getString("cod_cookie", null)?.let { runCatching { String(Vault.unwrap(it)) }.getOrNull() }
        set(v) = sp.edit().putString("cod_cookie", v?.let { Vault.wrap(it.toByteArray()) }).apply()

    var codUserAgent: String?
        get() = sp.getString("cod_ua", null)
        set(v) = sp.edit().putString("cod_ua", v).apply()

    var codTag: String?
        get() = sp.getString("cod_tag", null)
        set(v) = sp.edit().putString("cod_tag", v).apply()

    var codPlatform: String?
        get() = sp.getString("cod_platform", null)
        set(v) = sp.edit().putString("cod_platform", v).apply()

    var codTitle: String?
        get() = sp.getString("cod_title", null)
        set(v) = sp.edit().putString("cod_title", v).apply()

    var codLastSync: Long
        get() = sp.getLong("cod_last", 0L)
        set(v) = sp.edit().putLong("cod_last", v).apply()

    fun modeCode(mode: String): String? = sp.getString("cod_code_$mode", null)
    fun setModeCode(mode: String, code: String) = sp.edit().putString("cod_code_$mode", code).apply()

    fun clear() = sp.edit().clear().apply()
}

class Db(ctx: Context) : SQLiteOpenHelper(ctx, "scathe.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE msg (id TEXT PRIMARY KEY, ts INTEGER NOT NULL, body TEXT NOT NULL)")
        db.execSQL("CREATE INDEX msg_ts ON msg(ts)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun upsertAll(list: List<Msg>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (m in list) {
                val cv = ContentValues()
                cv.put("id", m.id)
                cv.put("ts", m.ts)
                cv.put("body", m.toStore().toString())
                db.insertWithOnConflict("msg", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun all(): List<Msg> {
        val out = ArrayList<Msg>()
        readableDatabase.rawQuery("SELECT body FROM msg ORDER BY ts", null).use { c ->
            while (c.moveToNext()) {
                try {
                    out.add(Msg.fromStore(JSONObject(c.getString(0))))
                } catch (_: Exception) {
                }
            }
        }
        return out
    }

    fun clear() {
        writableDatabase.delete("msg", null, null)
    }
}
