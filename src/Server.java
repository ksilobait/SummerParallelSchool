import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.*;

public class Server implements Runnable {
	private InetAddress serverIP;
	private int serverPort;

	private enum ConnectionState {
		UNKNOWN, MACHINE, PERSON
	}

	private Map<SocketChannel, Integer> hostPorts = new HashMap<>();
	private Map<SocketChannel, InetAddress> hostIPs = new HashMap<>();
	private Map<SocketChannel, ConnectionState> hostStates = new HashMap<>();

	public Server(String ip, int port) throws UnknownHostException {
		this.serverIP = InetAddress.getByName(ip);
		this.serverPort = port;
		new Thread(this).start();
	}

	@Override
	public void run() {
		try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
			serverSocketChannel.configureBlocking(false);
			ServerSocket serverSocket = serverSocketChannel.socket();
			serverSocket.bind(new InetSocketAddress(serverIP, serverPort));

			Selector selector = Selector.open();
			serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT); //listen for incoming connections
			System.out.println(">>listening on port " + serverPort);

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

						SocketChannel socketChannel = newSocket.getChannel();
						socketChannel.configureBlocking(false);
						socketChannel.register(selector, SelectionKey.OP_READ); //wait for incoming data

						hostIPs.put(socketChannel, newSocket.getInetAddress());
						hostPorts.put(socketChannel, newSocket.getPort());
						hostStates.put(socketChannel, ConnectionState.UNKNOWN);

						System.out.println(">>accepting new connection from " + newSocket.getInetAddress() + ":" + newSocket.getPort());
					} else if (key.isReadable()) {
						//socket received new data

						SocketChannel socketChannel = (SocketChannel) key.channel();

						ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
						socketChannel.read(byteBuffer);
						byteBuffer.flip();

						CharBuffer charBuffer = Charset.forName("US-ASCII").decode(byteBuffer);
						String message = charBuffer.toString();

						processMessage(message, socketChannel);

						socketChannel.register(selector, SelectionKey.OP_READ); //wait for incoming data
					}
					keyIterator.remove();
				}
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	private void processMessage(String message, SocketChannel socketChannel) throws IOException {
		try {
			String[] parsedMessage = message.trim().split(" ");
			if (parsedMessage[0].equals("init")) {
				if (hostStates.get(socketChannel).equals(ConnectionState.UNKNOWN)) {
					InetAddress ip = InetAddress.getByName(parsedMessage[1]);
					int port = Integer.valueOf(parsedMessage[2]);

					hostStates.put(socketChannel, ConnectionState.MACHINE);
					hostIPs.put(socketChannel, ip);
					hostPorts.put(socketChannel, port);
				} else {
					hostStates.put(socketChannel, ConnectionState.PERSON);
				}
				return;
			}

			if (hostStates.get(socketChannel).equals(ConnectionState.UNKNOWN)) {
				hostStates.put(socketChannel, ConnectionState.PERSON);
			}

			System.out.println("!!!" + message);
			switch (parsedMessage[0]) {
				case "connect": {
					for (int i = 1; i < parsedMessage.length - 1; i += 2) {
						InetAddress ip = InetAddress.getByName(parsedMessage[i]);
						int port = Integer.valueOf(parsedMessage[i + 1]);
						System.out.println("Try to connect with " + ip + " " + port);
						connect(new MyInetStruct(ip, port));
					}
					break;
				}
				case "conn_net": {
					//TODO
					InetAddress ip = InetAddress.getByName(parsedMessage[1]);
					int port = Integer.valueOf(parsedMessage[2]);
					conn_net(new MyInetStruct(ip, port));
					break;
				}
				case "request_hosts": {
					InetAddress ip = InetAddress.getByName(parsedMessage[1]);
					int port = Integer.valueOf(parsedMessage[2]);
					request_hosts(new MyInetStruct(ip, port));
					break;
				}
				case "print_hosts":
					print_hosts(socketChannel);
					break;
				case "print_hosts2":
					print_hosts2(socketChannel);
					break;
				case "exit":
					myExit();
					break;
				default:
					System.out.println("Illegal command!");
					break;
			}
		} catch (IndexOutOfBoundsException e) {
			System.err.println(e.getMessage());
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

	private void print_hosts(SocketChannel socketChannel) throws IOException {
		for (SocketChannel channel : hostIPs.keySet()) {
			if (!hostStates.get(channel).equals(ConnectionState.MACHINE)) {
				continue;
			}

			String response = hostIPs.get(channel).getHostAddress() + " " + hostPorts.get(channel) + "\n";
			ByteBuffer buffer = ByteBuffer.allocate(1024);
			buffer.clear();
			buffer.put(response.getBytes());
			buffer.flip();

			while (buffer.hasRemaining()) {
				socketChannel.write(buffer);
			}
		}
	}

	private void print_hosts2(SocketChannel socketChannel) throws IOException {
		for (SocketChannel channel : hostIPs.keySet()) {
			String response = hostIPs.get(channel).getHostAddress() + ":" + hostPorts.get(channel) + " " + hostStates.get(channel) + "\n";
			ByteBuffer buffer = ByteBuffer.allocate(1024);
			buffer.clear();
			buffer.put(response.getBytes());
			buffer.flip();

			while (buffer.hasRemaining()) {
				socketChannel.write(buffer);
			}
		}
	}

	private void conn_net(MyInetStruct struct) throws IOException {
		StringBuilder response = new StringBuilder("request_hosts "+serverIP.getHostAddress()+" "+serverPort);
		System.out.println(">>I'll send " + response);

		ByteBuffer buffer = ByteBuffer.allocate(1024);
		buffer.clear();
		buffer.put(response.toString().getBytes());
		buffer.flip();

		SocketChannel destination = null;
		for (SocketChannel channel : hostIPs.keySet()) {
			if (hostIPs.get(channel).equals(struct.ip) && hostPorts.get(channel).equals(struct.port)) {
				destination = channel;
				break;
			}
		}

		if (destination == null) {
			return;
		}

		while (buffer.hasRemaining()) {
			destination.write(buffer);
		}

	}

	private void request_hosts(MyInetStruct struct) throws IOException {
		StringBuilder response = new StringBuilder("connect");
		for (SocketChannel channel : hostIPs.keySet()) {
			if (!hostStates.get(channel).equals(ConnectionState.MACHINE)) {
				continue;
			}

			response.append(" ")
					.append(hostIPs.get(channel).getHostAddress())
					.append(" ")
					.append(hostPorts.get(channel));
		}
		if (response.toString().equals("connect")) {
			return;
		}

		System.out.println(">>I'll send " + response);
		//TODO: new connection or find existent
		SocketChannel destination = null;
		for (SocketChannel channel : hostIPs.keySet()) {
			if (hostIPs.get(channel).equals(struct.ip) && hostPorts.get(channel).equals(struct.port)) {
				destination = channel;
				break;
			}
		}

		if (destination == null) {
			return;
		}

		ByteBuffer buffer = ByteBuffer.allocate(1024);
		buffer.clear();
		buffer.put(response.toString().getBytes());
		buffer.flip();

		while (buffer.hasRemaining()) {
			destination.write(buffer);
		}
	}

	private void connect(MyInetStruct remote) throws IOException {
		SocketAddress address = new InetSocketAddress(remote.ip, remote.port);
		SocketChannel client = SocketChannel.open(address);

		if (serverIP.equals(remote.ip) && serverPort == remote.port) {
			return;
		}

		for (SocketChannel channel : hostIPs.keySet()) {
			if (hostIPs.get(channel).equals(remote.ip) && (hostPorts.get(channel).equals(remote.port))) {
				return;
			}
		}

		//send init
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		buffer.clear();
		buffer.put(("init " + this.serverIP.getHostAddress() + " " + this.serverPort).getBytes());
		buffer.flip();

		while (buffer.hasRemaining()) {
			client.write(buffer);
		}

		hostStates.put(client, ConnectionState.MACHINE);
		hostIPs.put(client, remote.ip);
		hostPorts.put(client, remote.port);

	}

	private void myExit() {
		//TODO
	}

	public static void main(String[] args) throws UnknownHostException {
		System.out.println("Please enter your ip and port:");
		Scanner scanner = new Scanner(System.in);
		//String ip = scanner.nextLine();
		String ip = "localhost";
		int port = scanner.nextInt();
		new Server(ip, port);
	}
}
