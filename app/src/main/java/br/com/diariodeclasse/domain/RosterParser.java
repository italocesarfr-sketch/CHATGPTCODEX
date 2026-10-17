package br.com.diariodeclasse.domain;

import java.text.Normalizer;
import java.util.*;

/** Normalização e alerta de possíveis homônimos; não executa OCR. */
public final class RosterParser {
    private RosterParser() {}
    public static String key(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
    public static boolean possibleDuplicate(String a, String b) {
        String x = key(a), y = key(b);
        if (x.isEmpty() || y.isEmpty()) return false;
        if (x.equals(y)) return true;
        if (Math.min(x.length(), y.length()) < 8 || Math.abs(x.length() - y.length()) > 1) return false;
        int[] previous = new int[y.length() + 1];
        for (int j = 0; j <= y.length(); j++) previous[j] = j;
        for (int i = 1; i <= x.length(); i++) {
            int[] next = new int[y.length() + 1]; next[0] = i;
            for (int j = 1; j <= y.length(); j++) next[j] = Math.min(Math.min(next[j - 1] + 1, previous[j] + 1), previous[j - 1] + (x.charAt(i - 1) == y.charAt(j - 1) ? 0 : 1));
            previous = next;
        }
        return previous[y.length()] <= 1;
    }
}
