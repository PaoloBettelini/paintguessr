package ch.bettelini.server.game;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;

import org.java_websocket.WebSocket;

import ch.bettelini.server.game.utils.Words;

public class Game {

	private Map<WebSocket, Player> players;
	
	private WebSocket drawing, admin;

	private boolean open;
	private int maxPlayers;
	private int rounds;
	private int turnDuration;

	private int currentRound;
	private int currentTurn;
	private long roundStartTime;
	private String currentWord;
	private List<String> lastWords;
	private TimerTask currentSchedulerTask;

	public Game(boolean open, int maxPlayers, int rounds, int turnDuration) {
		this.open = open;
		this.maxPlayers = maxPlayers;
		this.rounds = rounds;
		this.turnDuration = turnDuration;
		
		this.lastWords = new LinkedList<>();
		this.currentSchedulerTask = null;
		this.players = new HashMap<>();
		this.currentRound = 0;
		this.currentTurn = 0;
	}

	public void processPacket(WebSocket socket, byte[] data) {
		int cmd = data[0] & 0xFF;

		switch (cmd) {
			case Protocol.SET_WIDTH:
			case Protocol.MOUSE_UP:
			case Protocol.DRAW_BUFFER:
			case Protocol.SET_COLOR:
			case Protocol.UNDO:
				if (drawing != null && drawing == socket) {
					broadcastDrawingPacket(data);
				}
				break;
			case Protocol.START:
				start(socket);
				break;
			case Protocol.MSG:
				messageFrom(socket, data);
				break;
		}
	}

	public void addPlayer(WebSocket socket, String username) {
		// Send all previous player names
		for (Player player : players.values()) {
			socket.send(Protocol.createPlayerJoinedPacket(player.getUsername().getBytes()));
		}
		
		if (players.size() == 0) {
			admin = socket;
		}

		players.put(socket, new Player(username));

		// Notify everyone
		broadcast(Protocol.createPlayerJoinedPacket(username.getBytes()));

		
		if (players.size() == maxPlayers) {
			start();
		}

		System.out.println(players.size());
	}

	public void messageFrom(WebSocket socket, byte[] data) {
		Player sender = players.get(socket);
		boolean status = sender.hasWonTurn();

		String msg = new String(data, 1, data.length - 1);

		if (currentWord != null && !status && currentWord.equalsIgnoreCase(msg)) {
			int amount = 100 - (int) (System.currentTimeMillis() - roundStartTime) / turnDuration / 10;
			broadcast(Protocol.createAddScorePacket(amount, sender.getUsername().getBytes()));
			
			sender.hasWonTurn(true);

			if (isTurnComplete()) {
				forceNextTurn();
			}

			return;
		}

		byte[] packet = Protocol.createMessagePacket((sender.getUsername() + ": " + msg).getBytes(), sender.hasWonTurn());

		for (WebSocket player : players.keySet()) {
			if (!status || players.get(player).hasWonTurn()) {
				player.send(packet);
			}
		}
	}

	private boolean isTurnComplete() {
		for (Player player : players.values()) {
			if (!player.hasWonTurn()) {
				return false;
			}
		}

		return true;
	}

	public void start(WebSocket from) {
		if (from == admin && players.size() > 1 && !hasStarted()) {
			start();
		}
	}

	private void forceNextTurn() {
		release();
		nextTurn();
	}

	private void start() {
		nextTurn();
		++currentRound;
	}

	private void nextTurn() {
		if (++currentTurn > players.size()) {
			currentTurn = 1;

			if (++currentRound > rounds) {
				GameOver();
				return;
			}

			// Reset round dependent variables
			for (Player player : players.values()) {
				player.nextRound();
			}
		}

		// Reset turn dependent variables
		for (Player player : players.values()) {
			player.nextTurn();
		}

		// Generate who's drawing next
		for (WebSocket player : players.keySet()) {
			if (!players.get(player).hasAlreadyDrawn()) {
				drawing = player;
				break;
			}
		}

		Player _drawing = players.get(drawing);
		_drawing.hasAlreadyDrawn(true);
		_drawing.hasWonTurn(true);
		
		// Choose word
		this.currentWord = Words.random();
		do {
			this.currentWord = Words.random();
		} while(lastWords.contains(this.currentWord));
		lastWords.add(currentWord);

		byte[] packet = Protocol.createNextTurnPacket(false, Words.obfuscate(currentWord).getBytes());
		byte[] drawingPacket = Protocol.createNextTurnPacket(true, currentWord.getBytes());
		
		drawing.send(drawingPacket);

		for (WebSocket player : players.keySet()) {
			if (player != drawing) {
				player.send(packet);
			}
		}

		currentSchedulerTask = new TimerTask(){

			@Override
			public void run() {
				nextTurn();
			}
			
		};

		GamesHandler.scheduler.schedule(currentSchedulerTask, turnDuration * 1000);
		roundStartTime = System.currentTimeMillis();
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
		byte[] username = players.get(socket).getUsername().getBytes();
		players.remove(socket);
		boolean wasDrawing = socket == drawing;
		broadcast(Protocol.createPlayerLeftPacket(username, wasDrawing));

		if (players.size() < 2) {
			GameOver();
		} else if (wasDrawing) {
			forceNextTurn();
		}
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

	public void broadcastDrawingPacket(byte[] data) {
		for (WebSocket socket : players.keySet()) {
			if (socket != drawing) {
				socket.send(data);
			}
		}
	}

	public boolean contains(String username) {
		for (Player player : players.values()) {
			if (player.getUsername().equals(username)) {
				return true;
			}
		}

		return false;
	}

	private void GameOver() {
		broadcast(Protocol.createGameOverPacket());

		GamesHandler.delete(this);
	}

	public void release() {
		if (currentSchedulerTask != null) {
			currentSchedulerTask.cancel();
		}
	}

	public int getRounds() {
		return rounds;
	}

	public int getTurnDuration() {
		return turnDuration;
	}

	public int getMaxPlayers() {
		return maxPlayers;
	}

}