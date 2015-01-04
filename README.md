Manta Logback Rollover
======================

This is a implementation of a [Logback](http://logback.qos.ch/) RollingPolicy
that copies logs triggered for archival to the open-source
[Manta storage system](https://apidocs.joyent.com/manta/).

Dependencies
------------

There are only two dependencies: [logback](http://logback.qos.ch/) and
[java-manta](https://github.com/joyent/java-manta). Right now this project
is dependent on [my java-manta fork](https://github.com/dekobon/java-manta).
I hope to get my changes in the fork back ported into main code base. Until
then, you will need to build the Java Manta from my fork and use it to build
this project. You can find the forked jar
[here](https://github.com/dekobon/java-manta/releases/tag/1.5.2-dekobon-fork).

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
