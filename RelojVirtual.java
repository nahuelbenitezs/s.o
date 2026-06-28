/*
 * Reloj logico de la simulacion.
 * El tiempo no avanza con Thread.sleep: el Despachador lo adelanta al instante
 * del proximo evento. Como solo el Despachador lo escribe, no necesita semaforo.
 */
public class RelojVirtual {

    private long tNow = 0;

    public long ahora() {
        return tNow;
    }

    // el tiempo nunca retrocede
    public void avanzarA(long t) {
        if (t > tNow) {
            tNow = t;
        }
    }
}
