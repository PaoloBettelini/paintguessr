package ch.bettelini.server.game;

import java.util.HashMap;
import java.util.Map;

import org.java_websocket.WebSocket;

public class Game {

	public static final int TOKEN_SERVED	= 0;	// token
	public static final int JOIN_GAME		= 1;	// token, username
	public static final int CREATE_GAME		= 2;	// public, max_players, rounds, round_duration, username
	public static final int JOIN_RND		= 3;	// username
	public static final int NEXT_ROUND		= 4;	// -
	public static final int PLAYER_JOIN		= 5;	// -

	public static final int DRAW_BUFFER		= 20;	// point...
	public static final int MOUSE_UP		= 21;	// -
	public static final int SET_COLOR		= 22;	// r, g, b
	public static final int SET_WIDTH		= 23;	// line width

	public static final int MSG				= 30;	// message

	public static final int JOIN_ERROR		= 201;	// reason
	public static final int CREATE_ERROR	= 202;	// reason
	public static final int START_ERROR		= 203;	// reason

	//private WebSocket drawing;

	private Map<WebSocket, Player> players;
	
	private WebSocket drawing, admin;

	private boolean open;
	private int maxPlayers;
	private int rounds;
	private int roundDuration;

	private int currentRound;

	public Game(boolean open, int maxPlayers, int rounds, int roundDuration) {
		this.open = open;
		this.maxPlayers = maxPlayers;
		this.rounds = rounds;
		this.roundDuration = roundDuration;
		
		this.players = new HashMap<>();
		this.currentRound = 0;
	}

	public void addPlayer(WebSocket socket, String username) {
		// Send all previous player names
		for (Player player : players.values()) {
			socket.send(createPlayerJoinedPacket(player.getUsername()));
		}
		
		players.put(socket, new Player(username));

		// Notify everyone
		broadcast(createPlayerJoinedPacket(username));
	}

	public void messageFrom(WebSocket socket, byte[] data) {
		String msg = new String(data, 1, data.length - 1);

		// hasWonTurn
		boolean status = players.get(socket).hasWonRound();
		
		if (!status && socket != drawing) { // && is word
			// [...]
			// return;
		}

		for (WebSocket player : players.keySet()) {
			if (!status || players.get(player).hasWonRound()) {
				player.send(data);
				System.out.println("Sent to " + players.get(player).getUsername());
			}
		}
	}

	public int size() {
		return players.size();
	}

	public boolean canJoin() {
		return !hasStarted() && !isFull();
	}

	public boolean hasStarted() {
		return currentRound != 0;
	}

	public boolean contains(WebSocket socket) {
		return players.containsKey(socket);
	}

	public void removePlayer(WebSocket socket) {
		players.remove(socket);
	}

	public boolean isPublic() {
		return open;
	}

	public boolean isFull() {
		return players.size() == maxPlayers;
	}
	
	public void broadcast(byte[] data) {
		for (WebSocket socket : players.keySet()) {
			socket.send(data);
		}
	}

	private static byte[] createPlayerJoinedPacket(String username) {
		return createPacket((byte) PLAYER_JOIN, username.getBytes());
	}

	protected static byte[] createPacket(byte cmd, byte[] data) {
		byte[] packet = new byte[data.length + 1];

		packet[0] = cmd;

		for (int i = 0; i < data.length; i++) {
			packet[i + 1] = data[i];
		}

		return packet;
	}

}