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
    private val receiver: Actor<Int>,
    private val workersCount: Int) : AbstractActor<Task>(id) {

    private val initial: List<Task>
    private val remainingWork: List<Task>
    private val resultHeap: Heap<Task>
    private val limit: Int
    internal val execute: (WorkersManager) -> (Behavior) -> (Task) -> Unit

    private fun streamResult(
        result: Heap<Task>,
        expected: Int,
        resultsInOrder: List<Int>
    ): Triple<Heap<Task>, Int, List<Int>> {
        val triple = Triple(result, expected, resultsInOrder)

        return result.head.flatMap { headTask: Task ->
            result.tail().map { tail ->
                if (headTask.id == expected) {
                    val list = resultsInOrder.cons(headTask.number)
                    streamResult(tail, expected + 1, list)
                } else triple
            }
        }.getOrElse(triple)
    }

    init {
        val splits = zipWithPosition(toDoList).splitAt(this.workersCount)
        this.initial = splits.first
        this.remainingWork = splits.second
        this.resultHeap = Heap()
        this.limit = toDoList.length - 1
        this.execute = { workersManager ->
            { bh ->
                { task: Task ->
                    val r =
                        streamResult(bh.resultHeap + task, bh.expected, List())
                    r.third.forEach { receiver.tell(it) }

                    if (r.second > limit) receiver.tell(-1)
                    else workersManager.context
                        .become(Behavior(bh.workList.rest(), r.first, r.second))
                }
            }
        }
    }

    override fun onReceive(message: Task, sender: Result<Actor<Task>>) =
        context.become(Behavior(remainingWork, resultHeap, 0))

    private fun initWorkerCreator(task: Task): Result<() -> Unit> =
        Result({
            Worker("Worker ${task.id}")
                .tell(Task(task.id, task.number), self())
        })

    private
    fun tellWorkersToWork(workers: List<() -> Unit>) = workers.forEach { it() }

    fun start() {
        onReceive(Task(0, 0), self())

        sequence(initial.map { this.initWorkerCreator(it) })
            .forEach({ tellWorkersToWork(it) }, { this.receiver.tell(-1) })
    }

    internal inner class Behavior(
        internal val workList: List<Task>,
        internal val resultHeap: Heap<Task>,
        internal val expected: Int) : MessageProcessor<Task> {

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