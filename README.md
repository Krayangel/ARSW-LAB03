
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
Resolucion:
### Finalización anticipada

La finalización temprana se logra mediante un contador compartido AtomicInteger globalOccurrences.
Cada hilo (BlackListSearcher) verifica antes y durante su ciclo de búsqueda si el contador global ya alcanzó el límite. Cuando esto ocurre:

* El hilo actual se detiene inmediatamente.

* El hilo coordinador (HostBlackListsValidator) notifica a los demás hilos para que finalicen su ejecución.

* No se siguen consultando servidores restantes.

Esto permite reducir significativamente el tiempo de ejecución para IPs maliciosas.

### Ausencia de condiciones de carrera

La concurrencia se maneja de forma segura usando:

* AtomicInteger, que garantiza incrementos atómicos del contador compartido.

* Verificación del límite (globalOccurrences.get() >= BLACK_LIST_ALARM_COUNT) antes de continuar cada iteración.

* Una bandera volatile (shouldStop) que asegura visibilidad inmediata del estado de parada entre hilos.

De esta forma, no hay inconsistencias en el conteo ni accesos concurrentes inseguros.

### Comportamiento observado

Las pruebas confirmaron que:

* Para IPs maliciosas, la búsqueda se detiene inmediatamente al alcanzar 5 ocurrencias.

* Para IPs confiables, se recorren todos los servidores asignados.

* No se pierden resultados encontrados antes de la parada.

* El uso de CPU se mantiene eficiente, sin sobrecarga innecesaria.

Se puede evidenciar el proceso de codigo en el siguiente repositorio:
> https://github.com/Krayangel/ARSW-Lab1.git



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


### Resolucion:
Descripción de la Simulación

La simulación modela N inmortales, cada uno ejecutándose en su propio hilo. Cada inmortal selecciona un oponente aleatorio y realiza un ataque en el que resta M puntos de vida al oponente y suma M/2 puntos a su propia vida. Este proceso se repite continuamente mientras la simulación está en ejecución.

Invariante del Sistema

Dado un número N de inmortales con salud inicial H, la suma total de salud esperada es N × H. Este valor se utiliza como referencia para validar la consistencia del sistema al momento de pausar la simulación. En la implementación actual, la fórmula de combate (-M + M/2) provoca una pérdida neta de salud, por lo que el invariante no se mantiene estrictamente, lo cual es intencional y sirve como evidencia para el análisis de concurrencia.

Prueba “Pause & Check”

Al ejecutar la interfaz gráfica y utilizar el botón “Pause & Check”, se pausa la simulación y se calcula la salud total de todos los inmortales. Se observó que, antes de corregir la pausa, el valor obtenido variaba entre ejecuciones debido a que algunos hilos seguían ejecutándose mientras se leía el estado. Tras implementar una pausa correcta, el valor mostrado es consistente entre pausas consecutivas, aunque no coincide con el valor teórico del invariante debido a la lógica de combate.

Pausa Correcta y Resume

Se implementó un mecanismo de pausa centralizado que garantiza que todos los hilos de los inmortales se encuentren detenidos antes de leer o imprimir la salud total. Para ello, los hilos verifican periódicamente el estado de pausa y reaccionan a interrupciones. El botón Resume permite reanudar la ejecución sin inconsistencias. Al hacer clic repetido en Pause y Resume, el sistema mantiene un estado coherente y no se observan lecturas “a medias”.

Regiones Críticas y Sincronización

Las secciones de código donde ocurre el combate entre dos inmortales fueron identificadas como regiones críticas, ya que involucran lectura y escritura concurrente sobre el estado de ambos participantes. Estas secciones fueron sincronizadas adecuadamente para evitar condiciones de carrera. Cuando se requirió usar múltiples locks, se aplicó un orden consistente basado en el identificador de los inmortales, garantizando que todos los hilos adquieren los locks en el mismo orden.

Deadlocks: Diagnóstico y Corrección

En el modo de sincronización ingenuo, la aplicación puede detenerse debido a deadlocks causados por la adquisición cruzada de locks. Cuando esto ocurrió, se utilizó jps para identificar el proceso Java y jstack para analizar el volcado de hilos, donde se evidenció un deadlock a nivel de JVM. Para corregirlo, se implementaron estrategias anti-deadlock como el orden total de locks y, alternativamente, el uso de tryLock con timeout y reintentos, eliminando la espera indefinida.

Validación con N Grande

La simulación fue probada con 100, 1000 y hasta 10000 inmortales. En estos escenarios, la aplicación se mantiene estable, la pausa funciona correctamente y no se presentan deadlocks cuando se utilizan las estrategias de sincronización adecuadas. Si el invariante no se mantiene, se verifica que la causa sea la lógica de combate y no un error de concurrencia.

Remoción de Inmortales Muertos

La eliminación de inmortales cuya salud llega a cero se realizó sin bloquear la simulación completa. Para evitar condiciones de carrera y cuellos de botella, se utilizó una colección concurrente para registrar los inmortales muertos y limpiarlos de la población de forma segura, sin necesidad de sincronización global.

Implementación de STOP (Apagado Ordenado)

Se implementó un apagado ordenado que señaliza a todos los hilos que deben finalizar su ejecución, interrumpe aquellos que están bloqueados o en espera y espera su terminación con un timeout razonable. Finalmente, se liberan los recursos y se deja la aplicación en un estado consistente.

Invariante del Sistema

Aunque el invariante teórico es N × H, la fórmula de combate actual introduce una pérdida neta de M/2 por ataque, lo que implica que la salud total disminuye de forma monotónica.

Esto permite diferenciar claramente entre:

Errores de lógica del modelo

Errores de sincronización concurrente

Si el valor de salud total varía entre dos pausas consecutivas, el problema es de concurrencia; si es consistente pero menor al esperado, el problema es del modelo.

 Dato curioso: incluso con sincronización perfecta, el invariante no se cumple por diseño.

⏸ Pause & Check

Una pausa mal implementada puede generar lecturas intermedias, donde algunos hilos ya modificaron el estado y otros no.

Esto produce valores distintos sin que exista una carrera explícita, lo cual es una de las trampas más comunes en concurrencia.

La solución no es solo “pausar”, sino garantizar que todos los hilos estén efectivamente detenidos antes de leer el estado compartido.

 Dato curioso: Thread.sleep() no garantiza que un hilo pueda pausar inmediatamente; por eso la interrupción es clave.

 Pause / Resume Repetido

Al presionar Pause y Resume múltiples veces, se validó que:

No se acumulan estados inconsistentes

No se producen pérdidas de señal (lost wake-ups)

Esto confirma que el mecanismo de pausa no depende del timing, sino del estado global de control.

 Dato curioso: muchos sistemas concurrentes funcionan bien “una vez”, pero fallan al repetir la operación.

 Regiones Críticas

El combate involucra dos objetos compartidos, lo que lo convierte en una región crítica compuesta.

Sin sincronización, pueden ocurrir:

Daño aplicado dos veces

Salud negativa

Lecturas inconsistentes

La sincronización se realizó solo sobre los objetos necesarios, evitando un lock global que degradaría el rendimiento.

 Dato curioso: sincronizar “de más” elimina errores… pero mata la escalabilidad.

 Orden Total de Locks

El uso de un orden basado en ID garantiza que no exista espera circular, condición necesaria para un deadlock.

Todos los hilos adquieren los locks en el mismo orden, independientemente de quién ataque a quién.

 Dato curioso: esta técnica es exactamente la misma que se usa en bases de datos para evitar deadlocks.

 Deadlocks y Diagnóstico

En el modo ingenuo, se observaron deadlocks reales detectables por la JVM.

Con jstack, se identificó claramente el ciclo de espera entre hilos.

Esto permitió confirmar que el problema no era de rendimiento, sino de bloqueo mutuo.

 Dato curioso: Java puede detectar deadlocks automáticamente a nivel de JVM, pero no los corrige.

 TryLock y Timeouts

El uso de tryLock(timeout) evita la espera indefinida.

En caso de fallo, el hilo libera recursos y reintenta, reduciendo la probabilidad de bloqueo total.

Se puede aplicar backoff para disminuir la contención en escenarios con muchos hilos.

 Dato curioso: este patrón es muy usado en sistemas financieros y motores de juegos.

 Escalabilidad (N Grande)

Con 10.000 hilos, el cuello de botella no es la sincronización, sino:

Cambio de contexto

Consumo de CPU

El diseño evita locks globales, lo que permite que la simulación siga siendo funcional.

 Dato curioso: más hilos no siempre significa más rendimiento; a partir de cierto punto, empeora.

 Remoción de Inmortales Muertos

Remover elementos de una colección compartida mientras otros hilos la recorren es una fuente clásica de errores.

El uso de colecciones concurrentes permite:

Eliminación segura

Sin bloquear la simulación completa

Se evita ConcurrentModificationException.

 Dato curioso: muchas aplicaciones fallan solo cuando el sistema “envejece” y empieza a eliminar objetos.

 STOP (Apagado Ordenado)

El apagado no se basa en System.exit(), sino en:

Señales de parada

Interrupciones

Espera controlada de finalización

Esto garantiza que no queden hilos zombie ni recursos sin liberar.

 Dato curioso: apagar mal un sistema concurrente es una de las principales causas de fugas de memoria.
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
