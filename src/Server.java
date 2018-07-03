import java.io.IOException;
import java.io.PrintWriter;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.*;

public class Server implements Runnable {
	private int port;
	private Map<MyInetStruct, Socket> hosts = new HashMap<>();

	public Server(int port) {
		this.port = port;
		new Thread(this).start();
	}

	@Override
	public void run() {
		try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
			serverSocketChannel.configureBlocking(false);
			ServerSocket serverSocket = serverSocketChannel.socket();
			serverSocket.bind(new InetSocketAddress(port));

			Selector selector = Selector.open();
			serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT); //listen for incoming connections
			System.out.println(">>listening on port " + port);

			while (true) {
				int readyChannels = selector.select();
				if (readyChannels == 0) {
					continue;
				}

				Set<SelectionKey> keys = selector.selectedKeys();
				Iterator<SelectionKey> keyIterator = keys.iterator();
				while (keyIterator.hasNext()) {
					SelectionKey key = keyIterator.next();

					if (key.isAcceptable()) {
						//incoming connection
						Socket newSocket = serverSocket.accept();
						System.out.println(">>accepting new connection from " + newSocket);

						SocketChannel socketChannel = newSocket.getChannel();
						socketChannel.configureBlocking(false);
						socketChannel.register(selector, SelectionKey.OP_READ); //wait for incoming data
					} else if (key.isReadable()) {
						//socket received new data
						try (SocketChannel socketChannel = (SocketChannel) key.channel()) {
							ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
							socketChannel.read(byteBuffer);
							byteBuffer.flip();

							CharBuffer charBuffer = Charset.forName("US-ASCII").decode(byteBuffer);
							String message = charBuffer.toString();
							System.out.println(">>received " + message);

							processMessage(message);
						} catch (IOException e) {
							key.cancel();
						}
					}
					keyIterator.remove();
				}
			}
		} catch (IOException e) {
			System.out.println(e.getMessage());
		}
	}

	private void processMessage(String message) throws IOException {
		String[] parsedMessage = message.split(" ");

		switch (parsedMessage[0]) {
			case "connect": {
				for(int i = 1; i < parsedMessage.length - 1; i+=2) {
					InetAddress ip = InetAddress.getByName(parsedMessage[i]);
					int port = Integer.valueOf(parsedMessage[i + 1]);
					MyInetStruct tmpStruct = new MyInetStruct(ip, port);
					connect(tmpStruct);
				}
				break;
			}
			case "conn_net": {
				//TODO
				InetAddress ip = InetAddress.getByName(parsedMessage[1]);
				int port = Integer.valueOf(parsedMessage[2]);
				conn_net(ip, port);
				break;
			}
			case "request_hosts": {
				InetAddress ip = InetAddress.getByName(parsedMessage[1]);
				int port = Integer.valueOf(parsedMessage[2]);
				request_hosts(ip, port);
				break;
			}
			case "print_hosts":
				print_hosts();
				break;
			case "exit":
				myExit();
				break;
			case "help":
				myHelp();
				break;
			default:
				System.out.println("Illegal command!");
				break;
		}
	}

	private class MyInetStruct {
		MyInetStruct(InetAddress ip, int port) {
			this.ip = ip;
			this.port = port;
		}

		InetAddress ip;
		int port;
	}


	private static void print_hosts() {
		for (int i = 0; i < hosts.size(); i++) {
			System.out.print(hosts.get(i) + " ");
		}
		System.out.println();
	}


	private void conn_net(InetAddress ip, int port) {
		//TODO
	}

	private void request_hosts(MyInetStruct struct) throws IOException {
		Socket destination = hosts.get(struct);
		PrintWriter out = new PrintWriter(destination.getOutputStream());

	}

	/*private void connect(MyInetStruct... struct) throws IOException {
		for (MyInetStruct st: struct) {
			hosts.put(st, new Socket(st.ip, st.port));
		}
	}*/


	private void connect(MyInetStruct struct) throws IOException {
		hosts.put(struct, new Socket(struct.ip, struct.port));
	}

	private void myExit() {
		//TODO
	}

	private void myHelp() {
		//TODO
		System.out.println("LOL");
	}


	public static void main(String[] args) {
		System.out.println("Please enter your port:");
		Scanner scanner = new Scanner(System.in);
		int port = scanner.nextInt();
		new Server(port);
	}
}
