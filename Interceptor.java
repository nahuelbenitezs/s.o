import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/*
 * Hilo Interceptor: representa un recurso de intercepcion (un misil/interceptor).
 *
 * Hay N hilos de estos corriendo en paralelo. Todos compiten por tomar
 * amenazas de la MISMA cola compartida (de ahi la competencia por recursos).
 *
 * Cuando toma una amenaza, queda ocupado un tiempo fijo de RECARGA simulando
 * el servicio. Mientras tanto no puede atender otra. Si N es chico y llegan
 * muchas amenazas, varias quedan esperando y el Monitor las marca impactadas:
 * ese es el escenario de saturacion.
 */
public class Interceptor extends Thread {

    private final BlockingQueue<Amenaza> cola;
    private final Estadisticas stats;
    private final long inicioSimulacion;
    private final int tiempoRecarga;        // tiempo fijo de servicio (ms)

    private volatile boolean activo = true;

    public Interceptor(int numero, BlockingQueue<Amenaza> cola, Estadisticas stats,
                       long inicioSimulacion, int tiempoRecarga) {
        super("Interceptor-" + numero);
        this.cola = cola;
        this.stats = stats;
        this.inicioSimulacion = inicioSimulacion;
        this.tiempoRecarga = tiempoRecarga;
    }

    @Override
    public void run() {
        while (activo) {
            try {
                // Espera hasta 200ms por una amenaza. Si no hay, vuelve a chequear
                // si la simulacion sigue activa.
                Amenaza a = cola.poll(200, TimeUnit.MILLISECONDS);
                if (a == null) {
                    continue;
                }

                // Intenta reclamarla. Puede fallar si el Monitor ya la impacto
                // o si otro interceptor la tomo primero.
                if (!a.intentarTomar()) {
                    continue;
                }

                long espera = (System.currentTimeMillis() - inicioSimulacion) - a.getInstanteAparicion();
                System.out.println("[" + getName() + "] Atendiendo " + a
                        + " (espero " + espera + "ms en la cola)");

                // Tiempo fijo de recarga/servicio: el recurso queda ocupado.
                Thread.sleep(tiempoRecarga);

                a.marcarInterceptada();
                stats.registrarInterceptada(espera);
                System.out.println("[" + getName() + "] INTERCEPTADA con exito " + a);

            } catch (InterruptedException e) {
                // Se usa para cortar el hilo al final de la simulacion.
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void detener() {
        activo = false;
    }
}
