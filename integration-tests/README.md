# Integration tests

Cross-module consumer projects live here, proving Izar's generated behavior
across real process and HTTP boundaries. They are deliberately outside the
reactor in [`../pom.xml`](../pom.xml): a consumer project must resolve Izar
the way a real application does, from installed artifacts, so a test here
proves something about what a consuming application actually gets. This is
the acceptance suite for Izar's behavior, not a curated showcase; see
[`examples/`](../examples) for that.

Today the only project here is [`one-query-consumer`](one-query-consumer),
which covers query and mutation execution, custom scalar decoding, variable
encoding for generated input builders, and application-controlled retry on
top of Izar's own single-attempt execution. Its [README](one-query-consumer/README.md)
describes each test in detail.
