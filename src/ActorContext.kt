import fn.result.Result

interface MessageProcessor<T> {

    fun process(message: T, sender: Result<Actor<T>>)
}

interface ActorContext<T> {

    fun behavior(): MessageProcessor<T>

    fun become(behavior: MessageProcessor<T>)
}
