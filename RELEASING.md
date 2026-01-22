# Releasing

## Overview

Bitty City uses automated publishing to Maven Central via GitHub Actions. Each module is versioned and released independently. The release process is triggered by creating a tag with the module name prefix (e.g., `outie-v0.10.4`) on the main branch.

## Module Versioning

Each module maintains its own version in its `gradle.properties` file:

| Module | Version File |
|--------|--------------|
| common | `common/gradle.properties` |
| innie | `innie/gradle.properties` |
| outie | `outie/gradle.properties` |

## Prerequisites

Before releasing, ensure you have:
- Write access to the repository
- Access to the required GitHub secrets:
  - `SONATYPE_CENTRAL_USERNAME`
  - `SONATYPE_CENTRAL_PASSWORD`
  - `GPG_SECRET_KEY`
  - `GPG_SECRET_PASSPHRASE`

## Release Steps

### 1. Prepare the Release

1. Set the module and release version:

    ```sh
    export MODULE=outie
    export RELEASE_VERSION=A.B.C
    ```

2. Create a release branch:

    ```sh
    git checkout -b release/$MODULE-$RELEASE_VERSION
    ```

3. Update `CHANGELOG.md` with changes since the last release. Follow the existing `CHANGELOG.md` format, which is derived from [this guide](https://keepachangelog.com/en/1.0.0/)

4. Update the version in the module's `gradle.properties`:

    ```sh
    sed -i "" \
      "s/VERSION_NAME=.*/VERSION_NAME=$RELEASE_VERSION/g" \
      $MODULE/gradle.properties
    ```

5. Commit and push the release branch:

    ```sh
    git add .
    git commit -m "Prepare for release $MODULE $RELEASE_VERSION"
    git push origin release/$MODULE-$RELEASE_VERSION
    ```

6. Create a pull request to merge the release branch into main:

    ```sh
    gh pr create --title "Release $MODULE $RELEASE_VERSION" --body "Release $MODULE version $RELEASE_VERSION"
    ```

7. Review and merge the pull request to main

### 2. Create and Push the Release Tag

Once the release PR is merged to main:

1. Pull the latest changes from main:

    ```sh
    git checkout main
    git pull origin main
    ```

2. Create a tag with the module prefix:

    ```sh
    git tag -a $MODULE-v$RELEASE_VERSION -m "Release $MODULE version $RELEASE_VERSION"
    git push origin $MODULE-v$RELEASE_VERSION
    ```

### 3. Automated Publishing

Once the tag is pushed, the [Publish to Maven Central](.github/workflows/publish.yml) workflow will automatically:

1. Parse the module name and version from the tag
2. Build and sign the module's artifacts with GPG
3. Publish to Maven Central via Sonatype

**Note**: It can take 10-30 minutes for artifacts to appear on Maven Central after successful publishing.

### 4. Create GitHub Release

1. Go to [GitHub Releases](https://github.com/block/bitty-city/releases/new)
2. Select the tag you just created (`$MODULE-v$RELEASE_VERSION`)
3. Copy the release notes from `CHANGELOG.md` into the release description
4. Publish the release

## Dependency Ordering

When releasing modules with dependencies, publish in order:

1. `common` (no dependencies)
2. `innie` and `outie` (depend on `common`)

If you've made changes to `common`, release it first before releasing dependent modules.

## Troubleshooting

### Publishing Failures

- If the GitHub Action fails, check the workflow logs for specific error messages
- Common issues include:
  - Invalid GPG key or passphrase
  - Incorrect Sonatype credentials
  - Version conflicts (if the version was already published)
  - Network connectivity issues

### Manual Intervention

If the automated publishing fails and you need to manually intervene:

1. Check the [Sonatype Nexus](https://oss.sonatype.org/) staging repository
2. Drop any failed artifacts from the staging repository
3. Fix the issue and re-tag the release (delete the old tag first)
4. Re-run the workflow

### Access Issues

If you don't have access to the required secrets or Sonatype account, contact the project maintainers.

## Release Artifacts

Each module release includes:
- Main JAR with compiled classes
- Sources JAR
- Javadoc JAR
- POM file

All artifacts are signed with GPG and published to Maven Central under `xyz.block.bittycity`.
