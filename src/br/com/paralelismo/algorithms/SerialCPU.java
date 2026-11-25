package br.com.paralelismo.algorithms;

import br.com.paralelismo.model.SearchResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Método SerialCPU - Versão serial na CPU.
 * Percorre o texto com um loop simples palavra a palavra e conta as ocorrências exatas
 * (case-insensitive) da palavra informada.
 */
public class SerialCPU {

    /**
     * Executa a contagem serial a partir de um arquivo .txt e uma palavra alvo.
     */
    public SearchResult countWord(Path filePath, String targetWord, int sampleIndex) throws IOException {
        long startTotal = System.nanoTime();
        long fileBytes = Files.size(filePath);

        long startIo = System.nanoTime();
        char[] text = TextUtils.readFileChars(filePath);
        long endIo = System.nanoTime();

        char[] word = TextUtils.normalizeWord(targetWord);

        long startCompute = System.nanoTime();
        long count = countInRange(text, 0, text.length, word);
        long endCompute = System.nanoTime();

        long endTotal = System.nanoTime();

        return new SearchResult(
                "SerialCPU",
                filePath.getFileName().toString(),
                fileBytes,
                text.length,
                targetWord,
                1,
                sampleIndex,
                count,
                (endTotal - startTotal) / 1_000_000.0,
                (endIo - startIo) / 1_000_000.0,
                0.0,
                (endCompute - startCompute) / 1_000_000.0,
                "Execução sequencial single-thread"
        );
    }

    /**
     * Executa a contagem serial sobre um buffer de texto já carregado em memória.
     */
    public SearchResult countWordInMemory(
            String datasetName,
            long datasetBytes,
            char[] text,
            String targetWord,
            int sampleIndex) {
        char[] word = TextUtils.normalizeWord(targetWord);

        long startCompute = System.nanoTime();
        long count = countInRange(text, 0, text.length, word);
        long endCompute = System.nanoTime();

        double computeMs = (endCompute - startCompute) / 1_000_000.0;
        return new SearchResult(
                "SerialCPU",
                datasetName,
                datasetBytes,
                text.length,
                targetWord,
                1,
                sampleIndex,
                count,
                computeMs,
                0.0,
                0.0,
                computeMs,
                "Processamento em memória (sem I/O)"
        );
    }

    /**
     * Loop simples que itera sobre cada palavra no intervalo [start, end) e contabiliza
     * as ocorrências exatas da palavra alvo.
     */
    public static long countInRange(char[] text, int start, int end, char[] word) {
        int wordLen = word.length;
        long count = 0;
        int i = start;

        while (i < end) {
            // Avança até o início da próxima palavra
            while (i < end && !TextUtils.isWordChar(text[i])) {
                i++;
            }
            if (i >= end) {
                break;
            }

            int wordStart = i;
            // Avança até o final da palavra atual
            while (i < end && TextUtils.isWordChar(text[i])) {
                i++;
            }

            int currentLen = i - wordStart;
            if (currentLen == wordLen) {
                boolean match = true;
                for (int j = 0; j < wordLen; j++) {
                    if (TextUtils.toLowerChar(text[wordStart + j]) != word[j]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    count++;
                }
            }
        }
        return count;
    }
}
