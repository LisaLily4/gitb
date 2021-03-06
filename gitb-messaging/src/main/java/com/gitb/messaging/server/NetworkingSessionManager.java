package com.gitb.messaging.server;

import com.gitb.messaging.server.exceptions.ExistingSessionException;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by serbay on 9/24/14.
 *
 * Class that keeps the session information related to each messaging server worker instance.
 */
public class NetworkingSessionManager {

	private Map<InetAddress, String> sessions;

	public NetworkingSessionManager() {
		sessions = new ConcurrentHashMap<>();
	}

	public String bindToSession(InetAddress address, String sessionId) throws ExistingSessionException {
		if(sessions.containsKey(address)) {
			throw new ExistingSessionException(address, sessionId);
		} else {
			sessions.put(address, sessionId);

			return sessionId;
		}
	}

	public int getNumberOfActiveSessions() {
		return sessions.size();
	}

	public boolean isSessionExists(InetAddress address) {
		return sessions.containsKey(address);
	}

	public String getSessionId(InetAddress address) {
		return sessions.get(address);
	}

	public void endSession(InetAddress address) {
		sessions.remove(address);
	}
}
