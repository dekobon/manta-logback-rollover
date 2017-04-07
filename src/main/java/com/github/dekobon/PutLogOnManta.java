package com.github.dekobon;

import com.joyent.manta.client.MantaClient;
import com.joyent.manta.client.MantaObject;
import com.joyent.manta.config.ConfigContext;
import com.joyent.manta.exception.MantaClientException;
import com.joyent.manta.http.MantaHttpHeaders;
import com.joyent.manta.org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * Thread implementation that is responsible for copying the archived log files
 * to Manta.
 *
 * @author Elijah Zupancic
 * @since 1.0.0
 */
public class PutLogOnManta implements Callable<MantaObject> {
    public static MantaRolloverListener mantaRolloverListener = null;

    private final ConfigContext config;
    private final int mantaDurabilityLevel;
    private final String mantaLogDirectory;
    private final String filename;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public PutLogOnManta(final ConfigContext config,
                         final String mantaLogDirectory,
                         final String mantaDurabilityLevel,
                         final String filename) {
        this.config = config;
        String durabilityLevelStr = validateParam(mantaDurabilityLevel,
                "manta.durability_level", "MANTA_DURABILITY_LEVEL",
                "Durability level is the number of times to replicate the " +
                "file on manta");
        this.mantaDurabilityLevel = Integer.parseInt(durabilityLevelStr);

        if (StringUtils.isBlank(filename)) {
            throw new IllegalArgumentException("We can't roll over logs to " +
                "manta unless we have a valid filename");
        }

        this.mantaLogDirectory = validateParam(mantaLogDirectory,
                "manta.log_directory", "MANTA_LOG_DIR",
                "Manta log directory is the directory on manta where logs are " +
                        "copied to");

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
        if (StringUtils.isNotBlank(passedValue)) return passedValue;

        String systemPropertyValue = System.getProperty(systemProperty);
        if (StringUtils.isNotBlank(systemPropertyValue)) return systemPropertyValue;

        String envVarValue = System.getenv(envVar);
        if (StringUtils.isNotBlank(envVarValue)) return envVarValue;

        String msg = String.format(
                "Environment variable %s or system property %s not specified - %s",
                envVar, systemProperty, errorMessage
        );

        if (logger.isDebugEnabled()) {
            logger.debug(msg);
        }

        throw new IllegalArgumentException(msg);
    }

    @Override
    public MantaObject call() throws Exception {
        return copyToManta();
    }

    protected MantaObject copyToManta() throws IOException,
            MantaClientException {
        try (MantaClient client = new MantaClient(config)) {
            final File file = new File(filename);
            final String mantaFilename = String.format("%s/%s", mantaLogDirectory,
                    file.getName());
            final MantaHttpHeaders headers = new MantaHttpHeaders();
            headers.setDurabilityLevel(mantaDurabilityLevel);

            logger.info("Rolling log archive over to {}", mantaFilename);
            final MantaObject object = client.put(mantaFilename, file, headers);

            if (mantaRolloverListener != null) {
                mantaRolloverListener.rolledOver(filename, mantaFilename);
            }

            return object;
        }
    }
}
