package com.github.dekobon;

import com.joyent.manta.client.MantaClient;
import com.joyent.manta.client.MantaObject;
import com.joyent.manta.exception.MantaClientException;
import com.joyent.manta.exception.MantaClientHttpResponseException;
import com.joyent.manta.exception.MantaObjectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

/**
 * Thread implementation that is responsible for copying the archived log files
 * to Manta.
 *
 * @author Elijah Zupancic
 * @since 1.0.0
 */
public class PutLogOnManata implements Callable<MantaObject> {
    public static MantaRolloverListener mantaRolloverListener = null;

    private final String mantaUrl;
    private final String mantaUser;
    private final String mantaKeyPath;
    private final String mantaKeyFingerprint;
    private final String mantaLogDirectory;
    private final int mantaDurabilityLevel;
    private final int mantaRetryAttempts;
    private final String filename;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public PutLogOnManata(String mantaUrl,
                          String mantaUser,
                          String mantaKeyPath,
                          String mantaKeyFingerprint,
                          String mantaLogDirectory,
                          String mantaRetryAttempts,
                          String mantaDurabilityLevel,
                          String filename) {
        this.mantaUrl = validateParam(mantaUrl,
            "manta.url", "MANTA_URL",
            "Manta URL must be specified. It is typically a value like: " +
            "https://us-east.manta.joyent.com");
        this.mantaUser = validateParam(mantaUser, "manta.user", "MANTA_USER",
            "Manta User is the primary account holder's username");
        this.mantaKeyPath = validateParam(mantaKeyPath,
            "manta.key_path", "MANTA_KEY_PATH",
            "Manta Key Path is the path to the SSH key user to access manta");
        this.mantaKeyFingerprint = validateParam(mantaKeyFingerprint,
            "manta.key_fingerprint", "MANTA_KEY_ID",
            "Manta key finger print is the fingerprint of the SSH key used " +
            "to access manta");
        this.mantaLogDirectory = validateParam(mantaLogDirectory,
            "manta.log_directory", "MANTA_LOG_DIR",
            "Manta log directory is the directory on manta where logs are " +
            "copied to");

        String attemptsStr = validateParam(mantaRetryAttempts,
            "manta.retry_attempts", "MANTA_RETRY_ATTEMPTS",
            "Manta retry attempts is the number of times to retry copying to " +
            "manta");

        this.mantaRetryAttempts = Integer.parseInt(attemptsStr);

        String durabilityLevelStr = validateParam(mantaDurabilityLevel,
                "manta.durability_level", "MANTA_DURABILITY_LEVEL",
                "Durability level is the number of times to replicate the " +
                "file on manta");
        this.mantaDurabilityLevel = Integer.parseInt(durabilityLevelStr);

        if (isBlank(filename)) {
            throw new IllegalArgumentException("We can't roll over logs to " +
                "manta unless we have a valid filename");
        }

        this.filename = filename;
    }

    /**
     * Finds the correct setting for the property and returns it.
     * @param passedValue setting given to us by the constructor
     * @param systemProperty system property key to look for setting
     * @param envVar environment variable name to look for setting
     * @param errorMessage error message to display if setting is not found
     * @return the proper setting
     */
    private String validateParam(String passedValue,
                                        String systemProperty,
                                        String envVar,
                                        String errorMessage) {
        if (isNotBlank(passedValue)) return passedValue;

        String systemPropertyValue = System.getProperty(systemProperty);
        if (isNotBlank(systemPropertyValue)) return systemPropertyValue;

        String envVarValue = System.getenv(envVar);
        if (isNotBlank(envVarValue)) return envVarValue;

        if (logger.isDebugEnabled()) {
            logger.debug(errorMessage);
        }

        throw new IllegalArgumentException(errorMessage);
    }

    @Override
    public MantaObject call() throws Exception {
        int timesRetried = 0;
        Exception lastException = null;

        while (timesRetried < mantaRetryAttempts) {
            try {
                return copyToManta();
            } catch (Exception e) {
                if (++timesRetried < mantaRetryAttempts) {
                    logger.warn("Error copying logs to manta. Retrying " +
                            timesRetried + "/" + mantaRetryAttempts, e);
                } else {
                    lastException = e;
                }
            }
        }

        throw lastException;
    }

    protected MantaObject copyToManta() throws IOException,
            MantaClientException {
        MantaClient client = MantaClient.newInstance(mantaUrl, mantaUser, mantaKeyPath,
                mantaKeyFingerprint);
        File file = new File(filename);
        InputStream fileStream = new FileInputStream(file);
        String mantaFilename = String.format("%s/%s", mantaLogDirectory,
                file.getName());
        MantaObject mantaObject = new MantaObject(mantaFilename);
        mantaObject.setHeader("durability-level", mantaDurabilityLevel);
        mantaObject.setDataInputStream(fileStream);

        logger.info("Rolling log archive over to {}",
                mantaFilename);

        try {
            createDirectories(client);
        } catch (Exception e) {
            logger.error("foo", e);
        }

        client.put(mantaObject);

        if (mantaRolloverListener != null) {
            mantaRolloverListener.rolledOver(filename, mantaFilename);
        }

        return mantaObject;
    }

    protected void createDirectories(MantaClient client) throws IOException,
            MantaClientException, MantaObjectException {
        String[] directories = mantaLogDirectory.split("/");
        StringBuilder appendedDir = new StringBuilder();

        for (String d : directories) {
            if (d.isEmpty()) continue;

            appendedDir.append("/").append(d);

            try {
                client.listObjects(appendedDir.toString());
            } catch (MantaClientHttpResponseException e) {
                if (e.getStatusCode() == 404) {
                    logger.info("Creating directory on Manta: {}", appendedDir);
                    client.putDirectory(appendedDir.toString(), null);
                } else {
                    throw e;
                }
            }
        }
    }

    /**
     * Taken from Apache Commons Lang 3.
     *
     * <p>Checks if a CharSequence is whitespace, empty ("") or null.</p>
     *
     * <pre>
     * StringUtils.isBlank(null)      = true
     * StringUtils.isBlank("")        = true
     * StringUtils.isBlank(" ")       = true
     * StringUtils.isBlank("bob")     = false
     * StringUtils.isBlank("  bob  ") = false
     * </pre>
     *
     * @param cs  the CharSequence to check, may be null
     * @return {@code true} if the CharSequence is null, empty or whitespace
     * @since 2.0
     * @since 3.0 Changed signature from isBlank(String) to isBlank(CharSequence)
     */
    protected static boolean isBlank(final CharSequence cs) {
        int strLen;
        if (cs == null || (strLen = cs.length()) == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (Character.isWhitespace(cs.charAt(i)) == false) {
                return false;
            }
        }
        return true;
    }

    /**
     * Taken from Apache Commons Lang 3.
     *
     * <p>Checks if a CharSequence is not empty (""), not null and not whitespace only.</p>
     *
     * <pre>
     * StringUtils.isNotBlank(null)      = false
     * StringUtils.isNotBlank("")        = false
     * StringUtils.isNotBlank(" ")       = false
     * StringUtils.isNotBlank("bob")     = true
     * StringUtils.isNotBlank("  bob  ") = true
     * </pre>
     *
     * @param cs  the CharSequence to check, may be null
     * @return {@code true} if the CharSequence is
     *  not empty and not null and not whitespace
     * @since 2.0
     * @since 3.0 Changed signature from isNotBlank(String) to isNotBlank(CharSequence)
     */
    protected static boolean isNotBlank(final CharSequence cs) {
        return !isBlank(cs);
    }
}
