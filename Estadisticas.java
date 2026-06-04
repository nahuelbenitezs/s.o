import java.util.concurrent.Semaphore;

/*
 * Guarda los contadores compartidos de la simulacion.
 *
 * Como varios hilos (los interceptores y el monitor) escriben sobre estos
 * contadores al mismo tiempo, usamos un SEMAFORO BINARIO como mecanismo de
 * exclusion mutua (mecanismo central elegido en el Primer Avance).
 *
 * NOTA: java.util.concurrent.Semaphore(1) actua como un mutex: acquire()
 * baja el permiso y release() lo devuelve. Asi nos aseguramos de que un solo
 * hilo modifique los contadores a la vez.
 */
public class Estadisticas {

    private final Semaphore mutex = new Semaphore(1);

    private int generadas = 0;
    private int interceptadas = 0;
    private int impactadas = 0;
    private long sumaEsperaInterceptadas = 0; // para el tiempo promedio de espera
    private int criticidadImpactada = 0;      // metrica vinculada a criticidad

    public void registrarGenerada() {
        try {
            mutex.acquire();
            generadas++;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            mutex.release();
        }
    }

    public void registrarInterceptada(long tiempoEspera) {
        try {
            mutex.acquire();
            interceptadas++;
            sumaEsperaInterceptadas += tiempoEspera;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            mutex.release();
        }
    }

    public void registrarImpactada(int criticidad) {
        try {
            mutex.acquire();
            impactadas++;
            criticidadImpactada += criticidad;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            mutex.release();
        }
    }

    public int getGeneradas() {
        try {
            mutex.acquire();
            return generadas;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } finally {
            mutex.release();
        }
    }

    public int getResueltas() {
        // interceptadas + impactadas, leido de forma segura
        try {
            mutex.acquire();
            return interceptadas + impactadas;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } finally {
            mutex.release();
        }
    }

    public void imprimirResumen() {
        System.out.println();
        System.out.println("========== RESUMEN DE LA SIMULACION ==========");
        System.out.println("Amenazas generadas    : " + generadas);
        System.out.println("Amenazas interceptadas: " + interceptadas);
        System.out.println("Amenazas impactadas   : " + impactadas);
        if (interceptadas > 0) {
            System.out.println("Tiempo promedio de espera (interceptadas): "
                    + (sumaEsperaInterceptadas / interceptadas) + " ms");
        }
        System.out.println("Criticidad total impactada: " + criticidadImpactada
                + "  (suma de la criticidad de las zonas que recibieron impacto)");
        if (generadas > 0) {
            double tasa = (100.0 * interceptadas) / generadas;
            System.out.printf("Tasa de interceptacion: %.1f%%%n", tasa);
        }
        System.out.println("==============================================");
    }
}
