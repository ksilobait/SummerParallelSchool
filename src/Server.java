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

	private Server(String ip, int port) throws UnknownHostException {
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

						System.out.println(">>accepting new connection from " + newSocket.getInetAddress().getHostAddress() + ":" + newSocket.getPort());
					} else if (key.isReadable()) {
						//socket received new data

						SocketChannel socketChannel = (SocketChannel) key.channel();

						ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
						socketChannel.read(byteBuffer);
						byteBuffer.flip();

						CharBuffer charBuffer = Charset.forName("UTF-8").decode(byteBuffer);
						String message = charBuffer.toString();
						if (message.isEmpty()) {
							//disconnected
							System.out.println("client " + hostIPs.get(socketChannel).getHostAddress() + ":" + hostPorts.get(socketChannel) + " disconnected");
							hostStates.remove(socketChannel);
							hostPorts.remove(socketChannel);
							hostIPs.remove(socketChannel);
							socketChannel.close();
						} else {
							String[] messages = message.split("\r\n");
							for (String msg : messages) {
								processMessage(msg, socketChannel, selector);
								if (socketChannel.isOpen()) {
									socketChannel.register(selector, SelectionKey.OP_READ); //wait for incoming data
								}
							}
						}

					}
					keyIterator.remove();
				}
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	private void processMessage(String message, SocketChannel socketChannel, Selector selector) throws IOException {
		try {
			if (message.isEmpty()) {
				//disconnected
				System.out.println("client " + hostIPs.get(socketChannel).getHostAddress() + ":" + hostPorts.get(socketChannel) + " disconnected");
				hostStates.remove(socketChannel);
				hostPorts.remove(socketChannel);
				hostIPs.remove(socketChannel);
				socketChannel.close();
				return;
			}

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

			System.out.println(">>received \"" + message.trim() + "\"");
			switch (parsedMessage[0]) {
				case "connect": {
					for (int i = 1; i < parsedMessage.length - 1; i += 2) {
						InetAddress ip = InetAddress.getByName(parsedMessage[i]);
						int port = Integer.valueOf(parsedMessage[i + 1]);
						System.out.println(">>Connecting with " + ip.getHostAddress() + " " + port);
						connect(new MyInetStruct(ip, port), selector);
					}
					break;
				}
				case "conn_net": {
					InetAddress ip = InetAddress.getByName(parsedMessage[1]);
					int port = Integer.valueOf(parsedMessage[2]);
					MyInetStruct remote = new MyInetStruct(ip, port);
					connect(remote, selector);
					conn_net(remote);
					break;
				}
				case "request_hosts": {
					InetAddress ip = InetAddress.getByName(parsedMessage[1]);
					int port = Integer.valueOf(parsedMessage[2]);
					MyInetStruct remote = new MyInetStruct(ip, port);
					connect(remote, selector);
					request_hosts(remote);
					break;
				}
				case "print_hosts":
					print_hosts(socketChannel);
					break;
				case "print_hosts2":
					print_hosts2(socketChannel);
					break;
				case "exit":
					hostStates.remove(socketChannel);
					hostPorts.remove(socketChannel);
					hostIPs.remove(socketChannel);
					socketChannel.close();
					break;
				default:
					System.err.println("illegal command");
					break;
			}
		} catch (IndexOutOfBoundsException e) {
			e.printStackTrace();
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

		//write itself
		String response = serverIP.getHostAddress() + " " + serverPort + "\n";
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		buffer.clear();
		buffer.put(response.getBytes());
		buffer.flip();
		while (buffer.hasRemaining()) {
			socketChannel.write(buffer);
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
		StringBuilder response = new StringBuilder("request_hosts " + serverIP.getHostAddress() + " " + serverPort + "\r\n");
		System.out.println(">>I'll send \"" + response.toString().trim() + "\" in conn_net");

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
		response.append("\r\n");

		System.out.println(">>I'll send \"" + response + "\" in request_hosts");
		//TODO: new connection or find existent
		SocketChannel destination = null;
		for (SocketChannel channel : hostIPs.keySet()) {
			if (hostIPs.get(channel).equals(struct.ip) && hostPorts.get(channel).equals(struct.port)) {
				destination = channel;
				System.out.println("selected " + destination + "because of " + struct.ip + " " + struct.port);
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

	private void connect(MyInetStruct remote, Selector selector) throws IOException {
		if (serverIP.equals(remote.ip) && serverPort == remote.port) {
			return;
		}

		for (SocketChannel channel : hostIPs.keySet()) {
			if (hostIPs.get(channel).equals(remote.ip) && (hostPorts.get(channel).equals(remote.port))) {
				return;
			}
		}

		SocketAddress address = new InetSocketAddress(remote.ip, remote.port);
		SocketChannel client = SocketChannel.open(address);
		client.configureBlocking(false);
		client.register(selector, SelectionKey.OP_READ); //listen for incoming connections

		//send init
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		buffer.clear();
		buffer.put(("init " + this.serverIP.getHostAddress() + " " + this.serverPort + "\r\n").getBytes());
		buffer.flip();

		while (buffer.hasRemaining()) {
			client.write(buffer);
		}

		hostStates.put(client, ConnectionState.MACHINE);
		hostIPs.put(client, remote.ip);
		hostPorts.put(client, remote.port);

	}

	public static void main(String[] args) throws UnknownHostException {
		System.out.println("Please enter your ip and port:");
		Scanner scanner = new Scanner(System.in);
		//String ip = scanner.nextLine();
		//String ip = "192.168.12.31";
		String ip = "localhost";
		int port = scanner.nextInt();
		new Server(ip, port);
	}
}
