package i5.las2peer.services.socialBotManagerService.chat;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.TimeZone;

import org.json.JSONArray;

import com.rocketchat.core.model.RocketChatMessage;
import com.rocketchat.core.model.RocketChatMessage.Type;

public class RocketChatMessageCollector extends ChatMessageCollector {
	private static String[][] UMLAUT_REPLACEMENTS = { { new String("Ä"), "Ae" }, { new String("Ü"), "Ue" },
			{ new String("Ö"), "Oe" }, { new String("ä"), "ae" }, { new String("ü"), "ue" }, { new String("ö"), "oe" },
			{ new String("ß"), "ss" } };

	public static String replaceUmlaute(String orig) {
		String result = orig;

		for (int i = 0; i < UMLAUT_REPLACEMENTS.length; i++) {
			result = result.replace(UMLAUT_REPLACEMENTS[i][0], UMLAUT_REPLACEMENTS[i][1]);
		}

		return result;
	}

	public void handle(RocketChatMessage message) {
		Type type = message.getMsgType();
		if (type != null) {
			if (type.equals(Type.TEXT)) {
				try {
					System.out.println("Handling text.");
					JSONArray emails = message.getSender().getEmails();
					// System.out.println(emails.toString());
					String rid = message.getRoomId();
					String user = message.getSender().getUserName();
					String msg = replaceUmlaute(message.getMessage());
					ChatMessage cm = new ChatMessage(rid, user, msg);
					// timestamp
					cm.setTime(message.getMsgTimestamp().toInstant().toString());
					// domain
					cm.setDomain(this.getDomain());

					this.addMessage(cm);
					System.out.println("Message added.");
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				System.out.println("Unsupported type: " + type.toString());
			}
		} else {
			System.out.println("Skipped");
		}
	}

	public void handle(RocketChatMessage message, int role, String email) {
		Type type = message.getMsgType();
		if (type != null) {
			if (type.equals(Type.TEXT)) {
				try {
					System.out.println("Handling text.");
					JSONArray emails = message.getSender().getEmails();
					// System.out.println(emails.toString());
					String rid = message.getRoomId();
					String user = message.getSender().getUserName();
					String msg = replaceUmlaute(message.getMessage());
					ChatMessage cm = new ChatMessage(rid, user, msg);
					System.out.println("Email of user is " + email);
					cm.setEmail(email);
					cm.setRole(role);
					// timestamp
					cm.setTime(message.getMsgTimestamp().toInstant().toString());
					// domain
					cm.setDomain(this.getDomain());

					this.addMessage(cm);
					System.out.println("Message added.");
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				System.out.println("Unsupported type: " + type.toString());
			}
		} else {
			System.out.println("Skipped");
		}
	}

	public void handle(RocketChatMessage message, String fileBody, String fileName, String fileType, int role,
			String email) {
		Type type = message.getMsgType();
		if (type != null) {
			if (type.equals(Type.ATTACHMENT)) {
				try {
					System.out.println("Handling Attachment.");
					JSONArray emails = message.getSender().getEmails();
					System.out.println("rcket message is " + message);
					String rid = message.getRoomId();
					System.out.println(rid);
					String user = message.getSender().getUserName();
					String msg = replaceUmlaute(fileName);
					ChatMessage cm = new ChatMessage(rid, user, msg, fileName, fileType, fileBody);
					System.out.println("Email of user is " + email);
					cm.setEmail(email);
					cm.setRole(role);

					// timestamp
					cm.setTime(message.getMsgTimestamp().toInstant().toString());

					// domain
					cm.setDomain(this.getDomain());

					this.addMessage(cm);
					System.out.println("Message added.");
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				System.out.println("Unsupported type: " + type.toString());
			}
		} else {
			System.out.println("Skipped");
		}
	}
}
