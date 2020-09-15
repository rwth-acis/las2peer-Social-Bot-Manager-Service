package i5.las2peer.services.socialBotManagerService.dialogue;

import java.util.Collection;

import i5.las2peer.services.socialBotManagerService.dialogue.task.AgendaDialogueManager;
import i5.las2peer.services.socialBotManagerService.dialogue.task.AgendaDialogueNode;
import i5.las2peer.services.socialBotManagerService.model.ChatResponse;
import i5.las2peer.services.socialBotManagerService.model.Frame;
import i5.las2peer.services.socialBotManagerService.model.IncomingMessage;
import i5.las2peer.services.socialBotManagerService.model.Messenger;
import i5.las2peer.services.socialBotManagerService.model.Slot;

public class DialogueManagerGenerator {

    public AbstractDialogueManager generate(DialogueManagerType type, Messenger messenger) {
	return this.generate(type, messenger, null);
    }

    public AbstractDialogueManager generate(DialogueManagerType type, Messenger messenger, Frame frame) {

	System.out.println("generate Dialogue Manager " + type);
	AbstractDialogueManager manager;
	switch (type) {
	case AGENDA_TREE:
	    manager = generateAgendaDialogueManager(frame);
	    break;
	case SIMPLE:
	    manager = generateSimpleDialogueManager(messenger);
	    break;
	default:
	    manager = null;
	}
	return manager;
    }

    private AbstractDialogueManager generateSimpleDialogueManager(Messenger messenger) {

	SimpleDialogueManager manager = new SimpleDialogueManager();

	Collection<IncomingMessage> messages = messenger.getIncomingMessages();
	for (IncomingMessage message : messages) {
	    if (message.getResponseArray() != null) {
		for (ChatResponse response : message.getResponseArray()) {
		    manager.addIntent(message.getIntentKeyword(), response.getResponse());
		}
	    }
	}
	return manager;
    }

    private AbstractDialogueManager generateAgendaDialogueManager(Frame frame) {
	AgendaDialogueManager manager = new AgendaDialogueManager();

	// root node
	AgendaDialogueNode root = new AgendaDialogueNode();
	root.setIntent(frame.getIntent());
	root.addResponse(frame.getStartMessage().concat("\n"));
	manager.setRoot(root);
	manager.setGoal(frame);
	manager.goalMessage = frame.getEndMessage();

	// build tree
	for (Slot slot : frame.getSlots().values()) {
	    root.addChild(getNode(slot));
	}

	manager.validate();
	manager.reset();
	return manager;
    }

    private AgendaDialogueNode getNode(Slot slot) {
	AgendaDialogueNode node = new AgendaDialogueNode();
	node.setIntent(slot.getNlu_intent());
	node.setEntity(slot.getEntity());
	node.addResponse(slot.getMessage());
	if (slot.getParameter() != null && slot.getParameter().getParameterType() != null) {
	    node.setInputType(InputType.valueOf(slot.getParameter().getParameterType()));
	} else {
	    node.setInputType(InputType.String);
	}

	if (slot.hasChildren()) {
	    for (Slot subSlot : slot.getChildren()) {
		node.addChild(getNode(subSlot));
	    }
	}

	return node;

    }

}