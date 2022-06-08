# Releasing the CloudWatch exporter

Only maintainers can do this.
The process is based on the [`java_client` release process](https://github.com/prometheus/client_java/wiki/Development).

## Requirements

* JDK 17
* Maven
* GPG

## Access to the OSS Sonatype repository

Sign up through [Sonatype JIRA](https://issues.sonatype.org) if you don't have an account already.
[File a Publishing Support ticket](https://central.sonatype.org/faq/get-support/#producers) ([example](https://issues.sonatype.org/browse/OSSRH-70163)) to gain access to the `io.prometheus` group in the Sonatype OSSRH.
The same login will be used for the repository.

Verify that you can log into [OSSRH](https://oss.sonatype.org/).
The CloudWatch Exporter is at [io.prometheus.cloudwatch](https://oss.sonatype.org/#nexus-search;quick~io.prometheus.cloudwatch).

Set up [Maven publishing](https://central.sonatype.org/publish/publish-maven/), specifically the `<server>` block in `~/.m2/settings.xml`.
A minimal config:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
  https://maven.apache.org/xsd/settings-1.0.0.xsd">

  <servers>
    <server>
      <id>ossrh</id>
      <username>jira-user</username>
      <password>jira-password</password>
    </server>
  </servers>
</settings>
```

The project setup is already done.

## Push a snapshot

To push a snapshot, check out the latest main branch and run

```sh
mvn clean deploy
```

This should succeed.

## Start a release

To prepare a release:

```sh
mvn release:clean release:prepare
```

This will

1. prompt for the version
2. update `pom.xml` with this version
3. create and push a git tag
4. build everything
5. GPG-sign the artifacts

To actually release:

```sh
mvn release:perform
```

This will upload everything to OSSRH into a **staging repository**.
To locate it, [log into Sonatype OSS](https://oss.sonatype.org/), then open [Staging Repositories](https://oss.sonatype.org/#stagingRepositories).
If it spins forever, open the [main page](https://oss.sonatype.org/) and log in first.

Press "Close" to promote the release.

The staging repository will show "Activity: Operation in progress" for a few seconds.
Refresh or check the Activity tab to see what's going on.

Once closing is done, the "Release" button unlocks.
Press it.

This runs for a while, and the new version should become available on [OSSRH](https://oss.sonatype.org/#nexus-search;quick~io.prometheus.cloudwatch).
It usually appears immediately after the release process is done, but can take a few hours to show up.

## Docker images

As part of the release process, `mvn` will create the git tag.
This tag is picked up by [CircleCI](https://app.circleci.com/pipelines/github/prometheus/cloudwatch_exporter?branch=master), which builds and pushes the [Docker images](README.md#docker-images).

## GitHub Release

Create a [new GitHub release](https://github.com/prometheus/cloudwatch_exporter/releases/new).
Select the tag for this version that Maven pushed.

Use the format `A.B.C / YYYY-MM-DD` as the release title.
Summarize the changes.

The release files and signatures are available in `target/checkout/target/`.
Upload the `.jar` and `.jar.asc` files to the GitHub release.

Publish the release.

## Announcement

Announce the changes to [prometheus-announce](mailto:prometheus-announce@groups.google.com), linking to OSSRH and the GitHub release.
