package ch.qos.logback.core.rolling;

import ch.qos.logback.core.CoreConstants;
import com.github.dekobon.PutLogOnManata;
import com.joyent.manta.client.MantaObject;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Roll over policy that rolls over logs to the configured archive directory
 * and copies the logs to Manta.
 *
 * @author Elijah Zupancic
 * @since 1.0.0
 */
public class MantaTimeBasedRollingPolicy extends TimeBasedRollingPolicy {
    protected final MantaRolloverThreadFactory threadFactory =
            new MantaRolloverThreadFactory();

    String mantaUrl;
    String mantaUser;
    String mantaKeyPath;
    String mantaKeyFingerprint;
    String mantaLogDirectory;
    String mantaRetryAttempts;
    String mantaDurabilityLevel;

    public MantaTimeBasedRollingPolicy() {
        super();
    }

    @Override
    public void rollover() throws RolloverFailure {
        super.rollover();

        final String filename = archivedFilename();

        switch (getCompressionMode()) {
            case NONE:
                copyToManta(filename);
            default:
                if (future == null) return;

                while (!future.isDone()) {
                    if (future.isCancelled()) return;

                    try {
                        future.get(CoreConstants.SECONDS_TO_WAIT_FOR_COMPRESSION_JOBS, TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        addError("Timeout while waiting for compression job to finish", e);
                    } catch (Exception e) {
                        addError("Unexpected exception while waiting for compression job to finish", e);
                    }
                }

                copyToManta(filename);
        }
    }

    private String archivedFilename() {
        String elapsedPeriodsFileName = super.timeBasedFileNamingAndTriggeringPolicy
                .getElapsedPeriodsFileName();

        switch (getCompressionMode()) {
            case NONE:
                return elapsedPeriodsFileName;
            case GZ:
                return String.format("%s.%s", elapsedPeriodsFileName, "gz");
            case ZIP:
                return String.format("%s.%s", elapsedPeriodsFileName, "zip");
            default:
                throw new UnsupportedOperationException(
                        String.format("Unknown compression mode: %s",
                                getCompressionMode()));
        }
    }

    private void copyToManta(String filename) throws RolloverFailure {
        Callable<MantaObject> copy = new PutLogOnManata(
                mantaUrl,
                mantaUser,
                mantaKeyPath,
                mantaKeyFingerprint,
                mantaLogDirectory,
                mantaRetryAttempts,
                mantaDurabilityLevel,
                filename);

        ExecutorService executor = Executors.newScheduledThreadPool(1,
                threadFactory);
        super.future = executor.submit(copy);
        executor.shutdown();
    }

    class MantaRolloverThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String nameFormat = "manta-rollover-%d";
        private final Thread.UncaughtExceptionHandler handler =
                new UncaughtMantaExceptionHandler();

        @Override
        public Thread newThread(Runnable r) {
            String name = String.format(nameFormat, threadNumber.getAndIncrement());
            Thread t = new Thread(r, name);
            t.setDaemon(false); // Don't let the JVM exit until we are done
            t.setPriority(Thread.NORM_PRIORITY);
            t.setUncaughtExceptionHandler(handler);
            return t;
        }
    }

    class UncaughtMantaExceptionHandler implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            addError("Error copying logs to Manta", e);
        }
    }

    @Override
    public String toString() {
        return "c.q.l.core.rolling.MantaTimeBasedRollingPolicy";
    }

    public String getMantaUrl() {
        return mantaUrl;
    }

    public void setMantaUrl(String mantaUrl) {
        this.mantaUrl = mantaUrl;
    }

    public String getMantaUser() {
        return mantaUser;
    }

    public void setMantaUser(String mantaUser) {
        this.mantaUser = mantaUser;
    }

    public String getMantaKeyPath() {
        return mantaKeyPath;
    }

    public void setMantaKeyPath(String mantaKeyPath) {
        this.mantaKeyPath = mantaKeyPath;
    }

    public String getMantaKeyFingerprint() {
        return mantaKeyFingerprint;
    }

    public void setMantaKeyFingerprint(String mantaKeyFingerprint) {
        this.mantaKeyFingerprint = mantaKeyFingerprint;
    }

    public String getMantaLogDirectory() {
        return mantaLogDirectory;
    }

    public void setMantaLogDirectory(String mantaLogDirectory) {
        this.mantaLogDirectory = mantaLogDirectory;
    }

    public String getMantaRetryAttempts() {
        return mantaRetryAttempts;
    }

    public void setMantaRetryAttempts(String mantaRetryAttempts) {
        this.mantaRetryAttempts = mantaRetryAttempts;
    }

    public String getMantaDurabilityLevel() {
        return mantaDurabilityLevel;
    }

    public void setMantaDurabilityLevel(String mantaDurabilityLevel) {
        this.mantaDurabilityLevel = mantaDurabilityLevel;
    }
}
