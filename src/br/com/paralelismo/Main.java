package br.com.paralelismo;

import br.com.paralelismo.algorithms.ParallelCPU;
import br.com.paralelismo.algorithms.ParallelGPU;
import br.com.paralelismo.algorithms.SerialCPU;
import br.com.paralelismo.benchmark.BenchmarkRunner;
import br.com.paralelismo.model.SearchResult;
import br.com.paralelismo.ui.ChartGenerator;
import br.com.paralelismo.ui.DashboardSwing;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

/**
 * Ponto de entrada da aplicação de Análise Comparativa de Paralelismo (CPU Serial, CPU Paralela e GPU OpenCL).
 *
 * Modos de uso:
 *   1) Benchmark completo + CSV + Gráficos PNG (headless):
 *        java --enable-native-access=ALL-UNNAMED -cp "out;lib/jocl-2.0.4.jar" br.com.paralelismo.Main --benchmark
 *   2) Benchmark completo + CSV + Gráficos PNG + Interface Gráfica Swing:
 *        java --enable-native-access=ALL-UNNAMED -cp "out;lib/jocl-2.0.4.jar" br.com.paralelismo.Main --gui
 *   3) Busca individual em um arquivo .txt e palavra:
 *        java --enable-native-access=ALL-UNNAMED -cp "out;lib/jocl-2.0.4.jar" br.com.paralelismo.Main --search amostras/Dracula-165307.txt blood [threads]
 */
public class Main {

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);

        Path baseDir = Paths.get("").toAbsolutePath();
        Path samplesDir = baseDir.resolve("amostras");
        Path resultsDir = baseDir.resolve("resultados");
        Path chartsDir = baseDir.resolve("graficos");

        if (args.length >= 3 && "--search".equalsIgnoreCase(args[0])) {
            Path filePath = Paths.get(args[1]);
            String word = args[2];
            int threads = (args.length >= 4)
                    ? Integer.parseInt(args[3])
                    : Runtime.getRuntime().availableProcessors();
            runSingleSearch(filePath, word, threads);
            return;
        }

        boolean openGui = false;
        for (String arg : args) {
            if ("--gui".equalsIgnoreCase(arg)) {
                openGui = true;
            }
        }
        // Sem argumentos: executa benchmark completo + gera CSV + gera PNGs (e abre Swing se ambiente não for headless)
        if (args.length == 0) {
            openGui = !java.awt.GraphicsEnvironment.isHeadless();
        }

        BenchmarkRunner runner = new BenchmarkRunner(samplesDir, resultsDir);
        BenchmarkRunner.BenchmarkSuiteResult suite = runner.runAll();

        List<ChartGenerator.NamedChartPanel> chartPanels = ChartGenerator.buildAndExportAll(suite, chartsDir);
        System.out.println("Gráficos PNG gerados com sucesso em: " + chartsDir.toAbsolutePath());

        if (openGui) {
            System.out.println("Abrindo painel gráfico Java Swing...");
            DashboardSwing.showAsync(chartPanels, samplesDir, suite.gpuSummary);
        }
    }

    private static void runSingleSearch(Path filePath, String word, int threads) throws Exception {
        System.out.printf("Buscando palavra '%s' em: %s (threads CPU = %d)%n%n", word, filePath, threads);

        SerialCPU serial = new SerialCPU();
        ParallelCPU parallelCpu = new ParallelCPU(threads);

        SearchResult rSer = serial.countWord(filePath, word, 1);
        SearchResult rCpu = parallelCpu.countWord(filePath, word, 1);
        SearchResult rGpuWarm;
        try (ParallelGPU gpu = new ParallelGPU()) {
            gpu.initialize();
            rGpuWarm = gpu.countWord(filePath, word, 1);
        }
        SearchResult rGpuCold = ParallelGPU.countWordColdStart(filePath, word, 1);

        System.out.printf("SerialCPU:          %d ocorrências em %.2f ms%n", rSer.getOccurrences(), rSer.getTotalTimeMs());
        System.out.printf("ParallelCPU (%dT):  %d ocorrências em %.2f ms%n", threads, rCpu.getOccurrences(), rCpu.getTotalTimeMs());
        System.out.printf("ParallelGPU (Warm): %d ocorrências em %.2f ms (transfer=%.2f ms, kernel=%.2f ms)%n",
                rGpuWarm.getOccurrences(), rGpuWarm.getTotalTimeMs(), rGpuWarm.getSetupTimeMs(), rGpuWarm.getComputeTimeMs());
        System.out.printf("ParallelGPU (Cold): %d ocorrências em %.2f ms (init+transfer=%.2f ms, kernel=%.2f ms)%n",
                rGpuCold.getOccurrences(), rGpuCold.getTotalTimeMs(), rGpuCold.getSetupTimeMs(), rGpuCold.getComputeTimeMs());
    }
}
