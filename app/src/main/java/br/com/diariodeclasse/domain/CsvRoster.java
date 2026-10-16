package br.com.diariodeclasse.domain;
import java.util.*;
public final class CsvRoster {
    private CsvRoster() {}
    public static List<String> names(String csv) {
        List<String> result = new ArrayList<>();
        String[] lines = csv.replace("\uFEFF", "").split("\\r?\\n");
        int column = -1;
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            List<String> cells = new ArrayList<>(); StringBuilder cell = new StringBuilder(); boolean quoted = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '"') {
                    if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { cell.append('"'); i++; }
                    else quoted = !quoted;
                } else if ((c == ',' || c == ';' || c == '\t') && !quoted) { cells.add(cell.toString().trim()); cell.setLength(0); }
                else cell.append(c);
            }
            if (quoted) throw new IllegalArgumentException("CSV contém aspas não fechadas; use uma linha por aluno.");
            cells.add(cell.toString().trim());
            boolean header = false;
            for (int i = 0; i < cells.size(); i++) {
                String key = RosterParser.key(cells.get(i));
                if (key.equals("nome") || key.equals("nomecompleto") || key.equals("nomedoaluno")) { column = i; header = true; break; }
            }
            if (header) continue;
            int index = column >= 0 ? column : cells.size() > 1 && cells.get(0).matches("\\d+") ? 1 : 0;
            if (index < cells.size() && cells.get(index).length() >= 2) result.add(cells.get(index));
        }
        return result;
    }
}
