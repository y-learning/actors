package fn.result.option

import fn.collections.List

sealed class Option<out T> {
    abstract fun isEmpty(): Boolean

    abstract fun <U> map(f: (T) -> U): Option<U>

    abstract fun <U> flatMap(f: (T) -> Option<U>): Option<U>

    fun getOrElse(default: () -> @UnsafeVariance T): T =
        when (this) {
            None -> default()
            is Some -> this.value
        }

    fun orElse(default: () -> Option<@UnsafeVariance T>): Option<T> =
        map { this }.getOrElse(default)

    fun filter(p: (T) -> Boolean): Option<T> =
        flatMap { x -> if (p(x)) this else None }

    object None : Option<Nothing>() {
        override fun isEmpty(): Boolean = true

        override fun <U> map(f: (Nothing) -> U): Option<U> =
            None

        override fun <U> flatMap(f: (Nothing) -> Option<U>): Option<U> =
            None

        override fun toString(): String = "None"

        override fun equals(other: Any?): Boolean = other === None

        override fun hashCode(): Int = 0
    }

    internal data class Some<T>(internal val value: T) : Option<T>() {
        override fun isEmpty(): Boolean = false

        override fun <U> map(f: (T) -> U): Option<U> =
            Some(f(value))

        override fun <U> flatMap(f: (T) -> Option<U>): Option<U> =
            map(f).getOrElse { None }
    }

    companion object {
        operator fun <T> invoke(): Option<T> =
            None
        operator fun <T> invoke(t: T? = null): Option<T> =
            when (t) {
                null -> None
                else -> Some(t)
            }
    }
}

fun <K, V> Map<K, V>.getOption(key: K) =
    Option(this[key])

fun <A, B> lift(f: (A) -> B): (Option<A>) -> Option<B> = {
    try {
        it.map(f)
    } catch (e: Exception) {
        Option()
    }
}

fun <A, B> hLift(f: (A) -> B): (A) -> Option<B> = {
    try {
        Option(it).map(f)
    } catch (e: Exception) {
        Option()
    }
}

fun <A, B, C> map2(
    oa: Option<A>,
    ob: Option<B>,
    f: (A) -> (B) -> C
): Option<C> = oa.flatMap { a -> ob.map { f(a)(it) } }

fun <A> sequence(list: List<Option<A>>): Option<List<A>> =
    list.foldRight(Option(List())) { e: Option<A> ->
        { y: Option<List<A>> ->
            map2(e, y) { a: A ->
                { b: List<A> -> b.cons(a) }
            }
        }
    }

fun <A> sequence2(list: List<Option<A>>): Option<List<A>> {
    return if (list.isEmpty()) Option(List())
    else list.first().flatMap { x: A ->
        sequence2(list.rest()).map { it.cons(x) }
    }
}

fun <A, B> trverse(list: List<A>, f: (A) -> Option<B>): Option<List<B>> =
    list.foldRight(Option(List())) { a: A ->
        { optionListB: Option<List<B>> ->
            map2(f(a), optionListB) { b ->
                { listB: List<B> -> listB.cons(b) }
            }
        }
    }

fun <A> sequence3(list: List<Option<A>>): Option<List<A>> =
    trverse(list) { it }