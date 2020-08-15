package fibonacci

import AbstractActor
import Actor
import result.Result


class Worker(id: String) : AbstractActor<Int>(id) {
    private fun slowFibonacci(n: Int): Int = when (n) {
        0 -> 1
        1 -> 1
        else -> slowFibonacci(n - 1) + slowFibonacci(n - 2)
    }

    override fun onReceive(message: Int, sender: Result<Actor<Int>>) {
        sender.forEach(onSuccess = { _sender: Actor<Int> ->
            _sender.tell(slowFibonacci(message), self())
        })
    }
}