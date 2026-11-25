package br.com.paralelismo.ui;

import br.com.paralelismo.benchmark.BenchmarkRunner;
import br.com.paralelismo.benchmark.CsvExporter;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gerador de gráficos em Java Swing / Graphics2D.
 * Produz componentes JPanel reutilizáveis tanto para exibição na interface gráfica Swing
 * quanto para exportação direta em PNG de alta resolução.
 */
public final class ChartGenerator {

    private static final Color BG_COLOR = new Color(250, 251, 253);
    private static final Color PANEL_COLOR = Color.WHITE;
    private static final Color GRID_COLOR = new Color(226, 232, 240);
    private static final Color AXIS_COLOR = new Color(100, 116, 139);
    private static final Color TEXT_DARK = new Color(15, 23, 42);
    private static final Color TEXT_MUTED = new Color(71, 85, 105);

    private static final Color[] PALETTE = {
            new Color(37, 99, 235),   // Azul Royal (SerialCPU)
            new Color(16, 185, 129),  // Esmeralda (ParallelCPU 4T)
            new Color(5, 150, 105),   // Verde Escuro (ParallelCPU 8T)
            new Color(245, 158, 11),  // Âmbar (ParallelGPU Warm)
            new Color(225, 29, 72),   // Carmesim (ParallelGPU Cold / Kernel)
            new Color(124, 58, 237)   // Violeta
    };

    private static final Font FONT_TITLE = new Font("SansSerif", Font.BOLD, 20);
    private static final Font FONT_SUBTITLE = new Font("SansSerif", Font.PLAIN, 13);
    private static final Font FONT_AXIS_TITLE = new Font("SansSerif", Font.BOLD, 13);
    private static final Font FONT_TICK = new Font("SansSerif", Font.PLAIN, 12);
    private static final Font FONT_VALUE = new Font("SansSerif", Font.BOLD, 11);
    private static final Font FONT_LEGEND = new Font("SansSerif", Font.PLAIN, 12);

    private ChartGenerator() {}

    /**
     * Exporta todos os 5 gráficos da análise comparativa para arquivos PNG.
     */
    public static List<NamedChartPanel> buildAndExportAll(
            BenchmarkRunner.BenchmarkSuiteResult suite,
            Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        List<NamedChartPanel> panels = new ArrayList<>();
        panels.add(new NamedChartPanel(
                "01_comparativo_algoritmos_amostras.png",
                "1. Comparativo Amostras",
                createExp1BarChart(suite.exp1Rows)
        ));
        panels.add(new NamedChartPanel(
                "02_escalabilidade_nucleos_cpu.png",
                "2. Escalabilidade CPU (Threads)",
                createExp2CpuThreadsChart(suite.exp2Rows)
        ));
        panels.add(new NamedChartPanel(
                "03_speedup_eficiencia_cpu.png",
                "3. Speedup e Eficiência",
                createExp2SpeedupEfficiencyChart(suite.exp2Rows)
        ));
        panels.add(new NamedChartPanel(
                "04_impacto_volume_cpu_vs_gpu.png",
                "4. Volume de Dados (CPU vs GPU)",
                createExp3VolumeChart(suite.exp3Rows)
        ));
        panels.add(new NamedChartPanel(
                "05_decomposicao_tempo_gpu.png",
                "5. Decomposição GPU (Overhead)",
                createExp1GpuBreakdownChart(suite.exp1Rows)
        ));

        for (NamedChartPanel item : panels) {
            exportPanelToPng(item.panel, outputDir.resolve(item.fileName), 1200, 680);
        }
        return panels;
    }

    public static void exportPanelToPng(JPanel panel, Path outFile, int width, int height) throws IOException {
        panel.setSize(width, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        applyHints(g2);
        panel.paint(g2);
        g2.dispose();
        ImageIO.write(image, "png", outFile.toFile());
    }

    // -------------------------------------------------------------------------
    // GRÁFICO 1: Barras agrupadas - Tempo médio nas 3 amostras (End-to-End Warm)
    // -------------------------------------------------------------------------
    public static JPanel createExp1BarChart(List<CsvExporter.SummaryRow> exp1Rows) {
        List<String> categories = new ArrayList<>();
        String[] seriesNames = {"SerialCPU", "ParallelCPU (4T)", "ParallelCPU (8T)", "ParallelGPU (Warm)"};
        Map<String, double[]> data = new LinkedHashMap<>();

        for (CsvExporter.SummaryRow r : exp1Rows) {
            if (!categories.contains(r.dataset)) {
                categories.add(r.dataset);
                data.put(r.dataset, new double[seriesNames.length]);
            }
            for (int s = 0; s < seriesNames.length; s++) {
                if (r.algorithm.equals(seriesNames[s])) {
                    data.get(r.dataset)[s] = r.meanTotalMs;
                }
            }
        }

        // Busca tempo médio do Cold Start para nota explicativa no rodapé
        double avgCold = 0;
        int coldCount = 0;
        for (CsvExporter.SummaryRow r : exp1Rows) {
            if (r.algorithm.equals("ParallelGPU (Cold)")) {
                avgCold += r.meanTotalMs;
                coldCount++;
            }
        }
        String note = coldCount > 0
                ? String.format(Locale.US,
                "Nota: ParallelGPU (Cold Start com compilação OpenCL) levou em média %.1f ms (exibido no Gráfico 5 para preservar escala).",
                avgCold / coldCount)
                : "";

        return new GroupedBarChartPanel(
                "Desempenho Comparativo nas 3 Amostras Originais (End-to-End com I/O)",
                "Tempo médio de 5 execuções por algoritmo (menor é melhor)",
                "Tempo Médio (ms)",
                categories,
                seriesNames,
                data,
                new Color[]{PALETTE[0], PALETTE[1], PALETTE[2], PALETTE[3]},
                note
        );
    }

    // -------------------------------------------------------------------------
    // GRÁFICO 2: Linhas - Escalabilidade ParallelCPU por número de threads
    // -------------------------------------------------------------------------
    public static JPanel createExp2CpuThreadsChart(List<CsvExporter.SummaryRow> exp2Rows) {
        List<String> xLabels = new ArrayList<>();
        for (int t : BenchmarkRunner.CPU_THREAD_COUNTS) {
            xLabels.add(t + (t == 1 ? " Thread" : " Threads"));
        }

        Map<String, List<Double>> series = new LinkedHashMap<>();
        for (CsvExporter.SummaryRow r : exp2Rows) {
            series.computeIfAbsent(r.dataset, k -> new ArrayList<>()).add(r.meanComputeMs);
        }

        return new MultiLineChartPanel(
                "Escalabilidade do ParallelCPU por Número de Threads (Processamento em Memória)",
                "Tempo médio de cômputo (ms) variando de 1 a 16 threads em CPU 4 núcleos físicos / 8 lógicos",
                "Número de Threads",
                "Tempo Médio de Cômputo (ms)",
                xLabels,
                series,
                new Color[]{PALETTE[0], PALETTE[1], PALETTE[4]},
                "Observação: ganho acentuado até 4 threads (núcleos físicos); de 8 para 16 threads ocorre saturação/oversubscription."
        );
    }

    // -------------------------------------------------------------------------
    // GRÁFICO 3: Painel duplo - Speedup e Eficiência Paralela (CPU)
    // -------------------------------------------------------------------------
    public static JPanel createExp2SpeedupEfficiencyChart(List<CsvExporter.SummaryRow> exp2Rows) {
        return new SpeedupEfficiencyPanel(exp2Rows);
    }

    // -------------------------------------------------------------------------
    // GRÁFICO 4: Linhas - Impacto do volume de dados (0.87 MB até ~44 MB)
    // -------------------------------------------------------------------------
    public static JPanel createExp3VolumeChart(List<CsvExporter.SummaryRow> exp3Rows) {
        List<String> xLabels = new ArrayList<>();
        Map<String, List<Double>> series = new LinkedHashMap<>();
        series.put("SerialCPU", new ArrayList<>());
        series.put("ParallelCPU (4T)", new ArrayList<>());
        series.put("ParallelCPU (8T)", new ArrayList<>());
        series.put("ParallelGPU (Transfer + Kernel)", new ArrayList<>());
        series.put("ParallelGPU (Somente Kernel)", new ArrayList<>());

        for (CsvExporter.SummaryRow r : exp3Rows) {
            String xLabel = String.format(Locale.US, "%.1f MB", r.sizeMb);
            if (!xLabels.contains(xLabel)) {
                xLabels.add(xLabel);
            }
            if (r.algorithm.equals("SerialCPU")) {
                series.get("SerialCPU").add(r.meanComputeMs);
            } else if (r.algorithm.equals("ParallelCPU (4T)")) {
                series.get("ParallelCPU (4T)").add(r.meanComputeMs);
            } else if (r.algorithm.equals("ParallelCPU (8T)")) {
                series.get("ParallelCPU (8T)").add(r.meanComputeMs);
            } else if (r.algorithm.equals("ParallelGPU (Warm)")) {
                series.get("ParallelGPU (Transfer + Kernel)").add(r.meanTotalMs);
                series.get("ParallelGPU (Somente Kernel)").add(r.meanComputeMs);
            }
        }

        return new MultiLineChartPanel(
                "Impacto do Volume de Dados: CPU Serial vs CPU Paralela vs GPU OpenCL",
                "Tempo médio (ms) em memória de 0.9 MB até 42 MB — separando custo total da GPU e execução pura do kernel",
                "Tamanho do Texto de Entrada (MB)",
                "Tempo Médio (ms)",
                xLabels,
                series,
                new Color[]{PALETTE[0], PALETTE[1], PALETTE[2], PALETTE[3], PALETTE[4]},
                "Insight: o kernel GPU puro é altamente paralelo, mas o custo de cópia Host->Device domina o tempo total em textos pequenos."
        );
    }

    // -------------------------------------------------------------------------
    // GRÁFICO 5: Decomposição do tempo GPU (I/O, Transferência, Kernel e Cold Start)
    // -------------------------------------------------------------------------
    public static JPanel createExp1GpuBreakdownChart(List<CsvExporter.SummaryRow> exp1Rows) {
        return new GpuBreakdownPanel(exp1Rows);
    }

    // =========================================================================
    // COMPONENTES SWING DE PLOTAGEM (Graphics2D)
    // =========================================================================

    private static void applyHints(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private static double niceCeiling(double maxVal) {
        if (maxVal <= 0) return 10.0;
        double exp = Math.floor(Math.log10(maxVal));
        double mag = Math.pow(10, exp);
        double norm = maxVal / mag;
        double nice;
        if (norm <= 1.2) nice = 1.2;
        else if (norm <= 1.5) nice = 1.5;
        else if (norm <= 2.0) nice = 2.0;
        else if (norm <= 2.5) nice = 2.5;
        else if (norm <= 3.0) nice = 3.0;
        else if (norm <= 4.0) nice = 4.0;
        else if (norm <= 5.0) nice = 5.0;
        else if (norm <= 6.0) nice = 6.0;
        else if (norm <= 8.0) nice = 8.0;
        else nice = 10.0;
        return nice * mag;
    }

    private static String formatNumber(double v) {
        if (v >= 100) return String.format(Locale.US, "%.0f", v);
        if (v >= 10) return String.format(Locale.US, "%.1f", v);
        return String.format(Locale.US, "%.2f", v);
    }

    private static void drawHeader(Graphics2D g2, int width, String title, String subtitle) {
        g2.setColor(TEXT_DARK);
        g2.setFont(FONT_TITLE);
        FontMetrics fmT = g2.getFontMetrics();
        g2.drawString(title, (width - fmT.stringWidth(title)) / 2, 36);

        g2.setColor(TEXT_MUTED);
        g2.setFont(FONT_SUBTITLE);
        FontMetrics fmS = g2.getFontMetrics();
        g2.drawString(subtitle, (width - fmS.stringWidth(subtitle)) / 2, 58);
    }

    private static void drawFooter(Graphics2D g2, int width, int height, String note) {
        if (note == null || note.isEmpty()) return;
        g2.setColor(TEXT_MUTED);
        g2.setFont(new Font("SansSerif", Font.ITALIC, 11));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(note, (width - fm.stringWidth(note)) / 2, height - 16);
    }

    private static void drawLegend(Graphics2D g2, int width, int y, String[] names, Color[] colors) {
        g2.setFont(FONT_LEGEND);
        FontMetrics fm = g2.getFontMetrics();
        int totalW = 0;
        for (String name : names) {
            totalW += 16 + fm.stringWidth(name) + 22;
        }
        int x = Math.max(20, (width - totalW) / 2);
        for (int i = 0; i < names.length; i++) {
            g2.setColor(colors[i % colors.length]);
            g2.fill(new Rectangle2D.Double(x, y - 10, 12, 12));
            g2.setColor(TEXT_DARK);
            g2.drawString(names[i], x + 17, y);
            x += 17 + fm.stringWidth(names[i]) + 20;
        }
    }

    /**
     * Painel de barras agrupadas.
     */
    private static class GroupedBarChartPanel extends JPanel {
        private final String title, subtitle, yLabel, footerNote;
        private final List<String> categories;
        private final String[] seriesNames;
        private final Map<String, double[]> data;
        private final Color[] colors;

        GroupedBarChartPanel(String title, String subtitle, String yLabel,
                             List<String> categories, String[] seriesNames,
                             Map<String, double[]> data, Color[] colors, String footerNote) {
            this.title = title;
            this.subtitle = subtitle;
            this.yLabel = yLabel;
            this.categories = categories;
            this.seriesNames = seriesNames;
            this.data = data;
            this.colors = colors;
            this.footerNote = footerNote;
            setBackground(BG_COLOR);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            applyHints(g2);

            int w = getWidth();
            int h = getHeight();
            g2.setColor(BG_COLOR);
            g2.fillRect(0, 0, w, h);

            drawHeader(g2, w, title, subtitle);
            drawLegend(g2, w, 88, seriesNames, colors);

            int padLeft = 86, padRight = 40, padTop = 108, padBottom = 80;
            int plotW = w - padLeft - padRight;
            int plotH = h - padTop - padBottom;

            g2.setColor(PANEL_COLOR);
            g2.fillRect(padLeft, padTop, plotW, plotH);

            double maxVal = 0;
            for (double[] arr : data.values()) {
                for (double v : arr) maxVal = Math.max(maxVal, v);
            }
            double yMax = niceCeiling(maxVal * 1.15);

            // Grid Y
            int yTicks = 5;
            g2.setFont(FONT_TICK);
            FontMetrics fmTick = g2.getFontMetrics();
            for (int i = 0; i <= yTicks; i++) {
                double val = yMax * i / yTicks;
                int y = padTop + plotH - (int) Math.round(plotH * (val / yMax));
                g2.setColor(GRID_COLOR);
                g2.drawLine(padLeft, y, padLeft + plotW, y);
                g2.setColor(TEXT_MUTED);
                String lbl = formatNumber(val);
                g2.drawString(lbl, padLeft - fmTick.stringWidth(lbl) - 10, y + 4);
            }

            // Eixos
            g2.setColor(AXIS_COLOR);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(padLeft, padTop, padLeft, padTop + plotH);
            g2.drawLine(padLeft, padTop + plotH, padLeft + plotW, padTop + plotH);

            // Rótulo eixo Y rotacionado
            Graphics2D gRot = (Graphics2D) g2.create();
            gRot.setFont(FONT_AXIS_TITLE);
            gRot.setColor(TEXT_DARK);
            gRot.rotate(-Math.PI / 2.0, 26, padTop + plotH / 2.0);
            FontMetrics fmY = gRot.getFontMetrics();
            gRot.drawString(yLabel, 26 - fmY.stringWidth(yLabel) / 2, padTop + plotH / 2 + 5);
            gRot.dispose();

            // Barras agrupadas
            int nCat = categories.size();
            int nSer = seriesNames.length;
            double groupSlot = (double) plotW / Math.max(1, nCat);
            double groupW = groupSlot * 0.72;
            double barGap = 6.0;
            double barW = (groupW - barGap * (nSer - 1)) / nSer;

            for (int c = 0; c < nCat; c++) {
                String cat = categories.get(c);
                double[] vals = data.get(cat);
                double groupStartX = padLeft + c * groupSlot + (groupSlot - groupW) / 2.0;

                for (int s = 0; s < nSer; s++) {
                    double val = vals[s];
                    double barH = (val / yMax) * plotH;
                    double x = groupStartX + s * (barW + barGap);
                    double y = padTop + plotH - barH;

                    g2.setColor(colors[s % colors.length]);
                    g2.fill(new Rectangle2D.Double(x, y, barW, barH));

                    g2.setColor(TEXT_DARK);
                    g2.setFont(FONT_VALUE);
                    String vStr = formatNumber(val) + " ms";
                    FontMetrics fmV = g2.getFontMetrics();
                    g2.drawString(vStr, (int) (x + (barW - fmV.stringWidth(vStr)) / 2.0), (int) y - 5);
                }

                // Rótulo categoria X
                g2.setColor(TEXT_DARK);
                g2.setFont(FONT_AXIS_TITLE);
                FontMetrics fmX = g2.getFontMetrics();
                g2.drawString(cat, (int) (padLeft + c * groupSlot + (groupSlot - fmX.stringWidth(cat)) / 2.0), padTop + plotH + 26);
            }

            drawFooter(g2, w, h, footerNote);
        }
    }

    /**
     * Painel de múltiplas linhas com marcadores e rótulos.
     */
    private static class MultiLineChartPanel extends JPanel {
        private final String title, subtitle, xLabel, yLabel, footerNote;
        private final List<String> xLabels;
        private final Map<String, List<Double>> series;
        private final Color[] colors;

        MultiLineChartPanel(String title, String subtitle, String xLabel, String yLabel,
                            List<String> xLabels, Map<String, List<Double>> series,
                            Color[] colors, String footerNote) {
            this.title = title;
            this.subtitle = subtitle;
            this.xLabel = xLabel;
            this.yLabel = yLabel;
            this.xLabels = xLabels;
            this.series = series;
            this.colors = colors;
            this.footerNote = footerNote;
            setBackground(BG_COLOR);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            applyHints(g2);

            int w = getWidth();
            int h = getHeight();
            g2.setColor(BG_COLOR);
            g2.fillRect(0, 0, w, h);

            drawHeader(g2, w, title, subtitle);
            String[] names = series.keySet().toArray(new String[0]);
            drawLegend(g2, w, 88, names, colors);

            int padLeft = 86, padRight = 50, padTop = 108, padBottom = 92;
            int plotW = w - padLeft - padRight;
            int plotH = h - padTop - padBottom;

            g2.setColor(PANEL_COLOR);
            g2.fillRect(padLeft, padTop, plotW, plotH);

            double maxVal = 0;
            for (List<Double> vals : series.values()) {
                for (double v : vals) maxVal = Math.max(maxVal, v);
            }
            double yMax = niceCeiling(maxVal * 1.15);

            // Grid Y
            int yTicks = 5;
            g2.setFont(FONT_TICK);
            FontMetrics fmTick = g2.getFontMetrics();
            for (int i = 0; i <= yTicks; i++) {
                double val = yMax * i / yTicks;
                int y = padTop + plotH - (int) Math.round(plotH * (val / yMax));
                g2.setColor(GRID_COLOR);
                g2.drawLine(padLeft, y, padLeft + plotW, y);
                g2.setColor(TEXT_MUTED);
                String lbl = formatNumber(val);
                g2.drawString(lbl, padLeft - fmTick.stringWidth(lbl) - 10, y + 4);
            }

            // Grid X e rótulos
            int nX = xLabels.size();
            double stepX = (nX > 1) ? (double) plotW / (nX - 1) : plotW;
            for (int i = 0; i < nX; i++) {
                int x = padLeft + (int) Math.round(i * stepX);
                g2.setColor(GRID_COLOR);
                g2.drawLine(x, padTop, x, padTop + plotH);
                g2.setColor(TEXT_DARK);
                String lbl = xLabels.get(i);
                g2.drawString(lbl, x - fmTick.stringWidth(lbl) / 2, padTop + plotH + 22);
            }

            // Eixos
            g2.setColor(AXIS_COLOR);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(padLeft, padTop, padLeft, padTop + plotH);
            g2.drawLine(padLeft, padTop + plotH, padLeft + plotW, padTop + plotH);

            // Títulos de eixos
            g2.setFont(FONT_AXIS_TITLE);
            g2.setColor(TEXT_DARK);
            FontMetrics fmXTitle = g2.getFontMetrics();
            g2.drawString(xLabel, padLeft + (plotW - fmXTitle.stringWidth(xLabel)) / 2, padTop + plotH + 46);

            Graphics2D gRot = (Graphics2D) g2.create();
            gRot.setFont(FONT_AXIS_TITLE);
            gRot.setColor(TEXT_DARK);
            gRot.rotate(-Math.PI / 2.0, 26, padTop + plotH / 2.0);
            FontMetrics fmY = gRot.getFontMetrics();
            gRot.drawString(yLabel, 26 - fmY.stringWidth(yLabel) / 2, padTop + plotH / 2 + 5);
            gRot.dispose();

            // Séries (linhas + marcadores)
            int sIdx = 0;
            List<Color> seriesColors = new ArrayList<>();
            List<List<Double>> seriesValues = new ArrayList<>();
            for (Map.Entry<String, List<Double>> entry : series.entrySet()) {
                List<Double> vals = entry.getValue();
                Color col = colors[sIdx % colors.length];
                seriesColors.add(col);
                seriesValues.add(vals);

                g2.setColor(col);
                g2.setStroke(new BasicStroke(2.6f));
                for (int i = 0; i < vals.size() - 1; i++) {
                    double x1 = padLeft + i * stepX;
                    double y1 = padTop + plotH - (vals.get(i) / yMax) * plotH;
                    double x2 = padLeft + (i + 1) * stepX;
                    double y2 = padTop + plotH - (vals.get(i + 1) / yMax) * plotH;
                    g2.draw(new Line2D.Double(x1, y1, x2, y2));
                }

                for (int i = 0; i < vals.size(); i++) {
                    double x = padLeft + i * stepX;
                    double y = padTop + plotH - (vals.get(i) / yMax) * plotH;
                    g2.setColor(Color.WHITE);
                    g2.fill(new Ellipse2D.Double(x - 5, y - 5, 10, 10));
                    g2.setColor(col);
                    g2.setStroke(new BasicStroke(2.2f));
                    g2.draw(new Ellipse2D.Double(x - 5, y - 5, 10, 10));
                }
                sIdx++;
            }

            // Rótulos com resolução automática de colisões verticais (sem sobreposição!)
            g2.setFont(FONT_VALUE);
            FontMetrics fmVal = g2.getFontMetrics();
            for (int i = 0; i < nX; i++) {
                boolean showCol = (nX <= 5) || (i == nX - 1) || (i == nX - 2);
                if (!showCol) continue;

                List<double[]> labels = new ArrayList<>();
                for (int s = 0; s < seriesValues.size(); s++) {
                    List<Double> vals = seriesValues.get(s);
                    if (i >= vals.size()) continue;
                    double val = vals.get(i);
                    double y = padTop + plotH - (val / yMax) * plotH - 9;
                    labels.add(new double[]{y, s, val});
                }
                labels.sort((a, b) -> Double.compare(a[0], b[0]));

                // Empurra rótulos muito próximos para cima/baixo (mínimo 15 px entre linhas)
                for (int k = 1; k < labels.size(); k++) {
                    if (labels.get(k)[0] - labels.get(k - 1)[0] < 15.0) {
                        labels.get(k)[0] = labels.get(k - 1)[0] + 15.0;
                    }
                }
                // Garante que fiquem dentro da área do gráfico
                for (int k = labels.size() - 1; k >= 0; k--) {
                    if (labels.get(k)[0] > padTop + plotH - 4) {
                        labels.get(k)[0] = padTop + plotH - 4;
                    }
                    if (k > 0 && labels.get(k)[0] - labels.get(k - 1)[0] < 15.0) {
                        labels.get(k - 1)[0] = labels.get(k)[0] - 15.0;
                    }
                }

                double x = padLeft + i * stepX;
                for (double[] item : labels) {
                    int s = (int) item[1];
                    String txt = formatNumber(item[2]) + (i == nX - 1 ? " ms" : "");
                    int tw = fmVal.stringWidth(txt);
                    int tx = (i == nX - 1)
                            ? (int) Math.min(padLeft + plotW - tw + 38, x - tw / 2.0)
                            : (int) (x - tw / 2.0);
                    int ty = (int) Math.max(padTop + 14, item[0]);
                    g2.setColor(seriesColors.get(s));
                    g2.drawString(txt, tx, ty);
                }
            }

            drawFooter(g2, w, h, footerNote);
        }
    }

    /**
     * Painel duplo lado a lado: Speedup Real vs Ideal + Eficiência Paralela (%).
     */
    private static class SpeedupEfficiencyPanel extends JPanel {
        private final List<CsvExporter.SummaryRow> exp2Rows;

        SpeedupEfficiencyPanel(List<CsvExporter.SummaryRow> exp2Rows) {
            this.exp2Rows = exp2Rows;
            setBackground(BG_COLOR);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            applyHints(g2);

            int w = getWidth();
            int h = getHeight();
            g2.setColor(BG_COLOR);
            g2.fillRect(0, 0, w, h);

            drawHeader(g2, w,
                    "Speedup e Eficiência Paralela do ParallelCPU",
                    "Avaliação de paralelismo (Lei de Amdahl) em CPU 4 núcleos físicos / 8 threads lógicas");

            Map<String, List<Double>> speedups = new LinkedHashMap<>();
            Map<String, List<Double>> efficiencies = new LinkedHashMap<>();
            for (CsvExporter.SummaryRow r : exp2Rows) {
                speedups.computeIfAbsent(r.dataset, k -> new ArrayList<>()).add(r.speedupCompute);
                efficiencies.computeIfAbsent(r.dataset, k -> new ArrayList<>()).add(r.efficiencyPct);
            }

            String[] dsNames = speedups.keySet().toArray(new String[0]);
            Color[] cols = {PALETTE[0], PALETTE[1], PALETTE[4]};
            drawLegend(g2, w, 86, dsNames, cols);

            int gap = 72;
            int padLeft = 74, padRight = 36, padTop = 128, padBottom = 82;
            int subW = (w - padLeft - padRight - gap) / 2;
            int subH = h - padTop - padBottom;

            drawSubLinePlot(g2, padLeft, padTop, subW, subH,
                    "Speedup (S = T_serial / T_p)", "Speedup (x)",
                    speedups, cols, true);

            drawSubLinePlot(g2, padLeft + subW + gap, padTop, subW, subH,
                    "Eficiência Paralela (E = S / p × 100%)", "Eficiência (%)",
                    efficiencies, cols, false);

            drawFooter(g2, w, h, "Nota: linha tracejada cinza no gráfico à esquerda representa referência linear (limitada a 4 núcleos físicos).");
        }

        private void drawSubLinePlot(Graphics2D g2, int x0, int y0, int pw, int ph,
                                     String subTitle, String yLbl,
                                     Map<String, List<Double>> data, Color[] cols, boolean isSpeedup) {
            g2.setColor(TEXT_DARK);
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            FontMetrics fmSub = g2.getFontMetrics();
            g2.drawString(subTitle, x0 + (pw - fmSub.stringWidth(subTitle)) / 2, y0 - 12);

            g2.setColor(PANEL_COLOR);
            g2.fillRect(x0, y0, pw, ph);

            double maxVal = isSpeedup ? 4.5 : 110.0;
            for (List<Double> vals : data.values()) {
                for (double v : vals) maxVal = Math.max(maxVal, v * 1.15);
            }
            double yMax = isSpeedup ? niceCeiling(maxVal) : Math.max(100.0, niceCeiling(maxVal));

            g2.setFont(FONT_TICK);
            FontMetrics fmTick = g2.getFontMetrics();
            for (int i = 0; i <= 5; i++) {
                double val = yMax * i / 5.0;
                int y = y0 + ph - (int) Math.round(ph * (val / yMax));
                g2.setColor(GRID_COLOR);
                g2.drawLine(x0, y, x0 + pw, y);
                g2.setColor(TEXT_MUTED);
                String s = formatNumber(val) + (isSpeedup ? "x" : "%");
                g2.drawString(s, x0 - fmTick.stringWidth(s) - 8, y + 4);
            }

            int n = BenchmarkRunner.CPU_THREAD_COUNTS.length;
            double stepX = (double) pw / (n - 1);
            for (int i = 0; i < n; i++) {
                int x = x0 + (int) Math.round(i * stepX);
                g2.setColor(GRID_COLOR);
                g2.drawLine(x, y0, x, y0 + ph);
                g2.setColor(TEXT_DARK);
                String lbl = BenchmarkRunner.CPU_THREAD_COUNTS[i] + "T";
                g2.drawString(lbl, x - fmTick.stringWidth(lbl) / 2, y0 + ph + 20);
            }

            // Linha de referência (Speedup ideal até 4 núcleos físicos ou 100% eficiência)
            g2.setColor(new Color(148, 163, 184));
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 6f}, 0f));
            if (isSpeedup) {
                double[] ideal = {1.0, 2.0, 4.0, 4.0, 4.0};
                for (int i = 0; i < n - 1; i++) {
                    double x1 = x0 + i * stepX;
                    double y1 = y0 + ph - Math.min(1.0, ideal[i] / yMax) * ph;
                    double x2 = x0 + (i + 1) * stepX;
                    double y2 = y0 + ph - Math.min(1.0, ideal[i + 1] / yMax) * ph;
                    g2.draw(new Line2D.Double(x1, y1, x2, y2));
                }
            } else {
                double y100 = y0 + ph - (100.0 / yMax) * ph;
                g2.draw(new Line2D.Double(x0, y100, x0 + pw, y100));
            }

            // Eixos
            g2.setColor(AXIS_COLOR);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(x0, y0, x0, y0 + ph);
            g2.drawLine(x0, y0 + ph, x0 + pw, y0 + ph);

            // Séries
            int sIdx = 0;
            for (List<Double> vals : data.values()) {
                Color col = cols[sIdx % cols.length];
                g2.setColor(col);
                g2.setStroke(new BasicStroke(2.5f));
                for (int i = 0; i < vals.size() - 1; i++) {
                    double x1 = x0 + i * stepX;
                    double y1 = y0 + ph - (vals.get(i) / yMax) * ph;
                    double x2 = x0 + (i + 1) * stepX;
                    double y2 = y0 + ph - (vals.get(i + 1) / yMax) * ph;
                    g2.draw(new Line2D.Double(x1, y1, x2, y2));
                }
                for (int i = 0; i < vals.size(); i++) {
                    double x = x0 + i * stepX;
                    double y = y0 + ph - (vals.get(i) / yMax) * ph;
                    g2.setColor(Color.WHITE);
                    g2.fill(new Ellipse2D.Double(x - 4.5, y - 4.5, 9, 9));
                    g2.setColor(col);
                    g2.draw(new Ellipse2D.Double(x - 4.5, y - 4.5, 9, 9));
                }
                sIdx++;
            }

            g2.setColor(TEXT_DARK);
            g2.setFont(FONT_AXIS_TITLE);
            FontMetrics fmX = g2.getFontMetrics();
            g2.drawString("Threads (p)", x0 + (pw - fmX.stringWidth("Threads (p)")) / 2, y0 + ph + 44);
        }
    }

    /**
     * Painel 5: Decomposição do tempo da GPU (Warm: I/O + Transferência + Kernel) vs Cold Start.
     */
    private static class GpuBreakdownPanel extends JPanel {
        private final List<CsvExporter.SummaryRow> exp1Rows;

        GpuBreakdownPanel(List<CsvExporter.SummaryRow> exp1Rows) {
            this.exp1Rows = exp1Rows;
            setBackground(BG_COLOR);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            applyHints(g2);

            int w = getWidth();
            int h = getHeight();
            g2.setColor(BG_COLOR);
            g2.fillRect(0, 0, w, h);

            drawHeader(g2, w,
                    "Decomposição do Tempo de Execução na GPU OpenCL (Overhead vs Cômputo)",
                    "Esquerda: GPU Warm (I/O + Transferência Host↔Device + Kernel) | Direita: Impacto do Cold Start OpenCL");

            List<CsvExporter.SummaryRow> warmRows = new ArrayList<>();
            List<CsvExporter.SummaryRow> coldRows = new ArrayList<>();
            for (CsvExporter.SummaryRow r : exp1Rows) {
                if (r.algorithm.equals("ParallelGPU (Warm)")) warmRows.add(r);
                else if (r.algorithm.equals("ParallelGPU (Cold)")) coldRows.add(r);
            }

            String[] legNames = {"Leitura I/O (Disco)", "Transferência Host↔GPU", "Execução Kernel GPU", "Init + Build OpenCL (Cold)"};
            Color[] legColors = {new Color(100, 116, 139), PALETTE[3], PALETTE[1], PALETTE[4]};
            drawLegend(g2, w, 86, legNames, legColors);

            int gap = 80;
            int padLeft = 80, padRight = 36, padTop = 128, padBottom = 80;
            int subW = (w - padLeft - padRight - gap) / 2;
            int subH = h - padTop - padBottom;

            // Esquerda: Stacked Warm
            drawStackedPanel(g2, padLeft, padTop, subW, subH, "ParallelGPU (Contexto Warm)", warmRows, legColors, false);
            // Direita: Stacked Cold
            drawStackedPanel(g2, padLeft + subW + gap, padTop, subW, subH, "ParallelGPU (Cold Start Completo)", coldRows, legColors, true);

            drawFooter(g2, w, h, "Conclusão visual: o kernel OpenCL executa em poucos milissegundos; o gargalo em execuções avulsas é a inicialização e compilação JIT OpenCL.");
        }

        private void drawStackedPanel(Graphics2D g2, int x0, int y0, int pw, int ph,
                                      String subTitle, List<CsvExporter.SummaryRow> rows,
                                      Color[] cols, boolean isCold) {
            g2.setColor(TEXT_DARK);
            g2.setFont(new Font("SansSerif", Font.BOLD, 14));
            FontMetrics fmSub = g2.getFontMetrics();
            g2.drawString(subTitle, x0 + (pw - fmSub.stringWidth(subTitle)) / 2, y0 - 12);

            g2.setColor(PANEL_COLOR);
            g2.fillRect(x0, y0, pw, ph);

            double maxVal = 0;
            for (CsvExporter.SummaryRow r : rows) {
                maxVal = Math.max(maxVal, r.meanTotalMs);
            }
            double yMax = niceCeiling(maxVal * 1.18);

            g2.setFont(FONT_TICK);
            FontMetrics fmTick = g2.getFontMetrics();
            for (int i = 0; i <= 5; i++) {
                double val = yMax * i / 5.0;
                int y = y0 + ph - (int) Math.round(ph * (val / yMax));
                g2.setColor(GRID_COLOR);
                g2.drawLine(x0, y, x0 + pw, y);
                g2.setColor(TEXT_MUTED);
                String lbl = formatNumber(val);
                g2.drawString(lbl, x0 - fmTick.stringWidth(lbl) - 8, y + 4);
            }

            g2.setColor(AXIS_COLOR);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(x0, y0, x0, y0 + ph);
            g2.drawLine(x0, y0 + ph, x0 + pw, y0 + ph);

            int n = rows.size();
            double slot = (double) pw / Math.max(1, n);
            double barW = slot * 0.48;

            for (int i = 0; i < n; i++) {
                CsvExporter.SummaryRow r = rows.get(i);
                double x = x0 + i * slot + (slot - barW) / 2.0;

                double curY = y0 + ph;
                double hIo = (r.meanIoMs / yMax) * ph;
                double hSetup = (r.meanSetupTransferMs / yMax) * ph;
                double hKernel = (r.meanComputeMs / yMax) * ph;

                // 1. I/O
                g2.setColor(cols[0]);
                g2.fill(new Rectangle2D.Double(x, curY - hIo, barW, hIo));
                curY -= hIo;

                // 2. Transfer ou Cold Init+Transfer
                g2.setColor(isCold ? cols[3] : cols[1]);
                g2.fill(new Rectangle2D.Double(x, curY - hSetup, barW, hSetup));
                curY -= hSetup;

                // 3. Kernel
                g2.setColor(cols[2]);
                g2.fill(new Rectangle2D.Double(x, curY - hKernel, barW, hKernel));
                curY -= hKernel;

                g2.setColor(TEXT_DARK);
                g2.setFont(FONT_VALUE);
                String totalStr = formatNumber(r.meanTotalMs) + " ms";
                FontMetrics fmV = g2.getFontMetrics();
                g2.drawString(totalStr, (int) (x + (barW - fmV.stringWidth(totalStr)) / 2.0), (int) curY - 6);

                // Rótulo eixo X curto
                String shortDs = r.dataset.split(" \\(")[0];
                g2.setFont(FONT_TICK);
                FontMetrics fmX = g2.getFontMetrics();
                g2.drawString(shortDs, (int) (x0 + i * slot + (slot - fmX.stringWidth(shortDs)) / 2.0), y0 + ph + 22);
            }
        }
    }

    public static class NamedChartPanel {
        public final String fileName;
        public final String tabTitle;
        public final JPanel panel;

        public NamedChartPanel(String fileName, String tabTitle, JPanel panel) {
            this.fileName = fileName;
            this.tabTitle = tabTitle;
            this.panel = panel;
        }
    }
}
