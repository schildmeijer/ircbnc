import java.util.Date
import java.text.SimpleDateFormat;
import java.net.{Socket, ServerSocket}
import java.io.{IOException, InputStream, BufferedReader, InputStreamReader, DataOutputStream, PrintStream}
import scala.actors.Actor._
import scala.util.matching.Regex
import scala.collection.immutable._

case class ClientConnected(writer: PrintStream)
case class ClientDisconnected()

object IRCConnection {

	def main(args: Array[String]) {
		val formatter = new SimpleDateFormat("kk:mm")

		val ChanMsg = 		 "^:([\\w\\d]+)!.* PRIVMSG (#[\\w\\.\\d]+) :(.*)$".r
		val PingFromClient = "^:([\\w\\d]+)!.* PRIVMSG [\\w\\d]+ :PING$".r
		val PingFromServer = "PING (:[\\w\\d\\.]+)$".r
		val PrivateMsg = 	 "^:([\\w\\d]+)!.* PRIVMSG [\\w\\d]+ (.*)$".r

		val clientActor = actor {
			var history: List[String] = Nil
			var writer: PrintStream = null
			loop {
			react {
			case ClientConnected(clientWriter) => {
				writer = clientWriter
				history.reverse.foreach(writer.println(_))
				history = Nil
			}

			case ClientDisconnected => {
				writer = null
			}


			case msg: String => {
				if (writer != null) {
					writer.println(msg)
				}
				else { 
					history = msg :: history 
				}
			}
			}
		}
		}

		val ircSocket = new Socket("irc.efnet.nl", 6667)
		val ircReader = new BufferedReader(new InputStreamReader(ircSocket.getInputStream))
		val ircActor = actor {
			val ircWriter = new PrintStream(ircSocket.getOutputStream)

			loop {
				react {
				case ChanMsg(from, channel, content) => { 
					var line = 
						formatter.format(
								new Date()) + " " + channel + " " + from + ": " + 
								new String(content.getBytes(), "UTF-8")
					line = new String(line.getBytes(), "UTF-8")
					clientActor ! line
				}

				case PingFromClient(from) => { 
					println("PING from client: " + from)
				}

				case PingFromServer(from) => {
					ircWriter.println("PONG " + from)
				}

				case PrivateMsg(from, content) => { 
					val line = 
						formatter.format(
								new Date()) + " " + from + ": " +  
								new String(content.getBytes(), "UTF-8")
								clientActor ! line
				}

				case msg: String if msg.contains("Found your hostname") => {
					ircWriter.println("USER k3ttle localhost irc.efnet.net :k3ttle")
					ircWriter.println("NICK k3ttle")
					ircWriter.println("JOIN #lu.se")
				}
				}
			}
		}

		actor {
			val serverSocket = new ServerSocket(1492)
			loop {
				val clientSocket = serverSocket.accept()
				val clientWriter = new PrintStream(clientSocket.getOutputStream)
				val clientReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream))
				clientActor ! ClientConnected(clientWriter)
				actor {
					var clientConnected = true
					while(clientConnected) {
						try {
							if (clientReader.readLine == null) {
								clientActor ! ClientDisconnected
								clientConnected = false
							}
						} catch {
							case e: IOException => {
								clientActor ! ClientDisconnected
								clientConnected = false
							}
						}

						Thread.sleep(1000)
					}
				}
			}
		}

		while (true) {
			ircActor ! ircReader.readLine
		}

	}
}
