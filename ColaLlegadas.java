import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;

/*
 * Cola de llegadas entre el Generador (productor) y el Despachador (consumidor).
 * Es un productor-consumidor con semaforos: mutex para la cola e items para
 * contar cuantas llegadas hay. acquire() = Wait/P y release() = Signal/V.
 */
public class ColaLlegadas {

    private final Queue<Evento> buffer = new LinkedList<>();
    private final Semaphore mutex = new Semaphore(1);
    private final Semaphore items = new Semaphore(0);

    private volatile boolean cerrada = false;

    // el productor guarda una llegada
    public void poner(Evento e) {
        try {
            mutex.acquire();
            try {
                buffer.add(e);
            } finally {
                mutex.release();
            }
            items.release();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    // el productor avisa que no vienen mas llegadas
    public void cerrar() {
        cerrada = true;
        items.release(); // desbloquea al consumidor si estaba esperando
    }

    // devuelve la proxima llegada, o null si la cola fue cerrada y esta vacia
    public Evento siguiente() {
        try {
            items.acquire();
            mutex.acquire();
            try {
                return buffer.poll(); // null = nos desperto el cierre y no hay nada
            } finally {
                mutex.release();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public boolean estaCerrada() {
        return cerrada;
    }
}
