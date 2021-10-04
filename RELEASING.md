# Releasing the CloudWatch exporter

Only maintainers can do this.

## Requirements

* JDK 11
* Maven
* GPG

## Access to the OSS Sonatype repository

Sign up through [Sonatype JIRA](https://issues.sonatype.org) if you don't have an account already.
[File a Publishing Support ticket](https://central.sonatype.org/faq/get-support/#producers) ([example](https://issues.sonatype.org/browse/OSSRH-70163)) to gain access to the `io.prometheus` group in the Sonatype OSSRH.
The same login will be used for the repository.

Verify that you can log into [OSSRH](https://https://oss.sonatype.org/).
The CloudWatch Exporter is at [io.prometheus.cloudwatch](https://oss.sonatype.org/#nexus-search;quick~io.prometheus.cloudwatch).

Set up [Maven publishing](https://central.sonatype.org/publish/publish-maven/), specifically the `<server>` block in `~/.m2/settings.xml`.
The project setup is already done.

## Push a snapshot

To push a snapshot, check out the latest main branch and run

    mvn clean deploy

This should succeed.

## Start a release

To prepare a release:

    mvn release:clean release:prepare

This will

1. prompt for the version
2. update `pom.xml` with this version
3. create and push a git tag
4. build everything
5. GPG-sign the artifacts

To actually release:

    mvn release:perform

This will upload everything to OSSRH into a **staging repository**.
[Locate it](https://central.sonatype.org/publish/release/#locate-and-examine-your-staging-repository).
Press "Close" to promote it.

The staging repository will show "Activity: Operation in progress" for a few seconds.
Refresh or check the Activity tab to see what's going on.

Once closing is done, the "Release" button unlocks.
Press it.

This runs for a while, and the new version should become available on [OSSRH](https://oss.sonatype.org/#nexus-search;quick~io.prometheus.cloudwatch).
It can take a few hours to show up.
