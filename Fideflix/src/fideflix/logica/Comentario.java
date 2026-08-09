package fideflix.logica;

/*
 * Comentario de un usuario sobre una obra audiovisual.
 *
 * Reemplaza al ArrayList<String> 'static' que vivia en Audiovisual y que
 * era compartido por TODAS las obras. Aqui cada comentario declara
 * explicitamente a que audiovisual pertenece y quien lo escribio: la
 * relacion existe en el modelo, no en la memoria de una sola clase.
 *
 * 'autor' es el NOMBRE del usuario, no su id: este objeto se construye
 * para MOSTRARSE, y viene de una consulta con JOIN a la tabla usuario.
 * Guardar el id aqui obligaria a la ventana a hacer una segunda consulta
 * solo para saber como se llama la persona.
 *
 * 'fechaHora' se recibe como String ya formateado por el DAO. El
 * proposito de este objeto es viajar hacia la interfaz, no hacer
 * aritmetica de fechas.
 */
public record Comentario(int id, int audiovisualId, String autor,
                         String texto, String fechaHora) {

    @Override
    public String toString() {
        return "[" + fechaHora + "] " + autor + ": " + texto;
    }
}
