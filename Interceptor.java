import java.util.concurrent.BlockingQueue;
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
 *   1. poll(200ms) -> saca la amenaza de mayor prioridad.
 *   2. intentarTomar() -> CAS PENDIENTE->EN_PROCESO.
 *      Si falla (el Monitor ya la marco impactada), descarta y busca otra.
 *   3. sleep(tiempoRecarga) -> simula el tiempo de servicio.
 *   4. marcarInterceptada() + registrar estadisticas.
 *
 * La metrica de ocupacion (tiempoRecarga) se suma a Estadisticas para
 * calcular la utilizacion de los interceptores.
 */
public class Interceptor extends Thread {

    private final BlockingQueue<Amenaza> cola;
    private final Estadisticas           stats;
    private final long                   inicioSimulacion;
    private final int                    tiempoRecarga; // ms de servicio fijo

    private volatile boolean activo = true;

    public Interceptor(int numero, BlockingQueue<Amenaza> cola, Estadisticas stats,
                       long inicioSimulacion, int tiempoRecarga) {
        super("Interceptor-" + numero);
        this.cola             = cola;
        this.stats            = stats;
        this.inicioSimulacion = inicioSimulacion;
        this.tiempoRecarga    = tiempoRecarga;
    }

    @Override
    public void run() {
        while (activo) {
            try {
                Amenaza a = cola.poll(200, TimeUnit.MILLISECONDS);
                if (a == null) continue;

                // CAS: si el Monitor ya la impacto, descartamos y seguimos.
                if (!a.intentarTomar()) continue;

                long espera = (System.currentTimeMillis() - inicioSimulacion)
                              - a.getInstanteAparicion();
                System.out.printf("[%s] >>> Atendiendo %s (espero %dms)%n",
                        getName(), a, espera);

                long t0 = System.currentTimeMillis();
                Thread.sleep(tiempoRecarga);
                long ocupacion = System.currentTimeMillis() - t0;

                a.marcarInterceptada();
                stats.registrarInterceptada(espera, ocupacion);
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
