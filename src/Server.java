import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Server implements Runnable {
	private InetAddress serverIP;
	private int serverPort;
	private AtomicInteger minUnusedLocalID;
	private Map<String, Pair> names;
	private List<List<String>> arguments;
	private DRTS drts;

	int getCount(int call_id) {
		return arguments.get(call_id).size();
	}

	private class Pair {
		String first;
		String second;

		Pair(String first, String second) {
			this.first = first;
			this.second = second;
		}
	}

	public native void cLaunchMethod(String dll, String codeName, int call_id);
	static {
		System.load("/home/ruslan/Kraken/SummerParallelSchool/src/Server.so");
	}

	private enum ConnectionState {
		UNKNOWN, DEFAULT, SIMPLE, SELF
	}

	class ConnectionInfo {
		SocketChannel channel;
		InetAddress ip;
		int port;
		int localID;
		ConnectionState state;

		ConnectionInfo(SocketChannel channel, InetAddress ip, int port, int localID, ConnectionState state) {
			this.channel = channel;
			this.ip = ip;
			this.port = port;
			this.localID = localID;
			this.state = state;
		}
	}
	private List<ConnectionInfo> hostsConnections = new LinkedList<>();

	class GCID {
		InetAddress ip;
		int port;
		int lcid;

		@Override
		public String toString() {
			return ip.getHostAddress() + " " + port + " " + lcid + " ";
		}
	}

	private class ExtendedHostInfo {
		ExtendedHostInfo(InetAddress ip, int port, ConnectionState state) {
			this.ip = ip;
			this.port = port;
			this.state = state;
		}

		InetAddress ip;
		int port;
		ConnectionState state;
	}


	private class HostInfo {
		HostInfo(InetAddress ip, int port) {
			this.ip = ip;
			this.port = port;
		}

		InetAddress ip;
		int port;
	}

	private Set<String> dictionary = new HashSet<>();

	private Server(String ip, int port) throws UnknownHostException {
		this.dictionary.add("connect");
		this.dictionary.add("conn_net");
		this.dictionary.add("request_hosts");
		this.dictionary.add("print_hosts");
		this.dictionary.add("print_hosts2");
		this.dictionary.add("exit");
		this.dictionary.add("kill");

		this.dictionary.add("import");
		this.dictionary.add("exec");

		minUnusedLocalID = new AtomicInteger(0);
		names = new HashMap<>();
		arguments = new LinkedList<>();
		this.drts = new DRTS(this);

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

						hostsConnections.add(new ConnectionInfo(socketChannel, newSocket.getInetAddress(), newSocket.getPort(), Integer.MIN_VALUE, ConnectionState.UNKNOWN));

						System.out.println(">>accepting new connection from " + newSocket.getInetAddress().getHostAddress() + ":" + newSocket.getPort());
					} else if (key.isReadable()) {
						//socket received new data

						SocketChannel socketChannel = (SocketChannel) key.channel();

						ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
						socketChannel.read(byteBuffer);
						byteBuffer.flip();

						CharBuffer charBuffer = Charset.forName("UTF-8").decode(byteBuffer);
						String message = charBuffer.toString();
						String[] messages = message.split("\r\n");
						for (String msg : messages) {
							processMessage(msg, socketChannel, selector);
							if (socketChannel.isOpen()) {
								socketChannel.register(selector, SelectionKey.OP_READ); //wait for incoming data
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
				hostsConnections.removeIf(info -> info.channel.equals(socketChannel));
				System.out.println("client " + socketChannel.getRemoteAddress() + " disconnected");
				socketChannel.close();
				return;
			}

			String[] parsedMessage = message.trim().split(" ");
			System.out.println(">>received \"" + message.trim() + "\"");

			if (parsedMessage[0].equals("init")) {
				for (ConnectionInfo info : hostsConnections) {
					if (info.channel.equals(socketChannel)) {
						if (info.state.equals(ConnectionState.UNKNOWN)) {
							InetAddress ip = InetAddress.getByName(parsedMessage[1]);
							int port = Integer.valueOf(parsedMessage[2]);

							info.ip = ip;
							info.port = port;
							info.state = ConnectionState.DEFAULT;
						} else {
							System.err.println(">>init is not available anymore");
							info.state = ConnectionState.SIMPLE;
						}
						break;
					}
				}
				return;
			}


			//not an init message

			for (ConnectionInfo info : hostsConnections) {
				if (info.channel.equals(socketChannel) && info.state.equals(ConnectionState.UNKNOWN)) {
					info.state = ConnectionState.SIMPLE;
					info.localID = minUnusedLocalID.getAndIncrement();
					break;
				}
			}

			//check if there is gcid
			int shift = 0;
			GCID gcid = null;
			if (parsedMessage.length > 2 && parsedMessage[1].matches("[0-9]+") && parsedMessage[2].matches("[0-9]+")) { //port and lcid
				gcid = new GCID();
				gcid.ip = InetAddress.getByName(parsedMessage[0]);
				gcid.port = Integer.valueOf(parsedMessage[1]);
				gcid.lcid = Integer.valueOf(parsedMessage[2]);
				shift = 3;
			} else {
				for (ConnectionInfo info : hostsConnections) {
					if (info.channel.equals(socketChannel) && info.state.equals(ConnectionState.SIMPLE)) {
						gcid = new GCID();
						gcid.ip = info.ip;
						gcid.port = info.port;
						gcid.lcid = info.localID;
						break;
					}
				}

			}

			switch (parsedMessage[shift]) {
				case "connect": {
					for (int i = shift + 1; i < parsedMessage.length - 1; i += 2) {
						InetAddress ip = InetAddress.getByName(parsedMessage[i]);
						int port = Integer.valueOf(parsedMessage[i + 1]);
						connect(new HostInfo(ip, port), selector);
					}
					break;
				}
				case "conn_net": {
					InetAddress ip = InetAddress.getByName(parsedMessage[shift + 1]);
					int port = Integer.valueOf(parsedMessage[shift + 2]);
					HostInfo remote = new HostInfo(ip, port);

					connect(remote, selector);
					conn_net(remote, gcid);
					break;
				}
				case "request_hosts": {
					InetAddress ip = InetAddress.getByName(parsedMessage[shift + 1]);
					int port = Integer.valueOf(parsedMessage[shift + 2]);
					HostInfo remote = new HostInfo(ip, port);
					connect(remote, selector);
					request_hosts(remote, gcid);
					break;
				}
				case "print_hosts":
					print_hosts(socketChannel);
					break;
				case "print_hosts2":
					print_hosts2(socketChannel);
					break;
				case "exit":
					hostsConnections.removeIf(host -> host.channel.equals(socketChannel));
					socketChannel.close();
					break;
				case "kill":
					System.exit(0);
					break;

				// LEVEL 2
				case "import": {
					String newName = parsedMessage[shift + 1];
					String dll = parsedMessage[shift + 2];
					String codeName = parsedMessage[shift + 3];
					names.put(newName, new Pair(dll, codeName));
					break;
				}

				case "exec": {
					String newName = parsedMessage[shift + 1];

					String dll = names.get(newName).first;
					String codeName = names.get(newName).second;
					List<String> args = new ArrayList<>(Arrays.asList(parsedMessage).subList(shift + 2, parsedMessage.length));
					arguments.add(args);
					int call_id = arguments.size();
					cLaunchMethod(dll, codeName, call_id);
					break;
				}

				default:
					System.err.println(">>invalid command");
					String response = "illegal command; valid commands: " + String.join(", ", dictionary) + "\n";
					ByteBuffer buffer = ByteBuffer.allocate(1024);
					buffer.clear();
					buffer.put(response.getBytes());
					buffer.flip();
					while (buffer.hasRemaining()) {
						socketChannel.write(buffer);
					}
					break;
			}
		} catch (IndexOutOfBoundsException e) {
			System.err.println(">>out of bounds");
			String response = "out of bounds, check syntax: " + String.join(", ", dictionary) + "\n";
			ByteBuffer buffer = ByteBuffer.allocate(1024);
			buffer.clear();
			buffer.put(response.getBytes());
			buffer.flip();
			while (buffer.hasRemaining()) {
				socketChannel.write(buffer);
			}
		}

	}

	private void print_hosts(SocketChannel socketChannel) throws IOException {
		Set<HostInfo> hosts = new HashSet<>();
		hosts.add(new HostInfo(serverIP, serverPort)); //itself

		for (ConnectionInfo host : hostsConnections) {
			if (!host.state.equals(ConnectionState.DEFAULT)) {
				continue;
			}

			hosts.add(new HostInfo(host.ip, host.port));
		}

		for (HostInfo host : hosts) {
			String response = host.ip.getHostAddress() + " " + host.port + "\n";
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
		Set<ExtendedHostInfo> hosts = new HashSet<>();
		for (ConnectionInfo host : hostsConnections) {
			hosts.add(new ExtendedHostInfo(host.ip, host.port, host.state));
		}

		for (ExtendedHostInfo host : hosts) {
			String response = host.ip.getHostAddress() + ":" + host.port + " " + host.state + "\n";
			ByteBuffer buffer = ByteBuffer.allocate(1024);
			buffer.clear();
			buffer.put(response.getBytes());
			buffer.flip();

			while (buffer.hasRemaining()) {
				socketChannel.write(buffer);
			}
		}

		//write itself
		String response = serverIP.getHostAddress() + ":" + serverPort + " " + ConnectionState.SELF + "\n";
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		buffer.clear();
		buffer.put(response.getBytes());
		buffer.flip();
		while (buffer.hasRemaining()) {
			socketChannel.write(buffer);
		}
	}

	private void conn_net(HostInfo struct, GCID gcid) throws IOException {
		StringBuilder response = new StringBuilder();
		if (gcid != null) {
			response.append(gcid.toString());
		}
		response.append("request_hosts ").append(serverIP.getHostAddress()).append(" ").append(serverPort).append("\r\n");
		System.out.println(">>I'll send \"" + response.toString().trim() + "\" in conn_net");

		ByteBuffer buffer = ByteBuffer.allocate(1024);
		buffer.clear();
		buffer.put(response.toString().getBytes());
		buffer.flip();

		SocketChannel destination = null;
		for (ConnectionInfo host : hostsConnections) {
			if (host.ip.equals(struct.ip) && host.port== struct.port) {
				destination = host.channel;
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

	private void request_hosts(HostInfo struct, GCID gcid) throws IOException {
		StringBuilder response = new StringBuilder();
		if (gcid != null) {
			response.append(gcid.toString());
		}
		response.append("connect");

		for (ConnectionInfo host : hostsConnections) {
			if (!host.state.equals(ConnectionState.DEFAULT)) {
				continue;
			}

			response.append(" ").append(host.ip.getHostAddress()).append(" ").append(host.port);
		}
		if (response.toString().equals("connect") || (gcid != null && response.toString().equals(gcid.toString() + " connect"))) {
			return;
		}
		response.append("\r\n");

		System.out.println(">>I'll send \"" + response.toString().trim() + "\" in request_hosts");
		SocketChannel destination = null;
		for (ConnectionInfo host : hostsConnections) {
			if (host.ip.equals(struct.ip) && host.port == struct.port) {
				destination = host.channel;
				System.out.println("selected " + destination + " because of " + struct.ip + " " + struct.port);
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

	private void connect(HostInfo connectTo, Selector selector) throws IOException {
		if (serverIP.equals(connectTo.ip) && serverPort == connectTo.port) {
			return;
		}

		for (ConnectionInfo host : hostsConnections) {
			if (host.ip.equals(connectTo.ip) && (host.port == connectTo.port)) {
				return;
			}
		}

		System.out.println(">>Connecting with " + connectTo.ip.getHostAddress() + " " + connectTo.port);

		SocketAddress address = new InetSocketAddress(connectTo.ip, connectTo.port);
		SocketChannel client = SocketChannel.open(address);
		client.configureBlocking(false);
		client.register(selector, SelectionKey.OP_READ); //listen for incoming connections

		//send init
		String message = "init " + this.serverIP.getHostAddress() + " " + this.serverPort + "\r\n";
		System.out.println(">>I'll send \"" + message.trim() + "\" in connect");
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		buffer.clear();
		buffer.put(message.getBytes());
		buffer.flip();
		while (buffer.hasRemaining()) {
			client.write(buffer);
		}

		hostsConnections.add(new ConnectionInfo(client, connectTo.ip, connectTo.port, minUnusedLocalID.getAndIncrement(), ConnectionState.DEFAULT));
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
