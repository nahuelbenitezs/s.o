import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/* Estrategia 3: EDF (Earliest Deadline First), no apropiativo.
 * Atiende primero la amenaza con menor tiempo hasta el impacto, sin mirar la
 * criticidad. Le decimos EDF y no SRTN porque el tiempo de servicio (la recarga)
 * es igual para todas; lo que cambia es el plazo hasta el impacto (el deadline).*/
public class EstrategiaEDF implements Estrategia {

    @Override
    public String nombre() {
        return "EDF no apropiativo (menor tiempo hasta el impacto)";
    }

    @Override
    public List<Amenaza> ordenar(List<Amenaza> pendientes, long tNow) {
        List<Amenaza> orden = new ArrayList<>(pendientes);
        
        orden.sort(new Comparator<Amenaza>() {
            
            public int compare(Amenaza a, Amenaza b) {
                
                int cmp = Integer.compare(a.tiempoRestanteEn(tNow), b.tiempoRestanteEn(tNow));
                return (cmp != 0) ? cmp : Integer.compare(a.getId(), b.getId());
            }
        });
        return orden;
    }
}
