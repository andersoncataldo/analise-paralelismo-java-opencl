package br.com.paralelismo.algorithms;

import br.com.paralelismo.model.SearchResult;
import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jocl.CL.*;

/**
 * Método ParallelGPU - Versão paralela na GPU utilizando OpenCL (JOCL 2.0.4).
 *
 * Estratégia do Kernel OpenCL:
 * - Cada work-item percorre posições do texto em padrão grid-stride (coalesced memory access).
 * - Verifica fronteira inicial e final de palavra (exatamente a mesma semântica de SerialCPU/ParallelCPU)
 *   para evitar falsos positivos por substrings (ex: "the" dentro de "there").
 * - Acumula contagens em registrador privado e realiza redução paralela em memória local (__local)
 *   por work-group, gravando apenas 1 inteiro por work-group na memória global (sem contenção atômica).
 */
public class ParallelGPU implements AutoCloseable {

    private static final String KERNEL_SOURCE =
            "inline int isWordChar(ushort c) {\n" +
            "    return (c >= 'a' && c <= 'z') ||\n" +
            "           (c >= 'A' && c <= 'Z') ||\n" +
            "           (c >= '0' && c <= '9') ||\n" +
            "           (c >= 0x00C0 && c <= 0x00FF && c != 0x00D7 && c != 0x00F7);\n" +
            "}\n" +
            "\n" +
            "inline ushort toLowerChar(ushort c) {\n" +
            "    if (c >= 'A' && c <= 'Z') return (ushort)(c + 32);\n" +
            "    if (c >= 0x00C0 && c <= 0x00DE && c != 0x00D7) return (ushort)(c + 32);\n" +
            "    return c;\n" +
            "}\n" +
            "\n" +
            "__kernel void countWordOccurrences(\n" +
            "        __global const ushort* text,\n" +
            "        const int textLen,\n" +
            "        __global const ushort* word,\n" +
            "        const int wordLen,\n" +
            "        __global uint* partialCounts,\n" +
            "        __local uint* localCounts) {\n" +
            "    const int gid = get_global_id(0);\n" +
            "    const int gsize = get_global_size(0);\n" +
            "    const int lid = get_local_id(0);\n" +
            "    const int lsize = get_local_size(0);\n" +
            "\n" +
            "    uint myCount = 0;\n" +
            "    const int limit = textLen - wordLen;\n" +
            "\n" +
            "    for (int idx = gid; idx <= limit; idx += gsize) {\n" +
            "        // Verifica se idx é início de palavra\n" +
            "        if (idx > 0 && isWordChar(text[idx - 1])) {\n" +
            "            continue;\n" +
            "        }\n" +
            "        // Comparação rápida do primeiro caractere\n" +
            "        if (toLowerChar(text[idx]) != word[0]) {\n" +
            "            continue;\n" +
            "        }\n" +
            "        // Verifica fronteira final da palavra\n" +
            "        int endIdx = idx + wordLen;\n" +
            "        if (endIdx < textLen && isWordChar(text[endIdx])) {\n" +
            "            continue;\n" +
            "        }\n" +
            "        // Compara demais caracteres\n" +
            "        int match = 1;\n" +
            "        for (int j = 1; j < wordLen; j++) {\n" +
            "            if (toLowerChar(text[idx + j]) != word[j]) {\n" +
            "                match = 0;\n" +
            "                break;\n" +
            "            }\n" +
            "        }\n" +
            "        if (match) {\n" +
            "            myCount++;\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    localCounts[lid] = myCount;\n" +
            "    barrier(CLK_LOCAL_MEM_FENCE);\n" +
            "\n" +
            "    // Redução em árvore na memória local do work-group\n" +
            "    for (int stride = lsize >> 1; stride > 0; stride >>= 1) {\n" +
            "        if (lid < stride) {\n" +
            "            localCounts[lid] += localCounts[lid + stride];\n" +
            "        }\n" +
            "        barrier(CLK_LOCAL_MEM_FENCE);\n" +
            "    }\n" +
            "\n" +
            "    if (lid == 0) {\n" +
            "        partialCounts[get_group_id(0)] = localCounts[0];\n" +
            "    }\n" +
            "}\n";

    private cl_platform_id platform;
    private cl_device_id device;
    private cl_context context;
    private cl_command_queue commandQueue;
    private cl_program program;
    private cl_kernel kernel;

    private String platformName;
    private String deviceName;
    private int maxComputeUnits;
    private long maxWorkGroupSize;
    private long globalMemBytes;
    private double initTimeMs;
    private boolean initialized;

    public ParallelGPU() {
        this.initialized = false;
    }

    /**
     * Inicializa o ambiente OpenCL (seleção de GPU, contexto, fila e compilação do kernel).
     */
    public void initialize() {
        if (initialized) {
            return;
        }
        long startInit = System.nanoTime();
        CL.setExceptionsEnabled(true);

        int[] numPlatformsArray = new int[1];
        clGetPlatformIDs(0, null, numPlatformsArray);
        if (numPlatformsArray[0] == 0) {
            throw new IllegalStateException("Nenhuma plataforma OpenCL encontrada neste sistema.");
        }
        cl_platform_id[] platforms = new cl_platform_id[numPlatformsArray[0]];
        clGetPlatformIDs(platforms.length, platforms, null);

        // Prioriza dispositivo do tipo GPU; usa fallback para ALL caso não exista GPU OpenCL
        cl_platform_id selectedPlatform = null;
        cl_device_id selectedDevice = null;

        for (cl_platform_id p : platforms) {
            int[] numDevices = new int[1];
            try {
                clGetDeviceIDs(p, CL_DEVICE_TYPE_GPU, 0, null, numDevices);
                if (numDevices[0] > 0) {
                    cl_device_id[] devices = new cl_device_id[numDevices[0]];
                    clGetDeviceIDs(p, CL_DEVICE_TYPE_GPU, numDevices[0], devices, null);
                    selectedPlatform = p;
                    selectedDevice = devices[0];
                    break;
                }
            } catch (Exception ignored) {
                // Tenta a próxima plataforma
            }
        }

        if (selectedDevice == null) {
            for (cl_platform_id p : platforms) {
                int[] numDevices = new int[1];
                try {
                    clGetDeviceIDs(p, CL_DEVICE_TYPE_ALL, 0, null, numDevices);
                    if (numDevices[0] > 0) {
                        cl_device_id[] devices = new cl_device_id[numDevices[0]];
                        clGetDeviceIDs(p, CL_DEVICE_TYPE_ALL, numDevices[0], devices, null);
                        selectedPlatform = p;
                        selectedDevice = devices[0];
                        break;
                    }
                } catch (Exception ignored) {
                    // Tenta a próxima plataforma
                }
            }
        }

        if (selectedDevice == null) {
            throw new IllegalStateException("Nenhum dispositivo OpenCL disponível.");
        }

        this.platform = selectedPlatform;
        this.device = selectedDevice;
        this.platformName = getPlatformString(platform, CL_PLATFORM_NAME);
        this.deviceName = getDeviceString(device, CL_DEVICE_NAME);

        int[] cu = new int[1];
        clGetDeviceInfo(device, CL_DEVICE_MAX_COMPUTE_UNITS, Sizeof.cl_int, Pointer.to(cu), null);
        this.maxComputeUnits = cu[0];

        long[] wg = new long[1];
        clGetDeviceInfo(device, CL_DEVICE_MAX_WORK_GROUP_SIZE, Sizeof.size_t, Pointer.to(wg), null);
        this.maxWorkGroupSize = wg[0];

        long[] mem = new long[1];
        clGetDeviceInfo(device, CL_DEVICE_GLOBAL_MEM_SIZE, Sizeof.cl_long, Pointer.to(mem), null);
        this.globalMemBytes = mem[0];

        cl_context_properties contextProperties = new cl_context_properties();
        contextProperties.addProperty(CL_CONTEXT_PLATFORM, platform);
        this.context = clCreateContext(contextProperties, 1, new cl_device_id[]{device}, null, null, null);
        this.commandQueue = clCreateCommandQueue(context, device, 0, null);

        this.program = clCreateProgramWithSource(context, 1, new String[]{KERNEL_SOURCE}, null, null);
        clBuildProgram(program, 0, null, null, null, null);
        this.kernel = clCreateKernel(program, "countWordOccurrences", null);

        this.initTimeMs = (System.nanoTime() - startInit) / 1_000_000.0;
        this.initialized = true;
    }

    /**
     * Executa a contagem na GPU a partir de um arquivo .txt com inicialização completa
     * (Cold Start - cria e libera o contexto OpenCL por execução).
     */
    public static SearchResult countWordColdStart(Path filePath, String targetWord, int sampleIndex) throws IOException {
        long startTotal = System.nanoTime();
        long fileBytes = Files.size(filePath);

        long startIo = System.nanoTime();
        char[] text = TextUtils.readFileChars(filePath);
        long endIo = System.nanoTime();

        try (ParallelGPU gpu = new ParallelGPU()) {
            long startInit = System.nanoTime();
            gpu.initialize();
            long endInit = System.nanoTime();

            GpuRunMetrics metrics = gpu.executeKernel(text, TextUtils.normalizeWord(targetWord), 256);
            long endTotal = System.nanoTime();

            return new SearchResult(
                    "ParallelGPU (Cold)",
                    filePath.getFileName().toString(),
                    fileBytes,
                    text.length,
                    targetWord,
                    gpu.getMaxComputeUnits(),
                    sampleIndex,
                    metrics.count,
                    (endTotal - startTotal) / 1_000_000.0,
                    (endIo - startIo) / 1_000_000.0,
                    (endInit - startInit) / 1_000_000.0 + metrics.transferMs,
                    metrics.kernelMs,
                    String.format("Cold start (init+build=%.2fms, transfer=%.2fms, kernel=%.2fms) em %s",
                            (endInit - startInit) / 1_000_000.0, metrics.transferMs, metrics.kernelMs, gpu.getDeviceName())
            );
        }
    }

    /**
     * Executa a contagem na GPU a partir de um arquivo .txt reutilizando o contexto/kernel
     * OpenCL já inicializado (Warm Context).
     */
    public SearchResult countWord(Path filePath, String targetWord, int sampleIndex) throws IOException {
        if (!initialized) {
            initialize();
        }
        long startTotal = System.nanoTime();
        long fileBytes = Files.size(filePath);

        long startIo = System.nanoTime();
        char[] text = TextUtils.readFileChars(filePath);
        long endIo = System.nanoTime();

        GpuRunMetrics metrics = executeKernel(text, TextUtils.normalizeWord(targetWord), 256);
        long endTotal = System.nanoTime();

        return new SearchResult(
                "ParallelGPU",
                filePath.getFileName().toString(),
                fileBytes,
                text.length,
                targetWord,
                maxComputeUnits,
                sampleIndex,
                metrics.count,
                (endTotal - startTotal) / 1_000_000.0,
                (endIo - startIo) / 1_000_000.0,
                metrics.transferMs,
                metrics.kernelMs,
                String.format("Warm OpenCL (transfer=%.2fms, kernel=%.2fms) em %s",
                        metrics.transferMs, metrics.kernelMs, deviceName)
        );
    }

    /**
     * Executa a contagem na GPU sobre texto já em memória (permite isolar transferência PCIe vs kernel).
     */
    public SearchResult countWordInMemory(
            String datasetName,
            long datasetBytes,
            char[] text,
            String targetWord,
            int sampleIndex,
            int localWorkSize) {
        if (!initialized) {
            initialize();
        }
        GpuRunMetrics metrics = executeKernel(text, TextUtils.normalizeWord(targetWord), localWorkSize);

        return new SearchResult(
                "ParallelGPU",
                datasetName,
                datasetBytes,
                text.length,
                targetWord,
                maxComputeUnits,
                sampleIndex,
                metrics.count,
                metrics.transferMs + metrics.kernelMs,
                0.0,
                metrics.transferMs,
                metrics.kernelMs,
                String.format("LocalWG=%d, Groups=%d, transfer=%.2fms, kernel=%.2fms",
                        metrics.localSize, metrics.numGroups, metrics.transferMs, metrics.kernelMs)
        );
    }

    private GpuRunMetrics executeKernel(char[] text, char[] word, int requestedLocalSize) {
        int textLen = text.length;
        int wordLen = word.length;
        if (textLen < wordLen) {
            return new GpuRunMetrics(0, 0.0, 0.0, requestedLocalSize, 0);
        }

        // Garante potência de 2 <= maxWorkGroupSize para redução em árvore correta
        int localSize = 1;
        int cap = (int) Math.min(requestedLocalSize, maxWorkGroupSize);
        while ((localSize << 1) <= cap) {
            localSize <<= 1;
        }

        // Quantidade de work-groups proporcional às Compute Units para alta ocupação da GPU
        int desiredGroups = Math.max(maxComputeUnits * 8, 64);
        int maxUsefulGroups = Math.max(1, (textLen + localSize - 1) / localSize);
        int numGroups = Math.min(desiredGroups, maxUsefulGroups);
        long globalWorkSize = (long) numGroups * localSize;

        // Envia diretamente char[] (UTF-16, 16 bits = OpenCL ushort) sem cópia intermediária em CPU
        long startPrepTransfer = System.nanoTime();
        int[] partialCounts = new int[numGroups];

        cl_mem textMem = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_ushort * textLen, Pointer.to(text), null);
        cl_mem wordMem = clCreateBuffer(context, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                (long) Sizeof.cl_ushort * wordLen, Pointer.to(word), null);
        cl_mem partialMem = clCreateBuffer(context, CL_MEM_WRITE_ONLY,
                (long) Sizeof.cl_uint * numGroups, null, null);
        long endH2D = System.nanoTime();

        try {
            clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(textMem));
            clSetKernelArg(kernel, 1, Sizeof.cl_int, Pointer.to(new int[]{textLen}));
            clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(wordMem));
            clSetKernelArg(kernel, 3, Sizeof.cl_int, Pointer.to(new int[]{wordLen}));
            clSetKernelArg(kernel, 4, Sizeof.cl_mem, Pointer.to(partialMem));
            clSetKernelArg(kernel, 5, (long) Sizeof.cl_uint * localSize, null);

            long startKernel = System.nanoTime();
            clEnqueueNDRangeKernel(
                    commandQueue,
                    kernel,
                    1,
                    null,
                    new long[]{globalWorkSize},
                    new long[]{localSize},
                    0,
                    null,
                    null
            );
            clFinish(commandQueue);
            long endKernel = System.nanoTime();

            long startD2H = System.nanoTime();
            clEnqueueReadBuffer(
                    commandQueue,
                    partialMem,
                    CL_TRUE,
                    0,
                    (long) Sizeof.cl_uint * numGroups,
                    Pointer.to(partialCounts),
                    0,
                    null,
                    null
            );
            long endD2H = System.nanoTime();

            long total = 0;
            for (int c : partialCounts) {
                total += Integer.toUnsignedLong(c);
            }

            double transferMs = ((endH2D - startPrepTransfer) + (endD2H - startD2H)) / 1_000_000.0;
            double kernelMs = (endKernel - startKernel) / 1_000_000.0;
            return new GpuRunMetrics(total, transferMs, kernelMs, localSize, numGroups);
        } finally {
            clReleaseMemObject(textMem);
            clReleaseMemObject(wordMem);
            clReleaseMemObject(partialMem);
        }
    }

    public String getPlatformName() { return platformName; }
    public String getDeviceName() { return deviceName; }
    public int getMaxComputeUnits() { return maxComputeUnits; }
    public long getMaxWorkGroupSize() { return maxWorkGroupSize; }
    public long getGlobalMemBytes() { return globalMemBytes; }
    public double getInitTimeMs() { return initTimeMs; }

    public String getDeviceSummary() {
        if (!initialized) {
            initialize();
        }
        return String.format("%s | %s | %d Compute Units | MaxWG=%d | GlobalMem=%d MB | Init/Build=%.2f ms",
                platformName, deviceName, maxComputeUnits, maxWorkGroupSize,
                globalMemBytes / (1024L * 1024L), initTimeMs);
    }

    @Override
    public void close() {
        if (initialized) {
            if (kernel != null) clReleaseKernel(kernel);
            if (program != null) clReleaseProgram(program);
            if (commandQueue != null) clReleaseCommandQueue(commandQueue);
            if (context != null) clReleaseContext(context);
            initialized = false;
        }
    }

    private static String getPlatformString(cl_platform_id p, int param) {
        long[] size = new long[1];
        clGetPlatformInfo(p, param, 0, null, size);
        byte[] buf = new byte[(int) size[0]];
        clGetPlatformInfo(p, param, buf.length, Pointer.to(buf), null);
        return new String(buf, 0, Math.max(0, buf.length - 1)).trim();
    }

    private static String getDeviceString(cl_device_id d, int param) {
        long[] size = new long[1];
        clGetDeviceInfo(d, param, 0, null, size);
        byte[] buf = new byte[(int) size[0]];
        clGetDeviceInfo(d, param, buf.length, Pointer.to(buf), null);
        return new String(buf, 0, Math.max(0, buf.length - 1)).trim();
    }

    private static class GpuRunMetrics {
        final long count;
        final double transferMs;
        final double kernelMs;
        final int localSize;
        final int numGroups;

        GpuRunMetrics(long count, double transferMs, double kernelMs, int localSize, int numGroups) {
            this.count = count;
            this.transferMs = transferMs;
            this.kernelMs = kernelMs;
            this.localSize = localSize;
            this.numGroups = numGroups;
        }
    }
}
