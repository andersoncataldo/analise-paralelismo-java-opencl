package br.com.paralelismo.benchmark;

import br.com.paralelismo.algorithms.ParallelCPU;
import br.com.paralelismo.algorithms.ParallelGPU;
import br.com.paralelismo.algorithms.SerialCPU;
import br.com.paralelismo.algorithms.TextUtils;
import br.com.paralelismo.model.DatasetConfig;
import br.com.paralelismo.model.SearchResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Framework de testes e benchmark comparativo:
 * 1) Comparativo nas 3 amostras originais (End-to-End com I/O + Cold/Warm GPU)
 * 2) Escalabilidade ParallelCPU variando o número de núcleos/threads (1, 2, 4, 8, 16)
 * 3) Escalabilidade por volume de dados (0.87 MB até ~44 MB) comparando CPU vs GPU
 */
public class BenchmarkRunner {

    public static final int DEFAULT_SAMPLES = 5;
    public static final int COLD_GPU_SAMPLES = 3;
    public static final int[] CPU_THREAD_COUNTS = {1, 2, 4, 8, 16};

    private final Path samplesDir;
    private final Path resultsDir;

    public BenchmarkRunner(Path samplesDir, Path resultsDir) {
        this.samplesDir = samplesDir;
        this.resultsDir = resultsDir;
    }

    public List<DatasetConfig> getDefaultDatasets() {
        List<DatasetConfig> list = new ArrayList<>();
        list.add(new DatasetConfig(
                "dracula", "Dracula (0.85 MB)",
                samplesDir.resolve("Dracula-165307.txt"), "blood", "Inglês", false));
        list.add(new DatasetConfig(
                "mobydick", "Moby Dick (1.22 MB)",
                samplesDir.resolve("MobyDick-217452.txt"), "whale", "Inglês", false));
        list.add(new DatasetConfig(
                "donquixote", "Don Quixote (2.12 MB)",
                samplesDir.resolve("DonQuixote-388208.txt"), "quijote", "Espanhol", false));
        return list;
    }

    public BenchmarkSuiteResult runAll() throws IOException {
        List<DatasetConfig> datasets = getDefaultDatasets();
        List<SearchResult> allDetailed = new ArrayList<>();
        List<CsvExporter.SummaryRow> exp1Rows = new ArrayList<>();
        List<CsvExporter.SummaryRow> exp2Rows = new ArrayList<>();
        List<CsvExporter.SummaryRow> exp3Rows = new ArrayList<>();

        SerialCPU serial = new SerialCPU();
        ParallelCPU parallel4 = new ParallelCPU(4);
        ParallelCPU parallel8 = new ParallelCPU(8);

        System.out.println("=========================================================================");
        System.out.println("  ANÁLISE COMPARATIVA DE ALGORITMOS COM USO DE PARALELISMO (CPU vs GPU)  ");
        System.out.println("=========================================================================");
        System.out.printf("CPU lógica disponível na JVM: %d processadores%n", Runtime.getRuntime().availableProcessors());

        try (ParallelGPU gpu = new ParallelGPU()) {
            gpu.initialize();
            System.out.println("GPU OpenCL: " + gpu.getDeviceSummary());
            System.out.println();

            // Aquecimento JIT, cache de arquivos do SO e OpenCL (não contabilizado nas estatísticas)
            System.out.println("[Aquecimento] Executando warm-up da JVM, cache de disco e GPU OpenCL...");
            for (DatasetConfig ds : datasets) {
                for (int w = 0; w < 2; w++) {
                    serial.countWord(ds.getFilePath(), ds.getTargetWord(), 0);
                    parallel4.countWord(ds.getFilePath(), ds.getTargetWord(), 0);
                    parallel8.countWord(ds.getFilePath(), ds.getTargetWord(), 0);
                    gpu.countWord(ds.getFilePath(), ds.getTargetWord(), 0);
                }
            }
            System.out.println("[Aquecimento] Concluído.\n");

            // =========================================================================
            // EXPERIMENTO 1: Comparativo nas 3 amostras originais (com I/O de arquivo)
            // =========================================================================
            System.out.println("--- EXPERIMENTO 1: Comparativo nas 3 Amostras Originais (End-to-End) ---");
            for (DatasetConfig ds : datasets) {
                long fileBytes = Files.size(ds.getFilePath());
                double sizeMb = fileBytes / (1024.0 * 1024.0);
                System.out.printf("%nDataset: %s (%.2f MB) | Palavra: '%s'%n",
                        ds.getFilePath().getFileName(), sizeMb, ds.getTargetWord());

                List<SearchResult> serialRuns = new ArrayList<>();
                List<SearchResult> p4Runs = new ArrayList<>();
                List<SearchResult> p8Runs = new ArrayList<>();
                List<SearchResult> gpuWarmRuns = new ArrayList<>();
                List<SearchResult> gpuColdRuns = new ArrayList<>();

                for (int s = 1; s <= DEFAULT_SAMPLES; s++) {
                    SearchResult rSer = serial.countWord(ds.getFilePath(), ds.getTargetWord(), s);
                    SearchResult rP4 = parallel4.countWord(ds.getFilePath(), ds.getTargetWord(), s);
                    SearchResult rP8 = parallel8.countWord(ds.getFilePath(), ds.getTargetWord(), s);
                    SearchResult rGpu = gpu.countWord(ds.getFilePath(), ds.getTargetWord(), s);

                    verifyExactMatch(ds.getDisplayName(), rSer, rP4, rP8, rGpu);

                    serialRuns.add(rSer);
                    p4Runs.add(rP4);
                    p8Runs.add(rP8);
                    gpuWarmRuns.add(rGpu);

                    allDetailed.add(rSer);
                    allDetailed.add(rP4);
                    allDetailed.add(rP8);
                    allDetailed.add(rGpu);
                }

                for (int s = 1; s <= COLD_GPU_SAMPLES; s++) {
                    SearchResult rCold = ParallelGPU.countWordColdStart(ds.getFilePath(), ds.getTargetWord(), s);
                    verifyExactMatch(ds.getDisplayName(), serialRuns.get(0), rCold);
                    gpuColdRuns.add(rCold);
                    allDetailed.add(rCold);
                }

                CsvExporter.SummaryRow sumSer = summarize("Exp1_Amostras", ds.getDisplayName(), sizeMb, ds.getTargetWord(), "SerialCPU", 1, serialRuns, 0, 0);
                CsvExporter.SummaryRow sumP4 = summarize("Exp1_Amostras", ds.getDisplayName(), sizeMb, ds.getTargetWord(), "ParallelCPU (4T)", 4, p4Runs, sumSer.meanTotalMs, sumSer.meanComputeMs);
                CsvExporter.SummaryRow sumP8 = summarize("Exp1_Amostras", ds.getDisplayName(), sizeMb, ds.getTargetWord(), "ParallelCPU (8T)", 8, p8Runs, sumSer.meanTotalMs, sumSer.meanComputeMs);
                CsvExporter.SummaryRow sumGpuWarm = summarize("Exp1_Amostras", ds.getDisplayName(), sizeMb, ds.getTargetWord(), "ParallelGPU (Warm)", gpu.getMaxComputeUnits(), gpuWarmRuns, sumSer.meanTotalMs, sumSer.meanComputeMs);
                CsvExporter.SummaryRow sumGpuCold = summarize("Exp1_Amostras", ds.getDisplayName(), sizeMb, ds.getTargetWord(), "ParallelGPU (Cold)", gpu.getMaxComputeUnits(), gpuColdRuns, sumSer.meanTotalMs, sumSer.meanComputeMs);

                exp1Rows.add(sumSer);
                exp1Rows.add(sumP4);
                exp1Rows.add(sumP8);
                exp1Rows.add(sumGpuWarm);
                exp1Rows.add(sumGpuCold);

                printSummaryLine(sumSer);
                printSummaryLine(sumP4);
                printSummaryLine(sumP8);
                printSummaryLine(sumGpuWarm);
                printSummaryLine(sumGpuCold);
            }

            // =========================================================================
            // EXPERIMENTO 2: Escalabilidade por número de threads na CPU (em memória)
            // =========================================================================
            System.out.println("\n--- EXPERIMENTO 2: Escalabilidade ParallelCPU por Número de Threads ---");
            for (DatasetConfig ds : datasets) {
                long fileBytes = Files.size(ds.getFilePath());
                double sizeMb = fileBytes / (1024.0 * 1024.0);
                char[] text = TextUtils.readFileChars(ds.getFilePath());

                System.out.printf("%nDataset: %s | Palavra: '%s'%n", ds.getDisplayName(), ds.getTargetWord());

                // Baseline 1 thread (SerialCPU em memória)
                List<SearchResult> baseRuns = new ArrayList<>();
                for (int s = 1; s <= DEFAULT_SAMPLES; s++) {
                    baseRuns.add(serial.countWordInMemory(ds.getDisplayName(), fileBytes, text, ds.getTargetWord(), s));
                }
                CsvExporter.SummaryRow baseSummary = summarize("Exp2_NucleosCPU", ds.getDisplayName(), sizeMb, ds.getTargetWord(), "SerialCPU (1T)", 1, baseRuns, 0, 0);

                for (int threads : CPU_THREAD_COUNTS) {
                    ParallelCPU worker = new ParallelCPU(threads);
                    List<SearchResult> runs = new ArrayList<>();
                    ExecutorService pool = Executors.newFixedThreadPool(threads);
                    try {
                        // Warmup específico para essa contagem de threads
                        for (int w = 0; w < 3; w++) {
                            worker.countWordInMemory(ds.getDisplayName(), fileBytes, text, ds.getTargetWord(), 0, pool);
                        }
                        for (int s = 1; s <= DEFAULT_SAMPLES; s++) {
                            SearchResult r = worker.countWordInMemory(ds.getDisplayName(), fileBytes, text, ds.getTargetWord(), s, pool);
                            verifyExactMatch(ds.getDisplayName(), baseRuns.get(0), r);
                            runs.add(r);
                        }
                    } finally {
                        pool.shutdown();
                    }
                    CsvExporter.SummaryRow row = summarize(
                            "Exp2_NucleosCPU", ds.getDisplayName(), sizeMb, ds.getTargetWord(),
                            "ParallelCPU", threads, runs, baseSummary.meanTotalMs, baseSummary.meanComputeMs);
                    exp2Rows.add(row);
                    System.out.printf(Locale.US,
                            "  Threads=%2d | Ocorrências=%d | Total=%.2f ms (±%.2f) | Cômputo=%.2f ms | Speedup(cômputo)=%.2fx | Eficiência=%.1f%%%n",
                            threads, row.occurrences, row.meanTotalMs, row.stdDevTotalMs, row.meanComputeMs,
                            row.speedupCompute, row.efficiencyPct);
                }
            }

            // =========================================================================
            // EXPERIMENTO 3: Impacto do Volume de Dados (CPU vs GPU em memória)
            // =========================================================================
            System.out.println("\n--- EXPERIMENTO 3: Escalabilidade por Volume de Dados (CPU vs GPU) ---");
            DatasetConfig dq = datasets.get(2); // Don Quixote (2.19 MB)
            char[] baseDqChars = TextUtils.readFileChars(dq.getFilePath());
            long baseDqBytes = Files.size(dq.getFilePath());

            int[] multipliers = {1, 3, 5, 10, 20};
            Map<String, char[]> volumeBuffers = new LinkedHashMap<>();
            Map<String, Long> volumeBytes = new LinkedHashMap<>();

            // Inclui Dracula e MobyDick + múltiplos de Don Quixote
            for (int i = 0; i < 2; i++) {
                DatasetConfig ds = datasets.get(i);
                volumeBuffers.put(ds.getDisplayName(), TextUtils.readFileChars(ds.getFilePath()));
                volumeBytes.put(ds.getDisplayName(), Files.size(ds.getFilePath()));
            }
            for (int m : multipliers) {
                String label = (m == 1)
                        ? "Don Quixote 1x (2.12 MB)"
                        : String.format(Locale.US, "Don Quixote %dx (%.1f MB)", m, (baseDqBytes * m) / (1024.0 * 1024.0));
                volumeBuffers.put(label, repeatChars(baseDqChars, m));
                volumeBytes.put(label, baseDqBytes * m);
            }

            ExecutorService pool4 = Executors.newFixedThreadPool(4);
            ExecutorService pool8 = Executors.newFixedThreadPool(8);
            try {
                for (Map.Entry<String, char[]> entry : volumeBuffers.entrySet()) {
                    String label = entry.getKey();
                    char[] buf = entry.getValue();
                    long bytes = volumeBytes.get(label);
                    double sizeMb = bytes / (1024.0 * 1024.0);
                    String word = label.startsWith("Dracula") ? "blood"
                            : label.startsWith("Moby") ? "whale" : "quijote";

                    System.out.printf(Locale.US, "%nVolume: %s (%.2f MB, %,d chars) | Palavra: '%s'%n",
                            label, sizeMb, buf.length, word);

                    List<SearchResult> serRuns = new ArrayList<>();
                    List<SearchResult> p4Runs = new ArrayList<>();
                    List<SearchResult> p8Runs = new ArrayList<>();
                    List<SearchResult> gpuRuns = new ArrayList<>();

                    // 1 warm-up rápido para cada volume
                    serial.countWordInMemory(label, bytes, buf, word, 0);
                    parallel4.countWordInMemory(label, bytes, buf, word, 0, pool4);
                    parallel8.countWordInMemory(label, bytes, buf, word, 0, pool8);
                    gpu.countWordInMemory(label, bytes, buf, word, 0, 256);

                    for (int s = 1; s <= DEFAULT_SAMPLES; s++) {
                        SearchResult rSer = serial.countWordInMemory(label, bytes, buf, word, s);
                        SearchResult rP4 = parallel4.countWordInMemory(label, bytes, buf, word, s, pool4);
                        SearchResult rP8 = parallel8.countWordInMemory(label, bytes, buf, word, s, pool8);
                        SearchResult rGpu = gpu.countWordInMemory(label, bytes, buf, word, s, 256);
                        verifyExactMatch(label, rSer, rP4, rP8, rGpu);
                        serRuns.add(rSer);
                        p4Runs.add(rP4);
                        p8Runs.add(rP8);
                        gpuRuns.add(rGpu);
                    }

                    CsvExporter.SummaryRow sSer = summarize("Exp3_Volume", label, sizeMb, word, "SerialCPU", 1, serRuns, 0, 0);
                    CsvExporter.SummaryRow sP4 = summarize("Exp3_Volume", label, sizeMb, word, "ParallelCPU (4T)", 4, p4Runs, sSer.meanTotalMs, sSer.meanComputeMs);
                    CsvExporter.SummaryRow sP8 = summarize("Exp3_Volume", label, sizeMb, word, "ParallelCPU (8T)", 8, p8Runs, sSer.meanTotalMs, sSer.meanComputeMs);
                    CsvExporter.SummaryRow sGpu = summarize("Exp3_Volume", label, sizeMb, word, "ParallelGPU (Warm)", gpu.getMaxComputeUnits(), gpuRuns, sSer.meanTotalMs, sSer.meanComputeMs);

                    exp3Rows.add(sSer);
                    exp3Rows.add(sP4);
                    exp3Rows.add(sP8);
                    exp3Rows.add(sGpu);

                    System.out.printf(Locale.US, "  SerialCPU:        %7.2f ms (cômputo=%6.2f ms) | ocorrências=%d%n",
                            sSer.meanTotalMs, sSer.meanComputeMs, sSer.occurrences);
                    System.out.printf(Locale.US, "  ParallelCPU (4T): %7.2f ms (cômputo=%6.2f ms) | speedup=%.2fx%n",
                            sP4.meanTotalMs, sP4.meanComputeMs, sP4.speedupCompute);
                    System.out.printf(Locale.US, "  ParallelCPU (8T): %7.2f ms (cômputo=%6.2f ms) | speedup=%.2fx%n",
                            sP8.meanTotalMs, sP8.meanComputeMs, sP8.speedupCompute);
                    System.out.printf(Locale.US, "  ParallelGPU:      %7.2f ms (transfer=%6.2f ms, kernel=%6.2f ms) | speedup(kernel)=%.2fx%n",
                            sGpu.meanTotalMs, sGpu.meanSetupTransferMs, sGpu.meanComputeMs, sGpu.speedupCompute);
                }
            } finally {
                pool4.shutdown();
                pool8.shutdown();
            }

            // Exporta todos os arquivos CSV
            List<CsvExporter.SummaryRow> allSummaries = new ArrayList<>();
            allSummaries.addAll(exp1Rows);
            allSummaries.addAll(exp2Rows);
            allSummaries.addAll(exp3Rows);

            CsvExporter.exportDetailedResults(resultsDir.resolve("resultados_detalhados.csv"), allDetailed);
            CsvExporter.exportSummaryRows(resultsDir.resolve("resultados_resumo.csv"), exp1Rows);
            CsvExporter.exportSummaryRows(resultsDir.resolve("resultados_nucleos_cpu.csv"), exp2Rows);
            CsvExporter.exportSummaryRows(resultsDir.resolve("resultados_volume_gpu.csv"), exp3Rows);

            System.out.println("\nArquivos CSV exportados com sucesso em: " + resultsDir.toAbsolutePath());
            return new BenchmarkSuiteResult(exp1Rows, exp2Rows, exp3Rows, gpu.getDeviceSummary(), gpu.getInitTimeMs());
        }
    }

    private static char[] repeatChars(char[] base, int times) {
        if (times <= 1) {
            return base;
        }
        // Adiciona '\n' entre repetições para não juntar última e primeira palavra
        int singleLen = base.length + 1;
        char[] out = new char[singleLen * times];
        for (int i = 0; i < times; i++) {
            System.arraycopy(base, 0, out, i * singleLen, base.length);
            out[i * singleLen + base.length] = '\n';
        }
        return out;
    }

    private static void verifyExactMatch(String context, SearchResult baseline, SearchResult... others) {
        for (SearchResult other : others) {
            if (baseline.getOccurrences() != other.getOccurrences()) {
                throw new AssertionError(String.format(
                        "Divergência de contagem em %s! %s=%d vs %s=%d",
                        context, baseline.getAlgorithm(), baseline.getOccurrences(),
                        other.getAlgorithm(), other.getOccurrences()));
            }
        }
    }

    private static CsvExporter.SummaryRow summarize(
            String experiment,
            String dataset,
            double sizeMb,
            String word,
            String algorithm,
            int threadsOrUnits,
            List<SearchResult> runs,
            double serialTotalMs,
            double serialComputeMs) {
        List<Double> totals = new ArrayList<>();
        List<Double> ios = new ArrayList<>();
        List<Double> setups = new ArrayList<>();
        List<Double> computes = new ArrayList<>();

        for (SearchResult r : runs) {
            totals.add(r.getTotalTimeMs());
            ios.add(r.getIoTimeMs());
            setups.add(r.getSetupTimeMs());
            computes.add(r.getComputeTimeMs());
        }

        StatisticsUtil.Stats stTotal = StatisticsUtil.compute(totals);
        StatisticsUtil.Stats stIo = StatisticsUtil.compute(ios);
        StatisticsUtil.Stats stSetup = StatisticsUtil.compute(setups);
        StatisticsUtil.Stats stCompute = StatisticsUtil.compute(computes);

        double spTotal = (serialTotalMs > 0) ? StatisticsUtil.speedup(serialTotalMs, stTotal.mean) : 1.0;
        double spCompute = (serialComputeMs > 0) ? StatisticsUtil.speedup(serialComputeMs, stCompute.mean) : 1.0;
        double eff = StatisticsUtil.efficiency(spCompute, threadsOrUnits);

        return new CsvExporter.SummaryRow(
                experiment,
                dataset,
                sizeMb,
                word,
                algorithm,
                threadsOrUnits,
                runs.size(),
                runs.get(0).getOccurrences(),
                stTotal.mean,
                stTotal.stdDev,
                stTotal.min,
                stTotal.max,
                stIo.mean,
                stSetup.mean,
                stCompute.mean,
                spTotal,
                spCompute,
                eff
        );
    }

    private static void printSummaryLine(CsvExporter.SummaryRow r) {
        System.out.printf(Locale.US,
                "  %-20s | Ocorr=%5d | Total=%7.2f ms (±%5.2f) | I/O=%5.2f | Setup/Transf=%6.2f | Cômputo=%6.2f ms | Speedup(tot)=%.2fx%n",
                r.algorithm, r.occurrences, r.meanTotalMs, r.stdDevTotalMs,
                r.meanIoMs, r.meanSetupTransferMs, r.meanComputeMs, r.speedupTotal);
    }

    public static class BenchmarkSuiteResult {
        public final List<CsvExporter.SummaryRow> exp1Rows;
        public final List<CsvExporter.SummaryRow> exp2Rows;
        public final List<CsvExporter.SummaryRow> exp3Rows;
        public final String gpuSummary;
        public final double gpuInitMs;

        public BenchmarkSuiteResult(
                List<CsvExporter.SummaryRow> exp1Rows,
                List<CsvExporter.SummaryRow> exp2Rows,
                List<CsvExporter.SummaryRow> exp3Rows,
                String gpuSummary,
                double gpuInitMs) {
            this.exp1Rows = exp1Rows;
            this.exp2Rows = exp2Rows;
            this.exp3Rows = exp3Rows;
            this.gpuSummary = gpuSummary;
            this.gpuInitMs = gpuInitMs;
        }
    }
}
