import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;

/*
 * Clase principal: arma y ejecuta la simulacion.
 *
 * Componentes concurrentes:
 *   - 1 hilo Generador  -> lee el archivo y mete amenazas en la cola.
 *   - N hilos Interceptor -> compiten por atender las amenazas de la cola.
 *   - 1 hilo Monitor    -> hace correr el tiempo e impacta las que se vencen.
 *
 * Recurso compartido:
 *   - PriorityBlockingQueue ordenada por prioridad (formula del Primer Avance).
 *     Es thread-safe (exclusion mutua interna) y ademas mantiene el orden de
 *     prioridad, asi siempre se atiende primero la amenaza mas critica/urgente.
 *
 * Sincronizacion:
 *   - Semaforo binario dentro de Estadisticas para proteger los contadores.
 *   - Variables atomicas (AtomicReference + compareAndSet) en Amenaza para las
 *     transiciones de estado, sin usar synchronized (lock-free).
 *
 * Uso:
 *   java Simulador [archivo] [cantidadInterceptores] [tiempoRecargaMs]
 *   (todos los parametros son opcionales, hay valores por defecto)
 */
public class Simulador {

    // ----- Parametros por defecto de la simulacion -----
    private static final int CANT_INTERCEPTORES = 2;     // pocos -> fuerza saturacion
    private static final int TIEMPO_RECARGA_MS  = 1000;  // tiempo fijo de servicio
    private static final int PAUSA_LLEGADAS_MS  = 250;    // llegan mas rapido de lo que se atiende
    private static final int TICK_MONITOR_MS    = 200;    // resolucion del reloj del monitor

    public static void main(String[] args) {
        String archivo = (args.length >= 1) ? args[0] : "amenazas.txt";
        int cantInterceptores = (args.length >= 2) ? Integer.parseInt(args[1]) : CANT_INTERCEPTORES;
        int recarga = (args.length >= 3) ? Integer.parseInt(args[2]) : TIEMPO_RECARGA_MS;

        System.out.println("=== Sistema concurrente de intercepcion de amenazas aereas ===");
        System.out.println("Archivo de escenario : " + archivo);
        System.out.println("Interceptores (N)    : " + cantInterceptores);
        System.out.println("Tiempo de recarga    : " + recarga + " ms");
        System.out.println("Estrategia de cola   : prioridad = criticidad*100 + (1000 - tiempoRestante)");
        System.out.println("--------------------------------------------------------------");

        // Cola de prioridad: el de MAYOR prioridad sale primero.
        PriorityBlockingQueue<Amenaza> cola = new PriorityBlockingQueue<>(
                16, Comparator.comparingInt(Amenaza::prioridad).reversed());

        Estadisticas stats = new Estadisticas();
        long inicio = System.currentTimeMillis();

        // --- Crear los hilos ---
        Generador generador = new Generador(archivo, cola, stats, inicio, PAUSA_LLEGADAS_MS);

        Interceptor[] interceptores = new Interceptor[cantInterceptores];
        for (int i = 0; i < cantInterceptores; i++) {
            interceptores[i] = new Interceptor(i + 1, cola, stats, inicio, recarga);
        }

        Monitor monitor = new Monitor(cola, stats, TICK_MONITOR_MS);

        // --- Arrancar todo ---
        monitor.start();
        for (Interceptor in : interceptores) {
            in.start();
        }
        generador.start();

        // --- Esperar a que termine la simulacion ---
        // Termina cuando el generador ya leyo todo el archivo Y todas las
        // amenazas llegaron a un estado final (interceptada o impactada).
        try {
            while (!(generador.isTerminado()
                    && stats.getResueltas() >= stats.getGeneradas())) {
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // --- Apagar los hilos de forma ordenada ---
        monitor.detener();
        for (Interceptor in : interceptores) {
            in.detener();
            in.interrupt();
        }
        monitor.interrupt();

        try {
            generador.join();
            monitor.join();
            for (Interceptor in : interceptores) {
                in.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // --- Mostrar el resumen estadistico ---
        stats.imprimirResumen();
    }
}
