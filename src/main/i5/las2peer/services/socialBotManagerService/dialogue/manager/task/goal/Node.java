package i5.las2peer.services.socialBotManagerService.dialogue.manager.task.goal;

public abstract class Node {

    public abstract boolean isReady();

    public abstract boolean isFull();

    public abstract boolean isConfirmed();

    public abstract void invariant();

    public abstract NodeList getAll();

    public abstract Node next();

    public abstract Object toJSON();
}