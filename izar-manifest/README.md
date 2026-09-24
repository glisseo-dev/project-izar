# Izar manifest

Izar manifest reads, writes, merges, and validates Apollo-compatible persisted-query manifests, and defines the `ManifestSource` seam that supplies a versioned snapshot of one independently of transport. The compiler produces manifests, the client derives the same operation IDs a manifest carries, the server activates manifests it reads through a `ManifestSource`, and the controller stores and serves them. Each of those modules already depends on this one, so the manifest format and the ID derivation live here once instead of drifting across four independent implementations.

## Add the dependency

```xml
<dependency>
    <groupId>dev.glisseo.izar</groupId>
    <artifactId>izar-manifest</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Most adopters never add this by hand. It arrives transitively through [izar-client](../izar-client/), [izar-server](../izar-server/), [izar-compiler](../izar-compiler/), and [izar-maven-plugin](../izar-maven-plugin/). Add it directly when implementing a custom `ManifestSource` (an S3 bucket, an Artifactory repository) or a non-Maven build integration that needs `ManifestPublisher` without pulling in the compiler.

## Package organization

The root `dev.glisseo.izar.manifest` package contains the shared manifest contract and protocol
types: `OperationManifest`, `ManifestOperation`, `OperationHash`, `ManifestSnapshot`,
`PersistedQueryExtension`, and `EnforcementReport`. The capability packages own the code that
loads, publishes, assembles, analyzes, and packages those values:

- `dev.glisseo.izar.manifest.source` loads manifests from files and HTTP sources.
- `dev.glisseo.izar.manifest.publication` publishes manifests to a controller.
- `dev.glisseo.izar.manifest.assembly` selects releases and builds provenance and input locks.
- `dev.glisseo.izar.manifest.analysis` computes operation references, type/field/argument usage, schema impact, and candidate checks.
- `dev.glisseo.izar.manifest.bundle` writes, verifies, and installs transfer bundles.

Callers should depend on the types exposed by the owning package. The package tree is part of the
module's interface: concrete source adapters and atomic file writers live under owning `internal`
packages and are reached through public capability contracts or factories.

## The manifest format

`OperationManifest` is an Apollo-compatible persisted-query manifest: a `format` string (always `"apollo-persisted-query-manifest"`), a `version` integer (always `1`), and an ordered list of `ManifestOperation` entries. `toJson`/`fromJson` render and parse that exact shape, so a non-Izar Apollo tool reads a manifest this module writes and vice versa.

Each `ManifestOperation` carries an `id`, the `body` it was derived from, a `name`, and a `type` (`"query"`, `"mutation"`, or `"subscription"`). `id` is always the lowercase hex SHA-256 digest of `body`, computed by `OperationHash` from the exact final executable document a client sends on the wire, after the compiler adds any required type discriminators, never from hashing the author's source file separately. `ManifestOperation`'s canonical constructor recomputes that digest and rejects any entry whose `id` disagrees with it, on every construction path including `fromJson`, so a manifest read from disk or over HTTP can never carry an ID that doesn't match its own document.

`OperationManifestInput.fromJson` is the compiler's import path for an existing
manifest. It accepts any nonblank operation ID and keeps the exact body string.
The regular `OperationManifest.fromJson` reader retains the hash check for the
other manifest workflows.

Combining manifests is append-only: an operation whose ID already exists with an identical entry deduplicates silently, but an operation whose ID collides with an entry carrying a different `body`, `name`, or `type` is rejected rather than overwriting what's already registered. [izar-controller](../izar-controller/) applies this rule when folding a newly published release into its current snapshot; this module supplies the format and the identity check (`ManifestOperation`'s equality and its constructor's hash validation) that make the rule meaningful, not a merge method of its own.

## ManifestSource

`ManifestSource` is a single-method interface: `load()` returns a `ManifestSnapshot`, a manifest paired with a revision identifying that particular load. A source only reads and parses; it never validates against a schema, decides whether to activate what it read, or remembers a previously loaded snapshot; a caller such as izar-server's activation logic owns those decisions because only it knows the target schema and the currently active registry.

Two implementations ship with this module through the `ManifestSources` factory:

- `ManifestSources.file(path)` reads a complete manifest from one file on disk. Its revision is the SHA-256 hex digest of the file's raw bytes, so any edit, including one that leaves the JSON semantically unchanged, produces a new revision. The returned `LocalManifestSource` also exposes the path for bundle creation.
- `ManifestSources.http(uri)` fetches a `ManifestSnapshot` JSON body over HTTP, typically from a controller's `GET /api/snapshots/latest`, or any static host serving the identical body a file source would read. Use `ManifestSources.http(client, uri)` when authenticated or otherwise customized reads need a caller-owned `HttpClient`.

A custom source, for a different object store or artifact repository, implements `ManifestSource` directly: load the bytes, parse them with `OperationManifest.fromJson`, and pair the result with a revision that changes whenever the underlying content does.

`load()` throws `ManifestSourceException` when the candidate can't be read at all (a missing file, a denied read, an unreachable host) and `InvalidManifestException` when what was read parses but isn't a trustworthy manifest (malformed JSON, or an operation whose ID doesn't match its document).

## ManifestPublisher

`ManifestPublisher` sends a manifest to a controller's `POST /api/releases` endpoint over HTTP Basic authentication, given a client name, a manifest version, and the manifest itself. It performs only the write half of publication: a `PublishedRelease` response confirms the controller registered the release, not that any running server has activated the revision it now belongs to. A server replica activates a new revision only on its own next `ManifestSource` refresh.

`ManifestPublisher` takes a `URI`, credentials, and an `OperationManifest` as plain constructor and method arguments, with no dependency on Maven, Gradle, or any other build tool. That's why [izar-maven-plugin](../izar-maven-plugin/)'s publish goal calls it directly instead of reimplementing the HTTP call, and why a Gradle task or a CI script can do the same.

For the full release envelope, the conflict and validation responses a controller returns, and the schema-upload and coverage endpoints, see [Publish and inspect manifests and schemas](../docs/controller-publication.md).

## What this module does not do

Izar manifest registers and reads manifests; it does not manage their lifecycle beyond that. In particular, it has no concept of retiring or expiring a previously registered operation, no key rotation for publisher credentials, and no approval workflow gating what a publish request registers. A controller that wants any of those builds them on top of the registration and snapshot primitives this module defines, since none of them are part of the Apollo-compatible manifest format itself.

## Related modules

- [Root README](../README.md) for the full module layout.
- [izar-operation](../izar-operation/) defines the operation contract a generated class implements; its `operationId()` matches the ID a manifest entry for the same document carries.
- [izar-compiler](../izar-compiler/) generates the manifest this module's types read, write, and validate.
- [izar-client](../izar-client/) derives persisted-ID request IDs the same way `ManifestOperation` derives a manifest entry's ID.
- [izar-server](../izar-server/) activates manifests loaded through a `ManifestSource` and enforces them against incoming requests.
- [izar-controller](../izar-controller/) is the `ManifestPublisher` target and the typical HTTP manifest source origin.
