import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.PriorityBlockingQueue;

/*
 * Clase principal del Sistema Concurrente de Intercepcion de Amenazas Aereas.
 *
 * MODOS DE USO:
 *
 *   Modo interactivo (sin argumentos):
 *     java Simulador
 *     -> muestra menu y permite elegir estrategia y escenario.
 *
 *   Modo directo (argumentos):
 *     java Simulador <archivo> [interceptores] [recargaMs] [estrategia 1-4]
 *
 *   Modo batch (compara TODAS las estrategias sobre el mismo escenario):
 *     java Simulador <archivo> [interceptores] [recargaMs] --batch
 *
 * COMPONENTES CONCURRENTES:
 *   1 hilo Generador  : lee el archivo y mete amenazas en la cola.
 *   N hilos Interceptor: compiten por atender las amenazas.
 *   1 hilo Monitor    : hace correr el tiempo; impacta las que expiran.
 *
 * RECURSO COMPARTIDO:
 *   PriorityBlockingQueue<Amenaza> con Comparator segun la estrategia elegida.
 *   Es thread-safe (lock interno del JDK) y mantiene el orden de prioridad.
 *
 * SINCRONIZACION (sin usar synchronized, segun la letra):
 *   - Semaforo binario (Semaphore(1)) en Estadisticas -> exclusion mutua en contadores.
 *   - AtomicReference + compareAndSet en Amenaza      -> transiciones de estado lock-free.
 */
public class Simulador {

    // ------------------------------------------------------------------ //
    // Parametros por defecto                                               //
    // ------------------------------------------------------------------ //
    private static final int    DEF_INTERCEPTORES   = 2;
    private static final int    DEF_RECARGA_MS      = 1000;
    private static final int    DEF_PAUSA_LLEGADAS  = 250;   // ms entre amenazas
    private static final int    DEF_TICK_MONITOR    = 100;   // resolucion del reloj (ms)
    private static final String DEF_ARCHIVO         = "amenazas.txt";

    // ------------------------------------------------------------------ //
    // Punto de entrada                                                     //
    // ------------------------------------------------------------------ //
    public static void main(String[] args) throws InterruptedException {

        if (args.length == 0) {
            // ---- Modo interactivo ----------------------------------------
            modoInteractivo();
        } else if (args.length >= 4 && args[args.length - 1].equalsIgnoreCase("--batch")) {
            // ---- Modo batch: todas las estrategias -----------------------
            String archivo     = args[0];
            int interceptores  = Integer.parseInt(args[1]);
            int recarga        = Integer.parseInt(args[2]);
            modoBatch(archivo, interceptores, recarga);
        } else {
            // ---- Modo directo con argumentos -----------------------------
            String   archivo      = args[0];
            int interceptores     = args.length >= 2 ? Integer.parseInt(args[1]) : DEF_INTERCEPTORES;
            int recarga           = args.length >= 3 ? Integer.parseInt(args[2]) : DEF_RECARGA_MS;
            Estrategia estrategia = Estrategia.COMBINADA; // default
            if (args.length >= 4) {
                try {
                    int idx = Integer.parseInt(args[3]) - 1;
                    estrategia = Estrategia.values()[idx];
                } catch (Exception e) {
                    System.out.println("Estrategia invalida, usando COMBINADA.");
                }
            }
            ejecutar(archivo, interceptores, recarga, DEF_PAUSA_LLEGADAS,
                     DEF_TICK_MONITOR, estrategia, true);
        }
    }

    // ------------------------------------------------------------------ //
    // Modo interactivo                                                     //
    // ------------------------------------------------------------------ //
    private static void modoInteractivo() throws InterruptedException {
        Scanner sc = new Scanner(System.in);

        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║  Sistema Concurrente de Intercepcion de Amenazas Aereas  ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");

        // Archivo de escenario
        System.out.println("\nEscenarios disponibles:");
        System.out.println("  [1] amenazas.txt          (saturacion, 12 amenazas mixtas)");
        System.out.println("  [2] escenario_borde_critico.txt  (solo zonas criticas, tiempo corto)");
        System.out.println("  [3] escenario_hipersonico.txt    (misiles de alta velocidad)");
        System.out.println("  [4] escenario_borde_limite.txt   (caso limite: 1 interceptor)");
        System.out.println("  [5] Ingresar nombre de archivo manualmente");
        System.out.print("Elija escenario [1-5]: ");

        String archivo;
        int opEsc = leerEntero(sc, 1, 5);
        switch (opEsc) {
            case 1: archivo = "amenazas.txt";                   break;
            case 2: archivo = "escenario_borde_critico.txt";    break;
            case 3: archivo = "escenario_hipersonico.txt";      break;
            case 4: archivo = "escenario_borde_limite.txt";     break;
            default:
                System.out.print("Nombre del archivo: ");
                archivo = sc.nextLine().trim();
        }

        // Parametros
        System.out.print("\nCantidad de interceptores [" + DEF_INTERCEPTORES + "]: ");
        String inp = sc.nextLine().trim();
        int interceptores = inp.isEmpty() ? DEF_INTERCEPTORES : Integer.parseInt(inp);

        System.out.print("Tiempo de recarga (ms) [" + DEF_RECARGA_MS + "]: ");
        inp = sc.nextLine().trim();
        int recarga = inp.isEmpty() ? DEF_RECARGA_MS : Integer.parseInt(inp);

        // Estrategia
        Estrategia.imprimirMenu();
        System.out.print("Elija estrategia [1-" + Estrategia.values().length + "] o 0 para comparar todas: ");
        int opEst = leerEntero(sc, 0, Estrategia.values().length);

        if (opEst == 0) {
            modoBatch(archivo, interceptores, recarga);
        } else {
            Estrategia e = Estrategia.values()[opEst - 1];
            ejecutar(archivo, interceptores, recarga,
                     DEF_PAUSA_LLEGADAS, DEF_TICK_MONITOR, e, true);
        }
    }

    // ------------------------------------------------------------------ //
    // Modo batch: ejecuta TODAS las estrategias y compara                 //
    // ------------------------------------------------------------------ //
    private static void modoBatch(String archivo, int interceptores, int recarga)
            throws InterruptedException {

        System.out.println("\n╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║              MODO BATCH - COMPARACION DE ESTRATEGIAS     ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");

        List<String> csvLineas = new ArrayList<>();
        csvLineas.add("estrategia,generadas,interceptadas,impactadas,tasa(%),promEspera(ms),"
                     + "danioTotal,criticidadTotal,utilizacion(%)");

        for (Estrategia e : Estrategia.values()) {
            System.out.println("\n--- Ejecutando: " + e.getNombre() + " ---");
            Estadisticas stats = ejecutar(archivo, interceptores, recarga,
                                          DEF_PAUSA_LLEGADAS, DEF_TICK_MONITOR, e, false);
            if (stats != null) {
                csvLineas.add(stats.toCSV());
            }
            // Pausa entre ejecuciones para que los hilos terminen limpio
            Thread.sleep(500);
        }

        // Tabla comparativa
        System.out.println("\n╔═══════════════════════════════════════════════════════════════════╗");
        System.out.println("║                  TABLA COMPARATIVA DE ESTRATEGIAS                ║");
        System.out.println("╠════════════════════════════════╦══════╦══════╦════════╦══════════╣");
        System.out.println("║ Estrategia                     ║Inter.║Impac.║ Tasa % ║ DanioTot ║");
        System.out.println("╠════════════════════════════════╬══════╬══════╬════════╬══════════╣");
        for (int i = 1; i < csvLineas.size(); i++) {
            String[] f = csvLineas.get(i).split(",");
            System.out.printf("║ %-30s ║ %4s ║ %4s ║ %6s ║ %8s ║%n",
                    truncar(f[0], 30), f[2], f[3], f[4], f[6]);
        }
        System.out.println("╚════════════════════════════════╩══════╩══════╩════════╩══════════╝");
    }

    // ------------------------------------------------------------------ //
    // Metodo principal de ejecucion de una simulacion                     //
    // ------------------------------------------------------------------ //
    static Estadisticas ejecutar(String archivo, int cantInterceptores, int recarga,
                                  int pausaLlegadas, int tickMonitor,
                                  Estrategia estrategia, boolean imprimirResumen)
            throws InterruptedException {

        System.out.println("\n=== Sistema de Intercepcion de Amenazas Aereas ===");
        System.out.println("  Archivo      : " + archivo);
        System.out.println("  Interceptores: " + cantInterceptores);
        System.out.println("  Recarga      : " + recarga + " ms");
        System.out.println("  Estrategia   : " + estrategia.getNombre());
        System.out.println("  " + estrategia.getDescripcion());
        System.out.println("--------------------------------------------------");

        // Cola de prioridad segun la estrategia elegida.
        // Mayor prioridad() = sale primero.
        Estrategia finalEstrategia = estrategia;
        PriorityBlockingQueue<Amenaza> cola = new PriorityBlockingQueue<>(
                32,
                Comparator.comparingDouble((Amenaza a) -> a.prioridad(finalEstrategia))
                          .reversed());

        Estadisticas stats = new Estadisticas();
        long inicio = System.currentTimeMillis();
        stats.setConfiguracion(inicio, cantInterceptores, estrategia.getNombre());

        // Crear hilos
        Generador     generador    = new Generador(archivo, cola, stats, inicio,
                                                   pausaLlegadas, estrategia);
        Interceptor[] interceptores = new Interceptor[cantInterceptores];
        for (int i = 0; i < cantInterceptores; i++) {
            interceptores[i] = new Interceptor(i + 1, cola, stats, inicio, recarga);
        }
        Monitor monitor = new Monitor(cola, stats, tickMonitor);

        // Arrancar
        monitor.start();
        for (Interceptor in : interceptores) in.start();
        generador.start();

        // Esperar a que terminen todas las amenazas
        while (!(generador.isTerminado()
                && stats.getResueltas() >= stats.getGeneradas())) {
            Thread.sleep(50);
        }

        long fin = System.currentTimeMillis();
        stats.setFin(fin);

        // Apagar hilos ordenadamente
        monitor.detener();
        monitor.interrupt();
        for (Interceptor in : interceptores) {
            in.detener();
            in.interrupt();
        }

        generador.join(2000);
        monitor.join(2000);
        for (Interceptor in : interceptores) in.join(2000);

        if (imprimirResumen) {
            stats.imprimirResumen(fin);
        } else {
            // Guarda el fin para el CSV en modo batch
            stats.imprimirResumen(fin);
        }

        return stats;
    }

    // ------------------------------------------------------------------ //
    // Utilitarios                                                          //
    // ------------------------------------------------------------------ //

    private static int leerEntero(Scanner sc, int min, int max) {
        while (true) {
            try {
                String linea = sc.nextLine().trim();
                int v = linea.isEmpty() ? min : Integer.parseInt(linea);
                if (v >= min && v <= max) return v;
            } catch (NumberFormatException ignored) {}
            System.out.print("Valor invalido. Ingrese un numero entre " + min + " y " + max + ": ");
        }
    }

    private static String truncar(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
