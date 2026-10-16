package br.com.diariodeclasse.domain;

import java.util.*;
import java.util.regex.*;

/** CSV UTF-8 ou texto colado. Nada é descartado silenciosamente nem cadastrado aqui. */
public final class CsvRoster {
    private CsvRoster() {}
    public static final class Row {
        public final String name;
        public final Integer number;
        public Row(String name, Integer number) { this.name = name; this.number = number; }
    }
    public static List<String> names(String csv) {
        List<String> names = new ArrayList<>();
        for (Row row : parse(csv)) names.add(row.name);
        return names;
    }
    public static List<Row> parse(String text) {
        String[] lines = text.replace("\uFEFF", "").split("\\r?\\n");
        List<Row> result = new ArrayList<>();
        int nameColumn = -1, numberColumn = -1;
        char delimiter = 0;
        boolean first = true;
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            if (first) { delimiter = delimiter(line); first = false; }
            List<String> cells = cells(line, delimiter);
            if (result.isEmpty() && nameColumn < 0) {
                for (int i = 0; i < cells.size(); i++) {
                    String key = RosterParser.key(cells.get(i));
                    if (Arrays.asList("nome", "nomecompleto", "nomedoaluno", "aluno", "estudante").contains(key)) nameColumn = i;
                    if (Arrays.asList("numero", "n", "no", "nchamada", "numerodachamada", "numeronachamada").contains(key)) numberColumn = i;
                }
                if (nameColumn >= 0) continue;
            }
            String name;
            Integer number = null;
            if (nameColumn >= 0) {
                if (nameColumn >= cells.size() || numberColumn >= cells.size()) throw new IllegalArgumentException("CSV tem colunas ausentes. Confira todas as linhas.");
                name = cells.get(nameColumn);
                if (numberColumn >= 0) number = number(cells.get(numberColumn));
            } else if (cells.size() == 2 && cells.get(0).matches("\\d*")) {
                number = number(cells.get(0)); name = cells.get(1);
            } else if (cells.size() == 1) {
                Matcher match = Pattern.compile("^(\\d+)[\\s.)º°:\\-]+(.+)$").matcher(cells.get(0));
                if (match.matches()) { number = number(match.group(1)); name = match.group(2).trim(); }
                else name = cells.get(0);
            } else throw new IllegalArgumentException("CSV com várias colunas precisa de cabeçalho Nome e, opcionalmente, Número.");
            if (name.trim().length() < 2) throw new IllegalArgumentException("Há uma linha sem nome válido. Corrija o arquivo antes de importar.");
            result.add(new Row(name.trim(), number));
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Nenhum aluno encontrado na lista.");
        if (result.size() > 2000) throw new IllegalArgumentException("Importe até 2.000 alunos por vez.");
        return result;
    }
    private static Integer number(String value) {
        if (value.isEmpty()) return null;
        try {
            int n = Integer.parseInt(value);
            if (n <= 0) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException e) { throw new IllegalArgumentException("Número de chamada inválido: use inteiro positivo ou deixe vazio."); }
    }
    private static char delimiter(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') quoted = !quoted;
            else if (!quoted && (c == ';' || c == '\t' || c == ',')) return c;
        }
        return 0;
    }
    private static List<String> cells(String line, char delimiter) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false, closed = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else if (quoted) { quoted = false; closed = true; }
                else if (cell.toString().trim().isEmpty() && !closed) { cell.setLength(0); quoted = true; }
                else throw new IllegalArgumentException("Aspas inválidas no CSV.");
            } else if (c == delimiter && !quoted) { cells.add(cell.toString().trim()); cell.setLength(0); closed = false; }
            else {
                if (closed && !Character.isWhitespace(c)) throw new IllegalArgumentException("Texto após aspas no CSV.");
                cell.append(c);
            }
        }
        if (quoted) throw new IllegalArgumentException("CSV contém aspas não fechadas; use uma linha por aluno.");
        cells.add(cell.toString().trim());
        return cells;
    }
}
