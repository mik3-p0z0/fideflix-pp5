package fideflix.logica;
/*
 * Representa una Pelicula en Fideflix.
 * EXTIENDE Audiovisual (herencia).
 * IMPLEMENTA Comparable<Pelicula> (contrato de comparacion).
 *
 * Al implementar Comparable<Pelicula>, esta clase se compromete a
 * definir el metodo compareTo(Pelicula otra), lo que permite:
 *   - Collections.sort(listaDePeliculas)   <- ordenar
 *   - Collections.min / max                <- extremos
 *   - Uso en TreeSet / TreeMap             <- estructuras ordenadas
 */

public class Pelicula extends Audiovisual implements Comparable<Pelicula>{
    
    private int duracion;
    private String director;
    private String estudio;
    
    public Pelicula(String titulo, String descripcion, int estreno, String clasificacion, double calificacion_IMDb, 
            String genero, int duracion, String director, String estudio){
    
        super(titulo, descripcion, estreno, clasificacion, calificacion_IMDb, genero);
        this.duracion = duracion;
        this.director = director;
        this.estudio = estudio;
    }
    
    public int getDuracion() {
        return duracion;
    }

    public void setDuracion(int duracion) {
        this.duracion = duracion;
    }

    public String getDirector() {
        return director;
    }

    public void setDirector(String director) {
        this.director = director;
    }

    public String getEstudio() {
        return estudio;
    }

    public void setEstudio(String estudio) {
        this.estudio = estudio;
    }

    
    // ─── IMPLEMENTACION DE Comparable
    // Criterio elegido: ordenar por titulo alfabeticamente.
    // getTitulo() es heredado de Audiovisual.
    // String.compareTo() ya implementa comparacion alfabetica internamente.
    // Devolvemos directamente su resultado porque cumple la convencion:
    //   negativo -> this va antes  | 0 -> iguales | positivo -> this va despues
    
    @Override
    public int compareTo(Pelicula otra) {
        return this.getTitulo().compareTo(otra.getTitulo());
    }
    
    
    @Override
    public String toString() {
        return "[PELICULA] " + super.toString()
             + "\n | Director: " + director
             + "\n | Duración: " + duracion + " min"
             + "\n | Estudio: " + estudio;
    }
    
    
}
