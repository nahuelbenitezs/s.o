# Sistema concurrente de intercepción de amenazas aéreas

Sistemas Operativos.

## Cómo compilar y ejecutar

Necesitás el **JDK** (Java 8 o superior):

```
javac *.java
java Simulador
```

### Parámetros

```
java Simulador [archivo] [N] [recargaMs] [estrategia]
```

- **archivo**: escenario de amenazas (default `amenazas.txt`).
- **N**: cantidad de interceptores (default 2).
- **recargaMs**: tiempo fijo de recarga/servicio (default 1000).
- **estrategia**:
  - `1` = Prioridades (Event-Driven) con envejecimiento (default)
  - `2` = MLQ — Colas de Múltiples Niveles (3 colas)
  - `3` = EDF no apropiativo (menor tiempo hasta el impacto)

### Comparación automática

```
java Simulador batch amenazas.txt
java Simulador batch amenazas_critico.txt
```

Corre todas las estrategias por varios tiempos de recarga e imprime una tabla
comparativa (generadas, interceptadas, impactadas, espera, tasa, criticidad
impactada y utilización).

## Arquitectura

Despachador central con reloj virtual (simulación por eventos discretos):

- **1 hilo Generador** (productor): lee el archivo y deposita las llegadas en una
  cola compartida. Sincroniza con el despachador mediante **Productor-Consumidor
  con semáforos** (`mutex` + semáforo contador `items`).
- **1 hilo Despachador** (consumidor + planificador): decide a qué amenaza atender
  (según la estrategia) y le entrega el trabajo a un interceptor libre. Es el único
  que adelanta el reloj y el único que imprime, por lo que la salida queda
  **ordenada en el tiempo** y la simulación es **determinista**.
- **N hilos Interceptor** (recursos): cada interceptor es un hilo propio que espera
  una asignación, ejecuta la recarga y, al terminar, **libera** (`release()`) el
  semáforo contador `Semaphore(N)`. El Despachador **reserva** un permiso con
  `tryAcquire()` (no bloqueante) al asignar y cada Interceptor lo libera al
  terminar. Las amenazas compiten por una capacidad limitada de N recursos; el
  Despachador resuelve esa competencia con la estrategia y asigna el trabajo a uno
  de los N hilos Interceptor (como el Ejercicio 4 "Datacenter" del curso).

El tiempo avanza por **eventos** (llegada, fin de servicio, impacto): el reloj
salta al próximo evento, **sin `Thread.sleep`**.

### Sincronización (mecanismos vistos en clase)

- **Semáforo contador `Semaphore(N)`** para los recursos de intercepción
  (`tryAcquire()` por el Despachador, `release()` por cada Interceptor).
- **Productor-Consumidor con semáforos** entre Generador y Despachador.
- **Sección crítica con semáforo binario (mutex)** para los contadores de
  estadísticas (los escriben Generador, Interceptores y Despachador).
- En Java: `java.util.concurrent.Semaphore` con `acquire()` (= `P`/`Wait`) y
  `release()` (= `V`/`Signal`).

## Formato del archivo de escenario

```
ZONA;tiempoRestanteMs[;instanteLlegadaMs]
```

- Zonas válidas: `HOSPITAL`, `CENTRAL_ELECTRICA`, `DATACENTER`, `RESIDENCIAL`,
  `INDUSTRIAL`.
- El tercer campo (instante de llegada, en ms virtuales) es opcional; si no
  está, se calcula como `índice * 250 ms`.
- Las líneas vacías o que empiezan con `#` se ignoran.

Hay dos escenarios: `amenazas.txt` (general) y `amenazas_critico.txt` (diseñado
para comparar estrategias: muestra cómo EDF sacrifica zonas críticas que la
prioridad por criticidad protege).

## Fórmula de prioridad

```
P = criticidad * 10000 + (5000 - tiempoRestante) + envejecimiento
```

El factor `10000` (≥ rango de la urgencia) hace que la **criticidad domine** sobre
la urgencia. El **envejecimiento** reduce el riesgo de inanición (sobre todo entre
amenazas de igual criticidad). La prioridad se **recalcula en cada decisión**.

## Estrategias

| # | Nombre | Detalle |
|---|--------|---------|
| 1 | Prioridades (Event-Driven) con envejecimiento | fórmula combinada |
| 2 | MLQ (3 colas) | ALTA = EDF, MEDIA = FCFS, BAJA = rotación circular |
| 3 | EDF no apropiativo | menor tiempo hasta el impacto (deadline), sin criticidad |


## Estadísticas

Generadas, interceptadas, impactadas, tiempo promedio de espera y de retorno
(turnaround), tasa de interceptación, throughput, **utilización de los
interceptores** y **criticidad total impactada**.
