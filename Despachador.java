import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.Semaphore;

/*Hilo Despachador: planificador central.
 * Asigna amenazas a interceptores disponibles según la estrategia.
 * Controla recursos con un semáforo contador.
 * Avanza el reloj por eventos: llegada, fin de servicio, impacto.*/

public class Despachador extends Thread {

    private final ColaLlegadas colaLlegadas;
    private final Estadisticas stats;
    private final RelojVirtual reloj;
    private final int recarga;
    private final Estrategia estrategia;
    private final Interceptor[] interceptores;
    private final Semaphore recursos; // Semaphore(N)
    private final boolean verbose;

    private final List<Amenaza> pendientes = new ArrayList<>();
    private final PriorityQueue<Evento> agenda = new PriorityQueue<>(); // fin_servicio + impacto
    private long seq = 0;

    public Despachador(ColaLlegadas colaLlegadas, Estadisticas stats, RelojVirtual reloj, int recarga, Estrategia estrategia, Interceptor[] interceptores, Semaphore recursos, boolean verbose) {
        
        super("Despachador");
        this.colaLlegadas = colaLlegadas;
        this.stats = stats;
        this.reloj = reloj;
        this.recarga = recarga;
        this.estrategia = estrategia;
        this.interceptores = interceptores;
        this.recursos = recursos;
        this.verbose = verbose;
    }

    @Override
    public void run() {
        
        // Mezcla dos flujos ordenados en el tiempo: las llegadas y los eventos de
        // la agenda. proxLlegada es la siguiente llegada. Como el
        // Generador deposita todo al empezar, siguiente() casi no espera.
        Evento proxLlegada = colaLlegadas.siguiente();

        while (proxLlegada != null || !agenda.isEmpty()) {
            
            long tLleg = (proxLlegada != null) ? proxLlegada.tiempo : Long.MAX_VALUE;
            long tAgenda = !agenda.isEmpty() ? agenda.peek().tiempo : Long.MAX_VALUE;

            if (tLleg <= tAgenda) {
                reloj.avanzarA(tLleg);
                procesarLlegada(proxLlegada.amenaza);
                proxLlegada = colaLlegadas.siguiente();
            } else {
                Evento e = agenda.poll();
                reloj.avanzarA(e.tiempo);
                if (e.tipo == Evento.Tipo.FIN_SERVICIO) {
                    procesarFinServicio(e);
                } else {
                    procesarImpacto(e);
                }
            }

            intentarDespachar();
        }
    }

    private void procesarLlegada(Amenaza a) {
        pendientes.add(a);
        agenda.add(new Evento(Evento.Tipo.IMPACTO, a.getInstanteImpacto(), a, null, seq++));
        log("Llega " + a + " P=" + a.prioridad(reloj.ahora()));
    }

    // asigna interceptores libres a las pendientes, en el orden de la estrategia.
    // solo intercepta si la recarga termina antes del impacto.
    private void intentarDespachar() {
        long tNow = reloj.ahora();
        List<Amenaza> orden = estrategia.ordenar(pendientes, tNow);

        for (Amenaza a : orden) {
            int restante = a.tiempoRestanteEn(tNow);
            if (restante < recarga) {
                continue; // no llega a tiempo, se deja para que impacte
            }
            if (!recursos.tryAcquire()) {
                break; // no hay interceptor libre
            }
            pendientes.remove(a);
            a.setEstado(Estado.EN_PROCESO);
            a.setInstanteInicioServicio(tNow);
            
            Interceptor ic = interceptorLibre();
            ic.setOcupado(true);
            ic.asignar(a, tNow, tNow + recarga);
            agenda.add(new Evento(Evento.Tipo.FIN_SERVICIO, tNow + recarga, a, ic, seq++));
            
            long espera = tNow - a.getInstanteLlegada();
            log("Interceptor-" + ic.getNumero() + " ATIENDE " + a + "  (espero " + espera + "ms en la cola)");
        }
    }

    private void procesarFinServicio(Evento e) {
        Interceptor ic = e.interceptor;
        ic.anunciarFin();
        ic.esperarConfirmacion();
        ic.setOcupado(false);
        log("Interceptor-" + ic.getNumero() + " intercepta Amenaza#" + e.amenaza.getId());
    }

    private void procesarImpacto(Evento e) {
        Amenaza a = e.amenaza;
        if (a.getEstado() == Estado.PENDIENTE) {
            a.setEstado(Estado.IMPACTADA);
            pendientes.remove(a);
            stats.registrarImpactada(a.getZona().getCriticidad());
            log("Impacto de Amenaza#" + a.getId());
        }
    }

    private Interceptor interceptorLibre() {
        for (Interceptor in : interceptores) {
            if (!in.isOcupado()) {
                return in;
            }
        }
        return interceptores[0]; // si no encuentra, devuelve el primero
    }

    private void log(String msg) {
        if (verbose) {
            System.out.println("[t=" + reloj.ahora() + "] " + msg);
        }
    }
}
