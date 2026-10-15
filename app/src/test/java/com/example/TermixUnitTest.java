package com.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.csoftware.termix.config.EnvironmentConfig;
import com.csoftware.termix.engine.PathResolver;
import com.csoftware.termix.engine.ShellDetector;
import com.csoftware.termix.ui.AnsiColorFilter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TermixUnitTest {

    private Context context;
    private EnvironmentConfig config;

    @Before
    public void setup() {
        context = ApplicationProvider.getApplicationContext();
        config = new EnvironmentConfig(context);
    }

    @Test
    public void testExactEnvironmentVariableDefaults() {
        assertEquals("/data/data/com.csoftware.termix/files/home", config.getHome());
        assertEquals("/data/data/com.csoftware.termix/env/home", config.getEnv());
        assertEquals("/data/user/0/com.csoftware.termix/files/home", config.getUser());
        assertEquals("/system/bin:/xbin:/system/xbin:/sbin:/system_ext/bin:/bin", config.getPath());

        Map<String, String> map = config.getEnvironmentMap();
        assertEquals(config.getHome(), map.get("HOME"));
        assertEquals(config.getEnv(), map.get("ENV"));
        assertEquals(config.getUser(), map.get("USER"));
        assertEquals(config.getPath(), map.get("PATH"));
    }

    @Test
    public void testPathResolverOrderPreservation() {
        String testPath = "/system/bin:/xbin:/system/xbin:/sbin:/system_ext/bin:/bin";
        List<PathResolver.PathEntry> entries = PathResolver.inspectPath(testPath);

        assertEquals(6, entries.size());
        assertEquals("/system/bin", entries.get(0).path);
        assertEquals("/xbin", entries.get(1).path);
        assertEquals("/system/xbin", entries.get(2).path);
        assertEquals("/sbin", entries.get(3).path);
        assertEquals("/system_ext/bin", entries.get(4).path);
        assertEquals("/bin", entries.get(5).path);
    }

    @Test
    public void testAnsiColorFilter() {
        CharSequence formatted = AnsiColorFilter.formatAnsi("\u001B[32mTermix Green\u001B[0m Normal");
        assertNotNull(formatted);
        assertTrue(formatted.toString().contains("Termix Green Normal"));
    }

    @Test
    public void testShellDetectorFallback() {
        String shell = ShellDetector.detectShellBinary();
        assertNotNull(shell);
        assertTrue(shell.endsWith("sh") || shell.endsWith("box"));
    }
}
