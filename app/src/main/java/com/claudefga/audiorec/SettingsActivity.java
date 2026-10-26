package com.claudefga.audiorec;

import android.app.Activity;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;

/** Lets the user enter the Nextcloud account and target folder. */
public class SettingsActivity extends Activity {

    private EditText serverField;
    private EditText userField;
    private EditText passwordField;
    private EditText folderField;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        serverField = findViewById(R.id.server_field);
        userField = findViewById(R.id.user_field);
        passwordField = findViewById(R.id.password_field);
        folderField = findViewById(R.id.folder_field);
        statusView = findViewById(R.id.status_text);

        NextcloudSettings saved = NextcloudSettings.load(this);
        serverField.setText(saved.server);
        userField.setText(saved.user);
        passwordField.setText(saved.password);
        folderField.setText(saved.folder);

        findViewById(R.id.save_button).setOnClickListener(v -> save());
        findViewById(R.id.test_button).setOnClickListener(v -> test());
        findViewById(R.id.remove_button).setOnClickListener(v -> remove());
    }

    private NextcloudSettings read() {
        return new NextcloudSettings(
                serverField.getText().toString(),
                userField.getText().toString(),
                passwordField.getText().toString(),
                folderField.getText().toString());
    }

    private boolean validate(NextcloudSettings s) {
        if (!s.isConfigured()) {
            statusView.setText(R.string.fill_all);
            return false;
        }
        if (!s.isHttps()) {
            statusView.setText(R.string.https_only);
            return false;
        }
        return true;
    }

    private void save() {
        NextcloudSettings s = read();
        if (s.server.isEmpty() && s.user.isEmpty() && s.password.isEmpty()) {
            remove();
            return;
        }
        if (!validate(s)) return;
        s.save(this);
        statusView.setText(R.string.saved);
    }

    private void remove() {
        NextcloudSettings.clear(this);
        serverField.setText("");
        userField.setText("");
        passwordField.setText("");
        folderField.setText(NextcloudSettings.DEFAULT_FOLDER);
        statusView.setText(R.string.removed);
    }

    private void test() {
        final NextcloudSettings s = read();
        if (!validate(s)) return;
        statusView.setText(R.string.testing);
        new Thread(() -> {
            String result;
            try {
                NextcloudUploader.testConnection(s);
                result = getString(R.string.test_ok);
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : e.toString();
                result = getString(R.string.test_failed, message);
            }
            final String text = result;
            runOnUiThread(() -> statusView.setText(text));
        }).start();
    }
}
