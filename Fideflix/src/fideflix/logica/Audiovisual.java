package fideflix.logica;

/*
 * CLASE BASE de la jerarquia de contenido (Pelicula, Documental, Serie).
 *
 * CAMBIOS DE LA PP5 respecto a la PP4:
 *
 * 1. Se agrega el atributo 'id'. Sin un identificador estable no existe
 *    UPDATE ... WHERE id = ? ni DELETE: hasta ahora los objetos se
 *    distinguian por titulo, pero el titulo es justamente uno de los
 *    campos que el usuario puede editar. Una llave no puede ser un dato
 *    que cambia. El id lo genera la base de datos (AUTO_INCREMENT) y
 *    vale 0 mientras el objeto todavia no fue persistido.
 *
 * 2. Se ELIMINA el ArrayList<String> comentarios que era 'static'.
 *    Era un bug real: al ser estatico, la lista pertenecia a la CLASE y
 *    no a cada objeto, de modo que todos los audiovisuales compartian
 *    los mismos comentarios. Un comentario sobre "Interstellar" aparecia
 *    tambien en "Breaking Bad".
 *    Ahora los comentarios viven en la tabla 'comentario', atados por
 *    llave foranea a UNA obra concreta, y se consultan por demanda con
 *    ComentarioDAO. El modelo relacional hace ese error imposible.
 */
public abstract  class Audiovisual {

    /* Identificador asignado por la base de datos.
     * 0 = objeto nuevo, todavia no insertado. */
    private int id;

    private String titulo;
    private String descripcion;
    private int estreno;
    private String clasificacion;
    private double calificacion_IMDb;
    private String genero;

    public Audiovisual(String titulo, String descripcion, int estreno, String clasificacion, double calificacion_IMDb, String genero){

        this.titulo = titulo;
        this.descripcion = descripcion;
        this.estreno = estreno;
        this.clasificacion = clasificacion;
        this.calificacion_IMDb = calificacion_IMDb;
        this.genero = genero;
    }
    
    // ─── Identificador de base de datos ──────────────────────────────
    public int getId(){
        return id;
    }

    public void setId(int id){
        this.id = id;
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
