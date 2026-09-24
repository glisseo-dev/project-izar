# Izar Maven plugin

The Maven lifecycle adapter over [`izar-compiler`](../izar-compiler/) and
[`izar-manifest`](../izar-manifest/). It binds their parameters to Maven properties,
registers the generated-sources directory as a compile source root, and resolves
Maven-specific credentials for publication. It holds no generation or publication
logic of its own, so another build tool could wrap the same two libraries without
going through Maven. This is the module a Maven-based consumer actually adds to a
build; [`izar-compiler`](../izar-compiler/) and [`izar-operation`](../izar-operation/)
are dependencies of the generated code, not something a consumer's `pom.xml`
references directly.

## Add the plugin

```xml
<plugin>
  <groupId>dev.glisseo.izar</groupId>
  <artifactId>izar-maven-plugin</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <configuration>
    <basePackage>com.example.graphql</basePackage>
  </configuration>
  <executions>
    <execution>
      <goals>
        <goal>generate</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

The `generate` goal's default phase is `generate-sources`, so declaring the
execution above is enough; no explicit `<phase>` is needed. `basePackage` is the
only required parameter with no default.

Every other `generate` parameter has a default. Here is the same plugin with
all of them spelled out explicitly, at their default values:

```xml
<plugin>
  <groupId>dev.glisseo.izar</groupId>
  <artifactId>izar-maven-plugin</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <configuration>
    <basePackage>com.example.graphql</basePackage>
    <schema>${project.basedir}/src/main/graphql/schema.graphqls</schema>
    <operationDirectory>${project.basedir}/src/main/graphql</operationDirectory>
    <outputDirectory>${project.build.directory}/generated-sources/izar</outputDirectory>
    <manifestFile>${project.build.directory}/izar/manifest.json</manifestFile>
    <skip>false</skip>
  </configuration>
  <executions>
    <execution>
      <goals>
        <goal>generate</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

`scalarMappings` is left out of the full example above: it has no default
(an empty list, meaning no custom scalars), so there is no default entry to
show. See [Generation](#generation) below for the shape of an entry.

## Generation

By default, `generate` reads:

- A schema file at `src/main/graphql/schema.graphqls` (`izar.schema`). This must be
  a checked-in file or an unpacked pinned schema artifact; the goal never
  introspects a running server.
- `.graphql` operation files anywhere under `src/main/graphql`, scanned
  recursively (`izar.operations`).

Set `izar.manifestInput` to generate from an existing Apollo-compatible manifest
instead. The manifest replaces `operationDirectory` as the operation source. The
schema remains required so the compiler can validate operations and create Java
types. Izar preserves each operation's ID and exact document body. This mode does
not write `izar.manifestFile`.

```xml
<configuration>
  <basePackage>com.example.graphql</basePackage>
  <schema>${project.basedir}/src/main/graphql/schema.graphqls</schema>
  <manifestInput>${project.basedir}/src/main/resources/manifest.json</manifestInput>
</configuration>
```

Manifest input accepts any nonblank operation ID. Izar does not require the ID
to be a SHA-256 hash. Each entry must contain exactly one named operation, and
its `name` and `type` must match the operation in `body`. For interface or union
selections that need a runtime type discriminator, add an unconditional,
unaliased `__typename` to the shared selection or to every concrete branch.
Generation fails with an operation-specific diagnostic instead of changing the
document.

Both modes write generated Java sources to `target/generated-sources/izar`
(`izar.outputDirectory`). The goal registers that directory as a compile source
root before generation runs, so a generation failure still leaves the IDE's
source roots configured.
Operation-file mode also writes an Apollo-compatible operation manifest to
`target/izar/manifest.json` (`izar.manifestFile`); the `publish` goal below reads
that same default path. Manifest-input mode leaves `izar.manifestFile` untouched.

`izar.skip=true` skips the goal entirely, leaving generated files and manifests untouched.

### Generate for multiple GraphQL servers

Declare one `generate` execution per server and put that server's settings in
the execution's own `<configuration>`. Give each execution a distinct Java
package, generated-sources directory, and manifest path. The `attach` goal can
then select the manifest it should add to the Maven project during `package`.
Omit an `attach` execution for a manifest that should stay external.

```xml
<plugin>
  <groupId>dev.glisseo.izar</groupId>
  <artifactId>izar-maven-plugin</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <executions>
    <execution>
      <id>generate-server-a</id>
      <goals><goal>generate</goal></goals>
      <configuration>
        <schema>${project.basedir}/src/main/graphql/server-a/schema.graphqls</schema>
        <operationDirectory>${project.basedir}/src/main/graphql/server-a</operationDirectory>
        <basePackage>com.example.graphql.servera</basePackage>
        <outputDirectory>${project.build.directory}/generated-sources/server-a</outputDirectory>
        <manifestFile>${project.build.directory}/izar/server-a/manifest.json</manifestFile>
      </configuration>
    </execution>
    <execution>
      <id>generate-server-b</id>
      <goals><goal>generate</goal></goals>
      <configuration>
        <schema>${project.basedir}/src/main/graphql/server-b/schema.graphqls</schema>
        <operationDirectory>${project.basedir}/src/main/graphql/server-b</operationDirectory>
        <basePackage>com.example.graphql.serverb</basePackage>
        <outputDirectory>${project.build.directory}/generated-sources/server-b</outputDirectory>
        <manifestFile>${project.build.directory}/izar/server-b/manifest.json</manifestFile>
      </configuration>
    </execution>
    <execution>
      <id>attach-internal-server</id>
      <phase>package</phase>
      <goals><goal>attach</goal></goals>
      <configuration>
        <manifestFile>${project.build.directory}/izar/server-b/manifest.json</manifestFile>
        <classifier>izar-server-b-manifest</classifier>
      </configuration>
    </execution>
  </executions>
</plugin>
```

Each execution receives its own Mojo configuration, including nested values
such as `scalarMappings`. The defaults still apply to settings omitted from an
execution. Maven user properties such as `-Dizar.schema=...` have one value for
the whole Maven invocation, so use execution-level XML when schemas differ.
If several manifests are attached to one project, assign each a distinct
classifier. Omit the `attach` execution for any manifest that should remain
external.

A GraphQL built-in scalar needs no configuration. A custom scalar needs an
explicit Java type and codec, declared through `<scalarMappings>`:

```xml
<configuration>
  <basePackage>com.example.graphql</basePackage>
  <scalarMappings>
    <scalarMapping>
      <graphqlScalarName>DateTime</graphqlScalarName>
      <javaTypeName>java.time.Instant</javaTypeName>
      <codecClassName>dev.glisseo.izar.scalars.InstantScalarCodec</codecClassName>
    </scalarMapping>
  </scalarMappings>
</configuration>
```

`javaTypeName` must be fully qualified; generated code adds no import for it.
`codecClassName` names any class implementing
`dev.glisseo.izar.operation.ScalarCodec<T>` with a public no-argument
constructor, whether from the optional [`izar-scalars`](../izar-scalars/) library
or written by the consuming application. A custom scalar reached by an
operation's selections or variables with no configured mapping fails generation,
naming the scalar and what to configure.

## Publishing

The `publish` goal sends the manifest to a controller's release endpoint:

```sh
./mvnw dev.glisseo.izar:izar-maven-plugin:publish \
    -Dizar.publish.url=http://localhost:8080 \
    -Dizar.publish.clientName=catalog \
    -Dizar.publish.manifestVersion=2026.09
```

It is not bound to any lifecycle phase; publication is a deliberate, on-demand
step, never a side effect of `verify` or `install`. `izar.publish.url`,
`izar.publish.clientName`, and `izar.publish.manifestVersion` are required. By
default it reads the manifest at `target/izar/manifest.json`, the same path
`generate` writes; point `izar.publish.manifestFile` at a different path to
publish a manifest an existing Apollo-compatible artifact-collection pipeline
assembled instead. This goal only reads and validates that file; it never
regenerates one. `izar.publish.skip=true` skips it.

Credentials never appear in `pom.xml`, on the command line, or in build output:
there is no password parameter at all. Configure one of:

- The `IZAR_PUBLISH_USERNAME` and `IZAR_PUBLISH_PASSWORD` environment variables
  (both, or neither).
- A `<server>` entry in `settings.xml`, referenced by `izar.publish.serverId`,
  decrypted through the usual Maven settings mechanism.

The environment variables take precedence when both are configured.

A successful run logs the registered revision and operation count, and states
plainly that this confirms registration only, not that any server has activated
the revision yet. See [Publish and inspect manifests and schemas](../docs/controller-publication.md)
for the controller's HTTP API, the inventory and server-status endpoints, and
how enforcement mode relates to a published revision.

## Deploying and resolving through Maven repositories

`publish` and `deploy`/`assemble` are two independent exchanges. `publish`
registers a release with a controller's authenticated HTTP endpoint.
`deploy` and `assemble` instead move manifests as ordinary versioned Maven
artifacts through repositories the organization already runs, per issue 36.
A project can use either, both, or neither.

### Attach a project-bound manifest

Use the `attach` goal when the manifest belongs to the Maven project that
generated it:

```xml
<execution>
  <id>attach-izar-manifest</id>
  <phase>package</phase>
  <goals>
    <goal>attach</goal>
  </goals>
</execution>
```

The goal reads and validates `target/izar/manifest.json` by default, then
attaches it to the current project. `izar.attach.classifier` defaults to
`izar-manifest`, and `izar.attach.extension` defaults to `json`. Both can be
set in Maven XML or with user properties. `izar.attach.manifestFile` selects a
manifest assembled by another Apollo-compatible pipeline, and
`izar.attach.skip=true` skips the attachment.

The attached artifact uses the owning project's group, artifact, and version.
For example, a project with coordinates `com.example:catalog-client:1.2.3`
gets `com.example:catalog-client:json:izar-manifest:1.2.3`. A normal lifecycle
run publishes that attachment through Maven's standard goals:

```sh
./mvnw package
./mvnw install
```

`package` attaches the file without contacting a repository. `install` writes
it to the local Maven repository, and `deploy` publishes it through the
project's normal Maven deployment configuration.

A client build deploys its manifest with `deploy`:

```sh
./mvnw dev.glisseo.izar:izar-maven-plugin:deploy \
    -Dizar.deploy.groupId=com.example.manifests \
    -Dizar.deploy.artifactId=catalog \
    -Dizar.deploy.version=2026.09 \
    -Dizar.deploy.repositoryId=releases \
    -Dizar.deploy.repositoryUrl=https://repo.example.internal/releases
```

`groupId`, `artifactId`, and `version` are this manifest's own Maven
coordinates, independent of the client project's own coordinates: they are
usually the client release identity (client name and manifest version) a
deployment build later names to resolve this exact artifact back.
`repositoryId` and `repositoryUrl` name the target repository; `repositoryId`
is also what Maven matches against a `settings.xml` `<server>` entry for
credentials, exactly like `deploy:deploy-file`. There is no credential
parameter on this goal at all: authentication, mirrors, and proxies all come
from the ambient Maven session, the same infrastructure `mvn deploy` uses.
`izar.deploy.classifier` (default `izar-manifest`) and `izar.deploy.extension`
(default `json`) rarely need overriding. By default it deploys the manifest
at `target/izar/manifest.json`, the same path `generate` writes.

Invoke `izar:deploy` explicitly when the manifest needs independent Maven
coordinates. Direct goal invocation runs only that goal. It does not run
`generate-sources` or any other earlier lifecycle phase, so generate the
manifest first or point `izar.deploy.manifestFile` at an existing file.

A separate deployment build resolves exact releases and assembles them with
`assemble`:

```xml
<plugin>
  <groupId>dev.glisseo.izar</groupId>
  <artifactId>izar-maven-plugin</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <configuration>
    <graph>storefront</graph>
    <environment>production</environment>
    <releases>
      <release>
        <clientName>catalog</clientName>
        <manifestVersion>2026.09</manifestVersion>
        <groupId>com.example.manifests</groupId>
        <artifactId>catalog</artifactId>
        <version>2026.09</version>
      </release>
      <release>
        <clientName>checkout</clientName>
        <manifestVersion>2026.10</manifestVersion>
        <groupId>com.example.manifests</groupId>
        <artifactId>checkout</artifactId>
        <version>2026.10</version>
      </release>
    </releases>
  </configuration>
</plugin>
```

Run with `mvn izar:assemble`. Each `<release>` pairs a client release identity
(`clientName`, `manifestVersion`) with the Maven coordinates that supply it;
`classifier` and `extension` default the same way `deploy` does. Resolution
goes through this build's own configured repositories and mirrors — the
project's `<repositories>` or an inherited `settings.xml` mirror, not a
plugin-specific repository list — so a deployment build's ordinary dependency
configuration already governs where releases can come from. Every configured
release is resolved even after one fails, so a broken configuration reports
every missing or unresolvable coordinate in one run. Once every release
resolves, `assemble` hands the result to the same `ReleaseAssembler`
`izar-cli`'s `AssembleCommand` uses for a purely local selection (see
[`izar-manifest`](../izar-manifest/)), writing `manifest.json`,
`provenance.json`, and `lock.json` under `izar.assemble.outputDirectory`
(default `target/izar/assembled`). Resolution failures, missing releases, and
conflicting releases all fail the build with no partial output, the same
guarantee local assembly already makes.

Like `deploy`, `assemble` declares no credential parameter: it resolves
through `${project.remoteProjectRepositories}` by default, which already
carries this build's `settings.xml` authentication, mirrors, and proxies.

## What this module does not do

- No generation logic: schema parsing, code generation, and manifest assembly
  live in [`izar-compiler`](../izar-compiler/), reusable outside Maven.
- No publication logic: the HTTP call to the controller lives in
  [`izar-manifest`](../izar-manifest/)'s `ManifestPublisher`.
- No union or provenance logic: `assemble` binds Maven configuration onto
  [`izar-manifest`](../izar-manifest/)'s `ReleaseAssembler`, the same seam the
  purely local `AssembleCommand` in `izar-cli` uses.
- No credential storage or acceptance via parameter, for either `publish` or
  `deploy`/`assemble`; only Maven's own environment variable, `settings.xml`,
  and ambient-session mechanisms ever supply one.
- No schema introspection.
- No manifest activation on any server; `publish` only registers a revision with
  the controller, and `assemble` only writes local files a deployment installs
  itself.
- Static manifest distribution, a `FileManifestSource` or a plain static host
  serving `manifest.json`, needs neither this plugin's `publish` goal, `deploy`
  and `assemble`, nor [`izar-controller`](../izar-controller/) at all. `generate`
  alone is enough to produce a manifest file for that path.

See the [root README](../README.md) for how this module fits into the rest of
Izar.
