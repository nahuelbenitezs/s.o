import java.util.Scanner;
import java.util.concurrent.Semaphore;

/*
 * Clase principal. Arma y corre la simulacion.
 *
 * Hilos: 1 Generador (productor), 1 Despachador (planificador) y N Interceptores
 * (recursos, controlados por un Semaphore(N)).
 *
 * Uso:
 *   java Simulador                    -> menu para elegir archivo, N, recarga y estrategia
 *   java Simulador [archivo] [N] [recargaMs] [estrategia]
 *     estrategia: 1 = Prioridades+envejecimiento (default)
 *                 2 = MLQ (3 colas)
 *                 3 = EDF
 *   java Simulador batch [archivo]   -> compara las estrategias variando la recarga
 */
public class Simulador {

    private static final int CANT_INTERCEPTORES = 2;
    private static final int TIEMPO_RECARGA_MS  = 1000;
    private static final int PAUSA_LLEGADAS_MS  = 250;

    public static void main(String[] args) {
        if (args.length == 0) {
            menu();
            return;
        }
        if (args[0].equalsIgnoreCase("batch")) {
            String archivo = (args.length >= 2) ? args[1] : "amenazas.txt";
            batch(archivo);
            return;
        }

        String archivo = args[0];
        int n = (args.length >= 2) ? Integer.parseInt(args[1]) : CANT_INTERCEPTORES;
        int recarga = (args.length >= 3) ? Integer.parseInt(args[2]) : TIEMPO_RECARGA_MS;
        int estrategiaId = (args.length >= 4) ? Integer.parseInt(args[3]) : 1;

        ejecutar(archivo, n, recarga, crearEstrategia(estrategiaId));
    }

    // menu interactivo: pregunta escenario, N, recarga y estrategia
    private static void menu() {
        Scanner sc = new Scanner(System.in);

        System.out.println("=== Sistema concurrente de intercepcion de amenazas aereas ===");
        System.out.println("(en cada pregunta, toca Enter para usar el valor entre parentesis)");
        System.out.println();

        System.out.println("Escenario a simular:");
        System.out.println("  1) amenazas.txt           - saturacion general (12 amenazas)");
        System.out.println("  2) amenazas_critico.txt   - criticidad vs urgencia (10 amenazas)");
        System.out.println("  3) escenario_liviano.txt  - sin saturacion (5 amenazas)");
        System.out.println("  4) escenario_saturado.txt - saturacion fuerte (12 amenazas)");
        System.out.print("Elegir escenario 1-4 (Enter = 1): ");
        String archivo = archivoDeOpcion(leerInt(sc, 1));

        System.out.print("Cantidad de interceptores (Enter = " + CANT_INTERCEPTORES + "): ");
        int n = leerInt(sc, CANT_INTERCEPTORES);

        System.out.print("Tiempo de recarga en ms (Enter = " + TIEMPO_RECARGA_MS + "): ");
        int recarga = leerInt(sc, TIEMPO_RECARGA_MS);

        System.out.println("Estrategia de planificacion:");
        System.out.println("  1) Prioridades (Event-Driven) con envejecimiento");
        System.out.println("  2) MLQ - 3 colas (EDF / FCFS / rotacion)");
        System.out.println("  3) EDF (menor tiempo hasta el impacto)");
        System.out.print("Elegir estrategia 1-3 (Enter = 1): ");
        int estrategiaId = leerInt(sc, 1);

        System.out.println();
        ejecutar(archivo, n, recarga, crearEstrategia(estrategiaId));
    }

    // mapea la opcion 1-4 del menu al nombre de archivo
    private static String archivoDeOpcion(int opcion) {
        switch (opcion) {
            case 2:  return "amenazas_critico.txt";
            case 3:  return "escenario_liviano.txt";
            case 4:  return "escenario_saturado.txt";
            default: return "amenazas.txt";
        }
    }

    // lee un entero de la consola; si la linea esta vacia o es invalida usa porDefecto
    private static int leerInt(Scanner sc, int porDefecto) {
        String linea = sc.nextLine().trim();
        if (linea.isEmpty()) return porDefecto;
        try {
            return Integer.parseInt(linea);
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }

    // imprime la cabecera, corre la simulacion y muestra el resumen
    private static void ejecutar(String archivo, int n, int recarga, Estrategia estrategia) {
        System.out.println("=== Sistema concurrente de intercepcion de amenazas aereas ===");
        System.out.println("Archivo de escenario : " + archivo);
        System.out.println("Interceptores (N)    : " + n);
        System.out.println("Tiempo de recarga    : " + recarga + " ms");
        System.out.println("Estrategia           : " + estrategia.nombre());
        System.out.println("--------------------------------------------------------------");

        Resultado r = correr(archivo, n, recarga, estrategia, true);
        r.stats.imprimirResumen(r.interceptores, r.tFin);
    }

    // corre una simulacion completa y devuelve sus resultados
    private static Resultado correr(String archivo, int n, int recarga,
                                    Estrategia estrategia, boolean verbose) {
        Estadisticas stats = new Estadisticas();
        RelojVirtual reloj = new RelojVirtual();
        ColaLlegadas cola = new ColaLlegadas();
        Semaphore recursos = new Semaphore(n);   // N interceptores

        Interceptor[] interceptores = new Interceptor[n];
        for (int i = 0; i < n; i++) {
            interceptores[i] = new Interceptor(i + 1, recursos, stats);
        }

        Generador generador = new Generador(archivo, cola, stats, PAUSA_LLEGADAS_MS, verbose);
        Despachador despachador = new Despachador(cola, stats, reloj, recarga,
                estrategia, interceptores, recursos, verbose);

        for (Interceptor ic : interceptores) {
            ic.start();
        }
        generador.start();
        despachador.start();
        try {
            generador.join();
            despachador.join();
            for (Interceptor ic : interceptores) {
                ic.detener();
            }
            for (Interceptor ic : interceptores) {
                ic.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return new Resultado(stats, interceptores, reloj.ahora());
    }

    private static Estrategia crearEstrategia(int id) {
        switch (id) {
            case 2:  return new ColasMultinivel();
            case 3:  return new EstrategiaEDF();
            default: return new PrioridadCombinada();
        }
    }

    // corre las 3 estrategias con varias recargas e imprime una tabla comparativa
    private static void batch(String archivo) {
        int[] recargas = {500, 1000, 1500};
        int[] estrategias = {1, 2, 3};
        int n = CANT_INTERCEPTORES;

        System.out.println("=== COMPARACION DE ESTRATEGIAS (archivo=" + archivo
                + ", N=" + n + ") ===");
        System.out.printf("%-34s %7s %4s %6s %6s %8s %6s %8s %6s%n",
                "Estrategia", "recarga", "gen", "inter", "impac", "espInt", "tasa%",
                "critImp", "util%");
        System.out.println("--------------------------------------------------------------------------------------------");

        for (int eid : estrategias) {
            for (int recarga : recargas) {
                Estrategia est = crearEstrategia(eid);
                Resultado r = correr(archivo, n, recarga, est, false);

                int gen = r.stats.getGeneradas();
                int inter = r.stats.getInterceptadas();
                int impac = r.stats.getImpactadas();
                double tasa = (gen > 0) ? (100.0 * inter / gen) : 0;

                long ocupado = 0;
                for (Interceptor in : r.interceptores) ocupado += in.getTiempoOcupado();
                double util = (r.tFin > 0) ? (100.0 * ocupado / ((long) n * r.tFin)) : 0;

                String nom = est.nombre();
                if (nom.length() > 34) nom = nom.substring(0, 34);
                System.out.printf("%-34s %7d %4d %6d %6d %8d %6.1f %8d %6.1f%n",
                        nom, recarga, gen, inter, impac, r.stats.getEsperaPromedio(),
                        tasa, r.stats.getCriticidadImpactada(), util);
            }
            System.out.println();
        }
        System.out.println("A mayor recarga hay menos capacidad y mas impactos. Que estrategia");
        System.out.println("conviene depende de la saturacion: con poca recarga EDF salva mas, y en");
        System.out.println("saturacion la prioridad por criticidad protege mejor a las zonas criticas.");
    }

    // resultados de una corrida
    private static class Resultado {
        final Estadisticas stats;
        final Interceptor[] interceptores;
        final long tFin;

        Resultado(Estadisticas stats, Interceptor[] interceptores, long tFin) {
            this.stats = stats;
            this.interceptores = interceptores;
            this.tFin = tFin;
        }
    }
}
