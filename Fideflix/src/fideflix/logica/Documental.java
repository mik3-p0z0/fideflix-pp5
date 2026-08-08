package fideflix.logica;
/*
 * Representa un Documental en Fideflix.
 * El criterio de comparacion elegido es el titulo (alfabeticamente).
 */

public class Documental extends Audiovisual  implements Comparable<Documental>{
    
    private int duracion;
    private String director;
    private String tema;
    
    public Documental(String titulo, String descripcion, int estreno, String clasificacion, double calificacion_IMDb, 
            String genero, int duracion, String director, String tema) {
        
        super(titulo, descripcion, estreno, clasificacion, calificacion_IMDb, genero);
        
        this.duracion = duracion;
        this.director =director;
        this.tema = tema;
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

    public String getTema() {
        return tema;
    }

    public void setTema(String tema) {
        this.tema = tema;
    }

    @Override
    public int compareTo(Documental otro) {
        return this.getTitulo().compareTo(otro.getTitulo());
    }
    
    

    @Override
    public String toString() {
        return "[DOCUMENTAL] " + super.toString()
             + "\n | Director: " + director
             + "\n | Tema: " + tema
             + "\n | Duración: " + duracion + " min";
    }
    
    
    
    
}
