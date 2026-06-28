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
