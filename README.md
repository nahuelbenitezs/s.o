# Sistema concurrente de intercepción de amenazas aéreas

Primera versión ejecutable (Segundo Avance) — Sistemas Operativos, UCU 2026.

## Cómo compilar y ejecutar

Necesitás tener instalado el **JDK** (Java 11 o superior). Verificá con:

```
java -version
javac -version
```

Si no lo tenés, instalá un JDK (por ejemplo Temurin/Adoptium o el de Oracle).

Parado en la carpeta `codigo/`:

```
javac *.java
java Simulador amenazas.txt
```

También se pueden pasar parámetros opcionales (archivo, cantidad de
interceptores, tiempo de recarga en ms):

```
java Simulador amenazas.txt 2 1000
```

## Qué hace

- **1 hilo Generador**: lee `amenazas.txt` y deposita amenazas en la cola.
- **N hilos Interceptor**: compiten por tomar amenazas de la cola compartida y
  las atienden durante un tiempo fijo de recarga.
- **1 hilo Monitor**: hace correr el tiempo; si a una amenaza pendiente se le
  agota el tiempo, la marca como **impactada**.

Recurso compartido: una `PriorityBlockingQueue` ordenada por la prioridad del
Primer Avance: `P = criticidad*100 + (1000 - tiempoRestante)`.

Sincronización: semáforo binario (`Semaphore(1)`) protegiendo los contadores en
`Estadisticas`, y variables atómicas (`AtomicReference` con `compareAndSet`) en
`Amenaza` para las transiciones de estado, sin usar `synchronized` (evitan
condiciones de carrera entre interceptores y monitor de forma lock-free).

Al terminar imprime un resumen: generadas, interceptadas, impactadas, tiempo
promedio de espera, criticidad total impactada y tasa de interceptación.

## Escenario de prueba (saturación)

Con 2 interceptores y 1000 ms de recarga, el sistema atiende ~2 amenazas por
segundo, pero llegan ~4 por segundo. Eso provoca **saturación**: la cola se
acumula y varias amenazas impactan. Así se ve que la planificación por
prioridad importa (los hospitales y centrales se atienden antes que la zona
industrial). Para comparar, se puede subir la cantidad de interceptores o el
tiempo de recarga y ver cómo cambian las métricas.

## Limitaciones conocidas (a mejorar para la entrega final)

Esta es una primera versión pensada para demostrar que el camino es correcto;
quedan cosas por pulir:

- **Una sola estrategia de planificación** implementada (la fórmula combinada).
  La letra pide al menos dos para comparar; se agregarán (ej.: solo por menor
  tiempo restante, solo por criticidad) y un menú para elegirla.
- La prioridad usa `tiempoRestante`, que el Monitor va modificando. La
  `PriorityBlockingQueue` no reordena automáticamente al cambiar ese valor; el
  orden se fija al insertar. Para la versión final se evaluará recalcular el
  orden o usar otra estructura.
- El Monitor usa un *busy-wait* simple con `sleep` en lugar de variables de
  condición más finas.
- El fin de la simulación se detecta por sondeo (polling) cada 100 ms.
