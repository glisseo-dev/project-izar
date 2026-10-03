# Integration tests

Consumer projects that check Izar's generated code and client against real
servers over HTTP. They sit outside the root reactor in
[`../pom.xml`](../pom.xml) and resolve Izar from installed artifacts, the way
an application does. Run `./mvnw install` at the repository root first. These
projects are the acceptance suite. [`examples/`](../examples) holds the
demonstration projects.

[`bookstore-consumer`](bookstore-consumer) covers query, mutation, and
subscription execution, fragments and polymorphic decoding, custom scalars,
variable encoding for generated inputs, and persisted-ID requests. Its
[README](bookstore-consumer/README.md) describes each test.
