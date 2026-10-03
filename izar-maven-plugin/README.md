# Izar Maven plugin

The Izar Maven plugin generates Java code from your GraphQL schema and
`.graphql` operation files during the `generate-sources` phase. Each operation
becomes an immutable Java record with builders for its variables, ready to run
with [`izar-client`](../izar-client/) on a Spring for GraphQL `GraphQlClient`.
The plugin also writes an Apollo-compatible operation manifest and can publish
it to an Izar controller or to a Maven repository.

For Gradle builds, use [`izar-gradle-plugin`](../izar-gradle-plugin/), which
has the same options and produces the same output.

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

The `generate` goal runs in `generate-sources` by default, so the execution
needs no `<phase>`. Your project also needs
[`izar-client`](../izar-client/), which brings in the
[`izar-operation`](../izar-operation/) contract that generated code
implements.

## Generate Java from operation files

Put your schema at `src/main/graphql/schema.graphqls` and your `.graphql`
operation files anywhere under `src/main/graphql`. Each file holds one named
operation: a query, a mutation, or a subscription. The schema must be a file in
your project or an unpacked schema artifact. The plugin does not introspect a
running server.

`generate` writes Java sources to `target/generated-sources/izar` and registers
that directory as a compile source root before it generates anything, so your
IDE keeps the source root even when generation fails. It also writes the
manifest to `target/izar/manifest.json`.

| Parameter | Property | Default |
| --- | --- | --- |
| `basePackage` (required) | `izar.basePackage` | none |
| `schema` | `izar.schema` | `${project.basedir}/src/main/graphql/schema.graphqls` |
| `operationDirectory` | `izar.operations` | `${project.basedir}/src/main/graphql` |
| `outputDirectory` | `izar.outputDirectory` | `${project.build.directory}/generated-sources/izar` |
| `manifestFile` | `izar.manifestFile` | `${project.build.directory}/izar/manifest.json` |
| `manifestInput` | `izar.manifestInput` | none |
| `generationMode` | `izar.generationMode` | `IZAR` |
| `scalarMappings` | none | empty |
| `skip` | `izar.skip` | `false` |

Set `izar.skip=true` to skip the goal and leave existing generated files and
manifests as they are.

### Generate from an existing manifest

To generate from an Apollo-compatible manifest instead of operation files, set
`manifestInput`. The manifest replaces `operationDirectory` as the source of
operations. The schema stays required, because the compiler validates each
operation against it and builds the Java types from it. Izar keeps each
operation's ID and exact document text, and in this mode it does not write
`manifestFile`.

```xml
<configuration>
  <basePackage>com.example.graphql</basePackage>
  <manifestInput>${project.basedir}/src/main/resources/manifest.json</manifestInput>
</configuration>
```

Each manifest entry needs one named operation whose `name` and `type` match
its `body`, and any nonblank ID. The ID doesn't have to be a SHA-256 hash.
Generation fails with a message that names the operation when an entry breaks
these rules. Izar never rewrites the document to fix it.

### Choose a generation mode

The default `IZAR` mode generates sealed output enums, an `Unrecognized`
fallback for values and types your schema didn't know at generation time, and
a response decoder that `izar-client` calls.

Set `generationMode` to `JACKSON` to map response paths with Spring GraphQL's
`toEntity` methods instead:

```xml
<configuration>
  <basePackage>com.example.graphql</basePackage>
  <generationMode>JACKSON</generationMode>
</configuration>
```

In `JACKSON` mode:

- Output enums are `String`.
- Interface and union models carry Jackson subtype annotations, and unknown
  GraphQL types map to a generated `Unrecognized` subtype.
- Operations implement `GraphQlRequest` and have no Izar response decoder.
- Each operation has one path constant per root response key. It uses the
  alias when the field has one, so `GetBookScalarsQuery.BOOK` is the path for
  the `book` field.

Models that need sanitized Java identifiers or polymorphic response types
require `jackson-annotations`, which Jackson 3 also uses. Configure your
`GraphQlClient` with a Jackson JSON decoder to use `toEntity`. Custom scalar
response types need serializers and deserializers in your Jackson
configuration. In both modes, `ScalarCodec` implementations encode custom
scalar input variables. In `IZAR` mode, they also decode custom scalar
responses.

For polymorphic fields, the compiler adds a shared discriminator field to the
operation document. It uses `__typename` unless that response key is taken.
In operation-file mode, this changes the document and the operation ID. An
imported manifest document can't be rewritten, so manifest-input generation
needs an unconditional, unaliased `__typename` in the shared selection (or in
every concrete branch) of each interface or union selection that needs a type
discriminator. `JACKSON` mode needs it for every polymorphic field. The
compiler reports an error when it is missing.

### Map custom scalars

GraphQL built-in scalars need no configuration. Map each custom scalar to a
Java type and a codec:

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

`javaTypeName` must be fully qualified, because generated code adds no import
for it. `codecClassName` is any class that implements
`dev.glisseo.izar.operation.ScalarCodec<T>` and has a public no-argument
constructor. Use one from [`izar-scalars`](../izar-scalars/) or write your own.
If an operation reaches a custom scalar that has no mapping, generation fails
and names the scalar.

### Generate for multiple GraphQL servers

Declare one `generate` execution per server, each with its own `<configuration>`.
Give each execution a distinct Java package, output directory, and manifest
file.

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
  </executions>
</plugin>
```

Each execution gets its own configuration, including nested values such as
`scalarMappings`, and settings it omits fall back to the defaults. A user
property such as `-Dizar.schema=...` has one value for the whole Maven run, so
use execution-level XML when the schemas differ.

## Publish a manifest to a controller

The `publish` goal sends the manifest to the release endpoint of an
Izar controller:

```sh
./mvnw dev.glisseo.izar:izar-maven-plugin:publish \
    -Dizar.publish.url=http://localhost:8080 \
    -Dizar.publish.clientName=catalog \
    -Dizar.publish.manifestVersion=2026.09
```

`izar.publish.url`, `izar.publish.clientName`, and
`izar.publish.manifestVersion` are required. The goal belongs to no lifecycle
phase, so `verify` and `install` never publish. It reads the manifest at
`target/izar/manifest.json`, where `generate` writes it. To publish a manifest
from another Apollo-compatible pipeline, set `izar.publish.manifestFile`. The
goal reads and validates that file and never regenerates it.
`izar.publish.skip=true` skips the goal.

The goal has no password parameter, so credentials never appear in `pom.xml`,
on the command line, or in build output. Supply them in one of two ways:

- Set the `IZAR_PUBLISH_USERNAME` and `IZAR_PUBLISH_PASSWORD` environment
  variables. Set both or neither.
- Add a `<server>` entry to `settings.xml` and name it with
  `izar.publish.serverId`. Maven decrypts it the usual way.

When both are present, the environment variables win.

A successful run logs the registered revision and operation count. Registration
does not mean a server has activated the revision.

## Share manifests through Maven repositories

`publish` talks to a controller over authenticated HTTP. The `attach`,
`deploy`, and `assemble` goals instead move manifests as versioned Maven
artifacts through the repositories your organization already runs. You can use
either route, both, or neither.

### Attach a manifest to the project

Bind `attach` to `package` when the manifest belongs to the project that
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

The goal validates `target/izar/manifest.json` and attaches it to the project.
The classifier defaults to `izar-manifest` and the extension to `json`
(`izar.attach.classifier` and `izar.attach.extension`).
`izar.attach.manifestFile` selects another manifest file, and
`izar.attach.skip=true` skips the goal.

The attachment uses the project's own group, artifact, and version. A project
`com.example:catalog-client:1.2.3` produces
`com.example:catalog-client:json:izar-manifest:1.2.3`. `package` creates the
attachment, `install` copies it to your local repository, and `deploy` uploads
it through your normal deployment configuration.

When several manifests attach to one project, give each a distinct classifier.
Leave out the `attach` execution for any manifest that should stay outside the
repository.

### Deploy a manifest under its own coordinates

Use `deploy` when a manifest needs Maven coordinates independent of the project:

```sh
./mvnw dev.glisseo.izar:izar-maven-plugin:deploy \
    -Dizar.deploy.groupId=com.example.manifests \
    -Dizar.deploy.artifactId=catalog \
    -Dizar.deploy.version=2026.09 \
    -Dizar.deploy.repositoryId=releases \
    -Dizar.deploy.repositoryUrl=https://repo.example.internal/releases
```

`groupId`, `artifactId`, and `version` identify this manifest. They usually
encode the client name and manifest version, so a deployment build can later
resolve the exact release. `repositoryId` and `repositoryUrl` name the target.
Maven matches `repositoryId` to a `settings.xml` `<server>` entry for
credentials, as `deploy:deploy-file` does, and the goal has no credential
parameter. `izar.deploy.classifier` (default `izar-manifest`) and
`izar.deploy.extension` (default `json`) rarely need changing. The goal deploys
`target/izar/manifest.json` unless you set `izar.deploy.manifestFile`.

A directly invoked goal runs alone, with no earlier lifecycle phase. Generate
the manifest first, or point `izar.deploy.manifestFile` at an existing file.

### Assemble one manifest from several client releases

A deployment build can resolve exact client releases and merge them into one
union manifest with `assemble`:

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

Run it with `mvn izar:assemble`. Each `<release>` pairs a client release
identity (`clientName`, `manifestVersion`) with the Maven coordinates that
supply its manifest. `classifier` and `extension` default as they do for
`deploy`. The goal resolves releases through the build's own repositories and
mirrors, so the project's `<repositories>` and your `settings.xml` mirrors,
authentication, and proxies already apply.

The goal tries every release even after one fails, so one run reports every
missing coordinate. When all releases resolve, it writes `manifest.json`,
`provenance.json`, and `lock.json` to `izar.assemble.outputDirectory` (default
`target/izar/assembled`). Any resolution failure, missing release, or
conflicting release fails the build with no partial output.

## Distribute a manifest without a controller

If a server loads its manifest from a file or a static host, `generate` alone
produces everything you need. `publish`, `deploy`, `assemble`, and the
controller are all optional.

See the [root README](../README.md) for the other Izar modules.
