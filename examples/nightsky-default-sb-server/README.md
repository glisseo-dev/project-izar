# Nightsky plain Spring Boot server

This is a standalone Spring Boot GraphQL server for the Nightsky schema. It depends only on Spring Boot and GraphQL Java Extended Scalars. It does not depend on `izar-server` or `izar-controller`.

The server loads [`src/main/resources/izar/manifest.json`](src/main/resources/izar/manifest.json) from its classpath at startup and installs a graphql-java `PreparsedDocumentProvider`. A hash-only request using the Apollo `persistedQuery` extension resolves to the matching document in that manifest. The provider checks each manifest ID against the SHA-256 digest of its document before serving requests. Full-document GraphQL requests follow Spring GraphQL's normal execution path.

## Run

```bash
./mvnw -f examples/nightsky-default-sb-server/pom.xml spring-boot:run
```

On Windows:

```powershell
.\mvnw.cmd -f examples\nightsky-default-sb-server\pom.xml spring-boot:run
```

The GraphQL endpoint is `http://localhost:8083/graphql`; GraphiQL is available at `http://localhost:8083/graphiql`.

A hash-only request has an empty `query` and supplies the SHA-256 ID in `extensions`:

```json
{
  "extensions": {
    "persistedQuery": {
      "version": 1,
      "sha256Hash": "<id from the manifest file>"
    }
  }
}
```

Unknown IDs return a GraphQL `NOT_FOUND` error. This provider resolves persisted documents. It does not reject full-document requests or enforce an allowlist.
