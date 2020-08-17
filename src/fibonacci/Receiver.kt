package fibonacci

import AbstractActor
import Actor
import MessageProcessor
import fn.result.Result
import fn.collections.List

class Receiver(id: String, private val client: Actor<List<Int>>) :
    AbstractActor<Int>(id) {

    private val execute: (Receiver) -> (Behavior) -> (Int) -> Unit

    init {
        execute = { receiver ->
            { behavior ->
                { i ->
                    if (i == -1) {
                        this.client.tell(behavior.resultList.reverse())
                        shutdown()
                    } else receiver.context
                        .become(Behavior(behavior.resultList.cons(i)))
                }
            }
        }
    }

    override fun onReceive(message: Int, sender: Result<Actor<Int>>) =
        context.become(Behavior(List(message)))

    internal inner class Behavior internal constructor(
        internal val resultList: List<Int>) : MessageProcessor<Int> {

        override fun process(message: Int, sender: Result<Actor<Int>>) =
            execute(this@Receiver)(this@Behavior)(message)
    }
}