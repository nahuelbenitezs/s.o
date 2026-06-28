import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/*
 * Hilo Interceptor.
 *
 * Hay N instancias corriendo en paralelo. Todas compiten por tomar amenazas
 * de la MISMA PriorityBlockingQueue compartida: la cola garantiza thread-safety
 * y devuelve siempre la amenaza de mayor prioridad segun el Comparator
 * definido en el Simulador.
 *
 * Protocolo de toma:
 *   1. poll(tickPolling) -> saca la amenaza de mayor prioridad.
 *      tickPolling es la MISMA resolucion de tiempo que usa el Monitor
 *      (antes el Monitor usaba 100ms y el Interceptor 200ms hardcodeado
 *      por separado: dos relojes distintos para el mismo sistema).
 *   2. acquire() del semaforo "recursos" (Semaphore(N)): toma un permiso de
 *      la capacidad limitada de intercepcion antes de comprometerse con la
 *      amenaza. Este semaforo CONTADOR es el mecanismo de sincronizacion
 *      que hace EXPLICITA la restriccion de "N recursos disponibles" que
 *      pide la letra, en lugar de dejarla implicita en "hay N hilos
 *      Interceptor nada mas". Tiene sentido aunque cada Interceptor solo
 *      tome una amenaza a la vez, porque es el contrato visible y
 *      defendible de cuantas intercepciones puede haber en simultaneo en
 *      todo el sistema (y queda preparado para el caso en que en el futuro
 *      se quisiera desacoplar la cantidad de hilos de la cantidad de
 *      recursos reales).
 *   3. intentarTomar() -> CAS PENDIENTE->EN_PROCESO.
 *      Si falla (el Monitor ya la marco impactada), se libera el permiso
 *      del semaforo y se descarta, buscando otra.
 *   4. sleep(tiempoRecarga) -> simula el tiempo de servicio.
 *   5. marcarInterceptada() + registrar estadisticas + release() del permiso.
 *
 * La metrica de ocupacion (tiempoRecarga) se suma a Estadisticas para
 * calcular la utilizacion de los interceptores.
 */
public class Interceptor extends Thread {

    // Se guarda como arreglo de colas para poder soportar tanto el caso
    // normal (1 sola PriorityBlockingQueue, las 4 primeras estrategias)
    // como el caso MLQ (3 colas por nivel). El orden del arreglo importa:
    // se intenta poll() en colas[0] primero, luego colas[1], etc. Con una
    // sola cola este orden es irrelevante y el comportamiento es exactamente
    // el mismo que antes.
    private final BlockingQueue<Amenaza>[] colas;
    private final Estadisticas             stats;
    private final Semaphore                recursos;      // Semaphore(N): capacidad de intercepcion
    private final long                     inicioSimulacion;
    private final int                      tiempoRecarga; // ms de servicio fijo
    private final int                      tickPolling;   // ms de espera en poll()

    private volatile boolean activo = true;

    /** Constructor original: una sola cola (MENOR_TIEMPO, MAYOR_CRITICIDAD, COMBINADA, DANIO_ESPERADO). */
    public Interceptor(int numero, BlockingQueue<Amenaza> cola, Estadisticas stats,
                       Semaphore recursos, long inicioSimulacion,
                       int tiempoRecarga, int tickPolling) {
        super("Interceptor-" + numero);
        this.colas            = soloUna(cola);
        this.stats            = stats;
        this.recursos         = recursos;
        this.inicioSimulacion = inicioSimulacion;
        this.tiempoRecarga    = tiempoRecarga;
        this.tickPolling      = tickPolling;
    }

    /** Constructor MLQ: varias colas, en orden de prioridad de nivel (ALTA, MEDIA, BAJA). */
    @SafeVarargs
    public Interceptor(int numero, Estadisticas stats, Semaphore recursos,
                       long inicioSimulacion, int tiempoRecarga, int tickPolling,
                       BlockingQueue<Amenaza>... colasPorNivel) {
        super("Interceptor-" + numero);
        this.colas            = colasPorNivel;
        this.stats            = stats;
        this.recursos         = recursos;
        this.inicioSimulacion = inicioSimulacion;
        this.tiempoRecarga    = tiempoRecarga;
        this.tickPolling      = tickPolling;
    }

    @SuppressWarnings("unchecked")
    private static BlockingQueue<Amenaza>[] soloUna(BlockingQueue<Amenaza> cola) {
        return (BlockingQueue<Amenaza>[]) new BlockingQueue[] { cola };
    }

    /**
     * Intenta tomar la siguiente amenaza disponible, probando las colas en
     * orden (la primera con prioridad ALTA). Con una sola cola, equivale a
     * simplemente hacer poll() sobre ella, igual que antes.
     */
    private Amenaza tomarSiguiente() throws InterruptedException {
        // primero un poll corto en cada cola en orden de prioridad de nivel
        for (BlockingQueue<Amenaza> c : colas) {
            Amenaza a = c.poll();
            if (a != null) return a;
        }
        // si ninguna tenia nada disponible, esperamos en la de mayor
        // prioridad hasta tickPolling, para no ocupar CPU en un loop activo
        return colas[0].poll(tickPolling, TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
        while (activo) {
            try {
                Amenaza a = tomarSiguiente();
                if (a == null) continue;

                recursos.acquire(); // toma un permiso de la capacidad limitada

                // CAS: si el Monitor ya la impacto, liberamos el permiso y seguimos.
                if (!a.intentarTomar()) {
                    recursos.release();
                    continue;
                }

                long espera = (System.currentTimeMillis() - inicioSimulacion)
                              - a.getInstanteAparicion();
                System.out.printf("[%s] >>> Atendiendo %s (espero %dms)%n",
                        getName(), a, espera);

                long t0 = System.currentTimeMillis();
                Thread.sleep(tiempoRecarga);
                long ocupacion = System.currentTimeMillis() - t0;

                a.marcarInterceptada();
                stats.registrarInterceptada(espera, ocupacion);
                recursos.release(); // libera el permiso de capacidad
                System.out.printf("[%s] *** INTERCEPTADA %s%n", getName(), a);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.printf("[%s] detenido.%n", getName());
    }

    public void detener() { activo = false; }
}
