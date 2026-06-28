import java.util.concurrent.atomic.AtomicReference;

/*
 * Representa una amenaza aerea entrante.
 *
 * Atributos minimos segun la letra:
 *   - identificador, zona objetivo, instante de aparicion,
 *     tiempo restante hasta el impacto y estado.
 *
 * Atributos adicionales:
 *   - TipoMisil : velocidad y factor de danio del misil.
 *
 * SINCRONIZACION (lock-free):
 *   El estado se guarda en un AtomicReference<Estado>. Las transiciones
 *   criticas usan compareAndSet (CAS): operacion atomica que compara y
 *   cambia en un solo paso de hardware, sin necesidad de synchronized.
 *   Esto resuelve la condicion de carrera entre el Interceptor
 *   (PENDIENTE->EN_PROCESO) y el Monitor (PENDIENTE->IMPACTADA):
 *   exactamente uno de los dos obtiene true, el otro descarta.
 *
 * CALCULO DE PRIORIDAD:
 *   Cada estrategia llama a prioridad(Estrategia) para obtener un valor
 *   comparable. La PriorityBlockingQueue se crea con un Comparator que
 *   usa este metodo, por lo que el ordenamiento es siempre correcto en el
 *   momento de la insercion. Para las amenazas que ya estan en la cola y
 *   cuyo tiempo cambia, el Simulador usa una PriorityBlockingQueue con
 *   re-oferta periodica (drainTo + re-insercion) al cambiar la estrategia
 *   en caliente; dentro de una misma corrida el orden de insercion es
 *   suficientemente estable.
 */
public class Amenaza {

    private final int       id;
    private final Zona      zona;
    private final TipoMisil tipoMisil;
    private final long      instanteAparicion;   // ms desde el inicio

    /*
     * tiempoRestante es volatil para que el Monitor lo lea siempre fresco
     * desde la memoria principal (sin caches de CPU), y la reduccion se
     * hace desde un unico hilo (Monitor), por lo que no hay race condition
     * en la escritura.
     */
    private volatile int tiempoRestante;

    private final AtomicReference<Estado> estado =
            new AtomicReference<>(Estado.PENDIENTE);

    public Amenaza(int id, Zona zona, TipoMisil tipoMisil,
                   int tiempoRestante, long instanteAparicion) {
        this.id                = id;
        this.zona              = zona;
        this.tipoMisil         = tipoMisil;
        this.tiempoRestante    = tiempoRestante;
        this.instanteAparicion = instanteAparicion;
    }

    // ------------------------------------------------------------------ //
    // Calculos de prioridad para cada estrategia                          //
    // ------------------------------------------------------------------ //

    /**
     * Devuelve la prioridad segun la estrategia activa.
     * Mayor valor = mayor prioridad de atencion.
     */
    public double prioridad(Estrategia estrategia) {
        int    t    = Math.max(tiempoRestante, 1); // evita division por cero
        int    crit = zona.getCriticidad();
        double vel  = tipoMisil.getVelocidad();
        double danio= tipoMisil.getFactorDanio();

        switch (estrategia) {

            case MENOR_TIEMPO:
                // Cuanto menor el tiempo, mayor la prioridad.
                // Si el misil es hipersonico, reducimos el tiempo efectivo
                // (llega antes) para que suba aun mas en la cola.
                return (10_000.0 / t) * vel;

            case MAYOR_CRITICIDAD:
                // Solo la criticidad de la zona importa.
                // Desempate por tiempo restante (amenazas mas urgentes primero).
                return crit * 1_000.0 + (1_000.0 / t);

            case COMBINADA:
                // Formula del Primer Avance, enriquecida con tipo de misil.
                //
                // BUG CORREGIDO (formula mal escalada):
                //   La version original era:
                //     P = criticidad*100 + (1000 - t) * (velocidad*danio)
                //   El termino (1000-t)*(velocidad*danio) NO esta acotado:
                //   con t grande (ej. 3000ms, comun en estos escenarios) o
                //   vel*danio alto (hasta 4.0 con BALISTICO), ese termino
                //   se va a rangos de miles, positivos o negativos, muy por
                //   encima de la diferencia entre dos escalones de
                //   criticidad (100 puntos). Se comprobo en ejecucion real
                //   que un Hospital con t=3000ms obtenia P=-1000 mientras
                //   una zona Industrial urgente obtenia P>0, invirtiendo el
                //   criterio que el propio informe (Primer Avance) declara:
                //   "criticidad*100 asegura que la zona pese mas que la
                //   urgencia en la mayoria de los casos".
                //
                // CORRECCION:
                //   Se acota la urgencia a un rango fijo [0,1000] (clamp de
                //   t entre 0 y 1000ms) y se normaliza el factor del misil
                //   contra su propio maximo conocido (vel*danio maximo =
                //   2.0*2.0 = 4.0, caso BALISTICO). El bonus resultante
                //   nunca supera 99 puntos, por lo que JAMAS puede superar
                //   un escalon completo de criticidad (100 puntos): la zona
                //   manda siempre, y la urgencia/tipo de misil solo
                //   desempata dentro de la misma zona o entre zonas de
                //   igual criticidad.
                double tClamp        = Math.max(0, Math.min(t, 1000));
                double urgenciaNorm  = (1000.0 - tClamp) / 1000.0;      // [0,1]
                double factorMisilNorm = (vel * danio) / 4.0;            // [0.2,1.0] aprox
                double bonusUrgencia = urgenciaNorm * factorMisilNorm * 99.0; // <100 siempre
                return (crit * 100.0) + bonusUrgencia;

            case DANIO_ESPERADO:
                // Minimiza el danio esperado total.
                // P = criticidad * factorDanio * (1000 / t)
                // Privilegia: zona critica + misil peligroso + urgente.
                return crit * danio * (10_000.0 / t);

            case MLQ:
            default:
                // MLQ no usa una formula unica de prioridad: separa las
                // amenazas en 3 colas por nivel de criticidad y cada cola
                // tiene su propio criterio interno (ver Simulador). Este
                // metodo no se llama bajo MLQ, pero se devuelve 0 en vez de
                // lanzar una excepcion para mantener el metodo total.
                return 0;
        }
    }

    // ------------------------------------------------------------------ //
    // Transiciones de estado (lock-free con CAS)                          //
    // ------------------------------------------------------------------ //

    /** Un interceptor intenta tomar la amenaza. True si lo logra. */
    public boolean intentarTomar() {
        return estado.compareAndSet(Estado.PENDIENTE, Estado.EN_PROCESO);
    }

    /** El interceptor la marco como exitosamente interceptada. */
    public void marcarInterceptada() {
        estado.set(Estado.INTERCEPTADA);
    }

    /**
     * El Monitor intenta marcarla impactada. True solo si seguia PENDIENTE.
     * Si ya estaba EN_PROCESO, significa que un interceptor la tomo a tiempo
     * y el CAS falla, protegiendo esa amenaza de doble contabilizacion.
     */
    public boolean intentarImpactar() {
        return estado.compareAndSet(Estado.PENDIENTE, Estado.IMPACTADA);
    }

    // ------------------------------------------------------------------ //
    // Getters y utilitarios                                                //
    // ------------------------------------------------------------------ //

    public void reducirTiempo(int ms) {
        // La reduccion efectiva depende de la velocidad del misil.
        tiempoRestante -= (int)(ms * tipoMisil.getVelocidad());
    }

    public Estado    getEstado()           { return estado.get();        }
    public int       getTiempoRestante()   { return tiempoRestante;      }
    public int       getId()               { return id;                   }
    public Zona      getZona()             { return zona;                 }
    public TipoMisil getTipoMisil()        { return tipoMisil;           }
    public long      getInstanteAparicion(){ return instanteAparicion;   }

    /** Danio efectivo si impacta: criticidad de zona x factor del misil. */
    public double danioEfectivo() {
        return zona.getCriticidad() * tipoMisil.getFactorDanio();
    }

    /**
     * Nivel de la cola MLQ al que pertenece esta amenaza, segun la
     * criticidad de su zona. Se usa solo cuando la estrategia activa es
     * Estrategia.MLQ (ver Simulador, que mantiene 3 colas separadas).
     *   0 = ALTA  (Hospital, Central Electrica: criticidad >= 8)
     *   1 = MEDIA (Datacenter: criticidad >= 6)
     *   2 = BAJA  (Residencial, Industrial: criticidad < 6)
     */
    public int nivelMLQ() {
        int c = zona.getCriticidad();
        if (c >= 8) return 0;
        if (c >= 6) return 1;
        return 2;
    }

    @Override
    public String toString() {
        return String.format("Amenaza#%d [%s | %s | crit=%d | t=%dms]",
                id, zona, tipoMisil.name(), zona.getCriticidad(), tiempoRestante);
    }
}
