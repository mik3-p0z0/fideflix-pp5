package fideflix.logica;

/**
 * Representa una Serie en Fideflix.
 * Criterio de comparacion: titulo (alfabeticamente).
 */

public class Serie extends Audiovisual implements Comparable<Serie> {
    
    private int numTemporadas;
    private int numEpisodios;
    private String estado;

    public Serie(String titulo, String descripcion, int estreno, String clasificacion, double calificacion_IMDb, 
            String genero, int numTemporadas, int numEpisodios, String estado) {
        super(titulo, descripcion, estreno, clasificacion, calificacion_IMDb, genero);
        
        this.numTemporadas = numTemporadas;
        this.numEpisodios = numEpisodios;
        this.estado = estado;
        
    }
    
    public int getNumTemporadas() {
        return numTemporadas;
    }

    public void setNumTemporadas(int numTemporadas) {
        this.numTemporadas = numTemporadas;
    }

    public int getNumEpisodios() {
        return numEpisodios;
    }

    public void setNumEpisodios(int numEpisodios) {
        this.numEpisodios = numEpisodios;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    @Override
    public int compareTo(Serie otra) {
        return this.getTitulo().compareTo(otra.getTitulo());
    }
    
    

    @Override
    public String toString() {
        return "[SERIE] " + super.toString()
             + "\n | Temporadas: " + numTemporadas
             + "\n | Episodios: " + numEpisodios
             + "\n | Estado: " + estado;
    }
        
}
