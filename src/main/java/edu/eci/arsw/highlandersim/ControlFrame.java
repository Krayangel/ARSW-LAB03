package edu.eci.arsw.highlandersim;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import edu.eci.arsw.immortals.Immortal;
import edu.eci.arsw.immortals.ImmortalManager;

public final class ControlFrame extends JFrame {
    private ImmortalManager manager;
    private final JTextArea output = new JTextArea(20, 60);
    private final JButton startBtn = new JButton("Start");
    private final JButton pauseAndCheckBtn = new JButton("Pause & Check");
    private final JButton resumeBtn = new JButton("Resume");
    private final JButton stopBtn = new JButton("Stop");
    private final JButton deadlockBtn = new JButton("Check Deadlock");

    private final JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(8, 2, 10000, 1));
    private final JSpinner healthSpinner = new JSpinner(new SpinnerNumberModel(100, 10, 10000, 10));
    private final JSpinner damageSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 1000, 1));
    private final JComboBox<String> fightMode = new JComboBox<>(new String[]{"ordered", "naive", "trylock"});
    
    private final JLabel statusLabel = new JLabel("Estado: Detenido");

    public ControlFrame(int count, String fight) {
        setTitle("Highlander Simulator - ARSW Lab 3");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        
        // Panel superior
        JPanel topPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        
        JPanel configPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        configPanel.add(new JLabel("Count:"));
        countSpinner.setValue(count);
        configPanel.add(countSpinner);
        configPanel.add(new JLabel("Health:"));
        configPanel.add(healthSpinner);
        configPanel.add(new JLabel("Damage:"));
        configPanel.add(damageSpinner);
        configPanel.add(new JLabel("Fight:"));
        fightMode.setSelectedItem(fight);
        configPanel.add(fightMode);
        
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.add(statusLabel);
        statusPanel.add(deadlockBtn);
        
        topPanel.add(configPanel);
        topPanel.add(statusPanel);
        add(topPanel, BorderLayout.NORTH);
        
        // Área de salida
        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        output.setBackground(new Color(240, 240, 240));
        JScrollPane scrollPane = new JScrollPane(output);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Output"));
        add(scrollPane, BorderLayout.CENTER);
        
        // Panel de botones
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttonPanel.add(startBtn);
        buttonPanel.add(pauseAndCheckBtn);
        buttonPanel.add(resumeBtn);
        buttonPanel.add(stopBtn);
        add(buttonPanel, BorderLayout.SOUTH);
        
        // Acciones
        startBtn.addActionListener(this::onStart);
        pauseAndCheckBtn.addActionListener(this::onPauseAndCheck);
        resumeBtn.addActionListener(this::onResume);
        stopBtn.addActionListener(this::onStop);
        deadlockBtn.addActionListener(this::onCheckDeadlock);
        
        // Configurar ventana
        pack();
        setLocationByPlatform(true);
        setVisible(true);
        
        // Mensaje inicial
        output.append("=== HIGH-LANDER SIMULATOR ===\n\n");
        output.append("Configuración inicial:\n");
        output.append(String.format("  Inmortales: %d\n", count));
        output.append(String.format("  Salud inicial: %d\n", (Integer)healthSpinner.getValue()));
        output.append(String.format("  Daño: %d\n", (Integer)damageSpinner.getValue()));
        output.append(String.format("  Modo de lucha: %s\n\n", fight));
        output.append("Instrucciones:\n");
        output.append("1. Click 'Start' para iniciar simulación\n");
        output.append("2. Click 'Pause & Check' para verificar estado\n");
        output.append("3. 'Check Deadlock' detecta problemas\n");
        output.append("4. 'Resume' para continuar\n");
        output.append("5. 'Stop' para terminar limpiamente\n\n");
        output.append("Modos de lucha:\n");
        output.append("  - ordered: Sin deadlock (recomendado)\n");
        output.append("  - naive: Puede causar deadlock\n");
        output.append("  - trylock: Con timeout\n");
    }

    // PUNTO 11: START
    private void onStart(ActionEvent e) {
        safeStop();
        
        int n = (Integer) countSpinner.getValue();
        int health = (Integer) healthSpinner.getValue();
        int damage = (Integer) damageSpinner.getValue();
        String fight = (String) fightMode.getSelectedItem();
        
        output.append("\n=== INICIANDO SIMULACIÓN ===\n");
        output.append(String.format("Parámetros:\n"));
        output.append(String.format("  Inmortales: %d\n", n));
        output.append(String.format("  Salud inicial: %d\n", health));
        output.append(String.format("  Daño: %d\n", damage));
        output.append(String.format("  Modo: %s\n", fight));
        output.append(String.format("  Invariante esperado: %,d (N * H)\n\n", (long)n * health));
        
        try {
            manager = new ImmortalManager(n, fight, health, damage);
            manager.start();
            statusLabel.setText("Estado: Ejecutando (" + n + " inmortales)");
            statusLabel.setForeground(Color.GREEN);
            output.append("✅ Simulación iniciada correctamente\n");
        } catch (Exception ex) {
            output.append("❌ Error iniciando simulación: " + ex.getMessage() + "\n");
            statusLabel.setText("Estado: Error");
            statusLabel.setForeground(Color.RED);
        }
    }

    // PUNTOS 3, 5: PAUSE & CHECK
    // En el método onPauseAndCheck, simplificar:
private void onPauseAndCheck(ActionEvent e) {
    if (manager == null || !manager.isRunning()) {
        output.append("\n⚠️ No hay simulación ejecutándose\n");
        return;
    }
    
    output.append("\n=== PAUSE & CHECK ===\n");
    
    try {
        // Pausar
        manager.pause();
        
        // Tomar snapshot
        List<Immortal> pop = manager.populationSnapshot();
        
        // Calcular
        long sum = 0;
        StringBuilder sb = new StringBuilder();
        
        for (Immortal im : pop) {
            int h = im.getHealth();
            sum += h;
            sb.append(String.format("%-14s : %5d\n", im.name(), h));
        }
        
        // Verificar invariante
        int n = (Integer) countSpinner.getValue();
        int health = (Integer) healthSpinner.getValue();
        long expected = (long) n * health;
        
        sb.append("--------------------------------\n");
        sb.append("Total Health: ").append(sum).append("\n");
        sb.append("Expected: ").append(expected).append("\n");
        sb.append("Difference: ").append(Math.abs(expected - sum)).append("\n");
        sb.append("Invariant OK: ").append(sum == expected).append("\n");
        sb.append("Threads paused: ").append(manager.controller().getPausedThreadsCount())
        .append("/").append(manager.controller().getTotalThreads()).append("\n");
        
        output.append(sb.toString());
        
    } catch (Exception ex) {
        output.append("Error: " + ex.getMessage() + "\n");
    }
}

    // PUNTO 4: RESUME
    private void onResume(ActionEvent e) {
        if (manager == null || !manager.isRunning()) {
            output.append("\n⚠️ No hay simulación ejecutándose\n");
            return;
        }
        
        output.append("\n=== RESUMIENDO ===\n");
        manager.resume();
        statusLabel.setText("Estado: Ejecutando");
        statusLabel.setForeground(Color.GREEN);
        output.append("✅ Simulación reanudada\n");
    }

    // PUNTO 11: STOP ordenado
    private void onStop(ActionEvent e) {
        output.append("\n=== DETENIENDO SIMULACIÓN ===\n");
        safeStop();
        statusLabel.setText("Estado: Detenido");
        statusLabel.setForeground(Color.RED);
        output.append("✅ Simulación detenida correctamente\n");
    }
    
    // PUNTO 7: Verificar deadlocks
    private void onCheckDeadlock(ActionEvent e) {
        output.append("\n=== VERIFICANDO DEADLOCKS ===\n");
        checkForDeadlocks();
    }
    
    private void safeStop() {
        if (manager != null) {
            try {
                manager.stop();
            } catch (Exception ex) {
                output.append("⚠️ Error deteniendo: " + ex.getMessage() + "\n");
            }
            manager = null;
        }
    }
    
    // PUNTO 7: Método para detectar deadlocks
    private void checkForDeadlocks() {
        try {
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            long[] deadlockedThreads = threadBean.findDeadlockedThreads();
            
            if (deadlockedThreads == null || deadlockedThreads.length == 0) {
                output.append("✅ No se detectaron deadlocks\n");
            } else {
                output.append("❌ ¡DEADLOCK DETECTADO!\n");
                output.append("Threads involucrados: " + deadlockedThreads.length + "\n\n");
                
                ThreadInfo[] threadInfos = threadBean.getThreadInfo(deadlockedThreads, 10);
                for (ThreadInfo info : threadInfos) {
                    output.append("Thread: " + info.getThreadName() + "\n");
                    output.append("  Estado: " + info.getThreadState() + "\n");
                    
                    if (info.getLockInfo() != null) {
                        output.append("  Bloqueado en: " + info.getLockInfo() + "\n");
                    }
                    
                    output.append("  Stack trace (primeras 5 líneas):\n");
                    StackTraceElement[] stack = info.getStackTrace();
                    for (int i = 0; i < Math.min(stack.length, 5); i++) {
                        output.append("    at " + stack[i] + "\n");
                    }
                    output.append("\n");
                }
                
                output.append("\n=== RECOMENDACIONES ===\n");
                output.append("1. Usar modo 'ordered' en lugar de 'naive'\n");
                output.append("2. Reducir número de inmortales\n");
                output.append("3. Reiniciar la simulación\n");
                output.append("4. Para diagnóstico: ejecutar en terminal:\n");
                output.append("   jps                      # Ver PID\n");
                output.append("   jstack <PID>            # Thread dump\n");
            }
        } catch (Exception ex) {
            output.append("⚠️ Error verificando deadlocks: " + ex.getMessage() + "\n");
        }
    }
    
    public static void main(String[] args) {
        int count = Integer.getInteger("count", 8);
        String fight = System.getProperty("fight", "ordered");
        
        SwingUtilities.invokeLater(() -> new ControlFrame(count, fight));
    }
}