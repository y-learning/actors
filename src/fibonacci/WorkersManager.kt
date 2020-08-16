package fibonacci

import AbstractActor
import Actor
import MessageProcessor
import fn.collections.List
import fn.collections.range
import fn.collections.sequence
import fn.collections.zipWith
import fn.result.Result

class WorkersManager(
    id: String,
    toDoList: List<Int>,
    private val client: Actor<Result<List<Int>>>,
    private val workersCount: Int) :
    AbstractActor<Int>(id) {

    private val initial: List<Pair<Int, Int>>
    private val remainingWork: List<Int>
    private val resultList: List<Int>
    internal val execute: (WorkersManager) -> (Behavior) -> (Int) -> Unit

    init {
        val splits = toDoList.splitAt(this.workersCount)
        this.initial = zipWithPosition(splits.first)
        this.remainingWork = splits.second
        this.resultList = List()
        this.execute = { workersManager ->
            { behavior ->
                { resultNum: Int ->
                    val result = behavior.resultList.cons(resultNum)
                    if (result.length == toDoList.length)
                        client.tell(Result(result))
                    else workersManager.context
                        .become(Behavior(behavior.workList.rest(), result))
                }
            }
        }
    }

    override fun onReceive(message: Int, sender: Result<Actor<Int>>) =
        context.become(Behavior(remainingWork, resultList))

    private fun initWorkerCreator(pair: Pair<Int, Int>): Result<() -> Unit> =
        Result({ Worker("Worker ${pair.second}").tell(pair.first, self()) })

    private
    fun tellWorkersToWork(workers: List<() -> Unit>) = workers.forEach { it() }

    private fun tellClientEmptyResult(string: String) =
        client.tell(Result.failure("$string caused by empty input list."))

    fun start() {
        onReceive(0, self())

        sequence(initial.map { this.initWorkerCreator(it) })
            .forEach(onSuccess = { tellWorkersToWork(it) }, onFailure = {
                this.tellClientEmptyResult(it.message ?: "Unknown error")
            })
    }

    internal inner class Behavior(
        internal val workList: List<Int>,
        internal val resultList: List<Int>) : MessageProcessor<Int> {

        override fun process(message: Int, sender: Result<Actor<Int>>) {
            execute(this@WorkersManager)(this@Behavior)(message)
            sender.forEach(onSuccess = { _sender: Actor<Int> ->
                workList.firstSafe()
                    .forEach({ _sender.tell(it, self()) }) {
                        _sender.shutdown()
                    }
            })
        }
    }
}

fun zipWithPosition(numbers: List<Int>): List<Pair<Int, Int>> {
    return zipWith(numbers, range(0, numbers.length)) { n: Int ->
        { pos: Int ->
            Pair(n, pos)
        }
    }
}