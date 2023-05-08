import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.util.logging.Logger

class EventConsumer<E : Any>(private var channel: Channel<E>) {
    suspend fun start(onReceive: suspend (E) -> Unit, onClose: () -> Unit){
        try {
            logger.info("Starting event consumer...")

            while(true) {
                val receivedEvent = channel.receive()
                logger.info("Event $receivedEvent received!")

                onReceive(receivedEvent)
            }
        } catch (e: ClosedReceiveChannelException) {
            logger.warning("Channel closed to receive events!")
            onClose()
        } catch (e: Exception) {
            logger.warning("Something when wrong on channel...")
            e.printStackTrace()
        }
    }

    fun reset(channel: Channel<E>){
        this.channel = channel
    }

    fun stop(){
        channel.close()
    }

    companion object {
        private const val TAG = "EventConsumer"
        private val logger = Logger.getLogger(TAG)
    }
}