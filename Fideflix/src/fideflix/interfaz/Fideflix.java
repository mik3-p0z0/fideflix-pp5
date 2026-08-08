package fideflix.interfaz;

import fideflix.logica.Documental;
import fideflix.logica.Pelicula;
import fideflix.logica.Serie;
import fideflix.logica.Usuario;
import fideflix.excepciones.usuarioNoEncontradoException;
import fideflix.logica.Audiovisual;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;


public class Fideflix {

    // METODO AUXILIAR: eliminarUsuario
    
    public static void eliminarUsuario(ArrayList<Usuario> lista, String email)
            throws usuarioNoEncontradoException{
        
        Usuario buscador = new Usuario("", email, "","");
        boolean eliminado = lista.remove(buscador);
        if (!eliminado){
            throw new usuarioNoEncontradoException("No se encontró al usuario con el email: " + email);
        
        }
    
        System.out.println(" --> Usuario '"  + email + "' eliminado exitosamente.");
    }
 
      // MAIN — Punto de entrada del programa
    
    public static void main(String[] args) {
        
        //clases, objetos y herencia
        
        System.out.println("==== FIDEFLIX PREMIUM ====\n ");
        
        Pelicula pelicula1 = new Pelicula(
                "Hereditary",
                "Tras el fallecimiento de la abuela, la familia en duelo comienza a verse acosada por inquietantes "
                        + "y tragicos sucesos y empieza a desvelar oscuros secretos.",
                2018,
                "R",
                7.3,
                "Terror psicológico",
                127,
                "Ari Aster",
                "A24"
                 );
        
        Pelicula pelicula2 = new Pelicula(
                "Interstellar",
                "Un equipo de exploradores viaja a través de un agujero de gusano en "
                        + "el espacio en un intento de garantizar la supervicencia de la humanidad. ",
                2014,
                "PG-13",
                8.7,
                "Ciencia ficción espacial",
                169,
                "Christopher Nolan",
                "Warner Bros"
        );
        
        Serie serie1 = new Serie(
                "Mr Robot",
                "Elliot, un joven brillante pero altamente inestable, ingeniero de seguridad cibernética y pirata informatico, "
                        + "se convierte en la figura clave en un complejo juego de dominio global. ",
                2015,
                "TV-MA",
                8.5,
                "Drama psicológico",
                4,
                45,
                "Finalizada"
           
        );
        
        Documental documental1 = new Documental(
            "The Social Dilemma",
            "El impacto de las redes sociales en la sociedad.",
            2020,
            "PG-13",
            7.6,
            "Documental",
            94,
            "Jeff Orlowski",
            "Tecnologia y sociedad"
        );
        
        Usuario usuario1 = new Usuario(
                "Michael Pozo",
                "Michael@gmail.com",
                "SecretPassword123",
                "21-05-2026"
        );
        
        Usuario.iniciarSesion(usuario1);
        
        System.out.println("\n--- Catalogo disponible en Fideflix ---\n");
        System.out.println(pelicula1);
        System.out.println(pelicula2);
        System.out.println(serie1);
        System.out.println(documental1);

        System.out.println("\n--- Informacion del usuario ---\n");
        System.out.println(usuario1);
       
        
        
        // Demostracion que los metodos heredados funcionan en las clases hijas.
        
        System.out.println("\n--- Demostracion de herencia ---\n");
        System.out.println("Titulos de las peliculas (getter heredado): " 
                           + pelicula2.getTitulo()
                           +" y "
                           + pelicula1.getTitulo());
        System.out.println("Calificación de la serie (getter heredado): " 
                           + serie1.getCalificacion_IMDb());
        
        // Uso de setter heredado: cambiando la calificacion del documental
        System.out.println("Establesca su calificacion del documental");
        documental1.setCalificacion_IMDb(8.0);
        System.out.println("Nueva calificación del documental: " 
                           + documental1.getCalificacion_IMDb());
        
        
        // Polimorfismo, excepciones y colecciones
        
        System.out.println("\n [1]==========Coleccion de usuarios: ===========");
        
        ArrayList<Usuario> usuarios = new ArrayList<>();
        
        usuarios.add(usuario1);
        usuarios.add(new Usuario("Juliana Barquero",  "juliana@gmail.com",    "pass9",  "2025-06-18"));
        usuarios.add(new Usuario("Gerardo Vega",  "gerardo@gmail.com",  "pass6",  "2025-06-18"));
        usuarios.add(new Usuario("Eduardo Ruiz",  "eduardo@gmail.com",  "pass4",  "2025-04-05"));
        usuarios.add(new Usuario("Ana Garcia",    "ana@gmail.com",      "pass1",  "2025-01-10"));
        usuarios.add(new Usuario("Fernanda Cruz", "fernanda@gmail.com", "pass5",  "2025-05-12"));
        usuarios.add(new Usuario("Helena Soto",   "helena@gmail.com",   "pass7",  "2025-07-22"));
        usuarios.add(new Usuario("Ivan Torres",   "ivan@gmail.com",     "pass8",  "2025-08-30"));
        usuarios.add(new Usuario("Carlos Mora",   "carlos@gmail.com",   "pass2",  "2025-02-15"));
        usuarios.add(new Usuario("Diana Lopez",   "diana@gmail.com",    "pass3",  "2025-03-20"));
        
        
        System.out.println(" Lista inicial (" + usuarios.size() + " usuarios):");
        for (Usuario u : usuarios){
            System.out.println("     " + u);
        
        }
        
        System.out.println(" \n [2]  Lista ordenada alfabeticamente:");
        Collections.sort(usuarios);
        for (Usuario u : usuarios){
            System.out.println("    " + u);
        
        }
        
        System.out.println("\n  [3] Eliminando usuario existente Carlos Mora:");
        try {
            eliminarUsuario(usuarios, "carlos@gmail.com");
        } catch (usuarioNoEncontradoException e) {
            System.out.println("  Error: " + e.getMessage());
        }
        System.out.println("  Usuarios restantes: " + usuarios.size());
 
        // Excepcion personalizada (usuario inexistente)
        
        // CASO DE ERROR: el email NO existe. El metodo lanza la excepcion.
        // Sin el try/catch, el programa terminaria abruptamente (crash).
        // Con el catch, capturamos el error y continuamos.
        
        System.out.println("\n  [4] Intentando eliminar usuario que NO existe: Julia@gmail.com ");
        try {
            eliminarUsuario(usuarios, "Julia@gmail.com");
        } catch (usuarioNoEncontradoException e) {
            // getMessage() retorna el String que pasamos al constructor
            // de UsuarioNoEncontradoException cuando la lanzamos.
            System.out.println("  Error: " + e.getMessage());
        }
        
        //Metodo estatico en Audiovisual
        System.out.println("\n  [5] Comentarios ");
        Audiovisual.agregarComentario("Interstellar cambia tu perspectiva del universo");
        Audiovisual.agregarComentario("Hereditary es puro terror moderno");
        Audiovisual.agregarComentario("Mr Robot es la mejor serie de todos los tiempos para los informaticos");
 
        System.out.println("  Total comentarios: " + Audiovisual.getComentarios().size());
        for (String c : Audiovisual.getComentarios()) {
            System.out.println("    > " + c);
        }
        
        //Polimorfismo - varios tipos de objetos tratados como Audiovisual 
         System.out.println("\n  [7]  Catalogo de Audiovisual ordenado por calificación:");
 
        ArrayList<Audiovisual> catalogo = new ArrayList<>();
        catalogo.add(pelicula1); // Pelicula   
        catalogo.add(pelicula2);
        catalogo.add(documental1);         //Documental
        catalogo.add(serie1);       // Serie      
        
        catalogo.sort(Comparator.comparingDouble(Audiovisual::getCalificacion_IMDb).reversed());
        for (Audiovisual av : catalogo) {
            System.out.println("    [" + av.getCalificacion_IMDb() + "] " + av.getTitulo());
        }
        
        System.out.println("\n  [8] Destruccion de objetos:");
 
        // Vaciamos las colecciones — los objetos dentro quedan sin referencia
        usuarios.clear();
        catalogo.clear();
 
        // Anulamos las referencias a las listas
        usuarios = null;
        catalogo = null;
 
        // Anulamos las referencias a los objetos individuales
        pelicula1 = null;
        pelicula2 = null;
        documental1      = null;
        serie1    = null;
        usuario1  = null;
 
        // Sugerencia al GC. La JVM decide si y cuando actua.
        System.gc();
 
        System.out.println("  Todas las referencias liberadas.");
        
        
    }
        
        
        
    }
    
