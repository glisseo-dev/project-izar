package dev.glisseo.izar.compiler.fixtures;

import dev.glisseo.izar.operation.ScalarCodec;
import java.math.BigDecimal;

/**
 * A minimal application-supplied scalar codec fixture, deliberately independent of {@code
 * izar-scalars}: proves an application can map a custom scalar of its own without depending on the
 * optional common adapter library at all.
 */
public final class MoneyScalarCodec implements ScalarCodec<BigDecimal> {

    @Override
    public BigDecimal decode(Object raw) {
        if (raw instanceof String text) {
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Not a valid decimal amount: '" + text + "'.", e);
            }
        }
        throw new IllegalArgumentException("Expected a decimal string for GraphQL Money, got " + raw);
    }

    @Override
    public Object encode(BigDecimal value) {
        return value.toPlainString();
    }
}
