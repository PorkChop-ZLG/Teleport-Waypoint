package com.zonlong.teleportwaypoint.client.xaero;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.zonlong.teleportwaypoint.client.ClientWaypointInfo;
import com.zonlong.teleportwaypoint.client.ClientWaypointState;

import xaero.map.element.render.ElementRenderLocation;
import xaero.map.element.render.ElementRenderProvider;

/**
 * Provides the current dimension's teleport waypoints to Xaero's world map
 * element renderer.
 */
public class TeleportWaypointWorldProvider
        extends ElementRenderProvider<TeleportWaypointElement, TeleportWaypointContext> {

    private Iterator<TeleportWaypointElement> iterator;

    @Override
    public void begin(ElementRenderLocation location, TeleportWaypointContext context) {
        List<TeleportWaypointElement> elements = new ArrayList<>();
        if (context.mapDimension != null) {
            for (ClientWaypointInfo info : ClientWaypointState.getWaypointsIn(context.mapDimension)) {
                if (XaeroIntegration.shouldShow(info.pocket(), ClientWaypointState.isActivated(info.uid()))) {
                    elements.add(new TeleportWaypointElement(info, ClientWaypointState.isActivated(info.uid())));
                }
            }
        }
        iterator = elements.iterator();
    }

    @Override
    public boolean hasNext(ElementRenderLocation location, TeleportWaypointContext context) {
        return iterator != null && iterator.hasNext();
    }

    @Override
    public TeleportWaypointElement getNext(ElementRenderLocation location, TeleportWaypointContext context) {
        return iterator == null ? null : iterator.next();
    }

    @Override
    public void end(ElementRenderLocation location, TeleportWaypointContext context) {
        iterator = null;
    }
}
