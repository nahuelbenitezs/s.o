import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;

// Cola de llegadas entre Generador y Despachador.
// Usa semáforos para sincronizar productor-consumidor.
public class ColaLlegadas {

    private final Queue<Evento> buffer = new LinkedList<>();
    private final Semaphore mutex = new Semaphore(1);
    private final Semaphore items = new Semaphore(0);

    private volatile boolean cerrada = false;

    // el productor guarda una llegada
    public void poner(Evento e) {
        try {
            mutex.acquire();
            buffer.add(e);
            mutex.release();
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
            Evento ev = buffer.poll(); // puede ser null si se cerró
            mutex.release();
            return ev;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public boolean estaCerrada() {
        return cerrada;
    }
}
