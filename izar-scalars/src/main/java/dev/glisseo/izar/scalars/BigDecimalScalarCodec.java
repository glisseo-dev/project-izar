package dev.glisseo.izar.scalars;

import dev.glisseo.izar.operation.ScalarCodec;
import java.math.BigDecimal;

/**
 * Maps GraphQL Java Extended Scalars' {@code BigDecimal} scalar to {@link BigDecimal}. Accepts
 * either wire representation compatible clients use for it (a JSON number or a decimal string) and
 * always reads through {@link Object#toString()} rather than {@link Number#doubleValue()}, so an
 * arbitrary-precision value already parsed as some other {@link Number} does not lose precision
 * before this codec ever sees it. {@link BigDecimal} is immutable, so no defensive copying is
 * needed to keep a generated response or built input safe from later mutation.
 */
public final class BigDecimalScalarCodec implements ScalarCodec<BigDecimal> {

    @Override
    public BigDecimal decode(Object raw) {
        if (raw instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (raw instanceof String || raw instanceof Number) {
            try {
                return new BigDecimal(raw.toString());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Not a valid decimal number: '" + raw + "'.", e);
            }
        }
        throw new IllegalArgumentException(
                "Expected a decimal number for GraphQL BigDecimal, got " + ScalarValues.describe(raw));
    }

    @Override
    public Object encode(BigDecimal value) {
        return value.toPlainString();
    }
}
