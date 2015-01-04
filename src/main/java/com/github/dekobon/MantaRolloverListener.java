package com.github.dekobon;

/**
 * Listener interface invoked when a log file has been rolled over and archived
 * on Manta.
 *
 * @author Elijah Zupancic
 * @since 1.0.0
 */
public interface MantaRolloverListener {
    void rolledOver(String filename, String mantaFilename);
}
