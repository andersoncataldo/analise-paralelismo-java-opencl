package br.com.paralelismo.model;

import java.nio.file.Path;

/**
 * Configuração de um conjunto de dados (arquivo texto + palavra alvo).
 */
public class DatasetConfig {
    private final String id;
    private final String displayName;
    private final Path filePath;
    private final String targetWord;
    private final String language;
    private final boolean synthetic;

    public DatasetConfig(String id, String displayName, Path filePath, String targetWord, String language, boolean synthetic) {
        this.id = id;
        this.displayName = displayName;
        this.filePath = filePath;
        this.targetWord = targetWord;
        this.language = language;
        this.synthetic = synthetic;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Path getFilePath() { return filePath; }
    public String getTargetWord() { return targetWord; }
    public String getLanguage() { return language; }
    public boolean isSynthetic() { return synthetic; }
}
