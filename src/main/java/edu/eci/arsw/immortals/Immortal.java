package edu.eci.arsw.immortals;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

import edu.eci.arsw.concurrency.PauseController;

public final class Immortal implements Runnable {
    private final String name;
    private int health;
    private final int damage;
    private final List<Immortal> population;
    private final ScoreBoard scoreBoard;
    private final PauseController controller;
    private final ConcurrentLinkedQueue<Immortal> deadQueue;
    private volatile boolean running = true;
    private final ReentrantLock lock = new ReentrantLock(true);
    private final int id;
    private static int nextId = 0;
    
    // Para interrupción controlada
    private Thread myThread;
    
    public Immortal(String name, int health, int damage, List<Immortal> population, 
                ScoreBoard scoreBoard, PauseController controller, 
                ConcurrentLinkedQueue<Immortal> deadQueue) {
        this.name = Objects.requireNonNull(name);
        this.health = health;
        this.damage = damage;
        this.population = Objects.requireNonNull(population);
        this.scoreBoard = Objects.requireNonNull(scoreBoard);
        this.controller = Objects.requireNonNull(controller);
        this.deadQueue = Objects.requireNonNull(deadQueue);
        
        synchronized (Immortal.class) {
            this.id = nextId++;
        }
    }

    public String name() { return name; }
    
    public int getHealth() { 
    // Para thread-safety, sincronizar el acceso
    synchronized (this) {
        return health;
    }
}
    
    public boolean isAlive() { 
        lock.lock();
        try {
            return health > 0 && running;
        } finally {
            lock.unlock();
        }
    }
    
    // MÉTODO CRÍTICO: Detener el thread
    public void stop() { 
        running = false; 
        if (myThread != null) {
            myThread.interrupt(); // ¡ESTO ES CLAVE!
        }
    }

    @Override 
    public void run() {
        myThread = Thread.currentThread(); // Guardar referencia
        myThread.setName("Immortal-" + name);
        
        System.out.println(name + " iniciado (ID: " + id + ")");
        
        try {
            while (running && health > 0) {
                try {
                    // CHEQUEO DE PAUSA - Versión mejorada
                    controller.awaitIfPaused();
                    
                    if (!running) break;
                    
                    // Atacar
                    Immortal opponent = pickOpponent();
                    if (opponent != null) {
                        String mode = System.getProperty("fight", "ordered");
                        switch (mode.toLowerCase()) {
                            case "naive": fightNaive(opponent); break;
                            case "trylock": fightTryLock(opponent); break;
                            default: fightOrdered(opponent); break;
                        }
                    }
                    
                    // Sleep con chequeo de interrupción
                    try {
                        Thread.sleep(10); // Sleep fijo para mejor control
                    } catch (InterruptedException e) {
                        // Si nos interrumpen durante sleep, verificar si es pausa
                        if (controller.paused()) {
                            // Estamos siendo pausados, entrar en awaitIfPaused
                            controller.awaitIfPaused();
                        } else if (!running) {
                            // Estamos siendo detenidos
                            break;
                        }
                        // Continuar si fue otra interrupción
                        Thread.currentThread().interrupt();
                    }
                    
                } catch (InterruptedException e) {
                    // Manejar interrupción en awaitIfPaused
                    Thread.currentThread().interrupt();
                    if (!running) break;
                }
            }
        } finally {
            System.out.println(name + " terminó");
            myThread = null;
        }
    }

    private Immortal pickOpponent() {
        if (population.isEmpty()) return null;
        
        int attempts = 0;
        while (attempts < 5 && running) {
            int size = population.size();
            if (size <= 1) return null;
            
            int index = ThreadLocalRandom.current().nextInt(size);
            Immortal other = population.get(index);
            
            if (other != this && other.isAlive()) {
                return other;
            }
            attempts++;
        }
        return null;
    }

    private void fightNaive(Immortal other) {
        synchronized (this) {
            synchronized (other) {
                if (this.health <= 0 || other.health <= 0) return;
                
                other.health -= damage;
                this.health += damage / 2;
                
                if (other.health <= 0) {
                    deadQueue.offer(other);
                    other.stop();
                }
                if (this.health <= 0) {
                    deadQueue.offer(this);
                    this.stop();
                }
                
                scoreBoard.recordFight();
            }
        }
    }

    private void fightOrdered(Immortal other) {
        Immortal first = this.id < other.id ? this : other;
        Immortal second = this.id < other.id ? other : this;
        
        synchronized (first) {
            synchronized (second) {
                if (this.health <= 0 || other.health <= 0) return;
                
                other.health -= damage;
                this.health += damage / 2;
                
                if (other.health <= 0) {
                    deadQueue.offer(other);
                    other.stop();
                }
                if (this.health <= 0) {
                    deadQueue.offer(this);
                    this.stop();
                }
                
                scoreBoard.recordFight();
            }
        }
    }

    private void fightTryLock(Immortal other) {
        // Implementación similar...
    }
    
}