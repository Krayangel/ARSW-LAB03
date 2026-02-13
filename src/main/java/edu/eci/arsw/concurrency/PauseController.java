package edu.eci.arsw.concurrency;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class PauseController {
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition unpaused = lock.newCondition();
    private volatile boolean paused = false;
    private final AtomicInteger pausedThreads = new AtomicInteger(0);
    private volatile int totalThreads = 0;

    // Lista de threads a interrumpir
    private Thread[] threads;

    public void setTotalThreads(int total) {
        lock.lock();
        try {
            this.totalThreads = total;
            this.threads = new Thread[total];
            pausedThreads.set(0);
            System.out.println("[PAUSE] Configurados " + total + " threads");
        } finally {
            lock.unlock();
        }
    }

    // Registrar thread cuando inicia
    public void registerThread(Thread thread, int index) {
        if (index >= 0 && index < threads.length) {
            threads[index] = thread;
        }
    }

    public void removeThread() {
        lock.lock();
        try {
            totalThreads--;
            if (paused && pausedThreads.get() >= totalThreads) {
                System.out.println("[PAUSE] ✅ TODOS PAUSADOS (por salida de thread) (" + pausedThreads.get() + "/"
                        + totalThreads + ")");
            }
        } finally {
            lock.unlock();
        }
    }

    public void pause() {
        lock.lock();
        try {
            if (!paused) {
                System.out.println("\n=== INICIANDO PAUSA ===");
                paused = true;
                pausedThreads.set(0);

                // INTERRUMPIR TODOS LOS THREADS PARA QUE DESPIERTEN
                System.out.println("[PAUSE] Interrumpiendo " + totalThreads + " threads...");
                for (int i = 0; i < threads.length; i++) {
                    if (threads[i] != null && threads[i].isAlive()) {
                        threads[i].interrupt();
                        System.out.println("[PAUSE] Interrumpido: " + threads[i].getName());
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void resume() {
        lock.lock();
        try {
            if (paused) {
                System.out.println("\n=== REANUDANDO ===");
                paused = false;
                unpaused.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean paused() {
        return paused;
    }

    public void awaitIfPaused() throws InterruptedException {
        if (!paused)
            return;

        lock.lock();
        try {
            if (paused) {
                int current = pausedThreads.incrementAndGet();
                String threadName = Thread.currentThread().getName();

                System.out.println("[PAUSE] " + threadName + " se pausó (" + current + "/" + totalThreads + ")");

                // Si somos el último, notificar
                if (current >= totalThreads) {
                    System.out.println("[PAUSE] ✅ TODOS PAUSADOS (" + current + "/" + totalThreads + ")");
                }

                // Esperar
                while (paused) {
                    unpaused.await();
                }

                pausedThreads.decrementAndGet();
                System.out.println("[PAUSE] " + threadName + " reanudado");
            }
        } finally {
            lock.unlock();
        }
    }

    // Esperar con timeout
    public boolean waitForAllPaused(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (pausedThreads.get() < totalThreads) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                System.out.println("[PAUSE] ⏰ TIMEOUT: Solo " + pausedThreads.get() + "/" + totalThreads + " pausados");
                return false;
            }

            // Mostrar progreso
            System.out.println("[PAUSE] Progreso: " + pausedThreads.get() + "/" + totalThreads);

            // Pequeña espera
            Thread.sleep(100);
        }

        System.out.println("[PAUSE] ✅ PAUSA COMPLETA: " + pausedThreads.get() + "/" + totalThreads);
        return true;
    }

    public int getPausedThreadsCount() {
        return pausedThreads.get();
    }

    public int getTotalThreads() {
        return totalThreads;
    }

    public void forceResume() {
        lock.lock();
        try {
            paused = false;
            unpaused.signalAll();
        } finally {
            lock.unlock();
        }
    }
}