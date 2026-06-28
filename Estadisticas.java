import java.util.concurrent.Semaphore;

/*
 * Guarda los contadores compartidos de la simulacion.
 *
 * SINCRONIZACION: semaforo (Semaphore(1)) mutex
 *
 * Metricas incluidas:
 *   - generadas, interceptadas, impactadas
 *   - tiempo promedio de espera de las interceptadas
 *   - utilizacion de interceptores (tiempo activo / tiempo total)
 *   - criticidad total impactada (suma de criticidad de zonas impactadas)
 *   - danio total impactado (criticidad * factorDanio de cada misil)
 *   - tasa de intercepcion (%)
 */
public class Estadisticas {

    private final Semaphore mutex = new Semaphore(1);

    // Contadores base
    private int generadas = 0;
    private int interceptadas = 0;
    private int impactadas = 0;

    // Tiempo de espera
    private long sumaEsperaInterceptadas = 0;

    // Metricas de danio
    private double criticidadImpactada = 0; // suma de criticidad de zonas impactadas
    private double danioTotalImpactado = 0; // criticidad * factorDanio del misil

    // Utilizacion de interceptores
    private long sumaOcupacionMs = 0; // suma de milisegundos ocupados de todos los interceptores
    private long inicioSimulacion = 0;
    private int  cantInterceptores = 0;

    // Nombre de la estrategia actual
    private String nombreEstrategia = "";

    // Fin de la simulacion
    private long finSimulacion = 0;

    public void setConfiguracion(long inicioSimulacion, int cantInterceptores, String nombreEstrategia) {
        try {
            mutex.acquire();
            this.inicioSimulacion = inicioSimulacion;
            this.cantInterceptores = cantInterceptores;
            this.nombreEstrategia = nombreEstrategia;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            mutex.release();
        }
    }

    // Registros

    public void setFin(long finMs) {
        try {
            mutex.acquire();
            this.finSimulacion = finMs;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            mutex.release();
        }
    }

    // Devuelve el CSV
    public String toCSV() {
        return toCSV(finSimulacion > 0 ? finSimulacion : System.currentTimeMillis());
    }

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

    public void registrarInterceptada(long tiempoEsperaMs, long tiempoOcupacionMs) {
        try {
            mutex.acquire();
            interceptadas++;
            sumaEsperaInterceptadas += tiempoEsperaMs;
            sumaOcupacionMs += tiempoOcupacionMs;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            mutex.release();
        }
    }

    public void registrarImpactada(double criticidad, double danioEfectivo) {
        try {
            mutex.acquire();
            impactadas++;
            criticidadImpactada += criticidad;
            danioTotalImpactado += danioEfectivo;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            mutex.release();
        }
    }

    // Lecturas (con proteccion del semaforo)

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

    // Resumen estadistico

    public void imprimirResumen(long finSimulacion) {
        try {
            mutex.acquire();
            long duracionTotal = finSimulacion - inicioSimulacion;

            System.out.println();
            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║           RESUMEN DE LA SIMULACION                       ║");
            System.out.printf ("║  Estrategia: %-42s ║%n", nombreEstrategia);
            System.out.println("╠══════════════════════════════════════════════════════════╣");
            System.out.printf ("║  Amenazas generadas     : %-30d ║%n", generadas);
            System.out.printf ("║  Amenazas interceptadas : %-30d ║%n", interceptadas);
            System.out.printf ("║  Amenazas impactadas    : %-30d ║%n", impactadas);

            if (generadas > 0) {
                double tasa = (100.0 * interceptadas) / generadas;
                System.out.printf("║  Tasa de intercepcion   : %-29.1f%% ║%n", tasa);
            }

            if (interceptadas > 0) {
                long promEspera = sumaEsperaInterceptadas / interceptadas;
                System.out.printf("║  Tiempo prom. espera    : %-27d ms ║%n", promEspera);
            }

            // Utilizacion de interceptores
            if (cantInterceptores > 0 && duracionTotal > 0) {
                long capacidadTotal = (long) cantInterceptores * duracionTotal;
                double utilizacion  = (100.0 * sumaOcupacionMs) / capacidadTotal;
                System.out.printf("║  Utilizacion interceptores: %-25.1f%% ║%n", utilizacion);
            }

            System.out.printf ("║  Criticidad total impactada: %-25.0f ║%n", criticidadImpactada);
            System.out.printf ("║  Danio total impactado  : %-30.2f ║%n", danioTotalImpactado);
            System.out.printf ("║  Duracion simulacion    : %-27d ms ║%n", duracionTotal);
            System.out.println("╚══════════════════════════════════════════════════════════╝");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            mutex.release();
        }
    }

    /**
     * Devuelve una linea CSV con las metricas para comparar varias ejecuciones.
     * Cabecera: estrategia,generadas,interceptadas,impactadas,tasa,promEspera,danioTotal
     */
    public String toCSV(long finSimulacion) {
        try {
            mutex.acquire();
            double tasa      = generadas > 0 ? (100.0 * interceptadas) / generadas : 0;
            long   promEspera = interceptadas > 0 ? sumaEsperaInterceptadas / interceptadas : 0;
            long   duracion   = finSimulacion - inicioSimulacion;
            double utilizacion = (cantInterceptores > 0 && duracion > 0)
                    ? (100.0 * sumaOcupacionMs) / ((long) cantInterceptores * duracion)
                    : 0;
            return String.format("%s,%d,%d,%d,%.1f,%d,%.2f,%.0f,%.1f",
                    nombreEstrategia, generadas, interceptadas, impactadas,
                    tasa, promEspera, danioTotalImpactado, criticidadImpactada, utilizacion);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } finally {
            mutex.release();
        }
    }
}
