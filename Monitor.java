import java.util.concurrent.BlockingQueue;

/*
 * Hilo Monitor: controla el paso del tiempo para las amenazas pendientes
 *
 * Cada tick reduce el tiempoRestante de cada
 * amenaza que sigue en la cola. La reduccion ya esta ajustada por la
 * velocidad del misil dentro de Amenaza.reducirTiempo().
 *
 * Si el tiempo de una amenaza llega a cero o menos:
 *   - Intenta marcarla IMPACTADA via CAS (intentarImpactar()).
 *   - Si el CAS tiene exito: la saca de la cola y registra el impacto
 *   - Si falla: un interceptor ya la tomo a tiempo; no se hace mas nada
 *
 *   La operacion cola.remove(a) es O(n) pero con colas chicas en la simulacion esto esta bien
 */
public class Monitor extends Thread {

    private final BlockingQueue<Amenaza> cola;
    private final Estadisticas stats;
    private final int tick; // ms de resolucion del reloj

    private volatile boolean activo = true;

    public Monitor(BlockingQueue<Amenaza> cola, Estadisticas stats, int tick) {
        super("Monitor");
        this.cola = cola;
        this.stats = stats;
        this.tick = tick;
    }

    @Override
    public void run() {
        while (activo) {
            try {
                Thread.sleep(tick);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            for (Amenaza a : cola) {
                a.reducirTiempo(tick);
                if (a.getTiempoRestante() <= 0) {
                    if (a.intentarImpactar()) {
                        cola.remove(a);
                        stats.registrarImpactada(
                                a.getZona().getCriticidad(),
                                a.danioEfectivo());
                        System.out.printf("[Monitor] !!! IMPACTO %s  danio=%.1f%n",
                                a, a.danioEfectivo());
                    }
                }
            }
        }
        System.out.println("[Monitor] detenido.");
    }

    public void detener() { 
        activo = false; 
    }
}
