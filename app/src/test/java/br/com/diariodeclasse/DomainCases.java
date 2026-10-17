package br.com.diariodeclasse;

import br.com.diariodeclasse.domain.*;
import java.util.*;
import static br.com.diariodeclasse.domain.SchoolRules.*;

public final class DomainCases {
    private static int passed;
    private static void test(String name, Runnable action) {
        try { action.run(); passed++; System.out.println("OK " + name); }
        catch (Throwable failure) { throw new AssertionError(name, failure); }
    }
    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("Esperado " + expected + ", recebido " + actual);
    }
    private static void close(double expected, double actual) {
        if (Math.abs(expected - actual) > .00001) throw new AssertionError("Esperado " + expected + ", recebido " + actual);
    }
    private static void fails(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Deveria rejeitar os dados");
    }
    private static AttendanceEntry entry(String date, String state, String mark) { return new AttendanceEntry(date, state, mark); }
    private static Frequency frequency(List<AttendanceEntry> entries, boolean fj) { return SchoolRules.frequency("2026-01-01", null, entries, fj); }
    public static void main(String[] args) { runAll(); }
    public static void runAll() {
        passed = 0;
        test("soma decimal das notas", () -> close(7.5, grades(Arrays.asList(2.0, 1.5, 4.0), Arrays.asList(2.0, 3.0, 5.0), 10).total));
        test("zero é diferente de pendente", () -> { GradeSummary r = grades(Arrays.asList(0.0, null), Arrays.asList(5.0, 5.0), 10); equal(1, r.pending); equal(true, r.partial); close(0, r.total); });
        test("sem avaliações é parcial", () -> equal(true, grades(Collections.emptyList(), Collections.emptyList(), 10).partial));
        test("máximos abaixo da escala é parcial", () -> equal(true, grades(Arrays.asList(2.0), Arrays.asList(2.0), 10).partial));
        test("todas as notas lançadas é completo", () -> equal(false, grades(Arrays.asList(5.0, 5.0), Arrays.asList(5.0, 5.0), 10).partial));
        test("soma respeita limite final", () -> close(10, grades(Arrays.asList(7.0, 7.0), Arrays.asList(7.0, 7.0), 10).total));
        test("nota inválida é rejeitada", () -> { fails(() -> grades(Arrays.asList(-1.0), Arrays.asList(2.0), 10)); fails(() -> grades(Arrays.asList(3.0), Arrays.asList(2.0), 10)); fails(() -> grades(Arrays.asList(Double.NaN), Arrays.asList(2.0), 10)); });
        test("recuperação substitui avaliação", () -> close(8, recovery(5, 4, "SUBSTITUIR_AVALIACAO", 1, 10)));
        test("recuperação bimestral usa maior", () -> { close(7, recovery(5, 7, "MAIOR_BIMESTRAL", 0, 10)); close(7, recovery(7, 5, "MAIOR_BIMESTRAL", 0, 10)); });
        test("recuperação soma limitada", () -> close(10, recovery(8, 5, "SOMAR", 0, 10)));
        test("recuperação apenas registro", () -> close(5, recovery(5, 8, "REGISTRAR", 0, 10)));
        test("aula simples presente", () -> close(100, frequency(Arrays.asList(entry("2026-02-04", "REALIZADA", "P")), true).percent));
        test("aula dupla tem chamadas independentes", () -> { Frequency f = frequency(Arrays.asList(entry("2026-02-04", "REALIZADA", "P"), entry("2026-02-04", "REALIZADA", "F")), true); equal(1, f.absent); close(50, f.percent); });
        test("duas ausências são duas faltas", () -> equal(2, frequency(Arrays.asList(entry("2026-02-04", "REALIZADA", "F"), entry("2026-02-04", "REALIZADA", "F")), true).absent));
        test("falta justificada conta quando configurada", () -> { Frequency f = frequency(Arrays.asList(entry("2026-02-04", "REALIZADA", "FJ")), true); equal(1, f.justified); equal(0, f.absent); close(0, f.percent); });
        test("falta justificada abonada quando configurada", () -> close(100, frequency(Arrays.asList(entry("2026-02-04", "REALIZADA", "FJ")), false).percent));
        test("aula cancelada não gera falta", () -> { Frequency f = frequency(Arrays.asList(entry("2026-02-04", "CANCELADA", "F")), true); equal(0, f.performed); equal(0, f.absent); equal(null, f.percent); });
        test("NR e chamada ausente não geram falta", () -> { Frequency f = frequency(Arrays.asList(entry("2026-02-04", "REALIZADA", "NR"), entry("2026-02-05", "REALIZADA", null)), true); equal(2, f.performed); equal(0, f.valid); equal(null, f.percent); });
        test("aula prevista não conta", () -> equal(0, frequency(Arrays.asList(entry("2026-02-04", "PREVISTA", "F")), true).absent));
        test("entrada tardia não recebe faltas anteriores", () -> equal(1, SchoolRules.frequency("2026-02-05", null, Arrays.asList(entry("2026-02-04", "REALIZADA", "F"), entry("2026-02-05", "REALIZADA", "F")), true).absent));
        test("saída encerra contagem", () -> equal(0, SchoolRules.frequency("2026-01-01", "2026-02-03", Arrays.asList(entry("2026-02-04", "REALIZADA", "F")), true).absent));
        test("horário de quarta dupla e quinta simples", () -> { List<PlannedLesson> p = plan("2026-02-02", "2026-02-08", new int[]{0,0,2,1,0,0,0}); equal(3, p.size()); equal(2, p.get(1).ordinal); equal("2026-02-05", p.get(2).date); });
        test("limites do calendário", () -> fails(() -> plan("2026-03-01", "2026-02-01", new int[7])));
        test("duplicidade ignora acentos caixa e espaços", () -> equal(true, RosterParser.possibleDuplicate("ESTUDANTE FICTÍCIO ALFA", " estudante ficticio alfa ")));
        test("duplicidade alerta erro de uma letra", () -> equal(true, RosterParser.possibleDuplicate("ESTUDANTE FICTICIO ALFA", "ESTUDANTE FICTICIO ALPA")));
        test("nomes diferentes não são duplicados", () -> equal(false, RosterParser.possibleDuplicate("ESTUDANTE FICTICIO ALFA", "ESTUDANTE FICTICIO BETA")));
        test("CSV UTF-8 com cabeçalho aspas e número", () -> equal(Arrays.asList("Estudante Fictício Alfa", "Estudante Fictício Beta"), CsvRoster.names("\uFEFFnumero;nome\n1;\"Estudante Fictício Alfa\"\n2;Estudante Fictício Beta")));
        test("CSV rejeita aspas não fechadas", () -> fails(() -> CsvRoster.names("nome\n\"texto")));
        System.out.println(passed + " cenários aprovados.");
    }
}
