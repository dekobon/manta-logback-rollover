Manta Logback Rollover
======================

This is a implementation of a [Logback](http://logback.qos.ch/) RollingPolicy
that copies logs triggered for archival to the open-source
[Manta storage system](https://apidocs.joyent.com/manta/).

Configuration
-------------

Configuration can be done in one of three ways:

 * The Logback configuration file.
 * As Java system properties.
 * As environment variables.

| Logback               | Java System Property   | Environment Variable   |
| --------------------- | ---------------------- | ---------------------- |
| mantaUrl              | manta.url              | MANTA_URL              |
| mantaUser             | manta.user             | MANTA_USER             |
| mantaKeyPath          | manta.key_path         | MANTA_KEY_PATH         |
| mantaKeyFingerprint   | manta.key_fingerprint  | MANTA_KEY_ID           |
| mantaLogDirectory     | manta.log_directory    | MANTA_LOG_DIR          |
| mantaRetryAttempts    | manta.retry_attempts   | MANTA_RETRY_ATTEMPTS   |
| mantaDurabilityLevel  | manta.durability_level | MANTA_DURABILITY_LEVEL |

We will look first at any values in the Logback configuration, then we will
look in the Java system properties and finally we will look for an environment
variable specifying the setting.

As of right now, we don't have defaults for any of the settings. That said,
here are some sensible defaults and sample values:

```
mantaUrl             = https://us-east.manta.joyent.com
mantaUser            = username
mantaKeyPath         = /home/user/.ssh/manta_id_rsa
mantaKeyFingerprint  = 00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00
mantaLogDirectory    = /mantauser/stor/logs/
mantaRetryAttempts   = 3
mantaDurabilityLevel = 3
```

Here's what a sample roll-over configuration may look like:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="Console" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} ${HOSTNAME} [%thread] %-5level [%X{tracker}] %logger - %msg%n</pattern>
        </encoder>
    </appender>

    <appender name="RolloverFile" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <encoder>
            <charset>utf-8</charset>
            <pattern>%msg%n</pattern>
        </encoder>
        <append>true</append>
        <file>logs/rollover.log</file>

        <rollingPolicy class="ch.qos.logback.core.rolling.MantaTimeBasedRollingPolicy">
            <!-- Manta Rollover variables -->
            <mantaRetryAttempts>3</mantaRetryAttempts>
            <mantaDurabilityLevel>3</mantaDurabilityLevel>
            <!-- Assume that the other variables came in via system properies -->

            <fileNamePattern>logs/archive/rollover-%d{yyyy-MM-dd}-%i.log.gz</fileNamePattern>
            <timeBasedFileNamingAndTriggeringPolicy
                    class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
                <maxFileSize>1GB</maxFileSize>
            </timeBasedFileNamingAndTriggeringPolicy>
        </rollingPolicy>
    </appender>

    <logger name="rollover.test" level="trace" additivity="false">
        <appender-ref ref="RolloverFile" />
    </logger>

    <root level="debug">
        <appender-ref ref="Console" />
    </root>
</configuration>
```
