package fn.result

import fn.result.Result.Empty
import java.io.Serializable

sealed class Result<out A> : Serializable {
    abstract fun <B> map(f: (A) -> B): Result<B>

    abstract fun <B> flatMap(f: (A) -> Result<B>): Result<B>

    abstract fun mapFailure(errMsg: String): Result<A>

    abstract fun mapEmpty(errMsg: String): Result<A>

    abstract fun mapEmptyToSuccess(): Result<Any>

    abstract fun <E : RuntimeException> mapFailure(
        msg: String,
        f: (e: RuntimeException) -> (msg: String) -> E): Result<A>

    abstract fun forEach(
        onSuccess: (A) -> Unit = {},
        onFailure: (RuntimeException) -> Unit = {},
        onEmpty: () -> Unit = {})

    abstract fun isEmpty(): Boolean

    fun getOrElse(defaultValue: @UnsafeVariance A): A = when (this) {
        is Success -> this.value
        else -> defaultValue
    }

    fun getOrElse(defaultValue: () -> @UnsafeVariance A): A = when (this) {
        is Success -> this.value
        else -> defaultValue()
    }

    fun orElse(defaultValue: () -> Result<@UnsafeVariance A>): Result<A> =
        when (this) {
            is Success -> this
            else -> try {
                defaultValue()
            } catch (e: RuntimeException) {
                Failure<A>(e)
            } catch (e: Exception) {
                Failure<A>(RuntimeException(e))
            }
        }

    fun filter(message: String, p: (A) -> Boolean): Result<A> = flatMap {
        if (p(it)) this
        else failure(message)
    }

    fun filter(p: (A) -> Boolean): Result<A> =
        filter("Condition not matched.", p)

    fun exists(p: (A) -> Boolean): Boolean = map(p).getOrElse(false)

    internal
    object Empty : Result<Nothing>() {
        override fun <B> map(f: (Nothing) -> B): Result<B> = Empty

        override fun <B> flatMap(f: (Nothing) -> Result<B>): Result<B> = Empty

        override fun mapFailure(errMsg: String): Result<Nothing> = this

        override fun <E : RuntimeException> mapFailure(
            msg: String,
            f: (e: RuntimeException) -> (msg: String) -> E):
                Result<Nothing> = this

        override fun mapEmpty(errMsg: String): Result<Nothing> =
            Failure(RuntimeException(errMsg))

        override fun mapEmptyToSuccess(): Result<Any> = Result(Any())

        override fun forEach(
            onSuccess: (Nothing) -> Unit,
            onFailure: (RuntimeException) -> Unit,
            onEmpty: () -> Unit) = onEmpty()

        override fun isEmpty(): Boolean = true

        override fun toString(): String = "Empty"
    }

    internal
    data class Failure<out A>(internal val exception: RuntimeException) :
        Result<A>() {

        override fun <B> map(f: (A) -> B): Result<B> =
            Failure(exception)

        override fun <B> flatMap(f: (A) -> Result<B>): Result<B> =
            Failure(exception)

        override fun mapFailure(errMsg: String): Result<A> =
            Failure(RuntimeException(errMsg, exception))

        override fun <E : RuntimeException> mapFailure(
            msg: String,
            f: (e: RuntimeException) -> (msg: String) -> E): Result<A> =
            Failure(f(exception)(msg))

        override fun mapEmpty(errMsg: String): Result<A> = this

        override fun mapEmptyToSuccess(): Result<Any> = Failure(exception)

        override fun forEach(
            onSuccess: (A) -> Unit,
            onFailure: (RuntimeException) -> Unit,
            onEmpty: () -> Unit) = onFailure(exception)

        override fun isEmpty(): Boolean = false

        override fun toString(): String = "Failure(exception=$exception)"
    }

    internal
    data class Success<out A>(internal val value: A) : Result<A>() {

        override fun <B> map(f: (A) -> B): Result<B> = try {
            Success(f(value))
        } catch (e: RuntimeException) {
            Failure(e)
        } catch (e: Exception) {
            Failure(RuntimeException(e))
        }

        override fun <B> flatMap(f: (A) -> Result<B>): Result<B> = try {
            f(value)
        } catch (e: RuntimeException) {
            Failure(e)
        } catch (e: Exception) {
            Failure(RuntimeException(e))
        }

        override fun mapFailure(errMsg: String): Result<A> = this

        override fun <E : RuntimeException> mapFailure(
            msg: String,
            f: (e: RuntimeException) -> (msg: String) -> E):
                Result<A> = this

        override fun mapEmpty(errMsg: String): Result<A> = this

        override fun mapEmptyToSuccess(): Result<Any> = failure("Not Empty")

        override fun forEach(
            onSuccess: (A) -> Unit,
            onFailure: (RuntimeException) -> Unit,
            onEmpty: () -> Unit) = onSuccess(value)

        override fun isEmpty(): Boolean = false

        override fun toString(): String = "Success(value=$value)"
    }

    companion object {
        operator fun <A> invoke(a: A? = null): Result<A> =
            when (a) {
                null -> Failure(NullPointerException())
                else -> Success(a)
            }

        operator fun <A> invoke(a: A? = null, msg: String): Result<A> =
            when (a) {
                null -> Failure(NullPointerException(msg))
                else -> Success(a)
            }

        operator fun <A> invoke(a: A? = null, p: (A) -> Boolean): Result<A> =
            when (a) {
                null -> Failure(NullPointerException())
                else -> when {
                    p(a) -> Success(a)
                    else -> Empty
                }
            }

        operator fun <A> invoke(a: A? = null, msg: String, p: (A) -> Boolean):
                Result<A> =
            when (a) {
                null -> Failure(NullPointerException())
                else -> when {
                    p(a) -> Success(a)
                    else -> {
                        val errMsg = "" +
                                "Argument $a does not match the " +
                                "condition: $msg"
                        Failure(IllegalArgumentException(errMsg))
                    }
                }
            }

        operator fun <A> invoke(): Result<A> = Empty

        fun <A> failure(message: String): Result<A> =
            Failure(IllegalStateException(message))

        fun <A> failure(exception: RuntimeException): Result<A> =
            Failure(exception)

        fun <A> failure(exception: Exception): Result<A> =
            Failure(IllegalStateException(exception))
    }
}

fun <K, V> Map<K, V>.getResult(key: K) = when {
    this.containsKey(key) -> Result(this[key])
    else -> Empty
}

fun <A, B> lift(f: (A) -> B): (Result<A>) -> Result<B> = { it.map(f) }

fun <A, B, C> lift2(f: (A) -> (B) -> C):
            (Result<A>) -> (Result<B>) -> Result<C> =
    { a: Result<A> ->
        { b: Result<B> ->
            a.map(f).flatMap { b.map(it) }
        }
    }

fun <A, B, C, D> lift3(f: (A) -> (B) -> (C) -> D):
            (Result<A>) -> (Result<B>) -> (Result<C>) -> Result<D> =
    { a: Result<A> ->
        { b: Result<B> ->
            { c: Result<C> ->
                a.map(f).flatMap { b.map(it) }.flatMap { c.map(it) }
            }
        }
    }

fun <A, B, C> map2(a: Result<A>, b: Result<B>, f: (A) -> (B) -> C): Result<C> =
    lift2(f)(a)(b)