package com.example;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.csoftware.termix.config.EnvironmentConfig;
import com.csoftware.termix.engine.SessionManager;
import com.csoftware.termix.engine.ShellDetector;
import com.csoftware.termix.engine.TerminalSession;
import com.csoftware.termix.ui.AnsiColorFilter;
import com.csoftware.termix.ui.SettingsActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main activity for Termix — Native Android Terminal Emulator.
 * Provides a native XML terminal interface, interactive shell execution,
 * session switching, quick-action keys, and safe conditional root request.
 */
public class MainActivity extends AppCompatActivity implements TerminalSession.SessionListener {

    private EnvironmentConfig config;
    private SessionManager sessionManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    // UI Elements
    private Spinner spnSessions;
    private ImageButton btnNewSession;
    private ImageButton btnCloseSession;
    private ImageButton btnSettings;
    private TextView tvRootBadge;
    private TextView tvCwd;
    private TextView tvStatusIndicator;
    private ScrollView scrollTerminal;
    private TextView tvTerminalOutput;

    // Quick Keys
    private Button btnKeyCtrlC;
    private Button btnKeyTab;
    private Button btnKeyEsc;
    private Button btnKeyUp;
    private Button btnKeyDown;
    private Button btnKeyClear;
    private Button btnKeyPipe;
    private Button btnKeySlash;
    private Button btnKeyDash;
    private Button btnKeyTilde;
    private Button btnKeyCdHome;

    // Command Input
    private TextView tvInputPrompt;
    private EditText etCommandInput;
    private ImageButton btnRunCommand;

    private ArrayAdapter<String> sessionSpinnerAdapter;
    private final List<String> sessionTitles = new ArrayList<>();
    private boolean isUpdatingSpinner = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);

        config = new EnvironmentConfig(this);
        sessionManager = new SessionManager(config);

        initViews();
        setupListeners();
        setupSessionSpinner();

        // Perform safe, conditional root detection workflow
        handleRootCheckAndInitialSession();
    }

    private void initViews() {
        spnSessions = findViewById(R.id.spn_sessions);
        btnNewSession = findViewById(R.id.btn_new_session);
        btnCloseSession = findViewById(R.id.btn_close_session);
        btnSettings = findViewById(R.id.btn_settings);
        tvRootBadge = findViewById(R.id.tv_root_badge);
        tvCwd = findViewById(R.id.tv_cwd);
        tvStatusIndicator = findViewById(R.id.tv_status_indicator);
        scrollTerminal = findViewById(R.id.scroll_terminal);
        tvTerminalOutput = findViewById(R.id.tv_terminal_output);

        btnKeyCtrlC = findViewById(R.id.btn_key_ctrl_c);
        btnKeyTab = findViewById(R.id.btn_key_tab);
        btnKeyEsc = findViewById(R.id.btn_key_esc);
        btnKeyUp = findViewById(R.id.btn_key_up);
        btnKeyDown = findViewById(R.id.btn_key_down);
        btnKeyClear = findViewById(R.id.btn_key_clear);
        btnKeyPipe = findViewById(R.id.btn_key_pipe);
        btnKeySlash = findViewById(R.id.btn_key_slash);
        btnKeyDash = findViewById(R.id.btn_key_dash);
        btnKeyTilde = findViewById(R.id.btn_key_tilde);
        btnKeyCdHome = findViewById(R.id.btn_key_cd_home);

        tvInputPrompt = findViewById(R.id.tv_input_prompt);
        etCommandInput = findViewById(R.id.et_command_input);
        btnRunCommand = findViewById(R.id.btn_run_command);
    }

    /**
     * Root Detection and Conditional Root Request:
     * Step A — Detect root availability (non-destructive).
     * Step B — If detected: prompt user with option to Grant or Continue Without Root.
     * Step C — If not detected: do NOT call su, do NOT prompt, launch standard terminal normally.
     */
    private void handleRootCheckAndInitialSession() {
        boolean rootAvailable = ShellDetector.isRootAvailable(config.getPath());

        if (rootAvailable) {
            int savedChoice = ShellDetector.getSavedRootChoice(this);
            if (savedChoice == ShellDetector.CHOICE_GRANT_ROOT) {
                // User previously granted root
                verifyAndLaunchSession(true);
            } else if (savedChoice == ShellDetector.CHOICE_CONTINUE_WITHOUT_ROOT) {
                // User previously chose normal mode
                startNewSession(false);
            } else {
                // Step B: First time root detected, show clear native dialog
                showRootRequestDialog();
            }
        } else {
            // Step C: Root not detected on device. Never call su, never show dialog.
            startNewSession(false);
        }
    }

    private void showRootRequestDialog() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_root_request, null);

        final CheckBox cbRemember = dialogView.findViewById(R.id.cb_remember_root_choice);
        Button btnNormal = dialogView.findViewById(R.id.btn_dialog_root_continue_normal);
        Button btnGrant = dialogView.findViewById(R.id.btn_dialog_root_grant);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        btnNormal.setOnClickListener(v -> {
            dialog.dismiss();
            boolean remember = cbRemember.isChecked();
            if (remember) {
                ShellDetector.saveRootChoice(this, ShellDetector.CHOICE_CONTINUE_WITHOUT_ROOT, true);
            }
            startNewSession(false);
        });

        btnGrant.setOnClickListener(v -> {
            dialog.dismiss();
            boolean remember = cbRemember.isChecked();
            tvStatusIndicator.setText("VERIFYING ROOT...");
            tvStatusIndicator.setTextColor(getResources().getColor(R.color.termix_warning));

            backgroundExecutor.execute(() -> {
                boolean granted = ShellDetector.verifyRootAccessGranted();
                mainHandler.post(() -> {
                    if (remember) {
                        ShellDetector.saveRootChoice(this,
                                granted ? ShellDetector.CHOICE_GRANT_ROOT : ShellDetector.CHOICE_CONTINUE_WITHOUT_ROOT,
                                true);
                    }
                    if (granted) {
                        Toast.makeText(MainActivity.this, "Root access verified. Launching root session.", Toast.LENGTH_SHORT).show();
                        startNewSession(true);
                    } else {
                        Toast.makeText(MainActivity.this, "Root access denied or unavailable. Continuing in standard mode.", Toast.LENGTH_LONG).show();
                        startNewSession(false);
                    }
                });
            });
        });

        dialog.show();
    }

    private void verifyAndLaunchSession(boolean requestRoot) {
        if (requestRoot) {
            tvStatusIndicator.setText("CHECKING SU...");
            backgroundExecutor.execute(() -> {
                boolean granted = ShellDetector.verifyRootAccessGranted();
                mainHandler.post(() -> startNewSession(granted));
            });
        } else {
            startNewSession(false);
        }
    }

    private void startNewSession(boolean isRoot) {
        TerminalSession session = sessionManager.createSession(isRoot);
        session.setListener(this);
        refreshSessionList();
        displayActiveSession();
    }

    private void setupSessionSpinner() {
        sessionSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sessionTitles);
        spnSessions.setAdapter(sessionSpinnerAdapter);

        spnSessions.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingSpinner) return;
                if (position != sessionManager.getActiveIndex()) {
                    sessionManager.setActiveIndex(position);
                    displayActiveSession();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void refreshSessionList() {
        isUpdatingSpinner = true;
        sessionTitles.clear();
        for (TerminalSession session : sessionManager.getSessions()) {
            sessionTitles.add(session.getTitle());
        }
        sessionSpinnerAdapter.notifyDataSetChanged();
        spnSessions.setSelection(sessionManager.getActiveIndex());
        isUpdatingSpinner = false;
    }

    private void displayActiveSession() {
        TerminalSession session = sessionManager.getActiveSession();
        if (session == null) return;

        session.setListener(this);

        // Update privilege badge
        if (session.isRoot()) {
            tvRootBadge.setText("# ROOT");
            tvRootBadge.setBackgroundResource(R.drawable.bg_badge_root);
            tvRootBadge.setTextColor(getResources().getColor(R.color.termix_prompt_root));
            tvInputPrompt.setText("# ");
            tvInputPrompt.setTextColor(getResources().getColor(R.color.termix_prompt_root));
        } else {
            tvRootBadge.setText("$ NORMAL");
            tvRootBadge.setBackgroundResource(R.drawable.bg_badge_normal);
            tvRootBadge.setTextColor(getResources().getColor(R.color.termix_green_primary));
            tvInputPrompt.setText("$ ");
            tvInputPrompt.setTextColor(getResources().getColor(R.color.termix_prompt_normal));
        }

        // Update CWD
        tvCwd.setText(session.getCurrentWorkingDirectory());

        // Update indicator
        tvStatusIndicator.setText(session.isRunning() ? "READY" : "STOPPED");
        tvStatusIndicator.setTextColor(session.isRunning()
                ? getResources().getColor(R.color.termix_green_light)
                : getResources().getColor(R.color.termix_error));

        // Render buffer text with ANSI colors
        tvTerminalOutput.setText(AnsiColorFilter.formatAnsi(session.getBufferContent()));
        scrollToBottom();
    }

    private void setupListeners() {
        // Execute command on Run button
        btnRunCommand.setOnClickListener(v -> executeCurrentInput());

        // Execute command on keyboard Done/Enter action
        etCommandInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                executeCurrentInput();
                return true;
            }
            return false;
        });

        // Session buttons
        btnNewSession.setOnClickListener(v -> {
            boolean rootAvailable = ShellDetector.isRootAvailable(config.getPath());
            if (rootAvailable) {
                new AlertDialog.Builder(this)
                        .setTitle("New Terminal Session")
                        .setMessage("Select session privilege level:")
                        .setPositiveButton("Root (#)", (d, w) -> startNewSession(true))
                        .setNegativeButton("Normal ($)", (d, w) -> startNewSession(false))
                        .setNeutralButton("Cancel", null)
                        .show();
            } else {
                startNewSession(false);
            }
        });

        btnCloseSession.setOnClickListener(v -> {
            if (sessionManager.getSessions().size() <= 1) {
                Toast.makeText(this, "Cannot close the only active session", Toast.LENGTH_SHORT).show();
                return;
            }
            sessionManager.closeSession(sessionManager.getActiveIndex());
            refreshSessionList();
            displayActiveSession();
        });

        btnSettings.setOnClickListener(v -> {
            TerminalSession active = sessionManager.getActiveSession();
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra("extra_is_root", active != null && active.isRoot());
            startActivity(intent);
        });

        tvRootBadge.setOnClickListener(v -> showAboutDialog());

        // Quick Keys
        btnKeyCtrlC.setOnClickListener(v -> {
            TerminalSession session = sessionManager.getActiveSession();
            if (session != null) {
                session.sendInterrupt();
            }
        });

        btnKeyTab.setOnClickListener(v -> insertTextIntoInput("\t"));
        btnKeyEsc.setOnClickListener(v -> {
            TerminalSession session = sessionManager.getActiveSession();
            if (session != null) {
                session.writeDirect("\u001B");
            }
        });

        btnKeyUp.setOnClickListener(v -> {
            TerminalSession session = sessionManager.getActiveSession();
            if (session != null) {
                String cmd = session.getPreviousHistoryCommand();
                etCommandInput.setText(cmd);
                etCommandInput.setSelection(cmd.length());
            }
        });

        btnKeyDown.setOnClickListener(v -> {
            TerminalSession session = sessionManager.getActiveSession();
            if (session != null) {
                String cmd = session.getNextHistoryCommand();
                etCommandInput.setText(cmd);
                etCommandInput.setSelection(cmd.length());
            }
        });

        btnKeyClear.setOnClickListener(v -> {
            TerminalSession session = sessionManager.getActiveSession();
            if (session != null) {
                session.clearBuffer();
            }
        });

        btnKeyPipe.setOnClickListener(v -> insertTextIntoInput(" | "));
        btnKeySlash.setOnClickListener(v -> insertTextIntoInput("/"));
        btnKeyDash.setOnClickListener(v -> insertTextIntoInput("-"));
        btnKeyTilde.setOnClickListener(v -> insertTextIntoInput("~"));

        btnKeyCdHome.setOnClickListener(v -> {
            etCommandInput.setText("cd ~");
            executeCurrentInput();
        });
    }

    private void insertTextIntoInput(String text) {
        int start = Math.max(etCommandInput.getSelectionStart(), 0);
        int end = Math.max(etCommandInput.getSelectionEnd(), 0);
        etCommandInput.getText().replace(Math.min(start, end), Math.max(start, end), text, 0, text.length());
    }

    private void executeCurrentInput() {
        TerminalSession session = sessionManager.getActiveSession();
        if (session == null) return;

        String command = etCommandInput.getText().toString();
        etCommandInput.setText("");
        session.executeCommand(command);
    }

    private void scrollToBottom() {
        scrollTerminal.post(() -> scrollTerminal.fullScroll(View.FOCUS_DOWN));
    }

    private void showAboutDialog() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_about, null);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btn_about_close).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // --- TerminalSession.SessionListener Callbacks ---

    @Override
    public void onOutput(TerminalSession session, String text) {
        if (session == sessionManager.getActiveSession()) {
            tvTerminalOutput.setText(AnsiColorFilter.formatAnsi(session.getBufferContent()));
            scrollToBottom();
        }
    }

    @Override
    public void onWorkingDirectoryChanged(TerminalSession session, String cwd) {
        if (session == sessionManager.getActiveSession()) {
            tvCwd.setText(cwd);
        }
    }

    @Override
    public void onProcessTerminated(TerminalSession session, int exitCode) {
        if (session == sessionManager.getActiveSession()) {
            tvStatusIndicator.setText("STOPPED (" + exitCode + ")");
            tvStatusIndicator.setTextColor(getResources().getColor(R.color.termix_error));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if environment settings updated
        config = new EnvironmentConfig(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sessionManager.destroyAll();
        backgroundExecutor.shutdownNow();
    }
}
