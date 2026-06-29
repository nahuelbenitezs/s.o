
import java.util.concurrent.Semaphore;

/* Un interceptor (recurso), corriendo en su propio hilo. Hay N en paralelo.
 *
 * Espera a que el Despachador le asigne una amenaza, espera el fin de la recarga
 * y la marca como interceptada. Al terminar hace release() del semaforo de
 * recursos (el Despachador hizo tryAcquire() al asignar).
 *
 * Usa tres semaforos para coordinarse con el Despachador:
 *   asignacion -> el Despachador le da trabajo
 *   finServicio -> el Despachador avisa que el reloj llego al fin de la recarga
 *   confirmacion -> el interceptor avisa que ya termino (asi la salida queda ordenada)*/

public class Interceptor extends Thread {

    private final int id;
    private final Semaphore recursos; // semaforo contador Semaphore(N)
    private final Estadisticas stats;

    private final Semaphore asignacion = new Semaphore(0);
    private final Semaphore finServicio = new Semaphore(0);
    private final Semaphore confirmacion = new Semaphore(0);

    private volatile Amenaza actual;
    private volatile long tInicio;
    private volatile long tFin;
    private volatile boolean activo = true;

    private volatile long tiempoOcupado = 0; // para la utilizacion
    private boolean ocupado = false; // lo maneja solo el Despachador

    public Interceptor(int id, Semaphore recursos, Estadisticas stats) {
        super("Interceptor-" + id);
        this.id = id;
        this.recursos = recursos;
        this.stats = stats;
    }

    public int getNumero() {
        return id;
    }

    public long getTiempoOcupado() {
        return tiempoOcupado;
    }

    public boolean isOcupado() {
        return ocupado;
    }

    public void setOcupado(boolean b) {
        this.ocupado = b;
    }

    // el Despachador le entrega una amenaza
    public void asignar(Amenaza a, long inicio, long fin) {
        this.actual = a;
        this.tInicio = inicio;
        this.tFin = fin;
        asignacion.release();
    }

    // el Despachador avisa que el reloj llego al fin de la recarga
    public void anunciarFin() {
        finServicio.release();
    }

    // el Despachador espera a que el interceptor termine
    public void esperarConfirmacion() {
        try {
            confirmacion.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // para cortar el hilo al final
    public void detener() {
        activo = false;
        asignacion.release();
    }

    @Override
    public void run() {
        while (true) {
            try {
                asignacion.acquire(); // espera trabajo
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (!activo) {
                break;
            }

            try {
                finServicio.acquire(); // espera el fin de la recarga
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            Amenaza a = actual;
            a.setEstado(Estado.INTERCEPTADA);
            a.setInstanteFinServicio(tFin);
            a.setInterceptorAsignado(id);
            tiempoOcupado += (tFin - tInicio);
            long espera = tInicio - a.getInstanteLlegada();
            long retorno = tFin - a.getInstanteLlegada();
            stats.registrarInterceptada(espera, retorno);

            recursos.release(); // libera el recurso
            confirmacion.release(); // avisa al Despachador
        }
    }
}
