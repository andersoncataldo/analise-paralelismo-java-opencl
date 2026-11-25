package br.com.paralelismo.benchmark;

import br.com.paralelismo.model.SearchResult;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Exportador de resultados de benchmark para arquivos CSV (UTF-8 com separador vírgula).
 */
public final class CsvExporter {

    private CsvExporter() {}

    public static void exportDetailedResults(Path outputPath, List<SearchResult> results) throws IOException {
        Files.createDirectories(outputPath.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("algoritmo,dataset,tamanho_bytes,tamanho_chars,palavra,threads_ou_cus,amostra,ocorrencias,tempo_total_ms,tempo_io_ms,tempo_setup_transfer_ms,tempo_computo_ms,observacoes");
            writer.newLine();
            for (SearchResult r : results) {
                writer.write(String.format(Locale.US,
                        "%s,%s,%d,%d,%s,%d,%d,%d,%.4f,%.4f,%.4f,%.4f,\"%s\"",
                        escape(r.getAlgorithm()),
                        escape(r.getDatasetName()),
                        r.getDatasetBytes(),
                        r.getDatasetChars(),
                        escape(r.getTargetWord()),
                        r.getThreadsOrUnits(),
                        r.getSampleIndex(),
                        r.getOccurrences(),
                        r.getTotalTimeMs(),
                        r.getIoTimeMs(),
                        r.getSetupTimeMs(),
                        r.getComputeTimeMs(),
                        r.getNotes().replace("\"", "'")
                ));
                writer.newLine();
            }
        }
    }

    public static void exportSummaryRows(Path outputPath, List<SummaryRow> rows) throws IOException {
        Files.createDirectories(outputPath.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write("experimento,dataset,tamanho_mb,palavra,algoritmo,threads_ou_cus,amostras,ocorrencias,media_total_ms,desvio_total_ms,min_total_ms,max_total_ms,media_io_ms,media_setup_transfer_ms,media_computo_ms,speedup_total,speedup_computo,eficiencia_pct");
            writer.newLine();
            for (SummaryRow r : rows) {
                writer.write(String.format(Locale.US,
                        "%s,%s,%.3f,%s,%s,%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.2f",
                        escape(r.experiment),
                        escape(r.dataset),
                        r.sizeMb,
                        escape(r.word),
                        escape(r.algorithm),
                        r.threadsOrUnits,
                        r.samples,
                        r.occurrences,
                        r.meanTotalMs,
                        r.stdDevTotalMs,
                        r.minTotalMs,
                        r.maxTotalMs,
                        r.meanIoMs,
                        r.meanSetupTransferMs,
                        r.meanComputeMs,
                        r.speedupTotal,
                        r.speedupCompute,
                        r.efficiencyPct
                ));
                writer.newLine();
            }
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace(",", ";");
    }

    public static class SummaryRow {
        public final String experiment;
        public final String dataset;
        public final double sizeMb;
        public final String word;
        public final String algorithm;
        public final int threadsOrUnits;
        public final int samples;
        public final long occurrences;
        public final double meanTotalMs;
        public final double stdDevTotalMs;
        public final double minTotalMs;
        public final double maxTotalMs;
        public final double meanIoMs;
        public final double meanSetupTransferMs;
        public final double meanComputeMs;
        public final double speedupTotal;
        public final double speedupCompute;
        public final double efficiencyPct;

        public SummaryRow(
                String experiment,
                String dataset,
                double sizeMb,
                String word,
                String algorithm,
                int threadsOrUnits,
                int samples,
                long occurrences,
                double meanTotalMs,
                double stdDevTotalMs,
                double minTotalMs,
                double maxTotalMs,
                double meanIoMs,
                double meanSetupTransferMs,
                double meanComputeMs,
                double speedupTotal,
                double speedupCompute,
                double efficiencyPct) {
            this.experiment = experiment;
            this.dataset = dataset;
            this.sizeMb = sizeMb;
            this.word = word;
            this.algorithm = algorithm;
            this.threadsOrUnits = threadsOrUnits;
            this.samples = samples;
            this.occurrences = occurrences;
            this.meanTotalMs = meanTotalMs;
            this.stdDevTotalMs = stdDevTotalMs;
            this.minTotalMs = minTotalMs;
            this.maxTotalMs = maxTotalMs;
            this.meanIoMs = meanIoMs;
            this.meanSetupTransferMs = meanSetupTransferMs;
            this.meanComputeMs = meanComputeMs;
            this.speedupTotal = speedupTotal;
            this.speedupCompute = speedupCompute;
            this.efficiencyPct = efficiencyPct;
        }
    }
}
