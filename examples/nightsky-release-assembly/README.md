# Nightsky release assembly

A Maven-only deployment build: no Java sources, no application to run. Its
job is to stand in for a release engineer's pipeline that resolves several
clients' published manifests and assembles them into one deployable,
supported release.

This project is deliberately outside the root Maven reactor (see
[`../../README.md`](../../README.md)): it resolves Izar and the two Nightsky
clients' manifests the way a real deployment build would, as installed
artifacts.

## What it does

`izar-maven-plugin`'s `assemble` goal, bound to `package`, resolves two
`<release>` entries by Maven coordinate:

- `nightsky-sync-client`'s manifest (the plugin's default `izar-manifest`
  classifier)
- `nightsky-reactive-client`'s manifest (the `nightsky-reactive-manifest`
  classifier)

and unions them into `target/izar/assembled/manifest.json`,
`provenance.json`, and `lock.json`. A second execution of the plugin's
`attach` goal, also bound to `package`, then attaches that assembled
`manifest.json` — the "uber-manifest" covering both clients' operations — as
this project's own deployable artifact, under the `nightsky-uber-manifest`
classifier:

```text
dev.glisseo.izar.examples:nightsky-release-assembly:json:nightsky-uber-manifest:0.1.0-SNAPSHOT
```

Nothing here talks to a controller or a real remote repository:
`assemble` resolves through whatever repositories this build's
`${project.remoteProjectRepositories}` already names (the local repository
by default, exactly like resolving any other Maven dependency), and `attach`
only registers a local file as a secondary artifact of this reactor build.
`mvn install`/`mvn deploy` are what would actually publish it, the same as
any other attached artifact — see
[`nightsky-sync-client`](../nightsky-sync-client)'s README for why `package`
alone never copies the classified file into `target/`.

## Running it

Install the Izar modules and the two clients this project resolves manifests
from, first:

```bash
./mvnw install
./mvnw -f examples/nightsky-sync-client/pom.xml install
./mvnw -f examples/nightsky-reactive-client/pom.xml install
```

```powershell
.\mvnw.cmd install
.\mvnw.cmd -f examples\nightsky-sync-client\pom.xml install
.\mvnw.cmd -f examples\nightsky-reactive-client\pom.xml install
```

Then assemble and attach the union manifest:

```bash
./mvnw -f examples/nightsky-release-assembly/pom.xml package
```

```powershell
.\mvnw.cmd -f examples\nightsky-release-assembly\pom.xml package
```

Inspect the assembled files directly:

```bash
cat examples/nightsky-release-assembly/target/izar/assembled/manifest.json
cat examples/nightsky-release-assembly/target/izar/assembled/provenance.json
cat examples/nightsky-release-assembly/target/izar/assembled/lock.json
```

`manifest.json` contains both clients' operations, deduplicated by operation
ID. `provenance.json` names, per operation, which client release(s)
contributed it. `lock.json` pins the exact resolved version and content
digest behind each release.

Install it to see the deployable artifact land in the local Maven
repository, the same as any other attached artifact:

```bash
./mvnw -f examples/nightsky-release-assembly/pom.xml install
```

The installed file is
`~/.m2/repository/dev/glisseo/izar/examples/nightsky-release-assembly/0.1.0-SNAPSHOT/nightsky-release-assembly-0.1.0-SNAPSHOT-nightsky-uber-manifest.json`.

## Assembling several versions of one client

Nothing about `assemble` requires each `<release>` to name a distinct
client. Two `<release>` entries with the same `clientName` but different
`manifestVersion` values (and Maven coordinates pointing at two different
published versions of that same client) are unioned exactly the same way —
the ordinary case for a rolling deployment carrying both an old and a new
client version at once. See
[`ClientRelease`](../../izar-manifest/src/main/java/dev/glisseo/izar/manifest/assembly/ClientRelease.java)
in `izar-manifest`.
