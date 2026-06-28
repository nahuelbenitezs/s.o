import java.util.Comparator;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/*
 * Clase principal del Sistema Concurrente de Intercepcion de Amenazas Aereas.
 *
 * USO:
 *   java Simulador
 *   -> menu interactivo para elegir escenario, interceptores, recarga,
 *      estrategia y modo de ejecucion (paralelo o demo secuencial).
 *
 * COMPONENTES CONCURRENTES:
 *   1 hilo Generador     : lee el archivo y deposita amenazas en la cola.
 *   N hilos Interceptor  : compiten por atender las amenazas de la cola.
 *   1 hilo Monitor       : hace correr el tiempo; impacta las que expiran.
 *   (solo en modo MLQ) 1 hilo Clasificador + 3 Monitor, uno por nivel.
 *
 * RECURSO COMPARTIDO:
 *   PriorityBlockingQueue<Amenaza> con Comparator segun la estrategia elegida.
 *   Es thread-safe (lock interno del JDK) y mantiene el orden de prioridad
 *   SIEMPRE QUE las prioridades no cambien mientras los elementos estan
 *   dentro de la cola. Por eso el Monitor, en cada tick, hace drainTo()
 *   + recalculo + reinsercion en vez de mutar elementos in-place (ver
 *   Monitor.java para el detalle del bug que esto corrige).
 *
 *   Cuando la estrategia es MLQ se usan 3 PriorityBlockingQueue en paralelo,
 *   una por nivel de criticidad (ALTA/MEDIA/BAJA), cada una con su propio
 *   Comparator y su propio Monitor; los Interceptores intentan tomar
 *   siempre primero de ALTA, luego MEDIA, luego BAJA (ver Interceptor.tomarSiguiente()).
 *
 * RESOLUCION DE TIEMPO:
 *   Un unico tick (TICK_MS=200) se usa tanto para el paso del reloj del
 *   Monitor como para el timeout de poll() de los Interceptores, de
 *   forma que todo el sistema comparte la misma nocion de "instante".
 *   Es una resolucion fija de 200ms para toda la simulacion, sin importar
 *   la estrategia elegida.
 *
 * SINCRONIZACION (sin usar synchronized, segun la letra):
 *   - Semaforo binario  (Semaphore(1)) en Estadisticas -> exclusion mutua en contadores.
 *   - Semaforo contador (Semaphore(N)) en Interceptor  -> capacidad limitada de
 *     recursos de intercepcion, explicita y verificable.
 *   - AtomicReference + compareAndSet en Amenaza       -> transiciones de estado lock-free.
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
        System.out.println("  [5] escenario_mlq.txt            (contraste entre niveles ALTA/MEDIA/BAJA, para la estrategia MLQ)");
        System.out.println("  [6] Ingresar nombre de archivo manualmente");
        System.out.print("Elija escenario [1-6]: ");

        String archivo;
        int opEsc = leerEntero(sc, 1, 6);
        switch (opEsc) {
            case 1:  archivo = "amenazas.txt";                  break;
            case 2:  archivo = "escenario_borde_critico.txt";   break;
            case 3:  archivo = "escenario_hipersonico.txt";     break;
            case 4:  archivo = "escenario_borde_limite.txt";    break;
            case 5:  archivo = "escenario_mlq.txt";             break;
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

        // --- Modo de ejecucion ---
        // El modo demo fuerza 1 solo interceptor y mas pausa entre llegadas,
        // para poder leer el log linea por linea y verificar a ojo que el
        // orden de atencion respeta la prioridad de la estrategia elegida.
        // El escenario de saturacion real (el que pide la letra) se sigue
        // demostrando en el modo paralelo, con la cantidad de interceptores
        // que el usuario haya elegido arriba.
        System.out.println("\nModo de ejecucion:");
        System.out.println("  [1] Paralelo (N interceptores reales compitiendo)");
        System.out.println("  [2] Demo secuencial (1 interceptor, log facil de leer)");
        System.out.print("Elija modo [1-2]: ");
        int opModo = leerEntero(sc, 1, 2);

        int interceptoresFinal = (opModo == 2) ? 1 : interceptores;
        int pausaFinal         = (opModo == 2) ? Math.max(DEF_PAUSA_LLEGADAS, 600) : DEF_PAUSA_LLEGADAS;

        if (estrategia == Estrategia.MLQ) {
            ejecutarMLQ(archivo, interceptoresFinal, recarga, pausaFinal, TICK_MS, estrategia);
        } else {
            ejecutar(archivo, interceptoresFinal, recarga,
                     pausaFinal, TICK_MS, estrategia);
        }
    }

    // ------------------------------------------------------------------ //
    // Ejecucion de una simulacion con una sola cola de prioridad           //
    // (todas las estrategias salvo MLQ)                                    //
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

        // Semaforo contador: modela explicitamente la capacidad limitada de
        // N recursos de intercepcion (ver comentario en Interceptor.java).
        Semaphore recursos = new Semaphore(cantInterceptores);

        // --- Crear hilos ---
        Generador generador = new Generador(archivo, cola, stats, inicio,
                                            pausaLlegadas, estrategia);
        Interceptor[] interceptores = new Interceptor[cantInterceptores];
        for (int i = 0; i < cantInterceptores; i++) {
            interceptores[i] = new Interceptor(i + 1, cola, stats, recursos,
                                               inicio, recarga, tickMonitor);
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
    // Ejecucion de una simulacion con MLQ: 3 colas por nivel de criticidad //
    // ------------------------------------------------------------------ //
    private static void ejecutarMLQ(String archivo, int cantInterceptores, int recarga,
                                    int pausaLlegadas, int tickMonitor,
                                    Estrategia estrategia)
            throws InterruptedException {

        System.out.println("\n=== Iniciando simulacion (MLQ) ===");
        System.out.println("  Archivo      : " + archivo);
        System.out.println("  Interceptores: " + cantInterceptores);
        System.out.println("  Recarga      : " + recarga + " ms");
        System.out.println("  Estrategia   : " + estrategia.getNombre());
        System.out.println("  " + estrategia.getDescripcion());
        System.out.println("----------------------------");

        // Cola de llegadas cruda: el Generador no sabe nada de MLQ, solo
        // deposita amenazas en orden de llegada, igual que en el modo normal.
        // Al ser solo un buffer de paso hacia el Clasificador (no se decide
        // ninguna prioridad de atencion aqui), se usa una cola FCFS simple.
        LinkedBlockingQueue<Amenaza> colaLlegadas = new LinkedBlockingQueue<>();

        // Las 3 colas por nivel, cada una con su propio criterio interno:
        //   ALTA  -> EDF: menor tiempoRestante primero.
        //   MEDIA -> FCFS: orden de llegada.
        //   BAJA  -> FCFS: orden de llegada (se atiende cuando ALTA y MEDIA
        //            estan vacias; al haber como maximo unas pocas amenazas
        //            de baja criticidad por escenario, el orden de llegada
        //            ya reparte la atencion razonablemente entre ellas).
        PriorityBlockingQueue<Amenaza> colaAlta = new PriorityBlockingQueue<>(
                16, Comparator.comparingInt(Amenaza::getTiempoRestante));
        PriorityBlockingQueue<Amenaza> colaMedia = new PriorityBlockingQueue<>(
                16, Comparator.comparingLong(Amenaza::getInstanteAparicion));
        PriorityBlockingQueue<Amenaza> colaBaja = new PriorityBlockingQueue<>(
                16, Comparator.comparingLong(Amenaza::getInstanteAparicion));

        Estadisticas stats = new Estadisticas();
        long inicio = System.currentTimeMillis();
        stats.setConfiguracion(inicio, cantInterceptores, estrategia.getNombre());

        Semaphore recursos = new Semaphore(cantInterceptores);

        Generador generador = new Generador(archivo, colaLlegadas, stats, inicio,
                                            pausaLlegadas, estrategia);

        // Hilo Clasificador: reparte cada llegada a la cola de su nivel.
        // Es un hilo liviano (lambda) y no necesita un archivo propio porque
        // su unica responsabilidad es leer de una cola y escribir en otra.
        Thread clasificador = new Thread(() -> {
            while (true) {
                Amenaza a;
                try {
                    a = colaLlegadas.poll(tickMonitor, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (a == null) {
                    if (generador.isTerminado() && colaLlegadas.isEmpty()) return;
                    continue;
                }
                switch (a.nivelMLQ()) {
                    case 0: colaAlta.put(a);  break;
                    case 1: colaMedia.put(a); break;
                    default: colaBaja.put(a); break;
                }
            }
        }, "Clasificador");

        Interceptor[] interceptores = new Interceptor[cantInterceptores];
        for (int i = 0; i < cantInterceptores; i++) {
            interceptores[i] = new Interceptor(i + 1, stats, recursos, inicio,
                                               recarga, tickMonitor,
                                               colaAlta, colaMedia, colaBaja);
        }

        Monitor monitorAlta  = new Monitor(colaAlta,  stats, tickMonitor);
        Monitor monitorMedia = new Monitor(colaMedia, stats, tickMonitor);
        Monitor monitorBaja  = new Monitor(colaBaja,  stats, tickMonitor);

        // --- Arrancar ---
        monitorAlta.start();
        monitorMedia.start();
        monitorBaja.start();
        for (Interceptor in : interceptores) in.start();
        clasificador.start();
        generador.start();

        // --- Esperar a que terminen todas las amenazas ---
        while (!(generador.isTerminado()
                && stats.getResueltas() >= stats.getGeneradas())) {
            Thread.sleep(50);
        }

        long fin = System.currentTimeMillis();
        stats.setFin(fin);

        // --- Apagar hilos ordenadamente ---
        clasificador.interrupt();
        monitorAlta.detener();  monitorAlta.interrupt();
        monitorMedia.detener(); monitorMedia.interrupt();
        monitorBaja.detener();  monitorBaja.interrupt();
        for (Interceptor in : interceptores) {
            in.detener();
            in.interrupt();
        }

        generador.join(2000);
        clasificador.join(2000);
        monitorAlta.join(2000);
        monitorMedia.join(2000);
        monitorBaja.join(2000);
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
