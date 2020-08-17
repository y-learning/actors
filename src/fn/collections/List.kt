package fn.collections

import fn.result.Result
import fn.result.map2
import fn.result.option.Option
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService

const val SET_HEAD_EMPTY = "setHead called on an empty list"
const val FIRST_EMPTY = "first called on an empty list"

fun inc(i: Int) = i + 1

fun <T> flatten(list: List<List<T>>): List<T> =
    list.foldLeft(List()) { list1 -> list1::concat }

fun <E> List<E>.concat(list: List<E>) = List.concatViaFoldLeft(this, list)

fun <E> toList(result: Result<E>): List<E> =
    result.map { List(it) }.getOrElse(List())

fun <E> flattenResult(list: List<Result<E>>): List<E> =
    list.flatMap(::toList)

fun <E> sequence(list: List<Result<E>>): Result<List<E>> =
    list.filter { !it.isEmpty() }.foldRight(Result(List()))
    { item: Result<E> ->
        { acc: Result<List<E>> ->
            map2(item, acc) { a: E -> { b: List<E> -> b.cons(a) } }
        }
    }

fun <E, U> traverse(list: List<E>, f: (E) -> Result<U>): Result<List<U>> =
    list.coFoldRight(Result(List())) { item: E ->
        { acc: Result<List<U>> ->
            map2(acc, f(item)) { a: List<U> ->
                { b: U -> a.cons(b) }
            }
        }
    }

fun <E> sequence2(list: List<Result<E>>): Result<List<E>> =
    traverse(list) { x: Result<E> -> x }

fun <E, T, U> zipWith(l1: List<E>, l2: List<T>, f: (E) -> (T) -> U): List<U> {

    tailrec fun zipWithIter(l1: List<E>, l2: List<T>, acc: List<U>): List<U> {
        if (l1.isEmpty() || l2.isEmpty()) return acc

        val zip = f(l1.first())(l2.first())

        return zipWithIter(l1.rest(), l2.rest(), acc.cons(zip))
    }

    return zipWithIter(l1, l2, List()).reverse2<U>()
}

fun <E, T, U> product(l1: List<E>, l2: List<T>, f: (E) -> (T) -> U): List<U> =
    l1.flatMap { e: E -> l2.map { t: T -> f(e)(t) } }

fun <T, U> unzip(list: List<Pair<T, U>>): Pair<List<T>, List<U>> =
    list.unzip { it }


fun <T, S> unfold(
    start: S,
    nextVal: (S) -> Option<Pair<@UnsafeVariance T, S>>): List<T> {
    tailrec fun unfold(acc: List<T>, start: S): List<T> =
        when (val next = nextVal(start)) {
            Option.None -> acc
            is Option.Some -> {
                val pair = next.value
                unfold(acc.cons(pair.first), pair.second)
            }
        }

    return unfold(List(), start).reverse()
}

fun <T, S> unfoldRec(
    s: S,
    f: (S) -> Option<Pair<@UnsafeVariance T, S>>): List<T> =
    f(s).map { pair: Pair<T, S> ->
        unfoldRec(pair.second, f).cons(pair.first)
    }.getOrElse { List.Nil }

fun <T, S> unfoldCoRec(
    s: S,
    nextVal: (S) -> Result<Pair<@UnsafeVariance T, S>>): Result<List<T>> {
    tailrec fun unfoldCoRecIter(acc: List<T>, s1: S): Result<List<T>> =
        when (val next = nextVal(s1)) {
            Result.Empty -> Result(acc)
            is Result.Failure -> Result.failure(next.exception)
            is Result.Success -> {
                val pair = next.value
                unfoldCoRecIter(acc.cons(pair.first), pair.second)
            }
        }

    return unfoldCoRecIter(List(), s).map(List<T>::reverse)
}

fun range(start: Int, end: Int): List<Int> = unfold(start) {
    if (it < end) Option(Pair(it, it + 1))
    else Option()
}

sealed class List<out E> {
    abstract val length: Int

    abstract fun isEmpty(): Boolean

    abstract fun length(): Int

    abstract fun lengthMemoized(): Int

    abstract fun setHead(x: @UnsafeVariance E): List<E>

    abstract fun first(): E

    abstract fun firstSafe(): Result<E>

    abstract fun rest(): List<E>

    abstract fun lastSafe(): Result<E>

    abstract fun getAt(index: Int): Result<E>

    abstract fun <U> foldLeft(
        identity: U,
        p: (U) -> Boolean,
        f: (U) -> (E) -> U): U

    abstract fun <U> foldLeft(identity: U, zero: U, f: (U) -> (E) -> U): U

    abstract fun splitAt(index: Int): Pair<List<E>, List<E>>

    abstract fun startWith(sub: List<@UnsafeVariance E>): Boolean

    abstract fun hasSublist(list: List<@UnsafeVariance E>): Boolean

    abstract fun <T> groupBy(f: (E) -> T): Map<T, List<E>>

    abstract fun splitListAt(index: Int): List<List<E>>

    abstract fun divide(depth: Int): List<List<E>>

    abstract fun forEach(ef: (E) -> Unit)

    fun cons(x: @UnsafeVariance E): List<E> = Cons(x, this)

    fun drop(n: Int): List<E> = drop(n, this)

    fun dropWhile(list: List<@UnsafeVariance E>, p: (E) -> Boolean): List<E> =
        Companion.dropWhile(list, p)

    fun reverse(): List<E> = reverse(invoke(), this)

    fun <U> reverse2(): List<E> = this.foldLeft(Nil as List<E>) { acc ->
        { acc.cons(it) }
    }

    fun init(): List<E> = reverse().drop(1).reverse()

    fun <U> foldRight(identity: U, f: (E) -> (U) -> U): U =
        foldRight(this, identity, f)

    fun <U> foldLeft(identity: U, f: (U) -> (E) -> U): U =
        foldLeft(this, identity, f)

    fun <U> foldRightViaFoldLeft(identity: U, f: (E) -> (U) -> U): U =
        this.foldLeft(identity, { x -> { y -> f(y)(x) } })

    fun <U> coFoldRight(identity: U, f: (E) -> (U) -> U): U =
        coFoldRight(this.reverse(), identity, f)

    fun <U> map(f: (E) -> U): List<U> =
        this.coFoldRight(Nil as List<U>) { e -> { it.cons(f(e)) } }

    fun filter(p: (E) -> Boolean): List<E> =
        this.foldLeft(Nil as List<E>) { acc ->
            { e ->
                if (p(e)) acc.cons(e)
                else acc
            }
        }.reverse()

    fun <U> flatMap(f: (E) -> List<U>): List<U> = flatten(map(f))

    fun filterViaFlatMap(p: (E) -> Boolean) = this.flatMap { e ->
        if (p(e)) List(e) else Nil
    }

    fun <E1, E2> unzip(f: (E) -> Pair<E1, E2>): Pair<List<E1>, List<E2>> =
        coFoldRight(Pair(Nil, Nil)) { e: E ->
            { acc: Pair<List<E1>, List<E2>> ->
                f(e).let {
                    Pair(
                        acc.first.cons(it.first),
                        acc.second.cons(it.second))
                }
            }
        }

    fun exists(p: (E) -> Boolean): Boolean =
        foldLeft(identity = false, zero = true) { { e: E -> p(e) } }

    fun forAll(p: (E) -> Boolean): Boolean = !exists { e: E -> !p(e) }

//    fun forAll(p: (E) -> Boolean): Boolean =
//        foldLeft(identity = true, zero = false) { acc: Boolean ->
//            { e: E -> acc && p(e) }
//        }

    fun <U> parFoldLeft(
        es: ExecutorService,
        identity: U,
        f: (U) -> (E) -> U,
        g: (U) -> (U) -> U): Result<U> =
        try {
            val result = divide(6)
                .map { list: List<E> ->
                    es.submit<U> { list.foldLeft(identity, f) }
                }.map<U> { future ->
                    try {
                        future.get()
                    } catch (e: InterruptedException) {
                        throw RuntimeException(e)
                    } catch (e: ExecutionException) {
                        throw RuntimeException(e)
                    }
                }
            Result(result.foldLeft(identity, g))
        } catch (e: Exception) {
            Result.failure(e)
        }

    fun <U> parMap(es: ExecutorService, g: (E) -> U): Result<List<U>> =
        try {
            val result = map { e: E ->
                es.submit<U> { g(e) }
            }.map<U> { future ->
                try {
                    future.get()
                } catch (e: InterruptedException) {
                    throw RuntimeException(e)
                } catch (e: ExecutionException) {
                    throw RuntimeException(e)
                }
            }

            Result(result)
        } catch (e: Exception) {
            Result.failure(e)
        }

    abstract class Empty<E> : List<E>() {
        override val length: Int get() = 0

        override fun isEmpty(): Boolean = true

        override fun setHead(x: E): List<E> = throw Exception(SET_HEAD_EMPTY)

        override fun length(): Int = 0

        override fun lengthMemoized(): Int = 0

        override fun first(): E = throw Exception(FIRST_EMPTY)

        override fun firstSafe(): Result<E> = Result()

        override fun rest(): List<E> = this

        override fun lastSafe(): Result<E> = Result()

        override fun getAt(index: Int): Result<E> =
            Result.failure("getAt called on an empty list.")

        override fun <U> foldLeft(
            identity: U,
            p: (U) -> Boolean,
            f: (U) -> (E) -> U): U = identity

        override fun <U> foldLeft(identity: U, zero: U, f: (U) -> (E) -> U): U =
            identity

        override fun splitAt(index: Int): Pair<List<E>, List<E>> =
            Pair(this, this)

        override fun startWith(sub: List<E>): Boolean = false

        override fun hasSublist(list: List<E>): Boolean = false

        override fun <T> groupBy(f: (E) -> T): Map<T, List<E>> = mapOf()

        override fun splitListAt(index: Int): List<List<E>> = List(this)

        override fun divide(depth: Int): List<List<E>> = Nil

        override fun forEach(ef: (E) -> Unit) {}

        override fun toString(): String = "[NIL]"
    }

    internal object Nil : Empty<Nothing>()

    internal class Cons<E>(private val head: E, private val tail: List<E>) :
        List<E>() {
        override val length: Int = tail.length + 1

        override fun isEmpty(): Boolean = false

        override fun length(): Int = foldRight(0) { { i: Int -> inc(i) } }

        override fun lengthMemoized(): Int = length

        override fun setHead(x: E): List<E> = this.tail.cons(x)

        private tailrec fun toString(acc: String, list: List<E>): String =
            if (list.isEmpty()) acc
            else toString("$acc${list.first()}, ", list.rest())

        override fun first(): E = this.head

        override fun firstSafe(): Result<E> = Result(head)

        override fun rest(): List<E> = this.tail

        override fun lastSafe(): Result<E> =
            foldLeft(Result()) { { item -> Result(item) } }

        override fun <U> foldLeft(
            identity: U,
            p: (U) -> Boolean,
            f: (U) -> (E) -> U): U {
            tailrec fun foldLeftIter(list: List<E>, acc: U): U = when (list) {
                is Cons ->
                    if (p(acc)) acc
                    else foldLeftIter(list.rest(), f(acc)(list.first()))
                else -> acc
            }

            return foldLeftIter(this, identity)
        }

        override fun <U> foldLeft(identity: U, zero: U, f: (U) -> (E) -> U): U {
            fun <U> foldLeft(
                acc: U,
                zero: U,
                list: List<E>,
                f: (U) -> (E) -> U): U =
                when (list) {
                    is Cons ->
                        if (acc == zero) acc
                        else foldLeft(f(acc)(list.head), zero, list.tail, f)
                    else -> acc
                }

            return foldLeft(identity, zero, this, f)
        }

        override fun getAt(index: Int): Result<E> =
            Pair(Result.failure<E>("Index out of bound"), index).let {
                if (index < 0 || index >= length()) it
                else
                    foldLeft<Pair<Result<E>, Int>>(it, { pair ->
                        pair.second < 0
                    }) { pair ->
                        { e: E -> Pair(Result(e), pair.second - 1) }
                    }
            }.first

        override fun splitAt(index: Int): Pair<List<E>, List<E>> {
            val identity = Triple<List<E>, List<E>, Int>(this, Nil, 0)

            if (index <= 0 || index >= length)
                return Pair(identity.first, identity.second)

            val fold = foldLeft<Triple<List<E>, List<E>, Int>>(identity,
                { triple -> triple.third == index }) { triple ->
                { e: E ->
                    val list = triple.first.rest()
                    val acc = triple.second.cons(e)

                    Triple(list, acc, inc(triple.third))
                }
            }

            return Pair(fold.second.reverse(), fold.first)
        }

        override fun startWith(sub: List<E>): Boolean {
            tailrec fun startWith(list: List<E>, sub: List<E>): Boolean = when {
                sub.isEmpty() -> true
                list.first() == sub.first() -> startWith(
                    list.rest(),
                    sub.rest())
                else -> false
            }

            return startWith(this, sub)
        }

        override fun hasSublist(list: List<E>): Boolean {
            if (list.length > length) return false

            tailrec fun hasSublistIter(list: List<E>, sub: List<E>): Boolean =
                when {
                    list.isEmpty() -> false
                    list.startWith(sub) -> true
                    else -> hasSublistIter(list.rest(), sub)
                }

            return hasSublistIter(this, list)
        }

        override fun <T> groupBy(f: (E) -> T): Map<T, List<E>> =
            reverse().foldLeft(mapOf()) { map: Map<T, List<E>> ->
                { e: E ->
                    f(e).let {
                        map + (it to map.getOrDefault(it, Nil).cons(e))
                    }
                }
            }

        override fun splitListAt(index: Int): List<List<E>> {
            if (index < 0 || index > length)
                return List(this)

            tailrec fun splitIter(acc: List<E>, list: List<E>, i: Int):
                    List<List<E>> =
                if (i == index)
                    List(acc.reverse(), list)
                else
                    splitIter(acc.cons(list.first()), list.rest(), inc(i))

            return splitIter(Nil, this, 0)
        }

        override fun divide(depth: Int): List<List<E>> {
            tailrec
            fun divideIter(acc: List<List<E>>, steps: Int): List<List<E>> =
                if (steps == depth || acc.first().length < 2) acc
                else divideIter(
                    acc.flatMap { it.splitListAt(it.length / 2) },
                    inc(steps))

            return divideIter(List(this), 0)
        }

        override fun forEach(ef: (E) -> Unit) = foldLeft(Unit) { ef }

        override fun toString(): String = "[${toString("", this)}NIL]"
    }

    companion object {
        operator
        fun <E> invoke(vararg az: E): List<E> =
            az.foldRight(Nil) { item: E, acc: List<E> ->
                Cons(item, acc)
            }

        internal tailrec fun <E> drop(i: Int, list: List<E>): List<E> =
            when {
                i <= 0 -> list
                list.isEmpty() -> list
                else -> drop(i - 1, list.rest())
            }

        private tailrec fun <E> dropWhile(list: List<E>, p: (E) -> Boolean):
                List<E> =
            when {
                list.isEmpty() -> list
                p(list.first()) -> dropWhile(list.rest(), p)
                else -> list
            }

        private tailrec fun <E> reverse(
            acc: List<E>,
            list: List<E>): List<E> =
            if (list.isEmpty()) acc
            else reverse(acc.cons(list.first()), list.rest())

        fun <T, U> foldRight(
            list: List<T>,
            identity: U,
            f: (T) -> (U) -> U): U =
            if (list.isEmpty()) identity
            else f(list.first())(foldRight(list.rest(), identity, f))

        tailrec fun <E, U> foldLeft(
            list: List<E>, acc: U,
            f: (U) -> (E) -> U): U =
            if (list.isEmpty()) acc
            else foldLeft(list.rest(), f(acc)(list.first()), f)

        tailrec fun <T, U> coFoldRight(
            list: List<T>,
            acc: U,
            f: (T) -> (U) -> U): U =
            if (list.isEmpty()) acc
            else coFoldRight(list.rest(), f(list.first())(acc), f)

        fun <T> concatViaFoldRight(
            list1: List<T>,
            list2: List<T>): List<T> =
            list1.foldRight(list2, { x -> { y -> y.cons(x) } })

        fun <T> concatViaFoldLeft(list1: List<T>, list2: List<T>): List<T> =
            list1.reverse().foldLeft(list2, { x -> x::cons })
    }
}