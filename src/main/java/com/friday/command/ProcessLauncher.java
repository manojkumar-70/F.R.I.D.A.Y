package com.friday.command;

import java.net.URI;
import java.util.List;

/**
 * Seam for launching desktop processes and opening browser URIs.
 * Allows unit tests to verify launcher invocations deterministically without spawning OS windows.
 */
public interface ProcessLauncher {

    /**
     * Launches a program executable with arguments directly without shell expansion.
     *
     * @param executable the executable name or path
     * @param args list of command-line arguments
     * @throws Exception if launching fails
     */
    void launch(String executable, List<String> args) throws Exception;

    /**
     * Opens a URI in the default web browser.
     *
     * @param uri the URI to open
     * @throws Exception if browsing fails
     */
    void openUri(URI uri) throws Exception;
}

