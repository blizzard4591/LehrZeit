# Releasing LehrZeit

How to publish a new version, on GitHub and on F-Droid.

## 1. Prepare the version

1. `versionName` and `versionCode` in `app/build.gradle.kts` are raised with every change to the app, following semantic versioning, with `versionCode` going up by one each time. They should already be right; check that no tag uses them yet.
2. Write the changelog, at most 500 characters per language:
   - `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
   - `fastlane/metadata/android/de-DE/changelogs/<versionCode>.txt`

   Only released versions get one. The text for the first release is in `1.txt`; rename it to the first release's `versionCode`.
3. Update the screenshots in `fastlane/metadata/android/*/images/phoneScreenshots/` if the screens changed.
4. Commit, then tag the commit as `v<versionName>`, e.g. `v0.1.0`, and push the tag. F-Droid finds new versions through these tags.

## 2. Build and sign the APK

The release build is reproducible: the same commit always produces the same unsigned APK, byte for byte. This was checked for 0.1.0 by building it on Windows (JDK 25, two different folders, CRLF and LF checkouts) and on Linux (JDK 17), with identical results.

Gradle signs the release APK with your key if it finds it in `~/.gradle/gradle.properties` (on Windows `C:\Users\<name>\.gradle\gradle.properties`), which lies outside the repository:

```properties
lehrzeit.storeFile=<path>/lehrzeit-release.jks
lehrzeit.keyAlias=lehrzeit
lehrzeit.storePassword=<password>
```

The key is created once with `keytool`, which comes with the JDK: `keytool -genkeypair -v -keystore lehrzeit-release.jks -alias lehrzeit -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=<name>, O=<domain>, C=<country code>"`. The certificate, and with it these names, can be read from every APK. In a PKCS12 keystore, which is keytool's default, the key has the keystore's password. Without these properties, for example on F-Droid's build server, the release APK stays unsigned.

```sh
./gradlew distRelease
```

This writes `dist/LehrZeit-<versionName>-release-<commit>.apk`. Build from the tagged commit with no uncommitted changes, so the name doesn't end in `-dirty`. Rename the APK to `LehrZeit-<versionName>.apk`, the name the F-Droid recipe expects, and attach it to a GitHub release for the tag.

The keystore must never be committed. Keep a backup of it and its password in two places: without them, no update can be signed, and users would have to uninstall and reinstall.

## 3. F-Droid

F-Droid builds the app itself from the tagged source and reads the store texts, icon and screenshots from `fastlane/metadata/android/`. To add the app, open a merge request at <https://gitlab.com/fdroid/fdroiddata> with a file `metadata/de.janaberger.lehrzeit.yml`. A draft follows; the `Builds` entry and `CurrentVersion` have to match the release. `fdroid lint` and `fdroid rewritemeta` from fdroidserver check the file and put the fields in the expected order.

```yaml
AntiFeatures:
  NonFreeNet:
    en-US: Can download the timetable from WebUntis, a proprietary service, if the user enters a link.
    de-DE: Kann den Stundenplan von WebUntis herunterladen, einem proprietären Dienst, wenn ein Link eingegeben wird.
Categories:
  - Time
License: GPL-3.0-or-later
AuthorName: Jana Berger
SourceCode: https://github.com/blizzard4591/LehrZeit
IssueTracker: https://github.com/blizzard4591/LehrZeit/issues
Changelog: https://github.com/blizzard4591/LehrZeit/releases

AutoName: LehrZeit

RepoType: git
Repo: https://github.com/blizzard4591/LehrZeit.git
Binaries: https://github.com/blizzard4591/LehrZeit/releases/download/v%v/LehrZeit-%v.apk

Builds:
  - versionName: 0.2.0
    versionCode: 2
    commit: v0.2.0
    subdir: app
    gradle:
      - yes

AllowedAPKSigningKeys: e6c3cb85af51a11135c968a2f741f6516e9b957c72d8167938bca52f13c10e3c

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 0.2.0
CurrentVersionCode: 2
```

The `NonFreeNet` anti-feature is a guess: the app only contacts WebUntis (or another timetable server) if the user enters a link, but F-Droid usually marks optional use of a proprietary network service, too. The reviewers decide; drop the block if they disagree.

By default, F-Droid signs the APK with its own key. `Binaries` and `AllowedAPKSigningKeys` make it publish the APK signed with your key instead, which a reproducible build allows, so the F-Droid and GitHub downloads can update each other. F-Droid builds the app, copies the signature from the APK in the GitHub release onto its own unsigned build, and only publishes your APK if the signature still verifies, that is, if both builds match.

`AllowedAPKSigningKeys` is the SHA-256 of the signing certificate (`CN=Jana Berger, O=janaberger.de, C=DE`), without colons and in lower case. `apksigner verify --print-certs LehrZeit-<versionName>.apk` shows it as "certificate SHA-256 digest".

## Checking reproducibility again

After changing the build setup (Gradle, AGP, Kotlin or new libraries), build the same commit twice from fresh clones in different folders, ideally once on Linux, and compare the unsigned APKs:

```sh
sha256sum app-release-unsigned.apk
```

If they differ, `diffoscope` shows where.

To check the signed APK the way F-Droid does, compare it with an unsigned build of the same commit, e.g. from a fresh clone on a computer without the signing properties. This needs [apksigcopier](https://github.com/obfusk/apksigcopier) (`pip install apksigcopier`) and `apksigner` on the `PATH`:

```sh
apksigcopier compare --unsigned LehrZeit-<versionName>.apk app-release-unsigned.apk
```

It prints nothing if they match. This was checked for 0.2.0: the release APK signed by Gradle with the release key matched the unsigned build.
