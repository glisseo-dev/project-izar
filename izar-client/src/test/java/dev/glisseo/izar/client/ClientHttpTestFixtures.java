package dev.glisseo.izar.client;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.operation.DecodingPolicy;
import dev.glisseo.izar.operation.GraphQlOperation;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class ClientHttpTestFixtures {

    private ClientHttpTestFixtures() {}

    static GraphQlOperation<String> thingOperation() {
        return new GraphQlOperation<>() {
            @Override
            public String operationName() {
                return "GetThing";
            }

            @Override
            public String document() {
                return "query GetThing { thing }";
            }

            @Override
            public String operationId() {
                return ManifestOperation.idFor(document());
            }

            @Override
            public Map<String, Object> variables() {
                return Map.of();
            }

            @SuppressWarnings("unchecked")
            @Override
            public String decode(@Nullable Object data, DecodingPolicy policy) {
                return (String) ((Map<String, Object>) data).get("thing");
            }
        };
    }
}
