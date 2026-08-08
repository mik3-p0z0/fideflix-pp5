package fideflix.excepciones;

/*
 * Excepcion personalizada que se lanza cuando se intenta operar
 * sobre un usuario que no existe en la coleccion.
 */

public class usuarioNoEncontradoException extends Exception  {

    /*
     * Constructor que recibe un mensaje descriptivo del error.
     * super(mensaje) pasa ese mensaje a la clase padre Exception,
     * que lo almacena internamente. Se recupera con getMessage().
     * @param mensaje
     */
    
    
    public usuarioNoEncontradoException(String mensaje) {
        super(mensaje); 
        
    }
    
    
}
