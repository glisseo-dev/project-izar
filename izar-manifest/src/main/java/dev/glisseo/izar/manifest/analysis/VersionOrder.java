package dev.glisseo.izar.manifest.analysis;

import java.math.BigInteger;
import java.util.Comparator;

/**
 * Orders manifest version strings so that digit runs compare as numbers: {@code 1.9} sorts before
 * {@code 1.10}, which plain {@link String#compareTo} gets backwards.
 */
final class VersionOrder implements Comparator<String> {
    static final VersionOrder INSTANCE = new VersionOrder();

    private VersionOrder() {}

    @Override
    public int compare(String left, String right) {
        int l = 0;
        int r = 0;
        while (l < left.length() && r < right.length()) {
            boolean leftDigit = Character.isDigit(left.charAt(l));
            boolean rightDigit = Character.isDigit(right.charAt(r));
            int leftEnd = runEnd(left, l, leftDigit);
            int rightEnd = runEnd(right, r, rightDigit);
            String leftRun = left.substring(l, leftEnd);
            String rightRun = right.substring(r, rightEnd);
            int result;
            if (leftDigit && rightDigit) {
                result = new BigInteger(leftRun).compareTo(new BigInteger(rightRun));
            } else if (leftDigit != rightDigit) {
                // A digit run sorts before a text run so "1.0" precedes "1.beta".
                result = leftDigit ? -1 : 1;
            } else {
                result = leftRun.compareTo(rightRun);
            }
            if (result != 0) {
                return result;
            }
            l = leftEnd;
            r = rightEnd;
        }
        int remaining = Boolean.compare(l < left.length(), r < right.length());
        // "1.0" and "1.00" compare equal numerically; fall back to the raw strings for a total order.
        return remaining != 0 ? remaining : left.compareTo(right);
    }

    private static int runEnd(String value, int start, boolean digits) {
        int end = start;
        while (end < value.length() && Character.isDigit(value.charAt(end)) == digits) {
            end++;
        }
        return end;
    }
}
