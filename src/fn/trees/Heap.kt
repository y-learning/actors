package fn.trees

import fn.collections.List
import fn.result.Result
import fn.result.option.Option

sealed class Heap<out E> {
    internal abstract val head: Result<E>
    internal abstract val left: Result<Heap<E>>
    internal abstract val right: Result<Heap<E>>
    internal abstract val comparator: Result<Comparator<@UnsafeVariance E>>

    protected abstract val rank: Int

    abstract val size: Int
    abstract val isEmpty: Boolean

    abstract fun tail(): Result<Heap<E>>

    abstract fun get(index: Int): Result<E>

    abstract fun pop(): Option<Pair<E, Heap<E>>>

    operator fun plus(e: @UnsafeVariance E): Heap<E> =
        merge(this, Heap(e, comparator))

    fun <T, S, U> unfold(
        start: S,
        nextVal: (S) -> Option<Pair<@UnsafeVariance T, S>>,
        identity: U,
        f: (U) -> (T) -> U): U {
        tailrec
        fun unfold(acc: U, start: S): U = when (val next = nextVal(start)) {
            Option.None -> acc
            is Option.Some -> {
                val pair = next.value
                unfold(f(acc)(pair.first), pair.second)
            }
        }

        return unfold(identity, start)
    }

    fun <U> foldLeft(identity: U, f: (U) -> (E) -> U): U =
        unfold(this, { it.pop() }, identity, f)

    fun toList(): List<E> =
        foldLeft(List<E>()) { acc -> { e -> acc.cons(e) } }.reverse()

    class Empty<out E>(
        override
        val comparator: Result<Comparator<@UnsafeVariance E>> = Result()
    ) : Heap<E>() {
        override
        val head: Result<E> = Result.failure("head() called on empty heap")

        override val left: Result<Heap<E>> = Result(this)

        override val right: Result<Heap<E>> = Result(this)

        override val rank: Int = 0

        override val size: Int = 0

        override val isEmpty: Boolean = true


        override fun tail(): Result<Heap<E>> =
            Result.failure("tail() called on empty heap.")

        override fun get(index: Int): Result<E> =
            Result.failure(NoSuchElementException("Index out of bounds."))

        override fun pop(): Option<Pair<E, Heap<E>>> = Option()

        override fun toString(): String = "E"
    }

    internal class H<out E>(
        override val rank: Int,
        private val l: Heap<E>,
        private val h: E,
        private val r: Heap<E>,
        override val comparator: Result<Comparator<@UnsafeVariance E>> =
            l.comparator.orElse { r.comparator }) : Heap<E>() {

        override val head: Result<E> = Result(h)

        override val left: Result<Heap<E>> = Result(l)

        override val right: Result<Heap<E>> = Result(r)

        override val size: Int = 1 + l.size + r.size

        override val isEmpty: Boolean = false

        private fun mergeLeftRight() = merge(l, r)

        override fun tail(): Result<Heap<E>> = Result(mergeLeftRight())

        override fun get(index: Int): Result<E> = when (index) {
            0 -> Result(h)
            else -> tail().flatMap { it.get(index - 1) }
        }

        override
        fun pop(): Option<Pair<E, Heap<E>>> = Option(Pair(h, mergeLeftRight()))

        override fun toString(): String = "(T $l $h $r)"
    }

    companion object {
        operator fun <E : Comparable<E>> invoke(): Heap<E> = Empty()

        operator fun <E> invoke(comparator: Comparator<E>): Heap<E> =
            Empty(Result(comparator))

        operator fun <E> invoke(comparator: Result<Comparator<E>>): Heap<E> =
            Empty(comparator)

        operator
        fun <E> invoke(e: E, comparator: Result<Comparator<E>>): Heap<E> =
            H(1, Empty(comparator), e, Empty(comparator), comparator)

        operator
        fun <E> invoke(e: E, comparator: Comparator<E>): Heap<E> =
            H(
                1, Empty(Result(comparator)), e,
                Empty(Result(comparator)), Result(comparator))

        operator fun <E : Comparable<E>> invoke(e: E): Heap<E> =
            invoke(e, Comparator { o1: E, o2: E -> o1.compareTo(o2) })

        private
        fun <E> make(head: E, first: Heap<E>, second: Heap<E>): Heap<E> =
            first.comparator.orElse { second.comparator }.let {
                when {
                    first.rank >= second.rank ->
                        H(second.rank + 1, first, head, second, it)
                    else -> H(first.rank + 1, second, head, first, it)
                }
            }

        private fun <E> compare(
            head1: E, head2: E,
            comparator: Result<Comparator<E>>): Int =
            comparator.map { it.compare(head1, head2) }
                .getOrElse { (head1 as Comparable<E>).compareTo(head2) }


        fun <E> merge(
            heap1: Heap<E>, heap2: Heap<E>,
            comparator: Result<Comparator<E>> = heap1.comparator
                .orElse { heap2.comparator }): Heap<E> =
            heap1.head.flatMap { head1: E ->
                heap2.head.flatMap { head2: E ->
                    when {
                        compare(head1, head2, comparator) <= 0 -> {
                            heap1.left.flatMap { left1: Heap<E> ->
                                heap1.right.map { right1: Heap<E> ->
                                    make(head1, left1, merge(right1, heap2))
                                }
                            }
                        }

                        else -> {
                            heap2.left.flatMap { left2: Heap<E> ->
                                heap2.right.map { right2: Heap<E> ->
                                    make(head2, left2, merge(right2, heap1))
                                }
                            }
                        }
                    }
                }
            }.getOrElse(
                when (heap1) {
                    is Empty -> heap2
                    else -> heap1
                })
    }
}