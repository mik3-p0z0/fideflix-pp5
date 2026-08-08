package fideflix.persistencia;

import fideflix.logica.Usuario;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;





public class PersistenciaUsuarios {
    
    // nombre del archivo donde se guarda la coleccion
    
    public static final String ARCHIVO = "usuarios.dat";
    
    private PersistenciaUsuarios(){
    
    }
    /*
     * Guarda la coleccion COMPLETA en el archivo, sobrescribiendo lo anterior.
     * se serializa el ArrayList entero de una vez.
     * try-with-resources: el ObjectOutputStream se cierra solo al salir del try,
     * incluso si ocurre una excepcion. Evita fugas de descriptores de archivo.
     */
    public static void guardar(ArrayList<Usuario> usuarios) throws IOException {
        try (ObjectOutputStream salida =
                     new ObjectOutputStream(new FileOutputStream(ARCHIVO))) {
            salida.writeObject(usuarios);
        }
        // Nota: writeObject de un ArrayList serializa tambien cada Usuario que
        // contiene, por eso Usuario DEBE implementar Serializable.
    }

    /*
     * Lee la coleccion desde el archivo.
     * Casos que maneja sin reventar la aplicacion:
     * - El archivo aun no existe (primera ejecucion) -> devuelve lista vacia.
     *  - El archivo existe pero esta vacio (EOFException) -> lista vacia.
     * El @SuppressWarnings("unchecked") es porque readObject() devuelve Object
     * y al castear a ArrayList<Usuario> el compilador no puede verificar el tipo
     * generico en tiempo de compilacion (borrado de tipos). Es un cast controlado:
     * sabemos que nosotros mismos guardamos exactamente ese tipo en guardar().
     */
    @SuppressWarnings("unchecked")
    public static ArrayList<Usuario> cargar() throws IOException, ClassNotFoundException {
        try (ObjectInputStream entrada =
                     new ObjectInputStream(new FileInputStream(ARCHIVO))) {
            return (ArrayList<Usuario>) entrada.readObject();
        } catch (FileNotFoundException | EOFException e) {
            // Primera ejecucion o archivo vacio: arrancamos con coleccion limpia.
            return new ArrayList<>();
        }
    }

    /*
     * Busca un usuario por email + contrasena (validacion de login).
     * Devuelve el Usuario si las credenciales coinciden, o null si no existe.
     * el email es el identificador unico (coincide con equals() de
     * Usuario). El login compara email y contrasena exactos.
     */
    public static Usuario autenticar(ArrayList<Usuario> usuarios,
                                     String email, String contrasena) {
        if (usuarios == null) {
            return null;
        }
        for (Usuario u : usuarios) {
            if (u.getEmail().equals(email) && u.getContrasena().equals(contrasena)) {
                return u;
            }
        }
        return null;
    }
    
    
}
