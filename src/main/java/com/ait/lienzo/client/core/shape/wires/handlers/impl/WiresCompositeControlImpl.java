package com.ait.lienzo.client.core.shape.wires.handlers.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.ait.lienzo.client.core.shape.wires.WiresConnector;
import com.ait.lienzo.client.core.shape.wires.WiresContainer;
import com.ait.lienzo.client.core.shape.wires.WiresManager;
import com.ait.lienzo.client.core.shape.wires.WiresShape;
import com.ait.lienzo.client.core.shape.wires.handlers.MouseEvent;
import com.ait.lienzo.client.core.shape.wires.handlers.WiresCompositeControl;
import com.ait.lienzo.client.core.shape.wires.handlers.WiresConnectorHandler;
import com.ait.lienzo.client.core.shape.wires.handlers.WiresShapeControl;
import com.ait.lienzo.client.core.types.Point2D;

/**
 * The default WiresCompositeControl implementation.
 * It orchestrates different controls for handling interactions with multiple wires shapes and connectors.
 * Notice that docking capabilities are not being considered when handling multiple wires objects.
 */
public class WiresCompositeControlImpl
        extends AbstractWiresBoundsConstraintControl
        implements WiresCompositeControl {

    private Context selectionContext;
    private Point2D delta;
    private Collection<WiresShape> selectedShapes;
    private Collection<WiresConnector> selectedConnectors;
    private WiresConnector[] m_connectorsWithSpecialConnections;

    public WiresCompositeControlImpl(Context selectionContext) {
        this.selectionContext = selectionContext;
    }

    @Override
    public void setContext(Context provider) {
        this.selectionContext = provider;
    }

    @Override
    public void onMoveStart(double x,
                            double y) {
        delta = new Point2D(0, 0);
        selectedShapes = new ArrayList<>(selectionContext.getShapes());
        selectedConnectors = new ArrayList<>(selectionContext.getConnectors());

        Map<String, WiresConnector> connectors = new HashMap<String, WiresConnector>();

        for (WiresShape shape : selectedShapes) {

            ShapeControlUtils.collectionSpecialConnectors(shape,
                                                          connectors);

            if (shape.getMagnets() != null) {
                shape.getMagnets().onNodeDragStart(null); // Must do magnets first, to avoid attribute change updates being processed.
                // Don't need to do this for nested objects, as those just move with their containers, without attribute changes
            }

            disableDocking(shape.getControl());

            final Point2D location = shape.getComputedLocation();
            final double sx = location.getX();
            final double sy = location.getY();

            shape.getControl().onMoveStart(sx,
                                           sy);
        }

        m_connectorsWithSpecialConnections = connectors.values().toArray(new WiresConnector[connectors.size()]);

        for (WiresConnector connector : selectedConnectors) {
            WiresConnectorHandler handler = connector.getWiresConnectorHandler();
            handler.getControl().onMoveStart(x,
                                             y); // records the start position of all the points
            WiresConnector.updateHeadTailForRefreshedConnector(connector);
        }
    }

    @Override
    public boolean isOutOfBounds(final double dx,
                                 final double dy) {
        for (WiresShape shape : selectedShapes) {
            if (shape.getControl().isOutOfBounds(dx, dy)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onMove(double dx,
                          double dy) {

        if (isOutOfBounds(dx, dy)) {
            return true;
        }

        delta = new Point2D(dx, dy);

        // Delegate location deltas to shape controls and obtain current locations for each one.
        final Collection<WiresShape> shapes = selectedShapes;
        if (!shapes.isEmpty()) {
            final WiresManager wiresManager = shapes.iterator().next().getWiresManager();
            final Point2D[] locs = new Point2D[shapes.size()];
            int i = 0;
            for (WiresShape shape : shapes) {
                shape.getControl().onMove(dx,
                                          dy);
                locs[i++] = getCandidateShapeLocationRelativeToInitialParent(shape);
            }

            // Check if new locations are allowed.
            final WiresShape[] shapesArray = toArray(shapes);
            final boolean locationAllowed = wiresManager.getLocationAcceptor()
                    .allow(shapesArray,
                           locs);

            // Do the updates.
            if (locationAllowed) {
                i = 0;
                for (WiresShape shape : shapes) {
                    shape.getControl().getParentPickerControl().setShapeLocation(locs[i++]);
                    postUpdateShape(shape);
                }
            }
        }

        final Collection<WiresConnector> connectors = selectedConnectors;
        if (!connectors.isEmpty()) {
            // Update connectors and connections.
            for (WiresConnector connector : connectors) {
                WiresConnectorHandler handler = connector.getWiresConnectorHandler();
                handler.getControl().move(dx,
                                          dy,
                                          true,
                                          true);
                WiresConnector.updateHeadTailForRefreshedConnector(connector);
            }
        }

        ShapeControlUtils.updateSpecialConnections(m_connectorsWithSpecialConnections,
                                                   false);

        return false;
    }

    private Point2D getCandidateShapeLocationRelativeToInitialParent(final WiresShape shape) {
        final Point2D candidate = shape.getControl().getContainmentControl().getCandidateLocation();
        final WiresParentPickerControlImpl parentPickerControl =
                (WiresParentPickerControlImpl) shape.getControl().getParentPickerControl();
        final Point2D io = null != parentPickerControl.getInitialParent() ?
                parentPickerControl.getInitialParent().getComputedLocation() :
                new Point2D(0, 0);
        final Point2D co = null != parentPickerControl.getParent() ?
                parentPickerControl.getParent().getComputedLocation() :
                new Point2D(0, 0);
        return co.add(candidate).minus(io);
    }

    public boolean isAllowed() {
        // Check parents && allow acceptors.
        final WiresContainer parent = getSharedParent();
        return null != parent &&
                parent.getWiresManager().getContainmentAcceptor()
                        .containmentAllowed(parent,
                                            toArray(selectedShapes));
    }

    public WiresContainer getSharedParent() {
        final Collection<WiresShape> shapes = selectedShapes;
        if (shapes.isEmpty()) {
            return null;
        }
        WiresContainer shared = shapes.iterator().next().getControl().getParentPickerControl().getParent();
        for (WiresShape shape : shapes) {
            WiresContainer parent = shape.getControl().getParentPickerControl().getParent();
            if (parent != shared) {
                return null;
            }
        }
        return shared;
    }

    @Override
    public boolean onMoveComplete() {
        boolean completeResult = true;
        final Collection<WiresShape> shapes = selectedShapes;
        if (!shapes.isEmpty()) {
            final WiresManager wiresManager = shapes.iterator().next().getWiresManager();
            int i = 0;
            for (WiresShape shape : shapes) {
                if (!shape.getControl().onMoveComplete()) {
                    completeResult = false;
                }
                i++;
            }
        }
        final Collection<WiresConnector> connectors = selectedConnectors;
        if (!connectors.isEmpty()) {
            // Update connectors and connections.
            for (WiresConnector connector : connectors) {
                if (!connector.getWiresConnectorHandler().getControl().onMoveComplete()) {
                    completeResult = false;
                }
            }
        }

        delta = new Point2D(0, 0);
        return completeResult;
    }

    @Override
    public boolean accept() {
        final Collection<WiresShape> shapes = selectedShapes;
        if (!shapes.isEmpty()) {
            final WiresManager wiresManager = shapes.iterator().next().getWiresManager();
            final Point2D[] shapeCandidateLocations = new Point2D[shapes.size()];

            int i = 0;
            for (WiresShape shape : shapes) {
                shapeCandidateLocations[i] = shape.getControl().getContainmentControl().getCandidateLocation();
                i++;
            }

            final WiresShape[] shapesArray = toArray(shapes);
            final WiresContainer parent = getSharedParent();
            boolean completeResult = null != parent &&
                    wiresManager.getContainmentAcceptor()
                            .acceptContainment(parent,
                                               shapesArray);

            if (completeResult) {
                completeResult = wiresManager.getLocationAcceptor()
                        .accept(shapesArray,
                                shapeCandidateLocations);
            }
            return completeResult;
        }
        return false;
    }

    @Override
    public void execute() {
        int i = 0;
        for (WiresShape shape : selectedShapes) {
            shape.getControl().getContainmentControl().execute();
            postUpdateShape(shape);
        }

        for (WiresConnector connector : selectedConnectors) {
            WiresConnectorHandler handler = connector.getWiresConnectorHandler();
            WiresConnector.updateHeadTailForRefreshedConnector(connector);
        }

        ShapeControlUtils.updateSpecialConnections(m_connectorsWithSpecialConnections,
                                                   true);

        clear();
    }

    @Override
    public void clear() {
        for (WiresShape shape : selectedShapes) {
            shape.getControl().clear();
            enableDocking(shape.getControl());
        }
        clearState();
    }

    @Override
    public void reset() {
        for (WiresShape shape : selectedShapes) {
            shape.getControl().reset();
            enableDocking(shape.getControl());
        }
        for (WiresConnector connector : selectedConnectors) {
            WiresConnectorHandler handler = connector.getWiresConnectorHandler();
            handler.getControl().reset();
            WiresConnector.updateHeadTailForRefreshedConnector(connector);
        }
        clearState();
    }

    @Override
    public Point2D getAdjust() {
        return delta;
    }

    public Context getContext() {
        return selectionContext;
    }

    @Override
    public void onMouseClick(final MouseEvent event) {
        for (WiresShape shape : selectionContext.getShapes()) {
            shape.getControl().onMouseClick(event);
        }
    }

    @Override
    public void onMouseDown(MouseEvent event) {
        for (WiresShape shape : selectionContext.getShapes()) {
            shape.getControl().onMouseDown(event);
        }
    }

    @Override
    public void onMouseUp(MouseEvent event) {
        for (WiresShape shape : selectionContext.getShapes()) {
            shape.getControl().onMouseUp(event);
        }
    }

    private void clearState() {
        delta = new Point2D(0, 0);
        selectedShapes = null;
        selectedConnectors = null;
        m_connectorsWithSpecialConnections = null;
    }

    private static void disableDocking(WiresShapeControl control) {
        control.getDockingControl().setEnabled(false);
    }

    private static void enableDocking(WiresShapeControl control) {
        control.getDockingControl().setEnabled(true);
    }

    private void postUpdateShape(final WiresShape shape) {
        shape.getControl().getMagnetsControl().shapeMoved();
        ShapeControlUtils.updateNestedShapes(shape);
    }

    private static WiresShape[] toArray(final Collection<WiresShape> shapes) {
        return shapes.toArray(new WiresShape[shapes.size()]);
    }
}
