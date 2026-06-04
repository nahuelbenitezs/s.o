import java.util.concurrent.atomic.AtomicReference;

/*
 * Representa una amenaza aerea entrante.
 *
 * Guarda los datos minimos pedidos en la letra: identificador, zona objetivo,
 * instante de aparicion, tiempo restante hasta el impacto y estado.
 *
 * La prioridad se calcula con la formula definida en el Primer Avance:
 *      P = (Criticidad x 100) + (1000 - TiempoRestante)
 *
 * El estado se guarda en un AtomicReference. Las transiciones criticas se
 * hacen con compareAndSet (CAS): es una operacion atomica y "lock-free"
 * (sin synchronized). Esto resuelve la condicion de carrera entre el
 * interceptor (PENDIENTE -> EN_PROCESO) y el monitor (PENDIENTE -> IMPACTADA):
 * cuando ambos compiten, solo uno obtiene true.
 */
public class Amenaza {

    private final int id;
    private final Zona zona;
    private final long instanteAparicion;   // milisegundos desde el inicio
    private volatile int tiempoRestante;     // ms hasta el impacto (lo baja el Monitor)
    private final AtomicReference<Estado> estado =
            new AtomicReference<>(Estado.PENDIENTE);

    public Amenaza(int id, Zona zona, int tiempoRestante, long instanteAparicion) {
        this.id = id;
        this.zona = zona;
        this.tiempoRestante = tiempoRestante;
        this.instanteAparicion = instanteAparicion;
    }

    /*
     * Formula de prioridad del Primer Avance.
     * Un valor mas alto = mayor prioridad de atencion.
     */
    public int prioridad() {
        return (zona.getCriticidad() * 100) + (1000 - tiempoRestante);
    }

    /*
     * Un interceptor intenta "tomar" la amenaza. Solo lo logra si todavia
     * estaba PENDIENTE. compareAndSet hace la comparacion y el cambio en un
     * solo paso atomico. Devuelve true si la pudo reclamar.
     */
    public boolean intentarTomar() {
        return estado.compareAndSet(Estado.PENDIENTE, Estado.EN_PROCESO);
    }

    /*
     * El interceptor marca la amenaza como interceptada con exito.
     * Aca no hay competencia: la amenaza ya esta EN_PROCESO y solo el
     * interceptor que la tomo llega a este punto.
     */
    public void marcarInterceptada() {
        estado.set(Estado.INTERCEPTADA);
    }

    /*
     * El Monitor intenta marcarla como impactada. Solo lo logra si seguia
     * PENDIENTE (si ya estaba EN_PROCESO, se considera atendida a tiempo y
     * el compareAndSet falla devolviendo false).
     */
    public boolean intentarImpactar() {
        return estado.compareAndSet(Estado.PENDIENTE, Estado.IMPACTADA);
    }

    public Estado getEstado() {
        return estado.get();
    }

    public void reducirTiempo(int ms) {
        tiempoRestante -= ms;
    }

    public int getTiempoRestante() {
        return tiempoRestante;
    }

    public int getId() {
        return id;
    }

    public Zona getZona() {
        return zona;
    }

    public long getInstanteAparicion() {
        return instanteAparicion;
    }

    @Override
    public String toString() {
        return "Amenaza#" + id + " [" + zona + ", crit=" + zona.getCriticidad()
                + ", t=" + tiempoRestante + "ms, P=" + prioridad() + "]";
    }
}
