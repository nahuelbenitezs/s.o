import java.util.concurrent.BlockingQueue;

/*
 * Hilo Monitor: controla el paso del tiempo de las amenazas que siguen
 * esperando en la cola.
 *
 * Cada "tick" reduce el tiempo restante de cada amenaza pendiente. Si a alguna
 * se le agota el tiempo y todavia no fue atendida, la marca como IMPACTADA y
 * la saca de la cola.
 *
 * Las amenazas que ya fueron tomadas por un interceptor no estan en la cola,
 * asi que el Monitor no las toca (se consideran atendidas a tiempo).
 */
public class Monitor extends Thread {

    private final BlockingQueue<Amenaza> cola;
    private final Estadisticas stats;
    private final int tick;     // cada cuanto revisa (ms)

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

            // Recorremos una copia de la cola para ir restando tiempo.
            for (Amenaza a : cola) {
                a.reducirTiempo(tick);
                if (a.getTiempoRestante() <= 0) {
                    if (a.intentarImpactar()) {
                        cola.remove(a);
                        stats.registrarImpactada(a.getZona().getCriticidad());
                        System.out.println("[Monitor] IMPACTO !! " + a
                                + " (no se llego a interceptar)");
                    }
                }
            }
        }
    }

    public void detener() {
        activo = false;
    }
}
