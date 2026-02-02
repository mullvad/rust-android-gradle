# Release

This documents describes how to setup the release enviroment and how to publish
a new version of the plugin.

## Setup

The plugin is signed with a hardware key, this folder contains files necessary
for setting up a machine and releasing the plugin:

- `/99-android-signing-key.rules`
udev rule for correctly setting up the key.
- `/android-signing-key.pub`
The public key used for signing the plugin.
- `/gradle.properties.template`
A template file to configure publishing to Gradle Plugin Portal.
- `/publish`
A script used for publishing to [plugins.gradle.org](https://plugins.gradle.org)

Besides these files, the credentials to publish to the Gradle Plugin Portal setup according to
[the gradle documentation](https://plugins.gradle.org/docs/publish-plugin#local-setup). Usually, that's in ~/.gradle/gradle.properties.

Also make sure the following properties are set as well:

mullvad.rust-android-gradle.codeSigningEnabled=true
signing.gnupg.keyName=[signing key fingerprint]

## Releasing

At top-level, the publishPlugins Gradle task publishes the plugin for consumption:

$ ./gradlew publishPlugins
...
Publishing plugin org.mozilla.rust-android-gradle.rust-android version 0.8.1
Publishing artifact build/libs/plugin-0.8.1.jar
Publishing artifact build/libs/plugin-0.8.1-sources.jar
Publishing artifact build/libs/plugin-0.8.1-javadoc.jar
Publishing artifact build/publish-generated-resources/pom.xml
Activating plugin org.mozilla.rust-android-gradle.rust-android version 0.8.1

Furthermore, all published artifacts to the Gradle plugin portal must be signed. This is done through the signing plugin, and the following values must be set in e.g. /.gradle/gradle.properties:




## Releasing

To release enter a server that has been setup with:
 - Hardware key
 - Gradle publishing credentials
