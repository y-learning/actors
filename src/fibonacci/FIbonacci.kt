package fibonacci

import AbstractActor
import Actor
import fn.collections.range
import fn.result.Result
import fn.collections.List

import java.util.concurrent.Semaphore

private val semaphore = Semaphore(1)
private const val listLength = 20_000
private const val workersNum = 6
private val random = java.util.Random(0)
private val testSubject = range(0, listLength).map { random.nextInt(35) }

private fun processSuccess(list: List<Int>) {
    println("Input: ${testSubject.splitAt(40).first}")
    println("Result: ${list.splitAt(40).first}")
}

private fun processFailure(message: String) = println(message)

private fun makeClient(tPrint: () -> Unit): AbstractActor<Result<List<Int>>> {
    return object : AbstractActor<Result<List<Int>>>("Client") {

        override fun onReceive(
            message: Result<List<Int>>,
            sender: Result<Actor<Result<List<Int>>>>) {

            message.forEach(
                { processSuccess(it) },
                { processFailure(it.message ?: "Unknown Error") })
            tPrint()
            semaphore.release()
        }
    }
}

fun main() {
    semaphore.acquire()
    val startTime = System.currentTimeMillis()
    val client = makeClient {
        val delta = (System.currentTimeMillis() - startTime) / 1000.0
        println("Total time: $delta sec")
    }

    WorkersManager("Manager", testSubject, client, workersNum).start()

    semaphore.acquire()
}
