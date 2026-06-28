import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;

/*
 * Hilo Generador / Lector de amenazas.
 *
 * Lee el archivo de escenario linea a linea y deposita las amenazas en la
 * cola compartida, con una pausa configurable entre llegadas para simular
 * la llegada temporal de los eventos.
 *
 * Formato del archivo:
 *   ZONA;tiempoRestanteMs[;TIPO_MISIL]
 *   las lineas vacias o que empiezan con '#' son ignoradas
 *   TIPO_MISIL es opcional; si se omite, se usa CONVENCIONAL.
 *
 * El generador inserta mas rapido de lo que los interceptores pueden atender, lo que provoca el escenario de saturacion.
 *
 * Si la estrategia activa es Estrategia.MLQ, el log de cada llegada muestra
 * el nivel de cola (ALTA/MEDIA/BAJA) en lugar de un valor de prioridad
 * numerico, porque esa estrategia no usa una formula unica (ver Simulador).
 */
public class Generador extends Thread {

    private final String archivo;
    private final BlockingQueue<Amenaza> cola;
    private final Estadisticas stats;
    private final long inicioSimulacion;
    private final int pausaEntreLlegadas; // ms
    private final Estrategia estrategia; // para calcular prioridad al insertar

    private volatile boolean terminado = false;

    public Generador(String archivo, BlockingQueue<Amenaza> cola, Estadisticas stats,
                     long inicioSimulacion, int pausaEntreLlegadas, Estrategia estrategia) {
        super("Generador");
        this.archivo = archivo;
        this.cola = cola;
        this.stats = stats;
        this.inicioSimulacion = inicioSimulacion;
        this.pausaEntreLlegadas = pausaEntreLlegadas;
        this.estrategia = estrategia;
    }

    @Override
    public void run() {
        int id = 1;
        try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty() || linea.startsWith("#")) {
                    continue;
                }
                String[] partes = linea.split(";");
                if (partes.length < 2) {
                    System.out.println("[Generador] Linea ignorada (formato invalido): " + linea);
                    continue;
                }

                Zona zona;
                int tiempo;
                TipoMisil tipo = TipoMisil.CONVENCIONAL; // default

                try {
                    zona = Zona.valueOf(partes[0].trim().toUpperCase());
                    tiempo = Integer.parseInt(partes[1].trim());
                    if (partes.length >= 3) {
                        tipo = TipoMisil.valueOf(partes[2].trim().toUpperCase());
                    }
                } catch (IllegalArgumentException e) {
                    System.out.println("[Generador] Linea ignorada (dato invalido): " + linea);
                    continue;
                }

                long ahora = System.currentTimeMillis() - inicioSimulacion;
                Amenaza a = new Amenaza(id++, zona, tipo, tiempo, ahora);
                cola.put(a);
                stats.registrarGenerada();

                String[] niveles = {"ALTA", "MEDIA", "BAJA"};
                if (estrategia == Estrategia.MLQ) {
                    System.out.printf("[Generador] +++ %s  nivelMLQ=%s%n",
                            a, niveles[a.nivelMLQ()]);
                } else {
                    System.out.printf("[Generador] +++ %s  P=%.0f%n", a, a.prioridad(estrategia));
                }

                if (pausaEntreLlegadas > 0) {
                    Thread.sleep(pausaEntreLlegadas);
                }
            }

        } catch (IOException e) {
            System.out.println("[Generador] ERROR leyendo archivo: " + archivo);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            terminado = true;
            System.out.println("[Generador] Fin de generacion de amenazas.");
        }
    }

    public boolean isTerminado() { 
        return terminado; 
    }
}
