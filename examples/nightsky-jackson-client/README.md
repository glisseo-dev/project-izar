# Nightsky Jackson client

A small console client that asks the Nightsky server what is visible from
Amsterdam. Izar generates Jackson-annotated response records in `JACKSON`
mode, and Spring GraphQL maps the polymorphic `CelestialObject` response with
`toEntityList`.

Install Izar at the repository root and start the [Nightsky server](../nightsky-default-sb-server):

```bash
./mvnw install
./mvnw -f examples/nightsky-default-sb-server/pom.xml spring-boot:run
```

In another terminal, run this client:

```bash
./mvnw -f examples/nightsky-jackson-client/pom.xml spring-boot:run
```

The generated operation adds `__typename` to the polymorphic selection. Spring
GraphQL uses that value and the generated Jackson subtype annotations to create
the matching `Star`, `Nebula`, or `Galaxy` record. Output enums use `String` in
this mode, and this query does not select any custom scalar fields.

Unlike earlier versions of this example, the process then keeps running
instead of exiting, serving the REST endpoint below on
`http://localhost:8084`. Stop it with Ctrl-C.

## REST endpoint

[`VisibleSkyController`](src/main/java/dev/glisseo/izar/examples/nightsky/jackson/VisibleSkyController.java)
exposes the same `toEntityList`-decoded result as JSON:

| Endpoint | Description |
| --- | --- |
| `GET /visible-now?locationId=` | What's currently visible from a viewing location. |
