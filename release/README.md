# Release

This documents describes how to setup the release enviroment and how to publish a new version of the plugin.

## Setup

The plugin is signed with a hardware key, this folder contains files necessary for setting up a machine and releasing the plugin:

- `/99-android-signing-key.rules`
udev rule for correctly setting up the key.
- `/android-signing-key.pub`
The public key used for signing the plugin.
- `/gradle.properties.template`
A template file to configure publishing to Gradle Plugin Portal.
- `/publish`
A script used for publishing to [plugins.gradle.org](https://plugins.gradle.org)

Besides these files, the credentials to publish to the Gradle Plugin Portal setup according to [the gradle documentation](https://plugins.gradle.org/docs/publish-plugin#local-setup). Usually, that's in `~/.gradle/gradle.properties`.

Also make sure the following properties are set as well:

```
mullvad.rust-android-gradle.codeSigningEnabled=true
signing.gnupg.keyName=234B5F5BB6811BE0
```

## Releasing

To release a new version of the plugin, access the server that has been setup with according to the instructions in [Setup](#Setup).

Run the `publish` script, and then follow the instructions.
