package fideflix.logica;

import java.util.ArrayList;
        
public abstract  class Audiovisual {
    
    private String titulo;
    private String descripcion;
    private int estreno;
    private String clasificacion;
    private double calificacion_IMDb;
    private String genero;
    //Nuevo atributo para almacenar comentarios 
    private static ArrayList<String> comentarios = new ArrayList<>();
    
    public Audiovisual(String titulo, String descripcion, int estreno, String clasificacion, double calificacion_IMDb, String genero){
        
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.estreno = estreno;
        this.clasificacion = clasificacion;
        this.calificacion_IMDb = calificacion_IMDb;
        this.genero = genero;
    }
    
// ─── METODO ESTATICO: agregarComentario 
    // Metodo estatico --> pertenece a la clase, no al objeto.
    // Se llama  en el main como --> Audiovisual.agregarComentario
    // NO necesita instancia para funcionar.
    // Puede acceder a 'comentarios' directamente porque tambien es static.
public static void agregarComentario(String comentario) {
        comentarios.add(comentario);
        System.out.println("Comentario agregado: \"" + comentario + "\"");
    }

//Getter de la lista de comentarios tambien estatico 
public static ArrayList<String> getComentarios() {
    return comentarios;
}
 
    public String getTitulo(){
        return titulo;
    }
    
    public void setTitulo(String titulo){
        this.titulo = titulo;
    }
    
    public String getDescripcion(){
        return descripcion;
    }
    
    public void setDescripcion(String descripcion){
        this.descripcion = descripcion;
    }
    
    public int getEstreno(){
        return estreno;
    }
    
    public void setEstreno(int estreno){
        this.estreno = estreno;
    }
    
    public String getClasificacion(){
        return clasificacion;
    }
    
    public void setClasificacion(String clasificacion){
        this.clasificacion = clasificacion;
    }
    
    public double getCalificacion_IMDb(){
        return calificacion_IMDb;
    }
    
    public void setCalificacion_IMDb(double calificacion_IMDb){
        this.calificacion_IMDb = calificacion_IMDb;
    }
    
    public String getGenero(){
        return genero;
    }
    
    public void setGenero(String genero){
        this.genero = genero;
    }
    
    @Override
    public String toString() {
        return "Titulo: " + titulo
             +"\n | Descripción: " + descripcion 
             + "\n | Estreno: " + estreno
             + "\n | Genero: " + genero
             + "\n | Calificación en IMDb: " + calificacion_IMDb
             + "\n | Clasificación: " + clasificacion;
    }

    
}
