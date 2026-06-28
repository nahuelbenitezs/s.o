import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/*
 * Hilo Generador (productor). Lee el archivo de escenario y deposita las amenazas
 * como eventos de llegada en la ColaLlegadas.
 *
 * Formato del archivo:  ZONA;tiempoRestanteMs[;instanteLlegadaMs]
 * El instante de llegada es opcional; si no esta, se usa indice * pausaEntreLlegadas.
 */
public class Generador extends Thread {

    private final String archivo;
    private final ColaLlegadas cola;
    private final Estadisticas stats;
    private final int pausaEntreLlegadas;
    private final boolean verbose;

    private volatile boolean terminado = false;

    public Generador(String archivo, ColaLlegadas cola, Estadisticas stats, int pausaEntreLlegadas, boolean verbose) {
        super("Generador");
        this.archivo = archivo;
        this.cola = cola;
        this.stats = stats;
        this.pausaEntreLlegadas = pausaEntreLlegadas;
        this.verbose = verbose;
    }

    @Override
    public void run() {
        List<Amenaza> amenazas = leerArchivo();

        // se entregan en orden de llegada
        Collections.sort(amenazas, new Comparator<Amenaza>() {
            public int compare(Amenaza a, Amenaza b) {
                int cmp = Long.compare(a.getInstanteLlegada(), b.getInstanteLlegada());
                return (cmp != 0) ? cmp : Integer.compare(a.getId(), b.getId());
            }
        });

        long seq = 0;
        for (Amenaza a : amenazas) {
            cola.poner(new Evento(Evento.Tipo.LLEGADA, a.getInstanteLlegada(), a, null, seq++));
            stats.registrarGenerada();
        }

        terminado = true;
        cola.cerrar();
        if (verbose) {
            System.out.println("[Generador] Termino de generar " + amenazas.size() + " amenazas.");
        }
    }

    private List<Amenaza> leerArchivo() {
        List<Amenaza> amenazas = new ArrayList<>();
        int id = 1;
        int indice = 0;
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
                try {
                    zona = Zona.valueOf(partes[0].trim().toUpperCase());
                    tiempo = Integer.parseInt(partes[1].trim());
                } catch (IllegalArgumentException e) {
                    System.out.println("[Generador] Linea ignorada (dato invalido): " + linea);
                    continue;
                }
                long llegada;
                if (partes.length >= 3) {
                    llegada = Long.parseLong(partes[2].trim());
                } else {
                    llegada = (long) indice * pausaEntreLlegadas;
                }
                amenazas.add(new Amenaza(id++, zona, tiempo, llegada));
                indice++;
            }
        } catch (IOException e) {
            System.out.println("[Generador] No se pudo leer el archivo: " + archivo);
        }
        return amenazas;
    }

    public boolean isTerminado() {
        return terminado;
    }
}
