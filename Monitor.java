import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/*
 * Hilo Monitor: controla el paso del tiempo para las amenazas pendientes.
 *
 * Cada "tick" (periodo configurable) reduce el tiempoRestante de cada
 * amenaza que sigue en la cola. La reduccion ya esta ajustada por la
 * velocidad del misil dentro de Amenaza.reducirTiempo().
 *
 * Si el tiempo de una amenaza llega a cero o menos:
 *   - Intenta marcarla IMPACTADA via CAS (intentarImpactar()).
 *   - Si el CAS tiene exito: se descarta (no vuelve a la cola) y se
 *     registra el impacto.
 *   - Si falla: un interceptor ya la tomo a tiempo; no se hace nada.
 *
 * BUG CORREGIDO (re-ordenamiento dinamico de la cola):
 *   La version anterior mutaba a.reducirTiempo(tick) sobre objetos que
 *   seguian DENTRO del heap de la PriorityBlockingQueue. Una
 *   PriorityBlockingQueue solo reordena sus elementos al insertar (put)
 *   o extraer (poll/take); si la prioridad de un elemento cambia mientras
 *   sigue adentro, la invariante de heap queda corrompida en silencio
 *   (no lanza excepcion, simplemente deja de devolver el orden correcto).
 *   Esto se pudo confirmar en escenario_borde_limite.txt: con la version
 *   anterior, una Central Electrica llegaba a impactar mientras quedaban
 *   Hospitales pendientes con la estrategia MAYOR_CRITICIDAD.
 *
 *   La correccion es un "drain + recompute + reinsert" en cada tick:
 *     1. Se vacia la cola por completo (drainTo) de forma atomica.
 *     2. Sobre la copia local (ya no compartida), se actualiza el tiempo
 *        de cada amenaza con tranquilidad, sin que otro hilo la este
 *        mirando dentro del heap.
 *     3. Las que impactaron se descartan (CAS) y NO se reinsertan.
 *     4. Las que siguen vivas se reinsertan con cola.put(a): es en este
 *        paso que el heap recalcula su posicion segun la prioridad YA
 *        actualizada. Asi la cola nunca contiene una prioridad vieja.
 *
 *   Durante el breve instante del drain, un Interceptor que llame a
 *   poll() puede no encontrar nada (cola temporalmente vacia) y seguira
 *   su loop normalmente (poll con timeout); no se pierde ninguna amenaza
 *   porque drainTo() devuelve todos los elementos sacados y se reinsertan
 *   todos los que siguen pendientes.
 *
 * RESOLUCION DE TIEMPO FIJA:
 *   El parametro tick es la unica resolucion temporal del sistema: el
 *   Simulador lo fija en TICK_MS=200 y lo pasa tanto al Monitor (periodo
 *   de actualizacion) como al Interceptor (timeout de poll()), de forma
 *   que ambos compartan la misma nocion de "instante". No es un reloj
 *   logico por eventos (el tiempo real de Thread.sleep sigue marcando el
 *   paso de la simulacion), pero al quedar unificado en una sola constante
 *   se elimina el desajuste de tener dos resoluciones distintas conviviendo
 *   en el mismo sistema.
 *
 * COMPATIBILIDAD CON MLQ:
 *   Esta clase no conoce la estrategia activa: actualiza tiempos e impacta
 *   amenazas vencidas sobre cualquier BlockingQueue<Amenaza> que reciba.
 *   Cuando la estrategia elegida es Estrategia.MLQ, el Simulador arma 3
 *   colas (ALTA/MEDIA/BAJA) y lanza 3 instancias de Monitor, una por cada
 *   cola, sin necesidad de modificar esta clase.
 */
public class Monitor extends Thread {

    private final BlockingQueue<Amenaza> cola;
    private final Estadisticas           stats;
    private final int                    tick; // ms de resolucion fija del reloj (200ms)

    private volatile boolean activo = true;

    public Monitor(BlockingQueue<Amenaza> cola, Estadisticas stats, int tick) {
        super("Monitor");
        this.cola  = cola;
        this.stats = stats;
        this.tick  = tick;
    }

    @Override
    public void run() {
        while (activo) {
            try {
                Thread.sleep(tick);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            // 1. Vaciar la cola compartida de una sola vez (operacion atomica
            //    de BlockingQueue). A partir de aqui trabajamos sobre una
            //    lista local que ningun otro hilo puede ver ni modificar.
            List<Amenaza> snapshot = new ArrayList<>();
            cola.drainTo(snapshot);

            try {
                for (Amenaza a : snapshot) {
                    a.reducirTiempo(tick);

                    if (a.getTiempoRestante() <= 0) {
                        // Amenaza vencida: intentamos impactarla via CAS.
                        if (a.intentarImpactar()) {
                            stats.registrarImpactada(
                                    a.getZona().getCriticidad(),
                                    a.danioEfectivo());
                            System.out.printf("[Monitor] !!! IMPACTO %s  danio=%.1f%n",
                                    a, a.danioEfectivo());
                            // No se reinserta: queda fuera de la cola para siempre.
                        }
                        // Si el CAS falla, un interceptor ya la tomo a tiempo
                        // (EN_PROCESO); tampoco se reinserta, ya no esta pendiente.
                    } else {
                        // Sigue viva: se reinserta con el tiempo YA actualizado.
                        // Es este put() el que reconstruye correctamente el heap
                        // de prioridad segun la estrategia activa.
                        cola.put(a);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("[Monitor] detenido.");
    }

    public void detener() { activo = false; }
}
