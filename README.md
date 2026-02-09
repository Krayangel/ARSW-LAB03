
# ARSW — (Java 21): **Immortals & Synchronization** — con UI Swing

**Escuela Colombiana de Ingeniería – Arquitecturas de Software**  
Laboratorio de concurrencia: condiciones de carrera, sincronización, suspensión cooperativa y *deadlocks*, con interfaz **Swing** tipo *Highlander Simulator*.

--- 
### Particicpantes:
1. Juan Esteban Lozano Cardenas 
2. David Santiago Villadiego Medicis
---

## Requisitos

- **JDK 21** (Temurin recomendado)
- **Maven 3.9+**
- SO: Windows, macOS o Linux

---

## Cómo ejecutar

### Interfaz gráfica (Swing) — *Highlander Simulator*

**Opción A (desde `Main`, modo `ui`)**
```bash
mvn -q -DskipTests exec:java -Dmode=ui -Dcount=8 -Dfight=ordered -Dhealth=100 -Ddamage=10
```

**Opción B (clase de la UI directamente)**
```bash
mvn -q -DskipTests exec:java   -Dexec.mainClass=edu.eci.arsw.highlandersim.ControlFrame   -Dcount=8 -Dfight=ordered -Dhealth=100 -Ddamage=10
```

**Parámetros**  
- `-Dcount=N` → número de inmortales (por defecto 8)  
- `-Dfight=ordered|naive` → estrategia de pelea (`ordered` evita *deadlocks*, `naive` los puede provocar)  
- `-Dhealth`, `-Ddamage` → salud inicial y daño por golpe

### Demos teóricas (sin UI)
```bash
mvn -q -DskipTests exec:java -Dmode=demos -Ddemo=1  # 1 = Deadlock ingenuo
mvn -q -DskipTests exec:java -Dmode=demos -Ddemo=2  # 2 = Orden total (sin deadlock)
mvn -q -DskipTests exec:java -Dmode=demos -Ddemo=3  # 3 = tryLock + timeout (progreso)
```

---

## Controles en la UI

- **Start**: inicia una simulación con los parámetros elegidos.
- **Pause & Check**: pausa **todos** los hilos y muestra salud por inmortal y **suma total** (invariante).
- **Resume**: reanuda la simulación.
- **Stop**: detiene ordenadamente.

**Invariante**: con N jugadores y salud inicial H, la **suma total** de salud debe permanecer constante (salvo durante un update en curso). Usa **Pause & Check** para validarlo.

---

## Arquitectura (carpetas)

```
edu.eci.arsw
├─ app/                 # Bootstrap (Main): modes ui|immortals|demos
├─ highlandersim/       # UI Swing: ControlFrame (Start, Pause & Check, Resume, Stop)
├─ immortals/           # Dominio: Immortal, ImmortalManager, ScoreBoard
├─ concurrency/         # PauseController (Lock/Condition; paused(), awaitIfPaused())
├─ demos/               # DeadlockDemo, OrderedTransferDemo, TryLockTransferDemo
└─ core/                # BankAccount, TransferService (para demos teóricas)
```

---

# Actividades del laboratorio

## Parte I — (Antes de terminar la clase) `wait/notify`: Productor/Consumidor
1. Ejecuta el programa de productor/consumidor y monitorea CPU con **jVisualVM**. ¿Por qué el consumo alto? ¿Qué clase lo causa?   

* prueba del consumo:
  ![](/img/visualVm.png)
  ![](/img/LA3.2.1.png)

* Explicacion del consumo

  Al ejecutar la aplicación en modo MONITOR con 1 productor y 1 consumidor, se observó un comportamiento estable y eficiente, eEn una ejecución se produjeron y consumieron 1274 elementos, y en otra 1702 elementos, finalizando siempre con QueueSize = 0, lo que indica que no hubo pérdidas ni bloqueos indebidos, dDurante estas pruebas, VisualVM mostró un bajo consumo de CPU, consistente con el uso de wait() y notify() para suspender y reactivar hilos únicamente cuando es necesario.

  Cuando se incrementó el número de productores a 1000 manteniendo 1 consumidor, el programa continuó ejecutándose correctamente sin saturar el procesador ni generar errores, lo que confirma que el uso de monitores permite escalar la concurrencia de forma segura y eficiente.

  En contraste con el modo SPIN, donde el consumo de CPU es alto debido a la espera activa, el modo MONITOR demuestra un uso mucho más eficiente de los recursos.
  
  La causa del alto consumo en SPIN es la clase BusySpinQueue, cuyos métodos put() y take() utilizan ciclos de espera activa (while) en lugar de bloquear los hilos.


2. Ajusta la implementación para **usar CPU eficientemente** cuando el **productor es lento** y el **consumidor es rápido**. Valida de nuevo con VisualVM.  

* Prueba de rendimiento
![](/img/LAb3.2.2.png)

* Explicacion:

  Para eliminar el problema de busy-waiting presente en BusySpinQueue, se reemplazó la espera activa por una implementación basada en ReentrantLock y Condition. Con este enfoque, los hilos productores y consumidores se bloquean de forma eficiente cuando la cola está llena o vacía, evitando ciclos de CPU innecesarios.

  Tras compilar la versión modificada, se ejecutó el programa con un escenario de alta concurrencia (1000 productores y 1 consumidor), obteniendo el siguiente resultado:

  PCApp mode=monitor producers=1000 consumers=1 capacity=16 
  prodDelay=10ms consDelay=10ms duration=20s


  Durante la ejecución, en VisualVM se observó un consumo de CPU bajo, confirmando que los hilos consumidores se bloquean correctamente cuando la cola está vacía y los productores cuando está llena. El sistema se mantuvo estable, sin bloqueos ni sobrecarga del procesador.

  Además, el comportamiento fue consistente con lo esperado:

  A. La cantidad de elementos producidos y consumidos fue equivalente.
 
  . El tamaño de la cola nunca superó la capacidad definida.

  C. La ejecución finalizó correctamente, aun con un número elevado de productores.


3. Ahora **productor rápido** y **consumidor lento** con **límite de stock** (cola acotada): garantiza que el límite se respete **sin espera activa** y valida CPU con un stock pequeño.

* Pruebas de rendimiento:

![](/img/LA3.2.3.png)

* Explicacion:

  En este punto se validó que la clase BoundedBuffer respeta correctamente el límite de capacidad cuando existen productores rápidos y consumidores lentos, sin incurrir en espera activa y manteniendo un bajo consumo de CPU.

  La implementación de BoundedBuffer utiliza monitores de Java (synchronized, wait() y notifyAll()), donde antes de insertar un elemento se verifica la condición while (q.size() == capacity). Si el buffer está lleno, el productor se bloquea con wait(), liberando el monitor y evitando consumo innecesario de CPU. De forma análoga, los consumidores se bloquean cuando el buffer está vacío.

  La prueba se ejecutó con 3 productores rápidos, 1 consumidor lento, una capacidad pequeña (3) y una duración de 20 segundos, usando el modo monitor. Durante la ejecución se observó que:

  * El tamaño de la cola nunca superó la capacidad definida.

  * Los productores se bloquearon correctamente cuando el buffer estaba lleno.

  * El consumidor avanzó más lento, lo cual explica que Produced > Consumed.

  * El consumo de CPU observado en VisualVM fue bajo (≈ 0–10%), sin picos sostenidos.

  Al finalizar, los resultados mostraron un comportamiento esperado del patrón productor–consumidor:

  * QueueSize se mantuvo en el límite máximo cuando el consumidor era más lento.

  * No hubo bloqueos incorrectos ni sobreproducción.

  * El sistema fue estable incluso bajo desbalance entre velocidades.



> Nota: la Parte I se realiza en el repositorio dedicado https://github.com/DECSIS-ECI/Lab_busy_wait_vs_wait_notify — clona ese repo y realiza los ejercicios allí; contiene el código de productor/consumidor, variantes con busy-wait y las soluciones usando wait()/notify(), además de instrucciones para ejecutar y validar con jVisualVM.


> Usa monitores de Java: **`synchronized` + `wait()` + `notify/notifyAll()`**, evitando *busy-wait*.

---

## Parte II — (Antes de terminar la clase) Búsqueda distribuida y condición de parada
Reescribe el **buscador de listas negras** para que la búsqueda **se detenga tan pronto** el conjunto de hilos detecte el número de ocurrencias que definen si el host es confiable o no (`BLACK_LIST_ALARM_COUNT`). Debe:
- **Finalizar anticipadamente** (no recorrer servidores restantes) y **retornar** el resultado.  
- Garantizar **ausencia de condiciones de carrera** sobre el contador compartido.

> Puedes usar `AtomicInteger` o sincronización mínima sobre la región crítica del contador.

---

## Parte III — (Avance) Sincronización y *Deadlocks* con *Highlander Simulator*
1. Revisa la simulación: N inmortales; cada uno **ataca** a otro. El que ataca **resta M** al contrincante y **suma M/2** a su propia vida.  
2. **Invariante**: con N y salud inicial `H`, la suma total debería permanecer constante (salvo durante un update). Calcula ese valor y úsalo para validar.  
3. Ejecuta la UI y prueba **“Pause & Check”**. ¿Se cumple el invariante? Explica.  
4. **Pausa correcta**: asegura que **todos** los hilos queden pausados **antes** de leer/imprimir la salud; implementa **Resume** (ya disponible).  
5. Haz *click* repetido y valida consistencia. ¿Se mantiene el invariante?  
6. **Regiones críticas**: identifica y sincroniza las secciones de pelea para evitar carreras; si usas múltiples *locks*, anida con **orden consistente**:
   ```java
   synchronized (lockA) {
     synchronized (lockB) {
       // ...
     }
   }
   ```
7. Si la app se **detiene** (posible *deadlock*), usa **`jps`** y **`jstack`** para diagnosticar.  
8. Aplica una **estrategia** para corregir el *deadlock* (p. ej., **orden total** por nombre/id, o **`tryLock(timeout)`** con reintentos y *backoff*).  
9. Valida con **N=100, 1000 o 10000** inmortales. Si falla el invariante, revisa la pausa y las regiones críticas.  
10. **Remover inmortales muertos** sin bloquear la simulación: analiza si crea una **condición de carrera** con muchos hilos y corrige **sin sincronización global** (colección concurrente o enfoque *lock-free*).  
11. Implementa completamente **STOP** (apagado ordenado).

---

## Entregables

1. **Código fuente** (Java 21) con la UI funcionando.  
2. **`Informe de laboratorio en formato pdf`** con:
   - Parte I: diagnóstico de CPU y cambios para eliminar espera activa.  
   - Parte II: diseño de **parada temprana** y cómo evitas condiciones de carrera en el contador.  
   - Parte III:  
     - Regiones críticas y estrategia adoptada (**orden total** o **tryLock+timeout**).  
     - Evidencia de *deadlock* (si ocurrió) con `jstack` y corrección aplicada.  
     - Validación del **invariante** con **Pause & Check** (distintos N).  
     - Estrategia para **remover inmortales muertos** sin sincronización global.
3. Instrucciones de ejecución si cambias *defaults*.

---

## Criterios de evaluación (10 pts)

- (3) **Concurrencia correcta**: sin *data races*; sincronización bien localizada; no hay espera activa.  
- (2) **Pausa/Reanudar**: consistencia del estado e invariante bajo **Pause & Check**.  
- (2) **Robustez**: corre con N alto; sin `ConcurrentModificationException`, sin *deadlocks* no gestionados.  
- (1.5) **Calidad**: arquitectura clara, nombres y comentarios; separación UI/lógica.  
- (1.5) **Documentación**: **`RESPUESTAS.txt`** claro con evidencia (dumps/capturas) y justificación técnica.

---

## Tips y configuración útil

- **Estrategias de pelea**:  
  - `-Dfight=naive` → útil para **reproducir** carreras y *deadlocks*.  
  - `-Dfight=ordered` → **evita** *deadlocks* (orden total por nombre/id).
- **Pausa cooperativa**: usa `PauseController` (Lock/Condition), **sin** `suspend/resume/stop`.  
- **Colecciones**: evita estructuras no seguras; prefiere inmutabilidad o colecciones concurrentes.  
- **Diagnóstico**: `jps`, `jstack`, **jVisualVM**; revisa *thread dumps* cuando sospeches *deadlock*.  
- **Virtual Threads**: favorecen esperar con bloqueo (no *busy-wait*); usa timeouts.

---

## Cómo correr pruebas

```bash
mvn clean verify
```

Incluye compilación y pruebas JUnit.

---

## Créditos y licencia

Laboratorio basado en el enunciado histórico del curso (Highlander, Productor/Consumidor, Búsqueda distribuida), modernizado a **Java 21**.  
<a rel="license" href="http://creativecommons.org/licenses/by-nc/4.0/"><img alt="Creative Commons License" style="border-width:0" src="https://i.creativecommons.org/l/by-nc/4.0/88x31.png" /></a><br />Este contenido hace parte del curso Arquitecturas de Software (ECI) y está licenciado como <a rel="license" href="http://creativecommons.org/licenses/by-nc/4.0/">Creative Commons Attribution-NonCommercial 4.0 International License</a>.
