package br.com.paralelismo.algorithms;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utilitários compartilhados para garantir critério idêntico de delimitação e
 * normalização de palavras entre SerialCPU, ParallelCPU e ParallelGPU.
 */
public final class TextUtils {

    private TextUtils() {}

    /**
     * Lê um arquivo de texto UTF-8 completo para um array de caracteres.
     */
    public static char[] readFileChars(Path filePath) throws IOException {
        String content = Files.readString(filePath, StandardCharsets.UTF_8);
        return content.toCharArray();
    }

    /**
     * Normaliza a palavra de busca para minúsculas seguindo exatamente a mesma regra
     * implementada no kernel OpenCL.
     */
    public static char[] normalizeWord(String word) {
        if (word == null || word.isEmpty()) {
            throw new IllegalArgumentException("A palavra de busca não pode ser vazia.");
        }
        char[] chars = word.trim().toCharArray();
        for (int i = 0; i < chars.length; i++) {
            chars[i] = toLowerChar(chars[i]);
        }
        return chars;
    }

    /**
     * Define se um caractere pertence a uma palavra (letras ASCII, dígitos e letras
     * acentuadas do bloco Latin-1, como á, é, í, ó, ú, ñ, ç).
     * Regra espelhada exatamente no kernel OpenCL para contagem 100% consistente.
     */
    public static boolean isWordChar(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || (c >= 0x00C0 && c <= 0x00FF && c != 0x00D7 && c != 0x00F7);
    }

    /**
     * Converte letras maiúsculas ASCII e Latin-1 para minúsculas sem alocar objetos.
     */
    public static char toLowerChar(char c) {
        if (c >= 'A' && c <= 'Z') {
            return (char) (c + 32);
        }
        if (c >= 0x00C0 && c <= 0x00DE && c != 0x00D7) {
            return (char) (c + 32);
        }
        return c;
    }
}
