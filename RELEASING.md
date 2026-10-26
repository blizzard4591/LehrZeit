# Releasing LehrZeit

How to publish a new version, on GitHub and on F-Droid.

## 1. Prepare the version

1. In `app/build.gradle.kts`, raise `versionCode` by one and set `versionName`.
2. Write the changelog, at most 500 characters per language:
   - `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
   - `fastlane/metadata/android/de-DE/changelogs/<versionCode>.txt`
3. Update the screenshots in `fastlane/metadata/android/*/images/phoneScreenshots/` if the screens changed.
4. Commit, then tag the commit as `v<versionName>`, e.g. `v0.1.0`, and push the tag. F-Droid finds new versions through these tags.

## 2. Build and sign the APK

The release build is reproducible: the same commit always produces the same unsigned APK, byte for byte. This was checked for 0.1.0 by building it on Windows (JDK 25, two different folders, CRLF and LF checkouts) and on Linux (JDK 17), with identical results.

```sh
./gradlew assembleRelease
apksigner sign --ks lehrzeit-release.jks --out LehrZeit-0.1.0.apk \
    app/build/outputs/apk/release/app-release-unsigned.apk
```

`apksigner` is in the Android SDK under `build-tools/<version>/`. The APK built by Gradle is already zip-aligned, as `apksigner` requires.

The keystore (`lehrzeit-release.jks` above) must never be committed. Keep a backup: without it, no update can be signed, and users would have to uninstall and reinstall.

Attach the signed APK to a GitHub release for the tag.

## 3. F-Droid

F-Droid builds the app itself from the tagged source and reads the store texts, icon and screenshots from `fastlane/metadata/android/`. To add the app, open a merge request at <https://gitlab.com/fdroid/fdroiddata> with a file `metadata/de.janaberger.lehrzeit.yml`. A draft follows; replace `<github-user>` with the GitHub account that hosts the repository.

```yaml
Categories:
  - Time
License: GPL-3.0-or-later
AuthorName: Jana Berger
SourceCode: https://github.com/<github-user>/LehrZeit
IssueTracker: https://github.com/<github-user>/LehrZeit/issues
Changelog: https://github.com/<github-user>/LehrZeit/releases

AutoName: LehrZeit

RepoType: git
Repo: https://github.com/<github-user>/LehrZeit.git

Builds:
  - versionName: 0.1.0
    versionCode: 1
    commit: v0.1.0
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 0.1.0
CurrentVersionCode: 1
```

By default, F-Droid signs the APK with its own key. Because the build is reproducible, F-Droid can instead publish the APK signed with your key, so that the F-Droid and GitHub downloads can update each other. For that, add these two lines to the recipe:

```yaml
Binaries: https://github.com/<github-user>/LehrZeit/releases/download/v%v/LehrZeit-%v.apk
AllowedAPKSigningKeys: <SHA-256 of the signing certificate>
```

F-Droid then builds the app, compares its result with the APK from the GitHub release, and only publishes yours if both match. The certificate's SHA-256 is shown by `apksigner verify --print-certs LehrZeit-0.1.0.apk`; use it without colons, in lower case.

## Checking reproducibility again

After changing the build setup (Gradle, AGP, Kotlin or new libraries), build the same commit twice from fresh clones in different folders, ideally once on Linux, and compare the unsigned APKs:

```sh
sha256sum app-release-unsigned.apk
```

If they differ, `diffoscope` shows where.
