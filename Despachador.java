import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;

/*
 * Hilo Despachador: planificador central con reloj virtual.
 *
 * Es el UNICO hilo que toma decisiones de planificacion y el UNICO que
 * imprime la traza de la simulacion. Esto garantiza orden temporal y determinismo.
 *
 * RELOJ VIRTUAL:
 *   tNow avanza de evento en evento (llegada, fin de servicio, impacto).
 *   No hay Thread.sleep en el camino critico.
 *
 * SEMAFOROS (del pseudocodigo del informe, cap 5.1):
 *   mutex    Semaphore(1) : exclusion mutua del buffer de llegadas.
 *   items    Semaphore(0) : cuenta de llegadas (productor-consumidor).
 *   recursos Semaphore(N) : capacidad limitada; tryAcquire() = TryWait.
 *   Por interceptor: asignacion, finServicio, confirmacion (en Interceptor).
 *
 * ALGORITMO (del pseudocodigo del informe):
 *   proxLlegada = siguienteLlegada()   -> P(items) inicial
 *   mientras (proxLlegada != null) o (agenda no vacia):
 *     elegir el proximo evento (menor t entre llegada y agenda)
 *     avanzar tNow; procesar evento
 *     intentarDespachar()
 */
public class Despachador extends Thread {

    private enum TipoEvento { FIN_SERVICIO, IMPACTO }

    private static class Evento implements Comparable<Evento> {
        final long        tiempo;
        final TipoEvento  tipo;
        final Amenaza     amenaza;
        final Interceptor interceptor; // valido solo para FIN_SERVICIO

        Evento(long t, TipoEvento tipo, Amenaza a, Interceptor i) {
            this.tiempo = t; this.tipo = tipo; this.amenaza = a; this.interceptor = i;
        }
        @Override public int compareTo(Evento o) { return Long.compare(tiempo, o.tiempo); }
    }

    private final BlockingQueue<Amenaza> colaLlegadas;
    private final Semaphore              mutex;
    private final Semaphore              items;
    private final Semaphore              recursos;
    private final Interceptor[]          interceptores;
    private final Estadisticas           stats;
    private final long                   recargaMs;
    private final Estrategia             estrategia;
    private volatile int                 totalEsperado = -1; // cuantas amenazas genera el Generador

    private final PriorityQueue<Evento> agenda = new PriorityQueue<>();

    // Listas de pendientes
    private final List<Amenaza> pendientes      = new ArrayList<>(); // PRIORIDADES / EDF
    private final List<Amenaza> pendientesAlta  = new ArrayList<>(); // MLQ nivel 0
    private final List<Amenaza> pendientesMedia = new ArrayList<>(); // MLQ nivel 1
    private final List<Amenaza> pendientesBaja  = new ArrayList<>(); // MLQ nivel 2
    private int indiceRR = 0; // FCFS circular para nivel BAJA

    private long tNow = 0; // reloj virtual (ms desde inicio)
    private int  llegadasLeidas = 0;
    private int  resueltasTotal = 0; // interceptadas + impactadas

    public Despachador(BlockingQueue<Amenaza> colaLlegadas,
                       Semaphore mutex, Semaphore items, Semaphore recursos,
                       Interceptor[] interceptores, Estadisticas stats,
                       long recargaMs, Estrategia estrategia) {
        super("Despachador");
        this.colaLlegadas  = colaLlegadas;
        this.mutex         = mutex;
        this.items         = items;
        this.recursos      = recursos;
        this.interceptores = interceptores;
        this.stats         = stats;
        this.recargaMs     = recargaMs;
        this.estrategia    = estrategia;
    }

    /** El Generador llama a esto al terminar para que el Despachador sepa cuantas genero. */
    public void setTotalEsperado(int total) {
        this.totalEsperado = total;
    }

    // ------------------------------------------------------------------ //
    // Hilo principal                                                       //
    // ------------------------------------------------------------------ //

    @Override
    public void run() {
        try {
            Amenaza proxLlegada = siguienteLlegada(); // P(items) inicial

            while (proxLlegada != null || !agenda.isEmpty()) {
                long tLleg   = (proxLlegada != null)
                               ? proxLlegada.getInstanteLlegada() : Long.MAX_VALUE;
                long tAgenda = !agenda.isEmpty() ? agenda.peek().tiempo : Long.MAX_VALUE;

                if (tLleg <= tAgenda) {
                    tNow = tLleg;
                    procesarLlegada(proxLlegada);
                    proxLlegada = siguienteLlegada();
                } else {
                    Evento e = agenda.poll();
                    tNow = e.tiempo;
                    if (e.tipo == TipoEvento.FIN_SERVICIO) {
                        procesarFinServicio(e);
                    } else {
                        procesarImpacto(e);
                    }
                }
                intentarDespachar();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("[Despachador] Fin de la simulacion virtual.");
    }

    // ------------------------------------------------------------------ //
    // Lectura del buffer de llegadas                                       //
    // ------------------------------------------------------------------ //

    /**
     * Toma la proxima llegada del buffer.
     * Protocolo productor-consumidor con semaforos:
     *   P(items)  -> espera hasta que haya algo
     *   P(mutex)  -> entra a la seccion critica
     *   tomar()   -> lee la amenaza
     *   V(mutex)  -> sale de la seccion critica
     * Devuelve null cuando no hay mas llegadas (Generador termino y cola vacia).
     */
    private Amenaza siguienteLlegada() throws InterruptedException {
        // Si ya leimos todas las que se van a generar, no bloqueamos mas
        if (totalEsperado >= 0 && llegadasLeidas >= totalEsperado) return null;

        items.acquire();   // P(items): espera una llegada disponible
        mutex.acquire();   // P(mutex): seccion critica del buffer
        Amenaza a = colaLlegadas.poll();
        mutex.release();   // V(mutex)

        if (a != null) llegadasLeidas++;
        return a;
    }

    // ------------------------------------------------------------------ //
    // Procesamiento de eventos                                             //
    // ------------------------------------------------------------------ //

    private void procesarLlegada(Amenaza a) {
        System.out.printf("[Despachador t=%d] LLEGADA %s%n", tNow, a);
        if (estrategia == Estrategia.MLQ) {
            switch (a.nivelMLQ()) {
                case 0: pendientesAlta.add(a);  break;
                case 1: pendientesMedia.add(a); break;
                default: pendientesBaja.add(a);
            }
        } else {
            pendientes.add(a);
        }
        // Agendar el impacto en el deadline de la amenaza
        agenda.add(new Evento(a.getInstanteImpacto(), TipoEvento.IMPACTO, a, null));
    }

    private void procesarFinServicio(Evento e) throws InterruptedException {
        System.out.printf("[Despachador t=%d] FIN_SERVICIO %s%n", tNow, e.amenaza);
        e.interceptor.senializarFinServicio(); // V(finServicio[i])
        e.interceptor.esperarConfirmacion();   // P(confirmacion[i])
        resueltasTotal++;
    }

    private void procesarImpacto(Evento e) {
        Amenaza a = e.amenaza;
        if (a.getEstado() == Estado.EN_PROCESO || a.getEstado() == Estado.INTERCEPTADA) {
            // Ya fue tomada por un interceptor; no impacta
            return;
        }
        if (a.intentarImpactar()) {
            quitarDePendientes(a);
            stats.registrarImpactada(a.getZona().getCriticidad(), a.danioEfectivo());
            resueltasTotal++;
            System.out.printf("[Despachador t=%d] !!! IMPACTO %s  danio=%.1f%n",
                    tNow, a, a.danioEfectivo());
        }
    }

    // ------------------------------------------------------------------ //
    // Asignacion de interceptores                                          //
    // ------------------------------------------------------------------ //

    private void intentarDespachar() {
        if (estrategia == Estrategia.MLQ) {
            intentarDespacharMLQ();
        } else {
            intentarDespacharSimple();
        }
    }

    private void intentarDespacharSimple() {
        // Re-evaluar prioridades en tNow
        for (Amenaza a : pendientes) a.prioridad(tNow, estrategia);
        pendientes.sort(Comparator.comparingLong(Amenaza::getUltimoPrioridad).reversed());

        Iterator<Amenaza> it = pendientes.iterator();
        while (it.hasNext()) {
            Amenaza a = it.next();
            if (a.tiempoRestanteEn(tNow) < recargaMs) { it.remove(); continue; }
            if (!recursos.tryAcquire()) break; // TryWait(recursos)
            Interceptor ic = interceptorLibre();
            if (ic == null) { recursos.release(); break; }
            it.remove();
            a.intentarTomar();
            a.setTAsignacion(tNow);
            ic.asignar(a);
            agenda.add(new Evento(tNow + recargaMs, TipoEvento.FIN_SERVICIO, a, ic));
            System.out.printf("[Despachador t=%d] ASIGNA %s -> [%s]%n", tNow, a, ic.getName());
        }
    }

    private void intentarDespacharMLQ() {
        // Nivel ALTA: EDF (menor deadline primero)
        pendientesAlta.sort(Comparator.comparingLong(Amenaza::getInstanteImpacto));
        if (!despacharNivel(pendientesAlta)) return; // si hay alta con pendientes, no pasa a media

        // Nivel MEDIA: FCFS (orden de llegada)
        if (!despacharNivel(pendientesMedia)) return;

        // Nivel BAJA: FCFS circular
        despacharNivelCircular();
    }

    /**
     * Despacha de una lista hasta agotar recursos.
     * Devuelve false si la lista tiene pendientes pero no hay recursos libres.
     * Devuelve true si la lista quedo vacia o no tenia nada.
     */
    private boolean despacharNivel(List<Amenaza> lista) {
        Iterator<Amenaza> it = lista.iterator();
        while (it.hasNext()) {
            Amenaza a = it.next();
            if (a.tiempoRestanteEn(tNow) < recargaMs) { it.remove(); continue; }
            if (!recursos.tryAcquire()) return false; // aun hay pendientes, sin recursos
            Interceptor ic = interceptorLibre();
            if (ic == null) { recursos.release(); return false; }
            it.remove();
            a.intentarTomar();
            a.setTAsignacion(tNow);
            ic.asignar(a);
            agenda.add(new Evento(tNow + recargaMs, TipoEvento.FIN_SERVICIO, a, ic));
            System.out.printf("[Despachador t=%d] ASIGNA(MLQ) %s -> [%s]%n", tNow, a, ic.getName());
        }
        return true; // lista vacia
    }

    private void despacharNivelCircular() {
        if (pendientesBaja.isEmpty()) return;
        int n = pendientesBaja.size();
        for (int intentos = 0; intentos < n; intentos++) {
            if (pendientesBaja.isEmpty()) break;
            indiceRR = indiceRR % pendientesBaja.size();
            Amenaza a = pendientesBaja.get(indiceRR);
            if (a.tiempoRestanteEn(tNow) < recargaMs) {
                pendientesBaja.remove(indiceRR);
                n--; continue;
            }
            if (!recursos.tryAcquire()) break;
            Interceptor ic = interceptorLibre();
            if (ic == null) { recursos.release(); break; }
            pendientesBaja.remove(indiceRR);
            a.intentarTomar();
            a.setTAsignacion(tNow);
            ic.asignar(a);
            agenda.add(new Evento(tNow + recargaMs, TipoEvento.FIN_SERVICIO, a, ic));
            System.out.printf("[Despachador t=%d] ASIGNA(MLQ-BAJA) %s -> [%s]%n", tNow, a, ic.getName());
            indiceRR++;
        }
    }

    // ------------------------------------------------------------------ //
    // Utilitarios                                                          //
    // ------------------------------------------------------------------ //

    private Interceptor interceptorLibre() {
        for (Interceptor ic : interceptores) {
            if (ic.estaLibre()) return ic;
        }
        return null;
    }

    private void quitarDePendientes(Amenaza a) {
        pendientes.remove(a);
        pendientesAlta.remove(a);
        pendientesMedia.remove(a);
        pendientesBaja.remove(a);
    }
}
