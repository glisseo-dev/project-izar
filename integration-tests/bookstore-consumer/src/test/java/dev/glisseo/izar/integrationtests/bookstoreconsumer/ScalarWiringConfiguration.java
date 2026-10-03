package dev.glisseo.izar.integrationtests.bookstoreconsumer;

import graphql.scalars.ExtendedScalars;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * Registers GraphQL Java Extended Scalars' {@code DateTime}, {@code Date}, and {@code BigDecimal}
 * scalars with the fixture server. A server registers its own scalars through a
 * {@code RuntimeWiringConfigurer} bean.
 */
@Configuration
class ScalarWiringConfiguration {

    @Bean
    RuntimeWiringConfigurer scalarWiring() {
        return wiringBuilder ->
                wiringBuilder
                        .scalar(ExtendedScalars.DateTime)
                        .scalar(ExtendedScalars.Date)
                        .scalar(ExtendedScalars.GraphQLBigDecimal);
    }
}
