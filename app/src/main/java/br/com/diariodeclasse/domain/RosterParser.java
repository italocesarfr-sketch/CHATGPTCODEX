package br.com.diariodeclasse.domain;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Heurística conservadora; todo resultado exige revisão humana, nunca é matrícula automática. */
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
    public static final class Word {
        public final String text; public final int left, top, right, bottom;
        public Word(String text, int left, int top, int right, int bottom) {
            this.text = text; this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
        float center() { return (top + bottom) / 2f; }
        int height() { return Math.max(1, bottom - top); }
    }
    public static final class Candidate {
        public final String name, reason; public final Integer number;
        Candidate(String name, Integer number, String reason) { this.name = name; this.number = number; this.reason = reason; }
    }
    private static final Pattern NUMBER = Pattern.compile("^\\s*(\\d{1,3})[\\s.°º)\\-:]+(.*)$");
    private static final Pattern HEADER = Pattern.compile("(?i)\\b(escola|colegio|secretaria|professor|professora|disciplina|matricula|frequencia|bimestre|semestre|diretor|diretora|assinatura|pagina|turma|turno|ano letivo|relacao de|lista de|nome do|nome completo|total de|data de|observacoes|ensino|chamada|municipal|estadual|educacao|numero|nome|nomes|nascimento|situacao|sexo|codigo|total|data)\\b");
    public static List<Candidate> parse(List<Word> words) {
        List<Word> sorted = new ArrayList<>(words);
        sorted.sort(Comparator.comparingDouble(Word::center).thenComparingInt(w -> w.left));
        List<List<Word>> rows = new ArrayList<>();
        for (Word word : sorted) {
            List<Word> match = null;
            for (int i = rows.size() - 1; i >= Math.max(0, rows.size() - 3); i--) {
                List<Word> row = rows.get(i); Word first = row.get(0);
                if (Math.abs(word.center() - first.center()) <= Math.min(word.height(), first.height()) * .55f) { match = row; break; }
            }
            if (match == null) { match = new ArrayList<>(); rows.add(match); }
            match.add(word);
        }
        List<Candidate> result = new ArrayList<>();
        for (List<Word> row : rows) {
            row.sort(Comparator.comparingInt(w -> w.left));
            StringBuilder line = new StringBuilder();
            for (Word w : row) line.append(w.text).append(' ');
            Candidate candidate = parseLine(line.toString());
            if (candidate != null) result.add(candidate);
        }
        return result;
    }
    public static Candidate parseLine(String line) {
        String text = line.trim().replaceAll("\\s+", " ");
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        if (HEADER.matcher(plain).find() || text.matches(".*\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}.*")) return null;
        Integer number = null;
        Matcher matcher = NUMBER.matcher(text);
        if (matcher.matches()) { number = Integer.valueOf(matcher.group(1)); text = matcher.group(2); }
        List<String> name = new ArrayList<>(); boolean trimmed = false;
        for (String token : text.split("[\\s|;\\t]+")) {
            if (token.isEmpty() || (name.isEmpty() && token.matches("[-–.:]+"))) continue;
            if (token.matches("(?i)(P|F|FJ|NR|X|C|A|\\d+([,.]\\d+)?)") || !token.matches("[\\p{L}][\\p{L}'’.-]*")) {
                trimmed = true; break;
            }
            name.add(token);
        }
        if (name.size() < 2 || name.size() > 12) return null;
        String reason = "Conferir leitura: o OCR não fornece confiança por nome.";
        if (number == null) reason += " Número não identificado.";
        if (trimmed) reason += " Colunas à direita foram ignoradas.";
        return new Candidate(String.join(" ", name), number, reason);
    }
}
