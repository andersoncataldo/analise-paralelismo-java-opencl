package br.com.paralelismo.algorithms;

import br.com.paralelismo.model.SearchResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Método ParallelCPU - Versão paralela na CPU.
 * Utiliza um pool de threads (ExecutorService) para dividir o texto em partições
 * alinhadas a fronteiras de palavras e contabilizar as ocorrências em paralelo.
 */
public class ParallelCPU {

    private final int numThreads;

    public ParallelCPU() {
        this(Runtime.getRuntime().availableProcessors());
    }

    public ParallelCPU(int numThreads) {
        if (numThreads < 1) {
            throw new IllegalArgumentException("Número de threads deve ser >= 1.");
        }
        this.numThreads = numThreads;
    }

    public int getNumThreads() {
        return numThreads;
    }

    /**
     * Executa a contagem paralela em CPU a partir de um arquivo .txt e uma palavra alvo.
     */
    public SearchResult countWord(Path filePath, String targetWord, int sampleIndex) throws IOException {
        long startTotal = System.nanoTime();
        long fileBytes = Files.size(filePath);

        long startIo = System.nanoTime();
        char[] text = TextUtils.readFileChars(filePath);
        long endIo = System.nanoTime();

        char[] word = TextUtils.normalizeWord(targetWord);

        long startSetup = System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        long endSetup = System.nanoTime();

        long count;
        long startCompute = System.nanoTime();
        try {
            count = executeParallelCount(pool, text, word, numThreads);
        } finally {
            pool.shutdown();
        }
        long endCompute = System.nanoTime();
        long endTotal = System.nanoTime();

        return new SearchResult(
                "ParallelCPU",
                filePath.getFileName().toString(),
                fileBytes,
                text.length,
                targetWord,
                numThreads,
                sampleIndex,
                count,
                (endTotal - startTotal) / 1_000_000.0,
                (endIo - startIo) / 1_000_000.0,
                (endSetup - startSetup) / 1_000_000.0,
                (endCompute - startCompute) / 1_000_000.0,
                "ExecutorService com " + numThreads + " thread(s)"
        );
    }

    /**
     * Executa a contagem paralela sobre um texto já carregado em memória utilizando
     * um pool de threads já inicializado (evita ruído de criação de threads do SO em
     * benchmarks de escalabilidade pura).
     */
    public SearchResult countWordInMemory(
            String datasetName,
            long datasetBytes,
            char[] text,
            String targetWord,
            int sampleIndex,
            ExecutorService sharedPool) {
        char[] word = TextUtils.normalizeWord(targetWord);

        boolean ownPool = (sharedPool == null);
        long startSetup = System.nanoTime();
        ExecutorService pool = ownPool ? Executors.newFixedThreadPool(numThreads) : sharedPool;
        long endSetup = System.nanoTime();

        long count;
        long startCompute = System.nanoTime();
        try {
            count = executeParallelCount(pool, text, word, numThreads);
        } finally {
            if (ownPool) {
                pool.shutdown();
            }
        }
        long endCompute = System.nanoTime();

        double setupMs = (endSetup - startSetup) / 1_000_000.0;
        double computeMs = (endCompute - startCompute) / 1_000_000.0;

        return new SearchResult(
                "ParallelCPU",
                datasetName,
                datasetBytes,
                text.length,
                targetWord,
                numThreads,
                sampleIndex,
                count,
                setupMs + computeMs,
                0.0,
                setupMs,
                computeMs,
                "Memória + pool com " + numThreads + " thread(s)"
        );
    }

    public SearchResult countWordInMemory(
            String datasetName,
            long datasetBytes,
            char[] text,
            String targetWord,
            int sampleIndex) {
        return countWordInMemory(datasetName, datasetBytes, text, targetWord, sampleIndex, null);
    }

    /**
     * Divide o vetor de caracteres em blocos alinhados a fronteiras de palavras
     * (evitando cortar uma palavra ao meio) e submete ao pool de threads.
     */
    private static long executeParallelCount(ExecutorService pool, char[] text, char[] word, int numTasks) {
        int len = text.length;
        if (len == 0) {
            return 0;
        }
        int effectiveTasks = Math.min(numTasks, Math.max(1, len / 1024));
        if (effectiveTasks <= 1) {
            return SerialCPU.countInRange(text, 0, len, word);
        }

        int[] boundaries = computeWordSafeBoundaries(text, effectiveTasks);
        List<Callable<Long>> tasks = new ArrayList<>(effectiveTasks);

        for (int t = 0; t < effectiveTasks; t++) {
            final int start = boundaries[t];
            final int end = boundaries[t + 1];
            tasks.add(() -> SerialCPU.countInRange(text, start, end, word));
        }

        long total = 0;
        try {
            List<Future<Long>> futures = pool.invokeAll(tasks);
            for (Future<Long> f : futures) {
                total += f.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Execução paralela interrompida.", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Erro durante contagem paralela na CPU.", e.getCause());
        }
        return total;
    }

    /**
     * Calcula fronteiras [b0, b1, ..., bk] ajustando cada corte para fora de palavras,
     * garantindo que cada palavra pertença exclusivamente a uma única partição.
     */
    private static int[] computeWordSafeBoundaries(char[] text, int partitions) {
        int len = text.length;
        int[] bounds = new int[partitions + 1];
        bounds[0] = 0;
        bounds[partitions] = len;

        int chunkSize = len / partitions;
        for (int i = 1; i < partitions; i++) {
            int pos = Math.max(bounds[i - 1], i * chunkSize);
            // Se caiu no meio de uma palavra, avança até o delimitador seguinte
            while (pos < len && TextUtils.isWordChar(text[pos])) {
                pos++;
            }
            bounds[i] = pos;
        }
        return bounds;
    }
}
