package fibonacci

import AbstractActor
import Actor
import MessageProcessor
import fn.collections.List
import fn.collections.range
import fn.collections.sequence
import fn.collections.zipWith
import fn.result.Result
import fn.trees.Heap

class WorkersManager(
    id: String,
    toDoList: List<Int>,
    private val client: Actor<Result<List<Int>>>,
    private val workersCount: Int) :
    AbstractActor<Task>(id) {

    private val initial: List<Task>
    private val remainingWork: List<Task>
    private val resultHeap: Heap<Task>
    internal val execute: (WorkersManager) -> (Behavior) -> (Task) -> Unit

    init {
        val splits = zipWithPosition(toDoList).splitAt(this.workersCount)
        this.initial = splits.first
        this.remainingWork = splits.second
        this.resultHeap = Heap()

        this.execute = { workersManager ->
            { behavior ->
                { task: Task ->
                    val result = behavior.resultHeap + task
                    if (result.size == toDoList.length)
                        client.tell(Result(result.toList().map { it.number }))
                    else workersManager.context
                        .become(Behavior(behavior.workList.rest(), result))
                }
            }
        }
    }

    override fun onReceive(message: Task, sender: Result<Actor<Task>>) =
        context.become(Behavior(remainingWork, resultHeap))


    private fun initWorkerCreator(task: Task): Result<() -> Unit> =
        Result({
            Worker("Worker ${task.id}")
                .tell(Task(task.id, task.number), self())
        })

    private
    fun tellWorkersToWork(workers: List<() -> Unit>) = workers.forEach { it() }

    private fun tellClientEmptyResult(string: String) =
        client.tell(Result.failure("$string caused by empty input list."))

    fun start() {
        onReceive(Task(0, 0), self())

        sequence(initial.map { this.initWorkerCreator(it) })
            .forEach(onSuccess = { tellWorkersToWork(it) }, onFailure = {
                this.tellClientEmptyResult(it.message ?: "Unknown error")
            })
    }

    internal inner class Behavior(
        internal val workList: List<Task>,
        internal val resultHeap: Heap<Task>) : MessageProcessor<Task> {

        override fun process(message: Task, sender: Result<Actor<Task>>) {
            execute(this@WorkersManager)(this@Behavior)(message)
            sender.forEach(onSuccess = { _sender: Actor<Task> ->
                workList.firstSafe()
                    .forEach({ _sender.tell(it, self()) }) {
                        _sender.shutdown()
                    }
            })
        }
    }
}

fun zipWithPosition(numbers: List<Int>): List<Task> =
    zipWith(numbers, range(0, numbers.length)) { n: Int ->
        { pos: Int -> Task(pos, n) }
    }