package com.csoftware.termix.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.csoftware.termix.config.EnvironmentConfig;
import com.csoftware.termix.engine.PathResolver;
import com.csoftware.termix.engine.ShellDetector;
import com.example.R;

import java.util.List;

/**
 * Activity for viewing and configuring Termix environment variables,
 * executable search PATH, shell executable, and root mode options.
 */
public class SettingsActivity extends AppCompatActivity {

    private EnvironmentConfig config;

    private TextView tvRootAvailable;
    private TextView tvActivePrivilege;
    private TextView tvShellBin;
    private EditText etHome;
    private EditText etEnv;
    private EditText etUser;
    private EditText etPath;
    private TextView tvPathInspector;
    private Button btnRequestRoot;
    private Button btnResetDefaults;
    private Button btnSave;
    private ImageButton btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        config = new EnvironmentConfig(this);

        initViews();
        loadData();
        setupListeners();
    }

    private void initViews() {
        tvRootAvailable = findViewById(R.id.tv_settings_root_available);
        tvActivePrivilege = findViewById(R.id.tv_settings_active_privilege);
        tvShellBin = findViewById(R.id.tv_settings_shell_bin);
        etHome = findViewById(R.id.et_env_home);
        etEnv = findViewById(R.id.et_env_env);
        etUser = findViewById(R.id.et_env_user);
        etPath = findViewById(R.id.et_env_path);
        tvPathInspector = findViewById(R.id.tv_path_inspector);
        btnRequestRoot = findViewById(R.id.btn_settings_request_root);
        btnResetDefaults = findViewById(R.id.btn_reset_defaults);
        btnSave = findViewById(R.id.btn_settings_save);
        btnBack = findViewById(R.id.btn_settings_back);
    }

    private void loadData() {
        boolean rootAvailable = ShellDetector.isRootAvailable(config.getPath());
        tvRootAvailable.setText(rootAvailable ? "Available (su detected)" : "Not Detected on Device");
        tvRootAvailable.setTextColor(rootAvailable ? getResources().getColor(R.color.termix_green_primary) : getResources().getColor(R.color.termix_text_dim));

        boolean isActiveRoot = getIntent().getBooleanExtra("extra_is_root", false);
        tvActivePrivilege.setText(isActiveRoot ? "Elevated Root (#)" : "Standard Unprivileged ($)");
        tvActivePrivilege.setTextColor(isActiveRoot ? getResources().getColor(R.color.termix_prompt_root) : getResources().getColor(R.color.termix_green_light));

        tvShellBin.setText(ShellDetector.detectShellBinary());

        etHome.setText(config.getHome());
        etEnv.setText(config.getEnv());
        etUser.setText(config.getUser());
        etPath.setText(config.getPath());

        updatePathInspector(config.getPath());
    }

    private void updatePathInspector(String pathString) {
        List<PathResolver.PathEntry> entries = PathResolver.inspectPath(pathString);
        StringBuilder sb = new StringBuilder();
        sb.append("PATH Directory Status (in exact search order):\n");
        int index = 1;
        for (PathResolver.PathEntry entry : entries) {
            String status = entry.exists ? "[EXISTS] " : "[ABSENT] ";
            sb.append(index).append(". ").append(status).append(" ").append(entry.path).append("\n");
            index++;
        }
        sb.append("\nNote: Directories absent on device are preserved in configuration without error.");
        tvPathInspector.setText(sb.toString());
    }

    private void setupListeners() {
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        etPath.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePathInspector(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });

        btnResetDefaults.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Reset Environment")
                        .setMessage("Reset all Termix environment variables back to initial values?")
                        .setPositiveButton("Reset", (dialog, which) -> {
                            config.resetToDefaults();
                            loadData();
                            Toast.makeText(SettingsActivity.this, "Reset to Termix initial defaults", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        btnRequestRoot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ShellDetector.resetRootChoice(SettingsActivity.this);
                Toast.makeText(SettingsActivity.this, "Root authorization preference reset. New sessions will check root options.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveSettings() {
        String home = etHome.getText().toString().trim();
        String env = etEnv.getText().toString().trim();
        String user = etUser.getText().toString().trim();
        String path = etPath.getText().toString().trim();

        if (home.isEmpty() || env.isEmpty() || user.isEmpty() || path.isEmpty()) {
            Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        config.saveConfiguration(home, env, user, path);
        Toast.makeText(this, "Environment saved. Applied to new terminal sessions.", Toast.LENGTH_SHORT).show();
        finish();
    }
}
