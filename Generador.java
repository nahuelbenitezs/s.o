import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;

/*
 * Hilo Lector / Generador de amenazas.
 *
 * Lee el archivo de texto (una amenaza por linea, formato "ZONA;tiempoMs")
 * y va depositando las amenazas en la cola compartida, con una pequena pausa
 * entre cada una para simular que llegan durante la simulacion.
 *
 * Inserta mas rapido de lo que el sistema puede atender, para provocar el
 * escenario de SATURACION que pide la letra.
 */
public class Generador extends Thread {

    private final String archivo;
    private final BlockingQueue<Amenaza> cola;
    private final Estadisticas stats;
    private final long inicioSimulacion;
    private final int pausaEntreLlegadas; // ms entre una amenaza y la siguiente

    private volatile boolean terminado = false;

    public Generador(String archivo, BlockingQueue<Amenaza> cola, Estadisticas stats,
                     long inicioSimulacion, int pausaEntreLlegadas) {
        super("Generador");
        this.archivo = archivo;
        this.cola = cola;
        this.stats = stats;
        this.inicioSimulacion = inicioSimulacion;
        this.pausaEntreLlegadas = pausaEntreLlegadas;
    }

    @Override
    public void run() {
        int id = 1;
        try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty() || linea.startsWith("#")) {
                    continue; // ignora lineas vacias o comentarios
                }
                String[] partes = linea.split(";");
                if (partes.length != 2) {
                    System.out.println("[Generador] Linea ignorada (formato invalido): " + linea);
                    continue;
                }

                Zona zona;
                int tiempo;
                try {
                    zona = Zona.valueOf(partes[0].trim().toUpperCase());
                    tiempo = Integer.parseInt(partes[1].trim());
                } catch (IllegalArgumentException e) {
                    System.out.println("[Generador] Linea ignorada (dato invalido): " + linea);
                    continue;
                }

                long ahora = System.currentTimeMillis() - inicioSimulacion;
                Amenaza a = new Amenaza(id++, zona, tiempo, ahora);
                cola.put(a);
                stats.registrarGenerada();
                System.out.println("[Generador] Llega " + a);

                Thread.sleep(pausaEntreLlegadas);
            }
        } catch (IOException e) {
            System.out.println("[Generador] No se pudo leer el archivo: " + archivo);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            terminado = true;
            System.out.println("[Generador] Termino de generar amenazas.");
        }
    }

    public boolean isTerminado() {
        return terminado;
    }
}
