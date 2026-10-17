package br.com.diariodeclasse.domain;

import java.time.LocalDate;
import java.util.*;

/** Regras puras, compartilhadas pelo aplicativo e pelos testes sem Android. */
public final class SchoolRules {
    private SchoolRules() {}
    public static final class AttendanceEntry {
        public final String date, lessonState, mark;
        public AttendanceEntry(String date, String lessonState, String mark) {
            this.date = date; this.lessonState = lessonState; this.mark = mark;
        }
    }
    public static final class Frequency {
        public final int performed, valid, present, absent, justified;
        public final Double percent;
        Frequency(int performed, int valid, int present, int absent, int justified, boolean countJustified) {
            this.performed = performed; this.valid = valid; this.present = present;
            this.absent = absent; this.justified = justified;
            this.percent = valid == 0 ? null : 100.0 * (present + (countJustified ? 0 : justified)) / valid;
        }
    }
    public static Frequency frequency(String joined, String left, List<AttendanceEntry> entries, boolean countJustified) {
        int performed = 0, valid = 0, p = 0, f = 0, fj = 0;
        for (AttendanceEntry e : entries) {
            if (!"REALIZADA".equals(e.lessonState) || e.date.compareTo(joined) < 0 ||
                (left != null && !left.isEmpty() && e.date.compareTo(left) > 0)) continue;
            performed++;
            switch (e.mark == null ? "NR" : e.mark) {
                case "P": p++; valid++; break;
                case "F": f++; valid++; break;
                case "FJ": fj++; valid++; break;
                default: break;
            }
        }
        return new Frequency(performed, valid, p, f, fj, countJustified);
    }
    /** Soma de oportunidades de frequência das matrículas, sem média de percentuais. */
    public static Frequency aggregateFrequency(List<Frequency> rows, boolean countJustified) {
        int performed = 0, valid = 0, present = 0, absent = 0, justified = 0;
        for (Frequency row : rows) {
            performed += row.performed; valid += row.valid; present += row.present;
            absent += row.absent; justified += row.justified;
        }
        return new Frequency(performed, valid, present, absent, justified, countJustified);
    }
    public static final class GradeSummary {
        public final double total, maximum;
        public final int pending;
        public final boolean partial;
        GradeSummary(double total, double maximum, int pending, double scale) {
            this.total = Math.min(scale, total); this.maximum = maximum; this.pending = pending;
            partial = pending > 0 || maximum + 0.000001 < scale;
        }
    }
    public static GradeSummary grades(List<Double> scores, List<Double> maxima, double scale) {
        if (scores.size() != maxima.size() || !Double.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Escala inválida");
        double total = 0, maximum = 0; int pending = 0;
        for (int i = 0; i < scores.size(); i++) {
            double max = maxima.get(i);
            if (!Double.isFinite(max) || max <= 0) throw new IllegalArgumentException("Valor máximo inválido");
            maximum += max;
            Double score = scores.get(i);
            if (score == null) { pending++; continue; }
            if (!Double.isFinite(score) || score < 0 || score > max) throw new IllegalArgumentException("Nota fora do limite");
            total += score;
        }
        return new GradeSummary(total, maximum, pending, scale);
    }
    public static double recovery(double previous, double recovery, String rule, double replaced, double limit) {
        if (previous < 0 || recovery < 0 || limit <= 0) throw new IllegalArgumentException("Recuperação inválida");
        switch (rule) {
            case "SUBSTITUIR_AVALIACAO": return Math.min(limit, Math.max(0, previous - replaced + recovery));
            case "MAIOR_BIMESTRAL": return Math.min(limit, Math.max(previous, recovery));
            case "SOMAR": return Math.min(limit, previous + recovery);
            case "REGISTRAR": return previous;
            default: throw new IllegalArgumentException("Regra desconhecida");
        }
    }
    public static final class PlannedLesson {
        public final String date; public final int ordinal;
        PlannedLesson(String date, int ordinal) { this.date = date; this.ordinal = ordinal; }
    }
    public static List<PlannedLesson> plan(String start, String end, int[] counts) {
        if (counts.length != 7) throw new IllegalArgumentException("São necessários sete dias");
        LocalDate a = LocalDate.parse(start), b = LocalDate.parse(end);
        if (b.isBefore(a) || b.isAfter(a.plusYears(1))) throw new IllegalArgumentException("Período inválido");
        List<PlannedLesson> result = new ArrayList<>();
        for (LocalDate d = a; !d.isAfter(b); d = d.plusDays(1)) {
            int count = counts[d.getDayOfWeek().getValue() - 1];
            if (count < 0 || count > 12) throw new IllegalArgumentException("Quantidade de aulas inválida");
            for (int n = 1; n <= count; n++) result.add(new PlannedLesson(d.toString(), n));
        }
        return result;
    }
}
