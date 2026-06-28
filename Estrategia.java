/*
 * Estrategias de planificacion disponibles para ordenar la cola de amenazas.
 *
 * Cada constante expone un nombre legible y una descripcion para que el
 * Simulador pueda mostrarlas en el menu y registrarlas en el log.
 *
 *  MENOR_TIEMPO   : Shortest Remaining Time (SRT/EDF).
 *                   Atiende primero la amenaza con menor tiempo restante.
 *                   Minimiza la cantidad de impactos cuando los tiempos son
 *                   homogeneos, pero puede dejar sin atender zonas criticas.
 *
 *  MAYOR_CRITICIDAD : Priority Scheduling por zona.
 *                   Atiende siempre la zona mas importante.
 *                   Protege la infraestructura critica pero puede dejar
 *                   expirar amenazas a zonas de menor criticidad.
 *
 *  COMBINADA      : Weighted formula (Primer Avance).
 *                   P = criticidad*100 + (1000 - tiempoRestante) * (velocidadMisil*danioMisil)
 *                   Balancea criticidad y urgencia; el factor del tipo de misil
 *                   eleva la prioridad de misiles de mayor peligrosidad.
 *
 *  DANIO_ESPERADO : Damage-based scheduling.
 *                   P = criticidad * factorDanio * (1000 / max(tiempoRestante,1))
 *                   Minimiza el danio total esperado: privilegia alta criticidad,
 *                   alto danio y urgencia temporal de forma multiplicativa.
 *                   Es la estrategia "optima" en escenarios heterogeneos.
 *
 *  MLQ            : Multi-Level Queue (Colas de Multiples Niveles).
 *                   En vez de una sola formula, separa las amenazas en 3
 *                   colas segun la criticidad de su zona, cada una con su
 *                   propio algoritmo interno:
 *                     - ALTA  (Hospital, Central Electrica): EDF, por menor
 *                       tiempo restante.
 *                     - MEDIA (Datacenter): FCFS, por orden de llegada.
 *                     - BAJA  (Residencial, Industrial): FCFS con rotacion
 *                       circular entre las pendientes de ese nivel.
 *                   Siempre se atiende primero el nivel ALTA si tiene
 *                   amenazas pendientes; solo si esta vacio se pasa a MEDIA,
 *                   y asi sucesivamente. El Simulador implementa esto con
 *                   3 PriorityBlockingQueue separadas en vez de una sola.
 */
public enum Estrategia {

    MENOR_TIEMPO(
        "Menor Tiempo Restante (EDF)",
        "Atiende primero la amenaza con menor tiempo hasta el impacto."
    ),
    MAYOR_CRITICIDAD(
        "Mayor Criticidad de Zona",
        "Atiende primero la zona de mayor importancia, independientemente del tiempo."
    ),
    COMBINADA(
        "Combinada: criticidad + urgencia + tipo misil",
        "P = criticidad*100 + (1000-t)*(velocidad*danio). Balancea zona, urgencia y peligrosidad."
    ),
    DANIO_ESPERADO(
        "Minimo Danio Esperado (MDsched)",
        "P = criticidad * factorDanio * (1000/t). Minimiza el danio total acumulado."
    ),
    MLQ(
        "Colas de Multiples Niveles (3 colas)",
        "ALTA=EDF, MEDIA=FCFS, BAJA=FCFS con rotacion. Se atiende siempre el nivel mas alto con pendientes."
    );

    private final String nombre;
    private final String descripcion;

    Estrategia(String nombre, String descripcion) {
        this.nombre      = nombre;
        this.descripcion = descripcion;
    }

    public String getNombre() {
        return nombre;
    }
    public String getDescripcion() {
        return descripcion;
    }

    //Imprime el menu numerado de estrategias
    public static void imprimirMenu() {
        System.out.println("\n  Estrategias de planificacion disponibles:");
        for (Estrategia e : values()) {
            System.out.printf("    [%d] %s%n        %s%n",
                    e.ordinal() + 1, e.getNombre(), e.getDescripcion());
        }
    }
}
