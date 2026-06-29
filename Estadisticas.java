
import java.util.concurrent.Semaphore;

/*Contadores y metricas de la simulacion. Como los escriben varios hilos
 * (Generador, Interceptores y Despachador), el acceso se protege con un semaforo
 * binario (mutex): primero acquire() y, si salio bien, se libera en el finally.*/
public class Estadisticas {

    private final Semaphore mutex = new Semaphore(1);

    private int generadas = 0;
    private int interceptadas = 0;
    private int impactadas = 0;
    private long sumaEsperaInterceptadas = 0; // espera en cola de las interceptadas
    private long sumaRetornoInterceptadas = 0; // turnaround de las interceptadas
    private int criticidadImpactada = 0; // suma de criticidad de las impactadas

    public void registrarGenerada() {
        if (!tomar()) {
            return;
        }
        try {
            generadas++;
        } finally {
            mutex.release();
        }
    }

    public void registrarInterceptada(long espera, long retorno) {
        if (!tomar()) {
            return;
        }
        try {
            interceptadas++;
            sumaEsperaInterceptadas += espera;
            sumaRetornoInterceptadas += retorno;
        } finally {
            mutex.release();
        }
    }

    public void registrarImpactada(int criticidad) {
        if (!tomar()) {
            return;
        }
        try {
            impactadas++;
            criticidadImpactada += criticidad;
        } finally {
            mutex.release();
        }
    }

    // acquire del mutex; devuelve true solo si lo tomo
    private boolean tomar() {
        try {
            mutex.acquire();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public int getGeneradas() {
        return generadas;
    }

    public int getInterceptadas() {
        return interceptadas;
    }

    public int getImpactadas() {
        return impactadas;
    }

    public int getCriticidadImpactada() {
        return criticidadImpactada;
    }

    public long getEsperaPromedio() {
        return interceptadas > 0 ? sumaEsperaInterceptadas / interceptadas : 0;
    }

    public long getRetornoPromedio() {
        return interceptadas > 0 ? sumaRetornoInterceptadas / interceptadas : 0;
    }

    // resumen final: cantidades, espera, retorno, throughput, utilizacion y criticidad
    public void imprimirResumen(Interceptor[] interceptores, long tFin) {
        System.out.println();
        System.out.println("RESUMEN DE LA SIMULACION");
        System.out.println("Amenazas generadas: " + generadas);
        System.out.println("Amenazas interceptadas: " + interceptadas);
        System.out.println("Amenazas impactadas: " + impactadas);

        if (interceptadas > 0) {
            System.out.println("Tiempo promedio de espera (interceptadas): " + (sumaEsperaInterceptadas / interceptadas) + " ms");
            System.out.println("Tiempo promedio de retorno (turnaround)  : " + (sumaRetornoInterceptadas / interceptadas) + " ms");
        }
        if (generadas > 0) {
            double tasa = (100.0 * interceptadas) / generadas;
            System.out.printf("Tasa de interceptacion: %.1f%%%n", tasa);
        }
        if (tFin > 0) {
            double throughput = (1000.0 * interceptadas) / tFin;
            System.out.printf("Throughput: %.2f intercepciones/segundo%n", throughput);
        }

        long ocupadoTotal = 0;
        for (Interceptor in : interceptores) {
            ocupadoTotal += in.getTiempoOcupado();
        }
        
        if (tFin > 0 && interceptores.length > 0) {
            
            double util = (100.0 * ocupadoTotal) / ((long) interceptores.length * tFin);
            System.out.printf("Utilizacion de los interceptores: %.1f%%%n", util);
            
            for (Interceptor in : interceptores) {
                
                double u = (100.0 * in.getTiempoOcupado()) / tFin;
                System.out.printf("   Interceptor-%d: %d ms ocupado (%.1f%%)%n", in.getNumero(), in.getTiempoOcupado(), u);
            }
        }

        System.out.println("Criticidad total impactada: " + criticidadImpactada + "  (suma de la criticidad de las zonas que recibieron impacto)");
    }
}
