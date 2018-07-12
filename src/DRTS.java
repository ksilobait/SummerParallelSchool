public class DRTS {
	private Server server;

	DRTS(Server server) {
		this.server = server;
	}

	public int java_get_count(int call_id) {
		System.out.println("hello from java_get_count");
		return server.getCount(call_id);
	}
}
