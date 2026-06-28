import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/*
 * Estrategia 2: MLQ, tres colas por nivel de criticidad, cada una con su algoritmo:
 *   - ALTA  (hospital, central): EDF (menor tiempo hasta el impacto)
 *   - MEDIA (datacenter):        FCFS (orden de llegada)
 *   - BAJA  (residencial, industrial): rotacion circular entre las pendientes
 *
 * Se atiende siempre el nivel mas alto que tenga pendientes y recien cuando se
 * vacia se pasa al siguiente. La rotacion de la cola baja reparte la atencion;
 * como el servicio no es apropiativo y no hay quantum, no es un Round Robin clasico.
 */
public class ColasMultinivel implements Estrategia {

    private int rrBaja = 0; // puntero de rotacion para el nivel bajo

    private int nivel(Amenaza a) {
        int c = a.getZona().getCriticidad();
        if (c >= 8) return 0;   // ALTA
        if (c >= 6) return 1;   // MEDIA
        return 2;               // BAJA
    }

    @Override
    public String nombre() {
        return "MLQ - 3 colas (ALTA=EDF, MEDIA=FCFS, BAJA=rotacion)";
    }

    @Override
    public List<Amenaza> ordenar(List<Amenaza> pendientes, long tNow) {
        List<Amenaza> alta = new ArrayList<>();
        List<Amenaza> media = new ArrayList<>();
        List<Amenaza> baja = new ArrayList<>();
        for (Amenaza a : pendientes) {
            int n = nivel(a);
            if (n == 0) alta.add(a);
            else if (n == 1) media.add(a);
            else baja.add(a);
        }

        // ALTA: menor tiempo hasta el impacto primero
        alta.sort(new Comparator<Amenaza>() {
            public int compare(Amenaza a, Amenaza b) {
                int cmp = Integer.compare(a.tiempoRestanteEn(tNow), b.tiempoRestanteEn(tNow));
                return (cmp != 0) ? cmp : Integer.compare(a.getId(), b.getId());
            }
        });

        // MEDIA: orden de llegada
        media.sort(new Comparator<Amenaza>() {
            public int compare(Amenaza a, Amenaza b) {
                int cmp = Long.compare(a.getInstanteLlegada(), b.getInstanteLlegada());
                return (cmp != 0) ? cmp : Integer.compare(a.getId(), b.getId());
            }
        });

        // BAJA: orden de llegada pero rotando el inicio en cada decision
        baja.sort(new Comparator<Amenaza>() {
            public int compare(Amenaza a, Amenaza b) {
                int cmp = Long.compare(a.getInstanteLlegada(), b.getInstanteLlegada());
                return (cmp != 0) ? cmp : Integer.compare(a.getId(), b.getId());
            }
        });
        baja = rotar(baja, rrBaja);
        if (!baja.isEmpty()) rrBaja = (rrBaja + 1) % baja.size();

        List<Amenaza> orden = new ArrayList<>();
        orden.addAll(alta);
        orden.addAll(media);
        orden.addAll(baja);
        return orden;
    }

    // rota la lista para que arranque en el indice 'desde'
    private List<Amenaza> rotar(List<Amenaza> lista, int desde) {
        if (lista.isEmpty()) return lista;
        int n = lista.size();
        int inicio = desde % n;
        List<Amenaza> rotada = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rotada.add(lista.get((inicio + i) % n));
        }
        return rotada;
    }
}
