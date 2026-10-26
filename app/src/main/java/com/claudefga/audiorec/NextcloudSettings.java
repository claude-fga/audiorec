package com.claudefga.audiorec;

import android.content.Context;
import android.content.SharedPreferences;

/** Nextcloud account data, kept in the app's private storage. */
final class NextcloudSettings {

    private static final String PREFS = "nextcloud";
    static final String DEFAULT_FOLDER = "Recordings";

    final String server;
    final String user;
    final String password;
    final String folder;

    NextcloudSettings(String server, String user, String password, String folder) {
        this.server = normalizeServer(server);
        this.user = user.trim();
        this.password = password.trim();
        this.folder = folder.trim();
    }

    boolean isConfigured() {
        return !server.isEmpty() && !user.isEmpty() && !password.isEmpty();
    }

    boolean isHttps() {
        return server.startsWith("https://");
    }

    static NextcloudSettings load(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new NextcloudSettings(
                p.getString("server", ""),
                p.getString("user", ""),
                p.getString("password", ""),
                p.getString("folder", DEFAULT_FOLDER));
    }

    void save(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("server", server)
                .putString("user", user)
                .putString("password", password)
                .putString("folder", folder)
                .apply();
    }

    static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static String normalizeServer(String value) {
        String s = value.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        if (!s.isEmpty() && !s.contains("://")) {
            s = "https://" + s;
        }
        return s;
    }
}
