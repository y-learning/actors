package fibonacci

import AbstractActor
import Actor
import fn.result.Result


fun slowFibonacci(n: Int): Int = when (n) {
    0 -> 1
    1 -> 1
    else -> slowFibonacci(n - 1) + slowFibonacci(n - 2)
}

data class Task(val id: Int, val number: Int) : Comparable<Task> {
    override fun compareTo(other: Task): Int = when {
        this.id > other.id -> 1
        this.id < other.id -> -1
        else -> 0
    }
}

class Worker(id: String) : AbstractActor<Task>(id) {
    override fun onReceive(message: Task, sender: Result<Actor<Task>>) {
        sender.forEach(onSuccess = { _sender: Actor<Task> ->
            _sender.tell(
                Task(message.id, slowFibonacci(message.number)),
                self())
        })
    }
}
