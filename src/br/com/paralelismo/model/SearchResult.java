package br.com.paralelismo.model;

/**
 * Representa o resultado de uma execução de busca e contagem de palavras.
 */
public class SearchResult {
    private final String algorithm;
    private final String datasetName;
    private final long datasetBytes;
    private final int datasetChars;
    private final String targetWord;
    private final int threadsOrUnits;
    private final int sampleIndex;
    private final long occurrences;
    private final double totalTimeMs;
    private final double ioTimeMs;
    private final double setupTimeMs;
    private final double computeTimeMs;
    private final String notes;

    public SearchResult(
            String algorithm,
            String datasetName,
            long datasetBytes,
            int datasetChars,
            String targetWord,
            int threadsOrUnits,
            int sampleIndex,
            long occurrences,
            double totalTimeMs,
            double ioTimeMs,
            double setupTimeMs,
            double computeTimeMs,
            String notes) {
        this.algorithm = algorithm;
        this.datasetName = datasetName;
        this.datasetBytes = datasetBytes;
        this.datasetChars = datasetChars;
        this.targetWord = targetWord;
        this.threadsOrUnits = threadsOrUnits;
        this.sampleIndex = sampleIndex;
        this.occurrences = occurrences;
        this.totalTimeMs = totalTimeMs;
        this.ioTimeMs = ioTimeMs;
        this.setupTimeMs = setupTimeMs;
        this.computeTimeMs = computeTimeMs;
        this.notes = notes == null ? "" : notes;
    }

    public String getAlgorithm() { return algorithm; }
    public String getDatasetName() { return datasetName; }
    public long getDatasetBytes() { return datasetBytes; }
    public int getDatasetChars() { return datasetChars; }
    public String getTargetWord() { return targetWord; }
    public int getThreadsOrUnits() { return threadsOrUnits; }
    public int getSampleIndex() { return sampleIndex; }
    public long getOccurrences() { return occurrences; }
    public double getTotalTimeMs() { return totalTimeMs; }
    public double getIoTimeMs() { return ioTimeMs; }
    public double getSetupTimeMs() { return setupTimeMs; }
    public double getComputeTimeMs() { return computeTimeMs; }
    public String getNotes() { return notes; }

    public String formatShort() {
        return String.format("%s: %d ocorrências em %.2f ms (cômputo: %.2f ms)",
                algorithm, occurrences, totalTimeMs, computeTimeMs);
    }

    @Override
    public String toString() {
        return String.format("%s [%s | palavra='%s' | threads/CUs=%d | amostra=%d]: %d ocorrências em %.2f ms (I/O: %.2f ms, setup: %.2f ms, cômputo: %.2f ms)",
                algorithm, datasetName, targetWord, threadsOrUnits, sampleIndex,
                occurrences, totalTimeMs, ioTimeMs, setupTimeMs, computeTimeMs);
    }
}
