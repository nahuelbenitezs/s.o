import java.util.Comparator;
import java.util.Scanner;
import java.util.concurrent.PriorityBlockingQueue;

/*
 * Clase principal del Sistema Concurrente de Intercepcion de Amenazas Aereas.
 *
 * USO:
 *   java Simulador
 *   -> menu interactivo para elegir escenario, interceptores, recarga y estrategia.
 *
 * COMPONENTES CONCURRENTES:
 *   1 hilo Generador   : lee el archivo y deposita amenazas en la cola.
 *   N hilos Interceptor: compiten por atender las amenazas de la cola.
 *   1 hilo Monitor     : hace correr el tiempo; impacta las que expiran.
 *
 * RECURSO COMPARTIDO:
 *   PriorityBlockingQueue<Amenaza> con Comparator segun la estrategia elegida.
 *   Es thread-safe (lock interno del JDK) y mantiene el orden de prioridad
 *   SIEMPRE QUE las prioridades no cambien mientras los elementos estan
 *   dentro de la cola. Por eso el Monitor, en cada tick, hace drainTo()
 *   + recalculo + reinsercion en vez de mutar elementos in-place (ver
 *   Monitor.java para el detalle del bug que esto corrige).
 *
 * RESOLUCION DE TIEMPO:
 *   Un unico tick (TICK_MS) se usa tanto para el paso del reloj del
 *   Monitor como para el timeout de poll() de los Interceptores, de
 *   forma que todo el sistema comparte la misma nocion de "instante".
 *
 * SINCRONIZACION (sin usar synchronized, segun la letra):
 *   - Semaforo binario (Semaphore(1)) en Estadisticas -> exclusion mutua en contadores.
 *   - AtomicReference + compareAndSet en Amenaza      -> transiciones de estado lock-free.
 */
public class Simulador {

    // ------------------------------------------------------------------ //
    // Parametros por defecto                                               //
    // ------------------------------------------------------------------ //
    private static final int DEF_INTERCEPTORES  = 2;
    private static final int DEF_RECARGA_MS     = 1000;
    private static final int DEF_PAUSA_LLEGADAS = 250;  // ms entre amenazas

    // Resolucion de tiempo unica del sistema: el Monitor reduce el tiempo
    // restante cada TICK_MS, y el Interceptor hace poll() con ese mismo
    // timeout. Antes el Monitor usaba 100ms y el Interceptor 200ms
    // hardcodeado por separado: dos relojes distintos para el mismo
    // sistema simulado. Unificarlos en una sola constante evita esa
    // inconsistencia y hace que el "paso del tiempo" sea uno solo.
    private static final int TICK_MS            = 200;  // resolucion del reloj (ms)

    // ------------------------------------------------------------------ //
    // Punto de entrada                                                     //
    // ------------------------------------------------------------------ //
    public static void main(String[] args) throws InterruptedException {
        modoInteractivo();
    }

    // ------------------------------------------------------------------ //
    // Modo interactivo                                                     //
    // ------------------------------------------------------------------ //
    private static void modoInteractivo() throws InterruptedException {
        Scanner sc = new Scanner(System.in);

        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║  Sistema Concurrente de Intercepcion de Amenazas Aereas  ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");

        // --- Escenario ---
        System.out.println("\nEscenarios disponibles:");
        System.out.println("  [1] amenazas.txt                 (saturacion, 12 amenazas mixtas)");
        System.out.println("  [2] escenario_borde_critico.txt  (solo zonas criticas, tiempos cortos)");
        System.out.println("  [3] escenario_hipersonico.txt    (misiles de alta velocidad)");
        System.out.println("  [4] escenario_borde_limite.txt   (caso limite: 1 interceptor)");
        System.out.println("  [5] Ingresar nombre de archivo manualmente");
        System.out.print("Elija escenario [1-5]: ");

        String archivo;
        int opEsc = leerEntero(sc, 1, 5);
        switch (opEsc) {
            case 1:  archivo = "amenazas.txt";                  break;
            case 2:  archivo = "escenario_borde_critico.txt";   break;
            case 3:  archivo = "escenario_hipersonico.txt";     break;
            case 4:  archivo = "escenario_borde_limite.txt";    break;
            default:
                System.out.print("Nombre del archivo: ");
                archivo = sc.nextLine().trim();
        }

        // --- Parametros ---
        System.out.print("\nCantidad de interceptores [" + DEF_INTERCEPTORES + "]: ");
        String inp = sc.nextLine().trim();
        int interceptores = inp.isEmpty() ? DEF_INTERCEPTORES : Integer.parseInt(inp);

        System.out.print("Tiempo de recarga (ms) [" + DEF_RECARGA_MS + "]: ");
        inp = sc.nextLine().trim();
        int recarga = inp.isEmpty() ? DEF_RECARGA_MS : Integer.parseInt(inp);

        // --- Estrategia ---
        Estrategia.imprimirMenu();
        System.out.print("Elija estrategia [1-" + Estrategia.values().length + "]: ");
        int opEst = leerEntero(sc, 1, Estrategia.values().length);
        Estrategia estrategia = Estrategia.values()[opEst - 1];

        ejecutar(archivo, interceptores, recarga,
                 DEF_PAUSA_LLEGADAS, TICK_MS, estrategia);
    }

    // ------------------------------------------------------------------ //
    // Ejecucion de una simulacion                                          //
    // ------------------------------------------------------------------ //
    private static void ejecutar(String archivo, int cantInterceptores, int recarga,
                                 int pausaLlegadas, int tickMonitor,
                                 Estrategia estrategia)
            throws InterruptedException {

        System.out.println("\n=== Iniciando simulacion ===");
        System.out.println("  Archivo      : " + archivo);
        System.out.println("  Interceptores: " + cantInterceptores);
        System.out.println("  Recarga      : " + recarga + " ms");
        System.out.println("  Estrategia   : " + estrategia.getNombre());
        System.out.println("  " + estrategia.getDescripcion());
        System.out.println("----------------------------");

        // Cola de prioridad segun la estrategia elegida.
        // Mayor prioridad() = sale primero.
        PriorityBlockingQueue<Amenaza> cola = new PriorityBlockingQueue<>(
                32,
                Comparator.comparingDouble((Amenaza a) -> a.prioridad(estrategia))
                          .reversed());

        Estadisticas stats = new Estadisticas();
        long inicio = System.currentTimeMillis();
        stats.setConfiguracion(inicio, cantInterceptores, estrategia.getNombre());

        // --- Crear hilos ---
        Generador generador = new Generador(archivo, cola, stats, inicio,
                                            pausaLlegadas, estrategia);
        Interceptor[] interceptores = new Interceptor[cantInterceptores];
        for (int i = 0; i < cantInterceptores; i++) {
            interceptores[i] = new Interceptor(i + 1, cola, stats, inicio, recarga, tickMonitor);
        }
        Monitor monitor = new Monitor(cola, stats, tickMonitor);

        // --- Arrancar ---
        monitor.start();
        for (Interceptor in : interceptores) in.start();
        generador.start();

        // --- Esperar a que terminen todas las amenazas ---
        while (!(generador.isTerminado()
                && stats.getResueltas() >= stats.getGeneradas())) {
            Thread.sleep(50);
        }

        long fin = System.currentTimeMillis();
        stats.setFin(fin);

        // --- Apagar hilos ordenadamente ---
        monitor.detener();
        monitor.interrupt();
        for (Interceptor in : interceptores) {
            in.detener();
            in.interrupt();
        }

        generador.join(2000);
        monitor.join(2000);
        for (Interceptor in : interceptores) in.join(2000);

        stats.imprimirResumen(fin);
    }

    // ------------------------------------------------------------------ //
    // Utilitario                                                           //
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
}
