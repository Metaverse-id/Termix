package com.csoftware.termix.engine;

import com.csoftware.termix.config.EnvironmentConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages multiple active terminal sessions without sharing mutable shell state.
 */
public class SessionManager {

    private final EnvironmentConfig config;
    private final List<TerminalSession> sessions = new ArrayList<>();
    private int activeIndex = 0;
    private int sessionCounter = 1;

    public SessionManager(EnvironmentConfig config) {
        this.config = config;
    }

    /**
     * Creates and starts a new terminal session.
     */
    public synchronized TerminalSession createSession(boolean isRoot) {
        String id = "session_" + System.currentTimeMillis();
        String title = "Session " + sessionCounter + (isRoot ? " (Root)" : "");
        sessionCounter++;

        TerminalSession session = new TerminalSession(id, title, isRoot, config);
        sessions.add(session);
        activeIndex = sessions.size() - 1;
        session.start();
        return session;
    }

    public synchronized TerminalSession getActiveSession() {
        if (sessions.isEmpty()) {
            return null;
        }
        if (activeIndex < 0 || activeIndex >= sessions.size()) {
            activeIndex = 0;
        }
        return sessions.get(activeIndex);
    }

    public synchronized int getActiveIndex() {
        return activeIndex;
    }

    public synchronized void setActiveIndex(int index) {
        if (index >= 0 && index < sessions.size()) {
            this.activeIndex = index;
        }
    }

    public synchronized List<TerminalSession> getSessions() {
        return Collections.unmodifiableList(sessions);
    }

    /**
     * Closes the session at the specified index and frees resources.
     */
    public synchronized TerminalSession closeSession(int index) {
        if (index < 0 || index >= sessions.size()) {
            return null;
        }

        TerminalSession removed = sessions.remove(index);
        removed.destroy();

        if (sessions.isEmpty()) {
            // Automatically open a fresh default session if all were closed
            return createSession(false);
        }

        if (activeIndex >= sessions.size()) {
            activeIndex = sessions.size() - 1;
        }

        return getActiveSession();
    }

    /**
     * Shuts down and cleans up all sessions.
     */
    public synchronized void destroyAll() {
        for (TerminalSession session : sessions) {
            session.destroy();
        }
        sessions.clear();
    }
}
