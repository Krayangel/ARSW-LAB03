package edu.eci.arsw.immortals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import edu.eci.arsw.concurrency.PauseController;

/**
 * Gestor completo de inmortales - Implementa todos los puntos del laboratorio
 */
public final class ImmortalManager implements AutoCloseable {
    // Punto 10: Colecciones concurrentes para manejo seguro de threads
    private final CopyOnWriteArrayList<Immortal> population = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<Immortal> deadQueue = new ConcurrentLinkedQueue<>();
    private final List<Future<?>> futures = new ArrayList<>();
    
    // Controladores
    private final PauseController controller = new PauseController();
    private final ScoreBoard scoreBoard = new ScoreBoard();
    
    // Executor y estado
    private ExecutorService exec;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private long startTime = 0;
    
    // Configuración inicial
    private final int initialHealth;
    private final int initialCount;
    private final int damage;
    private final String fightMode;
    
    // Estadísticas
    private int deadRemoved = 0;

    /**
     * Constructor principal
     */
    public ImmortalManager(int n, String fightMode, int initialHealth, int damage) {
        if (n <= 0) throw new IllegalArgumentException("N debe ser > 0");
        if (initialHealth <= 0) throw new IllegalArgumentException("Salud debe ser > 0");
        if (damage <= 0) throw new IllegalArgumentException("Daño debe ser > 0");
        
        this.initialHealth = initialHealth;
        this.initialCount = n;
        this.damage = damage;
        this.fightMode = fightMode;
        
        // Configurar modo de lucha
        System.setProperty("fight", fightMode);
        
        System.out.println("\n=== CREANDO GESTOR DE INMORTALES ===");
        System.out.printf("Cantidad: %d | Salud: %d | Daño: %d | Modo: %s%n", 
                        n, initialHealth, damage, fightMode);
        System.out.printf("Invariante esperado: %,d%n", (long)n * initialHealth);
        
        // Configurar controlador de pausa
        controller.setTotalThreads(n);
        
        // Crear población inicial
        for (int i = 0; i < n; i++) {
            String name = String.format("Immortal-%04d", i);
            Immortal immortal = new Immortal(
                name, 
                initialHealth, 
                damage, 
                population, 
                scoreBoard, 
                controller,
                deadQueue
            );
            population.add(immortal);
        }
        
        System.out.printf("✅ Creados %d inmortales%n", population.size());
    }

    /**
     * Constructor simplificado
     */
    public ImmortalManager(int n, String fightMode) {
        this(n, fightMode, 100, 10);
    }

    /**
     * Punto 11: Iniciar simulación
     */
    public synchronized void start() {
        if (running.get()) {
            System.out.println("[MANAGER] Ya está ejecutándose");
            return;
        }
        
        running.set(true);
        startTime = System.currentTimeMillis();
        
        System.out.println("\n=== INICIANDO SIMULACIÓN ===");
        
        // Usar thread pool fijo para mejor control
        exec = Executors.newFixedThreadPool(initialCount);
        
        // Punto 10: Hilo de limpieza de muertos
        exec.submit(() -> {
            Thread.currentThread().setName("Cleanup-Thread");
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(100); // Limpiar cada 100ms
                    cleanDead();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.println("[CLEANUP] Hilo de limpieza terminado");
        });
        
        // Iniciar todos los inmortales
        System.out.printf("[MANAGER] Iniciando %d threads...%n", population.size());
        for (Immortal im : population) {
            Future<?> future = exec.submit(im);
            futures.add(future);
        }
        
        System.out.println("[MANAGER] ✅ Simulación iniciada");
        System.out.printf("Invariante esperado: %,d%n", getExpectedTotalHealth());
    }

    /**
     * Punto 4-5: Pausar simulación
     */
    public void pause() {
        if (!running.get()) {
            System.out.println("[MANAGER] No se puede pausar, no está ejecutándose");
            return;
        }
        
        System.out.println("\n[MANAGER] === SOLICITANDO PAUSA ===");
        controller.pause();
        
        try {
            // Esperar a que todos los threads se pausen
            Thread.sleep(100); // Dar tiempo inicial
            
            int timeout = 5000; // 5 segundos máximo
            long startWait = System.currentTimeMillis();
            
            while (controller.getPausedThreadsCount() < controller.getTotalThreads()) {
                long elapsed = System.currentTimeMillis() - startWait;
                if (elapsed > timeout) {
                    System.out.printf("[MANAGER] ⏰ TIMEOUT: Solo %d/%d threads pausados%n",
                                    controller.getPausedThreadsCount(), controller.getTotalThreads());
                    break;
                }
                
                System.out.printf("[MANAGER] Esperando... %d/%d threads pausados%n",
                                controller.getPausedThreadsCount(), controller.getTotalThreads());
                Thread.sleep(100);
            }
            
            if (controller.getPausedThreadsCount() >= controller.getTotalThreads()) {
                System.out.printf("[MANAGER] ✅ Todos pausados (%d/%d)%n",
                                controller.getPausedThreadsCount(), controller.getTotalThreads());
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Punto 10: Limpiar muertos mientras estamos pausados
        cleanDead();
    }
    
    /**
     * Punto 4: Reanudar simulación
     */
    public void resume() {
        if (!running.get()) {
            System.out.println("[MANAGER] No se puede reanudar, no está ejecutándose");
            return;
        }
        
        System.out.println("\n[MANAGER] === REANUDANDO ===");
        controller.resume();
        System.out.println("[MANAGER] ✅ Simulación reanudada");
    }
    
    /**
     * Punto 11: Detener simulación ordenadamente
     */
    public void stop() {
        if (!running.getAndSet(false)) {
            System.out.println("[MANAGER] Ya está detenido");
            return;
        }
        
        System.out.println("\n[MANAGER] === DETENIENDO SIMULACIÓN ===");
        
        // 1. Detener todos los inmortales
        System.out.println("[STOP] Deteniendo inmortales...");
        for (Immortal im : population) {
            im.stop();
        }
        
        // 2. Forzar reanudación si estaba pausado
        controller.forceResume();
        
        // 3. Apagar executor
        if (exec != null) {
            System.out.println("[STOP] Apagando executor...");
            exec.shutdownNow();
            
            try {
                if (!exec.awaitTermination(3, TimeUnit.SECONDS)) {
                    System.err.println("[STOP] Timeout esperando terminación");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exec.shutdownNow();
            }
            
            exec = null;
        }
        
        // 4. Limpiar recursos
        futures.clear();
        deadQueue.clear();
        population.clear();
        
        // 5. Mostrar estadísticas finales
        long duration = System.currentTimeMillis() - startTime;
        System.out.println("\n=== ESTADÍSTICAS FINALES ===");
        System.out.printf("Duración total: %,d ms%n", duration);
        System.out.printf("Peleas realizadas: %,d%n", scoreBoard.totalFights());
        System.out.printf("Inmortales eliminados: %,d%n", deadRemoved);
        System.out.printf("Modo de lucha usado: %s%n", fightMode);
        System.out.println("=============================\n");
        
        System.out.println("[MANAGER] ✅ Simulación detenida completamente");
    }
    
    /**
     * Punto 2: Calcular salud total actual
     */
    public long totalHealth() {
        long sum = 0;
        for (Immortal im : population) {
            sum += im.getHealth();
        }
        return sum;
    }
    
    /**
     * Punto 2: Salud total esperada (invariante)
     */
    public long getExpectedTotalHealth() {
        return (long) initialCount * initialHealth;
    }
    
    /**
     * Punto 3: Verificar invariante
     */
    public boolean checkInvariant() {
        long expected = getExpectedTotalHealth();
        long actual = totalHealth();
        boolean ok = (actual == expected);
        
        System.out.printf("[INVARIANT] Esperado: %,d | Actual: %,d | OK: %s%n",
                        expected, actual, ok ? "✅" : "❌");
        
        return ok;
    }
    
    /**
     * Contar inmortales vivos
     */
    public int aliveCount() {
        int count = 0;
        for (Immortal im : population) {
            if (im.isAlive()) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Punto 5: Tomar snapshot de población (todos pausados)
     */
    public List<Immortal> populationSnapshot() {
        // Asegurar que esté pausado
        if (!controller.paused()) {
            System.out.println("[SNAPSHOT] No está pausado, pausando primero...");
            pause();
        }
        
        // Crear copia de la población viva
        List<Immortal> snapshot = new ArrayList<>();
        for (Immortal im : population) {
            if (im.isAlive()) {
                snapshot.add(im);
            }
        }
        
        System.out.printf("[SNAPSHOT] Tomada con %,d inmortales vivos%n", snapshot.size());
        return Collections.unmodifiableList(snapshot);
    }
    
    /**
     * Punto 10: Limpiar inmortales muertos
     */
    private void cleanDead() {
        Immortal dead;
        int removedThisCycle = 0;
        
        while ((dead = deadQueue.poll()) != null) {
            if (population.remove(dead)) {
                removedThisCycle++;
                deadRemoved++;
                
                if (removedThisCycle <= 3) { // Log solo primeros 3
                    System.out.printf("[CLEANUP] Eliminado: %s%n", dead.name());
                }
            }
        }
        
        if (removedThisCycle > 0) {
            System.out.printf("[CLEANUP] Eliminados %,d inmortales. Población: %,d%n",
                            removedThisCycle, population.size());
            
            if (removedThisCycle > 3) {
                System.out.printf("[CLEANUP] ...y %,d más%n", removedThisCycle - 3);
            }
        }
    }
    
    /**
     * Métodos de acceso
     */
    public ScoreBoard scoreBoard() { 
        return scoreBoard; 
    }
    
    public PauseController controller() { 
        return controller; 
    }
    
    public boolean isRunning() {
        return running.get();
    }
    
    public int getPopulationSize() {
        return population.size();
    }
    
    public long getTotalFights() {
        return scoreBoard.totalFights();
    }
    
    public int getDeadRemovedCount() {
        return deadRemoved;
    }
    
    public long getSimulationTime() {
        return running.get() ? System.currentTimeMillis() - startTime : 0;
    }
    
    /**
     * Punto 11: Para uso con try-with-resources
     */
    @Override 
    public void close() { 
        stop(); 
    }
}