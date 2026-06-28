import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/*
 * Estrategia 1: prioridades por criticidad y urgencia, con envejecimiento.
 *
 *   puntaje = criticidad*10000 + (5000 - tiempoRestante) + (tNow - llegada)
 *
 * El factor 10000 hace que la criticidad pese mas que la urgencia. El ultimo
 * termino (envejecimiento) sube de a poco a las que esperan, para reducir la
 * inanicion de las zonas de baja criticidad.
 */
public class PrioridadCombinada implements Estrategia {

    private static final int K = 10000;
    private static final int TMAX = 5000;
    private static final int ENVEJ = 1;

    private long puntaje(Amenaza a, long tNow) {
        long criticidad = (long) a.getZona().getCriticidad() * K;
        long urgencia = TMAX - a.tiempoRestanteEn(tNow);
        long envejecimiento = (long) ENVEJ * (tNow - a.getInstanteLlegada());
        return criticidad + urgencia + envejecimiento;
    }

    @Override
    public String nombre() {
        return "Prioridades (Event-Driven) con envejecimiento";
    }

    @Override
    public List<Amenaza> ordenar(List<Amenaza> pendientes, long tNow) {
        List<Amenaza> orden = new ArrayList<>(pendientes);
        orden.sort(new Comparator<Amenaza>() {
            @Override
            public int compare(Amenaza a, Amenaza b) {
                int cmp = Long.compare(puntaje(b, tNow), puntaje(a, tNow)); // mayor primero
                if (cmp != 0) return cmp;
                return Integer.compare(a.getId(), b.getId());
            }
        });
        return orden;
    }
}
