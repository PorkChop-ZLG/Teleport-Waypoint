package com.zonlong.teleportwaypoint.menu;

/**
 * A menu that carries rename capability and current name to the client screen.
 */
public interface RenameMenu {
    boolean canEdit();

    String getName();
}
