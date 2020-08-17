package pingpong

import AbstractActor
import Actor
import fn.result.Result

import java.util.concurrent.Semaphore

private val semaphore = Semaphore(1)

val referee = object : AbstractActor<Int>("Referee") {
    override fun onReceive(message: Int, sender: Result<Actor<Int>>) {
        println("Game ended after $message shots")
        semaphore.release()
    }
}

fun player(id: String, sound: String, referee: Actor<Int>) =
    object : AbstractActor<Int>(id) {
        override fun onReceive(message: Int, sender: Result<Actor<Int>>) {
            println("$sound $message")
            Thread.sleep(300)
            if (message >= 10) referee.tell(message, sender)
            else sender.forEach({ _sender: Actor<Int> ->
                _sender.tell(message + 1, self())
            }, {
                referee.tell(message, sender)
            })
        }
    }

fun main() {
    val player1 = player("Player 1", "Ping", referee)
    val player2 = player("Player 2", "Pong", referee)
    val ball = 1

    semaphore.acquire()
    player1.tell(ball, Result(player2))
    semaphore.acquire()
}