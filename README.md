# Sistema Concurrente de Intercepcion de Amenazas Aereas
**Sistemas Operativos — UCU 2026 — Entrega Final**

Integrantes: María Belén Mendes · Nahuel Benítez · Franco Di Salvatore

---

## Compilacion y ejecucion

Requiere **JDK 11 o superior**. Verificar con `java -version` y `javac -version`.

```bash
# Compilar todos los fuentes
javac *.java

# Modo interactivo (menu de escenarios y estrategias)
java Simulador

# Modo directo: archivo, interceptores, recarga, estrategia (1-4)
java Simulador amenazas.txt 2 1000 3

# Modo batch: ejecuta las 4 estrategias y muestra tabla comparativa
java Simulador amenazas.txt 2 1000 --batch
```

---

## Escenarios incluidos

| Archivo                        | Descripcion                                              |
|-------------------------------|----------------------------------------------------------|
| `amenazas.txt`                | Saturacion mixta (12 amenazas, tipos variados)           |
| `escenario_borde_critico.txt` | Solo zonas criticas, tiempos muy cortos                  |
| `escenario_hipersonico.txt`   | Misiles hipersonicos (doble velocidad de descuento)      |
| `escenario_borde_limite.txt`  | 1 interceptor, recarga 1500ms (usar con `1 1500`)        |

---

## Estrategias de planificacion

| # | Nombre                         | Formula / Logica                                               |
|---|-------------------------------|----------------------------------------------------------------|
| 1 | Menor Tiempo Restante (EDF)   | `P = (10000/t) * velocidadMisil`                              |
| 2 | Mayor Criticidad de Zona      | `P = criticidad*1000 + 1000/t`                                |
| 3 | Combinada (Primer Avance)     | `P = criticidad*100 + (1000-t)*(velocidad*danio)`             |
| 4 | Minimo Danio Esperado (MDsched)| `P = criticidad * factorDanio * (10000/t)`                   |

---

## Tipos de misil

| Tipo          | Velocidad | Factor danio | Efecto                                 |
|---------------|-----------|-------------|----------------------------------------|
| CONVENCIONAL  | 1.0x      | 1.0x        | Estandar                               |
| HIPERSONICO   | 2.0x      | 1.5x        | El tiempo se consume el doble de rapido |
| BALÍSTICO     | 1.5x      | 2.0x        | Danio critico si impacta               |
| CRUCERO       | 0.8x      | 1.2x        | Mas lento pero dificulta la intercepcion|

---

## Sincronizacion (sin `synchronized`)

- **Semaforo binario** (`Semaphore(1)`) en `Estadisticas`: exclusion mutua sobre los contadores compartidos.
- **`AtomicReference` + `compareAndSet`** en `Amenaza`: transiciones de estado lock-free. Resuelve la condicion de carrera entre el interceptor (`PENDIENTE→EN_PROCESO`) y el monitor (`PENDIENTE→IMPACTADA`): solo uno de los dos gana el CAS.

---

## Formato del archivo de escenario

```
# Comentario
ZONA;tiempoRestanteMs[;TIPO_MISIL]
```

Zonas validas: `HOSPITAL`, `CENTRAL_ELECTRICA`, `DATACENTER`, `RESIDENCIAL`, `INDUSTRIAL`  
Tipos de misil: `CONVENCIONAL`, `HIPERSONICO`, `BALÍSTICO`, `CRUCERO` (opcional, default: CONVENCIONAL)
