package fideflix.logica;
import java.io.Serializable;

/*
 * Representa a un usuario registrado en Fideflix.
 * Implementa Comparable<Usuario> para poder ordenar colecciones de usuarios.
 * Criterio de ordenamiento: nombre (alfabeticamente)
 * Tambien sobreescribe equals() porque ArrayList.remove(Object) lo usa
 * internamente para saber si dos objetos son "el mismo". Sin equals(),
 * remove() solo funcionaria si le paso exactamente la misma referencia
 * de memoria, no un objeto con los mismos datos
Serializacion ---- Implementa serializable para poder escribir la coleccion de usuarios en un archivo .dat
con ObjectOutputStream y volver a leerla con ObjectInputStream.
*/



public class Usuario implements Comparable<Usuario>, Serializable {
    
    private static final long serialVersionUID = 1L;

    /* PP5: identificador asignado por MySQL (AUTO_INCREMENT).
     * Necesario para relacionar comentarios con su autor mediante la
     * llave foranea comentario.usuario_id. Vale 0 mientras el objeto
     * todavia no fue persistido. */
    private int id;

    private String nombre;
    private String email;
    private String contrasena;
    private String fechaRegistro;

    public Usuario(String nombre, String email, String contrasena, String fechaRegistro){
        this.nombre = nombre;
        this.email = email;
        this.contrasena = contrasena;
        this.fechaRegistro = fechaRegistro;
    
    }
    
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

 public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    public String getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(String fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }
    
    public static void iniciarSesion(Usuario usuario){
        
        System.out.println(" Bienvenido " + usuario.getNombre() + "!");
    
    
    }
    
    // ─── IMPLEMENTACION DE Comparable ────────────────────────────────────────
    // Ordenamos por nombre alfabeticamente.
    // Este metodo lo invoca Collections.sort() internamente al ordenar la lista.

    @Override
    public int compareTo(Usuario otro) {
        return this.nombre.compareTo(otro.getNombre());
    }
    
    // ─── SOBREESCRITURA DE equals() ───────────────────────────────────────────
    // equals() define cuando DOS objetos son considerados "iguales" en logica.
    // Por defecto (sin sobreescribir), Java compara REFERENCIAS de memoria:
    //   u1.equals(u2) -> true SOLO si u1 y u2 apuntan al mismo objeto en RAM.
    //
    // Nosotros necesitamos que dos Usuarios sean "iguales" si tienen el mismo EMAIL
    // (identificador unico del negocio), independiente de si son el mismo objeto.
    //
    // Esto habilita: lista.remove(u) y lista.contains(u) funcionen correctamente
    // aunque 'u' sea un objeto nuevo con los mismos datos.
    //
    // La firma es siempre: public boolean equals(Object o)  <- recibe Object, no Usuario
    
    @Override
    public boolean equals(Object o) {
        // Caso 1: si apuntan al mismo lugar en memoria, son iguales sin duda
        if (this == o) return true;

        // Caso 2: si el otro objeto es null o de otra clase, son distintos
        if (o == null || getClass() != o.getClass()) return false;

        // Ahora sabemos que 'o' es un Usuario. Hacemos el cast.
        Usuario otro = (Usuario) o;

        // Dos usuarios son iguales si tienen el mismo email (identificador unico)
        return this.email.equals(otro.email);
    }

    // ─── hashCode() ───────────────────────────────────────────────────────────
    // Regla de Java: si sobreescribes equals(), SIEMPRE sobreescribe hashCode().
    // Objetos iguales segun equals() DEBEN tener el mismo hashCode.
    // Esto es critico para que HashMap, HashSet y otras estructuras funcionen bien.
    // Usamos el hashCode del email porque es nuestro criterio de igualdad.
    
    @Override
    public int hashCode() {
        return email.hashCode();
    }
    
     @Override
    public String toString() {
        return "Usuario: " + nombre 
                + " | Email: " + email
                + " | Registrado: " + fechaRegistro;
    }
    
    
    
}
