package br.com.paralelismo.ui;

import br.com.paralelismo.algorithms.ParallelCPU;
import br.com.paralelismo.algorithms.ParallelGPU;
import br.com.paralelismo.algorithms.SerialCPU;
import br.com.paralelismo.model.SearchResult;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Interface gráfica Java Swing para visualização dos gráficos gerados e execução
 * interativa dos três algoritmos (SerialCPU, ParallelCPU e ParallelGPU).
 */
public class DashboardSwing extends JFrame {

    private final Path samplesDir;

    public DashboardSwing(List<ChartGenerator.NamedChartPanel> chartPanels, Path samplesDir, String gpuSummary) {
        super("Análise Comparativa de Paralelismo — SerialCPU vs ParallelCPU vs ParallelGPU (OpenCL)");
        this.samplesDir = samplesDir;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1260, 780);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.BOLD, 12));

        for (ChartGenerator.NamedChartPanel chart : chartPanels) {
            tabs.addTab(chart.tabTitle, chart.panel);
        }
        tabs.addTab("Teste Interativo", createInteractivePanel(gpuSummary));

        add(tabs, BorderLayout.CENTER);
    }

    private JPanel createInteractivePanel(String gpuSummary) {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        controls.setBorder(BorderFactory.createTitledBorder("Configuração da Busca Individual"));

        String[] presets = {
                samplesDir.resolve("Dracula-165307.txt").toString(),
                samplesDir.resolve("MobyDick-217452.txt").toString(),
                samplesDir.resolve("DonQuixote-388208.txt").toString()
        };
        JComboBox<String> fileCombo = new JComboBox<>(presets);
        fileCombo.setEditable(true);
        fileCombo.setPrototypeDisplayValue("amostras/DonQuixote-388208.txt            ");

        JButton browseBtn = new JButton("Escolher .txt...");
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(samplesDir.toFile());
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File selected = chooser.getSelectedFile();
                fileCombo.setSelectedItem(selected.getAbsolutePath());
            }
        });

        JTextField wordField = new JTextField("blood", 12);
        JSpinner threadSpinner = new JSpinner(new SpinnerNumberModel(
                Runtime.getRuntime().availableProcessors(), 1, 32, 1));
        JButton runBtn = new JButton("Executar Comparação");

        controls.add(new JLabel("Arquivo:"));
        controls.add(fileCombo);
        controls.add(browseBtn);
        controls.add(new JLabel("Palavra:"));
        controls.add(wordField);
        controls.add(new JLabel("Threads CPU:"));
        controls.add(threadSpinner);
        controls.add(runBtn);

        JTextArea outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        outputArea.setText("Dispositivo OpenCL detectado:\n  " + gpuSummary + "\n\n"
                + "Selecione um arquivo .txt, informe a palavra desejada e clique em 'Executar Comparação'.\n");

        runBtn.addActionListener(e -> {
            String fileStr = String.valueOf(fileCombo.getSelectedItem()).trim();
            String word = wordField.getText().trim();
            int threads = (Integer) threadSpinner.getValue();

            if (word.isEmpty()) {
                outputArea.append("\n[Erro] Informe uma palavra válida.\n");
                return;
            }
            runBtn.setEnabled(false);
            outputArea.append("\n------------------------------------------------------------\n");
            outputArea.append("Executando busca por '" + word + "' em " + fileStr + "...\n");

            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() throws Exception {
                    Path path = Paths.get(fileStr);
                    SerialCPU serial = new SerialCPU();
                    ParallelCPU parallelCpu = new ParallelCPU(threads);

                    SearchResult rSer = serial.countWord(path, word, 1);
                    SearchResult rCpu = parallelCpu.countWord(path, word, 1);
                    SearchResult rGpuWarm;
                    try (ParallelGPU gpu = new ParallelGPU()) {
                        gpu.initialize();
                        // 1 warm-up + medição
                        gpu.countWord(path, word, 0);
                        rGpuWarm = gpu.countWord(path, word, 1);
                    }
                    SearchResult rGpuCold = ParallelGPU.countWordColdStart(path, word, 1);

                    return String.format(
                            "SerialCPU:          %d ocorrências em %.2f ms (cômputo: %.2f ms)%n" +
                            "ParallelCPU (%2dT):  %d ocorrências em %.2f ms (cômputo: %.2f ms)%n" +
                            "ParallelGPU (Warm): %d ocorrências em %.2f ms (transfer: %.2f ms, kernel: %.2f ms)%n" +
                            "ParallelGPU (Cold): %d ocorrências em %.2f ms (init+transfer: %.2f ms, kernel: %.2f ms)%n",
                            rSer.getOccurrences(), rSer.getTotalTimeMs(), rSer.getComputeTimeMs(),
                            threads, rCpu.getOccurrences(), rCpu.getTotalTimeMs(), rCpu.getComputeTimeMs(),
                            rGpuWarm.getOccurrences(), rGpuWarm.getTotalTimeMs(), rGpuWarm.getSetupTimeMs(), rGpuWarm.getComputeTimeMs(),
                            rGpuCold.getOccurrences(), rGpuCold.getTotalTimeMs(), rGpuCold.getSetupTimeMs(), rGpuCold.getComputeTimeMs()
                    );
                }

                @Override
                protected void done() {
                    try {
                        outputArea.append(get());
                    } catch (Exception ex) {
                        outputArea.append("[Erro] " + ex.getMessage() + "\n");
                    } finally {
                        runBtn.setEnabled(true);
                    }
                }
            }.execute();
        });

        root.add(controls, BorderLayout.NORTH);
        root.add(new JScrollPane(outputArea), BorderLayout.CENTER);
        return root;
    }

    public static void showAsync(List<ChartGenerator.NamedChartPanel> chartPanels, Path samplesDir, String gpuSummary) {
        SwingUtilities.invokeLater(() -> {
            DashboardSwing frame = new DashboardSwing(chartPanels, samplesDir, gpuSummary);
            frame.setVisible(true);
        });
    }
}
