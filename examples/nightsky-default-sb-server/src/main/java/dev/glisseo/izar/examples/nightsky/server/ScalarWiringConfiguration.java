package dev.glisseo.izar.examples.nightsky.server;

import graphql.scalars.ExtendedScalars;
import graphql.schema.GraphQLScalarType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * Registers this server's two custom scalars: GraphQL Java Extended Scalars' {@code DateTime},
 * and a hand-written {@code Coordinates} backed by {@link GeoCoordinatesCoercing}.
 */
@Configuration
class ScalarWiringConfiguration {

    private static final GraphQLScalarType COORDINATES = GraphQLScalarType.newScalar()
            .name("Coordinates")
            .description("A 'latitude,longitude' pair identifying a point on Earth's surface.")
            .coercing(new GeoCoordinatesCoercing())
            .build();

    @Bean
    RuntimeWiringConfigurer scalarWiring() {
        return wiringBuilder -> wiringBuilder.scalar(ExtendedScalars.DateTime).scalar(COORDINATES);
    }
}
