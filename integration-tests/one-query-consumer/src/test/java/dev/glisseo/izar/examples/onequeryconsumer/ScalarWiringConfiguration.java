package dev.glisseo.izar.examples.onequeryconsumer;

import graphql.scalars.ExtendedScalars;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * Registers GraphQL Java Extended Scalars' {@code DateTime}, {@code Date}, and {@code BigDecimal}
 * scalars with the fixture server, the representative Extended Scalars server fixtures issue 06's
 * acceptance criteria ask for. Test-only: a real server registers its own scalars the same way,
 * through its own {@code RuntimeWiringConfigurer} bean.
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
