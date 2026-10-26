package com.claudefga.audiorec;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

/** Minimal Nextcloud (WebDAV) client: creates the target folder and uploads one file. */
final class NextcloudUploader {

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build();

    private NextcloudUploader() {
    }

    /** Checks login and makes sure the target folder exists. */
    static void testConnection(NextcloudSettings s) throws IOException {
        Request request = base(s, userRoot(s))
                .header("Depth", "0")
                .method("PROPFIND", null)
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            check(response);
        }
        ensureFolder(s);
    }

    /** Uploads the given recording into the configured folder. */
    static void upload(Context context, Uri file, String name, NextcloudSettings s) throws IOException {
        HttpUrl folder = ensureFolder(s);
        HttpUrl target = folder.newBuilder().addPathSegment(name).build();

        final android.content.ContentResolver resolver = context.getContentResolver();
        final Uri source = file;
        final long length;
        try (AssetFileDescriptor afd = resolver.openAssetFileDescriptor(source, "r")) {
            if (afd == null) throw new IOException("Recording file not found");
            length = afd.getLength();
        }

        RequestBody body = new RequestBody() {
            @Override
            public MediaType contentType() {
                return MediaType.parse("audio/mp4");
            }

            @Override
            public long contentLength() {
                return length; // -1 = unknown, OkHttp then streams in chunks
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                try (InputStream in = resolver.openInputStream(source)) {
                    if (in == null) throw new IOException("Recording file not found");
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        sink.write(buffer, 0, read);
                    }
                }
            }
        };

        Request request = base(s, target).put(body).build();
        try (Response response = CLIENT.newCall(request).execute()) {
            check(response);
        }
    }

    private static Request.Builder base(NextcloudSettings s, HttpUrl url) {
        return new Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic(s.user, s.password, StandardCharsets.UTF_8));
    }

    private static HttpUrl userRoot(NextcloudSettings s) throws IOException {
        HttpUrl server = HttpUrl.parse(s.server);
        if (server == null) throw new IOException("Invalid server address");
        return server.newBuilder()
                .addPathSegments("remote.php/dav/files")
                .addPathSegment(s.user)
                .build();
    }

    /** Creates every folder level (MKCOL) and returns the URL of the target folder. */
    private static HttpUrl ensureFolder(NextcloudSettings s) throws IOException {
        HttpUrl url = userRoot(s);
        for (String part : s.folder.split("/")) {
            String segment = part.trim();
            if (segment.isEmpty()) continue;
            url = url.newBuilder().addPathSegment(segment).build();
            Request request = base(s, url).method("MKCOL", null).build();
            try (Response response = CLIENT.newCall(request).execute()) {
                if (response.code() != 405) { // 405 = folder already exists
                    check(response);
                }
            }
        }
        return url;
    }

    private static void check(Response response) throws IOException {
        if (response.isSuccessful()) return;
        int code = response.code();
        switch (code) {
            case 401:
                throw new IOException("Login failed - check username and app password");
            case 403:
                throw new IOException("Access denied by the server");
            case 404:
                throw new IOException("Not found - check server address and username");
            case 507:
                throw new IOException("Nextcloud storage is full");
            default:
                throw new IOException("Server answered HTTP " + code);
        }
    }
}
