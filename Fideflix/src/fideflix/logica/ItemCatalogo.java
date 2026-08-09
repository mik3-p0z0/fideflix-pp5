package fideflix.logica;

/*
 * Elemento de un catalogo: un par (id, nombre) de las tablas 'genero'
 * o 'clasificacion'.
 *
 * ─── POR QUE UN record Y NO UNA CLASE NORMAL ────────────────────────
 * Un record es un portador de datos INMUTABLE: Java genera solos el
 * constructor, los getters (id(), nombre()), equals(), hashCode() y
 * toString(). No lleva setters, y eso es una decision de diseno, no una
 * limitacion: un item de catalogo leido de la base no deberia poder
 * mutar en memoria y quedar desincronizado de la fila que representa.
 *
 * Compara con Usuario, que SI es una clase normal con setters: alli
 * tiene sentido, porque un usuario se edita. Elegir entre record y
 * clase es preguntarse "¿esto cambia despues de creado?".
 *
 * ─── POR QUE SE SOBREESCRIBE toString() ─────────────────────────────
 * Los JComboBox de Swing muestran el resultado de toString() de cada
 * elemento. Devolviendo solo el nombre, el combo se ve limpio mientras
 * el objeto conserva el id internamente. Asi, al guardar, se lee el id
 * directamente del item seleccionado: no hay que buscar el nombre en la
 * base para traducirlo, ni existe riesgo de ambiguedad.
 */
public record ItemCatalogo(int id, String nombre) {

    @Override
    public String toString() {
        return nombre;
    }
}
